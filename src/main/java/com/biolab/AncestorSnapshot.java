package com.biolab;

/**
 * Immutable snapshot of an ancestor for lineage tracking.
 *
 * @param heatResistance  the ancestor's heat resistance gene value
 * @param toxinResistance the ancestor's toxin resistance gene value
 * @param speed           the ancestor's speed gene value
 * @param diet            the ancestor's diet gene value (0.0 = Herbivore, 1.0 = Carnivore)
 * @param maxHealth       the ancestor's individual max health
 * @param maxEnergy       the ancestor's individual max energy
 * @param generation      absolute generation number (0 = first generation, monotonically increasing)
 */
public record AncestorSnapshot(double heatResistance, double toxinResistance, double speed, double diet,
                               double maxHealth, double maxEnergy,
                               int generation) implements java.io.Serializable {

    @Override
    public String toString() {
        return String.format("AbsGen %d: Heat=%.2f, Toxin=%.2f, Speed=%.2f, Diet=%.2f, MaxHealth=%.1f, MaxEnergy=%.1f",
                generation, heatResistance, toxinResistance, speed, diet, maxHealth, maxEnergy);
    }
}
