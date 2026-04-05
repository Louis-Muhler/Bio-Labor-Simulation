package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorldMetricRegistryTest {

    @Test
    void registryShouldContainDefinitionForEachMetricId() {
        Set<WorldMetricId> definedIds = WorldMetricRegistry.definitions().stream()
                .map(WorldMetricDefinition::id)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(EnumSet.allOf(WorldMetricId.class), definedIds,
                "Alle Metrik-IDs muessen in der Registry definiert sein");
    }

    @Test
    void definitionsShouldExposeExtractorAndMetadata() {
        WorldMetricContext context = new WorldMetricContext(
                42, 128, 2.5, 1.5,
                0.6, 0.3, 0.8,
                0.5, 0.4, 0.7, 0.2,
                95.0, 66.0, 77.0
        );

        for (WorldMetricDefinition definition : WorldMetricRegistry.definitions()) {
            assertNotNull(definition.label());
            assertNotNull(definition.unit());
            assertNotNull(definition.color());
            assertNotNull(definition.category());
            double value = definition.extractor().applyAsDouble(context);
            assertTrue(Double.isFinite(value), () -> "Extractor lieferte keinen gueltigen Wert fuer " + definition.id());
            assertSame(definition, WorldMetricRegistry.definition(definition.id()));
        }
    }
}

