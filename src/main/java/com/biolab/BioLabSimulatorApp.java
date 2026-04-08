package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.OptionalLong;
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

    private final SimulationSessionCoordinator simulationSessionCoordinator;
    private final MicrobeSelectionCoordinator selectionCoordinator;
    private ModernButton globalSettingsButton;
    private final SessionSaveCoordinator sessionSaveCoordinator;
    private final UiFlowCoordinator uiFlowCoordinator;
    private final SettingsFlowCoordinator settingsFlowCoordinator;
    private final AppUiStateMachine uiStateMachine;
    private Timer inspectorRestoreTimer;
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
        sessionSaveCoordinator = new SessionSaveCoordinator(
                saveRepository,
                new AsyncSaveService(),
                LOGGER,
                settingsManager.getAutosaveIntervalSeconds()
        );
        simulationSessionCoordinator = new SimulationSessionCoordinator(
                this,
                settingsManager,
                sessionSaveCoordinator
        );
        selectionCoordinator = new MicrobeSelectionCoordinator(
                this::isSelectionBlockedByUi,
                simulationSessionCoordinator::engine,
                simulationSessionCoordinator::canvas,
                simulationSessionCoordinator::overlayManager,
                this::getLayeredPane
        );
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
                simulationSessionCoordinator::engine,
                this::isGameplaySession,
                simulationSessionCoordinator::loopController,
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

    static OptionalLong findPersistedSelectedMicrobeId(SimulationState state) {
        return MicrobeSelectionCoordinator.findPersistedSelectedMicrobeId(state);
    }

    private void scheduleInspectorRestoreAfterWindowMotion() {
        if (inspectorRestoreTimer == null) {
            inspectorRestoreTimer = new Timer(180, e -> restoreInspectorAfterWindowMotionIfNeeded());
            inspectorRestoreTimer.setRepeats(false);
        }
        inspectorRestoreTimer.restart();
    }

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
        if (simulationSessionCoordinator.overlayManager() != null) {
            simulationSessionCoordinator.repositionOverlays();
        }

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                scheduleInspectorRestoreAfterWindowMotion();
            }

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                positionGlobalSettingsButton();
                syncGameplayOverlayVisibilityByState();
                refreshOverlayBounds();
                // Enforce min-zoom and clamp camera to prevent out-of-bounds view
                SimulationCanvas runtimeCanvas = simulationSessionCoordinator.canvas();
                if (runtimeCanvas != null) SwingUtilities.invokeLater(runtimeCanvas::clampZoomAndCamera);
                scheduleInspectorRestoreAfterWindowMotion();
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                positionGlobalSettingsButton();
                syncGameplayOverlayVisibilityByState();
                refreshOverlayBounds();
                SimulationCanvas runtimeCanvas = simulationSessionCoordinator.canvas();
                if (runtimeCanvas != null) SwingUtilities.invokeLater(runtimeCanvas::clampZoomAndCamera);
                scheduleInspectorRestoreAfterWindowMotion();
            }
        });
    }

    private void refreshOverlayBounds() {
        settingsFlowCoordinator.refreshOverlayBounds(getWidth(), getHeight());
        uiFlowCoordinator.refreshOverlayBounds(getWidth(), getHeight());
    }

    private void restoreInspectorAfterWindowMotionIfNeeded() {
        if (!isGameplaySession()) {
            return;
        }
        selectionCoordinator.restoreInspectorAfterWindowMotionIfNeeded();
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

    private void syncGameplayOverlayVisibilityByState() {
        if (simulationSessionCoordinator.overlayManager() == null) {
            return;
        }
        AppUiState current = uiStateMachine.current();
        boolean settingsOverGameplay = settingsFlowCoordinator.settingsOverGameplay(current);
        boolean showGameplayOverlays = current == AppUiState.GAMEPLAY || settingsOverGameplay;
        if (showGameplayOverlays) {
            simulationSessionCoordinator.repositionOverlays();
            simulationSessionCoordinator.setGameplayOverlaysVisible(true);
        } else {
            simulationSessionCoordinator.setGameplayOverlaysVisible(false);
        }
    }


    // -------------------------------------------------------------------------
    // Settings overlay
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
            SimulationCanvas runtimeCanvas = simulationSessionCoordinator.canvas();
            if (runtimeCanvas != null) runtimeCanvas.clampZoomAndCamera();
        });
    }

    private void startPreviewSession() {
        startSimulationSession(PREVIEW_WORLD, false);
    }

    private void setupShutdownHook() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                uiStateMachine.transitionTo(AppUiState.SHUTDOWN);
                simulationSessionCoordinator.teardownSession();
                sessionSaveCoordinator.shutdown();
            }
        });
    }

    private void showMainMenu() {
        if (!transitionOrRecover(AppUiState.PREVIEW_MENU)) return;
        uiFlowCoordinator.showMainMenu();
    }

    private void startSimulationSession(WorldConfig config, boolean showGameOverlays) {
        gameplayParkedInMenu = false;
        selectionCoordinator.forceClearSelection();
        simulationSessionCoordinator.startSession(
                config,
                showGameOverlays,
                content,
                this,
                selectionCoordinator::checkDeadSelectedMicrobe
        );
    }

    private void createWorldAndStart(WorldConfig config) {
        startSimulationSession(config, true);
        if (!transitionOrRecover(AppUiState.GAMEPLAY)) {
            return;
        }
        uiFlowCoordinator.removeMainMenu();
        uiFlowCoordinator.removeSaveBrowser(false);

        try {
            SimulationRuntime engine = simulationSessionCoordinator.engine();
            if (engine == null) {
                sessionSaveCoordinator.markSessionStarted(config.mapName(), null);
                return;
            }
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
                    if (!transitionOrRecover(AppUiState.GAMEPLAY)) {
                        return;
                    }
                    SimulationRuntime engine = simulationSessionCoordinator.engine();
                    if (engine != null) {
                        engine.loadState(state);
                    }
                    sessionSaveCoordinator.markSessionStarted(metadata.mapName(), metadata);

                    uiFlowCoordinator.removeMainMenu();
                    uiFlowCoordinator.removeSaveBrowser(false);
                    restoreInspectorSelectionFromLoadedState(state);
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

    private void restoreInspectorSelectionFromLoadedState(SimulationState state) {
        selectionCoordinator.restoreInspectorSelectionFromLoadedState(state);
    }

    private boolean isGameplaySession() {
        return simulationSessionCoordinator.isGameplaySession();
    }

    private void returnToMainMenuFromGameplay() {
        SimulationRuntime runtime = simulationSessionCoordinator.engine();
        boolean gameplaySession = isGameplaySession();
        SwingWorker<Void, Void> menuSaveWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                sessionSaveCoordinator.saveCurrentWorld(runtime, gameplaySession);
                return null;
            }
        };
        menuSaveWorker.execute();
        settingsFlowCoordinator.closeForMenuReturn();
        sessionSaveCoordinator.stopAutoSave();
        simulationSessionCoordinator.resetRuntimeSpeedToDefault();
        simulationSessionCoordinator.setGameplayOverlaysVisible(false);
        selectionCoordinator.forceClearSelection();
        simulationSessionCoordinator.deactivateSpawnTool();
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
        simulationSessionCoordinator.deactivateSpawnTool();
        settingsFlowCoordinator.clearOverlayAndResume();
        uiFlowCoordinator.clearSaveBrowser();
        if (simulationSessionCoordinator.engine() == null || simulationSessionCoordinator.canvas() == null) {
            startPreviewSession();
        }
        simulationSessionCoordinator.setGameplayOverlaysVisible(false);
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
        sessionSaveCoordinator.setAutosaveIntervalSeconds(settingsManager.getAutosaveIntervalSeconds());
        simulationSessionCoordinator.setRenderFps(settingsManager.getSimulationFps());
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
        selectionCoordinator.onMicrobeSelected(microbe);
    }

    /** Called by the canvas when the user clicks empty space to deselect. */
    @Override
    public void onSelectionCleared() {
        selectionCoordinator.onSelectionCleared();
    }
}
