package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates one full simulation frame using preconfigured systems.
 */
final class SimulationFrameOrchestrator {
    private final ExecutorService executorService;
    private final int threadCount;
    private final SpatialGrid spatialGrid;
    private final MicrobeGrid microbeGrid;
    private final MicrobeBehaviorSystem behaviorSystem;
    private final PopulationCommitSystem populationCommitSystem;
    private final Logger logger;
    private final ArrayList<Future<?>> futureBuffer = new ArrayList<>();

    SimulationFrameOrchestrator(ExecutorService executorService,
                                int threadCount,
                                SpatialGrid spatialGrid,
                                MicrobeGrid microbeGrid,
                                MicrobeBehaviorSystem behaviorSystem,
                                PopulationCommitSystem populationCommitSystem,
                                Logger logger) {
        this.executorService = executorService;
        this.threadCount = threadCount;
        this.spatialGrid = spatialGrid;
        this.microbeGrid = microbeGrid;
        this.behaviorSystem = behaviorSystem;
        this.populationCommitSystem = populationCommitSystem;
        this.logger = logger;
    }

    SimulationFrameResult runFrame(List<Microbe> microbeSnapshot,
                                List<FoodPellet> foodSnapshot,
                                   int spawnedFoodCount,
                                double temperature,
                                double toxicity) throws InterruptedException {
        int microbeCount = microbeSnapshot.size();
        if (microbeCount == 0) {
            return populationCommitSystem.finalizeEmptyFrame(spawnedFoodCount);
        }

        spatialGrid.rebuild(foodSnapshot);
        microbeGrid.rebuild(microbeSnapshot);

        int chunkSize = Math.max(1, microbeCount / threadCount);
        futureBuffer.clear();

        for (int i = 0; i < microbeCount; i += chunkSize) {
            final int start = i;
            final int end = Math.min(i + chunkSize, microbeCount);
            Future<?> future = executorService.submit(() ->
                    behaviorSystem.processChunk(microbeSnapshot, spatialGrid, microbeGrid, start, end, temperature, toxicity));
            futureBuffer.add(future);
        }

        for (Future<?> future : futureBuffer) {
            try {
                future.get();
            } catch (ExecutionException e) {
                logger.log(Level.SEVERE, "Error during microbe chunk processing", e.getCause());
            }
        }

        return populationCommitSystem.finalizeFrame(spawnedFoodCount);
    }
}

