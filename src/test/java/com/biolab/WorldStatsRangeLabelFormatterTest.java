package com.biolab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldStatsRangeLabelFormatterTest {

    @Test
    void shouldFormatCompactTickValues() {
        assertEquals("2.5m", WorldStatsRangeLabelFormatter.formatTickRangeValue(WorldStatsTimeUnit.SEC.toTicks(150)));
        assertEquals("400s", WorldStatsRangeLabelFormatter.formatTickRangeValue(WorldStatsTimeUnit.SEC.toTicks(400)));
        assertEquals("1h", WorldStatsRangeLabelFormatter.formatTickRangeValue(WorldStatsTimeUnit.MIN.toTicks(60)));
    }

    @Test
    void shouldFormatCurrentRangeForCustomPreset() {
        long fromTick = WorldStatsTimeUnit.MIN.toTicks(2);
        long toTick = WorldStatsTimeUnit.MIN.toTicks(5);

        String label = WorldStatsRangeLabelFormatter.formatCurrentRange(WorldStatsRangePreset.CUSTOM, fromTick, toTick);

        assertEquals("2m - 5m", label);
    }

    @Test
    void shouldReturnPresetLabelForFixedPreset() {
        String label = WorldStatsRangeLabelFormatter.formatCurrentRange(WorldStatsRangePreset.LAST_10_MIN, 0L, 0L);
        assertEquals("Last 10m", label);
    }
}

