package com.biolab;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayManagerLayoutTest {

    @Test
    void leftViewersShouldShareTopAndLeftAlignment() {
        OverlayBundle bundle = newBundle(1280, 800, false);

        bundle.manager.positionInspectorPanel();
        bundle.manager.positionEnvironmentPanel();
        bundle.manager.positionWorldStatsPanel();

        Rectangle env = bundle.environmentPanel.getBounds();
        Rectangle stats = bundle.worldStatsPanel.getBounds();
        Rectangle inspector = bundle.inspectorPanel.getBounds();

        assertEquals(env.x, stats.x);
        assertEquals(env.y, stats.y);
        assertEquals(inspector.y, stats.y);
    }

    @Test
    void worldStatsShouldClampAgainstInspectorAreaAndWindowResize() {
        OverlayBundle bundle = newBundle(900, 600, true);

        bundle.worldStatsPanel.showPanel();
        bundle.manager.repositionAllOverlays();

        Rectangle firstStats = bundle.worldStatsPanel.getBounds();
        Rectangle firstInspector = bundle.inspectorPanel.getBounds();
        assertTrue(firstStats.x + firstStats.width <= firstInspector.x - 12,
                "WorldStats must not overlap the specimen area");

        bundle.layeredPane.setSize(760, 520);
        bundle.manager.repositionAllOverlays();

        Rectangle resizedStats = bundle.worldStatsPanel.getBounds();
        Rectangle resizedInspector = bundle.inspectorPanel.getBounds();
        assertTrue(resizedStats.x + resizedStats.width <= resizedInspector.x - 12,
                "WorldStats must remain clamped after resize");
        assertTrue(resizedStats.y + resizedStats.height <= bundle.layeredPane.getHeight() - 15,
                "WorldStats must remain inside bottom window bounds");
    }

    private OverlayBundle newBundle(int width, int height, boolean hugeSavedStatsSize) {
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setSize(width, height);

        SimulationRuntime runtime = new FakeRuntime();

        SettingsManager settingsManager = null;
        if (hugeSavedStatsSize) {
            try {
                Path tempDir = Files.createTempDirectory("overlay-layout-settings-");
                settingsManager = new SettingsManager(tempDir);
                settingsManager.setWorldStatsViewerWidth(4_000);
                settingsManager.setWorldStatsViewerHeight(3_000);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        InspectorPanel inspectorPanel = new InspectorPanel();
        EnvironmentPanel environmentPanel = new EnvironmentPanel(runtime);
        WorldStatsPanel worldStatsPanel = settingsManager == null
                ? new WorldStatsPanel(runtime)
                : new WorldStatsPanel(runtime, settingsManager);

        ModernButton envButton = new ModernButton("", ModernButton.ButtonIcon.ENVIRONMENT);
        ModernButton statsButton = new ModernButton("", ModernButton.ButtonIcon.CHART);
        ModernButton speedButton = new ModernButton("1x", ModernButton.ButtonIcon.SPEED_UP);

        OverlayManager manager = new OverlayManager(
                () -> layeredPane,
                inspectorPanel,
                environmentPanel,
                worldStatsPanel,
                envButton,
                statsButton,
                speedButton
        );

        return new OverlayBundle(layeredPane, manager, inspectorPanel, environmentPanel, worldStatsPanel);
    }

    private record OverlayBundle(
            JLayeredPane layeredPane,
            OverlayManager manager,
            InspectorPanel inspectorPanel,
            EnvironmentPanel environmentPanel,
            WorldStatsPanel worldStatsPanel
    ) {
    }

    private static final class FakeRuntime implements SimulationRuntime {
        private final WorldStatsStore store = new WorldStatsStore();
        private final Environment environment = new Environment();

        @Override
        public SimulationSnapshot getRenderSnapshot() {
            return new SimulationSnapshot(java.util.List.of(), java.util.List.of());
        }

        @Override
        public Microbe findLivingChild(long parentId) {
            return null;
        }

        @Override
        public Microbe findRandomLivingMicrobe() {
            return null;
        }

        @Override
        public Microbe findMicrobeById(long id) {
            return null;
        }

        @Override
        public int getPopulationCount() {
            return 0;
        }

        @Override
        public Environment getEnvironment() {
            return environment;
        }

        @Override
        public double getFoodSpawnRate() {
            return 0;
        }

        @Override
        public void setFoodSpawnRate(double rate) {
        }

        @Override
        public WorldStatsStore getWorldStatsStore() {
            return store;
        }

        @Override
        public SimulationState captureState() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDebugModeEnabled() {
            return false;
        }

        @Override
        public void update() {
        }

        @Override
        public void loadState(SimulationState state) {
        }

        @Override
        public void spawnMicrobe(Microbe microbe) {
        }

        @Override
        public void enqueueCommand(SimulationCommand command) {
        }

        @Override
        public boolean toggleDebugModeFlag() {
            return false;
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public void shutdown() {
        }
    }
}

