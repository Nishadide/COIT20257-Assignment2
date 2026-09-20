/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: EdgeLayer/DeviceHandler.java - one thread per connected device layer
 */
package EdgeLayer;

import Contract.DemoLogger;
import Contract.SensorFactor;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * DeviceHandler
 * -------------
 * Services ONE connected device layer. The edge server creates a new
 * DeviceHandler for every connection it accepts, so several device layers
 * can be served at the same time, each on its own thread.
 *
 * THE CONCURRENCY OF THE EDGE SERVER
 * ==================================
 * This class extends java.lang.Thread and puts its work in run(), which is
 * the thread-per-connection architecture described in the unit material: the
 * server's accept loop never blocks on one client's traffic, because each
 * connection is serviced by its own thread. It is the same pattern as the
 * Connection class of the multi-threaded TCP server in the Week 2 and Week 4
 * lectures.
 *
 * THE STREAM ORDER MATTERS
 * ========================
 * ObjectOutputStream is created BEFORE ObjectInputStream. Constructing an
 * ObjectOutputStream writes a stream header immediately, and constructing an
 * ObjectInputStream blocks until it has read one. If both ends created their
 * input stream first, both would block forever waiting for a header that the
 * other end has not sent. The device layer therefore creates its streams in
 * the same order.
 *
 * RESETTING THE OUTPUT STREAM
 * ===========================
 * ObjectOutputStream caches the objects it has written, and sends a back
 * reference instead of the new contents if it sees the same object again.
 * Because commands are sent repeatedly for the same sensors, reset() is
 * called before each write so the receiver always gets current values rather
 * than a stale cached copy.
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

    private ObjectOutputStream out;
    private ObjectInputStream  in;

    /**
     * @param socket   the accepted connection to a device layer
     * @param analyser the shared decision logic
     * @param server   the edge server, for status callbacks
     * @param demo     the shared demonstration logger
     */
    public DeviceHandler(Socket socket, EdgeAnalyser analyser,
                         EdgeServer server, DemoLogger demo) {
        super("DeviceHandler-" + socket.getPort());
        this.socket   = socket;
        this.analyser = analyser;
        this.server   = server;
        this.demo     = demo;
    }

    /**
     * Reads reported sensor statuses from this device layer until it
     * disconnects, replying with a command whenever one is needed.
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
            server.deviceConnected();

            Object received;
            while ((received = in.readObject()) != null) {

                if (!(received instanceof SensorFactor)) {
                    System.out.println("Edge: unexpected object received: "
                            + received.getClass().getName());
                    continue;
                }

                SensorFactor report = (SensorFactor) received;

                // Demonstration output: the cipher text of the message as
                // it arrived, and the plain text recovered from it. In this
                // phase the exchange is not yet encrypted, so the marker
                // below stands in for the Base64 cipher text; when the
                // security layer is integrated, the received cipher text is
                // passed here instead and nothing else changes.
                demo.received(NOT_ENCRYPTED_YET, report);
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
            server.deviceDisconnected();
        }
    }

    /**
     * Placeholder for the cipher text in the demonstration output, used
     * while the exchange is still in plain text. Replaced by the real
     * Base64 cipher text when the security layer is integrated.
     */
    private static final String NOT_ENCRYPTED_YET =
            "(plain text - encryption not yet integrated)";

    /**
     * Sends one command to the device layer.
     *
     * @param command the SensorFactor to send
     * @throws IOException if the connection fails
     */
    private void send(SensorFactor command) throws IOException {
        // Demonstration output: the plain text being sent and the cipher
        // text actually transmitted. See the note in run().
        demo.sent(command, NOT_ENCRYPTED_YET);

        out.reset();               // see the note in the class comment
        out.writeObject(command);
        out.flush();
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
