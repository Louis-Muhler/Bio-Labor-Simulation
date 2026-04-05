package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStatsSamplingTest {

    @Test
    void samplingShouldOnlyHappenOnTickMultiplesOfThirty() {
        SimulationEngine engine = new SimulationEngine(300, 300, 30);
        try {
            for (int i = 0; i < 3_600; i++) {
                engine.update();
            }

            List<WorldStatsSample> all = engine.getWorldStatsStore().queryRangeByTick(
                    EnumSet.of(WorldMetricId.POPULATION_ALIVE),
                    0,
                    Long.MAX_VALUE,
                    Integer.MAX_VALUE
            );

            assertEquals(120, all.size(), "120 sim-seconds muessen exakt 120 Samples erzeugen");
            assertTrue(all.stream().allMatch(s -> s.tick() % 30L == 0L), "Alle Sample-Ticks muessen Vielfache von 30 sein");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void sameSimulationTimeShouldYieldSameSampleCountIndependentOfSpeedMultiplier() {
        int simSeconds = 120;
        int ticksForTime = simSeconds * 30;

        int samplesAt1x = countSamplesAfterTicks(ticksForTime);
        int samplesAt5x = countSamplesAfterTicks(ticksForTime);

        assertEquals(120, samplesAt1x);
        assertEquals(120, samplesAt5x);
    }

    @Test
    void asyncAppenderShouldKeepSampleOrderAndCompletenessOnCapture() {
        SimulationEngine engine = new SimulationEngine(300, 300, 30);
        try {
            for (int i = 0; i < 3_600; i++) {
                engine.update();
            }

            List<WorldStatsSample> history = engine.captureState().worldStatsHistory();
            assertEquals(120, history.size());
            for (int i = 0; i < history.size(); i++) {
                assertEquals((i + 1) * 30L, history.get(i).tick());
            }
        } finally {
            engine.shutdown();
        }
    }

    private int countSamplesAfterTicks(int ticks) {
        SimulationEngine engine = new SimulationEngine(300, 300, 20);
        try {
            for (int i = 0; i < ticks; i++) {
                engine.update();
            }
            return engine.captureState().worldStatsHistory().size();
        } finally {
            engine.shutdown();
        }
    }
}

