/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: Security/KeyGenerator.java - generates the four stored key files
 */
package Security;

import Contract.Protocol;
import java.io.File;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

/**
 * KeyGenerator
 * ------------
 * Generates the two RSA key pairs the framework needs and writes them to
 * the four serialized key files. This is run ONCE; the files it produces
 * are then committed to the repository and shipped with the software.
 *
 * WHY IT IS RUN ONCE, NOT AT START-UP
 * ===================================
 * The assignment assumes that "a device layer has the public key of the
 * edge layer, and the edge layer has the public key of all device layers":
 * the keys are already in place before the two layers ever talk. In a real
 * system that would be arranged with digital certificates, which the
 * assignment puts out of scope.
 *
 * It also has to be run once and shared, rather than run on each machine.
 * If every team member generated their own key pairs, the edge layer on one
 * machine could not authenticate with the device layer on another, because
 * neither would hold the other's matching public key.
 *
 * WHICH FILE GOES WHERE AT RUNTIME
 * ================================
 *      EdgeLayer folder    EdgePri.ser   EdgePub.ser   DevicesPub.ser
 *      DeviceLayer folder  DevicesPri.ser DevicesPub.ser EdgePub.ser
 *
 * Each layer holds its OWN private key and the OTHER layer's public key.
 * A private key is never copied into the other folder: that is precisely
 * the assumption the authentication relies on, and it is why an attacker
 * who sees the traffic cannot impersonate either party.
 *
 * NOTE ON REAL DEPLOYMENTS
 * ========================
 * Storing private keys in files that travel with the software, and keeping
 * them in a shared repository, is a simplification made for this
 * assignment. In a real deployment each device would be provisioned with
 * its own private key, which would never leave that device.
 *
 * RUNNING IT
 * ==========
 *      java Security.KeyGenerator [output directory]
 * The directory defaults to "keys".
 */
public class KeyGenerator {

    /** Where the key files are written when no directory is given. */
    private static final String DEFAULT_DIRECTORY = "keys";

    /**
     * Generates both key pairs and writes the four key files.
     *
     * @param args optionally, the output directory
     */
    public static void main(String[] args) {
        String directory = (args.length > 0) ? args[0] : DEFAULT_DIRECTORY;

        try {
            File folder = new File(directory);
            if (!folder.exists() && !folder.mkdirs()) {
                System.out.println("Could not create the directory: "
                        + folder.getAbsolutePath());
                return;
            }

            KeyPairGenerator generator = KeyPairGenerator.getInstance(
                    Protocol.ASYMMETRIC_ALGORITHM);
            generator.initialize(Protocol.ASYMMETRIC_KEY_SIZE);

            // The edge layer's key pair.
            KeyPair edgePair = generator.generateKeyPair();
            write(edgePair, folder,
                  Protocol.EDGE_PRIVATE_KEY_FILE,
                  Protocol.EDGE_PUBLIC_KEY_FILE, "Edge layer");

            // The device layer's key pair.
            KeyPair devicesPair = generator.generateKeyPair();
            write(devicesPair, folder,
                  Protocol.DEVICES_PRIVATE_KEY_FILE,
                  Protocol.DEVICES_PUBLIC_KEY_FILE, "Device layer");

            System.out.println();
            System.out.println("Four key files written to "
                    + folder.getAbsolutePath());
            System.out.println();
            System.out.println("Copy them into the runtime folders as:");
            System.out.println("  EdgeLayer   : "
                    + Protocol.EDGE_PRIVATE_KEY_FILE + ", "
                    + Protocol.EDGE_PUBLIC_KEY_FILE + ", "
                    + Protocol.DEVICES_PUBLIC_KEY_FILE);
            System.out.println("  DeviceLayer : "
                    + Protocol.DEVICES_PRIVATE_KEY_FILE + ", "
                    + Protocol.DEVICES_PUBLIC_KEY_FILE + ", "
                    + Protocol.EDGE_PUBLIC_KEY_FILE);

        } catch (Exception e) {
            System.out.println("Key generation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Writes one key pair to its two files.
     *
     * @param pair        the generated key pair
     * @param folder      the output directory
     * @param privateFile the private key file name
     * @param publicFile  the public key file name
     * @param label       the name of the layer, for the printed output
     * @throws Exception if a file cannot be written
     */
    private static void write(KeyPair pair, File folder,
                              String privateFile, String publicFile,
                              String label) throws Exception {
        CryptoUtil.saveKey(pair.getPrivate(),
                new File(folder, privateFile).getPath());
        CryptoUtil.saveKey(pair.getPublic(),
                new File(folder, publicFile).getPath());
        System.out.println(label + " key pair written: "
                + privateFile + ", " + publicFile);
    }
}
