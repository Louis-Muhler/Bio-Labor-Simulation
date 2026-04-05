package com.biolab;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldStatsPanelRegistryIntegrationTest {

    @Test
    void panelShouldRenderAllMetricsFromRegistryWithoutPanelCodeChanges() throws Exception {
        AtomicReference<WorldStatsPanel> panelRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            WorldStatsStore store = new WorldStatsStore(128);
            FakeRuntime runtime = new FakeRuntime(store);
            panelRef.set(new WorldStatsPanel(runtime));
        });

        WorldStatsPanel panel = panelRef.get();
        Set<WorldMetricId> expectedIds = WorldMetricRegistry.definitions().stream()
                .map(WorldMetricDefinition::id)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(expectedIds.size(), panel.getRenderedMetricOptionCountForTest());
        assertEquals(expectedIds, panel.getRenderedMetricIdsForTest());
    }

    private record FakeRuntime(WorldStatsStore store) implements SimulationRuntime {

        @Override
            public SimulationSnapshot getRenderSnapshot() {
                return new SimulationSnapshot(List.of(), List.of());
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
                return new Environment();
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

