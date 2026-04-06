package com.biolab;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Manages the simulation logic using multithreading.
 * Handles concurrent updating of microbes and the food pellet system.
 */
public class SimulationEngine implements SimulationRuntime {
    private static final Logger LOGGER = Logger.getLogger(SimulationEngine.class.getName());

    private static final int MAX_QUEUED_COMMANDS = 4096;

    private final WorldState worldState;
    private final Environment environment;
    private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors());

    private final AtomicInteger availableReproductionSlots;
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
     * Serializes full-frame world mutation with state capture/load operations.
     * This guarantees that persistence never interleaves with worker-thread updates.
     */
    private final Object frameMutationLock = new Object();
    private final SimulationEngineContext context;
    private final WorldStatsStore worldStatsStore;
    private final WorldStatsSampleAppender worldStatsAppender;
    private volatile double foodSpawnRate = 0.75;
    private long simulationTick;
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
        this.availableReproductionSlots = new AtomicInteger(maxPopulation);

        this.executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        this.worldStatsStore = new WorldStatsStore();
        this.worldStatsAppender = new WorldStatsSampleAppender(worldStatsStore, LOGGER);
        this.context = SimulationEngineContext.create(
                MAX_QUEUED_COMMANDS,
                frameMutationLock,
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
        return debugModeService.toggle();
    }

    @Override
    public boolean isDebugModeEnabled() {
        return debugModeService.isEnabled();
    }

    /**
     * Main simulation update called every frame.
     * Uses thread pool to process microbes concurrently.
     * This method is always called from the SimulationLoop thread (single writer).
     */
    public void update() {
        SimulationFrameResult frameResult = context.updateService().runUpdate(renderSnapshot, foodSpawnRate);
        renderSnapshot = frameResult.snapshot();
        simulationTick++;
        spawnedSinceLastSample += Math.max(0, frameResult.spawnedFoodCount());
        consumedSinceLastSample += Math.max(0, frameResult.consumedFoodCount());

        if (simulationTick > 0 && simulationTick % 30L == 0L) {
            foodSpawnedPerSecond = spawnedSinceLastSample;
            foodConsumedPerSecond = consumedSinceLastSample;
            worldStatsAppender.submit(buildWorldStatsSample(System.currentTimeMillis()));
            spawnedSinceLastSample = 0;
            consumedSinceLastSample = 0;
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
        context.commandProcessor().enqueue(command);
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

    /**
     * Captures a serializable snapshot of the full simulation state.
     */
    public SimulationState captureState() {
        synchronized (frameMutationLock) {
            worldStatsAppender.flush();
            synchronized (worldState.dataLock()) {
                return context.stateCoordinator().captureState(
                        foodSpawnRate,
                        simulationTick,
                        worldStatsStore.snapshotAll()
                );
            }
        }
    }

    /**
     * Replaces the current world with a previously captured simulation state.
     */
    public void loadState(SimulationState state) {
        synchronized (frameMutationLock) {
            worldStatsAppender.flush();
            synchronized (worldState.dataLock()) {
                renderSnapshot = context.stateCoordinator().loadState(state, this::setFoodSpawnRate);
                simulationTick = Math.max(0L, state.simulationTick());
                worldStatsStore.replaceAll(state.worldStatsHistory());
                spawnedSinceLastSample = 0;
                consumedSinceLastSample = 0;
                recomputeLatestRatesFromStore();
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
            synchronized (worldState.dataLock()) {
                renderSnapshot = context.stateCoordinator().spawnMicrobe(microbe);
            }
        }
    }

    /**
     * Returns the current food spawn rate probability.
     */
    public double getFoodSpawnRate() {
        return foodSpawnRate;
    }

    @Override
    public WorldStatsStore getWorldStatsStore() {
        return worldStatsStore;
    }

    /**
     * Sets the food spawn probability per frame, clamped to [0.0, 1.0].
     * May be called from any thread (volatile write).
     */
    public void setFoodSpawnRate(double rate) {
        this.foodSpawnRate = Math.max(0.0, Math.min(1.0, rate));
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
        return new WorldStatsSample(timestampMillis, simulationTick, valuesMap);
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
