/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: EdgeLayer/DeviceHandler.java - one thread per connected device layer
 */
package EdgeLayer;

import Contract.CSAuthenticator;
import Contract.DemoLogger;
import Contract.SensorFactor;
import Security.Authenticator;
import Security.CryptoUtil;
import Security.SecurityKeys;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import javax.crypto.SecretKey;

/**
 * DeviceHandler
 * -------------
 * Services ONE connected device layer: authenticates it, then exchanges
 * encrypted sensor statuses and actuator commands with it.
 *
 * THE CONCURRENCY OF THE EDGE SERVER
 * ==================================
 * This class extends java.lang.Thread and puts its work in run(), which is
 * the thread-per-connection architecture described in the unit material:
 * the server's accept loop never blocks on one client's traffic, because
 * each connection is serviced by its own thread. It is the same pattern as
 * the Connection class of the multi-threaded TCP server in the Week 2 and
 * Week 4 lectures.
 *
 * THE TWO PHASES OF A CONNECTION
 * ==============================
 * 1. AUTHENTICATION. The first object read must be a CSAuthenticator. It
 *    is verified, a session key is generated, and a reply authenticator is
 *    sent back. If the verification fails the connection is closed at once
 *    and no sensor data is ever processed, which is what the specification
 *    means by requiring mutual authentication "before the full functions of
 *    the edge computing framework are enabled".
 *
 * 2. SECURED EXCHANGE. Every message thereafter is a SensorFactor encrypted
 *    with the session key. Nothing is accepted in plain text.
 *
 * HOW AN ENCRYPTED SensorFactor TRAVELS
 * =====================================
 * A SensorFactor is serialized, encrypted with AES under the session key,
 * and Base64 encoded, and that STRING is what goes over the stream. So the
 * cipher text printed in the demonstration output is exactly the bytes
 * transmitted, which is what makes the sender's printed cipher text
 * identical to the receiver's.
 *
 * THE STREAM ORDER MATTERS
 * ========================
 * ObjectOutputStream is created BEFORE ObjectInputStream. Constructing an
 * ObjectOutputStream writes a stream header immediately, and constructing an
 * ObjectInputStream blocks until it has read one. If both ends created their
 * input stream first, both would block forever.
 */
public class DeviceHandler extends Thread {

    /** The socket connected to one device layer. */
    private final Socket socket;

    /** The decision logic; stateless, so one instance is shared safely. */
    private final EdgeAnalyser analyser;

    /** The server that created this handler, notified of status changes. */
    private final EdgeServer server;

    /** The shared demonstration output required by the assignment. */
    private final DemoLogger demo;

    /** The edge layer's own private key and the device layer's public key. */
    private final SecurityKeys keys;

    /** The session key agreed during authentication. */
    private SecretKey sessionKey;

    /**
     * Whether this handler has counted itself as a connected device. Needed
     * so that a connection which fails authentication does not decrement a
     * count it never incremented.
     */
    private boolean counted = false;

    private ObjectOutputStream out;
    private ObjectInputStream  in;

    /**
     * @param socket   the accepted connection to a device layer
     * @param analyser the shared decision logic
     * @param server   the edge server, for status callbacks
     * @param demo     the shared demonstration logger
     * @param keys     the edge layer's security keys
     */
    public DeviceHandler(Socket socket, EdgeAnalyser analyser,
                         EdgeServer server, DemoLogger demo,
                         SecurityKeys keys) {
        super("DeviceHandler-" + socket.getPort());
        this.socket   = socket;
        this.analyser = analyser;
        this.server   = server;
        this.demo     = demo;
        this.keys     = keys;
    }

