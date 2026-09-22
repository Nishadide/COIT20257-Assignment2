/*
 * TEST HARNESS ONLY - NOT PART OF THE SUBMISSION.
 * Proves the cryptographic helper works on its own, before it is wired
 * into the device layer or the edge layer. Also demonstrates why an
 * attacker in the middle cannot succeed.
 */
package Security;

import Contract.CSAuthenticator;
import Contract.Protocol;
import Contract.SensorFactor;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.SecretKey;

public class CryptoTest {

    static int checks = 0;

    public static void main(String[] args) throws Exception {

        String dir = (args.length > 0) ? args[0] : "keys";

        // ---- 1. load the four stored keys ------------------------------
        PrivateKey edgePrivate = CryptoUtil.loadPrivateKey(
                dir + "/" + Protocol.EDGE_PRIVATE_KEY_FILE);
        PublicKey  edgePublic  = CryptoUtil.loadPublicKey(
                dir + "/" + Protocol.EDGE_PUBLIC_KEY_FILE);
        PrivateKey devPrivate  = CryptoUtil.loadPrivateKey(
                dir + "/" + Protocol.DEVICES_PRIVATE_KEY_FILE);
        PublicKey  devPublic   = CryptoUtil.loadPublicKey(
                dir + "/" + Protocol.DEVICES_PUBLIC_KEY_FILE);
        is("four key files load", edgePrivate != null && edgePublic != null
                && devPrivate != null && devPublic != null);
        eq("keys are RSA", "RSA", edgePrivate.getAlgorithm());

        // ---- 2. RSA round trip, both directions ------------------------
        // Private key encrypts, public key decrypts: this is how a party
        // PROVES ITS IDENTITY, because only it holds the private key.
        String signed = CryptoUtil.rsaEncrypt(
                CSAuthenticator.DEVICES_NAME, devPrivate);
        eq("username signed with the private key verifies with the public",
                CSAuthenticator.DEVICES_NAME,
                CryptoUtil.rsaDecrypt(signed, devPublic));

        // Public key encrypts, private key decrypts: this is how a SECRET
        // is sent so that only the intended recipient can read it.
        String secret = CryptoUtil.rsaEncrypt("a secret for the edge",
                edgePublic);
        eq("secret encrypted to the edge is readable by the edge",
                "a secret for the edge",
                CryptoUtil.rsaDecrypt(secret, edgePrivate));

        // ---- 3. the verification string --------------------------------
        String verification = CryptoUtil.randomVerificationString();
        eq("verification string is 128 characters",
                "128", String.valueOf(verification.length()));
        is("verification string is alphanumeric",
                verification.matches("[A-Za-z0-9]+"));
        String differentOne = CryptoUtil.randomVerificationString();
        is("two verification strings differ",
                !verification.equals(differentOne));

        // It must fit inside RSA's payload limit.
        String encVerification = CryptoUtil.rsaEncrypt(verification, edgePublic);
        eq("128-character verification string survives RSA",
                verification, CryptoUtil.rsaDecrypt(encVerification, edgePrivate));

        // ---- 4. the session key ----------------------------------------
        SecretKey session = CryptoUtil.generateSessionKey();
        eq("session key is AES", "AES", session.getAlgorithm());
        eq("session key is 128 bits (16 bytes)",
                "16", String.valueOf(session.getEncoded().length));

        // The session key travels encrypted with the DEVICE's public key,
        // so only the device layer can recover it.
        String encodedKey = CryptoUtil.encodeSessionKey(session);
        String encKey     = CryptoUtil.rsaEncrypt(encodedKey, devPublic);
        SecretKey recovered = CryptoUtil.decodeSessionKey(
                CryptoUtil.rsaDecrypt(encKey, devPrivate));
        eq("device recovers exactly the session key the edge generated",
                CryptoUtil.encodeSessionKey(session),
                CryptoUtil.encodeSessionKey(recovered));

        // ---- 5. AES on a whole SensorFactor ----------------------------
        SensorFactor report = new SensorFactor(
                SensorFactor.TEMPERATURE, 28, "Normal",
                "Temperature Control Off");
        String cipher = CryptoUtil.encryptObject(report, session);
        SensorFactor back = (SensorFactor)
                CryptoUtil.decryptObject(cipher, recovered);
        eq("SensorFactor survives encryption and decryption",
                report.toString(), back.toString());
        is("cipher text does not contain the plain text",
                !cipher.contains("Temperature"));

        // ---- 6. THE WHOLE AUTHENTICATION EXCHANGE ----------------------
        System.out.println("\n--- full mutual authentication ---");

        // Step 1: the device layer builds its authenticator.
        String v = CryptoUtil.randomVerificationString();
        CSAuthenticator toEdge = new CSAuthenticator(
                CSAuthenticator.DEVICES_NAME,
                CryptoUtil.rsaEncrypt(CSAuthenticator.DEVICES_NAME, devPrivate),
                CryptoUtil.rsaEncrypt(v, edgePublic),
                null);

        // Step 2: the edge checks the device's identity.
        eq("edge authenticates the device layer",
                CSAuthenticator.DEVICES_NAME,
                CryptoUtil.rsaDecrypt(toEdge.getCipherUserName(), devPublic));

        // Step 3: the edge reads the verification string.
        String vAtEdge = CryptoUtil.rsaDecrypt(
                toEdge.getVerficationString(), edgePrivate);
        eq("edge reads the verification string", v, vAtEdge);

        // Steps 4 and 5: the edge makes a session key and replies.
        SecretKey edgeSession = CryptoUtil.generateSessionKey();
        CSAuthenticator toDevice = new CSAuthenticator(
                CSAuthenticator.EDGE_NAME,
                CryptoUtil.rsaEncrypt(CSAuthenticator.EDGE_NAME, edgePrivate),
                CryptoUtil.aesEncrypt(vAtEdge, edgeSession),
                CryptoUtil.rsaEncrypt(
                        CryptoUtil.encodeSessionKey(edgeSession), devPublic));

        // Step 6: the device recovers the session key.
        SecretKey deviceSession = CryptoUtil.decodeSessionKey(
                CryptoUtil.rsaDecrypt(toDevice.getSessionKey(), devPrivate));
        eq("both sides now hold the same session key",
                CryptoUtil.encodeSessionKey(edgeSession),
                CryptoUtil.encodeSessionKey(deviceSession));

        // Step 7: the device checks the edge's identity.
        eq("device authenticates the edge layer",
                CSAuthenticator.EDGE_NAME,
                CryptoUtil.rsaDecrypt(toDevice.getCipherUserName(), edgePublic));

        // Step 8: the device checks the verification string came back.
        eq("verification string returns intact under the session key",
                v, CryptoUtil.aesDecrypt(
                        toDevice.getVerficationString(), deviceSession));

        System.out.println("  -> mutual authentication succeeds");

        // ---- 7. WHY A MAN IN THE MIDDLE FAILS --------------------------
        System.out.println("\n--- attacks that must fail ---");

        // The attacker has both PUBLIC keys (they are public) but neither
        // private key. It generates its own pair and tries to impersonate.
        java.security.KeyPairGenerator gen =
                java.security.KeyPairGenerator.getInstance("RSA");
        gen.initialize(Protocol.ASYMMETRIC_KEY_SIZE);
        java.security.KeyPair attacker = gen.generateKeyPair();

        // Attack 1: pretend to be the edge layer.
        failsToDecrypt("attacker cannot forge the edge's signed name",
                CryptoUtil.rsaEncrypt(CSAuthenticator.EDGE_NAME,
                        attacker.getPrivate()),
                edgePublic);

        // Attack 2: read the verification string meant for the edge.
        failsToDecrypt("attacker cannot read the verification string",
                toEdge.getVerficationString(), attacker.getPrivate());

        // Attack 3: obtain the session key.
        failsToDecrypt("attacker cannot obtain the session key",
                toDevice.getSessionKey(), attacker.getPrivate());

        // Attack 4: inject a fake SensorFactor. Without the session key the
        // attacker can only guess, and the receiver's decryption fails.
        SensorFactor fake = new SensorFactor(
                SensorFactor.TEMPERATURE, 0, "Temperature too High!",
                "Cooling On");
        SecretKey attackerKey = CryptoUtil.generateSessionKey();
        String fakeCipher = CryptoUtil.encryptObject(fake, attackerKey);
        boolean rejected;
        try {
            CryptoUtil.decryptObject(fakeCipher, deviceSession);
            rejected = false;
        } catch (Exception e) {
            rejected = true;
        }
        is("fake SensorFactor is rejected by the receiver", rejected);

        System.out.println("\nALL " + checks + " CRYPTO CHECKS PASSED");
    }

    /** Asserts that a decryption attempt fails. */
    static void failsToDecrypt(String label, String cipherText,
                               java.security.Key wrongKey) {
        try {
            CryptoUtil.rsaDecrypt(cipherText, wrongKey);
            throw new IllegalStateException("FAILED " + label
                    + ": the decryption unexpectedly succeeded");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception expected) {
            checks++;
            System.out.println("  OK  " + label);
        }
    }

    static void eq(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("FAILED " + label
                    + ": expected '" + expected + "' but got '" + actual + "'");
        }
        checks++;
        System.out.println("  OK  " + label);
    }

    static void is(String label, boolean ok) {
        if (!ok) {
            throw new IllegalStateException("FAILED " + label);
        }
        checks++;
        System.out.println("  OK  " + label);
    }
}
