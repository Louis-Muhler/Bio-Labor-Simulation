package com.biolab;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawn payload for creating one or more microbes at a world position.
 */
public record MicrobeSpawnRequest(
        double worldX,
        double worldY,
        int amount,
        boolean randomizePerSpawn,
        MicrobeGeneProfile baseProfile,
        MicrobeGeneProfile firstProfile) {

    private static final double TRAIT_JITTER = 0.22;
    private static final double CAP_JITTER_RATIO = 0.25;

    public MicrobeSpawnRequest {
        amount = Math.max(1, amount);
        baseProfile = baseProfile == null ? defaultProfile() : baseProfile;
    }

    public static MicrobeGeneProfile defaultProfile() {
        return new MicrobeGeneProfile(0.5, 0.5, 0.5, 0.5, 100.0, 100.0);
    }

    public static MicrobeGeneProfile randomizedProfile(MicrobeGeneProfile origin) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new MicrobeGeneProfile(
                jitter01(origin.heatResistance(), random),
                jitter01(origin.toxinResistance(), random),
                jitter01(origin.speed(), random),
                jitter01(origin.diet(), random),
                jitterCap(origin.maxHealth(), random),
                jitterCap(origin.maxEnergy(), random)
        );
    }

    private static double jitter01(double base, ThreadLocalRandom random) {
        return Math.max(0.0, Math.min(1.0, base + random.nextDouble(-TRAIT_JITTER, TRAIT_JITTER)));
    }

    private static double jitterCap(double base, ThreadLocalRandom random) {
        double ratio = 1.0 + random.nextDouble(-CAP_JITTER_RATIO, CAP_JITTER_RATIO);
        return Math.max(1.0, base * ratio);
    }

    public Microbe createMicrobeForIndex(int index) {
        MicrobeGeneProfile profile;
        if (index == 0 && firstProfile != null) {
            profile = firstProfile;
        } else if (randomizePerSpawn) {
            profile = randomizedProfile(baseProfile);
        } else {
            profile = baseProfile;
        }
        return profile.createMicrobe(worldX, worldY);
    }
}

