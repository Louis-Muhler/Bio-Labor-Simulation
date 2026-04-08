package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Logger;
/**
 * Owns gameplay-session runtime objects (engine, canvas, overlays, loop) and their lifecycle.
 */
final class SimulationSessionCoordinator {
    private static final Logger LOGGER = Logger.getLogger(SimulationSessionCoordinator.class.getName());
    private static final long LOOP_STOP_TIMEOUT_MS = 3_000L;
    private final JFrame hostFrame;
    private final SettingsManager settingsManager;
    private final SessionSaveCoordinator sessionSaveCoordinator;

    private SimulationRuntime engine;
    private SimulationCanvas canvas;
    private OverlayManager overlayManager;
    private SimulationLoopController loopController;
    private ModernButton runtimeSpeedButton;
    private MicrobeCreatorPanel microbeCreatorPanel;
    private boolean spawnToolActive;

    SimulationSessionCoordinator(JFrame hostFrame,
                                 SettingsManager settingsManager,
                                 SessionSaveCoordinator sessionSaveCoordinator) {
        this.hostFrame = hostFrame;
        this.settingsManager = settingsManager;
        this.sessionSaveCoordinator = sessionSaveCoordinator;
    }

    SimulationRuntime engine() {
        return engine;
    }

    SimulationCanvas canvas() {
        return canvas;
    }

    OverlayManager overlayManager() {
        return overlayManager;
    }

    SimulationLoopController loopController() {
        return loopController;
    }

    boolean isGameplaySession() {
        return overlayManager != null;
    }

    void startSession(WorldConfig config,
                      boolean showGameOverlays,
                      JPanel content,
                      SimulationCanvas.SelectionListener selectionListener,
                      Runnable deadSelectionCheck) {
        teardownSession();

        engine = new SimulationEngine(config.worldWidth(), config.worldHeight(),
                config.initialPopulation(), config.maxPopulation());
        engine.getEnvironment().setTemperature(config.temperature());
        engine.getEnvironment().setToxicity(config.toxicity());
        engine.setFoodSpawnRate(config.foodSpawnRate());

        canvas = new SimulationCanvas(config.worldWidth(), config.worldHeight(),
                hostFrame.getWidth(), hostFrame.getHeight(), engine, selectionListener);

        content.removeAll();
        content.add(canvas, BorderLayout.CENTER);

        loopController = new SimulationLoopController(
                engine,
                canvas,
                deadSelectionCheck,
                population -> {
                    OverlayManager manager = overlayManager;
                    if (manager != null) {
                        SwingUtilities.invokeLater(() -> manager.updatePopulationLabel(population));
                    }
                }
        );
        loopController.setRenderFps(showGameOverlays ? settingsManager.getSimulationFps() : 60);
        loopController.start();

        if (showGameOverlays) {
            createRuntimeOverlays();
            sessionSaveCoordinator.startAutoSave(this::engine, this::isGameplaySession);
        } else {
            sessionSaveCoordinator.stopAutoSave();
        }

        hostFrame.revalidate();
        hostFrame.repaint();
        SwingUtilities.invokeLater(() -> {
            if (canvas != null) {
                canvas.clampZoomAndCamera();
            }
        });
    }

    void teardownSession() {
        if (isGameplaySession()) {
            sessionSaveCoordinator.saveCurrentWorld(engine, true);
        }
        deactivateSpawnTool();
        sessionSaveCoordinator.stopAutoSave();
        if (overlayManager != null) {
            overlayManager.removeAllOverlays();
        }
        if (loopController != null) {
            boolean stopped = loopController.stopAndAwait(LOOP_STOP_TIMEOUT_MS);
            if (!stopped) {
                LOGGER.severe("Simulation loop did not stop before engine teardown");
                throw new IllegalStateException("Simulation loop did not stop before engine teardown");
            }
            loopController = null;
        }
        if (engine != null && engine.isRunning()) {
            engine.shutdown();
        }
        engine = null;
        canvas = null;
        overlayManager = null;
        microbeCreatorPanel = null;
        runtimeSpeedButton = null;
        sessionSaveCoordinator.clearSessionContext();
    }

    void deactivateSpawnTool() {
        if (canvas != null) {
            canvas.setPlacementTool(null);
        }
        setSpawnToolActive(false);
    }

    void setGameplayOverlaysVisible(boolean visible) {
        if (overlayManager == null) {
            return;
        }
        overlayManager.setGameplayOverlaysVisible(visible);
    }

    void repositionOverlays() {
        if (overlayManager != null) {
            overlayManager.repositionAllOverlays();
        }
    }

    void resetRuntimeSpeedToDefault() {
        if (loopController != null && runtimeSpeedButton != null) {
            runtimeSpeedButton.setDisplayText(loopController.resetSpeedToDefault());
        }
    }

