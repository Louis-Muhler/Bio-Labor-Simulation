package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for the Environment class: default values, synchronized access,
 * and value clamping.
 */
class EnvironmentTest {

    // ===== Default Values =====

    @Test
    void defaultValuesShouldBeMild() {
        Environment env = new Environment();
        assertEquals(0.3, env.getTemperature(), 0.001);
        assertEquals(0.3, env.getToxicity(), 0.001);
    }

    // ===== Clamping =====

    @Test
    void setTemperatureShouldClampToZeroOne() {
        Environment env = new Environment();

        env.setTemperature(1.5);
        assertEquals(1.0, env.getTemperature(), 0.001, "Should clamp to 1.0");

        env.setTemperature(-0.5);
        assertEquals(0.0, env.getTemperature(), 0.001, "Should clamp to 0.0");

        env.setTemperature(0.7);
        assertEquals(0.7, env.getTemperature(), 0.001, "Valid value should pass through");
    }

    @Test
    void setToxicityShouldClampToZeroOne() {
        Environment env = new Environment();

        env.setToxicity(2.0);
        assertEquals(1.0, env.getToxicity(), 0.001);

        env.setToxicity(-1.0);
        assertEquals(0.0, env.getToxicity(), 0.001);

        env.setToxicity(0.5);
        assertEquals(0.5, env.getToxicity(), 0.001);
    }

    // ===== Thread-Safety Stress Test =====

    @Test
    void concurrentAccessShouldNotCorruptValues() throws InterruptedException {
        Environment env = new Environment();
        int threadCount = 8;
        int iterations = 10_000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicBoolean errorDetected = new AtomicBoolean(false);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < iterations; i++) {
                    double value = (threadId * 0.1 + i * 0.0001) % 1.0;
                    env.setTemperature(value);
                    env.setToxicity(value);
                    double temp = env.getTemperature();
                    double tox = env.getToxicity();
                    // Values must always be in valid range
                    if (temp < 0.0 || temp > 1.0 || tox < 0.0 || tox > 1.0) {
                        errorDetected.set(true);
                        break;
                    }
                }
                doneLatch.countDown();
            }).start();
        }

        startLatch.countDown();
        doneLatch.await();

        assertFalse(errorDetected.get(), "Values went out of range during concurrent access");
    }
}

