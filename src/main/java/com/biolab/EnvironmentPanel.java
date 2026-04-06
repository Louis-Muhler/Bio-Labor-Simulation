package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;

/**
 * Floating overlay panel that exposes two environment sliders plus one food-per-tick control.
 *
 * <p>Temperature and Toxicity map to normalised [0.0, 1.0] values.
 * Food spawn is configured as a direct amount per tick (fractional allowed).
 * Slider positions are stored as {@link Rectangle} instances so mouse hit-tests
 * can be performed without re-computing layout on every event.</p>
 *
 * <p>The visual style mirrors {@link InspectorPanel}: dark background
 * ({@code #121212}), 15 px corner radius, neon-cyan glow border.</p>
 */
public class EnvironmentPanel extends JPanel {
    private final SimulationRuntime engine;

    private static final int PANEL_WIDTH = 300;
    private static final int MARGIN = 20;
    private static final int CONTENT_PADDING = 15;

    // ── Shared colours ────────────────────────────────────────────────────
    private static final Color BG_COLOR = OverlayTheme.PANEL_BG_ALPHA;
    private static final Color ACCENT_COLOR = OverlayTheme.ACCENT;
    private static final Color BORDER_GLOW_COLOR = OverlayTheme.ACCENT_GLOW;
    /**
     * Separator and empty-bar background – matches the InspectorPanel grid colour.
     */
    private static final Color SEPARATOR_COLOR = new Color(40, 40, 50);
    private static final Color BAR_BG_COLOR = new Color(40, 40, 50);

    // ── Fonts (identical hierarchy to InspectorPanel) ─────────────────────
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font VALUE_FONT = new Font("Consolas", Font.BOLD, 13);

    // ── Strokes ───────────────────────────────────────────────────────────
    private static final BasicStroke STROKE_1 = new BasicStroke(1);
    private static final BasicStroke STROKE_1_5 = new BasicStroke(1.5f);
    private static final BasicStroke STROKE_3 = new BasicStroke(3);

    // ── Per-slider colours (order: Temperature, Toxicity) ─
    private static final Color[] SLIDER_COLORS = {
            new Color(255, 100, 100),
            new Color(100, 255, 100)
    };
    /**
     * Semi-transparent glow drawn 1 px outside the filled bar.
     */
    private static final Color[] SLIDER_GLOW_COLORS = {
            new Color(255, 100, 100, 60),
            new Color(100, 255, 100, 60)
    };
    /** Glow ring drawn around the slider thumb. */
    private static final Color[] SLIDER_THUMB_GLOW_COLORS = {
            new Color(255, 100, 100, 80),
            new Color(100, 255, 100, 80)
    };

    private static final String[] SLIDER_LABELS = {"Temperature", "Toxicity"};
    private static final String FOOD_LABEL = "Food Spawn / Tick";
    private static final double FOOD_STEP_PER_CLICK = 0.25;
    private static final double MAX_FOOD_SPAWN_PER_TICK = 200.0;
    private static final int FOOD_BUTTON_W = 34;
    private static final int FOOD_BUTTON_H = 22;

    // ── Slider layout constants ───────────────────────────────────────────
    private static final int BAR_HEIGHT = 12;
    private static final int TRIANGLE_WIDTH = 7;
    private static final int TRIANGLE_HEIGHT = 10;
    private static final int LABEL_OFFSET_X = 12;
    private static final int LABEL_SPACING_Y = 18;
    private static final int VALUE_SPACING_Y     = 20;
    private static final int SLIDER_BOTTOM_SPACING = 16;
    private static final int THUMB_RADIUS        = 7;
    private static final int SLIDER_CORNER_RADIUS = 6;

    /**
     * Bounding rectangles of the two slider bars, updated each paint cycle.
     * Used for mouse hit-testing on press and drag events.
     */
    private final Rectangle[] sliderBars = new Rectangle[2];
    private final Rectangle foodDecreaseButton = new Rectangle();
    private final Rectangle foodIncreaseButton = new Rectangle();

    /**
     * Index of the slider currently being dragged, or -1 when idle.
     */
    private int draggingSlider = -1;

    // ────────────────────────────────────────────────────────────────────
    // Construction
    // ────────────────────────────────────────────────────────────────────

