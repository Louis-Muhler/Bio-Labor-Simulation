package com.biolab;

import java.util.Locale;

/**
 * Formats range labels shown in the world statistics controls.
 */
public final class WorldStatsRangeLabelFormatter {
    private static final long TICKS_PER_SECOND = 30L;
    private static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * 60L;
    private static final long TICKS_PER_HOUR = TICKS_PER_MINUTE * 60L;

    private WorldStatsRangeLabelFormatter() {
    }

    public static String formatCurrentRange(WorldStatsRangePreset preset, long fromTick, long toTick) {
        if (preset == null) {
            return "-";
        }
        if (preset == WorldStatsRangePreset.CUSTOM) {
            return formatTickRangeValue(fromTick) + " - " + formatTickRangeValue(toTick);
        }
        return preset.label();
    }

    public static String formatTickRangeValue(long ticks) {
        long safeTicks = Math.max(0L, ticks);
        if (safeTicks % TICKS_PER_HOUR == 0) {
            return (safeTicks / TICKS_PER_HOUR) + "h";
        }
        if (safeTicks % TICKS_PER_MINUTE == 0) {
            return (safeTicks / TICKS_PER_MINUTE) + "m";
        }
        if (safeTicks % (TICKS_PER_MINUTE / 2L) == 0) {
            return String.format(Locale.ROOT, "%.1fm", safeTicks / (double) TICKS_PER_MINUTE);
        }
        if (safeTicks % TICKS_PER_SECOND == 0) {
            return (safeTicks / TICKS_PER_SECOND) + "s";
        }
        return safeTicks + " ticks";
    }
}

