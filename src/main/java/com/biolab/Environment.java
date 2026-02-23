package com.biolab;

/**
 * Represents the environmental conditions of the simulation world.
 * Temperature and toxicity are adjustable at runtime via the UI sliders.
 * All accessors are {@code synchronized} for safe cross-thread reads and writes.
 */
public class Environment {
    private double temperature; // 0.0 (cold) to 1.0 (hot)
    private double toxicity;    // 0.0 (clean) to 1.0 (toxic)

    /**
     * Creates an Environment with mild default conditions.
     */
    public Environment() {
        this.temperature = 0.3;
        this.toxicity = 0.3;
    }

    /**
     * Returns the current temperature in the range [0.0, 1.0].
     */
    public synchronized double getTemperature() {
        return temperature;
    }

    /**
     * Sets the temperature, clamped to [0.0, 1.0].
     */
    public synchronized void setTemperature(double temperature) {
        this.temperature = Math.max(0.0, Math.min(1.0, temperature));
    }

    /**
     * Returns the current toxicity in the range [0.0, 1.0].
     */
    public synchronized double getToxicity() {
        return toxicity;
    }

    /**
     * Sets the toxicity, clamped to [0.0, 1.0].
     */
    public synchronized void setToxicity(double toxicity) {
        this.toxicity = Math.max(0.0, Math.min(1.0, toxicity));
    }
}
