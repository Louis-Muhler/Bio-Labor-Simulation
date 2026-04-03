package com.biolab;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleConsumer;

/**
 * Encapsulates simulation state capture/load transformations over shared world collections.
 * Must be called while the caller holds the engine's mutation locks.
 */
final class SimulationStateCoordinator {
    private final int width;
    private final int height;
    private final Environment environment;
    private final AtomicBoolean debugMode;
    private final List<Microbe> microbes;
    private final List<Microbe> newMicrobes;
    private final List<FoodPellet> foodPellets;
    private final Map<Long, Microbe> microbeById;

    SimulationStateCoordinator(int width,
                               int height,
                               Environment environment,
                               AtomicBoolean debugMode,
                               List<Microbe> microbes,
                               List<Microbe> newMicrobes,
                               List<FoodPellet> foodPellets,
                               Map<Long, Microbe> microbeById) {
        this.width = width;
        this.height = height;
        this.environment = environment;
        this.debugMode = debugMode;
        this.microbes = microbes;
        this.newMicrobes = newMicrobes;
        this.foodPellets = foodPellets;
        this.microbeById = microbeById;
    }

    SimulationState captureState(double foodSpawnRate) {
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
                debugMode.get()
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
        debugMode.set(state.debugMode());

        microbeById.clear();
        for (Microbe microbe : microbes) {
            microbeById.put(microbe.getId(), microbe);
        }

        return snapshotFromCurrentWorld();
    }

    SimulationSnapshot spawnMicrobe(Microbe microbe) {
        microbes.add(microbe);
        microbeById.put(microbe.getId(), microbe);
        return snapshotFromCurrentWorld();
    }

    private SimulationSnapshot snapshotFromCurrentWorld() {
        return new SimulationSnapshot(
                microbes.stream().map(Microbe::toRenderState).toList(),
                List.copyOf(foodPellets)
        );
    }
}

