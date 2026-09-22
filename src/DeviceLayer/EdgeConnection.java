/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: DeviceLayer/EdgeConnection.java - the socket connection to the edge
 */
package DeviceLayer;

import Contract.CSAuthenticator;
import Contract.DemoLogger;
import Contract.SensorFactor;
import Security.Authenticator;
import Security.CryptoUtil;
import Security.SecurityKeys;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;

/**
 * EdgeConnection
 * --------------
 * Owns the TCP connection from the device layer to the edge layer: the
 * socket, the object streams, and the two threads that use them.
 *
 * THE STREAM ORDER MATTERS
 * ========================
 * ObjectOutputStream is created BEFORE ObjectInputStream, matching the order
 * used by the edge server. Constructing an ObjectOutputStream writes a
 * stream header immediately, and constructing an ObjectInputStream blocks
 * until it has read one. If both ends created their input stream first, both
 * would block forever waiting for a header the other end had not yet sent.
 *
 * RESETTING THE OUTPUT STREAM
 * ===========================
 * ObjectOutputStream caches objects it has already written and sends a back
 * reference if it sees the same object again. The sensors report repeatedly,
 * so reset() is called before every write; otherwise the edge layer would
 * receive the first reading of each sensor over and over.
 *
 * WHO USES THIS CLASS
 * ===================
 *   ReportingThread calls send() every three seconds
 *   CommandThread   calls receive() in a loop
 * Both are started by connect() and stopped by disconnect().
 */
public class EdgeConnection {

    /** The window, told when the connection state changes. */
    private final DeviceMain ui;

    /** The four sensors, in display order and keyed by type. */
    private final SensorPanel[] sensorArray;
    private final Map<String, SensorPanel> sensorMap = new HashMap<>();

    /** The four actuator threads, switched on while connected. */
    private final ActuatorController[] actuators;

    /** The demonstration output required by the assignment. */
    private final DemoLogger demo;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    private ReportingThread reporting;
    private CommandThread   commands;

    private volatile boolean connected = false;

    /**
     * True once the mutual authentication has succeeded. Until then the
     * connection is open but the framework's functions are not enabled.
     */
    private volatile boolean authenticated = false;

    /** The device layer's own private key and the edge layer's public key. */
    private SecurityKeys keys;

    /**
     * The session key agreed during the mutual authentication.
     *
     * Declared volatile because it is written on the Event Dispatch Thread,
     * by the Security menu's Authentication action, and read by the
     * reporting and command threads. Those threads are started after the
     * key is set, which by itself guarantees they see it, but volatile
     * states the requirement plainly rather than leaving it to be inferred.
     */
    private volatile SecretKey sessionKey;

    /**
     * @param ui        the device layer window
     * @param sensors   the four sensor panels
     * @param actuators the four actuator threads
     * @param demo      the shared demonstration logger
     * @param keys      the device layer's security keys
     */
    public EdgeConnection(DeviceMain ui, SensorPanel[] sensors,
                          ActuatorController[] actuators, DemoLogger demo,
                          SecurityKeys keys) {
        this.ui          = ui;
        this.keys        = keys;
        this.sensorArray = sensors;
        this.actuators   = actuators;
        this.demo        = demo;
        for (SensorPanel sensor : sensors) {
            sensorMap.put(sensor.getType(), sensor);
        }
    }

