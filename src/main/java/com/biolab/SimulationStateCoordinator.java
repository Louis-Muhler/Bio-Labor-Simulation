package com.biolab;

import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * Encapsulates simulation state capture/load transformations over shared world collections.
 * Must be called while the caller holds the engine's mutation locks.
 */
final class SimulationStateCoordinator {
    private final int width;
    private final int height;
    private final Environment environment;
    private final DebugModeService debugModeService;
    private final WorldState worldState;

    SimulationStateCoordinator(int width,
                               int height,
                               Environment environment,
                               DebugModeService debugModeService,
                               WorldState worldState) {
        this.width = width;
        this.height = height;
        this.environment = environment;
        this.debugModeService = debugModeService;
        this.worldState = worldState;
    }

    SimulationState captureState(double foodSpawnRate) {
        List<Microbe> microbes = worldState.microbes();
        List<FoodPellet> foodPellets = worldState.foodPellets();
        List<Microbe.PersistedState> microbesState = microbes.stream()
                .map(Microbe::toPersistedState)
                .toList();
        List<SimulationState.FoodState> foodState = foodPellets.stream()
                .filter(food -> !food.isConsumed())
                .map(food -> new SimulationState.FoodState(food.getX(), food.getY()))
                .toList();

        return new SimulationState(
                width,
                height,
                environment.getTemperature(),
                environment.getToxicity(),
                foodSpawnRate,
                microbesState,
                foodState,
                debugModeService.isEnabled()
        );
    }

    SimulationSnapshot loadState(SimulationState state, DoubleConsumer foodSpawnRateSetter) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (state.worldWidth() != width || state.worldHeight() != height) {
            throw new IllegalArgumentException("State dimensions "
                    + state.worldWidth() + "x" + state.worldHeight()
                    + " do not match runtime world " + width + "x" + height);
        }

        List<Microbe> microbes = worldState.microbes();
        List<Microbe> newMicrobes = worldState.newMicrobes();
        List<FoodPellet> foodPellets = worldState.foodPellets();

        microbes.clear();
        foodPellets.clear();
        synchronized (newMicrobes) {
            newMicrobes.clear();
        }

        for (Microbe.PersistedState m : state.microbes()) {
            microbes.add(Microbe.fromPersistedState(m));
        }
        for (SimulationState.FoodState f : state.food()) {
            foodPellets.add(new FoodPellet(f.x(), f.y()));
        }

        environment.setTemperature(state.temperature());
        environment.setToxicity(state.toxicity());
        foodSpawnRateSetter.accept(state.foodSpawnRate());
        debugModeService.setEnabled(state.debugMode());

        worldState.microbeById().clear();
        for (Microbe microbe : microbes) {
            worldState.microbeById().put(microbe.getId(), microbe);
        }

        return snapshotFromCurrentWorld();
    }

    SimulationSnapshot spawnMicrobe(Microbe microbe) {
        List<Microbe> microbes = worldState.microbes();
        List<FoodPellet> foodPellets = worldState.foodPellets();
        microbes.add(microbe);
        worldState.microbeById().put(microbe.getId(), microbe);
        return snapshotFromCurrentWorld();
    }

    private SimulationSnapshot snapshotFromCurrentWorld() {
        List<Microbe> microbes = worldState.microbes();
        List<FoodPellet> foodPellets = worldState.foodPellets();
        return new SimulationSnapshot(
                microbes.stream().map(Microbe::toRenderState).toList(),
                List.copyOf(foodPellets)
        );
    }
}

