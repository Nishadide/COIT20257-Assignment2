/*
 * COIT20257 Distributed Systems - Assignment 2
 * A Secured Edge Computing Framework (Edge Layer)
 * Team: <team name>
 * File: DeviceLayer/DeviceMain.java - the device layer main class
 */
package DeviceLayer;

import Contract.DemoLogger;
import Contract.Protocol;
import Contract.SensorSpec;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * DeviceMain
 * ----------
 * The Device Layer of the smart farming edge computing framework: four IoT
 * sensors that report their statuses to the edge layer and carry out the
 * actuator commands it sends back.
 *
 * PROGRAM STRUCTURE
 * =================
 *   DeviceMain        - this class: the window, the Edge and Security menus
 *   SensorPanel       - the display of one sensor (used four times)
 *   ActuatorController- one thread per sensor, carrying out corrections
 *   EdgeConnection    - the socket connection to the edge layer
 *   ReportingThread   - sends the four sensor statuses every three seconds
 *   CommandThread     - receives commands and applies them
 *
 * THE DIFFERENCE FROM ASSIGNMENT 1
 * ================================
 * In Assignment 1 each sensor evaluated its own reading and decided its own
 * alarm and actuator states. Here the device layer decides nothing: it
 * reports raw readings to the edge layer and displays whatever alarm and
 * actuator states the edge layer commands. The only thing it decides for
 * itself is the direction of an actuator's correction, and it does that only
 * while the edge layer has switched that actuator on.
 *
 * THE MENUS, AS SHOWN IN THE DEMONSTRATION DOCUMENT
 * =================================================
 *   Edge     > Connect      opens a dialog for host name and port
 *            > Disconnect   disabled until connected
 *   Security > Authentication  performs the mutual authentication
 *            > Demo On         prints the plain and cipher text exchange
 *
 * The title bar reports the connection state:
 *   "Device Layer: Edge Disconnected!"  and  "Device Layer: Edge Connected!"
 *
 * CONCURRENCY
 * ===========
 * Six threads run while connected: four actuator threads (one per sensor),
 * the reporting thread, and the command thread, plus the Swing Event
 * Dispatch Thread. None of them touches a Swing component directly; every
 * interface change is handed to the Event Dispatch Thread with
 * SwingUtilities.invokeLater(), so all display state changes on one thread
 * and no locking is needed.
 */
public class DeviceMain extends JFrame {

    private static final String TITLE_DISCONNECTED =
            "Device Layer: Edge Disconnected!";
    private static final String TITLE_CONNECTED =
            "Device Layer: Edge Connected!";

    private final SensorPanel[]        sensors   = new SensorPanel[4];
    private final ActuatorController[] actuators = new ActuatorController[4];

    private final DemoLogger    demo = new DemoLogger("Device Layer");
    private final EdgeConnection connection;

    private JMenuItem connectItem;
    private JMenuItem disconnectItem;
    private JMenuItem authenticationItem;
    private JMenuItem demoOnItem;

    /** Program entry point: builds the window on the Event Dispatch Thread. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new DeviceMain().setVisible(true);
            }
        });
    }

    /** Builds the device layer window. */
    public DeviceMain() {
        super(TITLE_DISCONNECTED);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        /* ----- one panel and one actuator thread per sensor ------------ */
        for (int i = 0; i < SensorSpec.ALL.length; i++) {
            sensors[i]   = new SensorPanel(SensorSpec.ALL[i]);
            actuators[i] = new ActuatorController(sensors[i]);
        }

        connection = new EdgeConnection(this, sensors, actuators, demo);

        buildMenus();

        /* ----- the dashboard banner and the four sensors --------------- */
        JLabel banner = new JLabel("Dashboard", SwingConstants.CENTER);
        banner.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        banner.setForeground(new Color(0, 0, 205));
        banner.setOpaque(true);
        banner.setBackground(new Color(210, 210, 245));
        banner.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        JPanel grid = new JPanel(new GridLayout(4, 1));
        for (SensorPanel sensor : sensors) {
            grid.add(sensor);
        }

        setLayout(new BorderLayout());
        add(banner, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);

        // pack() sizes the window to its content, so nothing is clipped on
        // any platform, font size or display scaling.
        pack();
        setSize(Math.max(getWidth() + 30, 700), Math.max(getHeight(), 560));
        setMinimumSize(getSize());
        setLocationByPlatform(true);

        /* ----- start the actuator threads ------------------------------
           They are started now but remain inactive: on start-up no sensor
           is running, as required by the demonstration document. The
           connection switches them on.                                  */
        for (ActuatorController actuator : actuators) {
            actuator.start();
        }
    }

