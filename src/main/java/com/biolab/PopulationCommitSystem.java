package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles frame-bound world list mutation and immutable snapshot publishing.
 */
final class PopulationCommitSystem {
    private final Object dataLock;
    private final List<Microbe> microbes;
    private final List<Microbe> newMicrobes;
    private final List<FoodPellet> foodPellets;
    private final Map<Long, Microbe> microbeById;
    private final int maxPopulation;

    PopulationCommitSystem(Object dataLock,
                           List<Microbe> microbes,
                           List<Microbe> newMicrobes,
                           List<FoodPellet> foodPellets,
                           Map<Long, Microbe> microbeById,
                           int maxPopulation) {
        this.dataLock = dataLock;
        this.microbes = microbes;
        this.newMicrobes = newMicrobes;
        this.foodPellets = foodPellets;
        this.microbeById = microbeById;
        this.maxPopulation = maxPopulation;
    }

    SimulationSnapshot finalizeFrame() {
        synchronized (dataLock) {
            microbes.removeIf(Microbe::isDead);
            microbeById.entrySet().removeIf(e -> e.getValue().isDead());
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
                    microbeById.put(newborn.getId(), newborn);
                }
            } else {
                int allowedNewborns = Math.max(0, maxPopulation - currentPopulation);
                for (int i = 0; i < allowedNewborns && i < newbornsCopy.size(); i++) {
                    Microbe newborn = newbornsCopy.get(i);
                    microbes.add(newborn);
                    microbeById.put(newborn.getId(), newborn);
                }
            }

            return new SimulationSnapshot(
                    microbes.stream().map(Microbe::toRenderState).toList(),
                    List.copyOf(foodPellets)
            );
        }
    }

    SimulationSnapshot finalizeEmptyFrame() {
        synchronized (dataLock) {
            foodPellets.removeIf(FoodPellet::isConsumed);
            return new SimulationSnapshot(List.of(), List.copyOf(foodPellets));
        }
    }
}

