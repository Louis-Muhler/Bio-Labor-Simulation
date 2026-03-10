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
 * <p>Uses FlatLaf's default dark theme with the standard Windows title bar.
 * All in-game overlays (inspector, environment, settings, speed) are managed
 * by {@link OverlayManager} on the JLayeredPane.</p>
 *
 * <p>Delegates overlay management to {@link OverlayManager} and simulation
 * loop control to {@link SimulationLoopController}.</p>
 */
public class BioLabSimulatorApp extends JFrame implements SimulationCanvas.SelectionListener {
    private static final Logger LOGGER = Logger.getLogger(BioLabSimulatorApp.class.getName());

    private static final int INITIAL_POPULATION = 1500;
    private static final int WORLD_SIZE = 10_000;

    private final SettingsManager settingsManager;
    private final SimulationEngine engine;
    private final SimulationCanvas canvas;
    private final OverlayManager overlayManager;
    private final SimulationLoopController loopController;

    private volatile Microbe selectedMicrobe;
    private SettingsOverlay settingsOverlay;

    private int windowWidth;
    private int windowHeight;

    /**
     * Constructs and fully initialises the application window:
     * engine, canvas, overlays, loop controller, and display mode.
     */
    public BioLabSimulatorApp() {
        super("Bio-Lab Evolution Simulator");

        settingsManager = new SettingsManager();
        windowWidth = settingsManager.getWindowWidth();
        windowHeight = settingsManager.getWindowHeight();

        engine = new SimulationEngine(WORLD_SIZE, WORLD_SIZE, INITIAL_POPULATION);

        canvas = new SimulationCanvas(WORLD_SIZE, WORLD_SIZE, windowWidth,
                windowHeight, engine, this);

        InspectorPanel inspectorPanel = new InspectorPanel();
        EnvironmentPanel environmentPanel = new EnvironmentPanel(engine);
        ModernButton envToggleButton = new ModernButton("", ModernButton.ButtonIcon.ENVIRONMENT);
        ModernButton settingsButton = new ModernButton("", ModernButton.ButtonIcon.GEAR);
        ModernButton speedButton = new ModernButton("1x", ModernButton.ButtonIcon.SPEED_UP);

        overlayManager = new OverlayManager(this::getLayeredPane,
                inspectorPanel, environmentPanel, envToggleButton, settingsButton, speedButton);

        loopController = new SimulationLoopController(engine, canvas, overlayManager,
                this::checkDeadSelectedMicrobe);

        envToggleButton.addActionListener(e -> overlayManager.toggleEnvironmentPanel());
        settingsButton.addActionListener(e -> showSettingsOverlay());
        speedButton.addActionListener(e -> speedButton.setDisplayText(loopController.cycleSpeed()));

        inspectorPanel.setVisible(false);
        environmentPanel.setVisible(false);

        setupUI();
        setupShutdownHook();
        setVisible(true);
        applyDisplayMode();
        loopController.setRenderFps(settingsManager.getSimulationFps());
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

        // Set title bar colors BEFORE setup() so FlatLaf uses them as defaults.
        // #121212 = rgb(18,18,18) matches the app background.
        UIManager.put("RootPane.background", new Color(18, 18, 18));
        UIManager.put("TitlePane.background", new Color(18, 18, 18));
        UIManager.put("TitlePane.inactiveBackground", new Color(18, 18, 18));
        UIManager.put("TitlePane.foreground", new Color(200, 200, 200));
        UIManager.put("TitlePane.inactiveForeground", new Color(130, 130, 130));
        UIManager.put("TitlePane.unifiedBackground", true);

        FlatDarkLaf.setup();

        SwingUtilities.invokeLater(() -> {
            try {
                new BioLabSimulatorApp();
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

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(new Color(18, 18, 18));
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
                // Enforce min-zoom and clamp camera to prevent out-of-bounds view
                SwingUtilities.invokeLater(canvas::clampZoomAndCamera);
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                overlayManager.repositionAllOverlays();
                SwingUtilities.invokeLater(canvas::clampZoomAndCamera);
            }
        });
    }


