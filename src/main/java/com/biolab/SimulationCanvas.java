package com.biolab;

import javax.swing.*;
import java.awt.*;

/**
 * Canvas for rendering the simulation world with camera controls (pan, zoom).
 *
 * <h3>Follow-camera</h3>
 * When a microbe is selected the camera continuously pulls toward its world
 * position using exponential smoothing: each tick (~16 ms) the remaining
 * distance shrinks by {@code PULL_STRENGTH} (10 %), giving a fast initial
 * snap that decelerates naturally as the microbe reaches the screen centre.
 */
public class SimulationCanvas extends JPanel {
    private static final double MIN_ZOOM = 0.05;
    private static final double MAX_ZOOM = 3.0;
    private static final double ZOOM_STEP = 1.1;
    private static final int GRID_SIZE = 100;

    // ── Rendering constants ───────────────────────────────────────────────
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
    /**
     * Exponential pull strength per timer tick (~16 ms).
     * Each tick the camera closes this fraction of the remaining distance to
     * the microbe, producing a fast initial pull that decelerates smoothly.
     * Lower values = smoother/slower tracking; higher = snappier.
     */
    private static final double PULL_STRENGTH = 0.06;
    private final int worldHeight;
    private final SimulationEngine engine;
    private final SelectionListener selectionListener;

    // ── Camera – follow / lerp ────────────────────────────────────────────
    /**
     * If the camera is closer than this many world units to the target, it snaps
     * exactly onto it. Prevents infinite micro-jitter from exponential decay.
     */
    private static final double SNAP_THRESHOLD = 0.5;
    /**
     * A press+release within this many pixels is treated as a click, not a drag.
     */
    private static final int DRAG_THRESHOLD = 5;
    // ── World ─────────────────────────────────────────────────────────────
    private final int worldWidth;
    /**
     * The microbe the camera is currently locked onto, or {@code null} when free.
     */
    private volatile Microbe followTarget;
    private double cameraY;
    private double zoom = 1.0;
    // ── Camera – position & zoom ──────────────────────────────────────────
    private double cameraX;
    private int lastMouseY;
    private int pressMouseX;
    private int pressMouseY;
    // ── Mouse drag state ──────────────────────────────────────────────────
    private int lastMouseX;
    private boolean isDragging = false;

    // ─────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @param worldWidth        width of the simulation world in world units
     * @param worldHeight       height of the simulation world in world units
     * @param canvasWidth       preferred pixel width of the canvas component
     * @param canvasHeight      preferred pixel height of the canvas component
     * @param engine            simulation engine to query for render data
     * @param selectionListener receives microbe click / deselect events
     */
    public SimulationCanvas(int worldWidth, int worldHeight, int canvasWidth, int canvasHeight,
                            SimulationEngine engine, SelectionListener selectionListener) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.engine = engine;
        this.selectionListener = selectionListener;
        setPreferredSize(new Dimension(canvasWidth, canvasHeight));
        setBackground(new Color(18, 18, 18));
        setOpaque(false);

        cameraX = worldWidth / 2.0;
        cameraY = worldHeight / 2.0;

        // follow timer runs independently of the simulation loop to keep the camera smoothly in sync with the render frames, without risking desync from simulation lag spikes or pauses.
        javax.swing.Timer followTimer = new javax.swing.Timer(16, e -> tickFollowCamera());
        followTimer.start();

