package com.biolab;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Manages the simulation logic using multithreading.
 * Handles concurrent updating of microbes and the food pellet system.
 */
public class SimulationEngine implements SimulationRuntime {
    private static final Logger LOGGER = Logger.getLogger(SimulationEngine.class.getName());

    private static final int MAX_QUEUED_COMMANDS = 4096;
    private static final long STATS_SAMPLE_INTERVAL_TICKS = 30L;

    private final WorldState worldState;
    private final Environment environment;
    private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors());

    private final ExecutorService executorService;
    private static final int INITIAL_FOOD_COUNT = 1200;
    private static final int MAX_FOOD_PELLETS = 6000;
    /**
     * When {@code true}, the engine logs combat events to stdout and the canvas
     * renders debug overlays (AI target lines, vision radii, IDs).
     * Toggle at runtime via the 'D' key in {@link SimulationCanvas}.
     */
    private final DebugModeService debugModeService = new DebugModeService();
    private static final int DEFAULT_MAX_POPULATION = 20000;
    /**
     * Latest snapshot, published atomically (volatile pointer swap) at the end of
     * every {@code update()} call.  Readers (EDT) access it without
     * synchronisation.  The lists inside are unmodifiable defensive copies created
     * under {@code dataLock}.
     */
    private volatile SimulationSnapshot renderSnapshot;
    private static final int SPATIAL_CELL_SIZE = 30;
    /**
     * Coordinates frame execution with exclusive state operations (capture/load/spawn).
     * This guarantees that persistence never interleaves with worker-thread updates.
     */
    private final FrameMutationCoordinator frameMutationCoordinator = new FrameMutationCoordinator();
    private final SimulationEngineContext context;
    private final WorldStatsStore worldStatsStore;
    private final WorldStatsSampleAppender worldStatsAppender;
    /**
     * Desired average food spawn amount per simulation tick.
     * Fractional values are supported (e.g. 0.75 means 3 pellets every 4 ticks on average).
     */
    private volatile double foodSpawnRate = 0.75;
    private final AtomicLong simulationTick = new AtomicLong();
    private double foodSpawnedPerSecond;
    private double foodConsumedPerSecond;
    private int spawnedSinceLastSample;
    private int consumedSinceLastSample;

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
        this(width, height, initialPopulation, DEFAULT_MAX_POPULATION);
    }

    /**
     * Creates and initialises the simulation engine with a configurable population cap.
     */
    public SimulationEngine(int width, int height, int initialPopulation, int maxPopulation) {
        if (initialPopulation < 0) {
            throw new IllegalArgumentException("initialPopulation must be >= 0, was: " + initialPopulation);
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("World dimensions must be positive, was: " + width + "x" + height);
        }
        if (maxPopulation <= 0 || maxPopulation < initialPopulation) {
            throw new IllegalArgumentException("maxPopulation must be >= initialPopulation and > 0, was: " + maxPopulation);
        }

        this.worldState = new WorldState();
        this.environment = new Environment();
        AtomicInteger availableReproductionSlots = new AtomicInteger(maxPopulation);

        this.executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        this.worldStatsStore = new WorldStatsStore();
        this.worldStatsAppender = new WorldStatsSampleAppender(worldStatsStore, LOGGER);
        this.context = SimulationEngineContext.create(
                MAX_QUEUED_COMMANDS,
                frameMutationCoordinator,
                executorService,
                environment,
                debugModeService,
                worldState,
                availableReproductionSlots,
                maxPopulation,
                THREAD_COUNT,
                width,
                height,
                SPATIAL_CELL_SIZE,
                MAX_FOOD_PELLETS,
                this,
                LOGGER
        );
        LOGGER.info("SimulationEngine initialized with " + THREAD_COUNT + " threads");

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Microbe> microbes = worldState.population().microbes();
        ConcurrentHashMap<Long, Microbe> microbeById = worldState.index().byId();
        for (int i = 0; i < initialPopulation; i++) {
            double x = random.nextDouble() * width;
            double y = random.nextDouble() * height;
            Microbe seeded = new Microbe(x, y);
            microbes.add(seeded);
            microbeById.put(seeded.getId(), seeded);
        }

        List<FoodPellet> foodPellets = worldState.food().pellets();
        for (int i = 0; i < INITIAL_FOOD_COUNT; i++) {
            foodPellets.add(FoodPellet.createRandom(width, height));
        }

        // Publish initial snapshot so the EDT can render before the first update()
        List<Microbe.RenderState> initialMicrobeStates = new java.util.ArrayList<>(microbes.size());
        for (Microbe microbe : microbes) {
            initialMicrobeStates.add(microbe.toRenderState());
        }
        renderSnapshot = new SimulationSnapshot(initialMicrobeStates, List.copyOf(foodPellets));
    }

    /**
     * Toggles debug mode atomically and returns the new state.
     */
    @Override
    public boolean toggleDebugModeFlag() {
        return debugModeService.toggle();
    }

    @Override
    public boolean isDebugModeEnabled() {
        return debugModeService.isEnabled();
    }

    @Override
    public long getSimulationTick() {
        return simulationTick.get();
    }

    @Override
    public int getDebugVisionRadius() {
        return SPATIAL_CELL_SIZE;
    }

    private static double perTickAverage(int countOverSampleWindow) {
        return Math.max(0.0, countOverSampleWindow / 30.0);
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
        context.commandProcessor().enqueue(command);
    }

    /**
     * Returns a living child of the given microbe (one whose {@code parentId} matches),
     * or {@code null} if none exists. Used for auto-selection after a microbe dies.
     */
    public Microbe findLivingChild(long parentId) {
        return context.microbeLookupService().findLivingChild(parentId);
    }

    /**
     * Returns a random living microbe from the current population,
     * or {@code null} if the population is empty.
     */
    public Microbe findRandomLivingMicrobe() {
        return context.microbeLookupService().findRandomLivingMicrobe();
    }


    /**
     * Returns the microbe instance with the given ID, or {@code null} if not found.
     * Used by EDT hit-testing that runs against immutable render snapshots.
     */
    public Microbe findMicrobeById(long id) {
        return worldState.index().byId().get(id);
    }

    /**
     * Returns the current population count. Thread-safe.
     */
    public int getPopulationCount() {
        return renderSnapshot.microbes().size();
    }

    private static boolean isStatsSampleTick(long tick) {
        return tick > 0L && tick % STATS_SAMPLE_INTERVAL_TICKS == 0L;
    }

    /**
     * Captures a serializable snapshot of the full simulation state.
     */
    public SimulationState captureState() {
        return frameMutationCoordinator.runExclusive(() -> {
            ensureWorldStatsFlushed("captureState");
            synchronized (worldState.dataLock()) {
                return context.stateCoordinator().captureState(
                        foodSpawnRate,
                        simulationTick.get(),
                        worldStatsStore.snapshotAll()
                );
            }
        });
    }

    /**
     * Returns the shared {@link Environment} object (thread-safe via internal synchronisation).
     */
    public Environment getEnvironment() {
        return environment;
    }

    /**
     * Replaces the current world with a previously captured simulation state.
     */
    public void loadState(SimulationState state) {
        frameMutationCoordinator.runExclusive(() -> {
            ensureWorldStatsFlushed("loadState");
            synchronized (worldState.dataLock()) {
                renderSnapshot = context.stateCoordinator().loadState(state, this::setFoodSpawnRate);
                simulationTick.set(Math.max(0L, state.simulationTick()));
                worldStatsStore.replaceAll(state.worldStatsHistory());
                worldStatsStore.backfillDerivedTraitMetrics();
                spawnedSinceLastSample = 0;
                consumedSinceLastSample = 0;
                recomputeLatestRatesFromStore();
            }
        });
    }

    /**
     * Directly adds a microbe to the simulation.
     * Intended for sandbox / debug use only – bypasses population caps.
     *
     * @param microbe the microbe to inject
     */
    public void spawnMicrobe(Microbe microbe) {
        frameMutationCoordinator.runExclusive(() -> {
            synchronized (worldState.dataLock()) {
                renderSnapshot = context.stateCoordinator().spawnMicrobe(microbe);
            }
        });
    }

    @Override
    public void spawnFood(FoodPellet foodPellet) {
        frameMutationCoordinator.runExclusive(() -> {
            synchronized (worldState.dataLock()) {
                renderSnapshot = context.stateCoordinator().spawnFood(foodPellet);
            }
        });
    }

    /**
     * Main simulation update called every frame.
     * Uses thread pool to process microbes concurrently.
     * This method is always called from the SimulationLoop thread (single writer).
     */
    public void update() {
        SimulationFrameResult frameResult = context.updateService().runUpdate(renderSnapshot, foodSpawnRate);
        renderSnapshot = frameResult.snapshot();
        long nextTick = simulationTick.incrementAndGet();
        spawnedSinceLastSample += Math.max(0, frameResult.spawnedFoodCount());
        consumedSinceLastSample += Math.max(0, frameResult.consumedFoodCount());

        if (isStatsSampleTick(nextTick)) {
            // Keep variable names for binary compatibility; values are now stored as averages per tick.
            foodSpawnedPerSecond = perTickAverage(spawnedSinceLastSample);
            foodConsumedPerSecond = perTickAverage(consumedSinceLastSample);
            worldStatsAppender.submit(buildWorldStatsSample(System.currentTimeMillis()));
            spawnedSinceLastSample = 0;
            consumedSinceLastSample = 0;
        }
    }

    private void ensureWorldStatsFlushed(String operationName) {
        if (!worldStatsAppender.flush()) {
            throw new IllegalStateException("Timed out while flushing world stats before " + operationName);
        }
    }

    @Override
    public WorldStatsStore getWorldStatsStore() {
        return worldStatsStore;
    }

    /**
     * Returns the configured food spawn amount per tick (fractional allowed).
     */
    public double getFoodSpawnRate() {
        return foodSpawnRate;
    }

    private WorldStatsSample buildWorldStatsSample(long timestampMillis) {
        WorldMetricContext contextSnapshot;
        synchronized (worldState.dataLock()) {
            List<Microbe> microbes = worldState.population().microbes();
            int population = microbes.size();

            double heatSum = 0.0;
            double toxinSum = 0.0;
            double speedSum = 0.0;
            double dietSum = 0.0;
            double strengthSum = 0.0;
            double defenseSum = 0.0;
            double ageSum = 0.0;
            double energyPercentSum = 0.0;
            double healthPercentSum = 0.0;
            double energyAbsoluteSum = 0.0;
            double healthAbsoluteSum = 0.0;

            for (Microbe microbe : microbes) {
                heatSum += microbe.getHeatResistance();
                toxinSum += microbe.getToxinResistance();
                speedSum += microbe.getSpeed();
                dietSum += microbe.getDiet();
                strengthSum += microbe.getStrengthTrait();
                defenseSum += microbe.getDefenseTrait();
                ageSum += microbe.getAge();
                energyPercentSum += microbe.getEnergyRatio() * 100.0;
                healthPercentSum += microbe.getHealthRatio() * 100.0;
                energyAbsoluteSum += microbe.getEnergy();
                healthAbsoluteSum += microbe.getHealth();
            }

            double divisor = Math.max(1, population);
            contextSnapshot = new WorldMetricContext(
                    population,
                    worldState.food().pellets().size(),
                    foodSpawnedPerSecond,
                    foodConsumedPerSecond,
                    environment.getTemperature(),
                    environment.getToxicity(),
                    foodSpawnRate,
                    heatSum / divisor,
                    toxinSum / divisor,
                    speedSum / divisor,
                    dietSum / divisor,
                    strengthSum / divisor,
                    defenseSum / divisor,
                    ageSum / divisor,
                    energyPercentSum / divisor,
                    healthPercentSum / divisor,
                    energyAbsoluteSum / divisor,
                    healthAbsoluteSum / divisor
            );
        }

        java.util.EnumMap<WorldMetricId, Double> valuesMap = new java.util.EnumMap<>(WorldMetricId.class);
        for (WorldMetricDefinition definition : WorldMetricRegistry.definitions()) {
            double value = definition.extractor().applyAsDouble(contextSnapshot);
            valuesMap.put(definition.id(), value);
        }
        return new WorldStatsSample(timestampMillis, simulationTick.get(), valuesMap);
    }

    private void recomputeLatestRatesFromStore() {
        if (worldStatsStore.size() == 0) {
            foodSpawnedPerSecond = 0.0;
            foodConsumedPerSecond = 0.0;
            return;
        }
        List<WorldStatsSample> last = worldStatsStore.queryRangeByTick(
                java.util.EnumSet.of(WorldMetricId.FOOD_SPAWNED_PER_SEC, WorldMetricId.FOOD_CONSUMED_PER_SEC),
                worldStatsStore.lastTick(),
                worldStatsStore.lastTick(),
                1
        );
        if (last.isEmpty()) {
            foodSpawnedPerSecond = 0.0;
            foodConsumedPerSecond = 0.0;
            return;
        }
        WorldStatsSample sample = last.get(0);
        foodSpawnedPerSecond = sample.metricValues().getOrDefault(WorldMetricId.FOOD_SPAWNED_PER_SEC, 0.0);
        foodConsumedPerSecond = sample.metricValues().getOrDefault(WorldMetricId.FOOD_CONSUMED_PER_SEC, 0.0);
    }


    /**
     * Sets the target food spawn amount per tick.
     * May be called from any thread (volatile write).
     */
    public void setFoodSpawnRate(double rate) {
        double sanitized = Double.isFinite(rate) ? rate : 0.0;
        this.foodSpawnRate = Math.max(0.0, sanitized);
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
        worldStatsAppender.shutdown();
        context.lifecycleService().shutdown();
    }
}
