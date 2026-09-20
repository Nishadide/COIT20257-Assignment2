/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: Contract/DemoLogger.java - the shared demonstration output format
 */
package Contract;

/**
 * DemoLogger
 * ----------
 * Produces the console output that the demonstration document requires from
 * BOTH the device layer and the edge layer, in one shared format.
 *
 * WHY THIS CLASS EXISTS
 * =====================
 * The demonstration document requires each side to print, with "Demo On"
 * selected, every exchanged SensorFactor in plain text and in cipher text,
 * numbered:
 *
 *     Sent in plain text 1: {type='Temperature', value=20, ...}
 *     Sent in cipher text 1: Ab3kQ...
 *     Received in cipher text 1: Ab3kQ...
 *     Received in plain text 1: {type='Temperature', value=20, ...}
 *
 * The proof that the exchange really is encrypted end to end is that the
 * cipher text PRINTED BY THE SENDER is character for character the cipher
 * text PRINTED BY THE RECEIVER. That comparison only works if both layers
 * print in the same format, so the format lives here and both layers call
 * it, rather than each writing its own println statements.
 *
 * It also prints the verification string and the session key, in plain and
 * cipher text, which the demonstration document requires from both sides
 * once the mutual authentication has completed.
 *
 * HOW TO USE IT
 * =============
 * Each layer creates one DemoLogger, and the "Demo On" menu item calls
 * setDemoOn(true). While demo mode is off, every method below returns
 * without printing, so the calls can be left in the code permanently.
 *
 * Counting is separate for sending and receiving, and stops after
 * MAX_MESSAGES of each, because the demonstration document notes that about
 * five sendings and five receivings are enough to show the exchange without
 * flooding the console.
 */
public class DemoLogger {

    /** How many sent and received messages are printed before stopping. */
    public static final int MAX_MESSAGES = 5;

    /** Which layer this logger belongs to, printed as a prefix. */
    private final String layerName;

    /** True once the Demo On menu item has been selected. */
    private volatile boolean demoOn = false;

    /** Separate counters for the sent and the received messages. */
    private int sentCount = 0;
    private int receivedCount = 0;

    /**
     * @param layerName the name of the layer using this logger, for example
     *                  "Device Layer" or "Edge Layer"
     */
    public DemoLogger(String layerName) {
        this.layerName = layerName;
    }

    /** Turns the demonstration output on or off (the Demo On menu item). */
    public void setDemoOn(boolean demoOn) {
        this.demoOn = demoOn;
        if (demoOn) {
            System.out.println();
            System.out.println("--- " + layerName
                    + ": demonstration output is on ---");
        }
    }

    /** @return true while demonstration output is on. */
    public boolean isDemoOn() {
        return demoOn;
    }

    /* ------------------ authentication demonstration ------------------ */

    /**
     * Prints the verification string in plain and cipher text. Required by
     * the demonstration document from both layers once the mutual
     * authentication has completed; the two layers must print the same
     * values.
     *
     * @param plain  the verification string as generated
     * @param cipher the same string encrypted, Base64 encoded
     */
    public void verificationString(String plain, String cipher) {
        System.out.println(layerName + " - verification string in plain text:");
        System.out.println("  " + plain);
        System.out.println(layerName + " - verification string in cipher text:");
        System.out.println("  " + cipher);
    }

    /**
     * Prints the session key in plain and cipher text. Required by the
     * demonstration document from both layers; the two layers must print
     * the same values.
     *
     * @param plain  the session key bytes, Base64 encoded
     * @param cipher the session key encrypted, Base64 encoded
     */
    public void sessionKey(String plain, String cipher) {
        System.out.println(layerName + " - session key in plain text:");
        System.out.println("  " + plain);
        System.out.println(layerName + " - session key in cipher text:");
        System.out.println("  " + cipher);
    }

    /** Prints the confirmation that mutual authentication has succeeded. */
    public void authenticationComplete() {
        System.out.println(layerName + ": the mutual authentication is done!");
    }

    /* --------------------- exchange demonstration --------------------- */

    /**
     * Prints one outgoing SensorFactor in plain and cipher text. Call this
     * immediately before writing the cipher text to the socket.
     *
     * @param factor the SensorFactor being sent
     * @param cipher the encrypted form actually transmitted, Base64 encoded
     */
    public void sent(SensorFactor factor, String cipher) {
        if (!demoOn || sentCount >= MAX_MESSAGES) {
            return;
        }
        sentCount++;
        System.out.println("Sent in plain text " + sentCount + ": " + factor);
        System.out.println("Sent in cipher text " + sentCount + ": " + cipher);
    }

    /**
     * Prints one incoming SensorFactor in cipher and plain text. Call this
     * immediately after reading and decrypting a message. The cipher text
     * printed here must be identical to the cipher text printed by the
     * sending layer for the same message.
     *
     * @param cipher the encrypted form as received, Base64 encoded
     * @param factor the SensorFactor recovered by decrypting it
     */
    public void received(String cipher, SensorFactor factor) {
        if (!demoOn || receivedCount >= MAX_MESSAGES) {
            return;
        }
        receivedCount++;
        System.out.println("Received in cipher text " + receivedCount
                + ": " + cipher);
        System.out.println("Received in plain text " + receivedCount
                + ": " + factor);
    }

    /** Resets both counters, so a new demonstration can be run. */
    public void reset() {
        sentCount = 0;
        receivedCount = 0;
    }
}
