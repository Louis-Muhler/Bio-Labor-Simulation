package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorldMetricRegistryTest {
    private static final Set<WorldMetricId> NON_CHARTED_IDS = EnumSet.of(
            WorldMetricId.FOOD_SPAWNED_PER_SEC
    );

    @Test
    void registryShouldContainDefinitionForEachChartedMetricId() {
        Set<WorldMetricId> definedIds = WorldMetricRegistry.definitions().stream()
                .map(WorldMetricDefinition::id)
                .collect(java.util.stream.Collectors.toSet());

        Set<WorldMetricId> expectedCharted = EnumSet.allOf(WorldMetricId.class);
        expectedCharted.removeAll(NON_CHARTED_IDS);
        assertEquals(expectedCharted, definedIds,
                "Alle chartbaren Metrik-IDs muessen in der Registry definiert sein");
    }

    @Test
    void definitionsShouldExposeExtractorAndMetadata() {
        WorldMetricContext context = new WorldMetricContext(
                42, 128, 2.5, 1.5,
                0.6, 0.3, 0.8,
                0.5, 0.4, 0.7, 0.2,
                0.35, 0.45,
                95.0, 66.0, 77.0,
                54.5, 81.25
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

        assertEquals(54.5,
                WorldMetricRegistry.definition(WorldMetricId.AVG_ENERGY_ABSOLUTE).extractor().applyAsDouble(context),
                0.0001);
        assertEquals(81.25,
                WorldMetricRegistry.definition(WorldMetricId.AVG_HEALTH_ABSOLUTE).extractor().applyAsDouble(context),
                0.0001);
        assertEquals("Avg Energy", WorldMetricRegistry.definition(WorldMetricId.AVG_ENERGY_ABSOLUTE).label());
        assertEquals("Avg Health", WorldMetricRegistry.definition(WorldMetricId.AVG_HEALTH_ABSOLUTE).label());
        assertEquals("Avg Energy (%)", WorldMetricRegistry.definition(WorldMetricId.AVG_ENERGY_PERCENT).label());
        assertEquals("Avg Health (%)", WorldMetricRegistry.definition(WorldMetricId.AVG_HEALTH_PERCENT).label());
        assertEquals("Avg Strength", WorldMetricRegistry.definition(WorldMetricId.AVG_STRENGTH).label());
        assertEquals("Avg Defense", WorldMetricRegistry.definition(WorldMetricId.AVG_DEFENSE).label());
        assertEquals(35.0,
                WorldMetricRegistry.definition(WorldMetricId.AVG_STRENGTH).extractor().applyAsDouble(context),
                0.0001);
        assertEquals(45.0,
                WorldMetricRegistry.definition(WorldMetricId.AVG_DEFENSE).extractor().applyAsDouble(context),
                0.0001);
    }
}