    // -------------------------------------------------------------------------
    // Display mode
    // -------------------------------------------------------------------------

    /**
     * Switches between fullscreen exclusive mode and normal windowed mode.
     *
     * <p>Avoids {@code dispose()} which destroys the native peer and causes
     * visual glitches (gray bars, shifted overlays, white line artifacts).
     * Instead, uses {@code GraphicsDevice.setFullScreenWindow()} directly
     * and re-validates the layout afterwards.</p>
     */
    private void applyDisplayMode() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        boolean wantFullscreen = settingsManager.isFullscreen() && gd.isFullScreenSupported();
        boolean isFullscreen = gd.getFullScreenWindow() == this;

        if (wantFullscreen && !isFullscreen) {
            // Switch to fullscreen – setFullScreenWindow handles undecorated internally
            gd.setFullScreenWindow(this);
        } else if (!wantFullscreen && isFullscreen) {
            // Leave fullscreen
            gd.setFullScreenWindow(null);
            setSize(windowWidth, windowHeight);
            setLocationRelativeTo(null);
        } else if (!wantFullscreen) {
            // Just resize in windowed mode
            setSize(windowWidth, windowHeight);
            setLocationRelativeTo(null);
        }

        // Force layout and overlay recalculation after mode change
        revalidate();
        repaint();
        SwingUtilities.invokeLater(() -> {
            overlayManager.repositionAllOverlays();
            canvas.clampZoomAndCamera();
        });
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
        settingsOverlay = new SettingsOverlay(settingsManager,
                this::applySettingsAndClose,   // APPLY button
                this::cancelSettingsAndClose);  // CANCEL / ESC
        getLayeredPane().add(settingsOverlay, JLayeredPane.POPUP_LAYER);
        settingsOverlay.setBounds(0, 0, getWidth(), getHeight());
        settingsOverlay.setVisible(true);
        settingsOverlay.requestFocusInWindow();
        revalidate();
        repaint();
    }

    /**
     * Called when the user clicks APPLY – always re-applies display mode.
     */
    private void applySettingsAndClose() {
        removeSettingsOverlay();
        windowWidth = settingsManager.getWindowWidth();
        windowHeight = settingsManager.getWindowHeight();
        loopController.setRenderFps(settingsManager.getSimulationFps());
        applyDisplayMode();           // always – even if nothing changed
        loopController.resume();
        revalidate();
        repaint();
    }

    /**
     * Called when the user clicks CANCEL or presses ESC – no display change.
     */
    private void cancelSettingsAndClose() {
        removeSettingsOverlay();
        loopController.resume();
        revalidate();
        repaint();
    }

    private void removeSettingsOverlay() {
        if (settingsOverlay == null) return;
        getLayeredPane().remove(settingsOverlay);
        settingsOverlay = null;
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
        canvas.startFollowing(microbe);
    }

    /** Called by the canvas when the user clicks empty space to deselect. */
    @Override
    public void onSelectionCleared() {
        Microbe prev = selectedMicrobe;
        if (prev != null) prev.setSelected(false);
        selectedMicrobe = null;
        canvas.stopFollowing();
        overlayManager.getInspectorPanel().hidePanel();
        getLayeredPane().repaint();
    }

    /**
     * Checks if the currently selected microbe has died and performs auto-selection:
     * Priority 1 – a living child of the dead microbe.
     * Priority 2 – any random living microbe.
     * The camera smoothly pans to the new target.
     */
    private void checkDeadSelectedMicrobe() {
        Microbe current = selectedMicrobe;
        if (current == null || !current.isDead()) return;

        // Find replacement before clearing state
        Microbe replacement = engine.findLivingChild(current.getId());
        if (replacement == null) replacement = engine.findRandomLivingMicrobe();

        final Microbe next = replacement;
        selectedMicrobe = null;

        SwingUtilities.invokeLater(() -> {
            current.setSelected(false);
            if (next != null) {
                onMicrobeSelected(next);
            } else {
                overlayManager.getInspectorPanel().hidePanel();
                canvas.stopFollowing();
                getLayeredPane().repaint();
            }
        });
    }
}
