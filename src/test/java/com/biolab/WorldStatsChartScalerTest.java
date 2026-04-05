package com.biolab;

import org.junit.jupiter.api.Test;

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
}

