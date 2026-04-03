package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the simulation logic using multithreading.
 * Handles concurrent updating of microbes and the food pellet system.
 */
public class SimulationEngine implements SimulationRuntime {
    private static final Logger LOGGER = Logger.getLogger(SimulationEngine.class.getName());

    private static final int MAX_QUEUED_COMMANDS = 4096;

    private final List<Microbe> microbes;
    private final List<Microbe> newMicrobes;
    private final List<FoodPellet> foodPellets;
    private final Environment environment;
    private final int width;
    private final int height;
    private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors());

    private final AtomicInteger availableReproductionSlots;
    private final ExecutorService executorService;
    private static final int INITIAL_FOOD_COUNT = 1200;
    private static final int MAX_FOOD_PELLETS = 6000;
    private final SimulationCommandProcessor commandProcessor =
            new SimulationCommandProcessor(MAX_QUEUED_COMMANDS);
    private final ConcurrentHashMap<Long, Microbe> microbeById = new ConcurrentHashMap<>();
    /**
     * When {@code true}, the engine logs combat events to stdout and the canvas
     * renders debug overlays (AI target lines, vision radii, IDs).
     * Toggle at runtime via the 'D' key in {@link SimulationCanvas}.
     */
    private final AtomicBoolean debugMode = new AtomicBoolean(false);
    private static final int MAX_POPULATION = 20000;
    /**
     * Latest snapshot, published atomically (volatile pointer swap) at the end of
     * every {@code update()} call.  Readers (EDT) access it without
     * synchronisation.  The lists inside are unmodifiable defensive copies created
     * under {@code dataLock}.
     */
    private volatile SimulationSnapshot renderSnapshot;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 2;
    private static final int SHUTDOWN_NOW_TIMEOUT_SECONDS = 1;
    private static final int SPATIAL_CELL_SIZE = 30;
    private final Object dataLock = new Object();
    /**
     * Serializes full-frame world mutation with state capture/load operations.
     * This guarantees that persistence never interleaves with worker-thread updates.
     */
    private final Object frameMutationLock = new Object();
    private final SpatialGrid spatialGrid;
    private final MicrobeGrid microbeGrid;
    private final MicrobeBehaviorSystem behaviorSystem;
    private final SimulationStateCoordinator stateCoordinator;
    private volatile double foodSpawnRate = 0.75;

    // ── Lock-free render snapshot ─────────────────────────────────────────

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
        this.microbeGrid = new MicrobeGrid(width, height, SPATIAL_CELL_SIZE);
        this.behaviorSystem = new MicrobeBehaviorSystem(width, height, availableReproductionSlots, newMicrobes);
        this.stateCoordinator = new SimulationStateCoordinator(
                width,
                height,
                environment,
                debugMode,
                microbes,
                newMicrobes,
                foodPellets,
                microbeById
        );
        LOGGER.info("SimulationEngine initialized with " + THREAD_COUNT + " threads");

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < initialPopulation; i++) {
            double x = random.nextDouble() * width;
            double y = random.nextDouble() * height;
            Microbe seeded = new Microbe(x, y);
            microbes.add(seeded);
            microbeById.put(seeded.getId(), seeded);
        }

        for (int i = 0; i < INITIAL_FOOD_COUNT; i++) {
            foodPellets.add(FoodPellet.createRandom(width, height));
        }

        // Publish initial snapshot so the EDT can render before the first update()
        List<Microbe.RenderState> initialMicrobeStates = microbes.stream()
                .map(Microbe::toRenderState)
                .toList();
        renderSnapshot = new SimulationSnapshot(initialMicrobeStates, List.copyOf(foodPellets));
    }

    /**
     * Toggles debug mode atomically and returns the new state.
     */
    @Override
    public boolean toggleDebugModeFlag() {
        while (true) {
            boolean current = debugMode.get();
            boolean next = !current;
            if (debugMode.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    @Override
    public boolean isDebugModeEnabled() {
        return debugMode.get();
    }

    /**
     * Main simulation update called every frame.
     * Uses thread pool to process microbes concurrently.
     * This method is always called from the SimulationLoop thread (single writer).
     */
    public void update() {
        synchronized (frameMutationLock) {
            if (executorService.isShutdown()) return;

            // Apply queued UI-originated commands on the simulation thread.
            processPendingCommands();

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
            if (microbeCount == 0) {
                synchronized (dataLock) {
                    foodPellets.removeIf(FoodPellet::isConsumed);
                    renderSnapshot = new SimulationSnapshot(List.of(), List.copyOf(foodPellets));
                }
                return;
            }

        // Rebuild spatial grid for O(1) food lookup
            spatialGrid.rebuild(foodSnapshot);
        // Rebuild microbe spatial index for O(1) neighbor lookup
            microbeGrid.rebuild(snapshot);

            int chunkSize = Math.max(1, microbeCount / THREAD_COUNT);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < microbeCount; i += chunkSize) {
                final int start = i;
                final int end = Math.min(i + chunkSize, microbeCount);
                Future<?> future = executorService.submit(() ->
                        behaviorSystem.processChunk(snapshot, spatialGrid, microbeGrid, start, end, temp, tox));
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
            microbeById.entrySet().removeIf(e -> e.getValue().isDead());
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
                for (Microbe newborn : newbornsCopy) {
                    microbeById.put(newborn.getId(), newborn);
                }
            } else {
                int allowedNewborns = Math.max(0, MAX_POPULATION - currentPopulation);
                for (int i = 0; i < allowedNewborns && i < newbornCount; i++) {
                    Microbe newborn = newbornsCopy.get(i);
                    microbes.add(newborn);
                    microbeById.put(newborn.getId(), newborn);
                }
            }

            // Publish an immutable snapshot for lock-free EDT reading.
            // Both lists are unmodifiable copies created while holding dataLock,
            // guaranteeing happens-before visibility via the volatile write.
                renderSnapshot = new SimulationSnapshot(
                        microbes.stream().map(Microbe::toRenderState).toList(),
                        List.copyOf(foodPellets));
            }
        }
    }

    /**
     * Returns the latest immutable render snapshot for lock-free reading.
     * Contains unmodifiable lists of microbes and food pellets.
     * The snapshot is published atomically (volatile) at the end of each {@code update()}.
     */
    public SimulationSnapshot getRenderSnapshot() {
        return renderSnapshot;
    }

    @Override
    public void enqueueCommand(SimulationCommand command) {
        commandProcessor.enqueue(command);
    }

    private void processPendingCommands() {
        commandProcessor.processPending(this, LOGGER);
    }

    /**
     * Returns the spatial cell size (world units).
     * Used by the debug renderer as the approximate vision / aggro radius.
     */
    public static int getSpatialCellSize() {
        return SPATIAL_CELL_SIZE;
    }

    /**
     * Returns a living child of the given microbe (one whose {@code parentId} matches),
     * or {@code null} if none exists. Used for auto-selection after a microbe dies.
     */
    public Microbe findLivingChild(long parentId) {
        synchronized (dataLock) {
            for (Microbe m : microbes) {
                if (!m.isDead() && m.getParentId() == parentId) return m;
            }
        }
        return null;
    }

    /**
     * Returns a random living microbe from the current population,
     * or {@code null} if the population is empty.
     */
    public Microbe findRandomLivingMicrobe() {
        synchronized (dataLock) {
            if (microbes.isEmpty()) return null;
            int idx = ThreadLocalRandom.current().nextInt(microbes.size());
            return microbes.get(idx);
        }
    }


    /**
     * Returns the microbe instance with the given ID, or {@code null} if not found.
     * Used by EDT hit-testing that runs against immutable render snapshots.
     */
    public Microbe findMicrobeById(long id) {
        return microbeById.get(id);
    }

    /**
     * Returns the current population count. Thread-safe.
     */
    public int getPopulationCount() {
        return renderSnapshot.microbes().size();
    }

    /**
     * Captures a serializable snapshot of the full simulation state.
     */
    public SimulationState captureState() {
        synchronized (frameMutationLock) {
            synchronized (dataLock) {
                return stateCoordinator.captureState(foodSpawnRate);
            }
        }
    }

    /**
     * Replaces the current world with a previously captured simulation state.
     */
    public void loadState(SimulationState state) {
        synchronized (frameMutationLock) {
            synchronized (dataLock) {
                renderSnapshot = stateCoordinator.loadState(state, this::setFoodSpawnRate);
            }
        }
    }

    /**
     * Returns the shared {@link Environment} object (thread-safe via internal synchronisation).
     */
    public Environment getEnvironment() {
        return environment;
    }

    /**
     * Directly adds a microbe to the simulation.
     * Intended for sandbox / debug use only – bypasses population caps.
     *
     * @param microbe the microbe to inject
     */
    public void spawnMicrobe(Microbe microbe) {
        synchronized (frameMutationLock) {
            synchronized (dataLock) {
                renderSnapshot = stateCoordinator.spawnMicrobe(microbe);
            }
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
