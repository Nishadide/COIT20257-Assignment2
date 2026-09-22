/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: Security/CryptoUtil.java - the cryptographic helper
 */
package Security;

import Contract.Protocol;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * CryptoUtil
 * ----------
 * Every cryptographic operation the framework needs, in one place: loading
 * the stored keys, encrypting and decrypting with RSA and with AES, turning
 * objects into bytes and back, and generating the session key and the
 * verification string.
 *
 * THE TWO ALGORITHMS, AND WHY BOTH ARE NEEDED
 * ===========================================
 * RSA (asymmetric) is used for the mutual authentication, because each party
 * must prove it holds a private key that nobody else has. But RSA can only
 * encrypt a very small payload: with a 2048 bit key and PKCS#1 padding the
 * limit is 245 bytes. The two items encrypted with RSA here are the 128
 * character verification string and the 16 byte AES session key, and both
 * fit comfortably.
 *
 * AES (symmetric) is used for everything after the authentication, because
 * a serialized SensorFactor is far larger than 245 bytes and because
 * symmetric encryption is much faster. The AES key is the session key that
 * the authentication exchange established.
 *
 * EVERY CIPHER TEXT IS BASE64
 * ===========================
 * Encryption produces raw bytes, but the CSAuthenticator fields defined by
 * the assignment are Strings. Every method that returns cipher text
 * therefore Base64 encodes it, and every method that accepts cipher text
 * Base64 decodes it first. That also makes the cipher text directly
 * printable for the demonstration output the assignment requires.
 *
 * ENCRYPTING AN OBJECT
 * ====================
 * An object cannot be encrypted directly. It is first serialized to bytes
 * (toBytes), those bytes are encrypted, and the result is Base64 encoded.
 * Decryption reverses all three steps. encryptObject() and decryptObject()
 * do the whole sequence, so no caller has to remember the order.
 */
public final class CryptoUtil {

    /** The characters used in the random verification string. */
    private static final String ALPHANUMERIC =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /** A single strong random source, reused. */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Prevents instantiation: every method is static. */
    private CryptoUtil() {
    }

    /* ======================= key file handling ======================== */

    /**
     * Writes a key to a file, serialized. Java's PublicKey and PrivateKey
     * are Serializable, so the key object is stored directly; this is what
     * produces the .ser files the demonstration document shows.
     *
     * @param key  the key to store
     * @param path the file to write
     * @throws IOException if the file cannot be written
     */
    public static void saveKey(Key key, String path) throws IOException {
        ObjectOutputStream out =
                new ObjectOutputStream(new FileOutputStream(path));
        try {
            out.writeObject(key);
        } finally {
            out.close();
        }
    }

    /**
     * Reads a public key from a serialized key file.
     *
     * @param path the file to read
     * @return the public key
     * @throws IOException            if the file cannot be read
     * @throws ClassNotFoundException if the file does not hold a key
     */
    public static PublicKey loadPublicKey(String path)
            throws IOException, ClassNotFoundException {
        return (PublicKey) loadKey(path);
    }

    /**
     * Reads a private key from a serialized key file.
     *
     * @param path the file to read
     * @return the private key
     * @throws IOException            if the file cannot be read
     * @throws ClassNotFoundException if the file does not hold a key
     */
    public static PrivateKey loadPrivateKey(String path)
            throws IOException, ClassNotFoundException {
        return (PrivateKey) loadKey(path);
    }

    /** Reads any key from a serialized key file. */
    private static Object loadKey(String path)
            throws IOException, ClassNotFoundException {
        ObjectInputStream in =
                new ObjectInputStream(new FileInputStream(path));
        try {
            return in.readObject();
        } finally {
            in.close();
        }
    }

    /* ==================== asymmetric (RSA) ============================ */

