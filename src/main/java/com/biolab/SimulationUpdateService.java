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
    private final Runnable processPendingCommands;
    private final Environment environment;
    private final FramePreparationSystem framePreparationSystem;
    private final SimulationFrameOrchestrator frameOrchestrator;
    private final Logger logger;

    SimulationUpdateService(Object frameMutationLock,
                            ExecutorService executorService,
                            Runnable processPendingCommands,
                            Environment environment,
                            FramePreparationSystem framePreparationSystem,
                            SimulationFrameOrchestrator frameOrchestrator,
                            Logger logger) {
        this.frameMutationLock = frameMutationLock;
        this.executorService = executorService;
        this.processPendingCommands = processPendingCommands;
        this.environment = environment;
        this.framePreparationSystem = framePreparationSystem;
        this.frameOrchestrator = frameOrchestrator;
        this.logger = logger;
    }

    SimulationSnapshot runUpdate(SimulationSnapshot currentSnapshot, double foodSpawnRate) {
        synchronized (frameMutationLock) {
            if (executorService.isShutdown()) return currentSnapshot;

            processPendingCommands.run();

            final double temp = environment.getTemperature();
            final double tox = environment.getToxicity();
            FramePreparationSystem.FrameBatch frameData = framePreparationSystem.prepare(foodSpawnRate);

            try {
                return frameOrchestrator.runFrame(
                        frameData.microbeSnapshot(),
                        frameData.foodSnapshot(),
                        temp,
                        tox
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.log(Level.WARNING, "Simulation thread interrupted during processing", e);
                return currentSnapshot;
            }
        }
    }
}

