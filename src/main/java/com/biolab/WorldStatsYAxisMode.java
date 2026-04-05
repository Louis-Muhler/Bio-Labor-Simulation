package com.biolab;

/**
 * Y axis scaling mode for multi-series world stats charts.
 */
public enum WorldStatsYAxisMode {
    GLOBAL("GLOBAL"),
    RELATIV_PRO_SERIE("RELATIV");

    private final String label;

    WorldStatsYAxisMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

