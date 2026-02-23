package com.biolab;

/**
 * Immutable snapshot of an ancestor's genetic traits for lineage tracking.
 *
 * @param heatResistance the ancestor's heat resistance gene value
 * @param toxinResistance the ancestor's toxin resistance gene value
 * @param speed the ancestor's speed gene value
 * @param generation 0 = parent, 1 = grandparent, 2 = great-grandparent, etc.
 */
public record AncestorSnapshot(double heatResistance, double toxinResistance, double speed, int generation) {

    @Override
    public String toString() {
        return String.format("Gen %d: Heat=%.2f, Toxin=%.2f, Speed=%.2f",
            generation, heatResistance, toxinResistance, speed);
    }
}
