package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable world storage shared by engine systems.
 */
final class WorldState {
    private final Object dataLock = new Object();
    private final PopulationState population = new PopulationState();
    private final FoodStateStore food = new FoodStateStore();
    private final IndexState index = new IndexState();

    Object dataLock() {
        return dataLock;
    }

    PopulationState population() {
        return population;
    }

    FoodStateStore food() {
        return food;
    }

    IndexState index() {
        return index;
    }


    static final class PopulationState {
        private final List<Microbe> microbes = new ArrayList<>();
        private final List<Microbe> newMicrobes = new ArrayList<>();

        List<Microbe> microbes() {
            return microbes;
        }

        List<Microbe> newMicrobes() {
            return newMicrobes;
        }
    }

    static final class FoodStateStore {
        private final List<FoodPellet> pellets = new ArrayList<>();

        List<FoodPellet> pellets() {
            return pellets;
        }
    }

    static final class IndexState {
        private final ConcurrentHashMap<Long, Microbe> byId = new ConcurrentHashMap<>();

        ConcurrentHashMap<Long, Microbe> byId() {
            return byId;
        }
    }
}

