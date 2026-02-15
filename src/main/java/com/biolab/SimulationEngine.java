package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Manages the simulation logic using multithreading.
 * This class handles the concurrent updating of all microbes.
 */
public class SimulationEngine {
    private static final Logger LOGGER = Logger.getLogger(SimulationEngine.class.getName());

    private final List<Microbe> microbes;
    private final List<Microbe> newMicrobes; // For reproduction
    private final Environment environment;
    private final int width;
    private final int height;


    // Atomic counter for available reproduction slots (updated each frame)
    private final AtomicInteger availableReproductionSlots;

    // ===== MULTITHREADING COMPONENTS =====
    // ExecutorService with a fixed thread pool for parallel processing
    private final ExecutorService executorService;
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    private static final int MAX_POPULATION = 20000; // Erhöht von 5000 auf 20000

    // Maximum number of reproduction attempts (including the initial attempt)
    // used when claiming reproduction slots to prevent thread starvation.
    // Microbes that fail after exhausting these attempts will try again next frame.
    private static final int MAX_REPRODUCTION_ATTEMPTS = 5;

    // Number of initial fast retries before applying backoff strategy
    private static final int MIN_RETRIES_BEFORE_BACKOFF = 2;

    // Shutdown timeout configuration
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 2;
    private static final int SHUTDOWN_NOW_TIMEOUT_SECONDS = 1;

