package com.biolab;

import javax.swing.*;
import java.awt.*;

/**
 * Environment Panel - Schwebendes Overlay-Panel im Stil des InspectorPanel
 * mit Environment Settings (Temperature, Toxicity, Food Spawn Rate)
 */
public class EnvironmentPanel extends JPanel {
    private final SimulationEngine engine;
    private JSlider temperatureSlider;
    private JSlider toxicitySlider;
    private JSlider foodSpawnSlider;

    private static final int PANEL_WIDTH = 350;
    private static final int MARGIN = 20;
    private static final int CONTENT_PADDING = 20;
    private static final Color BG_COLOR = new Color(18, 18, 18, 240); // Semi-transparent dark
    private static final Color ACCENT_COLOR = new Color(0, 255, 255); // Cyan neon
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.BOLD, 13);

    public EnvironmentPanel(SimulationEngine engine) {
        this.engine = engine;
        setPreferredSize(new Dimension(PANEL_WIDTH, 600));
        setBackground(new Color(0, 0, 0, 0)); // Transparent for custom painting
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Calculate dynamic panel height
        int contentHeight = MARGIN + CONTENT_PADDING;
        contentHeight += 30; // Title
        contentHeight += 25; // Separator + spacing
        contentHeight += (3 * 100); // 3 Slider sections
        contentHeight += CONTENT_PADDING + MARGIN;

        int panelHeight = Math.min(contentHeight, getHeight());

        // Draw background panel
        g2d.setColor(BG_COLOR);
        g2d.fillRoundRect(MARGIN, MARGIN, PANEL_WIDTH - MARGIN, panelHeight - 2 * MARGIN, 12, 12);

        // Draw glowing border (multi-layer)
        g2d.setColor(new Color(ACCENT_COLOR.getRed(), ACCENT_COLOR.getGreen(), ACCENT_COLOR.getBlue(), 60));
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRoundRect(MARGIN - 1, MARGIN - 1, PANEL_WIDTH - MARGIN + 2, panelHeight - 2 * MARGIN + 2, 12, 12);

        g2d.setColor(ACCENT_COLOR);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(MARGIN, MARGIN, PANEL_WIDTH - MARGIN, panelHeight - 2 * MARGIN, 12, 12);

        // Draw content
        int y = MARGIN + CONTENT_PADDING;

        // Title
        g2d.setFont(TITLE_FONT);
        g2d.setColor(ACCENT_COLOR);
        g2d.drawString("ENVIRONMENT SETTINGS", MARGIN + CONTENT_PADDING, y + 20);
        y += 30;

        // Separator line
        g2d.setColor(new Color(ACCENT_COLOR.getRed(), ACCENT_COLOR.getGreen(), ACCENT_COLOR.getBlue(), 100));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(MARGIN + CONTENT_PADDING, y + 5, PANEL_WIDTH - CONTENT_PADDING, y + 5);
        y += 25;

        // Temperature Section
        y = drawSliderSection(g2d, y, "Temperature", new Color(255, 100, 100),
            engine.getEnvironment().getTemperature() * 100);

        // Toxicity Section
        y = drawSliderSection(g2d, y, "Toxicity", new Color(100, 255, 100),
            engine.getEnvironment().getToxicity() * 100);

        // Food Spawn Rate Section
        drawSliderSection(g2d, y, "Food Spawn Rate", new Color(100, 150, 255),
            engine.getFoodSpawnRate() * 100);
    }

    private int drawSliderSection(Graphics2D g2d, int y, String title, Color color, double value) {
        // Section label with icon
        g2d.setFont(LABEL_FONT);
        g2d.setColor(color);

        // Draw triangle icon
        int[] xPoints = {MARGIN + CONTENT_PADDING, MARGIN + CONTENT_PADDING + 8, MARGIN + CONTENT_PADDING};
        int[] yPoints = {y, y + 7, y + 14};
        g2d.fillPolygon(xPoints, yPoints, 3);

        g2d.drawString(title, MARGIN + CONTENT_PADDING + 15, y + 12);
        y += 25;

        // Value display
        g2d.setFont(new Font("Consolas", Font.BOLD, 18));
        g2d.setColor(ACCENT_COLOR);
        String valueText = String.format("%.0f%%", value);
        g2d.drawString(valueText, MARGIN + CONTENT_PADDING, y + 15);
        y += 30;

        // Progress bar background
        int barWidth = PANEL_WIDTH - MARGIN - 2 * CONTENT_PADDING;
        int barHeight = 10;
        g2d.setColor(new Color(40, 40, 50));
        g2d.fillRoundRect(MARGIN + CONTENT_PADDING, y, barWidth, barHeight, 5, 5);

        // Progress bar fill with glow
        int fillWidth = (int) (barWidth * (value / 100.0));
        if (fillWidth > 0) {
            // Glow effect
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
            g2d.fillRoundRect(MARGIN + CONTENT_PADDING - 1, y - 1, fillWidth + 2, barHeight + 2, 5, 5);

            // Main fill
            g2d.setColor(color);
            g2d.fillRoundRect(MARGIN + CONTENT_PADDING, y, fillWidth, barHeight, 5, 5);
        }

        // Border
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRoundRect(MARGIN + CONTENT_PADDING, y, barWidth, barHeight, 5, 5);

        y += 30;
        return y;
    }

    /**
     * Creates an invisible overlay panel for capturing slider interactions
     */
    public JPanel createInteractionOverlay() {
        JPanel overlay = new JPanel();
        overlay.setLayout(null);
        overlay.setOpaque(false);
        overlay.setBackground(new Color(0, 0, 0, 0));

        int baseY = MARGIN + CONTENT_PADDING + 30 + 25; // After title and separator

        // Temperature slider
        temperatureSlider = createSlider((int)(engine.getEnvironment().getTemperature() * 100));
        temperatureSlider.setBounds(MARGIN + CONTENT_PADDING, baseY + 55,
            PANEL_WIDTH - MARGIN - 2 * CONTENT_PADDING, 20);
        temperatureSlider.addChangeListener(e -> {
            engine.getEnvironment().setTemperature(temperatureSlider.getValue() / 100.0);
            repaint();
        });
        overlay.add(temperatureSlider);

        // Toxicity slider
        toxicitySlider = createSlider((int)(engine.getEnvironment().getToxicity() * 100));
        toxicitySlider.setBounds(MARGIN + CONTENT_PADDING, baseY + 155,
            PANEL_WIDTH - MARGIN - 2 * CONTENT_PADDING, 20);
        toxicitySlider.addChangeListener(e -> {
            engine.getEnvironment().setToxicity(toxicitySlider.getValue() / 100.0);
            repaint();
        });
        overlay.add(toxicitySlider);

        // Food spawn rate slider
        foodSpawnSlider = createSlider((int)(engine.getFoodSpawnRate() * 100));
        foodSpawnSlider.setBounds(MARGIN + CONTENT_PADDING, baseY + 255,
            PANEL_WIDTH - MARGIN - 2 * CONTENT_PADDING, 20);
        foodSpawnSlider.addChangeListener(e -> {
            engine.setFoodSpawnRate(foodSpawnSlider.getValue() / 100.0);
            repaint();
        });
        overlay.add(foodSpawnSlider);

        return overlay;
    }

    private JSlider createSlider(int initialValue) {
        JSlider slider = new JSlider(0, 100, initialValue);
        slider.setOpaque(false);
        slider.setForeground(TEXT_COLOR);
        slider.setBackground(new Color(0, 0, 0, 0));
        return slider;
    }
}