        setupMouseListeners();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Mouse input
    // ─────────────────────────────────────────────────────────────────────

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
                        pressMouseX = e.getX();
                        pressMouseY = e.getY();
                        lastMouseX = e.getX();
                        lastMouseY = e.getY();
                        isDragging  = true;
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    if (isDragging) {
                        int dx = Math.abs(e.getX() - pressMouseX);
                        int dy = Math.abs(e.getY() - pressMouseY);
                        if (dx <= DRAG_THRESHOLD && dy <= DRAG_THRESHOLD) {
                            selectionListener.onSelectionCleared();
                        }
                    }
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
            zoom = e.getWheelRotation() < 0 ? zoom * ZOOM_STEP : zoom / ZOOM_STEP;
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
            clampZoomAndCamera();
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Camera – follow mode
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Locks the camera onto {@code microbe}.
     * The exponential pull in {@link #tickFollowCamera()} will smoothly drag
     * the camera toward the microbe on every subsequent timer tick.
     */
    public void startFollowing(Microbe microbe) {
        followTarget = microbe;
    }

    /**
     * Releases follow mode. The camera stays at its current position and the
     * user can pan freely.
     */
    public void stopFollowing() {
        followTarget = null;
    }

    /**
     * Called every ~16 ms by the follow timer on the EDT.
     * Only triggers a repaint so the camera update in {@link #paintComponent}
     * runs. The actual camera movement happens there to stay in sync with
     * the render frame and avoid jitter from timer desynchronisation.
     */
    private void tickFollowCamera() {
        if (followTarget != null) repaint();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Camera – clamp helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Clamps the camera so the viewport never shows space outside the world.
     */
    private void clampCamera() {
        double visibleWidth = getWidth()  / zoom;
        double visibleHeight = getHeight() / zoom;
        cameraX = Math.max(visibleWidth / 2, Math.min(worldWidth - visibleWidth / 2, cameraX));
        cameraY = Math.max(visibleHeight / 2, Math.min(worldHeight - visibleHeight / 2, cameraY));
    }

    /**
     * Enforces the minimum zoom for the current canvas size and clamps the camera.
     * Must be called after every resize or display-mode change.
     */
    public void clampZoomAndCamera() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        double minZoom = Math.max((double) getWidth() / worldWidth,
                (double) getHeight() / worldHeight);
        zoom = Math.max(minZoom, zoom);
        clampCamera();
        repaint();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Hit testing
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the first microbe whose hit circle contains the given screen
     * position, or {@code null} if no microbe is there.
     */
    private Microbe findMicrobeAtScreenPos(int screenX, int screenY) {
        double worldX = (screenX - getWidth() / 2.0) / zoom + cameraX;
        double worldY = (screenY - getHeight() / 2.0) / zoom + cameraY;
        for (Microbe m : engine.getMicrobes()) {
            if (m.contains(worldX, worldY)) return m;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // ── Camera follow update (runs once per frame, in sync with rendering) ──
        Microbe target = followTarget;
        if (target != null) {
            if (target.isDead()) {
                followTarget = null;
            } else {
                double dx = target.getX() - cameraX;
                double dy = target.getY() - cameraY;
                double distSq = dx * dx + dy * dy;
                if (distSq < SNAP_THRESHOLD * SNAP_THRESHOLD) {
                    cameraX = target.getX();
                    cameraY = target.getY();
                } else {
                    cameraX += dx * PULL_STRENGTH;
                    cameraY += dy * PULL_STRENGTH;
                }
                clampCamera();
            }
        }

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(WORLD_BG_COLOR);
            g2d.fillRect(0, 0, getWidth(), getHeight());

            java.awt.geom.AffineTransform originalTransform = g2d.getTransform();

            // ── Apply camera transform ────────────────────────────────────
            g2d.translate(getWidth() / 2.0, getHeight() / 2.0);
            g2d.scale(zoom, zoom);
            g2d.translate(-cameraX, -cameraY);

            g2d.setColor(WORLD_BG_COLOR);
            g2d.fillRect(0, 0, worldWidth, worldHeight);

            // ── Grid (visible portion only) ───────────────────────────────
            double visibleX1 = cameraX - (getWidth() / (2 * zoom));
            double visibleX2 = cameraX + (getWidth() / (2 * zoom));
            double visibleY1 = cameraY - (getHeight() / (2 * zoom));
            double visibleY2 = cameraY + (getHeight() / (2 * zoom));

            g2d.setColor(GRID_LINE_COLOR);
            int startGridX = ((int) visibleX1 / GRID_SIZE) * GRID_SIZE;
            int endGridX = ((int) visibleX2 / GRID_SIZE + 1) * GRID_SIZE;
            int startGridY = ((int) visibleY1 / GRID_SIZE) * GRID_SIZE;
            int endGridY = ((int) visibleY2 / GRID_SIZE + 1) * GRID_SIZE;
            for (int gx = startGridX; gx <= endGridX; gx += GRID_SIZE)
                g2d.drawLine(gx, (int) visibleY1, gx, (int) visibleY2);
            for (int gy = startGridY; gy <= endGridY; gy += GRID_SIZE)
                g2d.drawLine((int) visibleX1, gy, (int) visibleX2, gy);

            // ── Food pellets ──────────────────────────────────────────────
            for (FoodPellet food : engine.getFoodPellets()) {
                if (food == null || food.isConsumed()) continue;
                double fx = food.getX(), fy = food.getY();
                if (fx < visibleX1 - 20 || fx > visibleX2 + 20
                        || fy < visibleY1 - 20 || fy > visibleY2 + 20) continue;

                for (int i = 3; i > 0; i--) {
                    g2d.setColor(FOOD_GLOW_COLORS[3 - i]);
                    int gs = food.getSize() + (i * 3);
                    g2d.fillOval((int) fx - gs / 2, (int) fy - gs / 2, gs, gs);
                }
                int x = (int) fx - food.getSize() / 2;
                int y = (int) fy - food.getSize() / 2;
                g2d.setColor(FOOD_OUTER_COLOR);
                g2d.fillOval(x, y, food.getSize(), food.getSize());
                g2d.setColor(food.getColor());
                g2d.fillOval(x + 1, y + 1, food.getSize() - 2, food.getSize() - 2);
                g2d.setColor(FOOD_CENTER_COLOR);
                g2d.fillOval(x + 2, y + 2, food.getSize() - 4, food.getSize() - 4);
            }

            // ── Microbes ──────────────────────────────────────────────────
            for (Microbe microbe : engine.getMicrobes()) {
                if (microbe == null) continue;
                double mx = microbe.getX(), my = microbe.getY();
                if (mx < visibleX1 - 20 || mx > visibleX2 + 20
                        || my < visibleY1 - 20 || my > visibleY2 + 20) continue;

                Color microbeColor = microbe.getColor();
                int size = microbe.getSize();
                int x = (int) mx - size / 2;
                int y = (int) my - size / 2;

                // Health-scaled multi-layer glow
                Composite orig = g2d.getComposite();
                double healthRatio = microbe.getHealthRatio();
                for (int i = 3; i > 0; i--) {
                    float alpha = (float) ((20 + i * 15) * healthRatio / 255f);
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
                    g2d.setColor(microbeColor);
                    int gs = size + (i * 4);
                    g2d.fillOval(x - i * 2, y - i * 2, gs, gs);
                }
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 220 / 255f));
                g2d.setColor(microbe.getBrightColor());
                g2d.fillOval(x, y, size, size);
                g2d.setComposite(orig);
                g2d.setColor(microbeColor);
                g2d.fillOval(x + 1, y + 1, size - 2, size - 2);

                if (microbe.isSelected()) {
                    g2d.setColor(SELECTION_GLOW_COLOR);
                    g2d.setStroke(STROKE_3);
                    g2d.drawOval(x - 5, y - 5, size + 10, size + 10);
                    g2d.setColor(SELECTION_SOLID_COLOR);
                    g2d.setStroke(STROKE_2);
                    g2d.drawOval(x - 4, y - 4, size + 8, size + 8);
                    g2d.setStroke(STROKE_1);
                    g2d.drawOval(x - 3, y - 3, size + 6, size +  6);
                }
            }

            // ── Restore screen-space transform ────────────────────────────
            g2d.setTransform(originalTransform);


        } finally {
            g2d.dispose();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Inner interface
    // ─────────────────────────────────────────────────────────────────────

    /** Callback interface for microbe selection events raised by the canvas. */
    public interface SelectionListener {
        /** Called when the user clicks on a microbe. */
        void onMicrobeSelected(Microbe microbe);

        /** Called when the user clicks on empty space, clearing the selection. */
        void onSelectionCleared();
    }
}

