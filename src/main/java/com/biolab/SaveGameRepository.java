package com.biolab;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Stores simulation save files and metadata under the app save directory.
 */
public class SaveGameRepository {
    private static final String STATE_FILE_NAME = "state.bls";
    private static final String META_FILE_NAME = "meta.properties";

    private final Path savesRoot;
    private final SimulationStateService stateService;

    public SaveGameRepository() {
        this(AppPaths.getSavesDir(), new SimulationStateService());
    }

    SaveGameRepository(Path savesRoot, SimulationStateService stateService) {
        this.savesRoot = savesRoot;
        this.stateService = stateService;
    }

    public synchronized List<SaveGameMetadata> listSaves() throws IOException {
        ensureRoot();
        List<SaveGameMetadata> result = new ArrayList<>();
        try (var stream = Files.list(savesRoot)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                try {
                    Path metaFile = dir.resolve(META_FILE_NAME);
                    if (Files.exists(metaFile)) {
                        Properties props = new Properties();
                        try (InputStream in = Files.newInputStream(metaFile)) {
                            props.load(in);
                        }
                        result.add(SaveGameMetadata.fromProperties(props));
                    }
                } catch (IOException | RuntimeException ignored) {
                    // Skip invalid save folders.
                }
            });
        }
        result.sort(Comparator.comparingLong(SaveGameMetadata::lastPlayedAtEpochSeconds).reversed());
        return result;
    }

    public synchronized Optional<SaveGameMetadata> findMostRecentSave() throws IOException {
        List<SaveGameMetadata> saves = listSaves();
        if (saves.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(saves.get(0));
    }

    public synchronized SaveGameMetadata createNewSave(WorldConfig config, SimulationState initialState) throws IOException {
        ensureRoot();
        String saveId = Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path saveDir = savesRoot.resolve(saveId);
        Files.createDirectories(saveDir);
        stateService.save(saveDir.resolve(STATE_FILE_NAME), initialState);
        SaveGameMetadata metadata = SaveGameMetadata.createNew(saveId, config, initialState.microbes().size());
        writeMetadata(saveDir, metadata);
        return metadata;
    }

    public synchronized void overwriteSave(SaveGameMetadata metadata, SimulationState state, long sessionPlaytimeSeconds) throws IOException {
        ensureRoot();
        Path saveDir = savesRoot.resolve(metadata.saveId());
        Files.createDirectories(saveDir);
        stateService.save(saveDir.resolve(STATE_FILE_NAME), state);
        SaveGameMetadata updated = metadata.withSessionUpdate(sessionPlaytimeSeconds, state.microbes().size());
        writeMetadata(saveDir, updated);
    }

    public synchronized SimulationState loadState(String saveId) throws IOException {
        Path saveDir = savesRoot.resolve(saveId);
        return stateService.load(saveDir.resolve(STATE_FILE_NAME));
    }

    public synchronized SaveGameMetadata loadMetadata(String saveId) throws IOException {
        Path meta = savesRoot.resolve(saveId).resolve(META_FILE_NAME);
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(meta)) {
            props.load(in);
        }
        return SaveGameMetadata.fromProperties(props);
    }

    public synchronized void deleteSave(String saveId) throws IOException {
        Path dir = savesRoot.resolve(saveId);
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    public synchronized SaveGameMetadata renameSave(String saveId, String newMapName) throws IOException {
        ensureRoot();
        String trimmedName = (newMapName == null) ? "" : newMapName.trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Map name must not be empty.");
        }

        Path saveDir = savesRoot.resolve(saveId);
        Files.createDirectories(saveDir);
        SaveGameMetadata current = loadMetadata(saveId);
        SaveGameMetadata renamed = current.withMapName(trimmedName);
        writeMetadata(saveDir, renamed);
        return renamed;
    }

    private void writeMetadata(Path saveDir, SaveGameMetadata metadata) throws IOException {
        Properties props = metadata.toProperties();
        try (OutputStream out = Files.newOutputStream(saveDir.resolve(META_FILE_NAME))) {
            props.store(out, "Bio-Lab save metadata");
        }
    }

    private void ensureRoot() throws IOException {
        Files.createDirectories(savesRoot);
    }
}

