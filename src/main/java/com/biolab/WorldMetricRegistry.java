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
                    new Color(0, 220, 255), WorldMetricCategory.POPULATION,
                    ctx -> ctx.populationAlive()),
            new WorldMetricDefinition(WorldMetricId.FOOD_PELLETS_AVAILABLE, "Food Pellets Available", "count",
                    new Color(0, 200, 120), WorldMetricCategory.FOOD,
                    ctx -> ctx.foodPelletsAvailable()),
            new WorldMetricDefinition(WorldMetricId.FOOD_CONSUMED_PER_SEC, "Food Consumed", "per tick",
                    new Color(255, 170, 0), WorldMetricCategory.FOOD,
                    ctx -> ctx.foodConsumedPerSec()),
            new WorldMetricDefinition(WorldMetricId.TEMPERATURE, "Temperature", "%",
                    new Color(255, 90, 60), WorldMetricCategory.ENVIRONMENT,
                    ctx -> ctx.temperature() * 100.0),
            new WorldMetricDefinition(WorldMetricId.TOXICITY, "Toxicity", "%",
                    new Color(140, 255, 40), WorldMetricCategory.ENVIRONMENT,
                    ctx -> ctx.toxicity() * 100.0),
            new WorldMetricDefinition(WorldMetricId.FOOD_SPAWN_RATE, "Food Spawn", "per tick",
                    new Color(70, 150, 255), WorldMetricCategory.ENVIRONMENT,
                    WorldMetricContext::foodSpawnRate),
            new WorldMetricDefinition(WorldMetricId.AVG_HEAT_RESISTANCE, "Avg Heat Resistance", "%",
                    new Color(255, 70, 70), WorldMetricCategory.TRAITS,
                    ctx -> ctx.avgHeatResistance() * 100.0),
            new WorldMetricDefinition(WorldMetricId.AVG_TOXIN_RESISTANCE, "Avg Toxin Resistance", "%",
                    new Color(80, 230, 120), WorldMetricCategory.TRAITS,
                    ctx -> ctx.avgToxinResistance() * 100.0),
            new WorldMetricDefinition(WorldMetricId.AVG_SPEED, "Avg Speed", "%",
                    new Color(70, 180, 255), WorldMetricCategory.TRAITS,
                    ctx -> ctx.avgSpeed() * 100.0),
            new WorldMetricDefinition(WorldMetricId.AVG_DIET, "Avg Diet", "%",
                    new Color(255, 200, 60), WorldMetricCategory.TRAITS,
                    ctx -> ctx.avgDiet() * 100.0),
            new WorldMetricDefinition(WorldMetricId.AVG_STRENGTH, "Avg Strength", "%",
                    new Color(170, 90, 255), WorldMetricCategory.TRAITS,
                    ctx -> ctx.avgStrength() * 100.0),
            new WorldMetricDefinition(WorldMetricId.AVG_DEFENSE, "Avg Defense", "%",
                    new Color(70, 220, 210), WorldMetricCategory.TRAITS,
                    ctx -> ctx.avgDefense() * 100.0),
            new WorldMetricDefinition(WorldMetricId.AVG_AGE, "Avg Age", "cycles",
                    new Color(235, 235, 235), WorldMetricCategory.TRAITS,
                    WorldMetricContext::avgAge),
            new WorldMetricDefinition(WorldMetricId.AVG_ENERGY_PERCENT, "Avg Energy (%)", "%",
                    new Color(255, 230, 70), WorldMetricCategory.TRAITS,
                    WorldMetricContext::avgEnergyPercent),
            new WorldMetricDefinition(WorldMetricId.AVG_HEALTH_PERCENT, "Avg Health (%)", "%",
                    new Color(255, 120, 70), WorldMetricCategory.TRAITS,
                    WorldMetricContext::avgHealthPercent),
            new WorldMetricDefinition(WorldMetricId.AVG_ENERGY_ABSOLUTE, "Avg Energy", "units",
                    new Color(120, 95, 255), WorldMetricCategory.TRAITS,
                    WorldMetricContext::avgEnergyAbsolute),
            new WorldMetricDefinition(WorldMetricId.AVG_HEALTH_ABSOLUTE, "Avg Health", "units",
                    new Color(255, 60, 115), WorldMetricCategory.TRAITS,
                    WorldMetricContext::avgHealthAbsolute)
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

