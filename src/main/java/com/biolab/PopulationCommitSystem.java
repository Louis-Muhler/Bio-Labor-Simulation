package com.biolab;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles frame-bound world list mutation and immutable snapshot publishing.
 */
final class PopulationCommitSystem {
    private final WorldState worldState;
    private final int maxPopulation;

    PopulationCommitSystem(WorldState worldState,
                           int maxPopulation) {
        this.worldState = worldState;
        this.maxPopulation = maxPopulation;
    }

    SimulationFrameResult finalizeFrame(int spawnedFoodCount) {
        // Commit phase invariant: all world-list mutations happen under dataLock,
        // and only after worker processing completed successfully for this frame.
        synchronized (worldState.dataLock()) {
            List<Microbe> microbes = worldState.population().microbes();
            List<Microbe> newMicrobes = worldState.population().newMicrobes();
            List<FoodPellet> foodPellets = worldState.food().pellets();
            microbes.removeIf(Microbe::isDead);
            worldState.index().byId().entrySet().removeIf(e -> e.getValue().isDead());
            int consumedFoodCount = (int) foodPellets.stream().filter(FoodPellet::isConsumed).count();
            foodPellets.removeIf(FoodPellet::isConsumed);

            int currentPopulation = microbes.size();
            List<Microbe> newbornsCopy;
            synchronized (newMicrobes) {
                newbornsCopy = new ArrayList<>(newMicrobes);
                newMicrobes.clear();
            }

            if (currentPopulation + newbornsCopy.size() <= maxPopulation) {
                microbes.addAll(newbornsCopy);
                for (Microbe newborn : newbornsCopy) {
                    worldState.index().byId().put(newborn.getId(), newborn);
                }
            } else {
                int allowedNewborns = Math.max(0, maxPopulation - currentPopulation);
                for (int i = 0; i < allowedNewborns && i < newbornsCopy.size(); i++) {
                    Microbe newborn = newbornsCopy.get(i);
                    microbes.add(newborn);
                    worldState.index().byId().put(newborn.getId(), newborn);
                }
            }

            SimulationSnapshot snapshot = new SimulationSnapshot(
                    microbes.stream().map(Microbe::toRenderState).toList(),
                    List.copyOf(foodPellets)
            );
            return new SimulationFrameResult(snapshot, spawnedFoodCount, consumedFoodCount);
        }
    }

    SimulationFrameResult finalizeEmptyFrame(int spawnedFoodCount) {
        // Even empty frames run cleanup under the same commit lock to keep semantics uniform.
        synchronized (worldState.dataLock()) {
            List<FoodPellet> foodPellets = worldState.food().pellets();
            int consumedFoodCount = (int) foodPellets.stream().filter(FoodPellet::isConsumed).count();
            foodPellets.removeIf(FoodPellet::isConsumed);
            return new SimulationFrameResult(
                    new SimulationSnapshot(List.of(), List.copyOf(foodPellets)),
                    spawnedFoodCount,
                    consumedFoodCount
            );
        }
    }
}

