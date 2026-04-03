package com.biolab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationCommandQueueTest {

    @Test
    void queuedEnvironmentCommandsShouldApplyOnNextUpdate() {
        SimulationEngine engine = new SimulationEngine(300, 300, 5);

        engine.enqueueCommand(SimulationCommand.setTemperature(0.85));
        engine.enqueueCommand(SimulationCommand.setToxicity(0.15));
        engine.enqueueCommand(SimulationCommand.setFoodSpawnRate(0.42));

        engine.update();

        assertEquals(0.85, engine.getEnvironment().getTemperature(), 0.0001);
        assertEquals(0.15, engine.getEnvironment().getToxicity(), 0.0001);
        assertEquals(0.42, engine.getFoodSpawnRate(), 0.0001);

        engine.shutdown();
    }

    @Test
    void queuedDebugToggleShouldSwitchDebugFlag() {
        SimulationEngine.DEBUG_MODE = false;
        SimulationEngine engine = new SimulationEngine(300, 300, 1);

        engine.enqueueCommand(SimulationCommand.toggleDebugMode());
        engine.update();

        assertTrue(engine.isDebugModeEnabled());

        engine.enqueueCommand(SimulationCommand.toggleDebugMode());
        engine.update();

        assertFalse(engine.isDebugModeEnabled());

        engine.shutdown();
        SimulationEngine.DEBUG_MODE = false;
    }
}

