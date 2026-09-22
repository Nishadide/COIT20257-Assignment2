/*
 * TEST HARNESS ONLY - NOT PART OF THE SUBMISSION.
 * Runs the REAL edge server and the REAL device layer together and drives
 * them through the scenarios of the demonstration document, verifying that
 * the edge layer decides and the device layer obeys.
 */
import Contract.Protocol;
import Contract.SensorFactor;
import Contract.SensorSpec;
import DeviceLayer.DeviceMain;
import DeviceLayer.EdgeConnection;
import DeviceLayer.SensorPanel;
import EdgeLayer.EdgeServer;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

public class IntegrationTest {

    static DeviceMain device;
    static SensorPanel[] sensors;
    static int checks = 0;

    public static void main(String[] args) throws Exception {

        // ---- start the REAL edge server --------------------------------
        final EdgeServer server = new EdgeServer();
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                server.listen(Protocol.EDGE_PORT);
            }
        }, "EdgeServerThread");
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(700);

        // ---- start the REAL device layer -------------------------------
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                device = new DeviceMain();
                device.setVisible(true);
            }
        });
        sensors = device.getSensors();
        Thread.sleep(700);

        eq("device starts disconnected",
                "Device Layer: Edge Disconnected!", title());
        shot("01_device_disconnected.png");

        // ---- connect (what Edge > Connect does) ------------------------
        System.out.println("\n=== CONNECT ===");
        EdgeConnection conn = connectionOf(device);
        conn.connect(Protocol.DEFAULT_HOST, Protocol.EDGE_PORT);
        Thread.sleep(900);

        eq("title shows connected",
                "Device Layer: Edge Connected!", title());
        is("connection reports connected", conn.isConnected());
        is("NOT yet authenticated", !conn.isAuthenticated());
        shot("02_device_connected.png");

        // Nothing must be reported before authentication: the assignment
        // requires mutual authentication before the framework's functions
        // are enabled.
        System.out.println("\n=== before authentication, nothing reports ===");
        setSlider(0, 12);
        Thread.sleep(Protocol.REPORTING_INTERVAL_MS + 1500);
        eq("no alarm arrives before authentication",
                "Normal", sensors[0].currentStatus().getAlarm());
        setSlider(0, 20);

        // ---- authenticate (what Security > Authentication does) --------
        System.out.println("\n=== AUTHENTICATE ===");
        conn.authenticationComplete();
        Thread.sleep(500);
        is("now authenticated", conn.isAuthenticated());

        // ---- every sensor starts normal --------------------------------
        for (SensorPanel s : sensors) {
            SensorFactor f = s.currentStatus();
            is(s.getType() + " starts at its perfect value",
                    f.getValue() == s.getSpec().getPerfect());
            eq(s.getType() + " starts Normal", "Normal", f.getAlarm());
            eq(s.getType() + " starts with actuator off",
                    s.getSpec().getActuatorOff(), f.getActuator());
        }

        // ---- SCENARIO: drag Temperature out of range -------------------
        // The device must NOT decide anything itself. The alarm and
        // actuator may change only after the edge layer commands them.
        System.out.println("\n=== drag Temperature to 12 ===");
        setSlider(0, 12);
        Thread.sleep(200);

        SensorFactor immediately = sensors[0].currentStatus();
        eq("device does NOT set its own alarm locally",
                "Normal", immediately.getAlarm());
        eq("device does NOT switch its own actuator on",
                "Temperature Control Off", immediately.getActuator());
        System.out.println("  (confirmed: no local decision was made)");

        // Wait for the report to go up and the command to come back.
        Thread.sleep(Protocol.REPORTING_INTERVAL_MS + 1500);

        SensorFactor afterEdge = sensors[0].currentStatus();
        eq("edge commanded the alarm",
                "Temperature too Low!", afterEdge.getAlarm());
        eq("edge commanded the actuator",
                "Heating On", afterEdge.getActuator());
        shot("03_device_alarm_from_edge.png");

        // ---- the actuator corrects the reading -------------------------
        System.out.println("\n=== actuator correcting (waiting ~12s) ===");
        int before = sensors[0].currentStatus().getValue();
        Thread.sleep(12000);
        int after = sensors[0].currentStatus().getValue();
        is("reading moved up from " + before + " to " + after,
                after > before);
        shot("04_device_correcting.png");

        // ---- alarm clears but the actuator keeps running ---------------
        // Drive the reading to just inside the ideal range and check the
        // two thresholds behave independently.
        System.out.println("\n=== reading back inside the ideal range ===");
        setSlider(0, 16);
        Thread.sleep(Protocol.REPORTING_INTERVAL_MS + 1500);

        SensorFactor inRange = sensors[0].currentStatus();
        eq("alarm cleared to Normal", "Normal", inRange.getAlarm());
        eq("actuator STILL running towards the perfect value",
                "Heating On", inRange.getActuator());
        shot("05_device_alarm_off_actuator_on.png");

        // ---- the actuator stops at the perfect value -------------------
        System.out.println("\n=== reading reaches the perfect value ===");
        setSlider(0, 20);
        Thread.sleep(Protocol.REPORTING_INTERVAL_MS + 1500);

        SensorFactor atPerfect = sensors[0].currentStatus();
        eq("actuator switched off at the perfect value",
                "Temperature Control Off", atPerfect.getActuator());
        shot("06_device_actuator_off.png");

        // ---- SCENARIO: all four sensors at once ------------------------
        System.out.println("\n=== all four sensors out of range ===");
        setSlider(0, 29);    // Temperature too high -> Cooling On
        setSlider(1, 85);    // Humidity    too high -> Ventilation On
        setSlider(2, 16);    // Moisture    too low  -> Irrigation On
        setSlider(3, 42);    // Light       too high -> Diming On
        Thread.sleep(Protocol.REPORTING_INTERVAL_MS + 2500);

        String[] expected = {"Cooling On", "Ventilation On",
                             "Irrigation On", "Diming On"};
        for (int i = 0; i < 4; i++) {
            SensorFactor f = sensors[i].currentStatus();
            eq(f.getType() + " commanded '" + expected[i] + "'",
                    expected[i], f.getActuator());
        }
        shot("07_device_all_alarming.png");

        // ---- all four correct concurrently -----------------------------
        System.out.println("\n=== all four correcting concurrently (~10s) ===");
        int[] startValues = new int[4];
        for (int i = 0; i < 4; i++) {
            startValues[i] = sensors[i].currentStatus().getValue();
        }
        Thread.sleep(10000);
        for (int i = 0; i < 4; i++) {
            int now = sensors[i].currentStatus().getValue();
            is(sensors[i].getType() + " moved " + startValues[i]
                    + " -> " + now + " (towards "
                    + sensors[i].getSpec().getPerfect() + ")",
                    now != startValues[i]);
        }
        shot("08_device_all_correcting.png");

        // ---- disconnect -------------------------------------------------
        System.out.println("\n=== DISCONNECT ===");
        conn.disconnect();
        Thread.sleep(900);
        eq("title shows disconnected",
                "Device Layer: Edge Disconnected!", title());
        for (SensorPanel s : sensors) {
            is(s.getType() + " reset to its perfect value",
                    s.currentStatus().getValue() == s.getSpec().getPerfect());
        }
        shot("09_device_after_disconnect.png");

        System.out.println("\nALL " + checks + " INTEGRATION CHECKS PASSED");
        System.exit(0);
    }

    /* ------------------------- helpers ------------------------------- */

    /** Reaches the connection the way Edge > Connect would. */
    static EdgeConnection connectionOf(DeviceMain d) throws Exception {
        Field f = DeviceMain.class.getDeclaredField("connection");
        f.setAccessible(true);
        return (EdgeConnection) f.get(d);
    }

    /** Moves a slider, as a user dragging it would. */
    static void setSlider(final int index, final int value) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                JSlider slider = findSlider(sensors[index]);
                if (slider != null) {
                    slider.setValue(value);
                }
            }
        });
    }

    static JSlider findSlider(java.awt.Container c) {
        for (java.awt.Component comp : c.getComponents()) {
            if (comp instanceof JSlider) {
                return (JSlider) comp;
            }
            if (comp instanceof java.awt.Container) {
                JSlider s = findSlider((java.awt.Container) comp);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    static String title() throws Exception {
        final String[] t = new String[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                t[0] = device.getTitle();
            }
        });
        return t[0];
    }

    static void shot(String file) throws Exception {
        final Rectangle[] r = new Rectangle[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                r[0] = device.getBounds();
            }
        });
        ImageIO.write(new Robot().createScreenCapture(r[0]), "png",
                new File("/home/claude/a2/shots2/" + file));
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
