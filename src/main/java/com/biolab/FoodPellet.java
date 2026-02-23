package com.biolab;

import java.awt.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a food pellet in the simulation.
 * Microbes can consume food to gain energy.
 */
public class FoodPellet {
    private static final Color FOOD_COLOR = new Color(50, 255, 100);
    private final double x;
    private static final int SIZE = 6;
    private static final double ENERGY_VALUE = 30.0;
    private final double y;
    private final AtomicBoolean consumed = new AtomicBoolean(false);

    /**
     * Creates a food pellet at the given position.
     */
    public FoodPellet(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Creates a food pellet at a random position within the world bounds.
     */
    public static FoodPellet createRandom(int worldWidth, int worldHeight) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = random.nextDouble() * worldWidth;
        double y = random.nextDouble() * worldHeight;
        return new FoodPellet(x, y);
    }

    /**
     * Checks if a microbe is close enough to consume this food.
     * Uses squared distance to avoid expensive Math.sqrt().
     */
    public boolean checkCollision(Microbe microbe) {
        if (consumed.get()) return false;

        double dx = microbe.getX() - x;
        double dy = microbe.getY() - y;
        double collisionDist = SIZE + microbe.getSize();

        return (dx * dx + dy * dy) < (collisionDist * collisionDist);
    }

    /**
     * Atomically marks this food as consumed and returns the energy value.
     * Returns 0.0 if already consumed by another thread.
     */
    public double consume() {
        if (consumed.compareAndSet(false, true)) {
            return ENERGY_VALUE;
        }
        return 0.0; // Already consumed by another thread
    }

    /**
     * Returns {@code true} if this food pellet has already been consumed.
     */
    public boolean isConsumed() {
        return consumed.get();
    }

    /**
     * Returns the x coordinate of this food pellet.
     */
    public double getX() {
        return x;
    }

    /**
     * Returns the y coordinate of this food pellet.
     */
    public double getY() {
        return y;
    }

    /**
     * Returns the visual radius of this food pellet.
     */
    public int getSize() {
        return SIZE;
    }

    /**
     * Returns the cached display color.
     */
    public Color getColor() {
        return FOOD_COLOR;
    }
}

