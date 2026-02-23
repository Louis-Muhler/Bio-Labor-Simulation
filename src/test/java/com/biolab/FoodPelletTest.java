package com.biolab;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the FoodPellet class: atomic consumption, collision detection,
 * and squared-distance optimization.
 */
class FoodPelletTest {

    // ===== Basic Properties =====

    @Test
    void foodPelletShouldStorePosition() {
        FoodPellet food = new FoodPellet(42.5, 99.3);
        assertEquals(42.5, food.getX(), 0.001);
        assertEquals(99.3, food.getY(), 0.001);
    }

    @Test
    void newFoodShouldNotBeConsumed() {
        FoodPellet food = new FoodPellet(100, 100);
        assertFalse(food.isConsumed());
    }

    // ===== Atomic Consumption =====

    @Test
    void consumeShouldReturnEnergyOnlyOnce() {
        FoodPellet food = new FoodPellet(100, 100);

        double first = food.consume();
        double second = food.consume();

        assertTrue(first > 0, "First consume should return energy");
        assertEquals(0.0, second, "Second consume should return 0");
        assertTrue(food.isConsumed());
    }

    @Test
    void concurrentConsumeShouldOnlyYieldEnergyOnce() throws InterruptedException {
        FoodPellet food = new FoodPellet(100, 100);
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // All threads start at the same time
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                double energy = food.consume();
                if (energy > 0) {
                    successCount.incrementAndGet();
                }
                doneLatch.countDown();
            }).start();
        }

        startLatch.countDown(); // Release all threads
        doneLatch.await();

        assertEquals(1, successCount.get(), "Exactly one thread should get the energy");
        assertTrue(food.isConsumed());
    }

    // ===== Collision Detection =====

    @Test
    void checkCollisionShouldDetectNearbyMicrobe() {
        FoodPellet food = new FoodPellet(100, 100);
        Microbe microbe = new Microbe(100, 100); // Same position
        assertTrue(food.checkCollision(microbe), "Microbe at same position should collide");
    }

    @Test
    void checkCollisionShouldRejectDistantMicrobe() {
        FoodPellet food = new FoodPellet(100, 100);
        Microbe microbe = new Microbe(500, 500); // Far away
        assertFalse(food.checkCollision(microbe), "Distant microbe should not collide");
    }

    @Test
    void checkCollisionShouldReturnFalseForConsumedFood() {
        FoodPellet food = new FoodPellet(100, 100);
        Microbe microbe = new Microbe(100, 100);

        food.consume(); // Mark as consumed
        assertFalse(food.checkCollision(microbe), "Consumed food should not trigger collision");
    }

    @Test
    void checkCollisionBoundaryCase() {
        FoodPellet food = new FoodPellet(100, 100);
        // Place microbe just at the edge of collision distance
        // collisionDist = FoodPellet.SIZE(6) + Microbe.SIZE(5) = 11
        Microbe nearMicrobe = new Microbe(110, 100); // dx=10, dy=0, dist=10 < 11
        Microbe farMicrobe = new Microbe(112, 100);  // dx=12, dy=0, dist=12 > 11

        assertTrue(food.checkCollision(nearMicrobe), "Microbe within collision distance should collide");
        assertFalse(food.checkCollision(farMicrobe), "Microbe outside collision distance should not collide");
    }

    // ===== Random Creation =====

    @Test
    void createRandomShouldPlaceWithinBounds() {
        for (int i = 0; i < 100; i++) {
            FoodPellet food = FoodPellet.createRandom(1000, 2000);
            assertTrue(food.getX() >= 0 && food.getX() <= 1000);
            assertTrue(food.getY() >= 0 && food.getY() <= 2000);
        }
    }

    // ===== Color =====

    @Test
    void getColorShouldReturnSameInstance() {
        FoodPellet food = new FoodPellet(0, 0);
        Color first = food.getColor();
        Color second = food.getColor();
        assertSame(first, second, "Color should be a cached constant");
    }
}

