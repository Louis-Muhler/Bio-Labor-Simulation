package com.biolab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldStatsRangePresetTest {

    @Test
    void fixedPresetShouldClampToEarliestTimestamp() {
        long latestTick = 1_000L;
        long earliestTick = 950L;

        long start = WorldStatsRangePreset.LAST_1_MIN.resolveStartTick(latestTick, earliestTick, 0L, 0L);
        long end = WorldStatsRangePreset.LAST_1_MIN.resolveEndTick(latestTick, 0L, 0L);

        assertEquals(earliestTick, start);
        assertEquals(latestTick, end);
    }

    @Test
    void customPresetShouldRespectSortedBounds() {
        long now = 1_000_000L;
        long earliest = 100_000L;
        long customStart = 700_000L;
        long customEnd = 650_000L;

        long start = WorldStatsRangePreset.CUSTOM.resolveStartTick(now, earliest, customStart, customEnd);
        long end = WorldStatsRangePreset.CUSTOM.resolveEndTick(now, customStart, customEnd);

        assertEquals(650_000L, start);
        assertEquals(700_000L, end);
    }

    @Test
    void mixedUnitsShouldConvertToComparableTicks() {
        long startTicks = WorldStatsTimeUnit.MIN.toTicks(2);
        long endTicks = WorldStatsTimeUnit.SEC.toTicks(90);

        long resolvedStart = WorldStatsRangePreset.CUSTOM.resolveStartTick(10_000L, 0L, startTicks, endTicks);
        long resolvedEnd = WorldStatsRangePreset.CUSTOM.resolveEndTick(10_000L, startTicks, endTicks);

        assertEquals(2_700L, resolvedStart);
        assertEquals(3_600L, resolvedEnd);
    }

    @Test
    void customDefaultRangeShouldMapToTwoPointFiveMinutes() {
        long startTicks = WorldStatsTimeUnit.MIN.toTicks(0);
        long endTicks = WorldStatsTimeUnit.SEC.toTicks(150);

        long resolvedStart = WorldStatsRangePreset.CUSTOM.resolveStartTick(100_000L, 0L, startTicks, endTicks);
        long resolvedEnd = WorldStatsRangePreset.CUSTOM.resolveEndTick(100_000L, startTicks, endTicks);

        assertEquals(0L, resolvedStart);
        assertEquals(WorldStatsTimeUnit.MIN.toTicks(2) + WorldStatsTimeUnit.SEC.toTicks(30), resolvedEnd);
    }

    @Test
    void rangeFormatterShouldProduceCompactReadableValues() {
        assertEquals("Since beginning", WorldStatsRangePreset.SINCE_BEGINNING.label());
        assertEquals("2.5m", WorldStatsRangeLabelFormatter.formatTickRangeValue(WorldStatsTimeUnit.SEC.toTicks(150)));
        assertEquals("400s", WorldStatsRangeLabelFormatter.formatTickRangeValue(WorldStatsTimeUnit.SEC.toTicks(400)));
    }
}

