package com.biolab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldStatsRangePresetTest {

    @Test
    void fixedPresetShouldClampToEarliestTimestamp() {
        long now = 1_000_000L;
        long earliest = 980_000L;

        long start = WorldStatsRangePreset.LAST_1_MIN.resolveStartMillis(now, earliest, 0L, 0L);
        long end = WorldStatsRangePreset.LAST_1_MIN.resolveEndMillis(now, 0L, 0L);

        assertEquals(earliest, start);
        assertEquals(now, end);
    }

    @Test
    void customPresetShouldRespectSortedBounds() {
        long now = 1_000_000L;
        long earliest = 100_000L;
        long customStart = 700_000L;
        long customEnd = 650_000L;

        long start = WorldStatsRangePreset.CUSTOM.resolveStartMillis(now, earliest, customStart, customEnd);
        long end = WorldStatsRangePreset.CUSTOM.resolveEndMillis(now, customStart, customEnd);

        assertEquals(650_000L, start);
        assertEquals(700_000L, end);
    }
}

