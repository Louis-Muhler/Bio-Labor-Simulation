package com.biolab;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns graceful/forced shutdown semantics of the simulation executor.
 */
final class SimulationLifecycleService {
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 2;
    private static final int SHUTDOWN_NOW_TIMEOUT_SECONDS = 1;

    private final ExecutorService executorService;
    private final Logger logger;

    SimulationLifecycleService(ExecutorService executorService, Logger logger) {
        this.executorService = executorService;
        this.logger = logger;
    }

    void shutdown() {
        logger.info("Shutting down simulation engine...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warning("Executor did not terminate in time, forcing shutdown...");
                executorService.shutdownNow();
                if (!executorService.awaitTermination(SHUTDOWN_NOW_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    logger.severe("Executor did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Shutdown interrupted, forcing immediate shutdown", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Simulation engine shutdown complete");
    }
}

