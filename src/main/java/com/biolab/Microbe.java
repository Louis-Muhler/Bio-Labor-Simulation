package com.biolab;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a single microbe entity in the simulation.
 * Each microbe has genes that determine its survival capabilities,
 * consumes energy through movement, and tracks its ancestry.
 */
public class Microbe {

    private double x;
    private double y;
    private double velocityX;
    private double velocityY;

    // Genetic traits (immutable, inherited with mutation)
    private final double heatResistance;
    private final double toxinResistance;
    private final double speed;

    // Vital stats
    private double health;
    private double energy;
    private int age;

    private static final double MAX_HEALTH = 100.0;
    private static final double MAX_ENERGY = 100.0;
    private static final double INITIAL_ENERGY = 80.0;
    private static final int REPRODUCTION_AGE = 120;
    private static final double MOVEMENT_ENERGY_COST = 0.05;
    private static final double REPRODUCTION_ENERGY_COST = 40.0;
    private static final double MIN_REPRODUCTION_ENERGY = 60.0;

    // Ancestry tracking for evolution visualization
    private final List<AncestorSnapshot> ancestry;
    private static final int MAX_ANCESTRY_DEPTH = 5;

    private static final int SIZE = 5;
    private boolean isSelected = false;

    /**
     * Creates a new microbe with random genes.
     */
    public Microbe(double x, double y) {
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
        randomizeVelocity();
    }

    /**
     * Creates a child microbe through reproduction (with mutation).
     */
    public Microbe(Microbe parent, double x, double y) {
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
                ancestor.getHeatResistance(),
                ancestor.getToxinResistance(),
                ancestor.getSpeed(),
                ancestor.getGeneration() + 1
            );
            ancestry.add(shifted);
        }

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

    public boolean canReproduce() {
        return age >= REPRODUCTION_AGE
            && health > MAX_HEALTH * 0.5
            && energy >= MIN_REPRODUCTION_ENERGY;
    }

    /**
     * Resets age and deducts reproduction costs.
     */
    public void resetReproduction() {
        age = 0;
        health -= MAX_HEALTH * 0.3;
        energy -= REPRODUCTION_ENERGY_COST;
    }

    /**
     * Returns visual color based on genes: Red = Heat Resistance, Green = Toxin Resistance, Blue = Speed.
     */
    public Color getColor() {
        int red = (int) (heatResistance * 255);
        int green = (int) (toxinResistance * 255);
        int blue = (int) (speed * 128);
        return new Color(red, green, blue);
    }

    public void eat(double energyGain) {
        energy = Math.min(MAX_ENERGY, energy + energyGain);
    }

    /**
     * Checks collision with a point for mouse selection.
     */
    public boolean contains(double px, double py) {
        double dx = px - x;
        double dy = py - y;
        double distance = Math.sqrt(dx * dx + dy * dy);
        return distance <= SIZE;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public int getSize() { return SIZE; }
    public double getHealth() { return health; }
    public double getEnergy() { return energy; }
    public double getHeatResistance() { return heatResistance; }
    public double getToxinResistance() { return toxinResistance; }
    public double getSpeed() { return speed; }
    public int getAge() { return age; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { this.isSelected = selected; }

    public List<AncestorSnapshot> getAncestry() {
        return Collections.unmodifiableList(ancestry);
    }

    public static double getMaxHealth() { return MAX_HEALTH; }
    public static double getMaxEnergy() { return MAX_ENERGY; }
}
