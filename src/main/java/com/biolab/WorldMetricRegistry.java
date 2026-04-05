package com.biolab;

import java.awt.*;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Central, data-driven registry for all chartable world metrics.
 */
public final class WorldMetricRegistry {
    private static final List<WorldMetricDefinition> DEFINITIONS = List.of(
            new WorldMetricDefinition(WorldMetricId.POPULATION_ALIVE, "Population Alive", "count",
                    new Color(0, 255, 255), WorldMetricCategory.POPULATION,
                    ctx -> ctx.populationAlive()),
            new WorldMetricDefinition(WorldMetricId.FOOD_PELLETS_AVAILABLE, "Food Pellets Available", "count",
                    new Color(50, 255, 100), WorldMetricCategory.FOOD,
                    ctx -> ctx.foodPelletsAvailable()),
            new WorldMetricDefinition(WorldMetricId.FOOD_SPAWNED_PER_SEC, "Food Spawned/sec", "per sec",
                    new Color(80, 190, 255), WorldMetricCategory.FOOD,
                    ctx -> ctx.foodSpawnedPerSec()),
            new WorldMetricDefinition(WorldMetricId.FOOD_CONSUMED_PER_SEC, "Food Consumed/sec", "per sec",
                    new Color(255, 170, 90), WorldMetricCategory.FOOD,
                    ctx -> ctx.foodConsumedPerSec()),
            new WorldMetricDefinition(WorldMetricId.TEMPERATURE, "Temperature", "%",
                    new Color(255, 120, 120), WorldMetricCategory.ENVIRONMENT,
                    ctx -> ctx.temperature() * 100.0),
            new WorldMetricDefinition(WorldMetricId.TOXICITY, "Toxicity", "%",
                    new Color(120, 255, 120), WorldMetricCategory.ENVIRONMENT,
                    ctx -> ctx.toxicity() * 100.0),
            new WorldMetricDefinition(WorldMetricId.FOOD_SPAWN_RATE, "Food Spawn Rate", "%",
                    new Color(120, 170, 255), WorldMetricCategory.ENVIRONMENT,
                    ctx -> ctx.foodSpawnRate() * 100.0),
            new WorldMetricDefinition(WorldMetricId.AVG_HEAT_RESISTANCE, "Avg Heat Resistance", "%",
                    new Color(255, 110, 110), WorldMetricCategory.TRAITS,
                    ctx -> ctx.avgHeatResistance() * 100.0),
            new WorldMetricDefinition(WorldMetricId.AVG_TOXIN_RESISTANCE, "Avg Toxin Resistance", "%",
                    new Color(110, 255, 110), WorldMetricCategory.TRAITS,
                    ctx -> ctx.avgToxinResistance() * 100.0),
            new WorldMetricDefinition(WorldMetricId.AVG_SPEED, "Avg Speed", "%",
                    new Color(120, 170, 255), WorldMetricCategory.TRAITS,
                    ctx -> ctx.avgSpeed() * 100.0),
            new WorldMetricDefinition(WorldMetricId.AVG_DIET, "Avg Diet", "%",
                    new Color(255, 210, 90), WorldMetricCategory.TRAITS,
                    ctx -> ctx.avgDiet() * 100.0),
            new WorldMetricDefinition(WorldMetricId.AVG_AGE, "Avg Age", "cycles",
                    new Color(220, 220, 220), WorldMetricCategory.TRAITS,
                    WorldMetricContext::avgAge),
            new WorldMetricDefinition(WorldMetricId.AVG_ENERGY_PERCENT, "Avg Energy%", "%",
                    new Color(255, 140, 220), WorldMetricCategory.TRAITS,
                    WorldMetricContext::avgEnergyPercent),
            new WorldMetricDefinition(WorldMetricId.AVG_HEALTH_PERCENT, "Avg Health%", "%",
                    new Color(255, 120, 200), WorldMetricCategory.TRAITS,
                    WorldMetricContext::avgHealthPercent)
    );

    private static final Map<WorldMetricId, WorldMetricDefinition> BY_ID;

    static {
        EnumMap<WorldMetricId, WorldMetricDefinition> map = new EnumMap<>(WorldMetricId.class);
        for (WorldMetricDefinition definition : DEFINITIONS) {
            map.put(definition.id(), definition);
        }
        BY_ID = Collections.unmodifiableMap(map);
    }

    private WorldMetricRegistry() {
    }

    public static List<WorldMetricDefinition> definitions() {
        return DEFINITIONS;
    }

    public static WorldMetricDefinition definition(WorldMetricId id) {
        return BY_ID.get(id);
    }
}

