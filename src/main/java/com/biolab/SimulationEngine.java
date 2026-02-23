package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors());

    private final AtomicInteger availableReproductionSlots;
    private final ExecutorService executorService;
    private static final int INITIAL_FOOD_COUNT = 200;
    private static final int MAX_FOOD_PELLETS = 1000;
    private static final int MAX_POPULATION = 20000;
    private static final int MAX_REPRODUCTION_ATTEMPTS = 5;
    private static final int MIN_RETRIES_BEFORE_BACKOFF = 2;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 2;
    private static final int SHUTDOWN_NOW_TIMEOUT_SECONDS = 1;
    private static final int SPATIAL_CELL_SIZE = 30;
    private final Object dataLock = new Object();
    private final SpatialGrid spatialGrid;
    private volatile double foodSpawnRate = 0.3;

    /**
     * Creates and initialises the simulation engine.
     *
     * @param width             width of the world in world units
     * @param height            height of the world in world units
     * @param initialPopulation number of microbes to seed at startup (must be &gt;= 0)
     * @throws IllegalArgumentException if dimensions are non-positive or population is negative
     */
    public SimulationEngine(int width, int height, int initialPopulation) {
        if (initialPopulation < 0) {
            throw new IllegalArgumentException("initialPopulation must be >= 0, was: " + initialPopulation);
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("World dimensions must be positive, was: " + width + "x" + height);
        }

        this.width = width;
        this.height = height;
        this.microbes = new ArrayList<>();
        this.newMicrobes = new ArrayList<>();
        this.foodPellets = new ArrayList<>();
        this.environment = new Environment();
        this.availableReproductionSlots = new AtomicInteger(MAX_POPULATION);

        this.executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        this.spatialGrid = new SpatialGrid(width, height, SPATIAL_CELL_SIZE);
        LOGGER.info("SimulationEngine initialized with " + THREAD_COUNT + " threads");

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < initialPopulation; i++) {
            double x = random.nextDouble() * width;
            double y = random.nextDouble() * height;
            microbes.add(new Microbe(x, y));
        }

        for (int i = 0; i < INITIAL_FOOD_COUNT; i++) {
            foodPellets.add(FoodPellet.createRandom(width, height));
        }
    }

    /**
     * Main simulation update called every frame.
     * Uses thread pool to process microbes concurrently.
     * This method is always called from the SimulationLoop thread (single writer).
     */
    public void update() {
        if (executorService.isShutdown()) return;

        // Get current environmental conditions (thread-safe)
        final double temp = environment.getTemperature();
        final double tox = environment.getToxicity();

        final List<Microbe> snapshot;
        final List<FoodPellet> foodSnapshot;

        // Lock only for reading/modifying the shared lists
        synchronized (dataLock) {
            // Calculate available slots for reproduction this frame
            int currentPop = microbes.size();
            int availableSlots = Math.max(0, MAX_POPULATION - currentPop);
            availableReproductionSlots.set(availableSlots);

            // Food spawning
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (random.nextDouble() < foodSpawnRate && foodPellets.size() < MAX_FOOD_PELLETS) {
                foodPellets.add(FoodPellet.createRandom(width, height));
            }

            // Create snapshots for safe chunk-based parallel processing
            snapshot = new ArrayList<>(microbes);
            foodSnapshot = new ArrayList<>(foodPellets);
        }

        final int microbeCount = snapshot.size();
        if (microbeCount == 0) return;

        // Rebuild spatial grid for O(1) food lookup
        spatialGrid.rebuild(foodSnapshot);

        int chunkSize = Math.max(1, microbeCount / THREAD_COUNT);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < microbeCount; i += chunkSize) {
            final int start = i;
            final int end = Math.min(i + chunkSize, microbeCount);
            Future<?> future = executorService.submit(() -> processMicrobeChunk(snapshot, spatialGrid, start, end, temp, tox));
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

        // Lock for list modifications (remove dead, add newborns)
        synchronized (dataLock) {
            microbes.removeIf(Microbe::isDead);
            foodPellets.removeIf(FoodPellet::isConsumed);

            // Add newborns within population limit
            int currentPopulation = microbes.size();
            List<Microbe> newbornsCopy;
            synchronized (newMicrobes) {
                newbornsCopy = new ArrayList<>(newMicrobes);
                newMicrobes.clear();
            }
            int newbornCount = newbornsCopy.size();

            if (currentPopulation + newbornCount <= MAX_POPULATION) {
                microbes.addAll(newbornsCopy);
            } else {
                int allowedNewborns = Math.max(0, MAX_POPULATION - currentPopulation);
                for (int i = 0; i < allowedNewborns && i < newbornCount; i++) {
                    microbes.add(newbornsCopy.get(i));
                }
            }
        }
    }

    /**
     * Processes a chunk of microbes concurrently in a worker thread.
     */
    private void processMicrobeChunk(List<Microbe> snapshot, SpatialGrid foodGrid,
                                     int start, int end, double temperature, double toxicity) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = start; i < end; i++) {
            Microbe microbe = snapshot.get(i);

            // Update position
            microbe.move(width, height);

            // Update health based on environment (natural selection!)
            microbe.updateHealth(temperature, toxicity);

            // Food collision detection – only check nearby cells via spatial grid
            for (FoodPellet food : foodGrid.getNearbyFood(microbe.getX(), microbe.getY())) {
                if (food.checkCollision(microbe)) {
                    double energyGain = food.consume();
                    if (energyGain > 0) {
                        microbe.eat(energyGain);
                    }
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
                        synchronized (newMicrobes) {
                            newMicrobes.add(child);
                        }
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

    /**
     * Returns a defensive copy of the microbe list for safe rendering on the EDT.
     * Uses dataLock to prevent reading while update() is modifying the list.
     */
    public List<Microbe> getMicrobes() {
        synchronized (dataLock) {
            return new ArrayList<>(microbes);
        }
    }

    /**
     * Returns a defensive copy of the food pellet list for safe rendering on the EDT.
     */
    public List<FoodPellet> getFoodPellets() {
        synchronized (dataLock) {
            return new ArrayList<>(foodPellets);
        }
    }

    /**
     * Returns the shared {@link Environment} object (thread-safe via internal synchronisation).
     */
    public Environment getEnvironment() {
        return environment;
    }

    /**
     * Returns the current population count. Thread-safe.
     */
    public int getPopulationCount() {
        synchronized (dataLock) {
            return microbes.size();
        }
    }

    /**
     * Returns the current food spawn rate probability.
     */
    public double getFoodSpawnRate() {
        return foodSpawnRate;
    }

    /**
     * Sets the food spawn probability per frame, clamped to [0.0, 1.0].
     * May be called from any thread (volatile write).
     */
    public void setFoodSpawnRate(double rate) {
        this.foodSpawnRate = Math.max(0.0, Math.min(1.0, rate));
    }

    /**
     * Returns true if the engine is still running (not yet shut down).
     */
    public boolean isRunning() {
        return !executorService.isShutdown();
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