    /** Builds the Edge and Security menus. */
    private void buildMenus() {
        JMenuBar menuBar = new JMenuBar();

        JMenu edgeMenu = new JMenu("Edge");
        connectItem = new JMenuItem("Connect");
        connectItem.addActionListener(e -> onConnect());
        disconnectItem = new JMenuItem("Disconnect");
        disconnectItem.setEnabled(false);          // until connected
        disconnectItem.addActionListener(e -> onDisconnect());
        edgeMenu.add(connectItem);
        edgeMenu.add(disconnectItem);

        JMenu securityMenu = new JMenu("Security");
        authenticationItem = new JMenuItem("Authentication");
        authenticationItem.setEnabled(false);      // until connected
        authenticationItem.addActionListener(e -> onAuthentication());
        demoOnItem = new JMenuItem("Demo On");
        // Becomes available only after the mutual authentication has been
        // performed, as shown in the demonstration document: there is no
        // encrypted exchange to demonstrate before then.
        demoOnItem.setEnabled(false);
        demoOnItem.addActionListener(e -> onDemoOn());
        securityMenu.add(authenticationItem);
        securityMenu.add(demoOnItem);

        menuBar.add(edgeMenu);
        menuBar.add(securityMenu);
        setJMenuBar(menuBar);
    }

    /* --------------------------- menu actions ------------------------- */

    /**
     * Edge > Connect: asks for the host name and port, then opens the
     * connection to the edge layer.
     */
    private void onConnect() {
        JTextField hostField = new JTextField(Protocol.DEFAULT_HOST, 14);
        JTextField portField = new JTextField(
                String.valueOf(Protocol.EDGE_PORT), 6);

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("Hostname:"));
        form.add(hostField);
        form.add(new JLabel("Port:"));
        form.add(portField);

        int choice = JOptionPane.showConfirmDialog(this, form,
                "Connect to Edge Layer", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "The port must be a number.",
                    "Connect to Edge Layer", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            connection.connect(host, port);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Could not connect to the edge layer at "
                    + host + ":" + port + "\n" + e.getMessage(),
                    "Connect to Edge Layer", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Edge > Disconnect: closes the connection and resets the sensors. */
    private void onDisconnect() {
        connection.disconnect();
    }

    /**
     * Security > Authentication: performs the mutual authentication with
     * the edge layer and establishes the shared session key.
     */
    private void onAuthentication() {
        // The CSAuthenticator exchange goes here, using the Security
        // package: build the first authenticator, send it, verify the
        // reply, and keep the session key. The call below is what runs
        // once that has succeeded.
        System.out.println("Device Layer: Authentication selected.");

        // Only after a successful authentication do the sensors begin
        // operating and reporting, as the specification requires.
        connection.authenticationComplete();

        demo.authenticationComplete();

        authenticationItem.setEnabled(false);   // done, cannot repeat
        demoOnItem.setEnabled(true);            // now there is an exchange
    }

    /**
     * Security > Demo On: switches on the numbered plain text and cipher
     * text output required by the assignment.
     */
    private void onDemoOn() {
        demo.setDemoOn(true);
        demoOnItem.setEnabled(false);
    }

    /* ----------------------- connection callbacks --------------------- */

    /**
     * Updates the window for the new connection state. Called from the
     * connection's threads, so the work is moved onto the Event Dispatch
     * Thread here.
     *
     * @param connected true when connected to the edge layer
     */
    public void setConnected(final boolean connected) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                setTitle(connected ? TITLE_CONNECTED : TITLE_DISCONNECTED);
                connectItem.setEnabled(!connected);
                disconnectItem.setEnabled(connected);
                authenticationItem.setEnabled(connected);

                if (!connected) {
                    // Returning to the start-up state: readings back on
                    // their perfect values, alarms normal, actuators off.
                    for (SensorPanel sensor : sensors) {
                        sensor.reset();
                    }
                }
            }
        });
    }

    /** @return the demonstration logger, for the security integration. */
    public DemoLogger getDemoLogger() {
        return demo;
    }

    /** @return the four sensor panels. */
    public SensorPanel[] getSensors() {
        return sensors;
    }
}
