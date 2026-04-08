package com.biolab;

/**
 * Runtime control surface for the simulation loop thread.
 */
public interface SimulationRuntime {
    SimulationSnapshot getRenderSnapshot();

    Microbe findLivingChild(long parentId);

    Microbe findRandomLivingMicrobe();

    Microbe findMicrobeById(long id);

    int getPopulationCount();

    Environment getEnvironment();

    double getFoodSpawnRate();

    WorldStatsStore getWorldStatsStore();

    SimulationState captureState();

    boolean isDebugModeEnabled();

    default long getSimulationTick() {
        return 0L;
    }

    default int getDebugVisionRadius() {
        return 0;
    }

    void update();

    void loadState(SimulationState state);

    void spawnMicrobe(Microbe microbe);

    default void spawnFood(FoodPellet foodPellet) {
        // Optional runtime capability used by spawn commands.
    }

    void setFoodSpawnRate(double rate);

    void enqueueCommand(SimulationCommand command);

    boolean toggleDebugModeFlag();

    boolean isRunning();

    void shutdown();
}

