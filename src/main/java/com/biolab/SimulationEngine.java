package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.Random;

/**
 * Manages the simulation logic using multithreading.
 * This class handles the concurrent updating of all microbes.
 */
public class SimulationEngine {
    private final List<Microbe> microbes;
    private final List<Microbe> newMicrobes; // For reproduction
    private final Environment environment;
    private final int width;
    private final int height;

    // ===== MULTITHREADING COMPONENTS =====
    // ExecutorService with a fixed thread pool for parallel processing
    private final ExecutorService executorService;
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    private final Random random;
    private static final int MAX_POPULATION = 5000;

    public SimulationEngine(int width, int height, int initialPopulation) {
        this.width = width;
        this.height = height;
        this.microbes = new CopyOnWriteArrayList<>(); // Thread-safe list
        this.newMicrobes = new CopyOnWriteArrayList<>();
        this.environment = new Environment();
        this.random = new Random();

        // ===== MULTITHREADING: Create a FixedThreadPool =====
        // This pool will distribute simulation work across multiple CPU cores
        this.executorService = Executors.newFixedThreadPool(THREAD_COUNT);

        // Initialize population
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

        // ===== MULTITHREADING: Parallel Processing =====
        // Split the microbe list into chunks and process each chunk in parallel
        int microbeCount = microbes.size();
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
            Future<?> future = executorService.submit(() -> {
                processMicrobeChunk(start, end, temp, tox);
            });
            futures.add(future);
        }

        // ===== MULTITHREADING: Wait for all threads to complete =====
        // This ensures all microbes are updated before proceeding
        for (Future<?> future : futures) {
            try {
                future.get(); // Block until this chunk is done
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        // Remove dead microbes (after all threads finished)
        microbes.removeIf(Microbe::isDead);

        // Add newborns to population (limit growth)
        if (microbes.size() + newMicrobes.size() <= MAX_POPULATION) {
            microbes.addAll(newMicrobes);
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
        for (int i = start; i < end && i < microbes.size(); i++) {
            Microbe microbe = microbes.get(i);

            // Update position
            microbe.move(width, height);

            // Update health based on environment (natural selection!)
            microbe.updateHealth(temperature, toxicity);

            // Check for reproduction
            if (microbe.canReproduce() && microbes.size() < MAX_POPULATION) {
                // Create offspring with slight genetic mutation
                double offsetX = (random.nextDouble() - 0.5) * 20;
                double offsetY = (random.nextDouble() - 0.5) * 20;
                Microbe child = new Microbe(
                    microbe,
                    microbe.getX() + offsetX,
                    microbe.getY() + offsetY
                );
                newMicrobes.add(child);
                microbe.resetReproduction();
            }
        }
    }

    /**
     * Gets a snapshot of all microbes (thread-safe).
     */
    public List<Microbe> getMicrobes() {
        return new ArrayList<>(microbes);
    }

    public Environment getEnvironment() {
        return environment;
    }

    public int getPopulationCount() {
        return microbes.size();
    }

    /**
     * Cleanup method - shuts down the thread pool gracefully.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}
