package com.biolab;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Read-side microbe lookup operations over the shared world list.
 */
final class MicrobeLookupService {
    private final WorldState worldState;

    MicrobeLookupService(WorldState worldState) {
        this.worldState = worldState;
    }

    Microbe findLivingChild(long parentId) {
        synchronized (worldState.dataLock()) {
            List<Microbe> microbes = worldState.population().microbes();
            for (Microbe m : microbes) {
                if (!m.isDead() && m.getParentId() == parentId) return m;
            }
        }
        return null;
    }

    Microbe findRandomLivingMicrobe() {
        synchronized (worldState.dataLock()) {
            List<Microbe> microbes = worldState.population().microbes();
            if (microbes.isEmpty()) return null;
            int idx = ThreadLocalRandom.current().nextInt(microbes.size());
            return microbes.get(idx);
        }
    }
}

