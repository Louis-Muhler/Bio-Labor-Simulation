package com.biolab;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralizes save/autosave lifecycle for one gameplay session.
 */
final class SessionSaveCoordinator {
    private static final long AUTOSAVE_INTERVAL_SECONDS = 8L;

    private final SaveGameRepository saveRepository;
    private final AsyncSaveService asyncSaveService;
    private final Logger logger;

    private SaveGameMetadata currentSave;
    private String currentWorldName;
    private long sessionStartMillis;
    private ScheduledExecutorService autosaveExecutor;
    private ScheduledFuture<?> autosaveTask;

    SessionSaveCoordinator(SaveGameRepository saveRepository,
                           AsyncSaveService asyncSaveService,
                           Logger logger) {
        this.saveRepository = saveRepository;
        this.asyncSaveService = asyncSaveService;
        this.logger = logger;
    }

    synchronized void markSessionStarted(String worldName, SaveGameMetadata save) {
        this.currentWorldName = worldName;
        this.currentSave = save;
        this.sessionStartMillis = System.currentTimeMillis();
    }

    synchronized void clearSessionContext() {
        this.currentWorldName = null;
        this.currentSave = null;
        this.sessionStartMillis = 0L;
    }

    synchronized void saveCurrentWorld(SimulationRuntime engine, boolean gameplaySession) {
        if (engine == null || !gameplaySession) {
            return;
        }

        final SimulationState state = engine.captureState();
        final long playedSeconds = elapsedSessionSeconds();
        final SaveGameMetadata existingSave = currentSave;
        final String worldName = currentWorldName;

        boolean accepted = asyncSaveService.submit(() -> {
            try {
                SaveGameMetadata saved;
                if (existingSave == null) {
                    String mapName = (worldName == null || worldName.isBlank()) ? "Auto Save" : worldName;
                    WorldConfig cfg = new WorldConfig(
                            mapName,
                            state.worldWidth(),
                            state.worldHeight(),
                            state.microbes().size(),
                            Math.max(20_000, state.microbes().size() * 3),
                            state.temperature(),
                            state.toxicity(),
                            state.foodSpawnRate()
                    );
                    saved = saveRepository.createNewSave(cfg, state);
                } else {
                    saveRepository.overwriteSave(existingSave, state, playedSeconds);
                    saved = saveRepository.loadMetadata(existingSave.saveId());
                }

                synchronized (SessionSaveCoordinator.this) {
                    currentSave = saved;
                    sessionStartMillis = System.currentTimeMillis();
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Async save failed", ex);
            }
        });
        if (!accepted) {
            logger.fine("Save submission skipped because save worker is shutting down");
        }
    }

    synchronized void flushPendingSaves() {
        if (!asyncSaveService.flushAndWait(2, TimeUnit.SECONDS)) {
            logger.fine("Timed out while flushing pending saves");
        }
    }

    synchronized void startAutoSave(Supplier<SimulationRuntime> engineSupplier,
                                    BooleanSupplier gameplaySessionSupplier) {
        stopAutoSave();
        autosaveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BioLab-Autosave");
            t.setDaemon(true);
            return t;
        });
        autosaveTask = autosaveExecutor.scheduleAtFixedRate(
                () -> saveCurrentWorld(engineSupplier.get(), gameplaySessionSupplier.getAsBoolean()),
                AUTOSAVE_INTERVAL_SECONDS,
                AUTOSAVE_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    synchronized void stopAutoSave() {
        if (autosaveTask != null) {
            autosaveTask.cancel(false);
            autosaveTask = null;
        }
        if (autosaveExecutor != null) {
            autosaveExecutor.shutdownNow();
            autosaveExecutor = null;
        }
    }

    synchronized void shutdown() {
        stopAutoSave();
        asyncSaveService.shutdownAndFlush(2, TimeUnit.SECONDS);
    }

    private long elapsedSessionSeconds() {
        if (sessionStartMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, (System.currentTimeMillis() - sessionStartMillis) / 1000L);
    }
}


