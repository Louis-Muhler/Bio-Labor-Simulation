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

    // Genetic traits – immutable after construction
    private final double heatResistance;
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
    private static final double MOVEMENT_ENERGY_COST = 0.05;
    private static final double REPRODUCTION_ENERGY_COST = 40.0;
    private static final double MIN_REPRODUCTION_ENERGY = 60.0;
    private static final int MAX_ANCESTRY_DEPTH = 5;
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
     * Creates a new microbe with random genes.
     */
    public Microbe(double x, double y) {
        this.id = ID_COUNTER.getAndIncrement();
        this.parentId = -1;
        this.x = x;
        this.y = y;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.heatResistance = random.nextDouble();
        this.toxinResistance = random.nextDouble();
        this.speed = random.nextDouble();
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
        this.x = x;
        this.y = y;

        // Inherit genes with slight mutation
        this.heatResistance = mutate(parent.heatResistance);
        this.toxinResistance = mutate(parent.toxinResistance);
        this.speed = mutate(parent.speed);

        this.health = MAX_HEALTH;
        this.energy = INITIAL_ENERGY;
        this.age = 0;

        // Build ancestry: copy parent's ancestry and add parent as generation 0
        this.ancestry = new ArrayList<>();

        // Create snapshot of parent (generation 0 = parent)
        AncestorSnapshot parentSnapshot = new AncestorSnapshot(
            parent.heatResistance,
            parent.toxinResistance,
            parent.speed,
            0
        );
        ancestry.add(parentSnapshot);

        // Copy parent's ancestors, incrementing their generation numbers
        for (AncestorSnapshot ancestor : parent.ancestry) {
            if (ancestry.size() >= MAX_ANCESTRY_DEPTH) break;
            AncestorSnapshot shifted = new AncestorSnapshot(
                    ancestor.heatResistance(),
                    ancestor.toxinResistance(),
                    ancestor.speed(),
                    ancestor.generation() + 1
            );
            ancestry.add(shifted);
        }

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
     */
    public void move(int width, int height) {
        double energyCost = MOVEMENT_ENERGY_COST * (1.0 + speed);
        energy -= energyCost;

        x += velocityX;
        y += velocityY;

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
        double heatDamage = temperature * (1.0 - heatResistance) * 0.5;
        double toxinDamage = toxicity * (1.0 - toxinResistance) * 0.5;

        health -= (heatDamage + toxinDamage);

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
        health -= MAX_HEALTH * 0.3;
        energy -= REPRODUCTION_ENERGY_COST;
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
        energy = Math.min(MAX_ENERGY, energy + energyGain);
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
     * Returns an unmodifiable view of this microbe's ancestry list (oldest-first order).
     */
    public List<AncestorSnapshot> getAncestry() {
        return unmodifiableAncestry;
    }
}
