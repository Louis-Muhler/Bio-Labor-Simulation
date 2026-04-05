package com.biolab;

import java.util.List;

/**
 * Serializable simulation snapshot used by save/load services.
 */
public record SimulationState(
        int worldWidth,
        int worldHeight,
        double temperature,
        double toxicity,
        double foodSpawnRate,
        long simulationTick,
        List<WorldStatsSample> worldStatsHistory,
        List<Microbe.PersistedState> microbes,
        List<FoodState> food,
        boolean debugMode) implements java.io.Serializable {

    public SimulationState {
        worldStatsHistory = List.copyOf(worldStatsHistory);
        microbes = List.copyOf(microbes);
        food = List.copyOf(food);
    }

    public record FoodState(double x, double y) implements java.io.Serializable {
    }
}


