package com.biolab;

/**
 * Command executed on the simulation loop thread to serialize UI writes.
 */
@FunctionalInterface
public interface SimulationCommand {
    static SimulationCommand setTemperature(double value) {
        return runtime -> runtime.getEnvironment().setTemperature(value);
    }

    static SimulationCommand setToxicity(double value) {
        return runtime -> runtime.getEnvironment().setToxicity(value);
    }

    static SimulationCommand setFoodSpawnRate(double value) {
        return runtime -> runtime.setFoodSpawnRate(value);
    }

    static SimulationCommand toggleDebugMode() {
        return runtime -> runtime.toggleDebugModeFlag();
    }

    void apply(SimulationRuntime runtime);
}


