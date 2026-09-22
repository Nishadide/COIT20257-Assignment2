/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: DeviceLayer/SensorPanel.java - the display of one IoT sensor
 */
package DeviceLayer;

import Contract.SensorFactor;
import Contract.SensorSpec;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * SensorPanel
 * -----------
 * The slider, alarm and actuator of ONE IoT sensor. The class is
 * instantiated four times, once for each of Temperature, Humidity, Moisture
 * and Light; all of its configuration comes from Contract.SensorSpec, so no
 * sensor value or label is written out twice.
 *
 * THE CRITICAL DIFFERENCE FROM ASSIGNMENT 1
 * =========================================
 * In Assignment 1 this class DECIDED its own alarm and actuator states: it
 * compared the slider reading against the ideal range and set the labels
 * itself. In Assignment 2 that decision belongs to the EDGE LAYER.
 *
 * This class therefore contains NO threshold logic at all. It:
 *
 *   - reports its current reading, alarm and actuator (currentStatus)
 *   - applies whatever alarm and actuator the edge layer commands
 *     (applyCommand)
 *   - moves its reading one step towards the perfect value while the edge
 *     has switched an actuator on (stepTowardsPerfect)
 *
 * Dragging a slider changes only the reading. The alarm and the actuator do
 * not change until the edge layer, having received the new reading, sends
 * back a command. That is the whole point of the edge computing model this
 * assignment implements: the device senses and acts, the edge decides.
 *
 * THREAD SAFETY
 * =============
 * Swing components may only be touched on the Event Dispatch Thread, but the
 * reporting thread needs to read this sensor's current state, and the
 * actuator thread needs to know whether an actuator is running. Rather than
 * let those threads read the components directly, the panel keeps three
 * volatile fields mirroring what is displayed:
 *
 *      currentValue, currentAlarm, currentActuator
 *
 * They are written only on the Event Dispatch Thread, whenever the display
 * changes, and read freely by the other threads. Being volatile guarantees
 * those threads always see the latest values.
 */
public class SensorPanel extends JPanel {

    /* ------------------------- display styling ------------------------ */
    private static final Font  MONO  = new Font(Font.MONOSPACED, Font.BOLD, 14);
    private static final Color BLUE  = new Color(0, 0, 205);
    private static final Color CYAN  = new Color(0, 160, 190);
    private static final Color GREEN = Color.GREEN;
    private static final Color RED   = Color.RED;

    /** The configuration of this sensor, from the shared Contract package. */
    private final SensorSpec spec;

    private final JSlider slider;
    private final JLabel  alarmLabel;
    private final JLabel  actuatorLabel;

    /* --- mirrors of the display, safe to read from any thread --------- */
    private volatile int    currentValue;
    private volatile String currentAlarm;
    private volatile String currentActuator;

