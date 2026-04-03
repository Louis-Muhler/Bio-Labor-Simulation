package com.biolab;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SimulationStateServiceTest {

    @Test
    void saveLoadRoundTripShouldPreserveCoreState() throws IOException {
        SimulationEngine.DEBUG_MODE = false;

        SimulationEngine source = new SimulationEngine(500, 500, 25);
        source.getEnvironment().setTemperature(0.77);
        source.getEnvironment().setToxicity(0.12);
        source.setFoodSpawnRate(0.65);

        SimulationState captured = source.captureState();

        SimulationStateService service = new SimulationStateService();
        Path file = Files.createTempFile("biolab-state-", ".bin");
        service.save(file, captured);
        SimulationState loaded = service.load(file);

        SimulationEngine target = new SimulationEngine(500, 500, 0);
        target.loadState(loaded);

        assertEquals(captured.microbes().size(), target.getPopulationCount());
        assertEquals(0.77, target.getEnvironment().getTemperature(), 0.0001);
        assertEquals(0.12, target.getEnvironment().getToxicity(), 0.0001);
        assertEquals(0.65, target.getFoodSpawnRate(), 0.0001);

        source.shutdown();
        target.shutdown();
    }

    @Test
    void loadedDebugModeShouldBeApplied() {
        SimulationEngine.DEBUG_MODE = false;

        SimulationEngine engine = new SimulationEngine(300, 300, 0);
        SimulationState state = new SimulationState(
                300,
                300,
                0.2,
                0.3,
                0.4,
                java.util.List.of(),
                java.util.List.of(),
                true
        );

        engine.loadState(state);
        assertTrue(SimulationEngine.DEBUG_MODE);

        engine.shutdown();
        SimulationEngine.DEBUG_MODE = false;
        assertFalse(SimulationEngine.DEBUG_MODE);
    }

    @Test
    void loadShouldSupportLegacyUnwrappedStateFiles() throws IOException {
        SimulationStateService service = new SimulationStateService();
        Path file = Files.createTempFile("biolab-legacy-state-", ".bin");
        SimulationState legacy = new SimulationState(
                300,
                300,
                0.1,
                0.2,
                0.3,
                java.util.List.of(),
                java.util.List.of(),
                false
        );

        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) {
            out.writeObject(legacy);
        }

        SimulationState loaded = service.load(file);
        assertEquals(legacy.worldWidth(), loaded.worldWidth());
        assertEquals(legacy.worldHeight(), loaded.worldHeight());
        assertEquals(legacy.foodSpawnRate(), loaded.foodSpawnRate(), 0.0001);
    }

    @Test
    void loadStateShouldRejectDimensionMismatch() {
        SimulationEngine engine = new SimulationEngine(300, 300, 0);
        SimulationState wrongDims = new SimulationState(
                301,
                300,
                0.2,
                0.3,
                0.4,
                java.util.List.of(),
                java.util.List.of(),
                false
        );

        assertThrows(IllegalArgumentException.class, () -> engine.loadState(wrongDims));

        engine.shutdown();
    }
}

