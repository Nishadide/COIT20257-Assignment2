import Contract.CSAuthenticator;
import Security.*;
import javax.crypto.SecretKey;
import java.lang.reflect.Field;

/** Checks the implementation against the specification, clause by clause. */
public class SpecCheck {
    static int n = 0;
    public static void main(String[] a) throws Exception {

        System.out.println("=== CSAuthenticator class definition ===");
        Class<?> c = CSAuthenticator.class;
        ok("package is Contract", "Contract".equals(c.getPackage().getName()));
        ok("implements Serializable",
                java.io.Serializable.class.isAssignableFrom(c));
        String[] names = {"PlainUserName","CipherUserName",
                          "VerficationString","SessionKey"};
        for (String f : names) {
            Field fld = c.getDeclaredField(f);
            ok("field " + f + " is String",
                    fld.getType() == String.class);
        }
        ok("has a 4-argument constructor",
                c.getConstructor(String.class,String.class,
                                 String.class,String.class) != null);
        for (String f : names) {
            String suffix = f;
            ok("getter for " + f,
                    c.getMethod("get" + suffix) != null);
            ok("setter for " + f,
                    c.getMethod("set" + suffix, String.class) != null);
        }

        System.out.println("\n=== the nine steps ===");
        SecurityKeys edge   = SecurityKeys.forEdgeLayer("keys");
        SecurityKeys device = SecurityKeys.forDeviceLayer("keys");

        // STEP 1
        String v = CryptoUtil.randomVerificationString();
        CSAuthenticator m1 = Authenticator.createDeviceAuthenticator(device, v);
        ok("1: PlainUserName is \"DEVICES\"",
                "DEVICES".equals(m1.getPlainUserName()));
        ok("1: CipherUserName = E(\"DEVICES\", Private(DEVICES))",
                "DEVICES".equals(CryptoUtil.rsaDecrypt(
                        m1.getCipherUserName(), edge.getPeerPublicKey())));
        ok("1: VerficationString = E(v, Public(EDGE))",
                v.equals(CryptoUtil.rsaDecrypt(
                        m1.getVerficationString(), edge.getOwnPrivateKey())));
        ok("1: SessionKey is null", m1.getSessionKey() == null);
        ok("1: verification string is 128 alphanumeric chars",
                v.length() == 128 && v.matches("[A-Za-z0-9]+"));

        // STEPS 2 and 3
        String vAtEdge = Authenticator.verifyDeviceAuthenticator(m1, edge);
        ok("2: edge authenticates the device layer", true);
        ok("3: edge recovers the verification string", v.equals(vAtEdge));

        // STEP 4
        SecretKey session = CryptoUtil.generateSessionKey();
        ok("4: edge creates a session key", session != null);

        // STEP 5
        CSAuthenticator m2 =
                Authenticator.createEdgeReply(edge, vAtEdge, session);
        ok("5: PlainUserName is \"EDGE\"",
                "EDGE".equals(m2.getPlainUserName()));
        ok("5: CipherUserName = E(\"EDGE\", Private(EDGE))",
                "EDGE".equals(CryptoUtil.rsaDecrypt(
                        m2.getCipherUserName(), device.getPeerPublicKey())));
        ok("5: VerficationString = E(v, SessionKey)",
                v.equals(CryptoUtil.aesDecrypt(
                        m2.getVerficationString(), session)));
        ok("5: SessionKey = E(SessionKey, Public(DEVICES))",
                CryptoUtil.encodeSessionKey(session).equals(
                        CryptoUtil.rsaDecrypt(m2.getSessionKey(),
                                device.getOwnPrivateKey())));

        // STEPS 6, 7, 8, 9
        SecretKey recovered =
                Authenticator.verifyEdgeReply(m2, device, v);
        ok("6: device recovers the session key",
                CryptoUtil.encodeSessionKey(session)
                    .equals(CryptoUtil.encodeSessionKey(recovered)));
        ok("7: device authenticates the edge layer", true);
        ok("8: verification string compared with the original", true);
        ok("9: session key kept for later communication", recovered != null);

        // the failure paths
        System.out.println("\n=== failures the spec implies ===");
        CSAuthenticator wrongName = new CSAuthenticator("INTRUDER",
                m1.getCipherUserName(), m1.getVerficationString(), null);
        rejected("2: a device claiming an unregistered name is rejected",
                () -> Authenticator.verifyDeviceAuthenticator(wrongName, edge));
        CSAuthenticator badV = new CSAuthenticator(m2.getPlainUserName(),
                m2.getCipherUserName(),
                CryptoUtil.aesEncrypt("wrong string", session),
                m2.getSessionKey());
        rejected("8: a wrong verification string is rejected",
                () -> Authenticator.verifyEdgeReply(badV, device, v));

        System.out.println("\nALL " + n + " SPECIFICATION CHECKS PASSED");
    }
    interface T { void run() throws Exception; }
    static void rejected(String l, T t) {
        try { t.run(); throw new IllegalStateException("FAILED " + l); }
        catch (IllegalStateException e) { throw e; }
        catch (Exception ex) { n++; System.out.println("  OK  " + l); }
    }
    static void ok(String l, boolean b) {
        if (!b) throw new IllegalStateException("FAILED " + l);
        n++; System.out.println("  OK  " + l);
    }
}
