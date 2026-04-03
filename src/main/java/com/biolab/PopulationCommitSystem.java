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

    SimulationSnapshot finalizeFrame() {
        synchronized (worldState.dataLock()) {
            List<Microbe> microbes = worldState.microbes();
            List<Microbe> newMicrobes = worldState.newMicrobes();
            List<FoodPellet> foodPellets = worldState.foodPellets();
            microbes.removeIf(Microbe::isDead);
            worldState.microbeById().entrySet().removeIf(e -> e.getValue().isDead());
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
                    worldState.microbeById().put(newborn.getId(), newborn);
                }
            } else {
                int allowedNewborns = Math.max(0, maxPopulation - currentPopulation);
                for (int i = 0; i < allowedNewborns && i < newbornsCopy.size(); i++) {
                    Microbe newborn = newbornsCopy.get(i);
                    microbes.add(newborn);
                    worldState.microbeById().put(newborn.getId(), newborn);
                }
            }

            return new SimulationSnapshot(
                    microbes.stream().map(Microbe::toRenderState).toList(),
                    List.copyOf(foodPellets)
            );
        }
    }

    SimulationSnapshot finalizeEmptyFrame() {
        synchronized (worldState.dataLock()) {
            List<FoodPellet> foodPellets = worldState.foodPellets();
            foodPellets.removeIf(FoodPellet::isConsumed);
            return new SimulationSnapshot(List.of(), List.copyOf(foodPellets));
        }
    }
}

