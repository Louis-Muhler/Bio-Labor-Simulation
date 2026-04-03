package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable world storage shared by engine systems.
 */
final class WorldState {
    private final Object dataLock = new Object();
    private final List<Microbe> microbes = new ArrayList<>();
    private final List<Microbe> newMicrobes = new ArrayList<>();
    private final List<FoodPellet> foodPellets = new ArrayList<>();
    private final ConcurrentHashMap<Long, Microbe> microbeById = new ConcurrentHashMap<>();

    Object dataLock() {
        return dataLock;
    }

    List<Microbe> microbes() {
        return microbes;
    }

    List<Microbe> newMicrobes() {
        return newMicrobes;
    }

    List<FoodPellet> foodPellets() {
        return foodPellets;
    }

    ConcurrentHashMap<Long, Microbe> microbeById() {
        return microbeById;
    }
}

