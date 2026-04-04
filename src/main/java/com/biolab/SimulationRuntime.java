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

    SimulationState captureState();

    boolean isDebugModeEnabled();

    void update();

    void loadState(SimulationState state);

    void spawnMicrobe(Microbe microbe);

    void setFoodSpawnRate(double rate);

    void enqueueCommand(SimulationCommand command);

    boolean toggleDebugModeFlag();

    boolean isRunning();

    void shutdown();
}

