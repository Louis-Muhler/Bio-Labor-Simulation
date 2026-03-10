package com.biolab;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single microbe entity in the simulation.
 * Each microbe has genes that determine its survival capabilities,
 * consumes energy through movement, and tracks its ancestry.
 *
 * <p><b>Thread-Safety Model:</b> Each microbe is modified by exactly one worker thread
 * per frame (chunk-based partitioning in {@code SimulationEngine.processMicrobeChunk()}).
 * This guarantees write-safety without locks. Mutable fields ({@code x}, {@code y},
 * {@code health}, {@code energy}, {@code age}, {@code velocityX/Y}) are read by the EDT
 * only via {@code getMicrobes()} which acquires {@code dataLock} — establishing a
 * happens-before between worker writes and EDT reads.
 * Only {@code isSelected} is {@code volatile} because it is written directly from the
 * EDT outside the lock.</p>
 */
public class Microbe {

    // ── Identity ──────────────────────────────────────────────────────────
    private static final AtomicLong ID_COUNTER = new AtomicLong(0);

    /**
     * Unique, monotonically increasing ID assigned at construction.
     */
    private final long id;

    /**
     * ID of the parent microbe, or {@code -1} for microbes with no parent
     * (i.e. those seeded at simulation start).
     */
    private final long parentId;

    /**
     * Maximum number of ancestry snapshots retained per lineage chain.
     * The smart-thinning algorithm keeps exactly this many snapshots, evenly
     * distributed across the absolute generation timeline.
     */
    private static final int MAX_SNAPSHOTS = 10;

    // Genetic traits – immutable after construction
    private final double heatResistance;
    /**
     * Diet gene: 0.0 = pure Herbivore, 1.0 = pure Carnivore.
     */
    private final double diet;
    /**
     * Intrinsic lock that guards all mutations to {@code health} and {@code energy}.
     * Required because cross-thread predator/prey interactions mean a microbe in
     * Thread A may now write to a microbe that is owned by Thread B.
     */
    private final Object stateLock = new Object();
    private double y;
    private double velocityX;
    private double velocityY;
    // Ancestry tracking for evolution visualization
    private final List<AncestorSnapshot> ancestry;
    private final List<AncestorSnapshot> unmodifiableAncestry;
    // Cached rendering values – immutable after construction
    private final Color cachedColor;
    private final Color cachedBrightColor;
    private final double toxinResistance;
    private final double speed;

    private static final double MAX_HEALTH = 100.0;
    private static final double MAX_ENERGY = 100.0;
    private static final double INITIAL_ENERGY = 80.0;
    private static final int REPRODUCTION_AGE = 120;
    private static final double MOVEMENT_ENERGY_COST = 0.02;
    private static final double REPRODUCTION_ENERGY_COST = 40.0;
    private static final double MIN_REPRODUCTION_ENERGY = 60.0;
    /**
     * Absolute generation counter: 1 for seed microbes, parent.absoluteGeneration + 1
     * for every child born through reproduction.  Never changes after construction.
     */
    private final int absoluteGeneration;
    private static final int SIZE = 5;
    // Mutable simulation state – written by one worker thread per frame,
    // read by the EDT only via getMicrobes() which acquires dataLock (happens-before guaranteed).
    private double x;
    private double health;
    private double energy;
    private int age;
    // isSelected is written directly from the EDT (outside dataLock), so it must be volatile
    private volatile boolean isSelected = false;

    /**
     * Timestamp (ms) of the last successful attack this microbe landed.
     * Written by the simulation worker thread that owns this microbe's chunk;
     * read by the EDT for rendering only.  {@code volatile} guarantees visibility
     * without needing to enter {@code stateLock}.
     */
    private volatile long lastAttackTime = 0;

