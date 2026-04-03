package com.biolab;

/**
 * Read-focused view of the simulation state used by UI and inspection code.
 */
public interface SimulationWorld {
    SimulationEngine.RenderSnapshot getRenderSnapshot();

    Microbe findLivingChild(long parentId);

    Microbe findRandomLivingMicrobe();

    Microbe findMicrobeById(long id);

    int getPopulationCount();

    Environment getEnvironment();

    double getFoodSpawnRate();

    SimulationState captureState();

    boolean isDebugModeEnabled();
}

