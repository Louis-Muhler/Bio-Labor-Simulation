package com.biolab;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsulates thread-safe debug mode state transitions.
 */
final class DebugModeService {
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    boolean toggle() {
        while (true) {
            boolean current = enabled.get();
            boolean next = !current;
            if (enabled.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    boolean isEnabled() {
        return enabled.get();
    }

    void setEnabled(boolean value) {
        enabled.set(value);
    }
}