    void setRenderFps(int fps) {
        if (loopController != null) {
            loopController.setRenderFps(fps);
        }
    }

    private void createRuntimeOverlays() {
        InspectorPanel inspectorPanel = new InspectorPanel();
        EnvironmentPanel environmentPanel = new EnvironmentPanel(engine);
        WorldStatsPanel worldStatsPanel = new WorldStatsPanel(engine, settingsManager);
        microbeCreatorPanel = new MicrobeCreatorPanel();
        microbeCreatorPanel.setRandomProfileSupplier(this::buildWorldAwareRandomBaseProfile);
        ModernButton envToggleButton = new ModernButton("", ModernButton.ButtonIcon.ENVIRONMENT);
        ModernButton statsToggleButton = new ModernButton("", ModernButton.ButtonIcon.CHART);
        ModernButton creatorToggleButton = new ModernButton("", ModernButton.ButtonIcon.CREATOR);
        ModernButton speedButton = new ModernButton("1x", ModernButton.ButtonIcon.SPEED_UP);
        ModernButton deactivateToolButton = new ModernButton("Deactivate Spawn Tool");
        runtimeSpeedButton = speedButton;

        overlayManager = new OverlayManager(hostFrame::getLayeredPane,
                inspectorPanel,
                environmentPanel,
                worldStatsPanel,
                microbeCreatorPanel,
                envToggleButton,
                statsToggleButton,
                creatorToggleButton,
                speedButton,
                deactivateToolButton);

        envToggleButton.addActionListener(e -> overlayManager.toggleEnvironmentPanel());
        statsToggleButton.addActionListener(e -> overlayManager.toggleWorldStatsPanel());
        creatorToggleButton.addActionListener(e -> overlayManager.toggleMicrobeCreatorPanel());
        speedButton.addActionListener(e -> speedButton.setDisplayText(loopController.cycleSpeed()));
        microbeCreatorPanel.setActivateSpawnToolAction(this::toggleSpawnToolFromCreator);
        microbeCreatorPanel.setLayoutRefreshAction(() -> {
            if (overlayManager != null && microbeCreatorPanel != null && microbeCreatorPanel.isVisible()) {
                overlayManager.positionMicrobeCreatorPanel();
            }
        });
        deactivateToolButton.addActionListener(e -> deactivateSpawnTool());

        inspectorPanel.setVisible(false);
        environmentPanel.setVisible(false);
        worldStatsPanel.hidePanel();
        microbeCreatorPanel.setVisible(false);
        setSpawnToolActive(false);
        overlayManager.repositionAllOverlays();
    }

    private MicrobeGeneProfile buildWorldAwareRandomBaseProfile() {
        if (engine == null) {
            return MicrobeSpawnRequest.defaultProfile();
        }
        SimulationState state;
        try {
            state = engine.captureState();
        } catch (RuntimeException ex) {
            return MicrobeSpawnRequest.defaultProfile();
        }
        if (state == null || state.microbes().isEmpty()) {
            return MicrobeSpawnRequest.defaultProfile();
        }
        double heat = 0.0;
        double toxin = 0.0;
        double speed = 0.0;
        double diet = 0.0;
        double maxHealth = 0.0;
        double maxEnergy = 0.0;
        for (Microbe.PersistedState microbe : state.microbes()) {
            heat += microbe.heatResistance();
            toxin += microbe.toxinResistance();
            speed += microbe.speed();
            diet += microbe.diet();
            maxHealth += microbe.maxHealth();
            maxEnergy += microbe.maxEnergy();
        }
        int count = state.microbes().size();
        return new MicrobeGeneProfile(
                heat / count,
                toxin / count,
                speed / count,
                diet / count,
                Math.max(1.0, maxHealth / count),
                Math.max(1.0, maxEnergy / count)
        );
    }

    private void toggleSpawnToolFromCreator() {
        if (spawnToolActive) {
            deactivateSpawnTool();
        } else {
            activateSpawnTool();
        }
    }

    private void activateSpawnTool() {
        if (canvas == null || engine == null || microbeCreatorPanel == null) {
            return;
        }
        canvas.setPlacementTool((worldX, worldY) ->
                engine.enqueueCommand(microbeCreatorPanel.buildSpawnCommand(worldX, worldY))
        );
        setSpawnToolActive(true);
    }

    private void setSpawnToolActive(boolean active) {
        spawnToolActive = active;
        if (microbeCreatorPanel != null) {
            microbeCreatorPanel.setSpawnToolActive(active);
        }
        if (overlayManager != null) {
            overlayManager.setSpawnToolActive(active);
            overlayManager.repositionAllOverlays();
        }
    }
}

