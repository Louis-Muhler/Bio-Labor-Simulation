package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
    private final AsyncSaveService asyncSaveService;
    private final AppUiStateMachine uiStateMachine;
    private ModernButton runtimeSpeedButton;
    private AppUiState stateBeforeSettings = AppUiState.BOOT;

    private volatile Microbe selectedMicrobe;
    private SettingsOverlay settingsOverlay;
    private MainMenuOverlay mainMenuOverlay;
    private SaveBrowserOverlay saveBrowserOverlay;

    private SaveGameMetadata currentSave;
    private static final long AUTOSAVE_INTERVAL_SECONDS = 8L;
    private static final Integer SETTINGS_LAYER = JLayeredPane.DRAG_LAYER + 200;
    private long sessionStartMillis;
    private String currentWorldName;
    private boolean pausedForSaveBrowser;
    private boolean reopenMainMenuOnBrowserClose;
    private boolean pausedForSettings;
    private boolean gameplayParkedInMenu;
    private ScheduledExecutorService autosaveExecutor;
    private ScheduledFuture<?> autosaveTask;

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
        asyncSaveService = new AsyncSaveService();
        uiStateMachine = new AppUiStateMachine(AppUiState.BOOT);
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
        if (settingsOverlay != null) {
            int topY = getContentTopY();
            settingsOverlay.setBounds(0, topY, getWidth(), Math.max(0, getHeight() - topY));
            settingsOverlay.revalidate();
        }
        if (mainMenuOverlay != null) {
            mainMenuOverlay.setBounds(0, 0, getWidth(), getHeight());
            mainMenuOverlay.revalidate();
        }
        if (saveBrowserOverlay != null) {
            int topY = getContentTopY();
            saveBrowserOverlay.setBounds(0, topY, getWidth(), Math.max(0, getHeight() - topY));
            saveBrowserOverlay.revalidate();
        }
    }

    private void syncGameplayOverlayVisibilityByState() {
        if (overlayManager == null) {
            return;
        }
        AppUiState current = uiStateMachine.current();
        boolean settingsOverGameplay = current == AppUiState.SETTINGS && stateBeforeSettings == AppUiState.GAMEPLAY;
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
                    saveCurrentWorld();
                }
                stopAutoSave();
                flushPendingSaves();
                uiStateMachine.transitionTo(AppUiState.SHUTDOWN);
                if (loopController != null) loopController.stop();
                if (engine != null && engine.isRunning()) engine.shutdown();
                asyncSaveService.shutdownAndFlush(2, TimeUnit.SECONDS);
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
            startAutoSave();
        } else {
            stopAutoSave();
        }

        revalidate();
        repaint();
        SwingUtilities.invokeLater(canvas::clampZoomAndCamera);
    }

    private void teardownSession() {
        if (isGameplaySession()) {
            saveCurrentWorld();
            flushPendingSaves();
        }
        stopAutoSave();
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
    }

    private void createRuntimeOverlays() {
        InspectorPanel inspectorPanel = new InspectorPanel();
        EnvironmentPanel environmentPanel = new EnvironmentPanel(engine);
        ModernButton envToggleButton = new ModernButton("", ModernButton.ButtonIcon.ENVIRONMENT);
        ModernButton settingsButton = new ModernButton("", ModernButton.ButtonIcon.GEAR);
        ModernButton speedButton = new ModernButton("1x", ModernButton.ButtonIcon.SPEED_UP);
        runtimeSpeedButton = speedButton;

        overlayManager = new OverlayManager(this::getLayeredPane,
                inspectorPanel, environmentPanel, envToggleButton, settingsButton, speedButton);

        envToggleButton.addActionListener(e -> overlayManager.toggleEnvironmentPanel());
        settingsButton.setVisible(false);
        speedButton.addActionListener(e -> speedButton.setDisplayText(loopController.cycleSpeed()));

        inspectorPanel.setVisible(false);
        environmentPanel.setVisible(false);
        overlayManager.repositionAllOverlays();
    }

    private void showMainMenu() {
        if (mainMenuOverlay != null) return;
        if (!transitionOrRecover(AppUiState.PREVIEW_MENU)) return;
        mainMenuOverlay = new MainMenuOverlay(this::showSaveBrowserFromMenu, this::resumeLatestSave);
        updateResumeAvailability();
        getLayeredPane().add(mainMenuOverlay, JLayeredPane.POPUP_LAYER);
        mainMenuOverlay.setBounds(0, 0, getWidth(), getHeight());
        mainMenuOverlay.setVisible(true);
        getLayeredPane().moveToFront(globalSettingsButton);
        getLayeredPane().repaint();
    }

    private void showSaveBrowserFromMenu() {
        removeMainMenu();
        showSaveBrowser(true);
    }

    private void removeMainMenu() {
        if (mainMenuOverlay == null) return;
        getLayeredPane().remove(mainMenuOverlay);
        mainMenuOverlay = null;
        revalidate();
        repaint();
    }

    private void showSaveBrowser(boolean reopenMainMenuOnClose) {
        if (saveBrowserOverlay != null) return;
        if (!transitionOrRecover(AppUiState.SAVE_BROWSER)) return;
        // Defensive: avoid stale menu controls being visible under/over the browser.
        removeMainMenu();
        reopenMainMenuOnBrowserClose = reopenMainMenuOnClose;
        // Keep menu preview/gameplay running when browser is opened from main menu.
        pausedForSaveBrowser = !reopenMainMenuOnClose && isGameplaySession() && loopController != null;
        if (pausedForSaveBrowser) {
            loopController.pause();
        }
        saveBrowserOverlay = new SaveBrowserOverlay(new SaveBrowserOverlay.Listener() {
            @Override
            public void onCreateRequested(WorldConfig config) {
                createWorldAndStart(config);
            }

            @Override
            public void onPlayRequested(SaveGameMetadata metadata) {
                loadSaveAndStart(metadata);
            }

            @Override
            public void onDeleteRequested(SaveGameMetadata metadata) {
                deleteSaveAsync(metadata);
            }

            @Override
            public void onBackRequested() {
                removeSaveBrowser(reopenMainMenuOnBrowserClose);
            }
        });
        getLayeredPane().add(saveBrowserOverlay, JLayeredPane.POPUP_LAYER);
        int topY = getContentTopY();
        saveBrowserOverlay.setBounds(0, topY, getWidth(), Math.max(0, getHeight() - topY));
        getLayeredPane().moveToFront(globalSettingsButton);
        refreshSaveBrowserAsync();
        revalidate();
        repaint();
    }

    private void refreshSaveBrowserAsync() {
        if (saveBrowserOverlay == null) return;
        SwingWorker<java.util.List<SaveGameMetadata>, Void> worker = new SwingWorker<>() {
            @Override
            protected java.util.List<SaveGameMetadata> doInBackground() throws Exception {
                return saveRepository.listSaves();
            }

            @Override
            protected void done() {
                if (saveBrowserOverlay == null) return;
                try {
                    saveBrowserOverlay.setSaves(get());
                    updateResumeAvailability();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(BioLabSimulatorApp.this,
                            "Failed to list saves: " + ex.getMessage(),
                            "Save Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void deleteSaveAsync(SaveGameMetadata metadata) {
        if (metadata == null) return;
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                saveRepository.deleteSave(metadata.saveId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    refreshSaveBrowserAsync();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(BioLabSimulatorApp.this,
                            "Delete failed: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void removeSaveBrowser(boolean reopenMainMenu) {
        if (saveBrowserOverlay != null) {
            getLayeredPane().remove(saveBrowserOverlay);
            saveBrowserOverlay = null;
        }
        if (pausedForSaveBrowser && loopController != null) {
            loopController.resume();
        }
        pausedForSaveBrowser = false;
        if (reopenMainMenu) {
            showMainMenu();
        } else if (isGameplaySession()) {
            uiStateMachine.transitionTo(AppUiState.GAMEPLAY);
        }
        reopenMainMenuOnBrowserClose = false;
        revalidate();
        repaint();
    }

    private void createWorldAndStart(WorldConfig config) {
        startSimulationSession(config, true);
        transitionOrRecover(AppUiState.GAMEPLAY);
        removeMainMenu();
        removeSaveBrowser(false);
        currentWorldName = config.mapName();

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
                    state.microbes().size(),
                    Math.max(20_000, state.microbes().size() * 3),
                    state.temperature(),
                    state.toxicity(),
                    state.foodSpawnRate()
            );
            startSimulationSession(config, true);
            transitionOrRecover(AppUiState.GAMEPLAY);
            engine.loadState(state);
            currentSave = metadata;
            currentWorldName = metadata.mapName();
            sessionStartMillis = System.currentTimeMillis();

            removeMainMenu();
            removeSaveBrowser(false);
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                    "Load failed: " + ex.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private synchronized void saveCurrentWorld() {
        if (engine == null || !isGameplaySession()) return;
        final SimulationState state = engine.captureState();
        final long playedSeconds = elapsedSessionSeconds();
        final SaveGameMetadata existingSave = currentSave;
        final String worldName = currentWorldName;

        boolean accepted = asyncSaveService.submit(() -> {
            try {
                SaveGameMetadata saved;
                if (existingSave == null) {
                    String mapName = (worldName == null || worldName.isBlank()) ? "Auto Save" : worldName;
                    WorldConfig cfg = new WorldConfig(
                            mapName,
                            state.worldWidth(),
                            state.worldHeight(),
                            state.microbes().size(),
                            Math.max(20_000, state.microbes().size() * 3),
                            state.temperature(),
                            state.toxicity(),
                            state.foodSpawnRate()
                    );
                    saved = saveRepository.createNewSave(cfg, state);
                } else {
                    saveRepository.overwriteSave(existingSave, state, playedSeconds);
                    saved = saveRepository.loadMetadata(existingSave.saveId());
                }

                synchronized (BioLabSimulatorApp.this) {
                    currentSave = saved;
                    sessionStartMillis = System.currentTimeMillis();
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Async save failed", ex);
            }
        });
        if (!accepted) {
            LOGGER.fine("Save submission skipped because save worker is shutting down");
        }
    }

    private void flushPendingSaves() {
        if (!asyncSaveService.flushAndWait(2, TimeUnit.SECONDS)) {
            LOGGER.warning("Timed out while flushing pending saves");
        }
    }

    private long elapsedSessionSeconds() {
        if (sessionStartMillis <= 0L) return 0L;
        return Math.max(0L, (System.currentTimeMillis() - sessionStartMillis) / 1000L);
    }

    private void startAutoSave() {
        stopAutoSave();
        autosaveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BioLab-Autosave");
            t.setDaemon(true);
            return t;
        });
        autosaveTask = autosaveExecutor.scheduleAtFixedRate(
                this::saveCurrentWorld,
                AUTOSAVE_INTERVAL_SECONDS,
                AUTOSAVE_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void stopAutoSave() {
        if (autosaveTask != null) {
            autosaveTask.cancel(false);
            autosaveTask = null;
        }
        if (autosaveExecutor != null) {
            autosaveExecutor.shutdownNow();
            autosaveExecutor = null;
        }
    }

    private boolean isGameplaySession() {
        return overlayManager != null;
    }

    private boolean hasResumeTarget() {
        try {
            return saveRepository.findMostRecentSave().isPresent();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to query latest save", ex);
            return false;
        }
    }

    private void updateResumeAvailability() {
        if (mainMenuOverlay != null) {
            mainMenuOverlay.setResumeEnabled(hasResumeTarget());
        }
    }

    private void closeApplication() {
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    private void returnToMainMenuFromGameplay() {
        saveCurrentWorld();
        flushPendingSaves();
        removeSettingsOverlay();
        stopAutoSave();
        if (pausedForSettings && loopController != null) {
            loopController.resume();
        }
        pausedForSettings = false;
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
                updateResumeAvailability();
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
        if (settingsOverlay != null) removeSettingsOverlay();
        if (saveBrowserOverlay != null) {
            getLayeredPane().remove(saveBrowserOverlay);
            saveBrowserOverlay = null;
        }
        if (engine == null || canvas == null) {
            startPreviewSession();
        }
        if (overlayManager != null) {
            overlayManager.setGameplayOverlaysVisible(false);
        }
        uiStateMachine.forceState(AppUiState.PREVIEW_MENU);
        if (mainMenuOverlay == null) {
            showMainMenu();
        } else {
            updateResumeAvailability();
        }
        revalidate();
        repaint();
    }

    private void showSettingsOverlay() {
        if (settingsOverlay != null) return;
        if (isGameplaySession()) {
            saveCurrentWorld();
            flushPendingSaves();
        }
        stateBeforeSettings = uiStateMachine.current();
        if (!transitionOrRecover(AppUiState.SETTINGS)) return;
        boolean mainMenuVisible = mainMenuOverlay != null;
        pausedForSettings = loopController != null;
        if (pausedForSettings) {
            loopController.pause();
        }
        Runnable closeAction = mainMenuVisible ? this::closeApplication : this::returnToMainMenuFromGameplay;
        settingsOverlay = new SettingsOverlay(settingsManager,
                this::applySettingsAndClose,   // APPLY button
                this::cancelSettingsAndClose,
                closeAction);  // CLOSE
        getLayeredPane().add(settingsOverlay, SETTINGS_LAYER);
        int topY = getContentTopY();
        settingsOverlay.setBounds(0, topY, getWidth(), Math.max(0, getHeight() - topY));
        settingsOverlay.setVisible(true);
        getLayeredPane().moveToFront(settingsOverlay);
        settingsOverlay.requestFocusInWindow();
        revalidate();
        repaint();
    }

    /**
     * Called when the user clicks APPLY – always re-applies display mode.
     */
    private void applySettingsAndClose() {
        removeSettingsOverlay();
        uiStateMachine.transitionTo(stateBeforeSettings);
        windowWidth = settingsManager.getWindowWidth();
        windowHeight = settingsManager.getWindowHeight();
        if (loopController != null) loopController.setRenderFps(settingsManager.getSimulationFps());
        applyDisplayMode();           // always – even if nothing changed
        if (pausedForSettings && loopController != null) loopController.resume();
        pausedForSettings = false;
        revalidate();
        repaint();
    }

    /**
     * Called when the user clicks CANCEL or presses ESC – no display change.
     */
    private void cancelSettingsAndClose() {
        removeSettingsOverlay();
        uiStateMachine.transitionTo(stateBeforeSettings);
        if (pausedForSettings && loopController != null) loopController.resume();
        pausedForSettings = false;
        revalidate();
        repaint();
    }

    private void removeSettingsOverlay() {
        if (settingsOverlay == null) return;
        getLayeredPane().remove(settingsOverlay);
        settingsOverlay = null;
    }

    private boolean isSelectionBlockedByUi() {
        return mainMenuOverlay != null
                || saveBrowserOverlay != null
                || settingsOverlay != null
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