    /**
     * Encrypts a short piece of text with an RSA key: the sender's private
     * key when proving identity, or the recipient's public key when sending
     * a secret only they should read.
     *
     * @param plainText the text to encrypt, at most 245 characters
     * @param key       the RSA key to encrypt with
     * @return the cipher text, Base64 encoded
     * @throws Exception if the text is too long or the key is unusable
     */
    public static String rsaEncrypt(String plainText, Key key)
            throws Exception {
        Cipher cipher = Cipher.getInstance(Protocol.ASYMMETRIC_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return Base64.getEncoder().encodeToString(
                cipher.doFinal(plainText.getBytes("UTF-8")));
    }

    /**
     * Decrypts text that was encrypted with the matching RSA key.
     *
     * @param cipherText the Base64 cipher text
     * @param key        the RSA key to decrypt with
     * @return the recovered plain text
     * @throws Exception if the cipher text or the key is wrong
     */
    public static String rsaDecrypt(String cipherText, Key key)
            throws Exception {
        Cipher cipher = Cipher.getInstance(Protocol.ASYMMETRIC_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key);
        return new String(
                cipher.doFinal(Base64.getDecoder().decode(cipherText)),
                "UTF-8");
    }

    /* ==================== symmetric (AES) ============================= */

    /**
     * Creates a new AES session key. Called by the edge layer during the
     * mutual authentication; the key is then sent to the device layer
     * encrypted with the device layer's public key.
     *
     * @return a fresh session key
     * @throws Exception if AES is unavailable
     */
    public static SecretKey generateSessionKey() throws Exception {
        KeyGenerator generator =
                KeyGenerator.getInstance(Protocol.SYMMETRIC_ALGORITHM);
        generator.init(Protocol.SYMMETRIC_KEY_SIZE);
        return generator.generateKey();
    }

    /**
     * Encrypts text with the session key.
     *
     * @param plainText the text to encrypt
     * @param sessionKey the shared session key
     * @return the cipher text, Base64 encoded
     * @throws Exception if the key is unusable
     */
    public static String aesEncrypt(String plainText, SecretKey sessionKey)
            throws Exception {
        return Base64.getEncoder().encodeToString(
                aesEncryptBytes(plainText.getBytes("UTF-8"), sessionKey));
    }

    /**
     * Decrypts text that was encrypted with the same session key.
     *
     * @param cipherText the Base64 cipher text
     * @param sessionKey the shared session key
     * @return the recovered plain text
     * @throws Exception if the cipher text or the key is wrong
     */
    public static String aesDecrypt(String cipherText, SecretKey sessionKey)
            throws Exception {
        return new String(aesDecryptBytes(
                Base64.getDecoder().decode(cipherText), sessionKey), "UTF-8");
    }

    /* ---------------- CBC with a per-message IV ---------------------- */

    /**
     * Encrypts bytes with the session key in CBC mode, generating a fresh
     * random initialisation vector and placing it in front of the cipher
     * text.
     *
     * The IV does not have to be secret, only unpredictable and different
     * for every message, so carrying it in the clear at the front of the
     * cipher text is both safe and the usual practice. It is what makes two
     * identical messages encrypt to completely different cipher texts.
     *
     * @param plain      the bytes to encrypt
     * @param sessionKey the shared session key
     * @return the IV followed by the cipher text
     * @throws Exception if the key is unusable
     */
    private static byte[] aesEncryptBytes(byte[] plain, SecretKey sessionKey)
            throws Exception {
        byte[] iv = new byte[Protocol.IV_LENGTH];
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(Protocol.SYMMETRIC_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plain);

        byte[] result = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
        return result;
    }

    /**
     * Decrypts bytes produced by aesEncryptBytes, taking the initialisation
     * vector from the front of the data.
     *
     * @param data       the IV followed by the cipher text
     * @param sessionKey the shared session key
     * @return the recovered bytes
     * @throws Exception if the data or the key is wrong
     */
    private static byte[] aesDecryptBytes(byte[] data, SecretKey sessionKey)
            throws Exception {
        if (data.length <= Protocol.IV_LENGTH) {
            throw new IllegalArgumentException(
                    "The cipher text is too short to contain an "
                    + "initialisation vector.");
        }
        byte[] iv = new byte[Protocol.IV_LENGTH];
        System.arraycopy(data, 0, iv, 0, iv.length);

        Cipher cipher = Cipher.getInstance(Protocol.SYMMETRIC_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, new IvParameterSpec(iv));
        return cipher.doFinal(data, iv.length, data.length - iv.length);
    }

    /* ================= session key as text ============================ */

    /**
     * The session key as a Base64 string. Used to send the key inside a
     * CSAuthenticator field (after encrypting it), and to print it in plain
     * text for the demonstration output.
     *
     * @param sessionKey the session key
     * @return its bytes, Base64 encoded
     */
    public static String encodeSessionKey(SecretKey sessionKey) {
        return Base64.getEncoder().encodeToString(sessionKey.getEncoded());
    }

    /**
     * Rebuilds a session key from its Base64 form.
     *
     * @param encoded the Base64 key bytes
     * @return the session key
     */
    public static SecretKey decodeSessionKey(String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        return new SecretKeySpec(bytes, Protocol.SYMMETRIC_ALGORITHM);
    }

    /* ==================== objects to cipher text ====================== */

    /**
     * Encrypts a whole object with the session key: the object is
     * serialized to bytes, the bytes are encrypted with AES, and the result
     * is Base64 encoded. This is how every SensorFactor is protected.
     *
     * AES is used rather than RSA because a serialized SensorFactor is far
     * larger than RSA's 245 byte limit.
     *
     * @param object     the object to encrypt, which must be Serializable
     * @param sessionKey the shared session key
     * @return the cipher text, Base64 encoded
     * @throws Exception if the object cannot be serialized or encrypted
     */
    public static String encryptObject(Object object, SecretKey sessionKey)
            throws Exception {
        return Base64.getEncoder().encodeToString(
                aesEncryptBytes(toBytes(object), sessionKey));
    }

    /**
     * Recovers an object encrypted with encryptObject.
     *
     * @param cipherText the Base64 cipher text
     * @param sessionKey the shared session key
     * @return the recovered object
     * @throws Exception if the cipher text or the key is wrong
     */
    public static Object decryptObject(String cipherText, SecretKey sessionKey)
            throws Exception {
        return fromBytes(aesDecryptBytes(
                Base64.getDecoder().decode(cipherText), sessionKey));
    }

    /** Serializes an object to bytes. */
    public static byte[] toBytes(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        try {
            out.writeObject(object);
            out.flush();
        } finally {
            out.close();
        }
        return bytes.toByteArray();
    }

    /** Rebuilds an object from serialized bytes. */
    public static Object fromBytes(byte[] bytes)
            throws IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes));
        try {
            return in.readObject();
        } finally {
            in.close();
        }
    }

    /* ==================== the verification string ===================== */

    /**
     * Creates the random alphanumeric verification string used in the
     * mutual authentication. Its length is the one named by the assignment,
     * 128 characters, which fits inside RSA's 245 byte limit.
     *
     * The device layer generates this string, sends it encrypted with the
     * edge layer's public key, and later checks that the string returned by
     * the edge layer matches. Only the real edge layer could have read it,
     * so a correct return proves the edge layer's identity.
     *
     * @return a fresh random verification string
     */
    public static String randomVerificationString() {
        return randomVerificationString(
                Contract.CSAuthenticator.VERIFICATION_STRING_LENGTH);
    }

    /**
     * Creates a random alphanumeric string of a given length.
     *
     * @param length how many characters
     * @return the random string
     */
    public static String randomVerificationString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(
                    RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
