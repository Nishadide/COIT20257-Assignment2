/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: Security/Authenticator.java - builds and verifies the authenticators
 */
package Security;

import Contract.CSAuthenticator;
import javax.crypto.SecretKey;

/**
 * Authenticator
 * -------------
 * Builds and verifies the two CSAuthenticator messages of the mutual
 * authentication, implementing the nine steps of the assignment
 * specification.
 *
 * WHY THERE IS NO NETWORKING HERE
 * ===============================
 * These methods only build and check messages; they never touch a socket.
 * The device layer and the edge layer send and receive the CSAuthenticator
 * objects themselves. Keeping the cryptography separate from the networking
 * means this class can be tested on its own, and means neither layer has to
 * contain any cryptographic reasoning.
 *
 * THE NINE STEPS, AND WHICH METHOD DOES EACH
 * ==========================================
 *  1. device builds {"DEVICES", E("DEVICES", Private(DEVICES)),
 *                    E(VerificationString, Public(EDGE)), null}
 *                                          -> createDeviceAuthenticator
 *  2. edge decrypts D("DEVICES", Public(DEVICES)) and compares
 *                                          -> verifyDeviceAuthenticator
 *  3. edge decrypts D(VerificationString, Private(EDGE))
 *                                          -> verifyDeviceAuthenticator
 *  4. edge creates the session key         -> CryptoUtil.generateSessionKey
 *  5. edge builds {"EDGE", E("EDGE", Private(EDGE)),
 *                  E(VerificationString, SessionKey),
 *                  E(SessionKey, Public(DEVICES))}
 *                                          -> createEdgeReply
 *  6. device decrypts D(SessionKey, Private(DEVICES))
 *                                          -> verifyEdgeReply
 *  7. device decrypts D("EDGE", Public(EDGE)) and compares
 *                                          -> verifyEdgeReply
 *  8. device decrypts D(VerificationString, SessionKey) and compares with
 *     the string it originally sent        -> verifyEdgeReply
 *  9. if 7 and 8 both succeed, the edge layer is authenticated and the
 *     session key is kept                  -> verifyEdgeReply returns it
 *
 * WHY STEP 8 IS THE ONE THAT DEFEATS AN ATTACKER
 * ==============================================
 * An attacker who has recorded earlier traffic could replay the edge
 * layer's signed name, because that value never changes. What it cannot do
 * is return the CURRENT verification string: the string is fresh and random
 * on every authentication, it was encrypted with the edge layer's public
 * key, and only the holder of the edge layer's private key can read it. An
 * attacker therefore cannot produce E(VerificationString, SessionKey) for a
 * session key the device will accept, and step 8 fails.
 */
public final class Authenticator {

    /** Prevents instantiation: every method is static. */
    private Authenticator() {
    }

    /* ===================== the device layer's side ==================== */

    /**
     * Step 1. Builds the authenticator the device layer sends first.
     *
     * The username is encrypted with the device layer's PRIVATE key, which
     * only the real device layer holds, so the edge layer can verify it
     * with the matching public key. The verification string is encrypted
     * with the edge layer's PUBLIC key, so only the real edge layer can
     * read it.
     *
     * @param keys             the device layer's keys
     * @param verificationString the fresh random string for this exchange
     * @return the authenticator to send to the edge layer
     * @throws Exception if the encryption fails
     */
    public static CSAuthenticator createDeviceAuthenticator(
            SecurityKeys keys, String verificationString) throws Exception {
        return new CSAuthenticator(
                CSAuthenticator.DEVICES_NAME,
                CryptoUtil.rsaEncrypt(CSAuthenticator.DEVICES_NAME,
                        keys.getOwnPrivateKey()),
                CryptoUtil.rsaEncrypt(verificationString,
                        keys.getPeerPublicKey()),
                null);                       // no session key yet
    }

