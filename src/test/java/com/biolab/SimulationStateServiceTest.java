package com.biolab;

import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SimulationStateServiceTest {

    @Test
    void saveLoadRoundTripShouldPreserveCoreState() throws IOException {
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
        assertEquals(captured.worldStatsHistory(), loaded.worldStatsHistory());

        source.shutdown();
        target.shutdown();
    }

    @Test
    void loadedDebugModeShouldBeApplied() {
        SimulationEngine engine = new SimulationEngine(300, 300, 0);
        SimulationState state = new SimulationState(
                300,
                300,
                0.2,
                0.3,
                0.4,
                0L,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                true
        );

        engine.loadState(state);
        assertTrue(engine.isDebugModeEnabled());

        engine.shutdown();
    }

    @Test
    void loadShouldRejectCorruptedFiles() throws IOException {
        SimulationStateService service = new SimulationStateService();
        Path file = Files.createTempFile("biolab-corrupt-state-", ".bin");

        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(new byte[]{0x01, 0x02, 0x03, 0x04});
        }

        assertThrows(IOException.class, () -> service.load(file));
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
                0L,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                false
        );

        assertThrows(IllegalArgumentException.class, () -> engine.loadState(wrongDims));

        engine.shutdown();
    }

    @Test
    void loadShouldRejectLegacyFormatV1() throws IOException {
        Path file = Files.createTempFile("biolab-legacy-v1-", ".bin");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(0x424C5331); // MAGIC
            out.writeInt(1);          // FORMAT_VERSION v1
            out.writeInt(400);
            out.writeInt(300);
            out.writeDouble(0.4);
            out.writeDouble(0.2);
            out.writeDouble(0.7);
            out.writeBoolean(false);
            out.writeInt(0); // microbes
            out.writeInt(0); // food
        }

        SimulationStateService service = new SimulationStateService();
        assertThrows(IOException.class, () -> service.load(file));
    }
}

