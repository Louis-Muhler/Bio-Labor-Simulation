package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application window for the Bio-Lab Evolution Simulator.
 *
 * <p>Refactored: delegates overlay management to {@link OverlayManager} and
 * simulation loop control to {@link SimulationLoopController}.</p>
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

        headerPanel = new CustomHeaderPanel(windowWidth, CUSTOM_HEADER_HEIGHT, this,
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

        loopController = new SimulationLoopController(engine, canvas, overlayManager, this::checkDeadSelectedMicrobe);

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
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not set system look and feel, using default", e);
        }

        SwingUtilities.invokeLater(() -> {
            try {
                new BioLabSimulatorApp().setVisible(true);
                LOGGER.info("Bio-Lab Simulator started successfully");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to start Bio-Lab Simulator", e);
                JOptionPane.showMessageDialog(null,
                        "Failed to start the Bio-Lab Simulator.\n\nError: " + e.getMessage(),
                        "Startup Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Display mode
    // -------------------------------------------------------------------------

    private void setupUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Custom undecorated window (with CustomHeaderPanel)
        setUndecorated(true);
        setBackground(new Color(18, 18, 18));
        setSize(windowWidth, windowHeight);
        setResizable(true);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(new Color(18, 18, 18));
        setContentPane(content);

        content.add(headerPanel, BorderLayout.NORTH);
        content.add(canvas, BorderLayout.CENTER);

        setLocationRelativeTo(null);

        // Overlays live on the layered pane
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
    // Window controls
    // -------------------------------------------------------------------------

    private void applyDisplayMode() {
        // Windowed vs. fullscreen is controlled via SettingsManager
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (settingsManager.isFullscreen() && gd.isFullScreenSupported()) {
            dispose();
            setUndecorated(true);
            gd.setFullScreenWindow(this);
        } else {
            if (gd.getFullScreenWindow() == this) gd.setFullScreenWindow(null);
            dispose();
            setUndecorated(true);
            setSize(windowWidth, windowHeight);
            setLocationRelativeTo(null);
        }

        setVisible(true);
        SwingUtilities.invokeLater(overlayManager::repositionAllOverlays);
    }

    private void closeApplication() {
        loopController.stop();
        if (engine.isRunning()) {
            engine.shutdown();
        }
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
                if (engine.isRunning()) {
                    engine.shutdown();
                }
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

    // -------------------------------------------------------------------------
    // Selection from canvas
    // -------------------------------------------------------------------------

    private void closeSettingsOverlay() {
        if (settingsOverlay == null) return;

        getLayeredPane().remove(settingsOverlay);
        settingsOverlay = null;

        boolean settingsChanged = false;
        boolean fullscreenChanged = false;

        if (windowWidth != settingsManager.getWindowWidth() || windowHeight != settingsManager.getWindowHeight()) {
            windowWidth = settingsManager.getWindowWidth();
            windowHeight = settingsManager.getWindowHeight();
            settingsChanged = true;
        }

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        boolean currentlyFullscreen = (gd.getFullScreenWindow() == this);
        boolean wantsFullscreen = settingsManager.isFullscreen();
        if (currentlyFullscreen != wantsFullscreen) {
            fullscreenChanged = true;
            settingsChanged = true;
        }

        if (settingsChanged) {
            applyDisplayMode();
            if (!fullscreenChanged) {
                canvas.setPreferredSize(new Dimension(windowWidth, windowHeight - CUSTOM_HEADER_HEIGHT));
            }
        }

        loopController.resume();
        revalidate();
        repaint();
    }

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

    /**
     * Called by the canvas when the user clicks empty space to deselect the current microbe.
     */
    @Override
    public void onSelectionCleared() {
        Microbe prev = selectedMicrobe;
        if (prev != null) prev.setSelected(false);
        selectedMicrobe = null;

        overlayManager.getInspectorPanel().hidePanel();
        getLayeredPane().repaint();
    }

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
