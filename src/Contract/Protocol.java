/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: Contract/Protocol.java - constants both layers must agree on
 */
package Contract;

/**
 * Protocol
 * --------
 * Every value that the Device Layer and the Edge Layer MUST agree on, held
 * in one place so the two layers cannot drift apart. If a value below is
 * changed, both layers pick up the change from this single definition.
 *
 * WHY THIS CLASS EXISTS
 * =====================
 * The device layer and the edge layer are developed by different team
 * members and run as two separate programs. Anything both sides must agree
 * on - the port number, the cryptographic algorithms, the key file names -
 * would otherwise be written out twice, and a single mismatch (a different
 * port, a different cipher transformation) causes a failure that looks like
 * a networking fault but is really a configuration fault. Declaring them
 * once removes that whole class of integration error.
 *
 * The class is not instantiated; it only holds constants.
 */
public final class Protocol {

    /** Prevents instantiation: this class only holds constants. */
    private Protocol() {
    }

    /* --------------------------- networking -------------------------- */

    /** The port the edge server listens on, as shown in the demonstration. */
    public static final int EDGE_PORT = 8888;

    /** The default host the device layer connects to. */
    public static final String DEFAULT_HOST = "localhost";

    /* ------------------------- cryptography -------------------------- */

    /** Asymmetric algorithm used for the two key pairs. */
    public static final String ASYMMETRIC_ALGORITHM = "RSA";

    /** Asymmetric key size in bits. */
    public static final int ASYMMETRIC_KEY_SIZE = 2048;

    /**
     * Asymmetric transformation. RSA can only encrypt a small payload: with
     * a 2048 bit key and PKCS#1 padding the limit is 245 bytes. The two
     * items encrypted with RSA in this framework are the 128 character
     * verification string (128 bytes) and the AES session key (16 bytes),
     * so both fit comfortably. Whole SensorFactor objects are far larger
     * and are therefore encrypted with the symmetric session key instead.
     */
    public static final String ASYMMETRIC_TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    /** Symmetric algorithm used for the session key. */
    public static final String SYMMETRIC_ALGORITHM = "AES";

    /** Symmetric key size in bits. */
    public static final int SYMMETRIC_KEY_SIZE = 128;

    /**
     * Symmetric transformation used for every SensorFactor exchanged.
     *
     * NOTE ON THE MODE. ECB encrypts each block independently, so two
     * identical plaintext blocks produce two identical ciphertext blocks.
     * A production system would use an authenticated mode such as
     * AES/GCM/NoPadding, or at least AES/CBC/PKCS5Padding with a fresh
     * initialisation vector sent alongside each message. ECB is used here
     * because it needs no initialisation vector to be carried in the fixed
     * four fields of the CSAuthenticator defined by the assignment, and
     * because the exchanged messages are short and varied. The limitation
     * is recorded here deliberately rather than left unstated.
     */
    public static final String SYMMETRIC_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /* --------------------------- key files --------------------------- */

    /**
     * The serialized key files. As shown in the demonstration document, the
     * two layers run from separate folders, each holding the key files it
     * needs:
     *
     *   EdgeLayer folder   : EdgePri.ser, EdgePub.ser, DevicesPub.ser
     *   DeviceLayer folder : DevicesPri.ser, DevicesPub.ser, EdgePub.ser
     *
     * Each layer therefore holds its OWN private key and the OTHER layer's
     * public key, which is exactly the assumption the assignment makes.
     * Private keys are never copied between the two folders.
     */
    public static final String EDGE_PRIVATE_KEY_FILE    = "EdgePri.ser";
    public static final String EDGE_PUBLIC_KEY_FILE     = "EdgePub.ser";
    public static final String DEVICES_PRIVATE_KEY_FILE = "DevicesPri.ser";
    public static final String DEVICES_PUBLIC_KEY_FILE  = "DevicesPub.ser";

    /* --------------------------- behaviour --------------------------- */

    /** How often the device layer reports its sensor statuses, in ms. */
    public static final int REPORTING_INTERVAL_MS = 3000;

    /** How often an active actuator corrects a reading, in ms. */
    public static final int ACTUATOR_INTERVAL_MS = 3000;

    /** One step of correction, in sensor units. */
    public static final int ACTUATOR_STEP = 1;
}
