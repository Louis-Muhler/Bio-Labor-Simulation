package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimulationEngineAtomicStateTest {

    @Test
    void captureAndLoadShouldNotRaceWithConcurrentUpdates() throws InterruptedException {
        SimulationEngine engine = new SimulationEngine(400, 400, 300);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread updater = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 120; i++) {
                    engine.update();
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        }, "test-updater");

        Thread persister = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 40; i++) {
                    SimulationState state = engine.captureState();
                    assertNotNull(state);
                    engine.loadState(state);
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        }, "test-persister");

        updater.start();
        persister.start();
        start.countDown();
        done.await();

        engine.shutdown();
        assertNull(error.get(), () -> "Concurrent state operations failed: " + error.get());
    }
}

