package com.biolab;

/**
 * Immutable frame-level values from which world metrics are extracted.
 */
record WorldMetricContext(
        int populationAlive,
        int foodPelletsAvailable,
        double foodSpawnedPerSec,
        double foodConsumedPerSec,
        double temperature,
        double toxicity,
        double foodSpawnRate,
        double avgHeatResistance,
        double avgToxinResistance,
        double avgSpeed,
        double avgDiet,
        double avgAge,
        double avgEnergyPercent,
        double avgHealthPercent
) {
}

