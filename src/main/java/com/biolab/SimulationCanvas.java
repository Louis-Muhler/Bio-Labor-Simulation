package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Canvas for rendering the simulation world with camera controls (pan, zoom).
 */
public class SimulationCanvas extends JPanel {
    private static final double MIN_ZOOM = 0.05;
    private static final double MAX_ZOOM = 3.0;
    private static final double ZOOM_STEP = 1.1;
    private static final int GRID_SIZE = 100;
    // Pre-allocated rendering constants
    private static final Color WORLD_BG_COLOR = new Color(15, 15, 20);
    private static final Color GRID_LINE_COLOR = new Color(25, 25, 35, 100);
    private static final Color FOOD_OUTER_COLOR = new Color(100, 220, 100);
    private static final Color FOOD_CENTER_COLOR = new Color(200, 255, 200);
    private static final Color SELECTION_GLOW_COLOR = new Color(0, 255, 255, 100);
    private static final Color SELECTION_SOLID_COLOR = new Color(0, 255, 255);
    private static final Color[] FOOD_GLOW_COLORS = {
            new Color(50, 255, 100, 45),
            new Color(50, 255, 100, 35),
            new Color(50, 255, 100, 25)
    };
    private static final BasicStroke STROKE_1 = new BasicStroke(1);
    private static final BasicStroke STROKE_2 = new BasicStroke(2);
    private static final BasicStroke STROKE_3 = new BasicStroke(3);
    private final int worldWidth;
    private final int worldHeight;
    private final SimulationEngine engine;
    private final SelectionListener selectionListener;
    // Camera system
    private double cameraX;
    private double cameraY;
    private double zoom = 1.0;
    // Mouse drag state
    private int lastMouseX;
    private int lastMouseY;
    private boolean isDragging = false;

    /**
     * Creates the simulation canvas.
     *
     * @param worldWidth        width of the simulation world in world units
     * @param worldHeight       height of the simulation world in world units
     * @param canvasWidth       preferred pixel width of the canvas component
     * @param canvasHeight      preferred pixel height of the canvas component
     * @param engine            the simulation engine to query for render data
     * @param selectionListener listener that receives microbe click/deselect events
     */
    public SimulationCanvas(int worldWidth, int worldHeight, int canvasWidth, int canvasHeight,
                            SimulationEngine engine, SelectionListener selectionListener) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.engine = engine;
        this.selectionListener = selectionListener;
        setPreferredSize(new Dimension(canvasWidth, canvasHeight));
        setBackground(new Color(18, 18, 18));
        setOpaque(false); // Parent contentPane clips children to the rounded border shape

        // Center camera initially
        cameraX = worldWidth / 2.0;
        cameraY = worldHeight / 2.0;

