package com.biolab;

/**
 * User-configurable world settings used to create a simulation session.
 */
public record WorldConfig(
        String mapName,
        int worldWidth,
        int worldHeight,
        int initialPopulation,
        int maxPopulation,
        double temperature,
        double toxicity,
        double foodSpawnRate
) {
    public WorldConfig {
        if (mapName == null || mapName.isBlank()) {
            throw new IllegalArgumentException("mapName must not be blank");
        }
        if (worldWidth <= 0 || worldHeight <= 0) {
            throw new IllegalArgumentException("world size must be positive");
        }
        if (initialPopulation < 0) {
            throw new IllegalArgumentException("initialPopulation must be >= 0");
        }
        if (maxPopulation <= 0 || maxPopulation < initialPopulation) {
            throw new IllegalArgumentException("maxPopulation must be >= initialPopulation and > 0");
        }
        temperature = clamp01(temperature);
        toxicity = clamp01(toxicity);
        foodSpawnRate = clamp01(foodSpawnRate);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static WorldConfig defaultConfig() {
        return new WorldConfig("New World", 10_000, 10_000, 1_500, 20_000, 0.3, 0.3, 0.75);
    }
}