    /**
     * Builds the display of one sensor.
     *
     * @param spec the sensor's configuration from Contract.SensorSpec
     */
    public SensorPanel(SensorSpec spec) {
        this.spec = spec;

        /* --- the slider: the sensor reading, starting at the perfect
               value, exactly as in Assignment 1 ------------------------- */
        slider = new JSlider(spec.getSliderMin(), spec.getSliderMax(),
                             spec.getPerfect());
        slider.setMajorTickSpacing(5);
        slider.setMinorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        slider.setForeground(BLUE);

        /* --- the alarm: fixed width and opaque, so the background paints
               and the layout does not jump when the text changes -------- */
        alarmLabel = new JLabel(SensorSpec.ALARM_NORMAL, SwingConstants.CENTER);
        alarmLabel.setOpaque(true);
        alarmLabel.setBackground(GREEN);
        alarmLabel.setFont(MONO);
        alarmLabel.setForeground(BLUE);
        int alarmW = 30 + Math.max(
                alarmLabel.getFontMetrics(MONO).stringWidth(spec.getAlarmLow()),
                alarmLabel.getFontMetrics(MONO).stringWidth(spec.getAlarmHigh()));
        Dimension alarmSize = new Dimension(alarmW, 24);
        alarmLabel.setPreferredSize(alarmSize);
        alarmLabel.setMinimumSize(alarmSize);
        alarmLabel.setMaximumSize(alarmSize);

        /* --- the actuator: natural width, nothing laid out to its right - */
        actuatorLabel = new JLabel(spec.getActuatorOff());
        actuatorLabel.setFont(MONO);
        actuatorLabel.setForeground(BLUE);

        /* --- captions ---------------------------------------------------- */
        JLabel nameLabel = new JLabel(spec.getDisplayLabel());
        nameLabel.setFont(MONO);
        nameLabel.setForeground(CYAN);
        int nameW = 45 + nameLabel.getFontMetrics(MONO)
                                  .stringWidth("Temperature (°C)");
        nameLabel.setPreferredSize(new Dimension(nameW, 24));

        JLabel alarmCaption = new JLabel("Alarm:");
        alarmCaption.setFont(MONO);
        alarmCaption.setForeground(CYAN);

        JLabel actuatorCaption = new JLabel("Actuator:");
        actuatorCaption.setFont(MONO);
        actuatorCaption.setForeground(CYAN);

        /* --- layout: two rows per sensor --------------------------------
               row 1:  <sensor name>  <slider>
               row 2:  Alarm: <alarm>  Actuator: <actuator>
               BoxLayout on row 2 never wraps the actuator onto a second
               line, whatever the platform's font metrics.               */
        setLayout(new GridLayout(2, 1, 0, 2));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        JPanel row1 = new JPanel(new BorderLayout(10, 0));
        row1.setOpaque(false);
        row1.add(nameLabel, BorderLayout.WEST);
        row1.add(slider, BorderLayout.CENTER);

        JPanel row2 = new JPanel();
        row2.setLayout(new BoxLayout(row2, BoxLayout.X_AXIS));
        row2.setOpaque(false);
        row2.add(alarmCaption);
        row2.add(Box.createHorizontalStrut(12));
        row2.add(alarmLabel);
        row2.add(Box.createHorizontalStrut(24));
        row2.add(actuatorCaption);
        row2.add(Box.createHorizontalStrut(12));
        row2.add(actuatorLabel);
        row2.add(Box.createHorizontalGlue());

        add(row1);
        add(row2);

        /* --- initialise the thread-visible mirrors ---------------------- */
        currentValue    = slider.getValue();
        currentAlarm    = alarmLabel.getText();
        currentActuator = actuatorLabel.getText();

        /* --- the slider listener MIRRORS the reading, it does not decide
               anything. Compare with Assignment 1, where this listener
               evaluated the thresholds and set the labels.              */
        slider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                currentValue = slider.getValue();
            }
        });
    }

    /* ------------------------ reporting upwards ----------------------- */

    /**
     * The status to report to the edge layer: the current reading, and the
     * alarm and actuator states currently displayed. The edge layer uses the
     * reported actuator state to tell whether a correction is already
     * running on this sensor.
     *
     * Safe to call from the reporting thread: it reads only the volatile
     * mirrors, never the Swing components.
     *
     * @return this sensor's current status as a SensorFactor
     */
    public SensorFactor currentStatus() {
        return new SensorFactor(spec.getType(), currentValue,
                                currentAlarm, currentActuator);
    }

    /* ----------------------- applying commands ------------------------ */

    /**
     * Applies a command received from the edge layer. Honours the "no new
     * value" convention of the assignment: a value of 0 leaves the reading
     * alone, and an alarm or actuator of "NA" leaves that label alone.
     *
     * MUST be called on the Event Dispatch Thread; the command thread uses
     * SwingUtilities.invokeLater to arrange that.
     *
     * @param command the SensorFactor received from the edge layer
     */
    public void applyCommand(SensorFactor command) {
        if (command.hasValue()) {
            slider.setValue(command.getValue());
            currentValue = command.getValue();
        }
        if (command.hasAlarm()) {
            alarmLabel.setText(command.getAlarm());
            // The colour follows the text the edge sent: green while the
            // edge reports "Normal", red for any alarm message. This is
            // presentation, not a decision - the device is told what to
            // display and only chooses how to display it.
            alarmLabel.setBackground(
                    SensorSpec.ALARM_NORMAL.equals(command.getAlarm())
                            ? GREEN : RED);
            currentAlarm = command.getAlarm();
        }
        if (command.hasActuator()) {
            actuatorLabel.setText(command.getActuator());
            currentActuator = command.getActuator();
        }
    }

    /* ------------------------ actuator action ------------------------- */

    /**
     * Whether the edge layer currently has an actuator switched on for this
     * sensor. Read from the actuator text the edge last commanded.
     *
     * Safe to call from the actuator thread.
     *
     * @return true while a correction is running
     */
    public boolean isActuatorOn() {
        String a = currentActuator;
        return a != null && !a.equals(spec.getActuatorOff());
    }

    /**
     * Moves the reading one unit towards the perfect value. Called by this
     * sensor's actuator thread once every three seconds while the edge layer
     * has an actuator switched on.
     *
     * Note that the device decides only the DIRECTION of the step, from the
     * perfect value in its configuration; whether to step at all is decided
     * by the edge layer, which switched the actuator on.
     *
     * MUST be called on the Event Dispatch Thread.
     */
    public void stepTowardsPerfect() {
        int value = slider.getValue();
        if (value < spec.getPerfect()) {
            slider.setValue(value + 1);
        } else if (value > spec.getPerfect()) {
            slider.setValue(value - 1);
        }
        currentValue = slider.getValue();
    }

    /**
     * Returns this sensor to its start-up state: the reading on the perfect
     * value, the alarm showing "Normal", and the actuator switched off.
     * Used when the device layer disconnects from the edge layer.
     *
     * MUST be called on the Event Dispatch Thread.
     */
    public void reset() {
        slider.setValue(spec.getPerfect());
        alarmLabel.setText(SensorSpec.ALARM_NORMAL);
        alarmLabel.setBackground(GREEN);
        actuatorLabel.setText(spec.getActuatorOff());
        currentValue    = spec.getPerfect();
        currentAlarm    = SensorSpec.ALARM_NORMAL;
        currentActuator = spec.getActuatorOff();
    }

    /** @return the configuration of this sensor. */
    public SensorSpec getSpec() {
        return spec;
    }

    /** @return the sensor type, e.g. "Temperature". */
    public String getType() {
        return spec.getType();
    }
}
