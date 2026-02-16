package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Manages the simulation logic using multithreading.
 * Handles concurrent updating of microbes and the food pellet system.
 */
public class SimulationEngine {
    private static final Logger LOGGER = Logger.getLogger(SimulationEngine.class.getName());

    private final List<Microbe> microbes;
    private final List<Microbe> newMicrobes;
    private final List<FoodPellet> foodPellets;
    private final Environment environment;
    private final int width;
    private final int height;
    private double foodSpawnRate = 0.3;

    private final AtomicInteger availableReproductionSlots;
    private final ExecutorService executorService;

    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int MAX_POPULATION = 20000;
    private static final int MAX_REPRODUCTION_ATTEMPTS = 5;
    private static final int MIN_RETRIES_BEFORE_BACKOFF = 2;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 2;
    private static final int SHUTDOWN_NOW_TIMEOUT_SECONDS = 1;

    public SimulationEngine(int width, int height, int initialPopulation) {
        this.width = width;
        this.height = height;
        this.microbes = new CopyOnWriteArrayList<>();
        this.newMicrobes = new CopyOnWriteArrayList<>();
        this.foodPellets = new CopyOnWriteArrayList<>();
        this.environment = new Environment();
        this.availableReproductionSlots = new AtomicInteger(MAX_POPULATION);

        this.executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        LOGGER.info("SimulationEngine initialized with " + THREAD_COUNT + " threads");

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < initialPopulation; i++) {
            double x = random.nextDouble() * width;
            double y = random.nextDouble() * height;
            microbes.add(new Microbe(x, y));
        }

        for (int i = 0; i < 200; i++) {
            foodPellets.add(FoodPellet.createRandom(width, height));
        }
    }

    /**
     * Main simulation update called every frame.
     * Uses thread pool to process microbes concurrently.
     */
    public void update() {
        // Get current environmental conditions (thread-safe)
        final double temp = environment.getTemperature();
        final double tox = environment.getToxicity();

        // Calculate available slots for reproduction this frame (prevents exceeding MAX_POPULATION)
        int currentPop = microbes.size();
        int availableSlots = Math.max(0, MAX_POPULATION - currentPop);
        availableReproductionSlots.set(availableSlots);

        // ===== FOOD SPAWNING =====
        // Spawn food based on spawn rate
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() < foodSpawnRate && foodPellets.size() < 1000) {
            foodPellets.add(FoodPellet.createRandom(width, height));
        }

        // Process microbes in parallel using thread pool
        final int microbeCount = microbes.size();
        if (microbeCount == 0) return;

        int chunkSize = Math.max(1, microbeCount / THREAD_COUNT);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < microbeCount; i += chunkSize) {
            final int start = i;
            final int end = Math.min(i + chunkSize, microbeCount);
            Future<?> future = executorService.submit(() -> processMicrobeChunk(start, end, temp, tox));
            futures.add(future);
        }

        // Wait for all worker threads to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "Simulation thread interrupted during processing", e);
                return;
            } catch (ExecutionException e) {
                LOGGER.log(Level.SEVERE, "Error during microbe chunk processing", e.getCause());
            }
        }

        microbes.removeIf(Microbe::isDead);
        foodPellets.removeIf(FoodPellet::isConsumed);

        // Add newborns within population limit
        int currentPopulation = microbes.size();
        int newbornCount = newMicrobes.size();

        if (currentPopulation + newbornCount <= MAX_POPULATION) {
            microbes.addAll(newMicrobes);
        } else {
            int allowedNewborns = Math.max(0, MAX_POPULATION - currentPopulation);
            for (int i = 0; i < allowedNewborns && i < newbornCount; i++) {
                microbes.add(newMicrobes.get(i));
            }
        }

        newMicrobes.clear();
    }

    /**
     * Processes a chunk of microbes concurrently in a worker thread.
     */
    private void processMicrobeChunk(int start, int end, double temperature, double toxicity) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Add a live size check to avoid IndexOutOfBoundsException if the List shrinks concurrently
        for (int i = start; i < end && i < microbes.size(); i++) {
            // CopyOnWriteArrayList guarantees non-null elements
            Microbe microbe = microbes.get(i);

            // Update position
            microbe.move(width, height);

            // Update health based on environment (natural selection!)
            microbe.updateHealth(temperature, toxicity);

            // ===== FOOD COLLISION DETECTION =====
            // Check if microbe is near any food
            for (FoodPellet food : foodPellets) {
                if (food.checkCollision(microbe)) {
                    double energyGain = food.consume();
                    microbe.eat(energyGain);
                    break;
                }
            }

            // Attempt reproduction with atomic slot claiming
            if (microbe.canReproduce()) {
                int retryCount = 0;
                while (retryCount < MAX_REPRODUCTION_ATTEMPTS) {
                    int currentSlots = availableReproductionSlots.get();
                    if (currentSlots <= 0) {
                        break;
                    }

                    if (availableReproductionSlots.compareAndSet(currentSlots, currentSlots - 1)) {
                        double offsetX = (random.nextDouble() - 0.5) * 20;
                        double offsetY = (random.nextDouble() - 0.5) * 20;
                        Microbe child = new Microbe(
                            microbe,
                            microbe.getX() + offsetX,
                            microbe.getY() + offsetY
                        );
                        newMicrobes.add(child);
                        microbe.resetReproduction();
                        break;
                    }

                    retryCount++;
                    if (retryCount > MIN_RETRIES_BEFORE_BACKOFF) {
                        Thread.yield();
                    }
                }
            }
        }
    }

    public List<Microbe> getMicrobes() {
        synchronized (microbes) {
            return new ArrayList<>(microbes);
        }
    }

    public List<FoodPellet> getFoodPellets() {
        synchronized (foodPellets) {
            return new ArrayList<>(foodPellets);
        }
    }

    public Environment getEnvironment() {
        return environment;
    }

    public int getPopulationCount() {
        return microbes.size();
    }

    public int getFoodCount() {
        return foodPellets.size();
    }

    public void setFoodSpawnRate(double rate) {
        this.foodSpawnRate = Math.max(0.0, Math.min(1.0, rate));
    }

    public double getFoodSpawnRate() {
        return foodSpawnRate;
    }

    /**
     * Shuts down the thread pool gracefully.
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
