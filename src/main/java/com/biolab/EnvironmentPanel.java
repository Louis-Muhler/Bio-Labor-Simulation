package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Environment Panel - Schwebendes Overlay-Panel im Stil des InspectorPanel
 * mit interaktiven Custom Slidern für Temperature, Toxicity, Food Spawn Rate.
 * Slider können geklickt und gezogen werden.
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

    private static final String[] SLIDER_LABELS = {
        "Temperature", "Toxicity", "Food Spawn Rate"
    };

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

    private void updateSliderValue(int sliderIndex, int mouseX) {
        Rectangle bar = sliderBars[sliderIndex];
        if (bar.width <= 0) return;

        double value = (double)(mouseX - bar.x) / bar.width;
        value = Math.max(0.0, Math.min(1.0, value));

        switch (sliderIndex) {
            case 0:
                engine.getEnvironment().setTemperature(value);
                break;
            case 1:
                engine.getEnvironment().setToxicity(value);
                break;
            case 2:
                engine.setFoodSpawnRate(value);
                break;
        }
        repaint();
    }

    private double getSliderValue(int index) {
        switch (index) {
            case 0: return engine.getEnvironment().getTemperature();
            case 1: return engine.getEnvironment().getToxicity();
            case 2: return engine.getFoodSpawnRate();
            default: return 0;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int panelW = getWidth() - 2 * MARGIN;
        int panelH = getHeight() - 2 * MARGIN;

        // Draw background panel
        g2d.setColor(BG_COLOR);
        g2d.fillRoundRect(MARGIN, MARGIN, panelW, panelH, 12, 12);

        // Draw glowing border (multi-layer)
        g2d.setColor(new Color(ACCENT_COLOR.getRed(), ACCENT_COLOR.getGreen(), ACCENT_COLOR.getBlue(), 60));
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRoundRect(MARGIN - 1, MARGIN - 1, panelW + 2, panelH + 2, 12, 12);

        g2d.setColor(ACCENT_COLOR);
        g2d.setStroke(new BasicStroke(1.5f));
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
        g2d.setColor(new Color(ACCENT_COLOR.getRed(), ACCENT_COLOR.getGreen(), ACCENT_COLOR.getBlue(), 100));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawLine(x, y + 3, x + contentWidth, y + 3);
        y += 14;

        // Draw 3 slider sections
        for (int i = 0; i < 3; i++) {
            y = drawSliderSection(g2d, x, y, contentWidth, i);
        }
    }

    private int drawSliderSection(Graphics2D g2d, int x, int y, int contentWidth, int index) {
        Color color = SLIDER_COLORS[index];
        double value = getSliderValue(index);

        // Section label with triangle icon
        g2d.setFont(LABEL_FONT);
        g2d.setColor(color);

        // Triangle icon
        int[] xPoints = {x, x + 7, x};
        int[] yPoints = {y, y + 5, y + 10};
        g2d.fillPolygon(xPoints, yPoints, 3);

        g2d.drawString(SLIDER_LABELS[index], x + 12, y + 10);
        y += 18;

        // Value display
        g2d.setFont(VALUE_FONT);
        g2d.setColor(ACCENT_COLOR);
        String valueText = String.format("%.0f%%", value * 100);
        g2d.drawString(valueText, x, y + 12);
        y += 20;

        // Interactive slider bar
        int barWidth = contentWidth;
        int barY = y;

        // Store bar position for mouse interaction
        sliderBars[index].setBounds(x, barY, barWidth, BAR_HEIGHT);

        // Bar background
        g2d.setColor(new Color(40, 40, 50));
        g2d.fillRoundRect(x, barY, barWidth, BAR_HEIGHT, 6, 6);

        // Bar fill with glow
        int fillWidth = (int) (barWidth * value);
        if (fillWidth > 0) {
            // Glow effect
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
            g2d.fillRoundRect(x - 1, barY - 1, fillWidth + 2, BAR_HEIGHT + 2, 6, 6);

            // Main fill
            g2d.setColor(color);
            g2d.fillRoundRect(x, barY, fillWidth, BAR_HEIGHT, 6, 6);
        }

        // Border
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRoundRect(x, barY, barWidth, BAR_HEIGHT, 6, 6);

        // Draw slider thumb/handle at current position
        int thumbX = x + fillWidth;
        int thumbRadius = 7;
        // Thumb glow
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 80));
        g2d.fillOval(thumbX - thumbRadius - 1, barY + BAR_HEIGHT / 2 - thumbRadius - 1,
            (thumbRadius + 1) * 2, (thumbRadius + 1) * 2);
        // Thumb main
        g2d.setColor(BG_COLOR);
        g2d.fillOval(thumbX - thumbRadius + 1, barY + BAR_HEIGHT / 2 - thumbRadius + 1,
            (thumbRadius - 1) * 2, (thumbRadius - 1) * 2);
        // Thumb border
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawOval(thumbX - thumbRadius + 1, barY + BAR_HEIGHT / 2 - thumbRadius + 1,
            (thumbRadius - 1) * 2, (thumbRadius - 1) * 2);

        y += BAR_HEIGHT + 16;
        return y;
    }
}
