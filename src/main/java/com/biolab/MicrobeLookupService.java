package com.biolab;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Read-side microbe lookup operations over the shared world list.
 */
final class MicrobeLookupService {
    private final Object dataLock;
    private final List<Microbe> microbes;

    MicrobeLookupService(Object dataLock, List<Microbe> microbes) {
        this.dataLock = dataLock;
        this.microbes = microbes;
    }

    Microbe findLivingChild(long parentId) {
        synchronized (dataLock) {
            for (Microbe m : microbes) {
                if (!m.isDead() && m.getParentId() == parentId) return m;
            }
        }
        return null;
    }

    Microbe findRandomLivingMicrobe() {
        synchronized (dataLock) {
            if (microbes.isEmpty()) return null;
            int idx = ThreadLocalRandom.current().nextInt(microbes.size());
            return microbes.get(idx);
        }
    }
}