    /**
     * @param engine the simulation engine whose environment parameters are controlled
     */
    public EnvironmentPanel(SimulationRuntime engine) {
        this.engine = engine;
        setPreferredSize(new Dimension(PANEL_WIDTH, 310));
        setBackground(new Color(0, 0, 0, 0));
        setOpaque(false);

        for (int i = 0; i < sliderBars.length; i++) {
            sliderBars[i] = new Rectangle();
        }

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                for (int i = 0; i < sliderBars.length; i++) {
                    // Expand the hit area by 5 px vertically for easier interaction
                    Rectangle hitArea = new Rectangle(
                        sliderBars[i].x, sliderBars[i].y - 5,
                        sliderBars[i].width, sliderBars[i].height + 10
                    );
                    if (hitArea.contains(e.getPoint())) {
                        draggingSlider = i;
                        updateSliderValue(i, e.getX());
                        break;
                    }
                }
                if (draggingSlider < 0) {
                    if (foodDecreaseButton.contains(e.getPoint())) {
                        adjustFoodSpawnPerTick(-FOOD_STEP_PER_CLICK);
                    } else if (foodIncreaseButton.contains(e.getPoint())) {
                        adjustFoodSpawnPerTick(FOOD_STEP_PER_CLICK);
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingSlider >= 0) updateSliderValue(draggingSlider, e.getX());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggingSlider = -1;
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    // ────────────────────────────────────────────────────────────────────
    // Slider logic
    // ────────────────────────────────────────────────────────────────────

    /**
     * Converts the raw mouse X position to a [0.0, 1.0] value and propagates
     * it to the engine. Safe to call from the EDT; the engine uses volatile
     * fields and synchronized setters.
     */
    private void updateSliderValue(int sliderIndex, int mouseX) {
        Rectangle bar = sliderBars[sliderIndex];
        if (bar.width <= 0) return;

        double value = Math.max(0.0, Math.min(1.0, (double) (mouseX - bar.x) / bar.width));

        switch (sliderIndex) {
            case 0 -> engine.enqueueCommand(SimulationCommand.setTemperature(value));
            case 1 -> engine.enqueueCommand(SimulationCommand.setToxicity(value));
            default -> {}
        }
        repaint();
    }

    private static String formatFoodSpawnPerTick(double value) {
        return String.format(Locale.ROOT, "%.2f / tick", value);
    }

    private void adjustFoodSpawnPerTick(double delta) {
        double next = Math.max(0.0, Math.min(MAX_FOOD_SPAWN_PER_TICK, engine.getFoodSpawnRate() + delta));
        engine.enqueueCommand(SimulationCommand.setFoodSpawnRate(next));
        repaint();
    }

    /** Returns the current normalised value for the given slider index. */
    private double getSliderValue(int index) {
        return switch (index) {
            case 0 -> engine.getEnvironment().getTemperature();
            case 1 -> engine.getEnvironment().getToxicity();
            default -> 0;
        };
    }

    // ────────────────────────────────────────────────────────────────────
    // Painting
    // ────────────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int panelW = getWidth() - 2 * MARGIN;
            int panelH = getHeight() - 2 * MARGIN;

            // Dark rounded background
            g2d.setColor(BG_COLOR);
            g2d.fillRoundRect(MARGIN, MARGIN, panelW, panelH, 15, 15);

            // Outer glow, then crisp cyan border
            g2d.setColor(BORDER_GLOW_COLOR);
            g2d.setStroke(STROKE_3);
            g2d.drawRoundRect(MARGIN, MARGIN, panelW, panelH, 15, 15);
            g2d.setColor(ACCENT_COLOR);
            g2d.setStroke(STROKE_1);
            g2d.drawRoundRect(MARGIN, MARGIN, panelW, panelH, 15, 15);

            int x = MARGIN + CONTENT_PADDING;
            int y = MARGIN + CONTENT_PADDING;
            int contentWidth = panelW - 2 * CONTENT_PADDING;

            // Centred title
            g2d.setFont(TITLE_FONT);
            g2d.setColor(ACCENT_COLOR);
            FontMetrics fm = g2d.getFontMetrics();
            String titleStr = "ENVIRONMENT SETTINGS";
            g2d.drawString(titleStr, MARGIN + (panelW - fm.stringWidth(titleStr)) / 2, y + 15);
            y += 25;

            // 2 px separator
            g2d.setColor(SEPARATOR_COLOR);
            g2d.fillRect(x, y, contentWidth, 2);
            y += 14;

            for (int i = 0; i < sliderBars.length; i++) {
                y = drawSliderSection(g2d, x, y, contentWidth, i);
            }
            drawFoodSpawnSection(g2d, x, y, contentWidth);
        } finally {
            g2d.dispose();
        }
    }

