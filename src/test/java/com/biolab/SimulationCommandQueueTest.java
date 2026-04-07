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

    @Test
    void spawnMicrobeCommandShouldCreateConfiguredBurst() {
        SimulationEngine engine = new SimulationEngine(300, 300, 1);
        int before = engine.getPopulationCount();

        MicrobeGeneProfile profile = new MicrobeGeneProfile(0.4, 0.6, 0.5, 0.3, 120, 110);
        engine.enqueueCommand(SimulationCommand.spawnMicrobes(
                new MicrobeSpawnRequest(120, 140, 7, false, profile, profile)
        ));

        engine.update();

        assertEquals(before + 7, engine.getPopulationCount());
        engine.shutdown();
    }

    @Test
    void spawnFoodCommandShouldCreateConfiguredBurst() {
        SimulationEngine engine = new SimulationEngine(300, 300, 0);
        engine.setFoodSpawnRate(0.0);
        int before = engine.getRenderSnapshot().food().size();

        engine.enqueueCommand(SimulationCommand.spawnFood(new FoodSpawnRequest(80, 90, 11)));
        engine.update();

        int after = engine.getRenderSnapshot().food().size();
        assertEquals(before + 11, after);
        engine.shutdown();
    }

    @Test
    void spawnCommandsShouldClampOutOfBoundsPositions() {
        SimulationEngine engine = new SimulationEngine(300, 300, 0);
        engine.setFoodSpawnRate(0.0);

        MicrobeGeneProfile profile = new MicrobeGeneProfile(0.5, 0.5, 0.5, 0.5, 100, 100);
        engine.enqueueCommand(SimulationCommand.spawnMicrobes(
                new MicrobeSpawnRequest(-500, 9999, 1, false, profile, profile)
        ));
        engine.enqueueCommand(SimulationCommand.spawnFood(new FoodSpawnRequest(-500, 9999, 1)));

        engine.update();

        SimulationSnapshot snapshot = engine.getRenderSnapshot();
        assertTrue(snapshot.microbes().stream().allMatch(m -> m.x() >= 0 && m.x() <= 300 && m.y() >= 0 && m.y() <= 300));
        assertTrue(snapshot.food().stream().allMatch(f -> f.getX() >= 0 && f.getX() <= 300 && f.getY() >= 0 && f.getY() <= 300));

        engine.shutdown();
    }
}

