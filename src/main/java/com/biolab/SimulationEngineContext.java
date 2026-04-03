package com.biolab;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Internal wiring container for engine services.
 */
record SimulationEngineContext(
        SimulationCommandProcessor commandProcessor,
        SimulationUpdateService updateService,
        SimulationStateCoordinator stateCoordinator,
        MicrobeLookupService microbeLookupService,
        SimulationLifecycleService lifecycleService
) {
    static SimulationEngineContext create(
            int maxQueuedCommands,
            Object frameMutationLock,
            Object dataLock,
            ExecutorService executorService,
            Environment environment,
            DebugModeService debugModeService,
            List<Microbe> microbes,
            List<Microbe> newMicrobes,
            List<FoodPellet> foodPellets,
            ConcurrentHashMap<Long, Microbe> microbeById,
            AtomicInteger availableReproductionSlots,
            int maxPopulation,
            int threadCount,
            int worldWidth,
            int worldHeight,
            int spatialCellSize,
            int maxFoodPellets,
            SimulationRuntime runtime,
            Logger logger
    ) {
        SimulationCommandProcessor commandProcessor = new SimulationCommandProcessor(maxQueuedCommands);

        FramePreparationSystem framePreparationSystem = new FramePreparationSystem(
                dataLock,
                microbes,
                foodPellets,
                availableReproductionSlots,
                maxPopulation,
                worldWidth,
                worldHeight,
                maxFoodPellets
        );

        MicrobeBehaviorSystem behaviorSystem = new MicrobeBehaviorSystem(
                worldWidth,
                worldHeight,
                availableReproductionSlots,
                newMicrobes
        );

        PopulationCommitSystem populationCommitSystem = new PopulationCommitSystem(
                dataLock,
                microbes,
                newMicrobes,
                foodPellets,
                microbeById,
                maxPopulation
        );

        SpatialGrid spatialGrid = new SpatialGrid(worldWidth, worldHeight, spatialCellSize);
        MicrobeGrid microbeGrid = new MicrobeGrid(worldWidth, worldHeight, spatialCellSize);

        SimulationFrameOrchestrator frameOrchestrator = new SimulationFrameOrchestrator(
                executorService,
                threadCount,
                spatialGrid,
                microbeGrid,
                behaviorSystem,
                populationCommitSystem,
                logger
        );

        SimulationUpdateService updateService = new SimulationUpdateService(
                frameMutationLock,
                executorService,
                commandProcessor,
                runtime,
                environment,
                framePreparationSystem,
                frameOrchestrator,
                logger
        );

        SimulationStateCoordinator stateCoordinator = new SimulationStateCoordinator(
                worldWidth,
                worldHeight,
                environment,
                debugModeService,
                microbes,
                newMicrobes,
                foodPellets,
                microbeById
        );

        MicrobeLookupService microbeLookupService = new MicrobeLookupService(dataLock, microbes);
        SimulationLifecycleService lifecycleService = new SimulationLifecycleService(executorService, logger);

        return new SimulationEngineContext(
                commandProcessor,
                updateService,
                stateCoordinator,
                microbeLookupService,
                lifecycleService
        );
    }
}

