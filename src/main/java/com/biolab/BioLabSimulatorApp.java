package com.biolab;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application window for the Bio-Lab Evolution Simulator.
 *
 * <p>Uses FlatLaf's native window decoration with {@code fullWindowContent} mode.
 * FlatLaf provides the native OS frame (resize handles, Aero Snap, drop shadow,
 * rounded corners on Windows 11) while hiding its own title/icon/buttons.
 * Our {@link CustomHeaderPanel} is placed inside the title bar area using
 * {@code FlatLaf.fullWindowContent} and {@code FlatLaf.titleBarCaption}
 * client properties – the same approach IntelliJ IDEA uses.</p>
 *
 * <p>Delegates overlay management to {@link OverlayManager} and simulation
 * loop control to {@link SimulationLoopController}.</p>
 */
public class BioLabSimulatorApp extends JFrame implements SimulationCanvas.SelectionListener {
    private static final Logger LOGGER = Logger.getLogger(BioLabSimulatorApp.class.getName());

    private static final int INITIAL_POPULATION = 1500;
    private static final int CUSTOM_HEADER_HEIGHT = 65;
    private static final int WORLD_SIZE = 10_000;

    private final SettingsManager settingsManager;
    private final SimulationEngine engine;
    private final SimulationCanvas canvas;
    private final CustomHeaderPanel headerPanel;
    private final OverlayManager overlayManager;
    private final SimulationLoopController loopController;

    private volatile Microbe selectedMicrobe;
    private SettingsOverlay settingsOverlay;

    private int windowWidth;
    private int windowHeight;

    /**
     * Constructs and fully initialises the application window:
     * engine, canvas, header, overlays, loop controller, and display mode.
     */
    public BioLabSimulatorApp() {
        super("Bio-Lab Evolution Simulator");

        settingsManager = new SettingsManager();
        windowWidth = settingsManager.getWindowWidth();
        windowHeight = settingsManager.getWindowHeight();

        engine = new SimulationEngine(WORLD_SIZE, WORLD_SIZE, INITIAL_POPULATION);

        canvas = new SimulationCanvas(WORLD_SIZE, WORLD_SIZE, windowWidth,
                windowHeight - CUSTOM_HEADER_HEIGHT, engine, this);

        headerPanel = new CustomHeaderPanel(windowWidth, CUSTOM_HEADER_HEIGHT,
                this::showSettingsOverlay,
                this::minimizeWindow,
                this::toggleMaximize,
                this::closeApplication,
                () -> (getExtendedState() & Frame.MAXIMIZED_BOTH) != 0);

        InspectorPanel inspectorPanel = new InspectorPanel();
        EnvironmentPanel environmentPanel = new EnvironmentPanel(engine);
        ModernButton envToggleButton = new ModernButton("", ModernButton.ButtonIcon.ENVIRONMENT);
        ModernButton speedButton = new ModernButton("1x", ModernButton.ButtonIcon.SPEED_UP);

        overlayManager = new OverlayManager(CUSTOM_HEADER_HEIGHT, this::getLayeredPane,
                inspectorPanel, environmentPanel, envToggleButton, speedButton);

        loopController = new SimulationLoopController(engine, canvas, overlayManager,
                this::checkDeadSelectedMicrobe);

        envToggleButton.addActionListener(e -> overlayManager.toggleEnvironmentPanel());
        speedButton.addActionListener(e -> speedButton.setDisplayText(loopController.cycleSpeed()));

        inspectorPanel.setVisible(false);
        environmentPanel.setVisible(false);

        setupUI();
        setupShutdownHook();
        applyDisplayMode();
        loopController.start();
    }

