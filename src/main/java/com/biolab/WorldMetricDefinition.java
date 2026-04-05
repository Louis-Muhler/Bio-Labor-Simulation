package com.biolab;

import java.awt.*;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/**
 * Definition of a world metric including display metadata and extraction logic.
 */
public record WorldMetricDefinition(
        WorldMetricId id,
        String label,
        String unit,
        Color color,
        WorldMetricCategory category,
        ToDoubleFunction<WorldMetricContext> extractor
) {
    public WorldMetricDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(extractor, "extractor");
    }
}

