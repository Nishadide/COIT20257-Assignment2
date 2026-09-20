/*
 * TEST HARNESS ONLY - NOT PART OF THE SUBMISSION.
 * Starts the real EdgeServer and connects a mock device layer to it over a
 * real TCP socket, to prove the plaintext round trip works: the device
 * reports sensor statuses, the edge analyses them and sends back commands,
 * and the device applies them.
 */
import Contract.Protocol;
import Contract.SensorFactor;
import Contract.SensorSpec;
import EdgeLayer.EdgeServer;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class RoundTripTest {

    /** The mock device layer's current state for each sensor. */
    static Map<String, SensorFactor> state = new HashMap<>();

    public static void main(String[] args) throws Exception {

        // ---- start the real edge server on a background thread ---------
        final EdgeServer server = new EdgeServer();
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                server.listen(Protocol.EDGE_PORT);
            }
        }, "EdgeServerThread");
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(600);

        // ---- the mock device layer starts every sensor at perfect -----
        for (SensorSpec spec : SensorSpec.ALL) {
            state.put(spec.getType(), new SensorFactor(
                    spec.getType(), spec.getPerfect(),
                    "Normal", spec.getActuatorOff()));
        }

        Socket socket = new Socket("localhost", Protocol.EDGE_PORT);
        // A read timeout is how the client distinguishes "the edge sent no
        // command" from "the command has not arrived yet". Note that
        // ObjectInputStream.available() CANNOT be used for this: it reports
        // 0 even when a complete object is buffered, because the object is
        // only decoded during readObject().
        socket.setSoTimeout(800);
        // Output stream FIRST, matching the server's order.
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        System.out.println("TEST: mock device layer connected\n");

        // ============ SCENARIO 1: reading inside the ideal range ========
        System.out.println("=== 1. Temperature 20 (perfect) - expect NO command ===");
        SensorFactor reply = exchange(out, in, state.get("Temperature"));
        check("no command for a perfect reading", reply == null);

        // ============ SCENARIO 2: dragged out of range ==================
        System.out.println("\n=== 2. Temperature dragged to 12 - expect alarm + Heating On ===");
        set("Temperature", 12);
        reply = exchange(out, in, state.get("Temperature"));
        check("command sent", reply != null);
        check("alarm is 'Temperature too Low!'",
                "Temperature too Low!".equals(reply.getAlarm()));
        check("actuator is 'Heating On'",
                "Heating On".equals(reply.getActuator()));
        check("value is 0 (edge never sets readings)", !reply.hasValue());
        apply(reply);

        // ============ SCENARIO 3: corrected back INTO the range =========
        // The alarm must clear while the actuator KEEPS RUNNING.
        System.out.println("\n=== 3. Corrected up to 16 (in range, not perfect) ===");
        set("Temperature", 16);
        reply = exchange(out, in, state.get("Temperature"));
        check("command sent", reply != null);
        check("alarm cleared to 'Normal'", "Normal".equals(reply.getAlarm()));
        check("actuator NOT switched off (still 'NA' = unchanged, or Heating On)",
                !"Temperature Control Off".equals(reply.getActuator()));
        apply(reply);
        check("device actuator still 'Heating On'",
                "Heating On".equals(state.get("Temperature").getActuator()));

        // ============ SCENARIO 4: reached the perfect value =============
        System.out.println("\n=== 4. Corrected to 20 (perfect) - expect actuator OFF ===");
        set("Temperature", 20);
        reply = exchange(out, in, state.get("Temperature"));
        check("command sent", reply != null);
        check("actuator switched off",
                "Temperature Control Off".equals(reply.getActuator()));
        apply(reply);

        // ============ SCENARIO 5: all four sensors at once ==============
        System.out.println("\n=== 5. All four sensors out of range simultaneously ===");
        set("Temperature", 29);   // too high -> Cooling On
        set("Humidity",    85);   // too high -> Ventilation On
        set("Moisture",    16);   // too low  -> Irrigation On
        set("Light",       42);   // too high -> Diming On

        String[] expected = {"Cooling On", "Ventilation On",
                             "Irrigation On", "Diming On"};
        int i = 0;
        for (SensorSpec spec : SensorSpec.ALL) {
            SensorFactor r = exchange(out, in, state.get(spec.getType()));
            check(spec.getType() + " commanded '" + expected[i] + "'",
                    r != null && expected[i].equals(r.getActuator()));
            if (r != null) {
                apply(r);
            }
            i++;
        }

        // ============ SCENARIO 6: no repeat command =====================
        System.out.println("\n=== 6. Re-report unchanged state - expect NO command ===");
        reply = exchange(out, in, state.get("Humidity"));
        check("edge stays quiet when nothing must change", reply == null);

        socket.close();
        Thread.sleep(300);
        System.out.println("\nALL ROUND TRIP CHECKS PASSED");
        System.exit(0);
    }

    /** Sends one report and waits briefly for a command, or null. */
    static SensorFactor exchange(ObjectOutputStream out, ObjectInputStream in,
                                 SensorFactor report) throws Exception {
        out.reset();
        out.writeObject(report);
        out.flush();
        System.out.println("  device sent: " + report);

        // The edge replies only when a command is needed, so the socket read
        // timeout set on the socket tells us when no command is coming.
        try {
            SensorFactor cmd = (SensorFactor) in.readObject();
            System.out.println("  device got : " + cmd);
            return cmd;
        } catch (java.net.SocketTimeoutException e) {
            System.out.println("  device got : (no command)");
            return null;
        }
    }

    /** Changes the mock device's reading for one sensor. */
    static void set(String type, int value) {
        state.get(type).setValue(value);
    }

    /** Applies a received command, honouring the "no new value" markers. */
    static void apply(SensorFactor cmd) {
        SensorFactor s = state.get(cmd.getType());
        if (cmd.hasValue())    { s.setValue(cmd.getValue()); }
        if (cmd.hasAlarm())    { s.setAlarm(cmd.getAlarm()); }
        if (cmd.hasActuator()) { s.setActuator(cmd.getActuator()); }
    }

    static void check(String label, boolean ok) {
        if (!ok) {
            throw new IllegalStateException("FAILED: " + label);
        }
        System.out.println("  OK  " + label);
    }
}
