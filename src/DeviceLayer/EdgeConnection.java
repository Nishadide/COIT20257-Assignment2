/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: DeviceLayer/EdgeConnection.java - the socket connection to the edge
 */
package DeviceLayer;

import Contract.DemoLogger;
import Contract.SensorFactor;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * @param ui        the device layer window
     * @param sensors   the four sensor panels
     * @param actuators the four actuator threads
     * @param demo      the shared demonstration logger
     */
    public EdgeConnection(DeviceMain ui, SensorPanel[] sensors,
                          ActuatorController[] actuators, DemoLogger demo) {
        this.ui          = ui;
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
     * Enables the full functions of the framework, once the mutual
     * authentication with the edge layer has succeeded: the sensors begin
     * operating, the command thread starts receiving, and the reporting
     * thread starts sending.
     *
     * Called by the Security menu's Authentication action after the
     * CSAuthenticator exchange completes and the session key is held.
     */
    public void authenticationComplete() {
        if (!connected || authenticated) {
            return;
        }
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

        // Demonstration output: the plain text being sent and the cipher
        // text actually transmitted. Until the security layer is
        // integrated the exchange is in plain text, so the marker below
        // stands in for the Base64 cipher text; when encryption is added,
        // the real cipher text is passed here and nothing else changes.
        demo.sent(status, NOT_ENCRYPTED_YET);
        System.out.println("Device sent:     " + status);

        out.reset();               // see the note in the class comment
        out.writeObject(status);
        out.flush();
    }

    /**
     * Reads one command from the edge layer, blocking until one arrives.
     *
     * @return the object received, or null if the connection has closed
     * @throws IOException            if the connection fails
     * @throws ClassNotFoundException if an unknown class arrives
     */
    public Object receive() throws IOException, ClassNotFoundException {
        if (!connected || in == null) {
            return null;
        }
        Object received = in.readObject();

        if (received instanceof SensorFactor) {
            demo.received(NOT_ENCRYPTED_YET, (SensorFactor) received);
        }
        return received;
    }

    /** @return true while the device layer is connected. */
    public boolean isConnected() {
        return connected;
    }

    /** @return true once the mutual authentication has succeeded. */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Placeholder for the cipher text in the demonstration output, used
     * while the exchange is still in plain text. Replaced by the real
     * Base64 cipher text when the security layer is integrated.
     */
    private static final String NOT_ENCRYPTED_YET =
            "(plain text - encryption not yet integrated)";

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
