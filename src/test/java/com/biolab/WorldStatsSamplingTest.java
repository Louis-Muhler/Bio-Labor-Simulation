package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

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
        return createPersistedMicrobe(maxHealth, maxEnergy, health, energy, age, 0.5, 0.5, 0.0, 1.0);
    }

    private static Microbe createPersistedMicrobe(double maxHealth,
                                                  double maxEnergy,
                                                  double health,
                                                  double energy,
                                                  int age,
                                                  double heatResistance,
                                                  double toxinResistance,
                                                  double speed,
                                                  double diet) {
        long id = persistedIdCounter++;
        Microbe.PersistedState state = new Microbe.PersistedState(
                id,
                -1,
                1,
                100.0,
                100.0,
                0.0,
                0.0,
                heatResistance,
                toxinResistance,
                speed,
                diet,
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

            List<WorldStatsSample> preFlushSamples = engine.getWorldStatsStore().queryRangeByTick(
                    EnumSet.of(WorldMetricId.AVG_HEALTH_PERCENT),
                    30,
                    30,
                    1
            );
            assertTrue(
                    preFlushSamples.isEmpty(),
                    "Before captureState(), tick-30 should not yet be visible in the async store"
            );

            // Flush async appender so tick-30 sample is visible to direct store queries.
            List<WorldStatsSample> flushedHistory = engine.captureState().worldStatsHistory();
            assertTrue(
                    flushedHistory.stream().anyMatch(sample -> sample.tick() == 30L),
                    "captureState() should flush async stats so the tick-30 sample is visible"
            );

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
            assertEquals(30L, sample.tick(), "Store query should return the flushed tick-30 sample");
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

    private static WorldStatsSample historySample(long tick,
                                                  double avgDietPercent,
                                                  double avgSpeedPercent,
                                                  double avgHeatPercent,
                                                  double avgToxinPercent) {
        Map<WorldMetricId, Double> values = new EnumMap<>(WorldMetricId.class);
        values.put(WorldMetricId.AVG_DIET, avgDietPercent);
        values.put(WorldMetricId.AVG_SPEED, avgSpeedPercent);
        values.put(WorldMetricId.AVG_HEAT_RESISTANCE, avgHeatPercent);
        values.put(WorldMetricId.AVG_TOXIN_RESISTANCE, avgToxinPercent);
        return new WorldStatsSample(System.currentTimeMillis(), tick, values);
    }

    @Test
    void samplingShouldProvideDerivedStrengthAndDefenseInPercentRange() {
        SimulationEngine engine = new SimulationEngine(300, 300, 0);
        try {
            engine.getEnvironment().setTemperature(0.0);
            engine.getEnvironment().setToxicity(0.0);

            Microbe microbe = createPersistedMicrobe(
                    120.0,
                    110.0,
                    120.0,
                    110.0,
                    0,
                    0.6,
                    0.2,
                    0.4,
                    0.8
            );
            engine.spawnMicrobe(microbe);

            for (int i = 0; i < 30; i++) {
                engine.update();
            }

            List<WorldStatsSample> preFlushSamples = engine.getWorldStatsStore().queryRangeByTick(
                    EnumSet.of(WorldMetricId.AVG_STRENGTH),
                    30,
                    30,
                    1
            );
            assertTrue(
                    preFlushSamples.isEmpty(),
                    "Before captureState(), tick-30 should not yet be visible in the async store"
            );

            // Flush async appender so tick-30 sample is visible to direct store queries.
            List<WorldStatsSample> flushedHistory = engine.captureState().worldStatsHistory();
            assertTrue(
                    flushedHistory.stream().anyMatch(sample -> sample.tick() == 30L),
                    "captureState() should flush async stats so the tick-30 sample is visible"
            );

            List<WorldStatsSample> samples = engine.getWorldStatsStore().queryRangeByTick(
                    EnumSet.of(WorldMetricId.AVG_STRENGTH, WorldMetricId.AVG_DEFENSE),
                    30,
                    30,
                    1
            );

            assertEquals(1, samples.size());
            WorldStatsSample sample = samples.get(0);
            assertEquals(30L, sample.tick(), "Store query should return the flushed tick-30 sample");
            double sampledStrength = sample.metricValues().get(WorldMetricId.AVG_STRENGTH);
            double sampledDefense = sample.metricValues().get(WorldMetricId.AVG_DEFENSE);

            assertTrue(sampledStrength >= 0.0 && sampledStrength <= 100.0);
            assertTrue(sampledDefense >= 0.0 && sampledDefense <= 100.0);
            assertEquals(74.0, sampledStrength, 0.01);
            assertEquals(40.0, sampledDefense, 0.01);
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void loadStateShouldBackfillDerivedTraitHistoryOnceAcrossFullLoadedRange() {
        SimulationEngine engine = new SimulationEngine(300, 300, 0);
        try {
            List<WorldStatsSample> history = List.of(
                    historySample(30, 80.0, 20.0, 25.0, 75.0),
                    historySample(60, 10.0, 90.0, 70.0, 20.0)
            );
            SimulationState loaded = new SimulationState(
                    300,
                    300,
                    0.1,
                    0.2,
                    0.3,
                    60,
                    history,
                    List.of(),
                    List.of(),
                    false
            );

            engine.loadState(loaded);

            List<WorldStatsSample> samples = engine.getWorldStatsStore().queryRangeByTick(
                    EnumSet.of(WorldMetricId.AVG_STRENGTH, WorldMetricId.AVG_DEFENSE),
                    30,
                    60,
                    Integer.MAX_VALUE
            );

            assertEquals(2, samples.size());
            assertEquals(80.0, samples.get(0).metricValues().get(WorldMetricId.AVG_STRENGTH), 0.0001);
            assertEquals(50.0, samples.get(0).metricValues().get(WorldMetricId.AVG_DEFENSE), 0.0001);
            assertEquals(10.0, samples.get(1).metricValues().get(WorldMetricId.AVG_STRENGTH), 0.0001);
            assertEquals(45.0, samples.get(1).metricValues().get(WorldMetricId.AVG_DEFENSE), 0.0001);
        } finally {
            engine.shutdown();
        }
    }
}

