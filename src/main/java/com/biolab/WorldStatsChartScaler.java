package com.biolab;

/**
 * Helper for consistent chart normalization in different Y-axis modes.
 */
final class WorldStatsChartScaler {
    private WorldStatsChartScaler() {
    }

    static double normalize(double value, double min, double max) {
        double span = Math.max(1e-9, max - min);
        return (value - min) / span;
    }

    static double[] ensureRange(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return new double[]{0.0, 1.0};
        }
        if (Math.abs(max - min) < 1e-9) {
            return new double[]{min, min + 1.0};
        }
        return new double[]{min, max};
    }
}

