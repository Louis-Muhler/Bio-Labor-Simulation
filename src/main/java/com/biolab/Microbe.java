package com.biolab;

import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a single microbe entity in the simulation.
 * Each microbe has genes that determine its survival capabilities.
 */
public class Microbe {

    // Position
    private double x;
    private double y;

    // Movement
    private double velocityX;
    private double velocityY;

    // Genes (0.0 to 1.0) - immutable after creation
    private final double heatResistance;
    private final double toxinResistance;
    private final double speed;

    // Vital stats
    private double health;
    private int age; // Frames survived
    private static final double MAX_HEALTH = 100.0;
    private static final int REPRODUCTION_AGE = 120; // Reproduce after ~2 seconds at 60 FPS

    // Size
    private static final int SIZE = 4;

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
        this.age = 0;
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
        this.age = 0;
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
     * Updates the microbe's position and handles boundary bouncing.
     */
    public void move(int width, int height) {
        x += velocityX;
        y += velocityY;

        // Bounce off walls
        if (x < 0 || x > width) {
            velocityX = -velocityX;
            x = Math.max(0, Math.min(width, x));
        }
        if (y < 0 || y > height) {
            velocityY = -velocityY;
            y = Math.max(0, Math.min(height, y));
        }

        // Occasionally change direction
        if (ThreadLocalRandom.current().nextDouble() < 0.02) {
            randomizeVelocity();
        }
    }

    /**
     * Updates health based on environmental conditions.
     * This is where natural selection happens!
     *
     * @param temperature Environment temperature (0.0 to 1.0)
     * @param toxicity Environment toxicity (0.0 to 1.0)
     */
    public void updateHealth(double temperature, double toxicity) {
        // Calculate damage from heat (higher temp = more damage if low resistance)
        double heatDamage = temperature * (1.0 - heatResistance) * 0.5;

        // Calculate damage from toxins
        double toxinDamage = toxicity * (1.0 - toxinResistance) * 0.5;

        // Apply damage
        health -= (heatDamage + toxinDamage);

        age++;
    }

    /**
     * Checks if the microbe is dead.
     */
    public boolean isDead() {
        return health <= 0;
    }

    /**
     * Checks if the microbe is ready to reproduce.
     */
    public boolean canReproduce() {
        return age >= REPRODUCTION_AGE && health > MAX_HEALTH * 0.5;
    }

    /**
     * Resets reproduction cooldown after reproducing.
     */
    public void resetReproduction() {
        age = 0;
        health -= MAX_HEALTH * 0.3; // Reproduction costs energy
    }

    /**
     * Gets the color representation of this microbe based on its genes.
     * Red = Heat Resistance
     * Green = Toxin Resistance
     * Blue = Speed
     */
    public Color getColor() {
        int red = (int) (heatResistance * 255);
        int green = (int) (toxinResistance * 255);
        int blue = (int) (speed * 128); // Less influence on color
        return new Color(red, green, blue);
    }

    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
    public int getSize() { return SIZE; }
    public double getHealth() { return health; }
    public double getHeatResistance() { return heatResistance; }
    public double getToxinResistance() { return toxinResistance; }
    public double getSpeed() { return speed; }
}