    /**
     * Draws a single slider section: label with triangle icon, numeric value,
     * filled bar with glow, and a circular thumb handle.
     *
     * @return the Y coordinate directly below the rendered section
     */
    private int drawSliderSection(Graphics2D g2d, int x, int y, int contentWidth, int index) {
        Color color = SLIDER_COLORS[index];
        double value = getSliderValue(index);

        // Triangle bullet + label
        g2d.setFont(LABEL_FONT);
        g2d.setColor(color);
        int[] xPts = {x, x + TRIANGLE_WIDTH, x};
        int[] yPts = {y, y + TRIANGLE_HEIGHT / 2, y + TRIANGLE_HEIGHT};
        g2d.fillPolygon(xPts, yPts, 3);
        g2d.drawString(SLIDER_LABELS[index], x + LABEL_OFFSET_X, y + 10);
        y += LABEL_SPACING_Y;

        // Percentage value
        g2d.setFont(VALUE_FONT);
        g2d.setColor(ACCENT_COLOR);
        g2d.drawString(String.format("%.0f%%", value * 100), x, y + 12);
        y += VALUE_SPACING_Y;

        // Store bar bounds for mouse interaction
        int barY = y;
        sliderBars[index].setBounds(x, barY, contentWidth, BAR_HEIGHT);

        // Empty track
        g2d.setColor(BAR_BG_COLOR);
        g2d.fillRoundRect(x, barY, contentWidth, BAR_HEIGHT, SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);

        // Filled portion with soft glow halo
        int fillWidth = (int) (contentWidth * value);
        if (fillWidth > 0) {
            g2d.setColor(SLIDER_GLOW_COLORS[index]);
            g2d.fillRoundRect(x - 1, barY - 1, fillWidth + 2, BAR_HEIGHT + 2,
                    SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);
            g2d.setColor(color);
            g2d.fillRoundRect(x, barY, fillWidth, BAR_HEIGHT,
                    SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);
        }

        // Track border
        g2d.setColor(color);
        g2d.setStroke(STROKE_1);
        g2d.drawRoundRect(x, barY, contentWidth, BAR_HEIGHT, SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);

        // Circular thumb at the fill end point
        int thumbX = x + fillWidth;
        int thumbCY = barY + BAR_HEIGHT / 2;
        g2d.setColor(SLIDER_THUMB_GLOW_COLORS[index]);
        g2d.fillOval(thumbX - THUMB_RADIUS - 1, thumbCY - THUMB_RADIUS - 1,
                (THUMB_RADIUS + 1) * 2, (THUMB_RADIUS + 1) * 2);
        g2d.setColor(BG_COLOR);
        g2d.fillOval(thumbX - THUMB_RADIUS + 1, thumbCY - THUMB_RADIUS + 1,
                (THUMB_RADIUS - 1) * 2, (THUMB_RADIUS - 1) * 2);
        g2d.setColor(color);
        g2d.setStroke(STROKE_1_5);
        g2d.drawOval(thumbX - THUMB_RADIUS + 1, thumbCY - THUMB_RADIUS + 1,
                (THUMB_RADIUS - 1) * 2, (THUMB_RADIUS - 1) * 2);

        return y + BAR_HEIGHT + SLIDER_BOTTOM_SPACING;
    }

    private void drawFoodSpawnSection(Graphics2D g2d, int x, int y, int contentWidth) {
        Color color = new Color(100, 150, 255);
        double value = engine.getFoodSpawnRate();

        g2d.setFont(LABEL_FONT);
        g2d.setColor(color);
        int[] xPts = {x, x + TRIANGLE_WIDTH, x};
        int[] yPts = {y, y + TRIANGLE_HEIGHT / 2, y + TRIANGLE_HEIGHT};
        g2d.fillPolygon(xPts, yPts, 3);
        g2d.drawString(FOOD_LABEL, x + LABEL_OFFSET_X, y + 10);
        y += LABEL_SPACING_Y;

        g2d.setFont(VALUE_FONT);
        g2d.setColor(ACCENT_COLOR);
        g2d.drawString(formatFoodSpawnPerTick(value), x, y + 12);
        y += VALUE_SPACING_Y;

        int centerY = y + FOOD_BUTTON_H / 2;
        foodDecreaseButton.setBounds(x, y, FOOD_BUTTON_W, FOOD_BUTTON_H);
        foodIncreaseButton.setBounds(x + contentWidth - FOOD_BUTTON_W, y, FOOD_BUTTON_W, FOOD_BUTTON_H);

        drawFoodAdjustButton(g2d, foodDecreaseButton, color, "-");
        drawFoodAdjustButton(g2d, foodIncreaseButton, color, "+");

        g2d.setColor(BAR_BG_COLOR);
        int valueBoxX = foodDecreaseButton.x + foodDecreaseButton.width + 8;
        int valueBoxW = Math.max(40, foodIncreaseButton.x - valueBoxX - 8);
        g2d.fillRoundRect(valueBoxX, y, valueBoxW, FOOD_BUTTON_H, SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);
        g2d.setColor(color);
        g2d.drawRoundRect(valueBoxX, y, valueBoxW, FOOD_BUTTON_H, SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);

        g2d.setColor(ACCENT_COLOR);
        g2d.setFont(new Font("Consolas", Font.BOLD, 12));
        String compact = String.format(Locale.ROOT, "%.2f", value);
        FontMetrics fm = g2d.getFontMetrics();
        int tx = valueBoxX + (valueBoxW - fm.stringWidth(compact)) / 2;
        int ty = centerY + (fm.getAscent() - fm.getDescent()) / 2;
        g2d.drawString(compact, tx, ty);
    }

    private void drawFoodAdjustButton(Graphics2D g2d, Rectangle rect, Color color, String symbol) {
        g2d.setColor(BAR_BG_COLOR);
        g2d.fillRoundRect(rect.x, rect.y, rect.width, rect.height, SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);
        g2d.setColor(color);
        g2d.drawRoundRect(rect.x, rect.y, rect.width, rect.height, SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);

        g2d.setFont(new Font("Segoe UI", Font.BOLD, 16));
        FontMetrics fm = g2d.getFontMetrics();
        int tx = rect.x + (rect.width - fm.stringWidth(symbol)) / 2;
        int ty = rect.y + (rect.height + fm.getAscent() - fm.getDescent()) / 2 - 1;
        g2d.drawString(symbol, tx, ty);
    }
}
