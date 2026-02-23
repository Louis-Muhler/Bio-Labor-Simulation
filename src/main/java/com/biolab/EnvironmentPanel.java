package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Floating overlay panel for environment controls.
 * Provides interactive custom sliders for Temperature, Toxicity, and Food Spawn Rate.
 */
public class EnvironmentPanel extends JPanel {
    private final SimulationEngine engine;

    private static final int PANEL_WIDTH = 300;
    private static final int MARGIN = 20; // Same as InspectorPanel for consistent look
    private static final int CONTENT_PADDING = 15;
    private static final Color BG_COLOR = new Color(18, 18, 18, 240);
    private static final Color ACCENT_COLOR = new Color(0, 255, 255);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font VALUE_FONT = new Font("Consolas", Font.BOLD, 14);

    // Slider bar areas (for mouse hit-testing)
    private static final int BAR_HEIGHT = 12;
    private final Rectangle[] sliderBars = new Rectangle[3]; // temp, toxicity, food
    private int draggingSlider = -1; // -1 = none, 0 = temp, 1 = toxicity, 2 = food

    // Slider colors
    private static final Color[] SLIDER_COLORS = {
        new Color(255, 100, 100),  // Temperature
        new Color(100, 255, 100),  // Toxicity
        new Color(100, 150, 255)   // Food Spawn Rate
    };

    // Pre-allocated glow variants for each slider color
    private static final Color[] SLIDER_GLOW_COLORS = {
            new Color(255, 100, 100, 60),
            new Color(100, 255, 100, 60),
            new Color(100, 150, 255, 60)
    };
    private static final Color[] SLIDER_THUMB_GLOW_COLORS = {
            new Color(255, 100, 100, 80),
            new Color(100, 255, 100, 80),
            new Color(100, 150, 255, 80)
    };

    private static final String[] SLIDER_LABELS = {
        "Temperature", "Toxicity", "Food Spawn Rate"
    };

    // Pre-allocated rendering constants
    private static final Color BORDER_GLOW_COLOR = new Color(0, 255, 255, 60);
    private static final Color SEPARATOR_COLOR = new Color(0, 255, 255, 100);
    private static final Color BAR_BG_COLOR = new Color(40, 40, 50);
    private static final BasicStroke STROKE_1 = new BasicStroke(1);
    private static final BasicStroke STROKE_1_5 = new BasicStroke(1.5f);
    private static final BasicStroke STROKE_3 = new BasicStroke(3);

    // Slider section layout constants
    private static final int TRIANGLE_WIDTH = 7;
    private static final int TRIANGLE_HEIGHT = 10;
    private static final int LABEL_OFFSET_X = 12;
    private static final int LABEL_SPACING_Y = 18;
    private static final int VALUE_SPACING_Y = 20;
    private static final int SLIDER_BOTTOM_SPACING = 16;
    private static final int THUMB_RADIUS = 7;
    private static final int SLIDER_CORNER_RADIUS = 6;

