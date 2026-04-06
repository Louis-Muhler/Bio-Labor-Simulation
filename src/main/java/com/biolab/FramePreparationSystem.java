package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds immutable frame inputs from mutable world state under the shared data lock.
 */
final class FramePreparationSystem {
    private final WorldState worldState;
    private final AtomicInteger availableReproductionSlots;
    private final int maxPopulation;
    private final int worldWidth;
    private final int worldHeight;
    private final int maxFoodPellets;

    FramePreparationSystem(WorldState worldState,
                           AtomicInteger availableReproductionSlots,
                           int maxPopulation,
                           int worldWidth,
                           int worldHeight,
                           int maxFoodPellets) {
        this.worldState = worldState;
        this.availableReproductionSlots = availableReproductionSlots;
        this.maxPopulation = maxPopulation;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.maxFoodPellets = maxFoodPellets;
    }

    FrameBatch prepare(double foodSpawnRate) {
        synchronized (worldState.dataLock()) {
            List<Microbe> microbes = worldState.population().microbes();
            List<FoodPellet> foodPellets = worldState.food().pellets();
            int currentPop = microbes.size();
            int availableSlots = Math.max(0, maxPopulation - currentPop);
            availableReproductionSlots.set(availableSlots);

            ThreadLocalRandom random = ThreadLocalRandom.current();
            int spawnedFoodCount = 0;
            int freeFoodSlots = Math.max(0, maxFoodPellets - foodPellets.size());
            if (freeFoodSlots > 0 && foodSpawnRate > 0.0) {
                double clampedSpawnPerTick = Math.min(foodSpawnRate, freeFoodSlots);
                int guaranteedSpawns = (int) Math.floor(clampedSpawnPerTick);
                double fractionalSpawnChance = clampedSpawnPerTick - guaranteedSpawns;
                spawnedFoodCount = guaranteedSpawns;
                if (random.nextDouble() < fractionalSpawnChance) {
                    spawnedFoodCount++;
                }
                if (spawnedFoodCount > freeFoodSlots) {
                    spawnedFoodCount = freeFoodSlots;
                }

                for (int i = 0; i < spawnedFoodCount; i++) {
                    foodPellets.add(FoodPellet.createRandom(worldWidth, worldHeight));
                }
            }

            return new FrameBatch(new ArrayList<>(microbes), new ArrayList<>(foodPellets), spawnedFoodCount);
        }
    }

    record FrameBatch(List<Microbe> microbeSnapshot, List<FoodPellet> foodSnapshot, int spawnedFoodCount) {
    }
}

