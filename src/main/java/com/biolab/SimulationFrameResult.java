package com.biolab;

/**
 * Result payload of one simulation frame.
 */
record SimulationFrameResult(
        SimulationSnapshot snapshot,
        int spawnedFoodCount,
        int consumedFoodCount
) {
}

