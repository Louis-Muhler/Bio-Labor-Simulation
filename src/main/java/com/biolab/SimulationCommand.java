package com.biolab;

/**
 * Command executed on the simulation loop thread to serialize UI writes.
 */
@FunctionalInterface
public interface SimulationCommand {
    static SimulationCommand setTemperature(double value) {
        return new SimulationCommand() {
            @Override
            public void apply(SimulationRuntime runtime) {
                runtime.getEnvironment().setTemperature(value);
            }

            @Override
            public CommandKey coalescingKey() {
                return CommandKey.SET_TEMPERATURE;
            }
        };
    }

    static SimulationCommand setToxicity(double value) {
        return new SimulationCommand() {
            @Override
            public void apply(SimulationRuntime runtime) {
                runtime.getEnvironment().setToxicity(value);
            }

            @Override
            public CommandKey coalescingKey() {
                return CommandKey.SET_TOXICITY;
            }
        };
    }

    static SimulationCommand setFoodSpawnRate(double value) {
        return new SimulationCommand() {
            @Override
            public void apply(SimulationRuntime runtime) {
                runtime.setFoodSpawnRate(value);
            }

            @Override
            public CommandKey coalescingKey() {
                return CommandKey.SET_FOOD_SPAWN_RATE;
            }
        };
    }

    default CommandKey coalescingKey() {
        return null;
    }

    static SimulationCommand toggleDebugMode() {
        return SimulationRuntime::toggleDebugModeFlag;
    }

    void apply(SimulationRuntime runtime);

    enum CommandKey {
        SET_TEMPERATURE,
        SET_TOXICITY,
        SET_FOOD_SPAWN_RATE
    }
}