    /**
     * Application entry point – bootstraps the Swing UI on the EDT.
     */
    public static void main(String[] args) {
        // Enable FlatLaf's native window decorations (provides OS frame, resize,
        // Aero Snap, shadow, rounded corners). This replaces setUndecorated(true).
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        FlatDarkLaf.setup();

        SwingUtilities.invokeLater(() -> {
            try {
                BioLabSimulatorApp app = new BioLabSimulatorApp();
                app.setVisible(true);
                LOGGER.info("Bio-Lab Simulator started successfully");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to start Bio-Lab Simulator", e);
                JOptionPane.showMessageDialog(null,
                        "Failed to start: " + e.getMessage(),
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    // -------------------------------------------------------------------------
    // UI setup
    // -------------------------------------------------------------------------

    private void setupUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(new Color(18, 18, 18));
        setMinimumSize(new Dimension(800, 600));
        setSize(windowWidth, windowHeight);
        setResizable(true);

        // FlatLaf fullWindowContent: our content extends into the title bar area.
        // FlatLaf still provides the native OS frame (resize, snap, shadow).
        getRootPane().putClientProperty("FlatLaf.fullWindowContent", true);

        // Hide FlatLaf's own title bar buttons (we have our own in CustomHeaderPanel)
        getRootPane().putClientProperty("JRootPane.titleBarShowClose", false);
        getRootPane().putClientProperty("JRootPane.titleBarShowMaximize", false);
        getRootPane().putClientProperty("JRootPane.titleBarShowMinimize", false);
        getRootPane().putClientProperty("JRootPane.titleBarShowTitle", false);
        getRootPane().putClientProperty("JRootPane.titleBarShowIcon", false);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(new Color(18, 18, 18));
        content.add(headerPanel, BorderLayout.NORTH);
        content.add(canvas, BorderLayout.CENTER);
        setContentPane(content);

        setLocationRelativeTo(null);
        overlayManager.repositionAllOverlays();

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                overlayManager.repositionAllOverlays();
                if (settingsOverlay != null) {
                    settingsOverlay.setBounds(0, 0, getWidth(), getHeight());
                }
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                overlayManager.repositionAllOverlays();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Display mode
    // -------------------------------------------------------------------------

    /**
     * Switches between fullscreen exclusive mode and normal windowed mode.
     * FlatLaf handles native frame decorations in both modes.
     */
    private void applyDisplayMode() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (settingsManager.isFullscreen() && gd.isFullScreenSupported()) {
            dispose();
            gd.setFullScreenWindow(this);
            setVisible(true);
        } else {
            if (gd.getFullScreenWindow() == this) gd.setFullScreenWindow(null);
            dispose();
            setSize(windowWidth, windowHeight);
            setLocationRelativeTo(null);
            setVisible(true);
        }

        SwingUtilities.invokeLater(overlayManager::repositionAllOverlays);
    }

    // -------------------------------------------------------------------------
    // Window controls
    // -------------------------------------------------------------------------

    private void closeApplication() {
        loopController.stop();
        if (engine.isRunning()) engine.shutdown();
        dispose();
    }

    private void minimizeWindow() {
        setState(Frame.ICONIFIED);
    }

    private void toggleMaximize() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if ((getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
            setExtendedState(Frame.NORMAL);
            setSize(windowWidth, windowHeight);
            setLocationRelativeTo(null);
        } else {
            setMaximizedBounds(ge.getMaximumWindowBounds());
            setExtendedState(Frame.MAXIMIZED_BOTH);
        }
        repaint();
        SwingUtilities.invokeLater(overlayManager::repositionAllOverlays);
    }

    // -------------------------------------------------------------------------
    // Settings overlay
    // -------------------------------------------------------------------------

    private void setupShutdownHook() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                loopController.stop();
                if (engine.isRunning()) engine.shutdown();
            }
        });
    }

    private void showSettingsOverlay() {
        if (settingsOverlay != null) return;
        loopController.pause();
        settingsOverlay = new SettingsOverlay(settingsManager, this::closeSettingsOverlay);
        getLayeredPane().add(settingsOverlay, JLayeredPane.POPUP_LAYER);
        settingsOverlay.setBounds(0, 0, getWidth(), getHeight());
        settingsOverlay.setVisible(true);
        settingsOverlay.requestFocusInWindow();
        revalidate();
        repaint();
    }

    private void closeSettingsOverlay() {
        if (settingsOverlay == null) return;
        getLayeredPane().remove(settingsOverlay);
        settingsOverlay = null;

        boolean settingsChanged = false;
        boolean fullscreenChanged = false;

        if (windowWidth != settingsManager.getWindowWidth()
                || windowHeight != settingsManager.getWindowHeight()) {
            windowWidth = settingsManager.getWindowWidth();
            windowHeight = settingsManager.getWindowHeight();
            settingsChanged = true;
        }

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if ((gd.getFullScreenWindow() == this) != settingsManager.isFullscreen()) {
            fullscreenChanged = true;
            settingsChanged = true;
        }

        if (settingsChanged) {
            applyDisplayMode();
            if (!fullscreenChanged) {
                canvas.setPreferredSize(
                        new Dimension(windowWidth, windowHeight - CUSTOM_HEADER_HEIGHT));
            }
        }

        loopController.resume();
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // Selection callbacks from SimulationCanvas
    // -------------------------------------------------------------------------

    /**
     * Called by the canvas when the user clicks a microbe to select it.
     */
    @Override
    public void onMicrobeSelected(Microbe microbe) {
        Microbe prev = selectedMicrobe;
        if (prev != null) prev.setSelected(false);
        selectedMicrobe = microbe;
        if (microbe != null) microbe.setSelected(true);
        overlayManager.getInspectorPanel().setSelectedMicrobe(microbe);
        overlayManager.getInspectorPanel().showPanel();
    }

    /** Called by the canvas when the user clicks empty space to deselect. */
    @Override
    public void onSelectionCleared() {
        Microbe prev = selectedMicrobe;
        if (prev != null) prev.setSelected(false);
        selectedMicrobe = null;

        overlayManager.getInspectorPanel().hidePanel();
        getLayeredPane().repaint();
    }

    /** Checks if the currently selected microbe has died and clears the selection if so. */
    private void checkDeadSelectedMicrobe() {
        Microbe current = selectedMicrobe;
        if (current != null && current.isDead()) {
            selectedMicrobe = null;
            SwingUtilities.invokeLater(() -> {
                current.setSelected(false);
                overlayManager.getInspectorPanel().hidePanel();
                getLayeredPane().repaint();
            });
        }
    }
}