    public SimulationEngine(int width, int height, int initialPopulation) {
        this.width = width;
        this.height = height;
        this.microbes = new CopyOnWriteArrayList<>(); // Thread-safe list
        this.newMicrobes = new CopyOnWriteArrayList<>();
        this.environment = new Environment();
        this.availableReproductionSlots = new AtomicInteger(MAX_POPULATION);

        // ===== MULTITHREADING: Create a FixedThreadPool =====
        // This pool will distribute simulation work across multiple CPU cores
        this.executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        LOGGER.info("SimulationEngine initialized with " + THREAD_COUNT + " threads for " +
                    Runtime.getRuntime().availableProcessors() + " available processors");

        // Initialize population
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < initialPopulation; i++) {
            double x = random.nextDouble() * width;
            double y = random.nextDouble() * height;
            microbes.add(new Microbe(x, y));
        }
    }

    /**
     * Main simulation update - called every frame.
     * This method orchestrates the MULTITHREADED simulation logic.
     */
    public void update() {
        // Get current environmental conditions (thread-safe)
        final double temp = environment.getTemperature();
        final double tox = environment.getToxicity();

        // Calculate available slots for reproduction this frame (prevents exceeding MAX_POPULATION)
        int currentPop = microbes.size();
        int availableSlots = Math.max(0, MAX_POPULATION - currentPop);
        availableReproductionSlots.set(availableSlots);

        // ===== MULTITHREADING: Parallel Processing =====
        // Split the microbe list into chunks and process each chunk in parallel
        // Use a snapshot of the actual list size to avoid race conditions
        final int microbeCount = microbes.size();
        if (microbeCount == 0) return;

        // Calculate chunk size for distributing work
        int chunkSize = Math.max(1, microbeCount / THREAD_COUNT);
        List<Future<?>> futures = new ArrayList<>();

        // Submit tasks to the thread pool
        for (int i = 0; i < microbeCount; i += chunkSize) {
            final int start = i;
            final int end = Math.min(i + chunkSize, microbeCount);

            // ===== MULTITHREADING: Submit parallel task =====
            // Each task processes a subset of microbes concurrently
            Future<?> future = executorService.submit(() -> processMicrobeChunk(start, end, temp, tox));
            futures.add(future);
        }

        // ===== MULTITHREADING: Wait for all threads to complete =====
        // This ensures all microbes are updated before proceeding
        for (Future<?> future : futures) {
            try {
                future.get(); // Block until this chunk is done
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                LOGGER.log(Level.WARNING, "Simulation thread was interrupted during microbe processing", e);
                return; // Exit early if interrupted
            } catch (ExecutionException e) {
                LOGGER.log(Level.SEVERE, "Error during microbe chunk processing", e.getCause());
            }
        }

        // Remove dead microbes (after all threads finished)
        microbes.removeIf(Microbe::isDead);

        // Add newborns to population (limit growth) based on actual list size
        int currentPopulation = microbes.size();
        int newbornCount = newMicrobes.size();

        if (currentPopulation + newbornCount <= MAX_POPULATION) {
            // All newborns can be added without exceeding the limit
            microbes.addAll(newMicrobes);
        } else {
            // Only add as many newborns as will fit within MAX_POPULATION
            int allowedNewborns = Math.max(0, MAX_POPULATION - currentPopulation);
            for (int i = 0; i < allowedNewborns && i < newbornCount; i++) {
                microbes.add(newMicrobes.get(i));
            }
        }

        newMicrobes.clear();
    }

    /**
     * Processes a chunk of microbes in a worker thread.
     * This method is executed CONCURRENTLY by multiple threads.
     *
     * @param start Start index (inclusive)
     * @param end End index (exclusive)
     * @param temperature Current temperature
     * @param toxicity Current toxicity
     */
    private void processMicrobeChunk(int start, int end, double temperature, double toxicity) {
        // Get ThreadLocalRandom once per method call for efficiency
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Add a live size check to avoid IndexOutOfBoundsException if the List shrinks concurrently
        for (int i = start; i < end && i < microbes.size(); i++) {
            // CopyOnWriteArrayList guarantees non-null elements
            Microbe microbe = microbes.get(i);

            // Update position
            microbe.move(width, height);

            // Update health based on environment (natural selection!)
            microbe.updateHealth(temperature, toxicity);

            // Check for reproduction
            if (microbe.canReproduce()) {
                // Atomically claim a reproduction slot to prevent exceeding MAX_POPULATION
                // Use a retry loop with CAS to strictly avoid negative slot counts
                int retryCount = 0;
                while (retryCount < MAX_REPRODUCTION_ATTEMPTS) {
                    int currentSlots = availableReproductionSlots.get();
                    if (currentSlots <= 0) {
                        break; // No slots available, give up for this frame
                    }

                    if (availableReproductionSlots.compareAndSet(currentSlots, currentSlots - 1)) {
                        // Successfully claimed a slot - create offspring
                        double offsetX = (random.nextDouble() - 0.5) * 20;
                        double offsetY = (random.nextDouble() - 0.5) * 20;
                        Microbe child = new Microbe(
                            microbe,
                            microbe.getX() + offsetX,
                            microbe.getY() + offsetY
                        );
                        newMicrobes.add(child);
                        microbe.resetReproduction();
                        break; // Success!
                    }

                    // CAS failed (contention), verify if we should back off
                    retryCount++;
                    if (retryCount > MIN_RETRIES_BEFORE_BACKOFF) {
                        Thread.yield(); // Yield to reduce CPU spinning
                    }
                }
            }
        }
    }

    /**
     * Gets a snapshot of all microbes (thread-safe).
     */
    public List<Microbe> getMicrobes() {
        synchronized (microbes) {
            return new ArrayList<>(microbes);
        }
    }

    public Environment getEnvironment() {
        return environment;
    }

    public int getPopulationCount() {
        return microbes.size(); // CopyOnWriteArrayList.size() is thread-safe
    }

    /**
     * Cleanup method - shuts down the thread pool gracefully.
     */
    public void shutdown() {
        LOGGER.info("Shutting down simulation engine...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warning("Executor did not terminate in time, forcing shutdown...");
                executorService.shutdownNow();
                // Wait a bit for tasks to respond to being canceled
                if (!executorService.awaitTermination(SHUTDOWN_NOW_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    LOGGER.severe("Executor did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Shutdown interrupted, forcing immediate shutdown", e);
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
        LOGGER.info("Simulation engine shutdown complete");
    }
}
