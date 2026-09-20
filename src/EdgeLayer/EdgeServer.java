/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: EdgeLayer/EdgeServer.java - the edge layer TCP server
 */
package EdgeLayer;

import Contract.DemoLogger;
import Contract.Protocol;
import Contract.SensorFactor;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EdgeServer
 * ----------
 * The Edge Layer of the smart farming edge computing framework: a
 * multi-threaded TCP server that receives sensor statuses from device
 * layers, analyses them, and sends actuator commands back.
 *
 * PROGRAM STRUCTURE
 * =================
 *   EdgeServer    - this class: opens the ServerSocket, accepts connections,
 *                   and creates one handler thread per connection.
 *   DeviceHandler - extends Thread; services one connected device layer.
 *   EdgeAnalyser  - decides the command for a reported sensor status.
 *   EdgeWindow    - the edge layer's monitoring interface (set separately,
 *                   so the server also runs headless for testing).
 *
 * THE ACCEPT LOOP
 * ===============
 * ServerSocket.accept() blocks until a device layer connects, then returns a
 * Socket for that connection. Because the accept loop immediately hands the
 * socket to a new DeviceHandler thread and goes back to accepting, the
 * server can serve several device layers at once and is never blocked by
 * one client's traffic. This is the thread-per-connection architecture from
 * the unit material.
 *
 * RUNNING IT
 * ==========
 *   java -jar EdgeServer.jar
 * The server prints "Server is listening on port 8888" and waits.
 */
public class EdgeServer {

    /** The shared, stateless decision logic used by every handler. */
    private final EdgeAnalyser analyser = new EdgeAnalyser();

    /**
     * The demonstration output required by the assignment. It is shared by
     * every handler thread, so that the numbering of the printed messages
     * runs across the whole server rather than restarting per connection.
     */
    private final DemoLogger demo = new DemoLogger("Edge Layer");

    /** The monitoring interface, or null when running headless. */
    private EdgeWindow window;

    /** The listening socket, kept so the server can be shut down. */
    private ServerSocket serverSocket;

    /**
     * How many device layers are connected, for the window title.
     * AtomicInteger, not a plain int: the count is changed by the device
     * handler THREADS, and ++ and -- are not atomic operations even on a
     * volatile field, so two handlers finishing at the same moment could
     * corrupt the count and leave the title bar wrong.
     */
    private final AtomicInteger connectedDevices = new AtomicInteger(0);

    /**
     * Program entry point. Starts the interface, then the server.
     */
    public static void main(String[] args) {
        EdgeServer server = new EdgeServer();

        // The interface is built on the Swing Event Dispatch Thread, and the
        // server runs on the main thread, so neither blocks the other.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                EdgeWindow w = new EdgeWindow();
                w.setDemoLogger(server.getDemoLogger());
                w.setVisible(true);
                server.setWindow(w);
            }
        });

        server.listen(Protocol.EDGE_PORT);
    }

    /** Attaches the monitoring interface to this server. */
    public void setWindow(EdgeWindow window) {
        this.window = window;
    }

    /**
     * Opens the listening socket and accepts device layer connections until
     * the server is stopped.
     *
     * @param port the port to listen on
     */
    public void listen(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server is listening on port " + port);

            while (!serverSocket.isClosed()) {
                // Blocks until a device layer connects.
                Socket socket = serverSocket.accept();

                // One thread per connection, so the accept loop is free
                // again immediately.
                DeviceHandler handler =
                        new DeviceHandler(socket, analyser, this, demo);
                handler.start();
                System.out.println("Edge: started " + handler.getName());
            }

        } catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed()) {
                System.out.println("Edge: server stopped.");
            } else {
                System.out.println("Edge: could not listen on port " + port
                        + ": " + e.getMessage());
            }
        }
    }

    /** Stops the server and releases the port. */
    public void stop() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    /* ------------------ callbacks used by DeviceHandler ---------------- */

    /**
     * Shows a reported sensor status on the monitoring interface. Called
     * from a handler thread, so the window is responsible for moving the
     * update onto the Swing Event Dispatch Thread.
     *
     * @param report the sensor status received from a device layer
     */
    public void displayReport(SensorFactor report) {
        if (window != null) {
            window.updateSensorDisplay(report);
        }
    }

    /** Records that a device layer has connected. */
    public void deviceConnected() {
        connectedDevices.incrementAndGet();
        if (window != null) {
            window.setConnected(true);
        }
    }

    /** Records that a device layer has disconnected. */
    public void deviceDisconnected() {
        int remaining = connectedDevices.decrementAndGet();
        if (window != null && remaining <= 0) {
            window.setConnected(false);
        }
    }

    /** @return the shared demonstration logger. */
    public DemoLogger getDemoLogger() {
        return demo;
    }
}
