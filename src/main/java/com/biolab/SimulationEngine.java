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
public class SimulationEngine implements SimulationRuntime {
    private static final Logger LOGGER = Logger.getLogger(SimulationEngine.class.getName());

    /**
     * When {@code true}, the engine logs combat events to stdout and the canvas
     * renders debug overlays (AI target lines, vision radii, IDs).
     * Toggle at runtime via the 'D' key in {@link SimulationCanvas}.
     */
    public static volatile boolean DEBUG_MODE = false;

    private static final double COMBAT_DAMAGE = 7.0;

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
    private final ConcurrentLinkedQueue<SimulationCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private static final int MAX_POPULATION = 20000;
    private static final int MAX_REPRODUCTION_ATTEMPTS = 5;
    private static final int MIN_RETRIES_BEFORE_BACKOFF = 2;
    private static final double HUNT_STEER_STRENGTH = 0.12;
    private static final double FLEE_STEER_STRENGTH = 0.18;
    private static final double MAX_STEER_DELTA = 1.2;
    private static final long ATTACK_COOLDOWN_MS = 300;
    /**
     * Latest snapshot, published atomically (volatile pointer swap) at the end of
     * every {@code update()} call.  Readers (EDT) access it without
     * synchronisation.  The lists inside are unmodifiable defensive copies created
     * under {@code dataLock}.
     */
    private volatile RenderSnapshot renderSnapshot;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 2;
    private static final int SHUTDOWN_NOW_TIMEOUT_SECONDS = 1;
    private static final int SPATIAL_CELL_SIZE = 30;
    private final Object dataLock = new Object();
    private final SpatialGrid spatialGrid;
    private final MicrobeGrid microbeGrid;
    private volatile double foodSpawnRate = 0.75;

    // ── Lock-free render snapshot ─────────────────────────────────────────

    /**
     * Toggles debug mode atomically and returns the new state.
     */
    public static synchronized boolean toggleDebugMode() {
        DEBUG_MODE = !DEBUG_MODE;
        return DEBUG_MODE;
    }

    @Override
    public boolean toggleDebugModeFlag() {
        return toggleDebugMode();
    }

    @Override
    public boolean isDebugModeEnabled() {
        return DEBUG_MODE;
    }

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

