/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: EdgeLayer/EdgeAnalyser.java - the edge layer's decision logic
 */
package EdgeLayer;

import Contract.SensorFactor;
import Contract.SensorSpec;

/**
 * EdgeAnalyser
 * ------------
 * Decides what command, if any, the edge layer should send in response to a
 * sensor status reported by the device layer.
 *
 * THIS IS WHERE THE DECISION IS MADE
 * ==================================
 * In Assignment 1 each sensor evaluated its own reading and set its own
 * alarm and actuator. In Assignment 2 that responsibility moves here, to the
 * edge layer: the device layer reports raw readings and obeys the commands
 * it receives, and does not evaluate anything itself. This class holds the
 * whole of that decision logic.
 *
 * WHY NO STATE IS KEPT HERE
 * =========================
 * Deciding whether an actuator should keep running requires knowing whether
 * one is already running. The edge layer does not have to remember that,
 * because the device layer reports its CURRENT actuator status in every
 * SensorFactor it sends. The assignment specification relies on exactly this:
 *
 *   "the device layer may send {type='Temperature', value=28,
 *    alarm='Normal', actuator='Temperature Control Off'} ... The edge layer
 *    interprets it as that the temperature (28 Celsius) is out of ideal
 *    range, but at the moment there is no any temperature control."
 *
 * The analyser is therefore a pure function of the report it is given, which
 * also means it is safe to call from several device handler threads at once
 * without synchronisation.
 *
 * THE TWO THRESHOLDS
 * ==================
 * The alarm and the actuator are governed by DIFFERENT thresholds:
 *
 *   the ALARM    is on while the reading is outside the IDEAL RANGE
 *   the ACTUATOR runs until the reading reaches the PERFECT VALUE
 *
 * So a reading that has been corrected back into the ideal range, but has
 * not yet reached the perfect value, produces alarm "Normal" while its
 * actuator is still running. Both thresholds come from Contract.SensorSpec,
 * so they are never re-declared here.
 *
 * WHEN A COMMAND IS SENT
 * ======================
 * The edge layer replies only when something needs to change. If the alarm
 * and actuator the device is already displaying are the correct ones, no
 * command is sent at all. When a command is sent, fields that do not need
 * to change carry the "no new value" markers defined by the assignment:
 * value 0 and "NA".
 */
public class EdgeAnalyser {

    /**
     * Analyses one reported sensor status and produces the command to send
     * back, or null if no command is needed.
     *
     * @param report the SensorFactor received from the device layer
     * @return the SensorFactor to send back, or null if nothing must change
     */
    public SensorFactor analyse(SensorFactor report) {

        if (report == null || report.getType() == null) {
            return null;                       // malformed report, ignore
        }

        SensorSpec spec = SensorSpec.forType(report.getType());
        if (spec == null) {
            System.out.println("Edge: unknown sensor type '"
                    + report.getType() + "' - ignored");
            return null;                       // unknown sensor, ignore
        }

        int value = report.getValue();

        /* ---- what the alarm should be: governed by the ideal range ---- */
        String requiredAlarm = spec.alarmFor(value);

        /* ---- what the actuator should be: governed by the perfect value.
           An actuator starts only when the reading is OUTSIDE the ideal
           range. Once running, it continues until the perfect value is
           reached, including while the reading is back inside the range.
           Whether one is already running is read from the device's own
           reported actuator status.                                    ---- */
        boolean actuatorRunning = isActuatorRunning(report, spec);
        String requiredActuator;

        if (!spec.isInIdealRange(value)) {
            // Outside the ideal range: the actuator must be correcting.
            requiredActuator = spec.actuatorFor(value);
        } else if (actuatorRunning && !spec.isAtPerfect(value)) {
            // Back inside the range but not yet perfect: keep correcting.
            requiredActuator = spec.actuatorFor(value);
        } else {
            // Inside the range with nothing running, or perfect value
            // reached: the actuator is off.
            requiredActuator = spec.getActuatorOff();
        }

        /* ---- send only what has to change ----------------------------- */
        boolean alarmChanges    = !requiredAlarm.equals(report.getAlarm());
        boolean actuatorChanges = !requiredActuator.equals(report.getActuator());

        if (!alarmChanges && !actuatorChanges) {
            return null;                       // device is already correct
        }

        return new SensorFactor(
                report.getType(),
                SensorFactor.NO_VALUE,         // the edge never sets readings
                alarmChanges    ? requiredAlarm    : SensorFactor.NA,
                actuatorChanges ? requiredActuator : SensorFactor.NA);
    }

    /**
     * Reads whether a correction is already running on this sensor, from the
     * actuator status the device layer reported.
     *
     * @param report the reported sensor status
     * @param spec   the configuration of that sensor
     * @return true if the device's actuator is currently on
     */
    private boolean isActuatorRunning(SensorFactor report, SensorSpec spec) {
        String reported = report.getActuator();
        if (reported == null || SensorFactor.NA.equals(reported)) {
            return false;                      // nothing reported, assume off
        }
        return !reported.equals(spec.getActuatorOff());
    }
}