    /**
     * Steps 6, 7, 8 and 9. Verifies the edge layer's reply and recovers the
     * session key.
     *
     * The session key is recovered first, because step 8 needs it to check
     * the verification string.
     *
     * @param reply              the authenticator received from the edge
     * @param keys               the device layer's keys
     * @param verificationString the string this device originally sent
     * @return the shared session key
     * @throws SecurityException if either comparison fails
     * @throws Exception         if the decryption fails
     */
    public static SecretKey verifyEdgeReply(CSAuthenticator reply,
            SecurityKeys keys, String verificationString) throws Exception {

        // Step 6: recover the session key with the device's private key.
        // Only the real device layer can do this.
        SecretKey sessionKey = CryptoUtil.decodeSessionKey(
                CryptoUtil.rsaDecrypt(reply.getSessionKey(),
                        keys.getOwnPrivateKey()));

        // Step 7: the edge layer's name, decrypted with its public key.
        String name = CryptoUtil.rsaDecrypt(reply.getCipherUserName(),
                keys.getPeerPublicKey());
        if (!CSAuthenticator.EDGE_NAME.equals(name)) {
            throw new SecurityException(
                    "The edge layer failed authentication: expected '"
                    + CSAuthenticator.EDGE_NAME + "' but the signed name "
                    + "decrypted to '" + name + "'.");
        }

        // Step 8: the verification string, returned under the session key.
        // Only a party that could READ the original string can return it.
        String returned = CryptoUtil.aesDecrypt(
                reply.getVerficationString(), sessionKey);
        if (!verificationString.equals(returned)) {
            throw new SecurityException(
                    "The edge layer failed authentication: the verification "
                    + "string did not come back intact.");
        }

        // Step 9: both checks passed.
        return sessionKey;
    }

    /* ====================== the edge layer's side ===================== */

    /**
     * Steps 2 and 3. Verifies the device layer's authenticator and returns
     * the verification string it contained.
     *
     * @param authenticator the authenticator received from a device layer
     * @param keys          the edge layer's keys
     * @return the verification string, for use in the reply
     * @throws SecurityException if the device layer fails authentication
     * @throws Exception         if the decryption fails
     */
    public static String verifyDeviceAuthenticator(
            CSAuthenticator authenticator, SecurityKeys keys)
            throws Exception {

        // Step 2: the device layer's name, decrypted with its public key.
        String name = CryptoUtil.rsaDecrypt(
                authenticator.getCipherUserName(), keys.getPeerPublicKey());
        if (!CSAuthenticator.DEVICES_NAME.equals(name)) {
            throw new SecurityException(
                    "The device layer failed authentication: expected '"
                    + CSAuthenticator.DEVICES_NAME + "' but the signed name "
                    + "decrypted to '" + name + "'.");
        }

        // Step 3: the verification string, readable only by the edge layer.
        return CryptoUtil.rsaDecrypt(
                authenticator.getVerficationString(),
                keys.getOwnPrivateKey());
    }

    /**
     * Step 5. Builds the edge layer's reply.
     *
     * It proves two things at once: that this really is the edge layer,
     * by signing its name with the edge private key; and that it genuinely
     * read the verification string, by returning that same string encrypted
     * under the new session key. The session key itself travels encrypted
     * with the device layer's public key, so only the device layer can
     * recover it.
     *
     * @param keys               the edge layer's keys
     * @param verificationString the string recovered from the device
     * @param sessionKey         the session key just generated
     * @return the authenticator to send back to the device layer
     * @throws Exception if the encryption fails
     */
    public static CSAuthenticator createEdgeReply(SecurityKeys keys,
            String verificationString, SecretKey sessionKey)
            throws Exception {
        return new CSAuthenticator(
                CSAuthenticator.EDGE_NAME,
                CryptoUtil.rsaEncrypt(CSAuthenticator.EDGE_NAME,
                        keys.getOwnPrivateKey()),
                CryptoUtil.aesEncrypt(verificationString, sessionKey),
                CryptoUtil.rsaEncrypt(
                        CryptoUtil.encodeSessionKey(sessionKey),
                        keys.getPeerPublicKey()));
    }
}
