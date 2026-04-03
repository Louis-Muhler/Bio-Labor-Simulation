package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
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

    private static final WorldConfig PREVIEW_WORLD = new WorldConfig(
            "Menu Preview", 4_000, 4_000, 140, 600, 0.35, 0.35, 0.78
    );

    private final SettingsManager settingsManager;
    private final SaveGameRepository saveRepository;
    private final JPanel content;

    private SimulationRuntime engine;
    private SimulationCanvas canvas;
    private OverlayManager overlayManager;
    private SimulationLoopController loopController;

    private ModernButton saveButton;
    private ModernButton loadButton;

    private volatile Microbe selectedMicrobe;
    private SettingsOverlay settingsOverlay;
    private MainMenuOverlay mainMenuOverlay;
    private SaveBrowserOverlay saveBrowserOverlay;
    private WorldSetupOverlay worldSetupOverlay;

    private SaveGameMetadata currentSave;
    private long sessionStartMillis;

    private int windowWidth;
    private int windowHeight;

    /**
     * Constructs and fully initialises the application window:
     * engine, canvas, overlays, loop controller, and display mode.
     */
    public BioLabSimulatorApp() {
        super("Bio-Lab Evolution Simulator");

        settingsManager = new SettingsManager();
        saveRepository = new SaveGameRepository();
        windowWidth = settingsManager.getWindowWidth();
        windowHeight = settingsManager.getWindowHeight();

        content = new JPanel(new BorderLayout());
        content.setBackground(new Color(18, 18, 18));

        setupUI();
        setupShutdownHook();
        setVisible(true);
        applyDisplayMode();

        startPreviewSession();
        showMainMenu();
    }

    /**
     * Application entry point – bootstraps the Swing UI on the EDT.
     */
    public static void main(String[] args) {
        AppThemeBootstrap.installDarkTheme();

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

        setContentPane(content);

        setLocationRelativeTo(null);
        if (overlayManager != null) {
            overlayManager.repositionAllOverlays();
        }

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (overlayManager != null) {
                    overlayManager.repositionAllOverlays();
                    positionRuntimeSaveLoadButtons();
                }
                if (settingsOverlay != null) {
                    settingsOverlay.setBounds(0, 0, getWidth(), getHeight());
                }
                if (mainMenuOverlay != null) mainMenuOverlay.setBounds(0, 0, getWidth(), getHeight());
                if (saveBrowserOverlay != null) saveBrowserOverlay.setBounds(0, 0, getWidth(), getHeight());
                if (worldSetupOverlay != null) worldSetupOverlay.setBounds(0, 0, getWidth(), getHeight());
                // Enforce min-zoom and clamp camera to prevent out-of-bounds view
                if (canvas != null) SwingUtilities.invokeLater(canvas::clampZoomAndCamera);
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                if (overlayManager != null) {
                    overlayManager.repositionAllOverlays();
                    positionRuntimeSaveLoadButtons();
                }
                if (canvas != null) SwingUtilities.invokeLater(canvas::clampZoomAndCamera);
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
            if (overlayManager != null) {
                overlayManager.repositionAllOverlays();
                positionRuntimeSaveLoadButtons();
            }
            if (canvas != null) canvas.clampZoomAndCamera();
        });
    }


    // -------------------------------------------------------------------------
    // Settings overlay
    // -------------------------------------------------------------------------

    private void setupShutdownHook() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (loopController != null) loopController.stop();
                if (engine != null && engine.isRunning()) engine.shutdown();
            }
        });
    }

    private void startPreviewSession() {
        startSimulationSession(PREVIEW_WORLD, false);
    }

    private void startSimulationSession(WorldConfig config, boolean showGameOverlays) {
        teardownSession();

        engine = new SimulationEngine(config.worldWidth(), config.worldHeight(),
                config.initialPopulation(), config.maxPopulation());
        engine.getEnvironment().setTemperature(config.temperature());
        engine.getEnvironment().setToxicity(config.toxicity());
        engine.setFoodSpawnRate(config.foodSpawnRate());

        canvas = new SimulationCanvas(config.worldWidth(), config.worldHeight(),
                windowWidth, windowHeight, engine, this);

        content.removeAll();
        content.add(canvas, BorderLayout.CENTER);

        loopController = new SimulationLoopController(
                engine,
                canvas,
                this::checkDeadSelectedMicrobe,
                population -> {
                    if (overlayManager != null) {
                        SwingUtilities.invokeLater(() -> overlayManager.updatePopulationLabel(population));
                    }
                }
        );
        loopController.setRenderFps(showGameOverlays ? settingsManager.getSimulationFps() : 60);
        loopController.start();

        if (showGameOverlays) {
            createRuntimeOverlays();
        }

        revalidate();
        repaint();
        SwingUtilities.invokeLater(canvas::clampZoomAndCamera);
    }

    private void teardownSession() {
        removeRuntimeOverlays();
        if (loopController != null) {
            loopController.stop();
            loopController = null;
        }
        if (engine != null && engine.isRunning()) {
            engine.shutdown();
        }
        engine = null;
        canvas = null;
        overlayManager = null;
        selectedMicrobe = null;
    }

    private void createRuntimeOverlays() {
        InspectorPanel inspectorPanel = new InspectorPanel();
        EnvironmentPanel environmentPanel = new EnvironmentPanel(engine);
        ModernButton envToggleButton = new ModernButton("", ModernButton.ButtonIcon.ENVIRONMENT);
        ModernButton settingsButton = new ModernButton("", ModernButton.ButtonIcon.GEAR);
        ModernButton speedButton = new ModernButton("1x", ModernButton.ButtonIcon.SPEED_UP);

        overlayManager = new OverlayManager(this::getLayeredPane,
                inspectorPanel, environmentPanel, envToggleButton, settingsButton, speedButton);

        envToggleButton.addActionListener(e -> overlayManager.toggleEnvironmentPanel());
        settingsButton.addActionListener(e -> showSettingsOverlay());
        speedButton.addActionListener(e -> speedButton.setDisplayText(loopController.cycleSpeed()));

        inspectorPanel.setVisible(false);
        environmentPanel.setVisible(false);
        overlayManager.repositionAllOverlays();

        saveButton = new ModernButton("SAVE");
        saveButton.setPreferredSize(new Dimension(95, 45));
        saveButton.addActionListener(e -> quickSaveCurrentWorld());
        loadButton = new ModernButton("LOAD");
        loadButton.setPreferredSize(new Dimension(95, 45));
        loadButton.addActionListener(e -> showSaveBrowser());
        getLayeredPane().add(saveButton, JLayeredPane.PALETTE_LAYER);
        getLayeredPane().add(loadButton, JLayeredPane.PALETTE_LAYER);
        positionRuntimeSaveLoadButtons();
    }

    private void removeRuntimeOverlays() {
        if (saveButton != null) {
            getLayeredPane().remove(saveButton);
            saveButton = null;
        }
        if (loadButton != null) {
            getLayeredPane().remove(loadButton);
            loadButton = null;
        }
    }

    private void positionRuntimeSaveLoadButtons() {
        if (saveButton == null || loadButton == null) return;
        int top = 15;
        JRootPane root = getRootPane();
        if (root != null) {
            top += root.getContentPane().getY();
        }
        saveButton.setBounds(15, top + 114, 95, 45);
        loadButton.setBounds(15, top + 165, 95, 45);
    }

    private void showMainMenu() {
        if (mainMenuOverlay != null) return;
        mainMenuOverlay = new MainMenuOverlay(this::showSaveBrowser, this::showSettingsOverlay);
        getLayeredPane().add(mainMenuOverlay, JLayeredPane.POPUP_LAYER);
        mainMenuOverlay.setBounds(0, 0, getWidth(), getHeight());
        mainMenuOverlay.setVisible(true);
        getLayeredPane().repaint();
    }

    private void removeMainMenu() {
        if (mainMenuOverlay == null) return;
        getLayeredPane().remove(mainMenuOverlay);
        mainMenuOverlay = null;
    }

    private void showSaveBrowser() {
        if (saveBrowserOverlay != null) return;
        if (loopController != null) loopController.pause();
        saveBrowserOverlay = new SaveBrowserOverlay(new SaveBrowserOverlay.Listener() {
            @Override
            public void onCreateRequested() {
                showWorldSetup();
            }

            @Override
            public void onPlayRequested(SaveGameMetadata metadata) {
                loadSaveAndStart(metadata);
            }

            @Override
            public void onDeleteRequested(SaveGameMetadata metadata) {
                try {
                    saveRepository.deleteSave(metadata.saveId());
                    refreshSaveBrowser();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(BioLabSimulatorApp.this,
                            "Delete failed: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
                }
            }

            @Override
            public void onBackRequested() {
                removeSaveBrowser();
            }
        });
        getLayeredPane().add(saveBrowserOverlay, JLayeredPane.POPUP_LAYER);
        saveBrowserOverlay.setBounds(0, 0, getWidth(), getHeight());
        refreshSaveBrowser();
        revalidate();
        repaint();
    }

    private void refreshSaveBrowser() {
        if (saveBrowserOverlay == null) return;
        try {
            saveBrowserOverlay.setSaves(saveRepository.listSaves());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to list saves: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeSaveBrowser() {
        if (saveBrowserOverlay != null) {
            getLayeredPane().remove(saveBrowserOverlay);
            saveBrowserOverlay = null;
        }
        removeWorldSetup();
        if (loopController != null) loopController.resume();
        revalidate();
        repaint();
    }

    private void showWorldSetup() {
        if (worldSetupOverlay != null) return;
        worldSetupOverlay = new WorldSetupOverlay(this::createWorldAndStart, this::removeWorldSetup);
        getLayeredPane().add(worldSetupOverlay, JLayeredPane.DRAG_LAYER);
        worldSetupOverlay.setBounds(0, 0, getWidth(), getHeight());
        worldSetupOverlay.setVisible(true);
        revalidate();
        repaint();
    }

    private void removeWorldSetup() {
        if (worldSetupOverlay == null) return;
        getLayeredPane().remove(worldSetupOverlay);
        worldSetupOverlay = null;
        revalidate();
        repaint();
    }

    private void createWorldAndStart(WorldConfig config) {
        startSimulationSession(config, true);
        removeMainMenu();
        removeSaveBrowser();

        try {
            SimulationState state = engine.captureState();
            currentSave = saveRepository.createNewSave(config, state);
            sessionStartMillis = System.currentTimeMillis();
        } catch (IOException ex) {
            currentSave = null;
            JOptionPane.showMessageDialog(this,
                    "World was created, but initial save failed: " + ex.getMessage(),
                    "Save Warning", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadSaveAndStart(SaveGameMetadata metadata) {
        try {
            SimulationState state = saveRepository.loadState(metadata.saveId());
            WorldConfig config = new WorldConfig(
                    metadata.mapName(),
                    metadata.worldWidth(),
                    metadata.worldHeight(),
                    Math.max(state.microbes().size(), 0),
                    Math.max(20_000, state.microbes().size() * 3),
                    state.temperature(),
                    state.toxicity(),
                    state.foodSpawnRate()
            );
            startSimulationSession(config, true);
            engine.loadState(state);
            currentSave = metadata;
            sessionStartMillis = System.currentTimeMillis();

            removeMainMenu();
            removeSaveBrowser();
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                    "Load failed: " + ex.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void quickSaveCurrentWorld() {
        if (engine == null) return;
        SimulationState state = engine.captureState();
        long playedSeconds = elapsedSessionSeconds();

        try {
            if (currentSave == null) {
                WorldConfig cfg = new WorldConfig(
                        "Quick Save",
                        state.worldWidth(),
                        state.worldHeight(),
                        state.microbes().size(),
                        Math.max(20_000, state.microbes().size() * 3),
                        state.temperature(),
                        state.toxicity(),
                        state.foodSpawnRate()
                );
                currentSave = saveRepository.createNewSave(cfg, state);
            } else {
                saveRepository.overwriteSave(currentSave, state, playedSeconds);
                currentSave = saveRepository.loadMetadata(currentSave.saveId());
            }
            sessionStartMillis = System.currentTimeMillis();
            JOptionPane.showMessageDialog(this, "Game saved.", "Save", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Save failed: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private long elapsedSessionSeconds() {
        if (sessionStartMillis <= 0L) return 0L;
        return Math.max(0L, (System.currentTimeMillis() - sessionStartMillis) / 1000L);
    }

    private void showSettingsOverlay() {
        if (settingsOverlay != null) return;
        if (loopController != null) loopController.pause();
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
        if (loopController != null) loopController.setRenderFps(settingsManager.getSimulationFps());
        applyDisplayMode();           // always – even if nothing changed
        if (loopController != null) loopController.resume();
        revalidate();
        repaint();
    }

    /**
     * Called when the user clicks CANCEL or presses ESC – no display change.
     */
    private void cancelSettingsAndClose() {
        removeSettingsOverlay();
        if (loopController != null) loopController.resume();
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
        if (overlayManager != null) {
            overlayManager.getInspectorPanel().setSelectedMicrobe(microbe);
            overlayManager.getInspectorPanel().showPanel();
        }
        canvas.startFollowing(microbe);
    }

    /** Called by the canvas when the user clicks empty space to deselect. */
    @Override
    public void onSelectionCleared() {
        Microbe prev = selectedMicrobe;
        if (prev != null) prev.setSelected(false);
        selectedMicrobe = null;
        canvas.stopFollowing();
        if (overlayManager != null) overlayManager.getInspectorPanel().hidePanel();
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
                if (overlayManager != null) overlayManager.getInspectorPanel().hidePanel();
                canvas.stopFollowing();
                getLayeredPane().repaint();
            }
        });
    }
}
