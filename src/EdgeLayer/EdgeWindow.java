/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: EdgeLayer/EdgeWindow.java - the edge layer monitoring interface
 */
package EdgeLayer;

import Contract.DemoLogger;
import Contract.SensorFactor;
import Contract.SensorSpec;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * EdgeWindow
 * ----------
 * The monitoring interface of the edge layer. It shows the latest reading
 * reported by each of the four sensors, arranged as four labelled value
 * boxes in a two by two grid, as shown in the demonstration document.
 *
 * The title bar reports the connection state:
 *   "Edge Layer"                                      before a device connects
 *   "Edge Layer: Devices Connected - Reporting Status!" while connected
 *
 * THREAD SAFETY
 * =============
 * updateSensorDisplay() and setConnected() are called from the device
 * handler THREADS, not from the Swing Event Dispatch Thread. Swing
 * components may only be touched on the Event Dispatch Thread, so both
 * methods wrap their work in SwingUtilities.invokeLater(). Doing it here,
 * rather than in every caller, means a handler thread cannot accidentally
 * update the interface directly.
 *
 * Note: the demonstration document states that line graphs on this interface
 * are optional. They are not implemented, because the labelled value boxes
 * satisfy the requirement.
 */
public class EdgeWindow extends JFrame {

    private static final Font  MONO  = new Font(Font.MONOSPACED, Font.BOLD, 14);
    private static final Color BLUE  = new Color(0, 0, 205);
    private static final Color CYAN  = new Color(0, 160, 190);
    private static final Color GREEN = new Color(0, 230, 0);

    private static final String TITLE_IDLE = "Edge Layer";
    private static final String TITLE_CONNECTED =
            "Edge Layer: Devices Connected - Reporting Status!";

    /** The value box of each sensor, looked up by sensor type. */
    private final Map<String, JLabel> valueLabels = new HashMap<>();

    /** The Demo On menu item, disabled once demonstration output is on. */
    private JMenuItem demoOnItem;

    /** The server's demonstration logger, attached after construction. */
    private DemoLogger demo;

    /** Builds the interface. Must be called on the Event Dispatch Thread. */
    public EdgeWindow() {
        super(TITLE_IDLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        /* ----- the menu bar: Security > Demo On --------------------- */
        JMenuBar menuBar = new JMenuBar();
        JMenu securityMenu = new JMenu("Security");
        demoOnItem = new JMenuItem("Demo On");
        demoOnItem.addActionListener(e -> onDemoOn());
        securityMenu.add(demoOnItem);
        menuBar.add(securityMenu);
        setJMenuBar(menuBar);

        /* ----- four sensor boxes in a two by two grid ---------------- */
        JPanel grid = new JPanel(new GridLayout(2, 2, 16, 16));
        grid.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        for (SensorSpec spec : SensorSpec.ALL) {
            grid.add(createSensorBox(spec));
        }

        add(grid);
        pack();
        setSize(Math.max(getWidth(), 520), Math.max(getHeight(), 260));
        setLocationByPlatform(true);
    }

    /**
     * Builds one labelled value box: the sensor's caption above, and the
     * latest reported reading below on a green field.
     *
     * @param spec the sensor this box displays
     * @return the assembled panel
     */
    private JPanel createSensorBox(SensorSpec spec) {
        JLabel caption = new JLabel(captionFor(spec));
        caption.setFont(MONO);
        caption.setForeground(CYAN);
        caption.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        JLabel value = new JLabel("--", SwingConstants.CENTER);
        value.setFont(MONO);
        value.setForeground(BLUE);
        value.setOpaque(true);                 // needed to paint the green
        value.setBackground(GREEN);
        value.setPreferredSize(new Dimension(150, 26));
        value.setMaximumSize(new Dimension(150, 26));
        value.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        valueLabels.put(spec.getType(), value);

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        box.add(caption);
        box.add(Box.createVerticalStrut(6));
        box.add(value);
        return box;
    }

    /** The caption text of a sensor box, e.g. "Temperature(C):". */
    private String captionFor(SensorSpec spec) {
        switch (spec.getType()) {
            case SensorFactor.TEMPERATURE: return "Temperature(C):";
            case SensorFactor.HUMIDITY:    return "Humidity(%RH):";
            case SensorFactor.MOISTURE:    return "Moisture(%VWC):";
            case SensorFactor.LIGHT:       return "Light(Klux):";
            default:                       return spec.getType() + ":";
        }
    }

    /**
     * Shows the latest reading of one sensor. Safe to call from any thread:
     * the update is moved onto the Event Dispatch Thread here.
     *
     * @param report the sensor status reported by a device layer
     */
    public void updateSensorDisplay(final SensorFactor report) {
        if (report == null || !report.hasValue()) {
            return;                    // no new reading in this message
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JLabel label = valueLabels.get(report.getType());
                if (label != null) {
                    label.setText(String.valueOf(report.getValue()));
                }
            }
        });
    }

    /**
     * Updates the title bar to show whether a device layer is connected.
     * Safe to call from any thread.
     *
     * @param connected true when at least one device layer is connected
     */
    public void setConnected(final boolean connected) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                setTitle(connected ? TITLE_CONNECTED : TITLE_IDLE);
                if (!connected) {
                    for (JLabel label : valueLabels.values()) {
                        label.setText("--");
                    }
                }
            }
        });
    }

    /**
     * Attaches the server's demonstration logger, so that the Demo On menu
     * item can switch the demonstration output on.
     *
     * @param demo the shared logger owned by the edge server
     */
    public void setDemoLogger(DemoLogger demo) {
        this.demo = demo;
    }

    /**
     * Handles the Security > Demo On menu item: switches on the numbered
     * plain text and cipher text output required by the assignment, and
     * disables the item so it cannot be selected twice.
     */
    private void onDemoOn() {
        if (demo != null) {
            demo.setDemoOn(true);
        }
        demoOnItem.setEnabled(false);
    }
}
