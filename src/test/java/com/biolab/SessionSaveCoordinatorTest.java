package com.biolab;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSaveCoordinatorTest {

    @Test
    void saveCurrentWorldShouldSwallowCaptureStateRuntimeException() throws Exception {
        Path tempRoot = Files.createTempDirectory("biolab-save-test-");
        AsyncSaveService asyncSaveService = new AsyncSaveService();
        SessionSaveCoordinator coordinator = new SessionSaveCoordinator(
                new SaveGameRepository(tempRoot, new SimulationStateService()),
                asyncSaveService,
                Logger.getLogger("SessionSaveCoordinatorTest"),
                1
        );

        SimulationRuntime throwingRuntime = new ThrowingCaptureRuntime();

        try {
            assertDoesNotThrow(() -> coordinator.saveCurrentWorld(throwingRuntime, true));
        } finally {
            coordinator.shutdown();
        }
    }

    @Test
    void autosaveSchedulerShouldContinueAfterCaptureStateRuntimeException() throws Exception {
        Path tempRoot = Files.createTempDirectory("biolab-autosave-test-");
        AsyncSaveService asyncSaveService = new AsyncSaveService();
        SessionSaveCoordinator coordinator = new SessionSaveCoordinator(
                new SaveGameRepository(tempRoot, new SimulationStateService()),
                asyncSaveService,
                Logger.getLogger("SessionSaveCoordinatorTest"),
                1
        );

        AtomicInteger captureAttempts = new AtomicInteger();
        SimulationRuntime throwingRuntime = new ThrowingCaptureRuntime(captureAttempts);

        try {
            coordinator.startAutoSave(() -> throwingRuntime, () -> true);
            Thread.sleep(2400);
            coordinator.stopAutoSave();
            assertTrue(captureAttempts.get() >= 2,
                    "Autosave scheduler should continue ticking even if captureState throws runtime exceptions");
        } finally {
            coordinator.shutdown();
        }
    }

    private record ThrowingCaptureRuntime(AtomicInteger attempts) implements SimulationRuntime {
            private ThrowingCaptureRuntime() {
                this(new AtomicInteger());
            }

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
                return 0.0;
            }

            @Override
            public void setFoodSpawnRate(double rate) {
            }

            @Override
            public WorldStatsStore getWorldStatsStore() {
                return null;
            }

            @Override
            public SimulationState captureState() {
                attempts.incrementAndGet();
                throw new RuntimeException("capture failed");
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

