package com.biolab;

/**
 * Immutable trait profile used by spawn tools and preview rendering.
 */
public record MicrobeGeneProfile(
        double heatResistance,
        double toxinResistance,
        double speed,
        double diet,
        double maxHealth,
        double maxEnergy) {

    public MicrobeGeneProfile {
        heatResistance = clamp01(heatResistance);
        toxinResistance = clamp01(toxinResistance);
        speed = clamp01(speed);
        diet = clamp01(diet);
        maxHealth = Math.max(1.0, maxHealth);
        maxEnergy = Math.max(1.0, maxEnergy);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public Microbe createMicrobe(double x, double y) {
        return Microbe.createSpawned(
                x,
                y,
                heatResistance,
                toxinResistance,
                speed,
                diet,
                maxHealth,
                maxEnergy
        );
    }
}

