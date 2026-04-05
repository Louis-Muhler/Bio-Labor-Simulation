package com.biolab;

import java.time.Duration;

/**
 * Time-window presets for the world statistics chart.
 */
public enum WorldStatsRangePreset {
    SINCE_BEGINNING(null, "Seit Beginn"),
    LAST_1_MIN(Duration.ofMinutes(1), "1m"),
    LAST_5_MIN(Duration.ofMinutes(5), "5m"),
    LAST_10_MIN(Duration.ofMinutes(10), "10m"),
    LAST_30_MIN(Duration.ofMinutes(30), "30m"),
    LAST_60_MIN(Duration.ofMinutes(60), "60m"),
    CUSTOM(null, "Custom");

    private final Duration duration;
    private final String label;

    WorldStatsRangePreset(Duration duration, String label) {
        this.duration = duration;
        this.label = label;
    }

    public String label() {
        return label;
    }

    public long resolveStartMillis(long nowMillis, long earliestMillis, long customStartMillis, long customEndMillis) {
        if (this == SINCE_BEGINNING) {
            return earliestMillis;
        }
        if (this == CUSTOM) {
            long start = Math.min(customStartMillis, customEndMillis);
            return Math.max(earliestMillis, start);
        }
        long start = nowMillis - duration.toMillis();
        return Math.max(earliestMillis, start);
    }

    public long resolveEndMillis(long nowMillis, long customStartMillis, long customEndMillis) {
        if (this == CUSTOM) {
            return Math.max(customStartMillis, customEndMillis);
        }
        return nowMillis;
    }
}

