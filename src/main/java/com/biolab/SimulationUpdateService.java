package com.biolab;

import java.util.List;
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

    private static SimulationSnapshot buildFallbackSnapshot(FramePreparationSystem.FrameBatch frameData) {
        return new SimulationSnapshot(
                frameData.microbeSnapshot().stream().map(Microbe::toRenderState).toList(),
                List.copyOf(frameData.foodSnapshot())
        );
    }

    SimulationFrameResult runUpdate(SimulationSnapshot currentSnapshot, double foodSpawnRate) {
        synchronized (frameMutationLock) {
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
                logger.log(Level.WARNING, "Simulation thread interrupted during processing", e);
                return new SimulationFrameResult(buildFallbackSnapshot(frameData), 0, 0);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Simulation frame aborted due to worker failure", e);
                return new SimulationFrameResult(buildFallbackSnapshot(frameData), 0, 0);
            }
        }
    }
}
