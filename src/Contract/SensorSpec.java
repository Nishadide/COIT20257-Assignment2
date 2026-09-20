/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: Contract/SensorSpec.java - the shared sensor configuration
 */
package Contract;

/**
 * SensorSpec
 * ----------
 * The configuration of one IoT sensor: its slider range, its ideal range,
 * its perfect value, and the exact alarm and actuator texts it uses. The
 * four sensors of the smart farming device layer are declared as constants
 * below and are looked up by type name.
 *
 * WHY THIS CLASS EXISTS
 * =====================
 * Both layers need this information, for different reasons:
 *
 *   The EDGE layer needs the ideal range and the perfect value to decide
 *   whether a reported reading is out of range, and it needs the alarm and
 *   actuator texts to put into the commands it sends back.
 *
 *   The DEVICE layer needs the slider range and the perfect value to build
 *   its sliders and to place them at their starting positions.
 *
 * If each layer declared these values separately, a single mistyped
 * threshold or label would make the edge layer and the device layer
 * disagree: the edge could command "Cooling On" while the device displays
 * a reading its own logic thinks is normal. Declaring them once, here,
 * makes that impossible.
 *
 * WHERE THE DECISION IS MADE
 * ==========================
 * This class holds DATA only; it contains no decision logic. Deciding what
 * command to send for a given reading is the responsibility of the edge
 * layer, as required by the assignment: the device layer reports readings
 * and obeys commands, and does not evaluate its own thresholds.
 *
 * The values are those given in the Assignment 1 specification and shown in
 * the demonstration documents of both assignments.
 */
public final class SensorSpec {

    /* ------------------- the four sensor configurations --------------- */

    public static final SensorSpec TEMPERATURE = new SensorSpec(
            SensorFactor.TEMPERATURE, "Temperature (°C)",
            10, 30, 15, 25, 20,
            "Temperature too Low!", "Temperature too High!",
            "Temperature Control Off", "Heating On", "Cooling On");

    public static final SensorSpec HUMIDITY = new SensorSpec(
            SensorFactor.HUMIDITY, "Humidity (% RH)",
            50, 90, 60, 80, 70,
            "Humidity too Low!", "Humidity too High!",
            "Humidity Control Off", "Humidifier On", "Ventilation On");

    public static final SensorSpec MOISTURE = new SensorSpec(
            SensorFactor.MOISTURE, "Moisture(% VWC)",
            10, 40, 20, 30, 25,
            "Moisture too Low!", "Moisture too High!",
            "Moisture Control Off", "Irrigation On", "Aeration On");

    public static final SensorSpec LIGHT = new SensorSpec(
            SensorFactor.LIGHT, "Light (Klux)",
            10, 50, 20, 40, 30,
            "Light too Low!", "Light too High!",
            "Light Control Off", "Brightening On", "Diming On");

    /** All four sensors, in the order they appear on the interfaces. */
    public static final SensorSpec[] ALL = {
        TEMPERATURE, HUMIDITY, MOISTURE, LIGHT
    };

    /** The alarm text shown when a reading is inside its ideal range. */
    public static final String ALARM_NORMAL = "Normal";

    /* --------------------------- the fields --------------------------- */

    private final String type;          // matches SensorFactor.getType()
    private final String displayLabel;  // the label shown on the device GUI
    private final int    sliderMin;     // lowest value of the slider
    private final int    sliderMax;     // highest value of the slider
    private final int    idealLow;      // lowest value of the ideal range
    private final int    idealHigh;     // highest value of the ideal range
    private final int    perfect;       // midpoint of the ideal range
    private final String alarmLow;      // alarm text when below ideal range
    private final String alarmHigh;     // alarm text when above ideal range
    private final String actuatorOff;   // actuator text when inactive
    private final String actuatorRaise; // actuator that raises the reading
    private final String actuatorLower; // actuator that lowers the reading

    /** Private: the four instances above are the only ones that exist. */
    private SensorSpec(String type, String displayLabel,
                       int sliderMin, int sliderMax,
                       int idealLow, int idealHigh, int perfect,
                       String alarmLow, String alarmHigh,
                       String actuatorOff, String actuatorRaise,
                       String actuatorLower) {
        this.type          = type;
        this.displayLabel  = displayLabel;
        this.sliderMin     = sliderMin;
        this.sliderMax     = sliderMax;
        this.idealLow      = idealLow;
        this.idealHigh     = idealHigh;
        this.perfect       = perfect;
        this.alarmLow      = alarmLow;
        this.alarmHigh     = alarmHigh;
        this.actuatorOff   = actuatorOff;
        this.actuatorRaise = actuatorRaise;
        this.actuatorLower = actuatorLower;
    }

    /**
     * Finds the configuration of a sensor by its type name, which is the
     * type field carried by every SensorFactor.
     *
     * @param type "Temperature", "Humidity", "Moisture" or "Light"
     * @return the matching configuration, or null if the type is unknown
     */
    public static SensorSpec forType(String type) {
        for (SensorSpec spec : ALL) {
            if (spec.type.equals(type)) {
                return spec;
            }
        }
        return null;     // unknown sensor type
    }

    /* --------------------------- accessors ---------------------------- */

    public String getType()          { return type; }
    public String getDisplayLabel()  { return displayLabel; }
    public int    getSliderMin()     { return sliderMin; }
    public int    getSliderMax()     { return sliderMax; }
    public int    getIdealLow()      { return idealLow; }
    public int    getIdealHigh()     { return idealHigh; }
    public int    getPerfect()       { return perfect; }
    public String getAlarmLow()      { return alarmLow; }
    public String getAlarmHigh()     { return alarmHigh; }
    public String getActuatorOff()   { return actuatorOff; }
    public String getActuatorRaise() { return actuatorRaise; }
    public String getActuatorLower() { return actuatorLower; }

    /* ------------------------- range questions ------------------------ */

    /** @return true if the reading lies inside the ideal range. */
    public boolean isInIdealRange(int value) {
        return value >= idealLow && value <= idealHigh;
    }

    /** @return true if the reading is below the ideal range. */
    public boolean isTooLow(int value) {
        return value < idealLow;
    }

    /** @return true if the reading is above the ideal range. */
    public boolean isTooHigh(int value) {
        return value > idealHigh;
    }

    /** @return true if the reading has reached the perfect value. */
    public boolean isAtPerfect(int value) {
        return value == perfect;
    }

    /**
     * The alarm text for a reading: "Normal" while the reading is inside
     * the ideal range, otherwise the too Low or too High text.
     *
     * @param value the reported reading
     * @return the alarm text this reading should produce
     */
    public String alarmFor(int value) {
        if (isTooLow(value)) {
            return alarmLow;
        }
        if (isTooHigh(value)) {
            return alarmHigh;
        }
        return ALARM_NORMAL;
    }

    /**
     * The actuator text needed to move a reading towards the perfect value:
     * the raising actuator below it, the lowering actuator above it, and the
     * off text once the reading has arrived.
     *
     * Note the two different thresholds required by the specification: the
     * ALARM is governed by the ideal range (alarmFor above), while the
     * ACTUATOR runs until the PERFECT value is reached. A reading that has
     * re-entered the ideal range therefore shows "Normal" while its
     * actuator is still running.
     *
     * @param value the reported reading
     * @return the actuator text for this reading
     */
    public String actuatorFor(int value) {
        if (value < perfect) {
            return actuatorRaise;
        }
        if (value > perfect) {
            return actuatorLower;
        }
        return actuatorOff;
    }
}
