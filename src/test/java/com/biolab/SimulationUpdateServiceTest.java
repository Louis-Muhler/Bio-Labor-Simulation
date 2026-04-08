package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SimulationUpdateServiceTest {

    private static SimulationUpdateService createService(WorldState worldState,
                                                         ExecutorService executorService,
                                                         SimulationRuntime runtime) {
        AtomicInteger reproductionSlots = new AtomicInteger();
        FramePreparationSystem prep = new FramePreparationSystem(worldState, reproductionSlots, 1000, 600, 600, 2000);
        MicrobeBehaviorSystem behaviorSystem = new MicrobeBehaviorSystem(
                600,
                600,
                reproductionSlots,
                worldState.population().newMicrobes()
        );
        SimulationFrameOrchestrator orchestrator = new SimulationFrameOrchestrator(
                executorService,
                2,
                new SpatialGrid(600, 600, 30),
                new MicrobeGrid(600, 600, 30),
                behaviorSystem,
                new PopulationCommitSystem(worldState, 1000),
                Logger.getLogger("SimulationUpdateServiceTest")
        );

        return new SimulationUpdateService(
                new Object(),
                executorService,
                new SimulationCommandProcessor(128),
                runtime,
                new Environment(),
                prep,
                orchestrator,
                Logger.getLogger("SimulationUpdateServiceTest")
        );
    }

    @Test
    void workerFailureShouldAbortCommitAndKeepLastKnownGoodSnapshot() {
        WorldState worldState = new WorldState();
        Microbe broken = new ThrowingMicrobe(100, 100);
        worldState.population().microbes().add(broken);
        worldState.index().byId().put(broken.getId(), broken);
        NoopRuntime runtime = new NoopRuntime();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            SimulationUpdateService service = createService(worldState, executor, runtime);
            SimulationFrameResult result = service.runUpdate(new SimulationSnapshot(List.of(), List.of()), 0.0);

            assertEquals(0, result.snapshot().microbes().size(), "Failure path must keep last known-good snapshot");
            assertEquals(0, result.spawnedFoodCount());
            assertEquals(0, result.consumedFoodCount());
            assertFalse(runtime.isRunning(), "Runtime should be stopped after worker failure");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void interruptDuringFrameShouldPreserveInterruptFlagAndKeepLastKnownGoodSnapshot() {
        WorldState worldState = new WorldState();
        Microbe microbe = new Microbe(120, 120);
        worldState.population().microbes().add(microbe);
        worldState.index().byId().put(microbe.getId(), microbe);
        NoopRuntime runtime = new NoopRuntime();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            SimulationUpdateService service = createService(worldState, executor, runtime);

            Thread.currentThread().interrupt();
            SimulationFrameResult result = service.runUpdate(new SimulationSnapshot(List.of(), List.of()), 0.0);

            assertTrue(Thread.currentThread().isInterrupted(), "Interrupt flag should be preserved");
            assertEquals(0, result.snapshot().microbes().size(), "Interrupted path must keep last known-good snapshot");
            assertFalse(runtime.isRunning(), "Runtime should be stopped after interrupted frame abort");
        } finally {
            // Clear for test isolation.
            Thread.interrupted();
            executor.shutdownNow();
        }
    }

    private static final class ThrowingMicrobe extends Microbe {
        ThrowingMicrobe(double x, double y) {
            super(x, y);
        }

        @Override
        public void move(int width, int height) {
            throw new RuntimeException("boom");
        }
    }

    private static final class NoopRuntime implements SimulationRuntime {
        private volatile boolean running = true;

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
            return null;
        }

        @Override
        public SimulationState captureState() {
            return null;
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
            return running;
        }

        @Override
        public void shutdown() {
            running = false;
        }
    }
}
