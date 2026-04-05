package com.biolab;

/**
 * Display grouping for metric selection.
 */
public enum WorldMetricCategory {
    POPULATION("Population"),
    FOOD("Food"),
    ENVIRONMENT("Environment"),
    TRAITS("Traits");

    private final String label;

    WorldMetricCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

