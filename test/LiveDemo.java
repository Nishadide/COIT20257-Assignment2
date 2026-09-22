/*
 * TEST HARNESS ONLY - NOT PART OF THE SUBMISSION.
 * Starts the real EdgeServer with its interface, connects a mock device
 * layer, drives the four sensors through a scenario, and screenshots the
 * edge window so the interface and the live reporting can be inspected.
 */
import Contract.Protocol;
import Contract.SensorFactor;
import Contract.SensorSpec;
import EdgeLayer.EdgeServer;
import EdgeLayer.EdgeWindow;
import Contract.CSAuthenticator;
import Security.Authenticator;
import Security.CryptoUtil;
import Security.SecurityKeys;
import javax.crypto.SecretKey;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class LiveDemo {

    static Map<String, SensorFactor> state = new HashMap<>();
    static JFrame edgeFrame;
    static SecretKey sessionKey;

    public static void main(String[] args) throws Exception {

        // ---- start the real edge server, with its interface -------------
        final EdgeServer server = new EdgeServer();
        server.setKeys(SecurityKeys.forEdgeLayer("keys"));
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                EdgeWindow w = new EdgeWindow();
                w.setDemoLogger(server.getDemoLogger());
                w.setVisible(true);
                server.setWindow(w);
                edgeFrame = w;
            }
        });

        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                server.listen(Protocol.EDGE_PORT);
            }
        }, "EdgeServerThread");
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(800);

        shot("01_edge_idle.png");
        System.out.println("\n>>> captured: edge layer before any device connects\n");

        // ---- mock device layer connects ---------------------------------
        for (SensorSpec spec : SensorSpec.ALL) {
            state.put(spec.getType(), new SensorFactor(
                    spec.getType(), spec.getPerfect(),
                    "Normal", spec.getActuatorOff()));
        }

        Socket socket = new Socket("localhost", Protocol.EDGE_PORT);
        socket.setSoTimeout(900);
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        // The edge server now requires mutual authentication before it will
        // accept any sensor data, so the mock device layer performs the
        // same exchange a real device layer does.
        SecurityKeys deviceKeys = SecurityKeys.forDeviceLayer("keys");
        String verification = CryptoUtil.randomVerificationString();
        out.writeObject(Authenticator.createDeviceAuthenticator(
                deviceKeys, verification));
        out.flush();
        CSAuthenticator reply = (CSAuthenticator) in.readObject();
        sessionKey = Authenticator.verifyEdgeReply(
                reply, deviceKeys, verification);
        System.out.println(">>> mock device layer authenticated\n");
        Thread.sleep(400);

        // ---- report all four sensors at their perfect values ------------
        System.out.println(">>> device layer reports all four sensors (all normal)\n");
        for (SensorSpec spec : SensorSpec.ALL) {
            exchange(out, in, state.get(spec.getType()));
        }
        Thread.sleep(500);
        shot("02_edge_connected_normal.png");
        System.out.println("\n>>> captured: connected, all four readings displayed\n");

        // ---- drive all four out of range --------------------------------
        System.out.println(">>> all four sensors dragged out of range\n");
        set("Temperature", 29);
        set("Humidity",    85);
        set("Moisture",    16);
        set("Light",       42);
        for (SensorSpec spec : SensorSpec.ALL) {
            SensorFactor cmd = exchange(out, in, state.get(spec.getType()));
            if (cmd != null) {
                apply(cmd);
            }
        }
        Thread.sleep(500);
        shot("03_edge_all_alarming.png");
        System.out.println("\n>>> captured: all four out-of-range readings displayed\n");

        // ---- simulate the actuators correcting the readings -------------
        System.out.println(">>> actuators correcting: readings move towards perfect\n");
        for (int step = 0; step < 3; step++) {
            for (SensorSpec spec : SensorSpec.ALL) {
                SensorFactor s = state.get(spec.getType());
                int v = s.getValue();
                if (v < spec.getPerfect())      { s.setValue(v + 1); }
                else if (v > spec.getPerfect()) { s.setValue(v - 1); }
                SensorFactor cmd = exchange(out, in, s);
                if (cmd != null) {
                    apply(cmd);
                }
            }
        }
        Thread.sleep(500);
        shot("04_edge_correcting.png");
        System.out.println("\n>>> captured: readings converging towards perfect values\n");

        socket.close();
        Thread.sleep(600);
        shot("05_edge_disconnected.png");
        System.out.println("\n>>> captured: after the device layer disconnects\n");

        System.out.println("LIVE DEMO COMPLETE");
        System.exit(0);
    }

    static SensorFactor exchange(ObjectOutputStream out, ObjectInputStream in,
                                 SensorFactor report) throws Exception {
        out.reset();
        out.writeObject(CryptoUtil.encryptObject(report, sessionKey));
        out.flush();
        try {
            String cipher = (String) in.readObject();
            return (SensorFactor)
                    CryptoUtil.decryptObject(cipher, sessionKey);
        } catch (java.net.SocketTimeoutException e) {
            return null;
        }
    }

    static void set(String type, int value) {
        state.get(type).setValue(value);
    }

    static void apply(SensorFactor cmd) {
        SensorFactor s = state.get(cmd.getType());
        if (cmd.hasValue())    { s.setValue(cmd.getValue()); }
        if (cmd.hasAlarm())    { s.setAlarm(cmd.getAlarm()); }
        if (cmd.hasActuator()) { s.setActuator(cmd.getActuator()); }
    }

    static void shot(String file) throws Exception {
        final Rectangle[] r = new Rectangle[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                r[0] = edgeFrame.getBounds();
            }
        });
        BufferedImage img = new Robot().createScreenCapture(r[0]);
        ImageIO.write(img, "png", new File("/home/claude/a2/shots/" + file));
    }
}