    /**
     * Opens the connection to the edge layer.
     *
     * NOTE what this does NOT do. It opens the socket and the streams, and
     * nothing else: the sensors do not begin operating and nothing is
     * reported yet. The assignment specification requires that "the device
     * layer and the edge layer need mutual authentication before the full
     * functions of the edge computing framework are enabled", so reporting
     * begins only when authenticationComplete() is called after a
     * successful mutual authentication.
     *
     * The streams are left free of any reading thread at this point, so
     * that the authentication exchange can use them directly without a
     * background thread consuming the reply.
     *
     * @param host the edge layer host name
     * @param port the edge layer port
     * @throws IOException if the connection cannot be opened
     */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);

        // Output stream FIRST - see the note in the class comment.
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in  = new ObjectInputStream(socket.getInputStream());

        connected = true;
        authenticated = false;
        System.out.println("Device: connected to the edge layer at "
                + host + ":" + port);
        System.out.println("Device: awaiting mutual authentication before "
                + "reporting begins.");

        ui.setConnected(true);
    }

    /**
     * Performs the device layer's half of the mutual authentication:
     * specification steps 1, 6, 7, 8 and 9, and then enables the full
     * functions of the framework.
     *
     * The exchange uses the streams directly. That is safe because
     * connect() deliberately starts no reading thread, so nothing else can
     * consume the edge layer's reply.
     *
     * @throws Exception if the connection fails or the edge layer cannot
     *                   be authenticated
     */
    public void authenticate() throws Exception {
        if (!connected || authenticated) {
            return;
        }

        // Step 1: a fresh random verification string for this exchange,
        // and the authenticator that carries it.
        String verificationString = CryptoUtil.randomVerificationString();
        CSAuthenticator toEdge =
                Authenticator.createDeviceAuthenticator(keys, verificationString);

        out.reset();
        out.writeObject(toEdge);
        out.flush();

        // The edge layer replies with its own authenticator.
        Object reply = in.readObject();
        if (!(reply instanceof CSAuthenticator)) {
            throw new SecurityException(
                    "The edge layer did not reply with an authenticator.");
        }
        CSAuthenticator fromEdge = (CSAuthenticator) reply;

        // Steps 6, 7, 8 and 9: recover the session key, verify the edge
        // layer's identity, and check the verification string came back.
        sessionKey = Authenticator.verifyEdgeReply(
                fromEdge, keys, verificationString);

        // The demonstration output required by the assignment: the
        // verification string and the session key, each in plain text and
        // cipher text. The edge layer prints the same values.
        demo.verificationString(verificationString,
                fromEdge.getVerficationString());
        demo.sessionKey(CryptoUtil.encodeSessionKey(sessionKey),
                fromEdge.getSessionKey());
        demo.authenticationComplete();

        authenticated = true;

        // The sensors begin operating only now. On start-up, and while
        // merely connected, no sensor is running.
        for (ActuatorController actuator : actuators) {
            actuator.setSensorsActive(true);
        }

        commands = new CommandThread(this, sensorMap);
        commands.start();

        reporting = new ReportingThread(this, sensorArray);
        reporting.start();

        System.out.println("Device: reporting started.");
    }

    /**
     * Closes the connection and returns the sensors to their start-up state.
     */
    public void disconnect() {
        if (!connected) {
            return;
        }
        connected = false;
        authenticated = false;

        for (ActuatorController actuator : actuators) {
            actuator.setSensorsActive(false);
        }
        if (reporting != null) {
            reporting.stopReporting();
        }
        if (commands != null) {
            commands.stopReceiving();
        }
        closeQuietly();

        System.out.println("Device: disconnected from the edge layer.");
        ui.setConnected(false);
    }

    /**
     * Called by the threads when the connection fails unexpectedly, so the
     * interface returns to its disconnected state.
     */
    public void connectionLost() {
        if (connected) {
            disconnect();
        }
    }

    /**
     * Sends one sensor status to the edge layer.
     *
     * @param status the SensorFactor to send
     * @throws IOException if the connection fails
     */
    public synchronized void send(SensorFactor status) throws IOException {
        if (!connected || out == null) {
            return;
        }

        try {
            // The status is serialized, encrypted with the session key and
            // Base64 encoded; that string is what travels over the stream,
            // so the cipher text printed below is exactly what is sent.
            String cipherText =
                    CryptoUtil.encryptObject(status, sessionKey);

            demo.sent(status, cipherText);
            System.out.println("Device sent:     " + status);

            out.reset();           // see the note in the class comment
            out.writeObject(cipherText);
            out.flush();

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("Device: could not encrypt a report: "
                    + e.getMessage());
        }
    }

    /**
     * Reads one command from the edge layer, blocking until one arrives.
     *
     * @return the decrypted command, or null if the message could not be
     *         decrypted and was discarded
     * @throws IOException            if the connection fails
     * @throws ClassNotFoundException if an unknown class arrives
     */
    public SensorFactor receive() throws IOException, ClassNotFoundException {
        if (!connected || in == null) {
            return null;
        }
        Object received = in.readObject();
        if (received == null) {
            return null;
        }

        if (!(received instanceof String)) {
            System.out.println("Device: expected encrypted data but received "
                    + received.getClass().getName());
            return null;
        }

        String cipherText = (String) received;
        try {
            SensorFactor command = (SensorFactor)
                    CryptoUtil.decryptObject(cipherText, sessionKey);

            // Demonstration output: the cipher text exactly as it arrived,
            // and the plain text recovered from it.
            demo.received(cipherText, command);
            return command;

        } catch (Exception e) {
            // A message that does not decrypt under the session key did not
            // come from the authenticated edge layer, so it is discarded.
            // This is what stops an attacker injecting a fake SensorFactor.
            System.out.println("Device: a message failed to decrypt "
                    + "and was discarded.");
            return null;
        }
    }

    /** @return true while the device layer is connected. */
    public boolean isConnected() {
        return connected;
    }

    /** @return true once the mutual authentication has succeeded. */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /** Closes the streams and socket, ignoring failures on the way out. */
    private void closeQuietly() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        in = null;
        out = null;
        socket = null;
    }
}
