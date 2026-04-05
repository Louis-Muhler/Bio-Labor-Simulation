package com.biolab;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * One timestamped sample containing all metric values for a frame.
 */
public record WorldStatsSample(
        long timestampMillis,
        long tick,
        Map<WorldMetricId, Double> metricValues
) {
    public WorldStatsSample {
        if (timestampMillis < 0) {
            throw new IllegalArgumentException("timestampMillis must be >= 0");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0");
        }
        Objects.requireNonNull(metricValues, "metricValues");
        EnumMap<WorldMetricId, Double> copy = new EnumMap<>(WorldMetricId.class);
        for (Map.Entry<WorldMetricId, Double> entry : metricValues.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        metricValues = Collections.unmodifiableMap(copy);
    }
}

