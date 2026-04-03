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
        SimulationEngine engine = new SimulationEngine(300, 300, 1);

        engine.enqueueCommand(SimulationCommand.toggleDebugMode());
        engine.update();

        assertTrue(engine.isDebugModeEnabled());

        engine.enqueueCommand(SimulationCommand.toggleDebugMode());
        engine.update();

        assertFalse(engine.isDebugModeEnabled());

        engine.shutdown();
    }

    @Test
    void burstSliderCommandsShouldCoalesceToLatestValues() {
        SimulationEngine engine = new SimulationEngine(300, 300, 1);

        for (int i = 0; i < 10_000; i++) {
            double value = (i % 1000) / 1000.0;
            engine.enqueueCommand(SimulationCommand.setTemperature(value));
            engine.enqueueCommand(SimulationCommand.setToxicity(1.0 - value));
            engine.enqueueCommand(SimulationCommand.setFoodSpawnRate(value));
        }

        engine.update();

        double expected = 0.999;
        assertEquals(expected, engine.getEnvironment().getTemperature(), 0.0001);
        assertEquals(1.0 - expected, engine.getEnvironment().getToxicity(), 0.0001);
        assertEquals(expected, engine.getFoodSpawnRate(), 0.0001);

        engine.shutdown();
    }
}