    /**
     * Creates the environment panel bound to the given simulation engine.
     *
     * @param engine the simulation engine whose environment settings are controlled
     */
    public EnvironmentPanel(SimulationEngine engine) {
        this.engine = engine;
        setPreferredSize(new Dimension(PANEL_WIDTH, 310));
        setBackground(new Color(0, 0, 0, 0));
        setOpaque(false);

        // Initialize slider bar rectangles
        for (int i = 0; i < 3; i++) {
            sliderBars[i] = new Rectangle();
        }

        // Mouse interaction for custom sliders
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                for (int i = 0; i < 3; i++) {
                    // Expand hit area vertically for easier clicking
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
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingSlider >= 0) {
                    updateSliderValue(draggingSlider, e.getX());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggingSlider = -1;
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    /**
     * Updates the slider value and propagates to the engine.
     * Called from EDT (mouse events). Thread-safe because:
     * - Environment uses synchronized getters/setters
     * - foodSpawnRate is volatile in SimulationEngine
     */
    private void updateSliderValue(int sliderIndex, int mouseX) {
        Rectangle bar = sliderBars[sliderIndex];
        if (bar.width <= 0) return;

        double value = (double)(mouseX - bar.x) / bar.width;
        value = Math.max(0.0, Math.min(1.0, value));

        switch (sliderIndex) {
            case 0 -> engine.getEnvironment().setTemperature(value);
            case 1 -> engine.getEnvironment().setToxicity(value);
            case 2 -> engine.setFoodSpawnRate(value);
            default -> {
            }
        }
        repaint();
    }

    private double getSliderValue(int index) {
        return switch (index) {
            case 0 -> engine.getEnvironment().getTemperature();
            case 1 -> engine.getEnvironment().getToxicity();
            case 2 -> engine.getFoodSpawnRate();
            default -> 0;
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        try {

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int panelW = getWidth() - 2 * MARGIN;
        int panelH = getHeight() - 2 * MARGIN;

        // Draw background panel
        g2d.setColor(BG_COLOR);
        g2d.fillRoundRect(MARGIN, MARGIN, panelW, panelH, 12, 12);

        // Draw glowing border (multi-layer)
            g2d.setColor(BORDER_GLOW_COLOR);
            g2d.setStroke(STROKE_3);
        g2d.drawRoundRect(MARGIN - 1, MARGIN - 1, panelW + 2, panelH + 2, 12, 12);

        g2d.setColor(ACCENT_COLOR);
            g2d.setStroke(STROKE_1_5);
        g2d.drawRoundRect(MARGIN, MARGIN, panelW, panelH, 12, 12);

        // Draw content
        int x = MARGIN + CONTENT_PADDING;
        int y = MARGIN + CONTENT_PADDING;
        int contentWidth = panelW - 2 * CONTENT_PADDING;

        // Title
        g2d.setFont(TITLE_FONT);
        g2d.setColor(ACCENT_COLOR);
        g2d.drawString("ENVIRONMENT SETTINGS", x, y + 14);
        y += 22;

        // Separator line
            g2d.setColor(SEPARATOR_COLOR);
            g2d.setStroke(STROKE_1_5);
        g2d.drawLine(x, y + 3, x + contentWidth, y + 3);
        y += 14;

        // Draw 3 slider sections
        for (int i = 0; i < 3; i++) {
            y = drawSliderSection(g2d, x, y, contentWidth, i);
        }

        } finally {
            g2d.dispose();
        }
    }

    private int drawSliderSection(Graphics2D g2d, int x, int y, int contentWidth, int index) {
        Color color = SLIDER_COLORS[index];
        double value = getSliderValue(index);

        // Section label with triangle icon
        g2d.setFont(LABEL_FONT);
        g2d.setColor(color);

        // Triangle icon
        int[] xPoints = {x, x + TRIANGLE_WIDTH, x};
        int[] yPoints = {y, y + TRIANGLE_HEIGHT / 2, y + TRIANGLE_HEIGHT};
        g2d.fillPolygon(xPoints, yPoints, 3);

        g2d.drawString(SLIDER_LABELS[index], x + LABEL_OFFSET_X, y + 10);
        y += LABEL_SPACING_Y;

        // Value display
        g2d.setFont(VALUE_FONT);
        g2d.setColor(ACCENT_COLOR);
        String valueText = String.format("%.0f%%", value * 100);
        g2d.drawString(valueText, x, y + 12);
        y += VALUE_SPACING_Y;

        // Interactive slider bar
        int barY = y;

        // Store bar position for mouse interaction
        sliderBars[index].setBounds(x, barY, contentWidth, BAR_HEIGHT);

        // Bar background
        g2d.setColor(BAR_BG_COLOR);
        g2d.fillRoundRect(x, barY, contentWidth, BAR_HEIGHT, SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);

        // Bar fill with glow
        int fillWidth = (int) (contentWidth * value);
        if (fillWidth > 0) {
            // Glow effect
            g2d.setColor(SLIDER_GLOW_COLORS[index]);
            g2d.fillRoundRect(x - 1, barY - 1, fillWidth + 2, BAR_HEIGHT + 2, SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);

            // Main fill
            g2d.setColor(color);
            g2d.fillRoundRect(x, barY, fillWidth, BAR_HEIGHT, SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);
        }

        // Border
        g2d.setColor(color);
        g2d.setStroke(STROKE_1);
        g2d.drawRoundRect(x, barY, contentWidth, BAR_HEIGHT, SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);

        // Draw slider thumb/handle at current position
        int thumbX = x + fillWidth;
        // Thumb glow
        g2d.setColor(SLIDER_THUMB_GLOW_COLORS[index]);
        g2d.fillOval(thumbX - THUMB_RADIUS - 1, barY + BAR_HEIGHT / 2 - THUMB_RADIUS - 1,
                (THUMB_RADIUS + 1) * 2, (THUMB_RADIUS + 1) * 2);
        // Thumb main
        g2d.setColor(BG_COLOR);
        g2d.fillOval(thumbX - THUMB_RADIUS + 1, barY + BAR_HEIGHT / 2 - THUMB_RADIUS + 1,
                (THUMB_RADIUS - 1) * 2, (THUMB_RADIUS - 1) * 2);
        // Thumb border
        g2d.setColor(color);
        g2d.setStroke(STROKE_1_5);
        g2d.drawOval(thumbX - THUMB_RADIUS + 1, barY + BAR_HEIGHT / 2 - THUMB_RADIUS + 1,
                (THUMB_RADIUS - 1) * 2, (THUMB_RADIUS - 1) * 2);

        y += BAR_HEIGHT + SLIDER_BOTTOM_SPACING;
        return y;
    }
}
