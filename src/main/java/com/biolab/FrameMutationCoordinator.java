package com.biolab;

import java.util.function.Supplier;

/**
 * Coordinates frame processing and exclusive world mutations (capture/load/spawn).
 *
 * <p>Only one frame may run at a time. Exclusive mutations wait until the current
 * frame finishes and block new frames until they complete.</p>
 */
final class FrameMutationCoordinator {
    private boolean frameInProgress;
    private boolean exclusiveMutationInProgress;
    private Thread frameOwner;

    void beginFrame() throws InterruptedException {
        synchronized (this) {
            while (exclusiveMutationInProgress || frameInProgress) {
                wait();
            }
            frameInProgress = true;
            frameOwner = Thread.currentThread();
        }
    }

    void endFrame() {
        synchronized (this) {
            frameInProgress = false;
            frameOwner = null;
            notifyAll();
        }
    }

    <T> T runExclusive(Supplier<T> action) {
        // Allow reentrant exclusive actions from the active frame thread (e.g. queued spawn commands).
        if (Thread.currentThread() == frameOwner) {
            return action.get();
        }

        synchronized (this) {
            while (frameInProgress || exclusiveMutationInProgress) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for exclusive world mutation", e);
                }
            }
            exclusiveMutationInProgress = true;
        }

        try {
            return action.get();
        } finally {
            synchronized (this) {
                exclusiveMutationInProgress = false;
                notifyAll();
            }
        }
    }

    void runExclusive(Runnable action) {
        runExclusive(() -> {
            action.run();
            return null;
        });
    }
}