    // ── Debug / AI Intent fields ──────────────────────────────────────────
    /**
     * Duration (ms) for which adrenaline stays active after a hit.
     */
    private static final long ADRENALINE_DURATION_MS = 2000;
    /**
     * Speed multiplier while adrenaline is active.
     */
    private static final double ADRENALINE_SPEED_MULT = 2.0;
    /**
     * Energy cost multiplier while adrenaline is active.
     */
    private static final double ADRENALINE_ENERGY_MULT = 3.0;
    /**
     * Damping factor applied to every incoming knockback force.
     * Reduces raw impulse values from the engine to prevent physics explosions.
     */
    private static final double KNOCKBACK_DAMPING = 0.15;
    /**
     * World-space X coordinate of the microbe's current AI target.
     * -1 means no active target (WANDER state).
     * {@code volatile} so the EDT can read it without acquiring stateLock.
     */
    private volatile double targetX = -1;
    /**
     * World-space Y coordinate of the microbe's current AI target.
     * -1 means no active target (WANDER state).
     */
    private volatile double targetY = -1;
    /**
     * Human-readable string describing the current AI state.
     * One of: "HUNT", "FLEE", "WANDER".
     */
    private volatile String aiState = "WANDER";
    /**
     * Timestamp (ms) at which this microbe last took damage.
     * While within {@code ADRENALINE_DURATION_MS} of this timestamp the microbe
     * moves twice as fast but burns 3× the energy (panic / adrenaline mechanic).
     * {@code volatile} for cross-thread visibility (written by victim's attacker
     * thread, read by the victim's own thread during {@code move()}).
     */
    private volatile long adrenalineTimer = 0;

    /**
     * Creates a new microbe with random genes.
     */
    public Microbe(double x, double y) {
        this.id = ID_COUNTER.getAndIncrement();
        this.parentId = -1;
        this.absoluteGeneration = 1;
        this.x = x;
        this.y = y;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.heatResistance = random.nextDouble() * 0.3;
        this.toxinResistance = random.nextDouble() * 0.3;
        this.speed = random.nextDouble() * 0.3;
        this.diet = random.nextDouble();
        this.health = MAX_HEALTH;
        this.energy = INITIAL_ENERGY;
        this.age = 0;
        this.ancestry = new ArrayList<>();
        this.unmodifiableAncestry = Collections.unmodifiableList(ancestry);
        this.cachedColor = computeColor();
        this.cachedBrightColor = computeBrightColor();
        randomizeVelocity();
    }

    /**
     * Creates a child microbe through reproduction (with mutation).
     */
    public Microbe(Microbe parent, double x, double y) {
        this.id = ID_COUNTER.getAndIncrement();
        this.parentId = parent.id;
        this.absoluteGeneration = parent.absoluteGeneration + 1;
        this.x = x;
        this.y = y;

        // Inherit genes with slight mutation
        this.heatResistance = mutate(parent.heatResistance);
        this.toxinResistance = mutate(parent.toxinResistance);
        this.speed = mutate(parent.speed);
        this.diet = mutate(parent.diet);

        this.health = MAX_HEALTH;
        this.energy = INITIAL_ENERGY;
        this.age = 0;

        // ── Smart ancestry thinning ──────────────────────────────────────
        // Copy the parent's ancestry list and append the parent itself as the
        // newest (most-recent) snapshot, using its absolute generation number.
        List<AncestorSnapshot> newAncestry = new ArrayList<>(parent.getAncestry());
        newAncestry.add(new AncestorSnapshot(
                parent.heatResistance,
                parent.toxinResistance,
                parent.speed,
                parent.diet,
                parent.absoluteGeneration   // absolute, not relative
        ));

        // If we have too many snapshots, drop the internal point whose removal
        // causes the least deviation from an ideally-even distribution across
        // the full absolute generation timeline (index 0 and the last entry
        // are always preserved as anchors).
        if (newAncestry.size() > MAX_SNAPSHOTS) {
            // Ideal gap between consecutive kept snapshots
            double idealGap = (double) parent.absoluteGeneration
                    / (MAX_SNAPSHOTS - 1);
            double minCost = Double.MAX_VALUE;
            int indexToRemove = 1; // fallback: always a valid internal index

            for (int i = 1; i < newAncestry.size() - 1; i++) {
                int gapIfRemoved = newAncestry.get(i + 1).generation()
                        - newAncestry.get(i - 1).generation();
                double cost = Math.abs(gapIfRemoved - idealGap);
                if (cost < minCost) {
                    minCost = cost;
                    indexToRemove = i;
                }
            }
            newAncestry.remove(indexToRemove);
        }

        this.ancestry = newAncestry;
        this.unmodifiableAncestry = Collections.unmodifiableList(this.ancestry);
        this.cachedColor = computeColor();
        this.cachedBrightColor = computeBrightColor();
        randomizeVelocity();
    }

