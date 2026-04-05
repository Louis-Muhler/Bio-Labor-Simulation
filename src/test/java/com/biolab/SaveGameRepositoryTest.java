package com.biolab;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveGameRepositoryTest {

    @Test
    void createListLoadAndOverwriteShouldWork() throws IOException {
        Path savesDir = Files.createTempDirectory("biolab-saves-test-");
        SaveGameRepository repository = new SaveGameRepository(savesDir, new SimulationStateService());

        WorldConfig config = new WorldConfig("Alpha", 800, 700, 30, 500, 0.2, 0.4, 0.5);
        SimulationState initialState = new SimulationState(
                800,
                700,
                0.2,
                0.4,
                0.5,
                0L,
                List.of(),
                List.of(),
                List.of(),
                false
        );

        SaveGameMetadata created = repository.createNewSave(config, initialState);
        List<SaveGameMetadata> list = repository.listSaves();
        assertEquals(1, list.size());
        assertEquals("Alpha", list.get(0).mapName());

        SimulationState loaded = repository.loadState(created.saveId());
        assertEquals(800, loaded.worldWidth());
        assertEquals(700, loaded.worldHeight());

        repository.overwriteSave(created, initialState, 42);
        SaveGameMetadata updated = repository.loadMetadata(created.saveId());
        assertTrue(updated.playtimeSeconds() >= 42);

        repository.deleteSave(created.saveId());
        assertTrue(repository.listSaves().isEmpty());
    }
}

