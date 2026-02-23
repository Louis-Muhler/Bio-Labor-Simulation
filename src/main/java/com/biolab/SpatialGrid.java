package com.biolab;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid-based spatial index for efficient food pellet collision detection.
 *
 * <p>Partitions the world into fixed-size cells. Each food pellet is assigned to
 * the cell that contains its position. To find nearby food for a microbe,
 * only the pellet's own cell and the 8 surrounding cells are checked,
 * reducing collision detection from O(n*m) to approximately O(n*(m/cellCount)).</p>
 *
 * <p>The grid is rebuilt every frame from food snapshot, so it is not modified
 * concurrently – each worker thread only reads from it.</p>
 */
public class SpatialGrid {
    private final int cellSize;
    private final int cols;
    private final int rows;
    private final ArrayList<List<FoodPellet>> cells;

    /**
     * Creates a new spatial grid.
     *
     * @param worldWidth  width of the world in pixels
     * @param worldHeight height of the world in pixels
     * @param cellSize    size of each grid cell (should be >= max collision distance)
     */
    public SpatialGrid(int worldWidth, int worldHeight, int cellSize) {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be > 0, was: " + cellSize);
        }
        this.cellSize = cellSize;
        this.cols = Math.max(1, (worldWidth + cellSize - 1) / cellSize);
        this.rows = Math.max(1, (worldHeight + cellSize - 1) / cellSize);

        int totalCells = this.cols * this.rows;
        cells = new ArrayList<>(totalCells);
        for (int i = 0; i < totalCells; i++) {
            cells.add(new ArrayList<>(4)); // Small initial capacity – most cells will have few pellets
        }
    }

    /**
     * Clears the grid and re-inserts all food pellets from the given snapshot.
     * Must be called once per frame before any {@link #getNearbyFood} queries.
     *
     * @param foodSnapshot immutable snapshot of food pellets for this frame
     */
    public void rebuild(List<FoodPellet> foodSnapshot) {
        // Clear all cells
        for (List<FoodPellet> cell : cells) {
            cell.clear();
        }

        // Insert each pellet into its cell
        for (FoodPellet food : foodSnapshot) {
            if (food.isConsumed()) continue; // Skip already consumed food

            int col = Math.min((int) (food.getX() / cellSize), cols - 1);
            int row = Math.min((int) (food.getY() / cellSize), rows - 1);
            col = Math.max(0, col);
            row = Math.max(0, row);
            cells.get(row * cols + col).add(food);
        }
    }

    /**
     * Returns all food pellets in the cell containing (x, y) and its 8 neighbors.
     * The returned list is a temporary collection suitable for iteration.
     *
     * @param x world x coordinate
     * @param y world y coordinate
     * @return list of food pellets in the 3×3 neighborhood (may contain consumed pellets)
     */
    public List<FoodPellet> getNearbyFood(double x, double y) {
        int centerCol = Math.min((int) (x / cellSize), cols - 1);
        int centerRow = Math.min((int) (y / cellSize), rows - 1);
        centerCol = Math.max(0, centerCol);
        centerRow = Math.max(0, centerRow);

        // Determine the neighborhood bounds
        int minCol = Math.max(0, centerCol - 1);
        int maxCol = Math.min(cols - 1, centerCol + 1);
        int minRow = Math.max(0, centerRow - 1);
        int maxRow = Math.min(rows - 1, centerRow + 1);

        // Collect pellets from the 3×3 neighborhood
        List<FoodPellet> nearby = new ArrayList<>();
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                nearby.addAll(cells.get(row * cols + col));
            }
        }
        return nearby;
    }

    /**
     * Returns the number of columns in the grid.
     */
    public int getCols() {
        return cols;
    }

    /**
     * Returns the number of rows in the grid.
     */
    public int getRows() {
        return rows;
    }

    /**
     * Returns the cell size used by this grid.
     */
    public int getCellSize() {
        return cellSize;
    }
}

