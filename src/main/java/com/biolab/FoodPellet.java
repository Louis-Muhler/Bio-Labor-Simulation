package com.biolab;

import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a food pellet in the simulation.
 * Microbes can consume food to gain energy.
 */
public class FoodPellet {
    private double x;
    private double y;
    private static final int SIZE = 6;
    private static final double ENERGY_VALUE = 30.0;
    private boolean consumed = false;

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
     */
    public boolean checkCollision(Microbe microbe) {
        if (consumed) return false;

        double dx = microbe.getX() - x;
        double dy = microbe.getY() - y;
        double distance = Math.sqrt(dx * dx + dy * dy);

        return distance < (SIZE + microbe.getSize());
    }

    /**
     * Marks this food as consumed and returns the energy value.
     */
    public double consume() {
        consumed = true;
        return ENERGY_VALUE;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public int getSize() {
        return SIZE;
    }

    public Color getColor() {
        return new Color(50, 255, 100); // Bright green
    }
}