        // Publish initial snapshot so the EDT can render before the first update()
        List<Microbe.RenderState> initialMicrobeStates = microbes.stream()
                .map(Microbe::toRenderState)
                .toList();
        renderSnapshot = new RenderSnapshot(initialMicrobeStates, List.copyOf(foodPellets));
    }

    /**
     * Main simulation update called every frame.
     * Uses thread pool to process microbes concurrently.
     * This method is always called from the SimulationLoop thread (single writer).
     */
    public void update() {
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
                renderSnapshot = new RenderSnapshot(List.of(), List.copyOf(foodPellets));
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
            Future<?> future = executorService.submit(() -> processMicrobeChunk(snapshot, spatialGrid, microbeGrid, start, end, temp, tox));
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

            // Publish an immutable snapshot for lock-free EDT reading.
            // Both lists are unmodifiable copies created while holding dataLock,
            // guaranteeing happens-before visibility via the volatile write.
            renderSnapshot = new RenderSnapshot(
                    microbes.stream().map(Microbe::toRenderState).toList(),
                    List.copyOf(foodPellets));
        }
    }

    /**
     * Returns the latest immutable render snapshot for lock-free reading.
     * Contains unmodifiable lists of microbes and food pellets.
     * The snapshot is published atomically (volatile) at the end of each {@code update()}.
     */
    public RenderSnapshot getRenderSnapshot() {
        return renderSnapshot;
    }

    @Override
    public void enqueueCommand(SimulationCommand command) {
        if (command == null) return;
        commandQueue.offer(command);
    }

    private void processPendingCommands() {
        SimulationCommand command;
        while ((command = commandQueue.poll()) != null) {
            try {
                command.apply(this);
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING, "Failed to execute simulation command", ex);
            }
        }
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
     * Returns a defensive copy of current microbe entities.
     * Intended for low-frequency operations (e.g. resolving hit-test IDs).
     */
    public List<Microbe> getMicrobes() {
        synchronized (dataLock) {
            return List.copyOf(microbes);
        }
    }

    /**
     * Returns the microbe instance with the given ID, or {@code null} if not found.
     * Used by EDT hit-testing that runs against immutable render snapshots.
     */
    public Microbe findMicrobeById(long id) {
        synchronized (dataLock) {
            for (Microbe microbe : microbes) {
                if (microbe.getId() == id) return microbe;
            }
        }
        return null;
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
        synchronized (dataLock) {
            List<Microbe.PersistedState> microbesState = microbes.stream()
                    .map(Microbe::toPersistedState)
                    .toList();
            List<SimulationState.FoodState> foodState = foodPellets.stream()
                    .filter(food -> !food.isConsumed())
                    .map(food -> new SimulationState.FoodState(food.getX(), food.getY()))
                    .toList();
            return new SimulationState(
                    width,
                    height,
                    environment.getTemperature(),
                    environment.getToxicity(),
                    foodSpawnRate,
                    microbesState,
                    foodState,
                    DEBUG_MODE
            );
        }
    }

    /**
     * Replaces the current world with a previously captured simulation state.
     */
    public void loadState(SimulationState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (state.worldWidth() != width || state.worldHeight() != height) {
            throw new IllegalArgumentException("State dimensions "
                    + state.worldWidth() + "x" + state.worldHeight()
                    + " do not match runtime world " + width + "x" + height);
        }
        synchronized (dataLock) {
            microbes.clear();
            foodPellets.clear();
            synchronized (newMicrobes) {
                newMicrobes.clear();
            }

            for (Microbe.PersistedState m : state.microbes()) {
                microbes.add(Microbe.fromPersistedState(m));
            }
            for (SimulationState.FoodState f : state.food()) {
                foodPellets.add(new FoodPellet(f.x(), f.y()));
            }

            environment.setTemperature(state.temperature());
            environment.setToxicity(state.toxicity());
            setFoodSpawnRate(state.foodSpawnRate());
            DEBUG_MODE = state.debugMode();

            renderSnapshot = new RenderSnapshot(
                    microbes.stream().map(Microbe::toRenderState).toList(),
                    List.copyOf(foodPellets)
            );
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
        synchronized (dataLock) {
            microbes.add(microbe);
            renderSnapshot = new RenderSnapshot(
                    microbes.stream().map(Microbe::toRenderState).toList(),
                    List.copyOf(foodPellets));
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Processes a chunk of microbes concurrently in a worker thread.
     *
     * <h3>Thread-safety notes</h3>
     * <ul>
     *   <li>Each microbe in [start,end) is <em>owned</em> by this thread for the
     *       duration of the frame (chunk partitioning guarantees no two threads
     *       write the same microbe's movement/age state).</li>
     *   <li>Combat writes that cross chunk boundaries (damage, knockback, energy
     *       transfer) are serialised via {@code stateLock} inside the Microbe
     *       methods, so they are safe even when attacker and victim live in
     *       different chunks.</li>
     *   <li>{@code microbeGrid} and {@code foodGrid} are read-only during this
     *       phase; they were fully built before any worker thread was submitted.</li>
     * </ul>
     */
    private void processMicrobeChunk(List<Microbe> snapshot, SpatialGrid foodGrid,
                                     MicrobeGrid microbeGrid,
                                     int start, int end, double temperature, double toxicity) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = start; i < end; i++) {
            Microbe microbe = snapshot.get(i);
            if (microbe.isDead()) continue;

            // ── 1. Movement ───────────────────────────────────────────────
            microbe.move(width, height);

            // ── 2. Environmental damage (natural selection) ───────────────
            microbe.updateHealth(temperature, toxicity);

            // ── 3. Predator / Prey interaction ────────────────────────────
            List<Microbe> neighbours = microbeGrid.getNearbyMicrobes(microbe.getX(), microbe.getY());
            if (microbe.isCarnivore()) {
                processCarnivoreBehaviour(microbe, neighbours);
            } else {
                processHerbivoreBehaviour(microbe, neighbours, foodGrid);
            }

            // ── 4. Reproduction ───────────────────────────────────────────
            tryReproduce(microbe, random);
        }
    }

    private void processCarnivoreBehaviour(Microbe microbe, List<Microbe> neighbours) {
        TargetCandidate preyCandidate = findNearestPrey(microbe, neighbours);
        Microbe prey = preyCandidate.target();
        if (prey == null) {
            microbe.setAiState(AiState.WANDER);
            return;
        }

        double dx = prey.getX() - microbe.getX();
        double dy = prey.getY() - microbe.getY();
        double distSq = preyCandidate.distSq();
        if (distSq <= 1e-12) {
            microbe.setAiState(AiState.WANDER);
            return;
        }
        double dist = Math.sqrt(distSq);

        microbe.setAiState(AiState.HUNT);
        microbe.setTargetX(prey.getX());
        microbe.setTargetY(prey.getY());

        double steerX = clamp((dx / dist) * microbe.getSpeed() * HUNT_STEER_STRENGTH,
                -MAX_STEER_DELTA, MAX_STEER_DELTA);
        double steerY = clamp((dy / dist) * microbe.getSpeed() * HUNT_STEER_STRENGTH,
                -MAX_STEER_DELTA, MAX_STEER_DELTA);
        microbe.applyKnockback(steerX, steerY);

        double attackRange = (microbe.getSize() + prey.getSize()) * 1.5;
        long now = System.currentTimeMillis();
        if (dist >= attackRange || prey.isDead() || (now - microbe.getLastAttackTime()) < ATTACK_COOLDOWN_MS) {
            return;
        }

        double sizeMultiplier = microbe.getSize() / (double) Math.max(1, prey.getSize());
        sizeMultiplier = clamp(sizeMultiplier, 0.5, 2.5);
        double scaledDamage = COMBAT_DAMAGE * sizeMultiplier;

        double energyGain = prey.takeDamageAndTransferEnergy(scaledDamage);
        microbe.eat(energyGain);
        microbe.markAttack();

        double kbDist = Math.max(0.1, Math.sqrt(dx * dx + dy * dy));
        double kx = (dx / kbDist) * 5.0;
        double ky = (dy / kbDist) * 5.0;
        prey.applyKnockback(kx, ky);
    }

    private void processHerbivoreBehaviour(Microbe microbe, List<Microbe> neighbours, SpatialGrid foodGrid) {
        for (FoodPellet food : foodGrid.getNearbyFood(microbe.getX(), microbe.getY())) {
            if (food.checkCollision(microbe)) {
                double energyGain = food.consume();
                if (energyGain > 0) microbe.eat(energyGain);
                break;
            }
        }

        TargetCandidate threatCandidate = findNearestThreat(microbe, neighbours);
        Microbe threat = threatCandidate.target();
        if (threat == null) {
            microbe.setAiState(AiState.WANDER);
            return;
        }

        double dx = microbe.getX() - threat.getX();
        double dy = microbe.getY() - threat.getY();
        double distSq = threatCandidate.distSq();
        if (distSq <= 1e-12) {
            microbe.setAiState(AiState.WANDER);
            return;
        }
        double dist = Math.sqrt(distSq);

        microbe.setAiState(AiState.FLEE);
        microbe.setTargetX(threat.getX());
        microbe.setTargetY(threat.getY());

        double steerX = clamp((dx / dist) * microbe.getSpeed() * FLEE_STEER_STRENGTH,
                -MAX_STEER_DELTA, MAX_STEER_DELTA);
        double steerY = clamp((dy / dist) * microbe.getSpeed() * FLEE_STEER_STRENGTH,
                -MAX_STEER_DELTA, MAX_STEER_DELTA);
        microbe.applyKnockback(steerX, steerY);
    }

    private void tryReproduce(Microbe microbe, ThreadLocalRandom random) {
        if (!microbe.canReproduce()) return;

        int retryCount = 0;
        while (retryCount < MAX_REPRODUCTION_ATTEMPTS) {
            int currentSlots = availableReproductionSlots.get();
            if (currentSlots <= 0) break;

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

    private TargetCandidate findNearestPrey(Microbe microbe, List<Microbe> neighbours) {
        Microbe best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Microbe other : neighbours) {
            if (other == microbe || other.isDead() || other.isCarnivore()) continue;
            double dx = other.getX() - microbe.getX();
            double dy = other.getY() - microbe.getY();
            double dSq = dx * dx + dy * dy;
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = other;
            }
        }
        return new TargetCandidate(best, bestDistSq);
    }

    private TargetCandidate findNearestThreat(Microbe microbe, List<Microbe> neighbours) {
        Microbe best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Microbe other : neighbours) {
            if (other == microbe || other.isDead() || !other.isCarnivore()) continue;
            double dx = other.getX() - microbe.getX();
            double dy = other.getY() - microbe.getY();
            double dSq = dx * dx + dy * dy;
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = other;
            }
        }
        return new TargetCandidate(best, bestDistSq);
    }

    private record TargetCandidate(Microbe target, double distSq) {
    }

    /**
     * Immutable snapshot of the simulation state published after each {@code update()}.
     * The EDT reads this via a single volatile read — no lock, no entity mutation races.
     */
    public record RenderSnapshot(List<Microbe.RenderState> microbes, List<FoodPellet> food) {
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
