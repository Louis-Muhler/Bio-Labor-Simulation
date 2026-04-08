package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationLoopControllerTest {

    @Test
    void cycleSpeedShouldIncludeMaxStageAndWrapAround() {
        SimulationLoopController controller = new SimulationLoopController(
                null,
                null,
                () -> {
                },
                population -> {
                }
        );

        assertEquals("2x", controller.cycleSpeed());
        assertEquals("5x", controller.cycleSpeed());
        assertEquals("10x", controller.cycleSpeed());
        assertEquals("25x", controller.cycleSpeed());
        assertEquals("50x", controller.cycleSpeed());
        assertEquals("100x", controller.cycleSpeed());
        assertEquals("MAX", controller.cycleSpeed());
        assertEquals("1x", controller.cycleSpeed());
    }

    @Test
    void resetSpeedShouldAlwaysReturnOneX() {
        SimulationLoopController controller = new SimulationLoopController(
                null,
                null,
                () -> {
                },
                population -> {
                }
        );

        controller.cycleSpeed();
        controller.cycleSpeed();
        assertEquals("1x", controller.resetSpeedToDefault());
        assertEquals("2x", controller.cycleSpeed());
    }

    @Test
    void updateRuntimeExceptionShouldStopLoopThreadCleanly() throws Exception {
        CountDownLatch firstUpdateAttempt = new CountDownLatch(1);
        SimulationRuntime throwingRuntime = new SimulationRuntime() {
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
                firstUpdateAttempt.countDown();
                throw new RuntimeException("boom");
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
        };

        SimulationLoopController controller = new SimulationLoopController(
                throwingRuntime,
                null,
                () -> {
                },
                population -> {
                }
        );

        controller.start();
        assertTrue(firstUpdateAttempt.await(1, TimeUnit.SECONDS), "Engine update should be attempted");
        assertTrue(controller.stopAndAwait(1_000), "Loop should be stoppable after runtime exception");
        assertTrue(controller.stopAndAwait(100), "Second stop should be idempotent when loop already ended");
    }
}


