package com.biolab;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid-based spatial index for efficient microbe proximity queries.
 *
 * <p>Partitions the world into fixed-size cells. Each living microbe is assigned
 * to the cell that contains its current position. To find nearby microbes for a
 * given world coordinate, only the microbe's own cell and the 8 surrounding cells
 * are checked, reducing neighbor lookup from O(n²) to approximately
 * O(n*(n/cellCount)).</p>
 *
 * <p>The grid is rebuilt every frame from the microbe snapshot (dead microbes are
 * skipped), so it is never modified concurrently – each worker thread only reads
 * from it after {@link #rebuild(List)} has completed.</p>
 */
public class MicrobeGrid {
    private final int cellSize;
    private final int cols;
    private final int rows;
    private final ArrayList<List<Microbe>> cells;
    /**
     * Frame stamp per cell to avoid full-grid clear each rebuild.
     */
    private final int[] cellFrameStamp;
    private int currentFrameStamp = 1;

    /**
     * Creates a new microbe spatial grid.
     *
     * @param worldWidth  width of the world in world units
     * @param worldHeight height of the world in world units
     * @param cellSize    size of each grid cell (should be &gt;= max interaction distance)
     * @throws IllegalArgumentException if {@code cellSize} is not positive
     */
    public MicrobeGrid(int worldWidth, int worldHeight, int cellSize) {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be > 0, was: " + cellSize);
        }
        this.cellSize = cellSize;
        this.cols = Math.max(1, (worldWidth + cellSize - 1) / cellSize);
        this.rows = Math.max(1, (worldHeight + cellSize - 1) / cellSize);

        int totalCells = this.cols * this.rows;
        cells = new ArrayList<>(totalCells);
        cellFrameStamp = new int[totalCells];
        for (int i = 0; i < totalCells; i++) {
            cells.add(new ArrayList<>(8)); // Slightly larger initial capacity for microbes
        }
    }

    /**
     * Clears the grid and re-inserts all living microbes from the given snapshot.
     * Dead microbes ({@link Microbe#isDead()}) are silently skipped.
     *
     * <p>Must be called once per frame, before any neighborhood queries,
     * and always from a single thread (the SimulationLoop thread).</p>
     *
     * @param snapshot immutable snapshot of microbes for this frame
     */
    public void rebuild(List<Microbe> snapshot) {
        currentFrameStamp++;
        if (currentFrameStamp == Integer.MAX_VALUE) {
            java.util.Arrays.fill(cellFrameStamp, 0);
            currentFrameStamp = 1;
        }

        // Insert each living microbe into its cell
        for (Microbe microbe : snapshot) {
            if (microbe.isDead()) continue; // Skip dead microbes

            int col = Math.min((int) (microbe.getX() / cellSize), cols - 1);
            int row = Math.min((int) (microbe.getY() / cellSize), rows - 1);
            col = Math.max(0, col);
            row = Math.max(0, row);
            int idx = row * cols + col;
            if (cellFrameStamp[idx] != currentFrameStamp) {
                cellFrameStamp[idx] = currentFrameStamp;
                cells.get(idx).clear();
            }
            cells.get(idx).add(microbe);
        }
    }

    /**
     * Writes nearby microbes into a caller-provided buffer to avoid per-query allocations.
     */
    public void fillNearbyMicrobes(double x, double y, List<Microbe> out) {
        out.clear();
        int centerCol = Math.min((int) (x / cellSize), cols - 1);
        int centerRow = Math.min((int) (y / cellSize), rows - 1);
        centerCol = Math.max(0, centerCol);
        centerRow = Math.max(0, centerRow);

        int minCol = Math.max(0, centerCol - 1);
        int maxCol = Math.min(cols - 1, centerCol + 1);
        int minRow = Math.max(0, centerRow - 1);
        int maxRow = Math.min(rows - 1, centerRow + 1);

        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                int idx = row * cols + col;
                if (cellFrameStamp[idx] == currentFrameStamp) {
                    out.addAll(cells.get(idx));
                }
            }
        }
    }

}