    /**
     * Debug constructor that forces a specific diet value.
     * Used by {@code DebugSandboxApp} to spawn microbes with a known role.
     *
     * @param x          initial X position
     * @param y          initial Y position
     * @param forcedDiet diet gene value to force (0.0 = Herbivore, 1.0 = Carnivore)
     */
    public Microbe(double x, double y, double forcedDiet) {
        this.id = ID_COUNTER.getAndIncrement();
        this.parentId = -1;
        this.absoluteGeneration = 1;
        this.x = x;
        this.y = y;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.heatResistance = random.nextDouble() * 0.3;
        this.toxinResistance = random.nextDouble() * 0.3;
        this.speed = 0.3 + random.nextDouble() * 0.4; // slightly faster for sandbox visibility
        this.diet = Math.max(0.0, Math.min(1.0, forcedDiet));
        this.health = MAX_HEALTH;
        this.energy = INITIAL_ENERGY;
        this.age = 0;
        this.ancestry = new ArrayList<>();
        this.unmodifiableAncestry = Collections.unmodifiableList(ancestry);
        this.cachedColor = computeColor();
        this.cachedBrightColor = computeBrightColor();
        randomizeVelocity();
    }

    /**
     * Mutates a gene value slightly.
     */
    private double mutate(double value) {
        double mutation = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.1; // ±5% mutation
        double newValue = value + mutation;
        return Math.max(0.0, Math.min(1.0, newValue)); // Clamp to [0, 1]
    }

