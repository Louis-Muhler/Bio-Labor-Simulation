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

            // Reservoir sampling: uniform random choice among living microbes without extra allocations.
            Microbe chosen = null;
            int livingCount = 0;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (Microbe microbe : microbes) {
                if (microbe == null || microbe.isDead()) continue;
                livingCount++;
                if (random.nextInt(livingCount) == 0) {
                    chosen = microbe;
                }
            }
            return chosen;
        }
    }
}

