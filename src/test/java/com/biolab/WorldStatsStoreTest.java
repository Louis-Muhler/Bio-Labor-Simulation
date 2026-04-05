package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldStatsStoreTest {

    @Test
    void queryShouldReturnSelectedMetricOnlyAndOrderedByTime() {
        WorldStatsStore store = new WorldStatsStore(32);

        for (int i = 0; i < 10; i++) {
            double[] values = new double[WorldMetricId.values().length];
            values[WorldMetricId.POPULATION_ALIVE.ordinal()] = i;
            values[WorldMetricId.TEMPERATURE.ordinal()] = i * 2;
            store.append(1_000L + i * 100L, i, values);
        }

        List<WorldStatsSample> result = store.queryRange(
                EnumSet.of(WorldMetricId.POPULATION_ALIVE),
                1_000L,
                2_000L,
                100
        );

        assertEquals(10, result.size());
        for (int i = 0; i < result.size(); i++) {
            WorldStatsSample sample = result.get(i);
            assertEquals(1_000L + i * 100L, sample.timestampMillis());
            assertEquals(i, sample.tick());
            assertTrue(sample.metricValues().containsKey(WorldMetricId.POPULATION_ALIVE));
            assertFalse(sample.metricValues().containsKey(WorldMetricId.TEMPERATURE));
        }
    }

    @Test
    void downsampleShouldHonorMaxPointsAndKeepRangeBoundaries() {
        WorldStatsStore store = new WorldStatsStore(512);

        for (int i = 0; i < 120; i++) {
            double[] values = new double[WorldMetricId.values().length];
            values[WorldMetricId.POPULATION_ALIVE.ordinal()] = Math.sin(i / 10.0) * 50.0 + 100.0;
            store.append(10_000L + i * 100L, i, values);
        }

        List<WorldStatsSample> result = store.queryRange(
                EnumSet.of(WorldMetricId.POPULATION_ALIVE),
                10_000L,
                30_000L,
                40
        );

        assertTrue(result.size() <= 40, "Downsampling darf maxPoints nicht ueberschreiten");
        assertEquals(10_000L, result.get(0).timestampMillis());
        assertEquals(21_900L, result.get(result.size() - 1).timestampMillis());
    }

    @Test
    void ringBufferShouldDiscardOldestSamplesWhenCapacityExceeded() {
        WorldStatsStore store = new WorldStatsStore(12);

        for (int i = 0; i < 20; i++) {
            double[] values = new double[WorldMetricId.values().length];
            values[WorldMetricId.POPULATION_ALIVE.ordinal()] = i;
            store.append(1_000L + i, i, values);
        }

        List<WorldStatsSample> result = store.queryRange(
                EnumSet.of(WorldMetricId.POPULATION_ALIVE),
                0L,
                Long.MAX_VALUE,
                100
        );

        assertEquals(12, result.size());
        assertEquals(1_008L, result.get(0).timestampMillis());
        assertEquals(1_019L, result.get(result.size() - 1).timestampMillis());
    }
}