    /**
     * Sets random velocity based on speed gene.
     */
    private void randomizeVelocity() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * 2 * Math.PI;
        double magnitude = speed * 2.0; // Scale speed
        this.velocityX = Math.cos(angle) * magnitude;
        this.velocityY = Math.sin(angle) * magnitude;
    }

    /**
     * Updates position and velocity. Movement costs energy proportional to speed.
     * While adrenaline is active (within {@value #ADRENALINE_DURATION_MS} ms of
     * the last hit), the microbe moves at double speed but burns 3× the energy.
     */
    public void move(int width, int height) {
        boolean hasAdrenaline = (System.currentTimeMillis() - adrenalineTimer < ADRENALINE_DURATION_MS);

        double energyCost = MOVEMENT_ENERGY_COST * (1.0 + speed);
        if (hasAdrenaline) {
            energyCost *= ADRENALINE_ENERGY_MULT;
        }
        synchronized (stateLock) {
            energy -= energyCost;
        }

        double appliedVX = hasAdrenaline ? velocityX * ADRENALINE_SPEED_MULT : velocityX;
        double appliedVY = hasAdrenaline ? velocityY * ADRENALINE_SPEED_MULT : velocityY;

        x += appliedVX;
        y += appliedVY;

        // Bounce off world boundaries
        if (x < 0 || x > width) {
            velocityX = -velocityX;
            x = Math.max(0, Math.min(width, x));
        }
        if (y < 0 || y > height) {
            velocityY = -velocityY;
            y = Math.max(0, Math.min(height, y));
        }

        // Random direction changes for more organic movement
        if (ThreadLocalRandom.current().nextDouble() < 0.02) {
            randomizeVelocity();
        }
    }

    /**
     * Applies environmental damage based on temperature and toxicity.
     * Microbes with better resistance genes take less damage.
     */
    public void updateHealth(double temperature, double toxicity) {
        double heatDamage = temperature * (1.0 - heatResistance) * 0.05;
        double toxinDamage = toxicity * (1.0 - toxinResistance) * 0.05;

        synchronized (stateLock) {
            health -= (heatDamage + toxinDamage);
        }

        age++;
    }

    public boolean isDead() {
        return health <= 0 || energy <= 0;
    }

    /**
     * Returns the maximum possible health value.
     */
    public static double getMaxHealth() {
        return MAX_HEALTH;
    }

    /**
     * Returns the maximum possible energy value.
     */
    public static double getMaxEnergy() {
        return MAX_ENERGY;
    }

    /**
     * Returns {@code true} if this microbe meets the age, health, and energy thresholds to reproduce.
     */
    public boolean canReproduce() {
        return age >= REPRODUCTION_AGE
            && health > MAX_HEALTH * 0.5
            && energy >= MIN_REPRODUCTION_ENERGY;
    }

    /**
     * Resets age and deducts reproduction costs.
     * Called on the parent after a child is spawned.
     */
    public void resetReproduction() {
        age = 0;
        synchronized (stateLock) {
            health -= MAX_HEALTH * 0.3;
            energy -= REPRODUCTION_ENERGY_COST;
        }
    }

    /**
     * Computes the visual color based on genes.
     */
    private Color computeColor() {
        int red = (int) (heatResistance * 255);
        int green = (int) (toxinResistance * 255);
        int blue = (int) (speed * 128);
        return new Color(red, green, blue);
    }

    /**
     * Computes a brightened variant of the visual color (+40 per channel, clamped to 255).
     * Used for the inner glow effect in rendering.
     */
    private Color computeBrightColor() {
        return new Color(
                Math.min(255, cachedColor.getRed() + 40),
                Math.min(255, cachedColor.getGreen() + 40),
                Math.min(255, cachedColor.getBlue() + 40)
        );
    }

    /**
     * Returns cached visual color based on genes: Red = Heat Resistance, Green = Toxin Resistance, Blue = Speed.
     */
    public Color getColor() {
        return cachedColor;
    }

    /**
     * Returns cached brightened color variant for inner glow rendering.
     */
    public Color getBrightColor() {
        return cachedBrightColor;
    }

    // ===== Accessors =====

    /**
     * Increases energy by {@code energyGain}, capped at {@link #MAX_ENERGY}.
     */
    public void eat(double energyGain) {
        synchronized (stateLock) {
            energy = Math.min(MAX_ENERGY, energy + energyGain);
        }
    }

    /**
     * Returns {@code true} if this microbe is a Carnivore (diet gene &gt; 0.6).
     * Carnivores hunt Herbivores and ignore food pellets.
     */
    public boolean isCarnivore() {
        return diet > 0.6;
    }

    /**
     * Applies a knockback impulse to this microbe's velocity, damped by
     * {@link #KNOCKBACK_DAMPING} (0.15) to prevent physics explosions from
     * raw engine force values.
     * Called from the attacker's worker thread; guarded by {@code stateLock}
     * because the victim may belong to a different worker thread.
     *
     * @param forceX horizontal velocity delta (before damping)
     * @param forceY vertical velocity delta (before damping)
     */
    public void applyKnockback(double forceX, double forceY) {
        synchronized (stateLock) {
            this.velocityX += forceX * KNOCKBACK_DAMPING;
            this.velocityY += forceY * KNOCKBACK_DAMPING;
        }
    }

    /**
     * Returns the timestamp (ms) of the last successful attack this microbe landed,
     * or {@code 0} if it has never attacked.  Used only for visual feedback.
     */
    public long getLastAttackTime() {
        return lastAttackTime;
    }

    /**
     * Returns the timestamp (ms) at which this microbe last took damage,
     * or {@code 0} if it has never been hit.  Used for adrenaline/panic logic.
     */
    public long getAdrenalineTimer() {
        return adrenalineTimer;
    }

    /**
     * Returns {@code true} if the adrenaline/panic effect is currently active.
     * Convenience method for renderers and AI code.
     */
    public boolean isAdrenalineActive() {
        return (System.currentTimeMillis() - adrenalineTimer) < ADRENALINE_DURATION_MS;
    }

    /**
     * Records that this microbe just successfully attacked.
     * Must only be called from the worker thread that owns this microbe's chunk.
     */
    void markAttack() {
        lastAttackTime = System.currentTimeMillis();
    }

    /**
     * Inflicts {@code damage} on this microbe and returns the energy the attacker absorbs.
     *
     * <p>Called from the attacker's worker thread, so the victim may belong to a different
     * thread – hence this method is fully guarded by {@code stateLock}.</p>
     *
     * <ul>
     *   <li>If the victim survives the hit, the attacker receives energy proportional to the
     *       fraction of health removed (scaled to {@link #MAX_ENERGY}).</li>
     *   <li>If the victim is killed by the hit, the attacker receives <em>all</em> of the
     *       victim's remaining energy before it dies (energy is then zeroed).</li>
     * </ul>
     *
     * @param damage raw damage amount (positive value)
     * @return energy awarded to the attacker (≥ 0)
     */
    public double takeDamageAndTransferEnergy(double damage) {
        synchronized (stateLock) {
            double energyTransferred;
            health -= damage;
            // Trigger the adrenaline/panic response on any hit
            adrenalineTimer = System.currentTimeMillis();
            if (health <= 0) {
                // Victim dies – attacker claims all remaining energy
                energyTransferred = energy;
                energy = 0;
            } else {
                // Victim survives – attacker gets energy proportional to damage dealt
                energyTransferred = (damage / MAX_HEALTH) * MAX_ENERGY;
            }
            return energyTransferred;
        }
    }

    /**
     * Returns the health as a [0.0, 1.0] ratio, used to scale visual glow intensity.
     */
    public double getHealthRatio() {
        return Math.max(0.0, health / MAX_HEALTH);
    }

    /**
     * Checks whether a world-space point falls within the click hit area.
     * The hit radius is {@code 3 × SIZE} so small microbes are easier to select.
     * Uses squared distance to avoid {@code Math.sqrt()}.
     */
    public boolean contains(double px, double py) {
        final int hitRadius = SIZE * 3;
        double dx = px - x;
        double dy = py - y;
        return (dx * dx + dy * dy) <= (hitRadius * hitRadius);
    }

    /**
     * Returns the unique numeric ID of this microbe.
     */
    public long getId() {
        return id;
    }

    /**
     * Returns the ID of the parent microbe, or {@code -1} if this microbe
     * was seeded at simulation start and has no parent.
     */
    public long getParentId() {
        return parentId;
    }

    /**
     * Returns the current x position.
     */
    public double getX() {
        return x;
    }

    /**
     * Returns the current y position.
     */
    public double getY() {
        return y;
    }

    /**
     * Returns the visual radius of this microbe.
     */
    public int getSize() {
        return SIZE;
    }

    /**
     * Returns current health (0–{@link #MAX_HEALTH}).
     */
    public double getHealth() {
        return health;
    }

    /**
     * Returns current energy (0–{@link #MAX_ENERGY}).
     */
    public double getEnergy() {
        return energy;
    }

    /**
     * Returns the heat resistance gene value (0.0–1.0).
     */
    public double getHeatResistance() {
        return heatResistance;
    }

    /**
     * Returns the toxin resistance gene value (0.0–1.0).
     */
    public double getToxinResistance() {
        return toxinResistance;
    }

    /**
     * Returns the speed gene value (0.0–1.0).
     */
    public double getSpeed() {
        return speed;
    }

    /**
     * Returns the diet gene value (0.0 = pure Herbivore, 1.0 = pure Carnivore).
     */
    public double getDiet() {
        return diet;
    }

    /**
     * Returns the current age in simulation cycles.
     */
    public int getAge() {
        return age;
    }

    /**
     * Returns whether this microbe is currently selected by the user.
     */
    public boolean isSelected() {
        return isSelected;
    }

    /**
     * Sets the selection state of this microbe.
     */
    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }

    /**
     * Returns the absolute generation counter.
     * Seed microbes have generation 1; each reproduction increments this by 1.
     * Never mutates after construction, so no synchronisation is required.
     */
    public int getAbsoluteGeneration() {
        return absoluteGeneration;
    }

    /**
     * Returns an unmodifiable view of this microbe's ancestry list (oldest-first order).
     */
    public List<AncestorSnapshot> getAncestry() {
        return unmodifiableAncestry;
    }

    // ── AI Intent accessors (debug / Developer Vision) ────────────────────

    /**
     * Returns the world-space X of the current AI target, or -1 if none.
     */
    public double getTargetX() {
        return targetX;
    }

    /**
     * Sets the world-space X of the current AI target.
     */
    public void setTargetX(double x) {
        this.targetX = x;
    }

    /**
     * Returns the world-space Y of the current AI target, or -1 if none.
     */
    public double getTargetY() {
        return targetY;
    }

    /**
     * Sets the world-space Y of the current AI target.
     */
    public void setTargetY(double y) {
        this.targetY = y;
    }

    /**
     * Returns the current AI state string: "HUNT", "FLEE", or "WANDER".
     */
    public String getAiState() {
        return aiState;
    }

    /**
     * Sets the current AI state. Expected values: "HUNT", "FLEE", "WANDER".
     */
    public void setAiState(String state) {
        this.aiState = state;
    }
}
