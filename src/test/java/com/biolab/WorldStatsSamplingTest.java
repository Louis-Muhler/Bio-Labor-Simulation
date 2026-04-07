package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStatsSamplingTest {
    private static long persistedIdCounter = 2_000_000L;

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

    private static Microbe createPersistedMicrobe(double maxHealth,
                                                  double maxEnergy,
                                                  double health,
                                                  double energy,
                                                  int age) {
        long id = persistedIdCounter++;
        Microbe.PersistedState state = new Microbe.PersistedState(
                id,
                -1,
                1,
                100.0,
                100.0,
                0.0,
                0.0,
                0.5,
                0.5,
                0.0,
                1.0,
                maxHealth,
                maxEnergy,
                health,
                energy,
                age,
                false,
                0L,
                -1.0,
                -1.0,
                AiState.WANDER,
                0L,
                List.of()
        );
        return Microbe.fromPersistedState(state);
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

    @Test
    void samplingShouldProvideAbsoluteAndPercentAveragesForHealthAndEnergy() {
        SimulationEngine engine = new SimulationEngine(300, 300, 0);
        try {
            engine.getEnvironment().setTemperature(0.0);
            engine.getEnvironment().setToxicity(0.0);

            Microbe microbe = createPersistedMicrobe(200.0, 150.0, 80.0, 120.0, 0);
            engine.spawnMicrobe(microbe);

            for (int i = 0; i < 30; i++) {
                engine.update();
            }

            List<WorldStatsSample> samples = engine.getWorldStatsStore().queryRangeByTick(
                    EnumSet.of(
                            WorldMetricId.AVG_HEALTH_PERCENT,
                            WorldMetricId.AVG_ENERGY_PERCENT,
                            WorldMetricId.AVG_HEALTH_ABSOLUTE,
                            WorldMetricId.AVG_ENERGY_ABSOLUTE
                    ),
                    30,
                    30,
                    1
            );

            assertEquals(1, samples.size());
            WorldStatsSample sample = samples.get(0);
            double expectedEnergy = engine.captureState().microbes().get(0).energy();

            assertEquals(80.0, sample.metricValues().get(WorldMetricId.AVG_HEALTH_ABSOLUTE), 0.001);
            assertEquals(expectedEnergy, sample.metricValues().get(WorldMetricId.AVG_ENERGY_ABSOLUTE), 0.001);
            assertEquals(40.0, sample.metricValues().get(WorldMetricId.AVG_HEALTH_PERCENT), 0.001);
            assertEquals((expectedEnergy / 150.0) * 100.0,
                    sample.metricValues().get(WorldMetricId.AVG_ENERGY_PERCENT),
                    0.001);
        } finally {
            engine.shutdown();
        }
    }
}

