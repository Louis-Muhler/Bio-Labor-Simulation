package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds immutable frame inputs from mutable world state under the shared data lock.
 */
final class FramePreparationSystem {
    private final Object dataLock;
    private final List<Microbe> microbes;
    private final List<FoodPellet> foodPellets;
    private final AtomicInteger availableReproductionSlots;
    private final int maxPopulation;
    private final int worldWidth;
    private final int worldHeight;
    private final int maxFoodPellets;

    FramePreparationSystem(Object dataLock,
                           List<Microbe> microbes,
                           List<FoodPellet> foodPellets,
                           AtomicInteger availableReproductionSlots,
                           int maxPopulation,
                           int worldWidth,
                           int worldHeight,
                           int maxFoodPellets) {
        this.dataLock = dataLock;
        this.microbes = microbes;
        this.foodPellets = foodPellets;
        this.availableReproductionSlots = availableReproductionSlots;
        this.maxPopulation = maxPopulation;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.maxFoodPellets = maxFoodPellets;
    }

    FrameBatch prepare(double foodSpawnRate) {
        synchronized (dataLock) {
            int currentPop = microbes.size();
            int availableSlots = Math.max(0, maxPopulation - currentPop);
            availableReproductionSlots.set(availableSlots);

            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (random.nextDouble() < foodSpawnRate && foodPellets.size() < maxFoodPellets) {
                foodPellets.add(FoodPellet.createRandom(worldWidth, worldHeight));
            }

            return new FrameBatch(new ArrayList<>(microbes), new ArrayList<>(foodPellets));
        }
    }

    record FrameBatch(List<Microbe> microbeSnapshot, List<FoodPellet> foodSnapshot) {
    }
}

