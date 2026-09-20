/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: Contract/CSAuthenticator.java - the mutual authentication message
 */
package Contract;

import java.io.Serializable;

/**
 * CSAuthenticator
 * ---------------
 * The message exchanged between the Device Layer and the Edge Layer to
 * perform mutual authentication and to deliver the shared session key. One
 * instance travels in each direction, and the SAME four fields carry
 * different content depending on the direction.
 *
 * DATA STRUCTURE (as defined by the assignment specification)
 * ==========================================================
 *   PlainUserName      - a username in plain text
 *   CipherUserName     - the username encrypted by the sender's PRIVATE key
 *   VerficationString  - a random alphanumeric string (128 characters),
 *                        encrypted by the edge layer public key or by the
 *                        session key
 *   SessionKey         - the shared session key used for all communication
 *                        after the mutual authentication is completed
 *
 * NOTE: the field name "VerficationString" is spelled exactly as given in the
 * assignment specification, including its missing "i". It is kept unchanged
 * so that the implementation matches the specification.
 *
 * All four fields are String. Because the cryptographic operations produce
 * raw bytes, every encrypted field is Base64 encoded before being stored
 * here, and Base64 decoded again before being decrypted. That also makes the
 * cipher text directly printable for the demonstration output required by the
 * assignment.
 *
 * THE TWO MESSAGES
 * ================
 * 1. Device layer -> edge layer
 *      PlainUserName     = "DEVICES"
 *      CipherUserName    = E("DEVICES", Private(DEVICES))
 *      VerficationString = E(VerificationString, Public(EDGE))
 *      SessionKey        = null
 *
 * 2. Edge layer -> device layer
 *      PlainUserName     = "EDGE"
 *      CipherUserName    = E("EDGE", Private(EDGE))
 *      VerficationString = E(VerificationString, SessionKey)
 *      SessionKey        = E(SessionKey, Public(DEVICES))
 *
 * WHY THIS AUTHENTICATES BOTH PARTIES
 * ===================================
 * Only the real device layer can produce E("DEVICES", Private(DEVICES)),
 * because only it holds that private key; the edge layer checks this by
 * decrypting with the device layer public key. Only the real edge layer can
 * read the verification string, because it was encrypted with the edge layer
 * public key; the edge layer proves it did so by returning that same string
 * encrypted under the new session key. Only the real device layer can recover
 * that session key, because it arrives encrypted with the device layer public
 * key. An attacker in the middle holds neither private key, so it can neither
 * impersonate either party nor obtain the session key.
 *
 * NOTE FOR THE TEAM: this class is part of the frozen Contract package. Every
 * other package depends on it, so any change must be agreed by the whole team
 * through the team leader.
 */
public class CSAuthenticator implements Serializable {

    /** Fixed so separately compiled device and edge layers stay compatible. */
    private static final long serialVersionUID = 1L;

    /** The agreed username of the device layer. */
    public static final String DEVICES_NAME = "DEVICES";

    /** The agreed username of the edge layer. */
    public static final String EDGE_NAME = "EDGE";

    /** The required length of the random verification string. */
    public static final int VERIFICATION_STRING_LENGTH = 128;

    private String PlainUserName;      // username in plain text
    private String CipherUserName;     // username encrypted by the private key
    private String VerficationString;  // random string, encrypted (spelling per spec)
    private String SessionKey;         // the shared session key, encrypted

    /** No-argument constructor, required for general serialization use. */
    public CSAuthenticator() {
        this.PlainUserName     = null;
        this.CipherUserName    = null;
        this.VerficationString = null;
        this.SessionKey        = null;
    }

    /**
     * Full constructor.
     *
     * @param PlainUserName     the username in plain text
     * @param CipherUserName    the username encrypted by the sender private key
     * @param VerficationString the verification string, encrypted
     * @param SessionKey        the session key, encrypted, or null in the
     *                          first message of the exchange
     */
    public CSAuthenticator(String PlainUserName, String CipherUserName,
                           String VerficationString, String SessionKey) {
        this.PlainUserName     = PlainUserName;
        this.CipherUserName    = CipherUserName;
        this.VerficationString = VerficationString;
        this.SessionKey        = SessionKey;
    }

    /* ----------------------- getters and setters --------------------- */

    public String getPlainUserName() { return PlainUserName; }
    public void   setPlainUserName(String PlainUserName) {
        this.PlainUserName = PlainUserName;
    }

    public String getCipherUserName() { return CipherUserName; }
    public void   setCipherUserName(String CipherUserName) {
        this.CipherUserName = CipherUserName;
    }

    public String getVerficationString() { return VerficationString; }
    public void   setVerficationString(String VerficationString) {
        this.VerficationString = VerficationString;
    }

    public String getSessionKey() { return SessionKey; }
    public void   setSessionKey(String SessionKey) {
        this.SessionKey = SessionKey;
    }

    /**
     * Printable form for diagnostics. The encrypted fields are long Base64
     * strings, so they are shortened here; the demonstration output prints
     * the full cipher text separately where the assignment requires it.
     *
     * @return a short summary of this authenticator
     */
    @Override
    public String toString() {
        return "CSAuthenticator{PlainUserName='" + PlainUserName + "'"
             + ", CipherUserName=" + shorten(CipherUserName)
             + ", VerficationString=" + shorten(VerficationString)
             + ", SessionKey=" + shorten(SessionKey) + "}";
    }

    /** Shortens a long Base64 field for readable diagnostic output. */
    private String shorten(String s) {
        if (s == null) {
            return "null";
        }
        if (s.length() <= 24) {
            return "'" + s + "'";
        }
        return "'" + s.substring(0, 24) + "...(" + s.length() + " chars)'";
    }
}
