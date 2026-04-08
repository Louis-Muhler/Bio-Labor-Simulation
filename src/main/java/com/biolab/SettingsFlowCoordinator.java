package com.biolab;

import javax.swing.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Encapsulates settings overlay lifecycle, pause/resume behavior, and state transitions.
 */
final class SettingsFlowCoordinator {
    private static final Integer SETTINGS_LAYER = JLayeredPane.DRAG_LAYER + 200;

    private final JFrame hostFrame;
    private final SettingsManager settingsManager;
    private final SessionSaveCoordinator sessionSaveCoordinator;
    private final Supplier<SimulationRuntime> engineSupplier;
    private final BooleanSupplier gameplaySessionSupplier;
    private final Supplier<SimulationLoopController> loopSupplier;
    private final Supplier<Integer> contentTopYSupplier;
    private final AppUiStateMachine uiStateMachine;
    private final Predicate<AppUiState> transitionOrRecover;
    private final Runnable onReturnToMainMenu;
    private final Runnable onApplySettings;

    private SettingsOverlay settingsOverlay;
    private AppUiState stateBeforeSettings = AppUiState.BOOT;
    private boolean pausedForSettings;

    SettingsFlowCoordinator(JFrame hostFrame,
                            SettingsManager settingsManager,
                            SessionSaveCoordinator sessionSaveCoordinator,
                            Supplier<SimulationRuntime> engineSupplier,
                            BooleanSupplier gameplaySessionSupplier,
                            Supplier<SimulationLoopController> loopSupplier,
                            Supplier<Integer> contentTopYSupplier,
                            AppUiStateMachine uiStateMachine,
                            Predicate<AppUiState> transitionOrRecover,
                            Runnable onReturnToMainMenu,
                            Runnable onApplySettings) {
        this.hostFrame = hostFrame;
        this.settingsManager = settingsManager;
        this.sessionSaveCoordinator = sessionSaveCoordinator;
        this.engineSupplier = engineSupplier;
        this.gameplaySessionSupplier = gameplaySessionSupplier;
        this.loopSupplier = loopSupplier;
        this.contentTopYSupplier = contentTopYSupplier;
        this.uiStateMachine = uiStateMachine;
        this.transitionOrRecover = transitionOrRecover;
        this.onReturnToMainMenu = onReturnToMainMenu;
        this.onApplySettings = onApplySettings;
    }

    void showSettingsOverlay() {
        if (settingsOverlay != null) return;

        if (gameplaySessionSupplier.getAsBoolean()) {
            SimulationRuntime runtime = engineSupplier.get();
            SwingWorker<Void, Void> preSettingsSave = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    sessionSaveCoordinator.saveCurrentWorld(runtime, true);
                    return null;
                }
            };
            preSettingsSave.execute();
        }

        stateBeforeSettings = uiStateMachine.current();
        if (!transitionOrRecover.test(AppUiState.SETTINGS)) return;

        SimulationLoopController loop = loopSupplier.get();
        pausedForSettings = loop != null;
        if (pausedForSettings) {
            loop.pause();
        }

        settingsOverlay = new SettingsOverlay(
                settingsManager,
                this::applyAndClose,
                this::cancelAndClose,
                onReturnToMainMenu
        );
        JLayeredPane layeredPane = hostFrame.getLayeredPane();
        layeredPane.add(settingsOverlay, SETTINGS_LAYER);
        int topY = contentTopYSupplier.get();
        settingsOverlay.setBounds(0, topY, hostFrame.getWidth(), Math.max(0, hostFrame.getHeight() - topY));
        settingsOverlay.setVisible(true);
        layeredPane.moveToFront(settingsOverlay);
        settingsOverlay.requestFocusInWindow();
        hostFrame.revalidate();
        hostFrame.repaint();
    }

    void refreshOverlayBounds(int width, int height) {
        if (settingsOverlay == null) return;
        int topY = contentTopYSupplier.get();
        settingsOverlay.setBounds(0, topY, width, Math.max(0, height - topY));
        settingsOverlay.revalidate();
    }

    boolean isSettingsVisible() {
        return settingsOverlay != null;
    }

    boolean settingsOverGameplay(AppUiState currentState) {
        return currentState == AppUiState.SETTINGS && stateBeforeSettings == AppUiState.GAMEPLAY;
    }

    void closeForMenuReturn() {
        removeSettingsOverlay();
        resumeIfPaused();
    }

    void clearOverlayAndResume() {
        removeSettingsOverlay();
        resumeIfPaused();
    }

    private void applyAndClose() {
        removeSettingsOverlay();
        uiStateMachine.transitionTo(stateBeforeSettings);
        onApplySettings.run();
        resumeIfPaused();
        hostFrame.revalidate();
        hostFrame.repaint();
    }

    private void cancelAndClose() {
        removeSettingsOverlay();
        uiStateMachine.transitionTo(stateBeforeSettings);
        resumeIfPaused();
        hostFrame.revalidate();
        hostFrame.repaint();
    }

    private void removeSettingsOverlay() {
        if (settingsOverlay == null) return;
        hostFrame.getLayeredPane().remove(settingsOverlay);
        settingsOverlay = null;
    }

    private void resumeIfPaused() {
        if (pausedForSettings) {
            SimulationLoopController loop = loopSupplier.get();
            if (loop != null) {
                loop.resume();
            }
        }
        pausedForSettings = false;
    }
}

