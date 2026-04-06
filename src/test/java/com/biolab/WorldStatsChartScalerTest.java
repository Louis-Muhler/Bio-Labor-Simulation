package com.biolab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldStatsChartScalerTest {

    @Test
    void globalVsRelativeRangesShouldProduceDifferentNormalizedValues() {
        double global = WorldStatsChartScaler.normalize(50.0, 0.0, 100.0);
        double relative = WorldStatsChartScaler.normalize(50.0, 40.0, 60.0);

        assertEquals(0.5, global, 1e-9);
        assertEquals(0.5, relative, 1e-9);

        double globalSecond = WorldStatsChartScaler.normalize(50.0, 0.0, 200.0);
        double relativeSecond = WorldStatsChartScaler.normalize(50.0, 40.0, 60.0);
        assertEquals(0.25, globalSecond, 1e-9);
        assertEquals(0.5, relativeSecond, 1e-9);
    }

    @Test
    void percentMetricsShouldUseFixedZeroToHundredRange() {
        WorldMetricDefinition percentMetric = WorldMetricRegistry.definition(WorldMetricId.TEMPERATURE);

        double[] resolved = WorldStatsPanel.resolveYAxisRangeForMetric(percentMetric, new double[]{40.0, 60.0});

        assertArrayEquals(new double[]{0.0, 100.0}, resolved, 1e-9);
    }

    @Test
    void nonPercentMetricsShouldUseAutoRangePerSeries() {
        WorldMetricDefinition nonPercentMetric = WorldMetricRegistry.definition(WorldMetricId.POPULATION_ALIVE);
        double[] autoRange = new double[]{10.0, 25.0};

        double[] resolved = WorldStatsPanel.resolveYAxisRangeForMetric(nonPercentMetric, autoRange);

        assertArrayEquals(autoRange, resolved, 1e-9);
    }
}

