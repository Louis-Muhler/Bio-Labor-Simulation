package com.biolab;

/**
 * Represents a snapshot of an ancestor's stats for lineage tracking.
 * Used to visualize evolution over generations.
 */
public class AncestorSnapshot {
    private final double heatResistance;
    private final double toxinResistance;
    private final double speed;
    private final int generation; // 0 = current, 1 = parent, 2 = grandparent, etc.
    private final long birthTime; // For debugging/display

    public AncestorSnapshot(double heatResistance, double toxinResistance, double speed, int generation) {
        this.heatResistance = heatResistance;
        this.toxinResistance = toxinResistance;
        this.speed = speed;
        this.generation = generation;
        this.birthTime = System.currentTimeMillis();
    }

    public double getHeatResistance() {
        return heatResistance;
    }

    public double getToxinResistance() {
        return toxinResistance;
    }

    public double getSpeed() {
        return speed;
    }

    public int getGeneration() {
        return generation;
    }

    public long getBirthTime() {
        return birthTime;
    }

    @Override
    public String toString() {
        return String.format("Gen %d: Heat=%.2f, Toxin=%.2f, Speed=%.2f",
            generation, heatResistance, toxinResistance, speed);
    }
}

