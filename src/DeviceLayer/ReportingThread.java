/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: DeviceLayer/ReportingThread.java - reports sensor statuses upwards
 */
package DeviceLayer;

import Contract.Protocol;
import Contract.SensorFactor;
import java.io.IOException;

/**
 * ReportingThread
 * ---------------
 * Reports the statuses of all four sensors to the edge layer, once every
 * three seconds, for as long as the connection is open.
 *
 * WHY THIS IS A SEPARATE THREAD
 * =============================
 * Reporting must not run on the Swing Event Dispatch Thread, or the
 * interface would freeze for the duration of every network write. It must
 * also be separate from the thread that receives commands, because the edge
 * layer replies ONLY when a command is needed: if this thread waited for a
 * reply after each report, it would block indefinitely whenever the sensors
 * were all behaving normally and no command was due.
 *
 * So the device layer sends on this thread and receives on another, and
 * neither waits for the other.
 */
public class ReportingThread extends Thread {

    /** The connection to the edge layer. */
    private final EdgeConnection connection;

    /** The four sensors whose statuses are reported. */
    private final SensorPanel[] sensors;

    /** Cleared when the device layer disconnects, ending the loop. */
    private volatile boolean running = true;

    /**
     * @param connection the open connection to the edge layer
     * @param sensors    the four sensor panels
     */
    public ReportingThread(EdgeConnection connection, SensorPanel[] sensors) {
        super("DeviceReporting");
        this.connection = connection;
        this.sensors    = sensors;
        setDaemon(true);
    }

    /** Stops the reporting loop. */
    public void stopReporting() {
        running = false;
        interrupt();
    }

    /**
     * Sends one SensorFactor per sensor every three seconds.
     */
    @Override
    public void run() {
        try {
            while (running) {
                Thread.sleep(Protocol.REPORTING_INTERVAL_MS);
                if (!running) {
                    break;
                }

                for (SensorPanel sensor : sensors) {
                    SensorFactor status = sensor.currentStatus();
                    connection.send(status);
                }
            }
        } catch (InterruptedException e) {
            // Normal: the device layer has disconnected.
        } catch (IOException e) {
            System.out.println("Device: reporting stopped, connection lost: "
                    + e.getMessage());
            connection.connectionLost();
        }
        System.out.println("Device: reporting thread ended.");
    }
}
