package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the SpatialGrid class: cell assignment, nearby food lookup,
 * edge cases, and correctness of spatial partitioning.
 */
class SpatialGridTest {

    // ===== Construction =====

    @Test
    void constructorShouldCalculateCorrectGridDimensions() {
        SpatialGrid grid = new SpatialGrid(100, 200, 50);
        assertEquals(2, grid.getCols());  // 100/50 = 2
        assertEquals(4, grid.getRows());  // 200/50 = 4
        assertEquals(50, grid.getCellSize());
    }

    @Test
    void constructorShouldHandleNonDivisibleDimensions() {
        SpatialGrid grid = new SpatialGrid(105, 210, 50);
        assertEquals(3, grid.getCols());  // ceil(105/50) = 3
        assertEquals(5, grid.getRows());  // ceil(210/50) = 5
    }

    @Test
    void constructorShouldRejectZeroCellSize() {
        assertThrows(IllegalArgumentException.class, () -> new SpatialGrid(100, 100, 0));
    }

    @Test
    void constructorShouldRejectNegativeCellSize() {
        assertThrows(IllegalArgumentException.class, () -> new SpatialGrid(100, 100, -5));
    }

    // ===== Empty Grid =====

    @Test
    void emptyGridShouldReturnNoFood() {
        SpatialGrid grid = new SpatialGrid(1000, 1000, 50);
        grid.rebuild(List.of());

        List<FoodPellet> nearby = grid.getNearbyFood(500, 500);
        assertTrue(nearby.isEmpty(), "Empty grid should return no food");
    }

    // ===== Rebuild and Lookup =====

    @Test
    void rebuildShouldPlaceFoodInCorrectCell() {
        SpatialGrid grid = new SpatialGrid(100, 100, 50);

        FoodPellet food = new FoodPellet(25, 25); // Cell (0,0)
        grid.rebuild(List.of(food));

        List<FoodPellet> nearby = grid.getNearbyFood(25, 25);
        assertTrue(nearby.contains(food), "Food should be found in its own cell");
    }

    @Test
    void nearbyFoodShouldIncludeAdjacentCells() {
        SpatialGrid grid = new SpatialGrid(200, 200, 50);

        // Place food in cell (0,0) - position (10, 10)
        FoodPellet food = new FoodPellet(10, 10);
        grid.rebuild(List.of(food));

        // Query from cell (1,1) - position (60, 60) – which is adjacent to cell (0,0)
        List<FoodPellet> nearby = grid.getNearbyFood(60, 60);
        assertTrue(nearby.contains(food), "Food in adjacent cell should be found");
    }

    @Test
    void distantFoodShouldNotBeReturned() {
        SpatialGrid grid = new SpatialGrid(1000, 1000, 50);

        FoodPellet food = new FoodPellet(10, 10);   // Cell (0,0)
        grid.rebuild(List.of(food));

        // Query from far away - cell (10,10)
        List<FoodPellet> nearby = grid.getNearbyFood(500, 500);
        assertFalse(nearby.contains(food), "Food in distant cell should not be found");
    }

    // ===== Consumed Food =====

    @Test
    void rebuildShouldSkipConsumedFood() {
        SpatialGrid grid = new SpatialGrid(100, 100, 50);

        FoodPellet food = new FoodPellet(25, 25);
        food.consume(); // Mark as consumed

        grid.rebuild(List.of(food));

        List<FoodPellet> nearby = grid.getNearbyFood(25, 25);
        assertFalse(nearby.contains(food), "Consumed food should not be in the grid");
    }

    // ===== Edge Positions =====

    @Test
    void foodAtWorldOriginShouldBeFoundCorrectly() {
        SpatialGrid grid = new SpatialGrid(100, 100, 50);

        FoodPellet food = new FoodPellet(0, 0);
        grid.rebuild(List.of(food));

        List<FoodPellet> nearby = grid.getNearbyFood(0, 0);
        assertTrue(nearby.contains(food));
    }

    @Test
    void foodAtWorldBorderShouldBeFoundCorrectly() {
        SpatialGrid grid = new SpatialGrid(100, 100, 50);

        FoodPellet food = new FoodPellet(99, 99);
        grid.rebuild(List.of(food));

        List<FoodPellet> nearby = grid.getNearbyFood(99, 99);
        assertTrue(nearby.contains(food));
    }

    @Test
    void negativeCoordinatesShouldBeClampedToZero() {
        SpatialGrid grid = new SpatialGrid(100, 100, 50);

        FoodPellet food = new FoodPellet(5, 5);
        grid.rebuild(List.of(food));

        // Query with negative coords should still find food in cell (0,0)
        List<FoodPellet> nearby = grid.getNearbyFood(-10, -10);
        assertTrue(nearby.contains(food), "Negative coords should clamp to cell (0,0)");
    }

    // ===== Multiple Foods in Same Cell =====

    @Test
    void multipleFoodInSameCellShouldAllBeReturned() {
        SpatialGrid grid = new SpatialGrid(100, 100, 50);

        FoodPellet food1 = new FoodPellet(10, 10);
        FoodPellet food2 = new FoodPellet(20, 20);
        FoodPellet food3 = new FoodPellet(30, 30);
        grid.rebuild(List.of(food1, food2, food3));

        List<FoodPellet> nearby = grid.getNearbyFood(15, 15);
        assertTrue(nearby.containsAll(List.of(food1, food2, food3)),
                "All food in same cell should be returned");
    }

    // ===== Rebuild Clears Old Data =====

    @Test
    void rebuildShouldClearPreviousData() {
        SpatialGrid grid = new SpatialGrid(100, 100, 50);

        FoodPellet food1 = new FoodPellet(25, 25);
        grid.rebuild(List.of(food1));

        // Rebuild with different food
        FoodPellet food2 = new FoodPellet(75, 75);
        grid.rebuild(List.of(food2));

        List<FoodPellet> nearOld = grid.getNearbyFood(25, 25);
        assertFalse(nearOld.contains(food1), "Old food should be gone after rebuild");
    }

    // ===== Large Grid =====

    @Test
    void largeWorldShouldWorkCorrectly() {
        SpatialGrid grid = new SpatialGrid(10_000, 10_000, 30);

        List<FoodPellet> foods = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            foods.add(FoodPellet.createRandom(10_000, 10_000));
        }
        grid.rebuild(foods);

        // Query should return some subset (not all 1000)
        List<FoodPellet> nearby = grid.getNearbyFood(5000, 5000);
        assertTrue(nearby.size() < foods.size(),
                "Spatial grid should filter – got " + nearby.size() + " of " + foods.size());
    }
}

