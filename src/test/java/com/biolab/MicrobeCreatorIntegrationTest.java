package com.biolab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrobeCreatorIntegrationTest {

    @Test
    void microbeSpawnShouldRespectPositionAndAmount() {
        SimulationEngine engine = new SimulationEngine(300, 300, 0);
        MicrobeCreatorPanel panel = new MicrobeCreatorPanel();

        panel.setSelectedMode(MicrobeCreatorPanel.SpawnMode.MICROBE);
        panel.setRandomEnabled(false);
        panel.setMicrobeAmount(4);

        engine.enqueueCommand(panel.buildSpawnCommand(42.5, 84.5));
        engine.update();

        SimulationSnapshot snapshot = engine.getRenderSnapshot();
        assertEquals(4, snapshot.microbes().size());
        assertTrue(snapshot.microbes().stream().allMatch(m -> {
            double dx = m.x() - 42.5;
            double dy = m.y() - 84.5;
            return dx * dx + dy * dy <= 25.0;
        }));

        engine.shutdown();
    }

    @Test
    void foodSpawnShouldRespectPositionAndAmount() {
        SimulationEngine engine = new SimulationEngine(300, 300, 0);
        engine.setFoodSpawnRate(0.0);
        MicrobeCreatorPanel panel = new MicrobeCreatorPanel();

        int before = engine.getRenderSnapshot().food().size();

        panel.setSelectedMode(MicrobeCreatorPanel.SpawnMode.FOOD);
        panel.setFoodAmount(6);

        engine.enqueueCommand(panel.buildSpawnCommand(12.25, 34.75));
        engine.update();

        SimulationSnapshot snapshot = engine.getRenderSnapshot();
        int exactSpawned = (int) snapshot.food().stream()
                .filter(f -> f.getX() == 12.25 && f.getY() == 34.75)
                .count();

        assertEquals(before + 6, snapshot.food().size());
        assertEquals(6, exactSpawned);

        engine.shutdown();
    }
}


