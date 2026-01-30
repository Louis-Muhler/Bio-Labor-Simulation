package com.biolab;

/**
 * Represents the environmental conditions in the simulation.
 * These values are controlled by the user through UI sliders.
 */
public class Environment {
    private double temperature; // 0.0 (cold) to 1.0 (hot)
    private double toxicity;    // 0.0 (clean) to 1.0 (toxic)

    public Environment() {
        this.temperature = 0.3; // Start mild
        this.toxicity = 0.3;
    }

    public synchronized double getTemperature() {
        return temperature;
    }

    public synchronized void setTemperature(double temperature) {
        this.temperature = Math.max(0.0, Math.min(1.0, temperature));
    }

    public synchronized double getToxicity() {
        return toxicity;
    }

    public synchronized void setToxicity(double toxicity) {
        this.toxicity = Math.max(0.0, Math.min(1.0, toxicity));
    }
}
