/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: DeviceLayer/CommandThread.java - receives and applies edge commands
 */
package DeviceLayer;

import Contract.SensorFactor;
import java.io.EOFException;
import java.io.IOException;
import java.util.Map;
import javax.swing.SwingUtilities;

/**
 * CommandThread
 * -------------
 * Receives actuator commands from the edge layer and applies them to the
 * sensors, for as long as the connection is open.
 *
 * WHY THIS IS A SEPARATE THREAD
 * =============================
 * Reading from a socket blocks until something arrives. The edge layer sends
 * a command only when a sensor's alarm or actuator has to change, which may
 * be many seconds apart or not at all, so this thread spends most of its
 * time blocked. Keeping it separate from the reporting thread means the
 * device layer can keep reporting while waiting, and keeping it off the
 * Event Dispatch Thread means the interface stays responsive: sliders can be
 * dragged at any time, including while commands are arriving.
 *
 * APPLYING A COMMAND
 * ==================
 * Commands change Swing components, so each one is handed to the Event
 * Dispatch Thread with SwingUtilities.invokeLater(). Every change to a
 * sensor's displayed state therefore happens on one single thread, which
 * keeps that state consistent without any locking.
 */
public class CommandThread extends Thread {

    /** The connection to the edge layer. */
    private final EdgeConnection connection;

    /** The sensor panels, looked up by sensor type. */
    private final Map<String, SensorPanel> sensors;

    /** Cleared when the device layer disconnects, ending the loop. */
    private volatile boolean running = true;

    /**
     * @param connection the open connection to the edge layer
     * @param sensors    the sensor panels, keyed by sensor type
     */
    public CommandThread(EdgeConnection connection,
                         Map<String, SensorPanel> sensors) {
        super("DeviceCommands");
        this.connection = connection;
        this.sensors    = sensors;
        setDaemon(true);
    }

    /** Stops the receiving loop. */
    public void stopReceiving() {
        running = false;
    }

    /**
     * Reads commands until the connection closes, applying each one to the
     * sensor it names.
     */
    @Override
    public void run() {
        try {
            while (running) {
                Object received = connection.receive();
                if (received == null) {
                    break;                       // connection closed
                }
                if (!(received instanceof SensorFactor)) {
                    System.out.println("Device: unexpected object received: "
                            + received.getClass().getName());
                    continue;
                }

                final SensorFactor command = (SensorFactor) received;
                System.out.println("Device received command: " + command);

                final SensorPanel sensor = sensors.get(command.getType());
                if (sensor == null) {
                    System.out.println("Device: command for unknown sensor '"
                            + command.getType() + "' - ignored");
                    continue;
                }

                // Swing components are touched only on the Event Dispatch
                // Thread.
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        sensor.applyCommand(command);
                    }
                });
            }
        } catch (EOFException e) {
            System.out.println("Device: the edge layer closed the connection.");
            connection.connectionLost();
        } catch (IOException e) {
            if (running) {
                System.out.println("Device: connection to the edge layer lost: "
                        + e.getMessage());
                connection.connectionLost();
            }
        } catch (ClassNotFoundException e) {
            System.out.println("Device: unknown class received: "
                    + e.getMessage());
        }
        System.out.println("Device: command thread ended.");
    }
}