        setupMouseListeners();
    }

    /**
     * Sets up pan (left-click drag) and zoom (scroll wheel) mouse listeners.
     */
    private void setupMouseListeners() {
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    Microbe clicked = findMicrobeAtScreenPos(e.getX(), e.getY());
                    if (clicked != null) {
                        selectionListener.onMicrobeSelected(clicked);
                    } else {
                        selectionListener.onSelectionCleared();
                        // Start dragging camera
                        lastMouseX = e.getX();
                        lastMouseY = e.getY();
                        isDragging = true;
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    isDragging = false;
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (isDragging) {
                    int dx = e.getX() - lastMouseX;
                    int dy = e.getY() - lastMouseY;
                    cameraX -= dx / zoom;
                    cameraY -= dy / zoom;
                    clampCamera();
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    repaint();
                }
            }
        });

        addMouseWheelListener(e -> {
            if (e.getWheelRotation() < 0) {
                zoom *= ZOOM_STEP;
            } else {
                zoom /= ZOOM_STEP;
            }
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
            double minZoomForWorld = Math.max(
                    (double) getWidth() / worldWidth,
                    (double) getHeight() / worldHeight
            );
            zoom = Math.max(minZoomForWorld, zoom);
            clampCamera();
            repaint();
        });
    }

    /**
     * Clamps the camera position so the viewport never shows outside the world.
     */
    private void clampCamera() {
        double visibleWidth = getWidth() / zoom;
        double visibleHeight = getHeight() / zoom;
        cameraX = Math.max(visibleWidth / 2, Math.min(worldWidth - visibleWidth / 2, cameraX));
        cameraY = Math.max(visibleHeight / 2, Math.min(worldHeight - visibleHeight / 2, cameraY));
    }

    /**
     * Returns the first microbe whose bounding circle contains the given screen position,
     * or {@code null} if no microbe is at that point.
     */
    private Microbe findMicrobeAtScreenPos(int screenX, int screenY) {
        double worldX = (screenX - getWidth() / 2.0) / zoom + cameraX;
        double worldY = (screenY - getHeight() / 2.0) / zoom + cameraY;
        List<Microbe> microbes = engine.getMicrobes();
        for (Microbe microbe : microbes) {
            if (microbe.contains(worldX, worldY)) {
                return microbe;
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Fill entire canvas area first (outside-world "space" background)
            g2d.setColor(WORLD_BG_COLOR);
            g2d.fillRect(0, 0, getWidth(), getHeight());

            java.awt.geom.AffineTransform originalTransform = g2d.getTransform();

            // Apply camera transformation
            g2d.translate(getWidth() / 2.0, getHeight() / 2.0);
            g2d.scale(zoom, zoom);
            g2d.translate(-cameraX, -cameraY);

            // Draw world boundary with sci-fi grid pattern
            g2d.setColor(WORLD_BG_COLOR);
            g2d.fillRect(0, 0, worldWidth, worldHeight);

            // Draw grid pattern (only visible portion)
            g2d.setColor(GRID_LINE_COLOR);
            double visibleX1 = cameraX - (getWidth() / (2 * zoom));
            double visibleX2 = cameraX + (getWidth() / (2 * zoom));
            double visibleY1 = cameraY - (getHeight() / (2 * zoom));
            double visibleY2 = cameraY + (getHeight() / (2 * zoom));

            int startGridX = ((int) visibleX1 / GRID_SIZE) * GRID_SIZE;
            int endGridX = ((int) visibleX2 / GRID_SIZE + 1) * GRID_SIZE;
            int startGridY = ((int) visibleY1 / GRID_SIZE) * GRID_SIZE;
            int endGridY = ((int) visibleY2 / GRID_SIZE + 1) * GRID_SIZE;

            for (int gx = startGridX; gx <= endGridX; gx += GRID_SIZE) {
                g2d.drawLine(gx, (int) visibleY1, gx, (int) visibleY2);
            }
            for (int gy = startGridY; gy <= endGridY; gy += GRID_SIZE) {
                g2d.drawLine((int) visibleX1, gy, (int) visibleX2, gy);
            }


            // ===== RENDER FOOD PELLETS =====
            List<FoodPellet> foodPellets = engine.getFoodPellets();
            for (FoodPellet food : foodPellets) {
                if (food == null || food.isConsumed()) continue;
                double fx = food.getX();
                double fy = food.getY();
                if (fx < visibleX1 - 20 || fx > visibleX2 + 20 ||
                        fy < visibleY1 - 20 || fy > visibleY2 + 20) {
                    continue;
                }

                for (int i = 3; i > 0; i--) {
                    g2d.setColor(FOOD_GLOW_COLORS[3 - i]);
                    int glowSize = food.getSize() + (i * 3);
                    g2d.fillOval(
                            (int) food.getX() - glowSize / 2,
                            (int) food.getY() - glowSize / 2,
                            glowSize, glowSize
                    );
                }

                g2d.setColor(FOOD_OUTER_COLOR);
                int x = (int) food.getX() - food.getSize() / 2;
                int y = (int) food.getY() - food.getSize() / 2;
                g2d.fillOval(x, y, food.getSize(), food.getSize());
                g2d.setColor(food.getColor());
                g2d.fillOval(x + 1, y + 1, food.getSize() - 2, food.getSize() - 2);
                g2d.setColor(FOOD_CENTER_COLOR);
                g2d.fillOval(x + 2, y + 2, food.getSize() - 4, food.getSize() - 4);
            }

            // ===== RENDER MICROBES WITH GLOW =====
            List<Microbe> microbes = engine.getMicrobes();
            for (Microbe microbe : microbes) {
                if (microbe == null) continue;
                double mx = microbe.getX();
                double my = microbe.getY();
                if (mx < visibleX1 - 20 || mx > visibleX2 + 20 ||
                        my < visibleY1 - 20 || my > visibleY2 + 20) {
                    continue;
                }

                Color microbeColor = microbe.getColor();
                int size = microbe.getSize();
                int x = (int) microbe.getX() - size / 2;
                int y = (int) microbe.getY() - size / 2;

                // Multi-layer glow effect using AlphaComposite
                Composite originalComposite = g2d.getComposite();
                for (int i = 3; i > 0; i--) {
                    float alpha = (20 + (i * 15)) / 255f;
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    g2d.setColor(microbeColor);
                    int glowSize = size + (i * 4);
                    g2d.fillOval(x - i * 2, y - i * 2, glowSize, glowSize);
                }

                float innerAlpha = 220 / 255f;
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, innerAlpha));
                g2d.setColor(microbe.getBrightColor());
                g2d.fillOval(x, y, size, size);
                g2d.setComposite(originalComposite);

                g2d.setColor(microbeColor);
                g2d.fillOval(x + 1, y + 1, size - 2, size - 2);

                // Draw selection indicator
                if (microbe.isSelected()) {
                    g2d.setColor(SELECTION_GLOW_COLOR);
                    g2d.setStroke(STROKE_3);
                    g2d.drawOval(x - 5, y - 5, size + 10, size + 10);
                    g2d.setColor(SELECTION_SOLID_COLOR);
                    g2d.setStroke(STROKE_2);
                    g2d.drawOval(x - 4, y - 4, size + 8, size + 8);
                    g2d.setStroke(STROKE_1);
                    g2d.drawOval(x - 3, y - 3, size + 6, size + 6);
                }
            }

            g2d.setTransform(originalTransform);
        } finally {
            g2d.dispose();
        }
    }

    /**
     * Callback interface for microbe selection events from the canvas.
     */
    public interface SelectionListener {
        /**
         * Called when the user clicks on a microbe.
         */
        void onMicrobeSelected(Microbe microbe);

        /**
         * Called when the user clicks on empty space, clearing the selection.
         */
        void onSelectionCleared();
    }
}


