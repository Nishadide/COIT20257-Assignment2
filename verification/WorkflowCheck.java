import Contract.*;
import DeviceLayer.*;
import EdgeLayer.EdgeAnalyser;
import Security.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import javax.crypto.SecretKey;

/** Checks the secured interaction workflow against the specification. */
public class WorkflowCheck {
    static int n = 0;
    public static void main(String[] a) throws Exception {

        System.out.println("=== SensorFactor class definition ===");
        Class<?> c = SensorFactor.class;
        ok("implements Serializable",
                java.io.Serializable.class.isAssignableFrom(c));
        check(c, "type",     String.class);
        check(c, "value",    int.class);
        check(c, "alarm",    String.class);
        check(c, "actuator", String.class);

        System.out.println("\n=== the worked example from the specification ===");
        EdgeAnalyser analyser = new EdgeAnalyser();

        // "the device layer may send a SensorFactor: {type='Temperature',
        //  value=28, alarm='Normal', actuator='Temperature Control Off'}"
        SensorFactor report = new SensorFactor(
                "Temperature", 28, "Normal", "Temperature Control Off");
        ok("device report matches the specification's example exactly",
                ("{type='Temperature', value=28, alarm='Normal', "
                 + "actuator='Temperature Control Off'}").equals(
                        report.toString()));

        // "The edge layer may respond with a SensorFactor:
        //  {type='Temperature', value=0, alarm='Temperature too High!',
        //   actuator='Cooling On'}"
        SensorFactor reply = analyser.analyse(report);
        ok("edge replies (does not stay silent)", reply != null);
        ok("edge reply matches the specification's example exactly",
                ("{type='Temperature', value=0, "
                 + "alarm='Temperature too High!', "
                 + "actuator='Cooling On'}").equals(reply.toString()));

        System.out.println("\n=== the no-new-value convention ===");
        ok("value 0 means no new reading", !reply.hasValue());
        SensorFactor na = new SensorFactor("Light", 0, "NA", "NA");
        ok("alarm 'NA' means no new alarm",       !na.hasAlarm());
        ok("actuator 'NA' means no new actuator", !na.hasActuator());
        ok("SensorFactor.NA constant is \"NA\"",  "NA".equals(SensorFactor.NA));
        ok("SensorFactor.NO_VALUE constant is 0", SensorFactor.NO_VALUE == 0);
        // none of the four sensors can legitimately read 0, so value 0 is
        // never ambiguous
        boolean zeroImpossible = true;
        for (SensorSpec s : SensorSpec.ALL) {
            if (s.getSliderMin() <= 0) { zeroImpossible = false; }
        }
        ok("0 is outside every sensor's range, so it is never ambiguous",
                zeroImpossible);

        System.out.println("\n=== the edge sends only what changes ===");
        SensorFactor settled = new SensorFactor(
                "Temperature", 28, "Temperature too High!", "Cooling On");
        ok("no command when the device is already correct",
                analyser.analyse(settled) == null);
        SensorFactor backInRange = new SensorFactor(
                "Temperature", 16, "Temperature too Low!", "Heating On");
        SensorFactor r2 = analyser.analyse(backInRange);
        ok("alarm cleared but actuator left unchanged ('NA')",
                "Normal".equals(r2.getAlarm())
                && "NA".equals(r2.getActuator()));

        System.out.println("\n=== workflow step 1: all FOUR sensors reported ===");
        SecurityKeys dk = SecurityKeys.forDeviceLayer("keys");
        DeviceMain[] holder = new DeviceMain[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try { holder[0] = new DeviceMain(dk); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        SensorPanel[] panels = holder[0].getSensors();
        ok("device layer has four sensors", panels.length == 4);
        String[] types = {"Temperature","Humidity","Moisture","Light"};
        for (int i = 0; i < 4; i++) {
            ok("sensor " + types[i] + " reports a SensorFactor of its type",
                    types[i].equals(panels[i].currentStatus().getType()));
        }

        System.out.println("\n=== workflow step 3: the device actions commands ===");
        SensorPanel temp = panels[0];
        javax.swing.SwingUtilities.invokeAndWait(() ->
                temp.applyCommand(new SensorFactor(
                        "Temperature", 0, "Temperature too High!", "Cooling On")));
        ok("device applied the commanded alarm",
                "Temperature too High!".equals(
                        temp.currentStatus().getAlarm()));
        ok("device applied the commanded actuator",
                "Cooling On".equals(temp.currentStatus().getActuator()));
        ok("device did NOT change its reading (value was 0)",
                temp.currentStatus().getValue() == 20);
        ok("device knows its actuator is on", temp.isActuatorOn());

        javax.swing.SwingUtilities.invokeAndWait(() ->
                temp.applyCommand(new SensorFactor(
                        "Temperature", 0, "NA", "Temperature Control Off")));
        ok("'NA' alarm left the previous alarm untouched",
                "Temperature too High!".equals(
                        temp.currentStatus().getAlarm()));
        ok("device switched the actuator off when commanded",
                !temp.isActuatorOn());

        System.out.println("\n=== every SensorFactor is encrypted ===");
        SecretKey session = CryptoUtil.generateSessionKey();
        String cipher = CryptoUtil.encryptObject(report, session);
        ok("cipher text reveals no field name",
                !cipher.contains("Temperature") && !cipher.contains("Normal"));
        SensorFactor round = (SensorFactor)
                CryptoUtil.decryptObject(cipher, session);
        ok("decrypts back to the identical SensorFactor",
                report.toString().equals(round.toString()));
        SecretKey other = CryptoUtil.generateSessionKey();
        boolean rejected;
        try { CryptoUtil.decryptObject(cipher, other); rejected = false; }
        catch (Exception e) { rejected = true; }
        ok("a different session key cannot decrypt it", rejected);
        String again = CryptoUtil.encryptObject(report, session);
        ok("the same SensorFactor encrypts differently each time",
                !cipher.equals(again));

        System.out.println("\nALL " + n + " WORKFLOW CHECKS PASSED");
        System.exit(0);
    }

    static void check(Class<?> c, String name, Class<?> type) throws Exception {
        Field f = c.getDeclaredField(name);
        ok("field " + name + " is private " + type.getSimpleName(),
                f.getType() == type && Modifier.isPrivate(f.getModifiers()));
    }
    static void ok(String l, boolean b) {
        if (!b) throw new IllegalStateException("FAILED: " + l);
        n++; System.out.println("  OK  " + l);
    }
}
