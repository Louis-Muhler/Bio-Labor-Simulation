package com.biolab;

import java.util.ArrayList;
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

    SimulationState captureState(double foodSpawnRate,
                                 long simulationTick,
                                 List<WorldStatsSample> worldStatsHistory) {
        List<Microbe> microbes = worldState.population().microbes();
        List<FoodPellet> foodPellets = worldState.food().pellets();
        List<Microbe.PersistedState> microbesState = new ArrayList<>(microbes.size());
        for (Microbe microbe : microbes) {
            microbesState.add(microbe.toPersistedState());
        }
        List<SimulationState.FoodState> foodState = new ArrayList<>(foodPellets.size());
        for (FoodPellet food : foodPellets) {
            if (!food.isConsumed()) {
                foodState.add(new SimulationState.FoodState(food.getX(), food.getY()));
            }
        }

        return new SimulationState(
                width,
                height,
                environment.getTemperature(),
                environment.getToxicity(),
                foodSpawnRate,
                simulationTick,
                worldStatsHistory,
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

        List<Microbe> microbes = worldState.population().microbes();
        List<Microbe> newMicrobes = worldState.population().newMicrobes();
        List<FoodPellet> foodPellets = worldState.food().pellets();

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

        worldState.index().byId().clear();
        for (Microbe microbe : microbes) {
            worldState.index().byId().put(microbe.getId(), microbe);
        }

        return snapshotFromCurrentWorld();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    SimulationSnapshot spawnMicrobe(Microbe microbe) {
        if (microbe == null) {
            return snapshotFromCurrentWorld();
        }
        List<Microbe> microbes = worldState.population().microbes();
        clampMicrobeToWorld(microbe);
        microbes.add(microbe);
        worldState.index().byId().put(microbe.getId(), microbe);
        return snapshotFromCurrentWorld();
    }

    SimulationSnapshot spawnFood(FoodPellet foodPellet) {
        if (foodPellet == null) {
            return snapshotFromCurrentWorld();
        }
        List<FoodPellet> foodPellets = worldState.food().pellets();
        foodPellets.add(clampFoodToWorld(foodPellet));
        return snapshotFromCurrentWorld();
    }

    private void clampMicrobeToWorld(Microbe microbe) {
        double clampedX = clamp(microbe.getX(), 0.0, width);
        double clampedY = clamp(microbe.getY(), 0.0, height);
        microbe.setPosition(clampedX, clampedY);
    }

    private FoodPellet clampFoodToWorld(FoodPellet foodPellet) {
        double clampedX = clamp(foodPellet.getX(), 0.0, width);
        double clampedY = clamp(foodPellet.getY(), 0.0, height);
        return new FoodPellet(clampedX, clampedY);
    }

    private SimulationSnapshot snapshotFromCurrentWorld() {
        List<Microbe> microbes = worldState.population().microbes();
        List<FoodPellet> foodPellets = worldState.food().pellets();
        List<Microbe.RenderState> renderStates = new ArrayList<>(microbes.size());
        for (Microbe microbe : microbes) {
            renderStates.add(microbe.toRenderState());
        }
        return new SimulationSnapshot(renderStates, List.copyOf(foodPellets));
    }
}
