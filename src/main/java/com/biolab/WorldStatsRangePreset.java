package com.biolab;

/**
 * Time-window presets for the world statistics chart.
 */
public enum WorldStatsRangePreset {
    SINCE_BEGINNING(-1L, "Since beginning"),
    LAST_1_MIN(30L * 60L, "Last 1m"),
    LAST_5_MIN(30L * 60L * 5L, "Last 5m"),
    LAST_10_MIN(30L * 60L * 10L, "Last 10m"),
    LAST_30_MIN(30L * 60L * 30L, "Last 30m"),
    LAST_60_MIN(30L * 60L * 60L, "Last 60m"),
    CUSTOM(null, "Custom");

    private final Long durationTicks;
    private final String label;

    WorldStatsRangePreset(Long durationTicks, String label) {
        this.durationTicks = durationTicks;
        this.label = label;
    }

    public String label() {
        return label;
    }

    public long resolveStartTick(long latestTick, long earliestTick, long customStartTick, long customEndTick) {
        if (this == SINCE_BEGINNING) {
            return earliestTick;
        }
        if (this == CUSTOM) {
            long start = Math.min(customStartTick, customEndTick);
            return Math.max(earliestTick, start);
        }
        long start = latestTick - durationTicks;
        return Math.max(earliestTick, start);
    }

    public long resolveEndTick(long latestTick, long customStartTick, long customEndTick) {
        if (this == CUSTOM) {
            return Math.max(customStartTick, customEndTick);
        }
        return latestTick;
    }
}

