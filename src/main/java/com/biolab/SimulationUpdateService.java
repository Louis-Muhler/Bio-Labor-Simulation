package com.biolab;

import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs one simulation frame update while preserving the engine's lock semantics.
 */
final class SimulationUpdateService {
    private final Object frameMutationLock;
    private final ExecutorService executorService;
    private final SimulationCommandProcessor commandProcessor;
    private final SimulationRuntime runtime;
    private final Environment environment;
    private final FramePreparationSystem framePreparationSystem;
    private final SimulationFrameOrchestrator frameOrchestrator;
    private final Logger logger;

    SimulationUpdateService(Object frameMutationLock,
                            ExecutorService executorService,
                            SimulationCommandProcessor commandProcessor,
                            SimulationRuntime runtime,
                            Environment environment,
                            FramePreparationSystem framePreparationSystem,
                            SimulationFrameOrchestrator frameOrchestrator,
                            Logger logger) {
        this.frameMutationLock = frameMutationLock;
        this.executorService = executorService;
        this.commandProcessor = commandProcessor;
        this.runtime = runtime;
        this.environment = environment;
        this.framePreparationSystem = framePreparationSystem;
        this.frameOrchestrator = frameOrchestrator;
        this.logger = logger;
    }

    private SimulationFrameResult stopRuntimeAndKeepLastConsistentSnapshot(SimulationSnapshot currentSnapshot,
                                                                           String reason,
                                                                           Throwable cause) {
        logger.log(Level.SEVERE, reason + " - runtime will be stopped to avoid committing partial frame mutations", cause);
        if (runtime.isRunning()) {
            runtime.shutdown();
        }
        return new SimulationFrameResult(currentSnapshot, 0, 0);
    }

    SimulationFrameResult runUpdate(SimulationSnapshot currentSnapshot, double foodSpawnRate) {
        synchronized (frameMutationLock) {
            // Frame invariant: publish either a fully committed frame or keep the last
            // known-good snapshot; partial worker mutations must never be published.
            if (executorService.isShutdown()) return new SimulationFrameResult(currentSnapshot, 0, 0);

            commandProcessor.processPending(runtime, logger);

            final double temp = environment.getTemperature();
            final double tox = environment.getToxicity();
            FramePreparationSystem.FrameBatch frameData = framePreparationSystem.prepare(foodSpawnRate);

            try {
                return frameOrchestrator.runFrame(
                        frameData.microbeSnapshot(),
                        frameData.foodSnapshot(),
                        frameData.spawnedFoodCount(),
                        temp,
                        tox
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return stopRuntimeAndKeepLastConsistentSnapshot(
                        currentSnapshot,
                        "Simulation thread interrupted during frame processing",
                        e
                );
            } catch (RuntimeException e) {
                return stopRuntimeAndKeepLastConsistentSnapshot(
                        currentSnapshot,
                        "Simulation frame aborted due to worker failure",
                        e
                );
            }
        }
    }
}
