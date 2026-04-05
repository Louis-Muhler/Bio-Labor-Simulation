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
    private ModernButton globalSettingsButton;
    private final SessionSaveCoordinator sessionSaveCoordinator;
    private final UiFlowCoordinator uiFlowCoordinator;
    private final SettingsFlowCoordinator settingsFlowCoordinator;
    private final AppUiStateMachine uiStateMachine;
    private ModernButton runtimeSpeedButton;

    private volatile Microbe selectedMicrobe;
    private boolean gameplayParkedInMenu;

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
        sessionSaveCoordinator = new SessionSaveCoordinator(saveRepository, new AsyncSaveService(), LOGGER);
        uiStateMachine = new AppUiStateMachine(AppUiState.BOOT);
        uiFlowCoordinator = new UiFlowCoordinator(
                this,
                saveRepository,
                this::getContentTopY,
                () -> globalSettingsButton,
                this::transitionOrRecover,
                this::resumeLatestSave,
                this::createWorldAndStart,
                this::loadSaveAndStart,
                () -> {
                    if (isGameplaySession()) {
                        uiStateMachine.transitionTo(AppUiState.GAMEPLAY);
                    }
                },
                LOGGER
        );
        settingsFlowCoordinator = new SettingsFlowCoordinator(
                this,
                settingsManager,
                sessionSaveCoordinator,
                () -> engine,
                this::isGameplaySession,
                () -> loopController,
                this::getContentTopY,
                uiStateMachine,
                this::transitionOrRecover,
                this::returnToMainMenuFromGameplay,
                this::applySettingsAfterOverlay
        );
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
        uiStateMachine.transitionTo(AppUiState.PREVIEW_MENU);
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

        globalSettingsButton = new ModernButton("", ModernButton.ButtonIcon.GEAR);
        globalSettingsButton.addActionListener(e -> showSettingsOverlay());
        getLayeredPane().add(globalSettingsButton, JLayeredPane.DRAG_LAYER);
        positionGlobalSettingsButton();

        setLocationRelativeTo(null);
        if (overlayManager != null) {
            overlayManager.repositionAllOverlays();
        }

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                positionGlobalSettingsButton();
                syncGameplayOverlayVisibilityByState();
                refreshOverlayBounds();
                // Enforce min-zoom and clamp camera to prevent out-of-bounds view
                if (canvas != null) SwingUtilities.invokeLater(canvas::clampZoomAndCamera);
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                positionGlobalSettingsButton();
                syncGameplayOverlayVisibilityByState();
                refreshOverlayBounds();
                if (canvas != null) SwingUtilities.invokeLater(canvas::clampZoomAndCamera);
            }
        });
    }

    private void refreshOverlayBounds() {
        settingsFlowCoordinator.refreshOverlayBounds(getWidth(), getHeight());
        uiFlowCoordinator.refreshOverlayBounds(getWidth(), getHeight());
    }

    private void syncGameplayOverlayVisibilityByState() {
        if (overlayManager == null) {
            return;
        }
        AppUiState current = uiStateMachine.current();
        boolean settingsOverGameplay = settingsFlowCoordinator.settingsOverGameplay(current);
        boolean showGameplayOverlays = current == AppUiState.GAMEPLAY || settingsOverGameplay;
        if (showGameplayOverlays) {
            overlayManager.repositionAllOverlays();
            overlayManager.setGameplayOverlaysVisible(true);
        } else {
            overlayManager.setGameplayOverlaysVisible(false);
        }
    }

    private void positionGlobalSettingsButton() {
        if (globalSettingsButton == null) return;
        int top = 15;
        JRootPane root = getRootPane();
        if (root != null) {
            top += root.getContentPane().getY();
        }
        globalSettingsButton.setBounds(15, top, 45, 45);
        globalSettingsButton.repaint();
    }

    private int getContentTopY() {
        JRootPane root = getRootPane();
        return root == null ? 0 : root.getContentPane().getY();
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
            positionGlobalSettingsButton();
            syncGameplayOverlayVisibilityByState();
            refreshOverlayBounds();
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
                if (isGameplaySession()) {
                    sessionSaveCoordinator.saveCurrentWorld(engine, true);
                }
                sessionSaveCoordinator.shutdown();
                uiStateMachine.transitionTo(AppUiState.SHUTDOWN);
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
        gameplayParkedInMenu = false;

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
            sessionSaveCoordinator.startAutoSave(() -> engine, this::isGameplaySession);
        } else {
            sessionSaveCoordinator.stopAutoSave();
        }

        revalidate();
        repaint();
        SwingUtilities.invokeLater(canvas::clampZoomAndCamera);
    }

    private void teardownSession() {
        if (isGameplaySession()) {
            sessionSaveCoordinator.saveCurrentWorld(engine, true);
        }
        sessionSaveCoordinator.stopAutoSave();
        if (overlayManager != null) {
            overlayManager.removeAllOverlays();
        }
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
        runtimeSpeedButton = null;
        selectedMicrobe = null;
        sessionSaveCoordinator.clearSessionContext();
    }

    private void createRuntimeOverlays() {
        InspectorPanel inspectorPanel = new InspectorPanel();
        EnvironmentPanel environmentPanel = new EnvironmentPanel(engine);
        ModernButton envToggleButton = new ModernButton("", ModernButton.ButtonIcon.ENVIRONMENT);
        ModernButton speedButton = new ModernButton("1x", ModernButton.ButtonIcon.SPEED_UP);
        runtimeSpeedButton = speedButton;

        overlayManager = new OverlayManager(this::getLayeredPane,
                inspectorPanel, environmentPanel, envToggleButton, speedButton);

        envToggleButton.addActionListener(e -> overlayManager.toggleEnvironmentPanel());
        speedButton.addActionListener(e -> speedButton.setDisplayText(loopController.cycleSpeed()));

        inspectorPanel.setVisible(false);
        environmentPanel.setVisible(false);
        overlayManager.repositionAllOverlays();
    }

    private void showMainMenu() {
        if (!transitionOrRecover(AppUiState.PREVIEW_MENU)) return;
        uiFlowCoordinator.showMainMenu();
    }

    private void createWorldAndStart(WorldConfig config) {
        startSimulationSession(config, true);
        transitionOrRecover(AppUiState.GAMEPLAY);
        uiFlowCoordinator.removeMainMenu();
        uiFlowCoordinator.removeSaveBrowser(false);

        try {
            SimulationState state = engine.captureState();
            SaveGameMetadata createdSave = saveRepository.createNewSave(config, state);
            sessionSaveCoordinator.markSessionStarted(config.mapName(), createdSave);
        } catch (IOException ex) {
            sessionSaveCoordinator.markSessionStarted(config.mapName(), null);
            JOptionPane.showMessageDialog(this,
                    "World was created, but initial save failed: " + ex.getMessage(),
                    "Save Warning", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadSaveAndStart(SaveGameMetadata metadata) {
        if (metadata == null) return;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<SimulationState, Void> worker = new SwingWorker<>() {
            @Override
            protected SimulationState doInBackground() throws Exception {
                return saveRepository.loadState(metadata.saveId());
            }

            @Override
            protected void done() {
                try {
                    SimulationState state = get();
                    WorldConfig config = new WorldConfig(
                            metadata.mapName(),
                            metadata.worldWidth(),
                            metadata.worldHeight(),
                            state.microbes().size(),
                            Math.max(20_000, state.microbes().size() * 3),
                            state.temperature(),
                            state.toxicity(),
                            state.foodSpawnRate()
                    );
                    startSimulationSession(config, true);
                    transitionOrRecover(AppUiState.GAMEPLAY);
                    engine.loadState(state);
                    sessionSaveCoordinator.markSessionStarted(metadata.mapName(), metadata);

                    uiFlowCoordinator.removeMainMenu();
                    uiFlowCoordinator.removeSaveBrowser(false);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(BioLabSimulatorApp.this,
                            "Load failed: " + ex.getMessage(),
                            "Load Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        worker.execute();
    }

    private boolean isGameplaySession() {
        return overlayManager != null;
    }

    private void returnToMainMenuFromGameplay() {
        sessionSaveCoordinator.saveCurrentWorld(engine, isGameplaySession());
        settingsFlowCoordinator.closeForMenuReturn();
        sessionSaveCoordinator.stopAutoSave();
        if (loopController != null && runtimeSpeedButton != null) {
            runtimeSpeedButton.setDisplayText(loopController.resetSpeedToDefault());
        }
        if (overlayManager != null) {
            overlayManager.setGameplayOverlaysVisible(false);
        }
        Microbe prev = selectedMicrobe;
        if (prev != null) prev.setSelected(false);
        selectedMicrobe = null;
        if (canvas != null) canvas.stopFollowing();
        gameplayParkedInMenu = true;
        showMainMenu();
    }

    private void resumeLatestSave() {
        try {
            SaveGameMetadata latest = saveRepository.findMostRecentSave().orElse(null);
            if (latest != null) {
                gameplayParkedInMenu = false;
                loadSaveAndStart(latest);
            } else {
                uiFlowCoordinator.updateResumeAvailability();
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Resume failed: " + ex.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean transitionOrRecover(AppUiState target) {
        if (uiStateMachine.transitionTo(target)) {
            return true;
        }
        recoverToMainMenu();
        return false;
    }

    private void recoverToMainMenu() {
        if (uiStateMachine.current() == AppUiState.SHUTDOWN) {
            return;
        }
        settingsFlowCoordinator.clearOverlayAndResume();
        uiFlowCoordinator.clearSaveBrowser();
        if (engine == null || canvas == null) {
            startPreviewSession();
        }
        if (overlayManager != null) {
            overlayManager.setGameplayOverlaysVisible(false);
        }
        uiStateMachine.forceState(AppUiState.PREVIEW_MENU);
        if (!uiFlowCoordinator.isMainMenuVisible()) {
            showMainMenu();
        } else {
            uiFlowCoordinator.updateResumeAvailability();
        }
        revalidate();
        repaint();
    }

    private void showSettingsOverlay() {
        settingsFlowCoordinator.showSettingsOverlay();
    }

    private void applySettingsAfterOverlay() {
        windowWidth = settingsManager.getWindowWidth();
        windowHeight = settingsManager.getWindowHeight();
        if (loopController != null) loopController.setRenderFps(settingsManager.getSimulationFps());
        applyDisplayMode();
    }

    private boolean isSelectionBlockedByUi() {
        return uiFlowCoordinator.hasBlockingOverlay()
                || settingsFlowCoordinator.isSettingsVisible()
                || gameplayParkedInMenu;
    }

    // -------------------------------------------------------------------------
    // Selection callbacks from SimulationCanvas
    // -------------------------------------------------------------------------

    /**
     * Called by the canvas when the user clicks a microbe to select it.
     */
    @Override
    public void onMicrobeSelected(Microbe microbe) {
        if (isSelectionBlockedByUi()) return;
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
        if (isSelectionBlockedByUi()) return;
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