    /**
     * Authenticates the device layer, then exchanges encrypted sensor
     * statuses and commands until it disconnects.
     */
    @Override
    public void run() {
        try {
            // Output stream FIRST - see the note in the class comment.
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());

            System.out.println("Edge: device layer connected from "
                    + socket.getInetAddress().getHostAddress());

            // ---- PHASE 1: mutual authentication ----------------------
            if (!authenticate()) {
                System.out.println("Edge: authentication failed, "
                        + "closing the connection.");
                return;                     // finally{} closes everything
            }
            server.deviceConnected();
            counted = true;

            // ---- PHASE 2: the secured exchange -----------------------
            Object received;
            while ((received = in.readObject()) != null) {

                if (!(received instanceof String)) {
                    System.out.println("Edge: expected encrypted data but "
                            + "received " + received.getClass().getName());
                    continue;
                }

                String cipherText = (String) received;
                SensorFactor report;
                try {
                    report = (SensorFactor)
                            CryptoUtil.decryptObject(cipherText, sessionKey);
                } catch (Exception e) {
                    // A message that does not decrypt under the session key
                    // did not come from the authenticated device layer. It
                    // is discarded: this is what stops an attacker in the
                    // middle injecting a fake SensorFactor.
                    System.out.println("Edge: a message failed to decrypt "
                            + "and was discarded.");
                    continue;
                }

                // Demonstration output: the cipher text exactly as it
                // arrived, and the plain text recovered from it.
                demo.received(cipherText, report);
                System.out.println("Edge received: " + report);

                // Show the reported reading on the edge interface.
                server.displayReport(report);

                // Decide whether a command is needed, and send it if so.
                SensorFactor command = analyser.analyse(report);
                if (command != null) {
                    send(command);
                    System.out.println("Edge sent:     " + command);
                }
            }

        } catch (EOFException e) {
            System.out.println("Edge: device layer closed the connection.");
        } catch (IOException e) {
            System.out.println("Edge: connection to device layer lost: "
                    + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("Edge: unknown class received: "
                    + e.getMessage());
        } finally {
            close();
            if (counted) {
                server.deviceDisconnected();
            }
        }
    }

    /**
     * Performs the edge layer's half of the mutual authentication:
     * specification steps 2, 3, 4 and 5.
     *
     * @return true if the device layer authenticated successfully
     * @throws IOException            if the connection fails
     * @throws ClassNotFoundException if an unknown class arrives
     */
    private boolean authenticate()
            throws IOException, ClassNotFoundException {

        Object first = in.readObject();
        if (!(first instanceof CSAuthenticator)) {
            System.out.println("Edge: the first message was not an "
                    + "authenticator - rejecting the connection.");
            return false;
        }
        CSAuthenticator fromDevice = (CSAuthenticator) first;

        try {
            // Steps 2 and 3: verify the device layer and recover the
            // verification string it sent.
            String verificationString =
                    Authenticator.verifyDeviceAuthenticator(fromDevice, keys);

            // Step 4: create the session key for this connection.
            sessionKey = CryptoUtil.generateSessionKey();

            // Step 5: reply, proving the edge layer's identity and that it
            // could read the verification string.
            CSAuthenticator reply = Authenticator.createEdgeReply(
                    keys, verificationString, sessionKey);
            out.reset();
            out.writeObject(reply);
            out.flush();

            // The demonstration output required by the assignment: the
            // verification string and the session key, each in plain text
            // and cipher text. The device layer prints the same values.
            demo.verificationString(verificationString,
                    reply.getVerficationString());
            demo.sessionKey(CryptoUtil.encodeSessionKey(sessionKey),
                    reply.getSessionKey());
            demo.authenticationComplete();

            System.out.println("Edge: the device layer is authenticated.");
            return true;

        } catch (SecurityException e) {
            // The device layer failed a check: it is not who it claims.
            System.out.println("Edge: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.out.println("Edge: authentication error: "
                    + e.getMessage());
            return false;
        }
    }

    /**
     * Encrypts one command with the session key and sends it.
     *
     * @param command the SensorFactor to send
     * @throws IOException if the connection fails
     */
    private void send(SensorFactor command) throws IOException {
        try {
            String cipherText =
                    CryptoUtil.encryptObject(command, sessionKey);

            // Demonstration output: the plain text being sent and the
            // cipher text actually transmitted.
            demo.sent(command, cipherText);

            out.reset();               // see the note in the class comment
            out.writeObject(cipherText);
            out.flush();

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("Edge: could not encrypt a command: "
                    + e.getMessage());
        }
    }

    /** Closes the streams and the socket, ignoring failures on the way out. */
    private void close() {
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
    }
}
