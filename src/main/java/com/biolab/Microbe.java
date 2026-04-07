package com.biolab;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
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
 * {@code health}, {@code energy}, {@code age}, {@code velocityX/Y}) are published to the EDT
 * through immutable render snapshots created by {@link #toRenderState()} at the end of each
 * engine update.
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
    private static final int MAX_SNAPSHOTS = 32;

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
    private volatile double y;
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
    private static final double BASE_MAX_HEALTH = 100.0;
    private static final double BASE_MAX_ENERGY = 100.0;
    private static final double MIN_MAX_HEALTH = 1.0;
    private static final double MIN_MAX_ENERGY = 1.0;
    private static final double INITIAL_ENERGY_RATIO = 0.92;
    private static final double REPRODUCTION_START_ENERGY_RATIO = 0.32;
    private static final double BASE_REPRODUCTION_HEALTH_COST_RATIO = 0.28;
    private static final double BASE_REPRODUCTION_ENERGY_COST_RATIO = 0.30;
    private static final double BASE_REPRODUCTION_HEALTH_THRESHOLD_RATIO = 0.50;
    private static final double BASE_REPRODUCTION_ENERGY_THRESHOLD_RATIO = 0.62;
    private static final double REPRODUCTION_THRESHOLD_PER_LOAD = 0.10;
    private static final double REPRODUCTION_HEALTH_COST_PER_LOAD = 0.08;
    private static final double REPRODUCTION_ENERGY_COST_PER_LOAD = 0.12;
    private static final int REPRODUCTION_AGE = 80;
    private static final double MOVEMENT_ENERGY_COST = 0.009;
    private static final double MOVEMENT_SPEED_COST_FACTOR = 0.95;
    private static final double MOVEMENT_CAPACITY_COST_FACTOR = 0.55;
    private static final double MAX_AGILITY_DRAG = 0.35;
    private static final double AGILITY_DRAG_PER_LOAD = 0.22;
    private static final double AGILITY_DRAG_PER_DEFENSE = 0.10;
    private static final double ENERGY_TRANSFER_BASE_FACTOR = 0.70;
    private static final double ENERGY_TRANSFER_BULK_PENALTY = 0.08;
    private final double maxHealth;
    private final double maxEnergy;
    /**
     * Absolute generation counter: 1 for seed microbes, parent.absoluteGeneration + 1
     * for every child born through reproduction.  Never changes after construction.
     */
    private final int absoluteGeneration;
    // Mutable simulation state – written by one worker thread per frame.
    private volatile double x;
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
     * -1 means no active target (wander state).
     * {@code volatile} so the EDT can read it without acquiring stateLock.
     */
    private volatile double targetX = -1;
    /**
     * World-space Y coordinate of the microbe's current AI target.
     * -1 means no active target (wander state).
     */
    private volatile double targetY = -1;
    /**
     * Current typed AI state used by behaviour and debug rendering.
     */
    private volatile AiState aiState = AiState.WANDER;
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
        // Triangle-tradeoff: strong genes in one axis reduce headroom in others.
        double offenseAxis = random.nextDouble();
        double defenseAxis = random.nextDouble();
        double agilityAxis = random.nextDouble();
        double sum = offenseAxis + defenseAxis + agilityAxis + 1e-9;
        offenseAxis /= sum;
        defenseAxis /= sum;
        agilityAxis /= sum;

        this.diet = clamp01(0.10 + offenseAxis * 1.15 + (random.nextDouble() - 0.5) * 0.10);
        this.speed = clamp01(0.12 + agilityAxis * 1.10 + (random.nextDouble() - 0.5) * 0.08);
        double baseDefense = clamp01(0.15 + defenseAxis * 1.15);
        this.heatResistance = clamp01(baseDefense + (random.nextDouble() - 0.5) * 0.18);
        this.toxinResistance = clamp01(baseDefense + (random.nextDouble() - 0.5) * 0.18);
        this.maxHealth = createSeedMaxHealth(defenseAxis);
        this.maxEnergy = createSeedMaxEnergy(agilityAxis);
        this.health = this.maxHealth;
        this.energy = this.maxEnergy * INITIAL_ENERGY_RATIO;
        this.age = 0;
        this.ancestry = new ArrayList<>();
        this.unmodifiableAncestry = Collections.unmodifiableList(ancestry);
        this.cachedColor = computeColor();
        this.cachedBrightColor = computeBrightColor();
        randomizeVelocity();
    }

    private Microbe(long id,
                    long parentId,
                    int absoluteGeneration,
                    double x,
                    double y,
                    double heatResistance,
                    double toxinResistance,
                    double speed,
                    double diet,
                    double maxHealth,
                    double maxEnergy,
                    double health,
                    double energy,
                    int age,
                    double velocityX,
                    double velocityY,
                    boolean selected,
                    long lastAttackTime,
                    double targetX,
                    double targetY,
                    AiState aiState,
                    long adrenalineTimer,
                    List<AncestorSnapshot> ancestry) {
        this.id = id;
        this.parentId = parentId;
        this.absoluteGeneration = absoluteGeneration;
        this.x = x;
        this.y = y;
        this.heatResistance = heatResistance;
        this.toxinResistance = toxinResistance;
        this.speed = speed;
        this.diet = diet;
        this.maxHealth = sanitizeMaxHealth(maxHealth);
        this.maxEnergy = sanitizeMaxEnergy(maxEnergy);
        this.health = clamp(health, 0.0, this.maxHealth);
        this.energy = clamp(energy, 0.0, this.maxEnergy);
        this.age = age;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.isSelected = selected;
        this.lastAttackTime = lastAttackTime;
        this.targetX = targetX;
        this.targetY = targetY;
        this.aiState = aiState == null ? AiState.WANDER : aiState;
        this.adrenalineTimer = adrenalineTimer;
        this.ancestry = new ArrayList<>(ancestry);
        this.unmodifiableAncestry = Collections.unmodifiableList(this.ancestry);
        this.cachedColor = computeColor();
        this.cachedBrightColor = computeBrightColor();
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
        this.maxHealth = mutateCap(parent.maxHealth, MIN_MAX_HEALTH, Double.MAX_VALUE);
        this.maxEnergy = mutateCap(parent.maxEnergy, MIN_MAX_ENERGY, Double.MAX_VALUE);

        this.health = this.maxHealth;
        this.energy = this.maxEnergy * REPRODUCTION_START_ENERGY_RATIO;
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
                parent.maxHealth,
                parent.maxEnergy,
                parent.absoluteGeneration   // absolute, not relative
        ));

        // On overflow, globally remap to a deterministic evenly-covered set.
        // Anchors (oldest/newest) are always preserved.
        if (newAncestry.size() > MAX_SNAPSHOTS) {
            newAncestry = remapAncestrySnapshots(newAncestry, MAX_SNAPSHOTS);
        }

        this.ancestry = newAncestry;
        this.unmodifiableAncestry = Collections.unmodifiableList(this.ancestry);
        this.cachedColor = computeColor();
        this.cachedBrightColor = computeBrightColor();
        randomizeVelocity();
    }

    private static List<AncestorSnapshot> remapAncestrySnapshots(List<AncestorSnapshot> snapshots, int capacity) {
        if (snapshots.size() <= capacity) {
            return snapshots;
        }

        int lastIndex = snapshots.size() - 1;
        int firstGen = snapshots.get(0).generation();
        int lastGen = snapshots.get(lastIndex).generation();
        int targetCount = Math.max(2, capacity);

        TreeSet<Integer> selected = new TreeSet<>();
        selected.add(0);
        selected.add(lastIndex);

        // Pick slots by global generation quantiles so old and new eras are covered evenly.
        for (int slot = 1; slot < targetCount - 1; slot++) {
            int targetGen = firstGen + (int) Math.round(slot * (double) (lastGen - firstGen) / (targetCount - 1));
            int bestIdx = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int i = 1; i < lastIndex; i++) {
                if (selected.contains(i)) {
                    continue;
                }
                int distance = Math.abs(snapshots.get(i).generation() - targetGen);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIdx = i;
                }
            }
            if (bestIdx >= 0) {
                selected.add(bestIdx);
            }
        }

        while (selected.size() < targetCount) {
            Integer prev = null;
            int bestLeft = -1;
            int bestGap = 0;
            for (Integer current : selected) {
                if (prev != null) {
                    int gap = current - prev;
                    if (gap > bestGap) {
                        bestGap = gap;
                        bestLeft = prev;
                    }
                }
                prev = current;
            }
            if (bestGap <= 1 || bestLeft < 0) {
                break;
            }
            selected.add(bestLeft + bestGap / 2);
        }

        if (selected.size() < targetCount) {
            for (int i = 0; i <= lastIndex && selected.size() < targetCount; i++) {
                selected.add(i);
            }
        }

        List<AncestorSnapshot> remapped = new ArrayList<>(targetCount);
        for (Integer index : selected) {
            remapped.add(snapshots.get(index));
        }
        return remapped;
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
        this.heatResistance = 0.35 + random.nextDouble() * 0.45;
        this.toxinResistance = 0.35 + random.nextDouble() * 0.45;
        this.speed = 0.35 + random.nextDouble() * 0.45; // slightly faster for sandbox visibility
        this.diet = Math.max(0.0, Math.min(1.0, forcedDiet));
        this.maxHealth = createSeedMaxHealth((this.heatResistance + this.toxinResistance) * 0.5);
        this.maxEnergy = createSeedMaxEnergy(this.speed);
        this.health = this.maxHealth;
        this.energy = this.maxEnergy * INITIAL_ENERGY_RATIO;
        this.age = 0;
        this.ancestry = new ArrayList<>();
        this.unmodifiableAncestry = Collections.unmodifiableList(ancestry);
        this.cachedColor = computeColor();
        this.cachedBrightColor = computeBrightColor();
        randomizeVelocity();
    }

    /**
     * Creates a sandbox-spawned microbe with explicit trait values.
     */
    public static Microbe createSpawned(double x,
                                        double y,
                                        double heatResistance,
                                        double toxinResistance,
                                        double speed,
                                        double diet,
                                        double maxHealth,
                                        double maxEnergy) {
        double clampedHeat = clamp01(heatResistance);
        double clampedToxin = clamp01(toxinResistance);
        double clampedSpeed = clamp01(speed);
        double clampedDiet = clamp01(diet);
        double sanitizedMaxHealth = sanitizeMaxHealth(maxHealth);
        double sanitizedMaxEnergy = sanitizeMaxEnergy(maxEnergy);
        Microbe microbe = new Microbe(
                ID_COUNTER.getAndIncrement(),
                -1,
                1,
                x,
                y,
                clampedHeat,
                clampedToxin,
                clampedSpeed,
                clampedDiet,
                sanitizedMaxHealth,
                sanitizedMaxEnergy,
                sanitizedMaxHealth,
                sanitizedMaxEnergy * INITIAL_ENERGY_RATIO,
                0,
                0.0,
                0.0,
                false,
                0L,
                -1,
                -1,
                AiState.WANDER,
                0L,
                List.of()
        );
        microbe.randomizeVelocity();
        return microbe;
    }

    /**
     * Mutates a gene value slightly.
     */
    private double mutate(double value) {
        double mutation = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.1; // ±5% mutation
        double newValue = value + mutation;
        return Math.max(0.0, Math.min(1.0, newValue)); // Clamp to [0, 1]
    }

    private static double createSeedMaxHealth(double defenseAxis) {
        double jitter = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.08;
        double scaled = BASE_MAX_HEALTH * (0.90 + clamp01(defenseAxis) * 0.20 + jitter);
        return sanitizeMaxHealth(scaled);
    }

    private static double createSeedMaxEnergy(double agilityAxis) {
        double jitter = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.08;
        double scaled = BASE_MAX_ENERGY * (0.90 + clamp01(agilityAxis) * 0.20 + jitter);
        return sanitizeMaxEnergy(scaled);
    }

    private static double mutateCap(double value, double min, double max) {
        double mutation = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.10;
        return clamp(value * (1.0 + mutation), min, max);
    }

    private static double sanitizeMaxHealth(double value) {
        return Math.max(MIN_MAX_HEALTH, value);
    }

    private static double sanitizeMaxEnergy(double value) {
        return Math.max(MIN_MAX_ENERGY, value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampRatio(double ratio) {
        return clamp(ratio, 0.0, 1.0);
    }

    private static double computeCapacityLoad(double maxHealth, double maxEnergy) {
        double healthLoad = Math.max(0.0, (maxHealth / BASE_MAX_HEALTH) - 1.0);
        double energyLoad = Math.max(0.0, (maxEnergy / BASE_MAX_ENERGY) - 1.0);
        // Health-heavy builds get a slightly higher burden than battery-heavy builds.
        return healthLoad * 0.60 + energyLoad * 0.40;
    }

    private double capacityLoad() {
        return computeCapacityLoad(maxHealth, maxEnergy);
    }

    /**
     * Sets random velocity based on speed gene.
     */
    private void randomizeVelocity() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * 2 * Math.PI;
        double magnitude = 0.8 + speed * 2.8;
        synchronized (stateLock) {
            this.velocityX = Math.cos(angle) * magnitude;
            this.velocityY = Math.sin(angle) * magnitude;
        }
    }

    /**
     * Updates position and velocity. Movement costs energy proportional to speed.
     * While adrenaline is active (within {@value #ADRENALINE_DURATION_MS} ms of
     * the last hit), the microbe moves at double speed but burns 3× the energy.
     */
    public void move(int width, int height) {
        boolean hasAdrenaline = (System.currentTimeMillis() - adrenalineTimer < ADRENALINE_DURATION_MS);

        double load = capacityLoad();
        double energyCost = MOVEMENT_ENERGY_COST
                * (1.0 + speed * MOVEMENT_SPEED_COST_FACTOR)
                * (1.0 + load * MOVEMENT_CAPACITY_COST_FACTOR);
        if (hasAdrenaline) {
            energyCost *= ADRENALINE_ENERGY_MULT;
        }

        double baseVX;
        double baseVY;
        synchronized (stateLock) {
            energy -= energyCost;
            baseVX = velocityX;
            baseVY = velocityY;
        }

        double appliedVX = hasAdrenaline ? baseVX * ADRENALINE_SPEED_MULT : baseVX;
        double appliedVY = hasAdrenaline ? baseVY * ADRENALINE_SPEED_MULT : baseVY;

        double vitality = getVitality();
        double energySpeedFactor = 0.30 + getEnergyRatio() * 0.70;
        double agilityDrag = Math.min(MAX_AGILITY_DRAG,
                load * AGILITY_DRAG_PER_LOAD + getDefenseTrait() * AGILITY_DRAG_PER_DEFENSE);
        double agilityFactor = 1.0 - agilityDrag;
        x += appliedVX * vitality * energySpeedFactor * agilityFactor;
        y += appliedVY * vitality * energySpeedFactor * agilityFactor;

        boolean bounceX = (x < 0 || x > width);
        boolean bounceY = (y < 0 || y > height);
        if (bounceX || bounceY) {
            synchronized (stateLock) {
                if (bounceX) {
                    velocityX = -velocityX;
                }
                if (bounceY) {
                    velocityY = -velocityY;
                }
            }
        }

        if (bounceX) {
            x = Math.max(0, Math.min(width, x));
        }
        if (bounceY) {
            y = Math.max(0, Math.min(height, y));
        }

        // Random direction changes for more organic movement
        if (ThreadLocalRandom.current().nextDouble() < 0.02 && getEnergyRatio() > 0.30) {
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
        double vitality = getVitality();
        double totalDamage = (heatDamage + toxinDamage) / vitality;

        synchronized (stateLock) {
            health -= totalDamage;
        }

        age++;
    }

    private static void ensureCounterAtLeast(long nextValueExclusive) {
        while (true) {
            long current = ID_COUNTER.get();
            if (current >= nextValueExclusive) {
                return;
            }
            if (ID_COUNTER.compareAndSet(current, nextValueExclusive)) {
                return;
            }
        }
    }

    public static Microbe fromPersistedState(PersistedState state) {
        Microbe microbe = new Microbe(
                state.id(),
                state.parentId(),
                state.absoluteGeneration(),
                state.x(),
                state.y(),
                state.heatResistance(),
                state.toxinResistance(),
                state.speed(),
                state.diet(),
                state.maxHealth(),
                state.maxEnergy(),
                state.health(),
                state.energy(),
                state.age(),
                state.velocityX(),
                state.velocityY(),
                state.selected(),
                state.lastAttackTime(),
                state.targetX(),
                state.targetY(),
                state.aiState(),
                state.adrenalineTimer(),
                state.ancestry()
        );
        ensureCounterAtLeast(state.id() + 1);
        return microbe;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public double getMaxEnergy() {
        return maxEnergy;
    }

    /**
     * Returns {@code true} if this microbe meets the age, health, and energy thresholds to reproduce.
     */
    public boolean canReproduce() {
        double load = capacityLoad();
        double requiredHealthRatio = clampRatio(BASE_REPRODUCTION_HEALTH_THRESHOLD_RATIO
                + load * (REPRODUCTION_THRESHOLD_PER_LOAD * 0.5));
        double requiredEnergyRatio = clampRatio(BASE_REPRODUCTION_ENERGY_THRESHOLD_RATIO
                + load * REPRODUCTION_THRESHOLD_PER_LOAD);

        double currentHealth;
        double currentEnergy;
        synchronized (stateLock) {
            currentHealth = health;
            currentEnergy = energy;
        }

        return age >= REPRODUCTION_AGE
                && currentHealth > maxHealth * requiredHealthRatio
                && currentEnergy >= maxEnergy * requiredEnergyRatio;
    }

    /**
     * Computes a brightened variant of the visual color (+40 per channel, clamped to 255).
     * Used for the inner glow effect in rendering.
     */
    private static Color computeBrightColor(Color color) {
        return new Color(
                Math.min(255, color.getRed() + 40),
                Math.min(255, color.getGreen() + 40),
                Math.min(255, color.getBlue() + 40)
        );
    }

    private static double traitCurve(double value) {
        return Math.pow(clamp01(value), 1.5);
    }

    private static double energyCurve(double value) {
        return Math.pow(clamp01(value), 0.7);
    }

    /**
     * Computes the visual color from Heat/Toxin genes and current energy ratio.
     * Diet is intentionally excluded from color mapping.
     */
    private Color computeColor(double energyRatio) {
        double clampedEnergy = clamp01(energyRatio);
        double heatWeight = traitCurve(heatResistance);
        double toxinWeight = traitCurve(toxinResistance);
        double speedWeight = traitCurve(speed);
        double dietWeight = traitCurve(diet);

        double[] hues = {
                0.0,            // heat -> red
                120.0 / 360.0,  // toxin -> green
                220.0 / 360.0,  // speed -> blue
                290.0 / 360.0   // diet -> violet
        };
        double[] weights = {heatWeight, toxinWeight, speedWeight, dietWeight};

        double sum = 0.0;
        double dominantWeight = 0.0;
        double x = 0.0;
        double y = 0.0;
        for (int i = 0; i < weights.length; i++) {
            double weight = weights[i];
            sum += weight;
            if (weight > dominantWeight) {
                dominantWeight = weight;
            }
            double angle = hues[i] * Math.PI * 2.0;
            x += Math.cos(angle) * weight;
            y += Math.sin(angle) * weight;
        }

        if (sum < 1e-6) {
            return new Color(110, 130, 150);
        }

        double hue = Math.atan2(y, x) / (Math.PI * 2.0);
        if (hue < 0.0) {
            hue += 1.0;
        }

        // Add controlled hue twist only for mixed profiles to avoid collapsing into a few colors.
        double balance = 1.0 - (dominantWeight / sum);
        double twist = balance * (
                0.10 * (heatResistance - toxinResistance)
                        + 0.08 * (speed - diet)
                        + 0.06 * (heatResistance * speed - toxinResistance * diet)
        );
        hue = hue + twist;
        hue = hue - Math.floor(hue);

        double sumNorm = clamp01(sum / 4.0);
        double saturation = clamp01(0.58 + 0.30 * sumNorm + 0.12 * balance);
        // High toxin intentionally darkens the color; energy still brightens visible state.
        double value = 0.78 - 0.35 * toxinWeight - 0.10 * sumNorm + 0.22 * energyCurve(clampedEnergy);
        value = Math.max(0.24, Math.min(0.94, value));
        return Color.getHSBColor((float) hue, (float) saturation, (float) value);
    }

    private Color computeColor() {
        double ratio;
        synchronized (stateLock) {
            ratio = maxEnergy <= 0.0 ? 0.0 : energy / maxEnergy;
        }
        return computeColor(ratio);
    }

    private Color computeBrightColor() {
        return computeBrightColor(cachedColor);
    }

    /**
     * Returns cached visual color.
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
     * Resets age and deducts reproduction costs.
     * Called on the parent after a child is spawned.
     */
    public void resetReproduction() {
        age = 0;
        double load = capacityLoad();
        double healthCostRatio = clampRatio(BASE_REPRODUCTION_HEALTH_COST_RATIO
                + load * REPRODUCTION_HEALTH_COST_PER_LOAD);
        double energyCostRatio = clampRatio(BASE_REPRODUCTION_ENERGY_COST_RATIO
                + load * REPRODUCTION_ENERGY_COST_PER_LOAD);
        synchronized (stateLock) {
            health = Math.max(0.0, health - maxHealth * healthCostRatio);
            energy = Math.max(0.0, energy - maxEnergy * energyCostRatio);
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
    private void applyVelocityImpulseLocked(double forceX, double forceY) {
        double sizeFactor = Math.max(0.5, getSize() / 5.0);
        this.velocityX += (forceX * KNOCKBACK_DAMPING) / sizeFactor;
        this.velocityY += (forceY * KNOCKBACK_DAMPING) / sizeFactor;
    }

    public void applyKnockback(double forceX, double forceY) {
        synchronized (stateLock) {
            applyVelocityImpulseLocked(forceX, forceY);
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
     * Records that this microbe just successfully attacked.
     * Must only be called from the worker thread that owns this microbe's chunk.
     */
    void markAttack() {
        lastAttackTime = System.currentTimeMillis();
    }

    /**
     * Increases energy, capped at this microbe's individual max energy.
     */
    public void eat(double energyGain) {
        synchronized (stateLock) {
            energy = Math.min(maxEnergy, energy + energyGain);
            // Food can slowly repair damage, enabling long-lived lineages when foraging succeeds.
            if (energyGain > 0 && health > 0) {
                health = Math.min(maxHealth, health + energyGain * 0.06);
            }
        }
    }

    /**
     * Inflicts {@code damage} on this microbe and returns the energy the attacker absorbs.
     *
     * <p>Called from the attacker's worker thread, so the victim may belong to a different
     * thread – hence this method is fully guarded by {@code stateLock}.</p>
     *
     * <p>Transfer scales with actually removed health and individual max values. High-capacity
     * victims therefore reward meaningful hits, but dense/tanky builds leak slightly less
     * energy per damage point.</p>
     *
     * @param damage raw damage amount (positive value)
     * @return energy awarded to the attacker (≥ 0)
     */
    private double applyDamageAndTransferEnergyLocked(double damage) {
        double vitality = getVitality();
        double scaledDamage = Math.max(0.0, damage) / vitality;
        double healthBeforeHit = Math.max(0.0, health);
        double actualDamage = Math.min(healthBeforeHit, scaledDamage);

        health -= actualDamage;
        // Trigger the adrenaline/panic response on any hit
        adrenalineTimer = System.currentTimeMillis();

        // Transfer only what was actually "harvested" by this hit, capped by victim's current energy.
        double transferEfficiency = Math.max(0.35, ENERGY_TRANSFER_BASE_FACTOR - capacityLoad() * ENERGY_TRANSFER_BULK_PENALTY);
        double potentialTransfer = maxHealth > 0.0
                ? (actualDamage / maxHealth) * maxEnergy * transferEfficiency
                : 0.0;
        double energyTransferred = Math.min(energy, potentialTransfer);
        energy -= energyTransferred;

        if (health < 0) {
            health = 0;
        }
        return energyTransferred;
    }

    public double takeDamageAndTransferEnergy(double damage) {
        synchronized (stateLock) {
            return applyDamageAndTransferEnergyLocked(damage);
        }
    }

    /**
     * Applies combat damage and knockback in one victim lock section.
     */
    public double takeDamageAndTransferEnergyWithKnockback(double damage, double forceX, double forceY) {
        synchronized (stateLock) {
            double transferred = applyDamageAndTransferEnergyLocked(damage);
            applyVelocityImpulseLocked(forceX, forceY);
            return transferred;
        }
    }

    /**
     * Checks whether a world-space point falls within the click hit area.
     * The hit radius is {@code 3 × getSize()} so microbes remain easy to select.
     * Uses squared distance to avoid {@code Math.sqrt()}.
     */
    public boolean contains(double px, double py) {
        final int hitRadius = getSize() * 3;
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
        double defense = getDefenseTrait();
        double strength = getStrengthTrait();
        double load = clamp01(capacityLoad());
        double bulk = 0.35 * defense + 0.25 * strength + 0.40 * load;
        double agilityPenalty = speed * (0.24 + load * 0.16);
        double sizeFactor = clamp01(0.18 + bulk - agilityPenalty);
        return 5 + (int) Math.round(sizeFactor * 8.0);
    }

    /**
     * Returns a senescence factor in [0.1, 1.0].
     * Young microbes are fully vital; old microbes become slower and more fragile.
     */
    public double getVitality() {
        if (age < 3000) return 1.0;
        return Math.max(0.1, 1.0 - ((age - 3000) / 6000.0));
    }

    public double getEnergyRatio() {
        synchronized (stateLock) {
            return maxEnergy <= 0.0 ? 0.0 : clamp01(energy / maxEnergy);
        }
    }

    public double getDefenseTrait() {
        return clamp01((heatResistance + toxinResistance) * 0.5);
    }

    public double getStrengthTrait() {
        // Carnivore tendency + body mass proxy produce striking power.
        return clamp01(0.70 * diet + 0.30 * (1.0 - speed));
    }

    /**
     * Returns how far this microbe's health/energy caps exceed the species baseline.
     * 0.0 means baseline capacity, larger values mean increasingly heavy upkeep.
     */
    public double getCapacityLoad() {
        return capacityLoad();
    }

    public boolean isDead() {
        synchronized (stateLock) {
            return health <= 0 || energy <= 0;
        }
    }

    /**
     * Returns the health as a [0.0, 1.0] ratio, used to scale visual glow intensity.
     */
    public double getHealthRatio() {
        synchronized (stateLock) {
            return maxHealth <= 0.0 ? 0.0 : clamp01(health / maxHealth);
        }
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

    /**
     * Returns current health in the range [0, maxHealth].
     */
    public double getHealth() {
        synchronized (stateLock) {
            return health;
        }
    }

    /**
     * Returns current energy in the range [0, maxEnergy].
     */
    public double getEnergy() {
        synchronized (stateLock) {
            return energy;
        }
    }

    // ── AI Intent accessors (debug / Developer Vision) ────────────────────

    /**
     * Sets the world-space X of the current AI target.
     */
    public void setTargetX(double x) {
        this.targetX = x;
    }

    /**
     * Sets the world-space Y of the current AI target.
     */
    public void setTargetY(double y) {
        this.targetY = y;
    }

    /**
     * Smoothly steers velocity toward a world-space target.
     *
     * @param tx       target x
     * @param ty       target y
     * @param strength blend factor in [0,1], higher = snappier turn
     */
    public void steerTowards(double tx, double ty, double strength) {
        double dx = tx - x;
        double dy = ty - y;
        double distSq = dx * dx + dy * dy;
        if (distSq <= 1e-9) return;

        double dist = Math.sqrt(distSq);
        double desiredMag = 0.9 + speed * 2.6;
        double desiredVX = (dx / dist) * desiredMag;
        double desiredVY = (dy / dist) * desiredMag;
        double s = clamp01(strength);

        synchronized (stateLock) {
            velocityX = velocityX * (1.0 - s) + desiredVX * s;
            velocityY = velocityY * (1.0 - s) + desiredVY * s;
        }
    }

    /**
     * Captures a single immutable snapshot of all render-relevant values.
     */
    public RenderState toRenderState() {
        double healthRatio;
        double energyRatio;
        synchronized (stateLock) {
            healthRatio = maxHealth <= 0.0 ? 0.0 : clamp01(health / maxHealth);
            energyRatio = maxEnergy <= 0.0 ? 0.0 : clamp01(energy / maxEnergy);
        }
        Color renderColor = computeColor(energyRatio);
        Color renderBrightColor = computeBrightColor(renderColor);
        return new RenderState(
                id,
                x,
                y,
                getSize(),
                renderColor,
                renderBrightColor,
                healthRatio,
                isCarnivore(),
                getStrengthTrait(),
                getDefenseTrait(),
                isSelected,
                lastAttackTime,
                aiState,
                targetX,
                targetY
        );
    }

    /**
     * Sets the current AI state.
     */
    public void setAiState(AiState state) {
        this.aiState = state == null ? AiState.WANDER : state;
    }

    public PersistedState toPersistedState() {
        double h;
        double e;
        double vx;
        double vy;
        synchronized (stateLock) {
            h = health;
            e = energy;
            vx = velocityX;
            vy = velocityY;
        }
        return new PersistedState(
                id,
                parentId,
                absoluteGeneration,
                x,
                y,
                vx,
                vy,
                heatResistance,
                toxinResistance,
                speed,
                diet,
                maxHealth,
                maxEnergy,
                h,
                e,
                age,
                isSelected,
                lastAttackTime,
                targetX,
                targetY,
                aiState,
                adrenalineTimer,
                List.copyOf(ancestry)
        );
    }

    /**
     * Immutable state consumed by rendering and hit-testing on the EDT.
     */
    public record RenderState(
            long id,
            double x,
            double y,
            int size,
            Color color,
            Color brightColor,
            double healthRatio,
            boolean carnivore,
            double strength,
            double defense,
            boolean selected,
            long lastAttackTime,
            AiState aiState,
            double targetX,
            double targetY) {
        /**
         * Returns whether a world-space point is inside this microbe's click area.
         */
        public boolean contains(double px, double py) {
            int hitRadius = size * 3;
            double dx = px - x;
            double dy = py - y;
            return (dx * dx + dy * dy) <= (hitRadius * hitRadius);
        }
    }

    public record PersistedState(
            long id,
            long parentId,
            int absoluteGeneration,
            double x,
            double y,
            double velocityX,
            double velocityY,
            double heatResistance,
            double toxinResistance,
            double speed,
            double diet,
            double maxHealth,
            double maxEnergy,
            double health,
            double energy,
            int age,
            boolean selected,
            long lastAttackTime,
            double targetX,
            double targetY,
            AiState aiState,
            long adrenalineTimer,
            List<AncestorSnapshot> ancestry) implements java.io.Serializable {
    }
}
