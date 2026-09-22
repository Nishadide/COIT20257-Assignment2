/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: DeviceLayer/ActuatorController.java - one actuator thread per sensor
 */
package DeviceLayer;

import Contract.Protocol;
import javax.swing.SwingUtilities;

/**
 * ActuatorController
 * ------------------
 * Carries out the physical action of ONE sensor's actuator: while the edge
 * layer has switched that actuator on, the reading is moved one unit towards
 * the perfect value every three seconds.
 *
 * ONE THREAD PER SENSOR
 * =====================
 * Four ActuatorController threads are started, one for each sensor, so all
 * four sensors correct concurrently and independently, each on its own three
 * second cadence. This is the same thread-per-activity structure used in
 * Assignment 1, and it is what allows the four sensors to respond to the
 * edge server's commands at the same time.
 *
 * WHAT IS DECIDED HERE, AND WHAT IS NOT
 * =====================================
 * This thread does NOT decide whether a correction is needed. It acts only
 * while the sensor's actuator label shows an actuator the EDGE LAYER
 * switched on, and it stops as soon as the edge layer switches it off. The
 * device carries out the action; the edge decides that the action is needed.
 *
 * THREAD SAFETY
 * =============
 * The check isActuatorOn() reads a volatile mirror of the display, not a
 * Swing component. The step itself changes a slider, so it is handed to the
 * Swing Event Dispatch Thread with SwingUtilities.invokeLater().
 */
public class ActuatorController extends Thread {

    /** The sensor this thread is responsible for. */
    private final SensorPanel sensor;

    /**
     * True only while the device layer is connected to the edge layer. On
     * start-up no sensor is running, as required by the demonstration
     * document; the connection switches the sensors on.
     */
    private volatile boolean sensorsActive = false;

    /**
     * @param sensor the sensor whose actuator this thread operates
     */
    public ActuatorController(SensorPanel sensor) {
        super("Actuator-" + sensor.getType());
        this.sensor = sensor;
        setDaemon(true);          // ends with the application
    }

    /**
     * Switches this sensor on or off. The sensors are switched on when the
     * device layer connects to the edge layer, and off when it disconnects.
     *
     * @param active true to allow the actuator to act
     */
    public void setSensorsActive(boolean active) {
        this.sensorsActive = active;
    }

    /**
     * The control loop: every three seconds, apply one correction step if
     * the edge layer has an actuator switched on for this sensor.
     */
    @Override
    public void run() {
        try {
            while (true) {
                Thread.sleep(Protocol.ACTUATOR_INTERVAL_MS);

                if (!sensorsActive || !sensor.isActuatorOn()) {
                    continue;             // nothing to correct
                }

                // The slider is a Swing component, so the step is applied
                // on the Event Dispatch Thread.
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        sensor.stepTowardsPerfect();
                    }
                });
            }
        } catch (InterruptedException e) {
            System.out.println(getName() + " stopped.");
        }
    }
}
