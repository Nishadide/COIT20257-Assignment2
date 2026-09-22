/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: Security/SecurityKeys.java - the keys one layer holds
 */
package Security;

import Contract.Protocol;
import java.io.File;
import java.io.FileNotFoundException;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * SecurityKeys
 * ------------
 * The pair of keys one layer needs: its OWN private key, and the OTHER
 * layer's public key. Loaded from the serialized key files that sit in the
 * layer's runtime folder.
 *
 * WHICH KEYS EACH LAYER HOLDS
 * ===========================
 *      the EDGE layer holds   EdgePri.ser    and DevicesPub.ser
 *      the DEVICE layer holds DevicesPri.ser and EdgePub.ser
 *
 * A private key never leaves its own layer. That is the assumption the
 * mutual authentication depends on: because only the edge layer holds
 * EdgePri, only the edge layer can produce E("EDGE", Private(EDGE)) and
 * only the edge layer can read something encrypted with EdgePub.
 *
 * The two factory methods below load the correct pair for each layer, so
 * neither layer has to know the file names.
 */
public class SecurityKeys {

    /** This layer's own private key, used to prove its identity. */
    private final PrivateKey ownPrivateKey;

    /** The other layer's public key, used to verify it and encrypt to it. */
    private final PublicKey  peerPublicKey;

    private SecurityKeys(PrivateKey ownPrivateKey, PublicKey peerPublicKey) {
        this.ownPrivateKey = ownPrivateKey;
        this.peerPublicKey = peerPublicKey;
    }

    /**
     * Loads the keys the EDGE layer needs: its own private key, and the
     * device layer's public key.
     *
     * @param directory the folder holding the key files
     * @return the edge layer's keys
     * @throws Exception if a key file is missing or unreadable
     */
    public static SecurityKeys forEdgeLayer(String directory)
            throws Exception {
        return new SecurityKeys(
                loadPrivate(directory, Protocol.EDGE_PRIVATE_KEY_FILE),
                loadPublic(directory, Protocol.DEVICES_PUBLIC_KEY_FILE));
    }

    /**
     * Loads the keys the DEVICE layer needs: its own private key, and the
     * edge layer's public key.
     *
     * @param directory the folder holding the key files
     * @return the device layer's keys
     * @throws Exception if a key file is missing or unreadable
     */
    public static SecurityKeys forDeviceLayer(String directory)
            throws Exception {
        return new SecurityKeys(
                loadPrivate(directory, Protocol.DEVICES_PRIVATE_KEY_FILE),
                loadPublic(directory, Protocol.EDGE_PUBLIC_KEY_FILE));
    }

    /** @return this layer's own private key. */
    public PrivateKey getOwnPrivateKey() {
        return ownPrivateKey;
    }

    /** @return the other layer's public key. */
    public PublicKey getPeerPublicKey() {
        return peerPublicKey;
    }

    /* ----------------------- loading with a clear error --------------- */

    private static PrivateKey loadPrivate(String directory, String fileName)
            throws Exception {
        return CryptoUtil.loadPrivateKey(check(directory, fileName));
    }

    private static PublicKey loadPublic(String directory, String fileName)
            throws Exception {
        return CryptoUtil.loadPublicKey(check(directory, fileName));
    }

    /**
     * Checks a key file exists before trying to read it, so a missing file
     * produces a message that says what to do rather than a bare
     * FileNotFoundException. The end user instruction tells the reader to
     * place these files in the runtime folder, and this is the error they
     * see if they have not.
     */
    private static String check(String directory, String fileName)
            throws FileNotFoundException {
        File file = new File(directory, fileName);
        if (!file.exists()) {
            throw new FileNotFoundException(
                    "The key file " + fileName + " was not found in "
                    + new File(directory).getAbsolutePath()
                    + ". Place the key files in this folder, or run "
                    + "Security.KeyGenerator to create them.");
        }
        return file.getPath();
    }
}
