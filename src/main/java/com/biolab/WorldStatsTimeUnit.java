package com.biolab;

/**
 * Tick-based units used by the world stats range controls.
 */
public enum WorldStatsTimeUnit {
    TICK(1L, "tick"),
    SEC(30L, "sec"),
    MIN(30L * 60L, "min"),
    HOUR(30L * 60L * 60L, "hour");

    private final long ticksPerUnit;
    private final String label;

    WorldStatsTimeUnit(long ticksPerUnit, String label) {
        this.ticksPerUnit = ticksPerUnit;
        this.label = label;
    }

    public static String formatTickDuration(long ticks) {
        if (ticks < 30L * 60L) {
            return (ticks / 30L) + "s (" + ticks + " ticks)";
        }
        if (ticks < 30L * 60L * 60L) {
            return (ticks / (30L * 60L)) + "m (" + ticks + " ticks)";
        }
        return (ticks / (30L * 60L * 60L)) + "h (" + ticks + " ticks)";
    }

    public long toTicks(long value) {
        return Math.max(0L, value) * ticksPerUnit;
    }

    public String label() {
        return label;
    }
}

