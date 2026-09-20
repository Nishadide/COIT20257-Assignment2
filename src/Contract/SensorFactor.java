/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: Contract/SensorFactor.java - the shared sensor message class
 */
package Contract;

import java.io.Serializable;

/**
 * SensorFactor
 * ------------
 * The single message type exchanged between the Device Layer and the Edge
 * Layer. It is used in BOTH directions:
 *
 *   Device Layer  -> Edge Layer : a sensor status report
 *   Edge Layer    -> Device Layer : an actuator command
 *
 * DATA STRUCTURE
 * ==============
 *   type     - the type of the IoT sensor: "Temperature", "Humidity",
 *              "Moisture" or "Light". This field is ALWAYS set, because it
 *              identifies which sensor the message belongs to.
 *   value    - the current reading of the sensor.
 *   alarm    - the alarm status of the sensor.
 *   actuator - the actuator status of the sensor.
 *
 * THE "NO NEW VALUE" CONVENTION
 * =============================
 * As specified in the assignment, a field may carry "no new value":
 *
 *   value    == 0      means no new reading is provided
 *   alarm    == "NA"   means no new alarm status is provided
 *   actuator == "NA"   means no new actuator status is provided
 *
 * The constants NO_VALUE and NA below name these, and the helper methods
 * hasValue(), hasAlarm() and hasActuator() test them, so that neither layer
 * has to repeat the literal 0 and "NA" tests in its own code.
 *
 * Example from the specification:
 *   The device layer reports  {Temperature, 28, "Normal",
 *                              "Temperature Control Off"}
 *   The edge layer replies    {Temperature, 0, "Temperature too High!",
 *                              "Cooling On"}
 * The reply carries value 0, so the device layer keeps its own reading and
 * applies only the new alarm and actuator states.
 *
 * SERIALIZATION
 * =============
 * The class implements java.io.Serializable so that instances can be written
 * to an ObjectOutputStream and read from an ObjectInputStream across the TCP
 * connection. serialVersionUID is fixed so that the device layer and the edge
 * layer stay compatible even if the two sides are compiled separately.
 *
 * NOTE FOR THE TEAM: this class is part of the frozen Contract package. Every
 * other package depends on it, so any change must be agreed by the whole team
 * through the team leader.
 */
public class SensorFactor implements Serializable {

    /** Fixed so separately compiled device and edge layers stay compatible. */
    private static final long serialVersionUID = 1L;

    /** value == 0 means "no new reading is provided in this message". */
    public static final int NO_VALUE = 0;

    /** alarm or actuator == "NA" means "no new status in this message". */
    public static final String NA = "NA";

    /** The four sensor types, named so the code never mistypes them. */
    public static final String TEMPERATURE = "Temperature";
    public static final String HUMIDITY    = "Humidity";
    public static final String MOISTURE    = "Moisture";
    public static final String LIGHT       = "Light";

    private String type;      // the type of an IoT sensor
    private int    value;     // the current reading of an IoT sensor
    private String alarm;     // the alarm status of an IoT sensor
    private String actuator;  // the actuator status of an IoT sensor

    /** No-argument constructor, required for general serialization use. */
    public SensorFactor() {
        this.type     = null;
        this.value    = NO_VALUE;
        this.alarm    = NA;
        this.actuator = NA;
    }

    /**
     * Full constructor.
     *
     * @param type     the sensor type, e.g. SensorFactor.TEMPERATURE
     * @param value    the reading, or NO_VALUE (0) if none is provided
     * @param alarm    the alarm text, or NA if none is provided
     * @param actuator the actuator text, or NA if none is provided
     */
    public SensorFactor(String type, int value, String alarm, String actuator) {
        this.type     = type;
        this.value    = value;
        this.alarm    = alarm;
        this.actuator = actuator;
    }

    /* ----------------------- getters and setters --------------------- */

    public String getType()              { return type; }
    public void   setType(String type)   { this.type = type; }

    public int    getValue()             { return value; }
    public void   setValue(int value)    { this.value = value; }

    public String getAlarm()             { return alarm; }
    public void   setAlarm(String alarm) { this.alarm = alarm; }

    public String getActuator()                 { return actuator; }
    public void   setActuator(String actuator)  { this.actuator = actuator; }

    /* ------------------- "no new value" convenience ------------------ */

    /** @return true if this message carries a new sensor reading. */
    public boolean hasValue() {
        return value != NO_VALUE;
    }

    /** @return true if this message carries a new alarm status. */
    public boolean hasAlarm() {
        return alarm != null && !NA.equals(alarm);
    }

    /** @return true if this message carries a new actuator status. */
    public boolean hasActuator() {
        return actuator != null && !NA.equals(actuator);
    }

    /**
     * Printable form, used for the plain-text lines of the demonstration
     * output required by the assignment. The format matches the examples in
     * the specification.
     *
     * @return e.g. {type='Temperature', value=28, alarm='Normal',
     *              actuator='Temperature Control Off'}
     */
    @Override
    public String toString() {
        return "{type='" + type + "'"
             + ", value=" + value
             + ", alarm='" + alarm + "'"
             + ", actuator='" + actuator + "'}";
    }
}
