package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * Inspector Panel displays detailed information about selected microbes,
 * including vital stats, genetic profile, and ancestry evolution chart.
 */
public class InspectorPanel extends JPanel {
    private Microbe selectedMicrobe;
    private static final int PANEL_WIDTH = 320;
    private static final int MARGIN = 20; // Consistent margin for top and right
    private static final int CONTENT_PADDING = 20; // Internal padding
    private static final Color BG_COLOR = new Color(18, 18, 18, 240); // Semi-transparent dark
    private static final Color ACCENT_COLOR = new Color(0, 255, 255); // Cyan neon
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color GRID_COLOR = new Color(40, 40, 50);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font MONO_FONT = new Font("Consolas", Font.PLAIN, 11);

    // Pre-allocated rendering constants
    private static final Color BORDER_GLOW_COLOR = new Color(0, 255, 255, 80);
    private static final Color NO_SELECTION_BG = new Color(18, 18, 18, 150);
    private static final Color NO_SELECTION_BORDER = new Color(40, 40, 50, 200);
    private static final Color NO_SELECTION_TEXT = new Color(220, 220, 220, 180);
    private static final Color CHART_BG_COLOR = new Color(0, 0, 0, 100);
    private static final Color CHART_HEAT_COLOR = new Color(255, 100, 100);
    private static final Color CHART_TOXIN_COLOR = new Color(100, 255, 100);
    private static final Color CHART_SPEED_COLOR = new Color(100, 150, 255);
    private static final BasicStroke STROKE_1 = new BasicStroke(1);
    private static final BasicStroke STROKE_2 = new BasicStroke(2);
    private static final BasicStroke STROKE_3 = new BasicStroke(3);

    // Layout spacing constants
    private static final int TITLE_HEIGHT = 30;
    private static final int SEPARATOR_HEIGHT = 25;
    private static final int SECTION_TITLE_HEIGHT = 20;
    private static final int STAT_LINE_HEIGHT = 20;
    private static final int SECTION_SPACING = 15;
    private static final int ANCESTRY_CHART_HEIGHT = 120;
    private static final int ANCESTRY_LEGEND_HEIGHT = 30;
    private static final int ANCESTRY_SPACING = 30;
    private static final int NO_ANCESTRY_SPACING = 20;
    private static final int COLOR_INDICATOR_HEIGHT = 70;
    private static final int CORNER_RADIUS = 15;
    private static final int TRIANGLE_X = MARGIN + CONTENT_PADDING + 5;

    /**
     * Creates the inspector panel with a fixed width and transparent background.
     */
    public InspectorPanel() {
        setPreferredSize(new Dimension(PANEL_WIDTH, 600));
        setBackground(new Color(0, 0, 0, 0)); // Transparent for custom painting
        setOpaque(false);
    }

    /**
     * Hides the inspector panel and clears any rendering artifacts.
     */
    public void hidePanel() {
        this.selectedMicrobe = null;
        setVisible(false);
        // Request parent to repaint to clear ghost borders
        if (getParent() != null) {
            getParent().repaint();
        }
    }


    /**
     * Sets the microbe to display and triggers a repaint.
     */
    public void setSelectedMicrobe(Microbe microbe) {
        this.selectedMicrobe = microbe;
        repaint();
    }

    /**
     * Makes the inspector panel visible and repaints it.
     */
    public void showPanel() {
        setVisible(true);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Local copy – microbe can become dead mid-render from engine thread
            Microbe microbe = this.selectedMicrobe;
            if (microbe == null || microbe.isDead()) {
                drawNoSelection(g2d);
                return;
            }

        // Calculate dynamic panel height based on content
        int y = MARGIN + CONTENT_PADDING;
            int contentHeight = y;

            contentHeight += TITLE_HEIGHT;
            contentHeight += SEPARATOR_HEIGHT;

            // Vital Signs section
            contentHeight += SECTION_TITLE_HEIGHT + (3 * STAT_LINE_HEIGHT);
            contentHeight += SECTION_SPACING;

        // Genetic Profile section
            contentHeight += SECTION_TITLE_HEIGHT + (3 * STAT_LINE_HEIGHT);
            contentHeight += SECTION_SPACING;

        // Ancestry section if exists
            List<AncestorSnapshot> ancestry = microbe.getAncestry();
        if (!ancestry.isEmpty()) {
            contentHeight += SECTION_TITLE_HEIGHT + SEPARATOR_HEIGHT + ANCESTRY_CHART_HEIGHT + ANCESTRY_LEGEND_HEIGHT;
            contentHeight += ANCESTRY_SPACING;
        } else {
            contentHeight += NO_ANCESTRY_SPACING;
        }

            // Color indicator
            contentHeight += COLOR_INDICATOR_HEIGHT;
            contentHeight += CONTENT_PADDING + MARGIN;

        // Draw background panel with dynamic height
        int panelHeight = contentHeight;
            g2d.setColor(BG_COLOR);
            g2d.fillRoundRect(MARGIN, MARGIN, PANEL_WIDTH - (2 * MARGIN), panelHeight, CORNER_RADIUS, CORNER_RADIUS);

        // Border with glow effect
            g2d.setColor(BORDER_GLOW_COLOR);
            g2d.setStroke(STROKE_3);
            g2d.drawRoundRect(MARGIN, MARGIN, PANEL_WIDTH - (2 * MARGIN), panelHeight, CORNER_RADIUS, CORNER_RADIUS);
        g2d.setColor(ACCENT_COLOR);
            g2d.setStroke(STROKE_1);
            g2d.drawRoundRect(MARGIN, MARGIN, PANEL_WIDTH - (2 * MARGIN), panelHeight, CORNER_RADIUS, CORNER_RADIUS);

        y = MARGIN + CONTENT_PADDING + 20;

        g2d.setColor(ACCENT_COLOR);
        g2d.setFont(TITLE_FONT);
        drawCenteredString(g2d, "SPECIMEN ANALYSIS", PANEL_WIDTH / 2, y);
        y += 30;

        g2d.setColor(GRID_COLOR);
        g2d.fillRect(MARGIN + CONTENT_PADDING, y, PANEL_WIDTH - (2 * MARGIN) - (2 * CONTENT_PADDING), 2);
        y += 20;

        y = drawSection(g2d, y, "VITAL SIGNS", new String[]{
                String.format("Health: %.1f%%", (microbe.getHealth() / Microbe.getMaxHealth()) * 100),
                String.format("Energy: %.1f%%", (microbe.getEnergy() / Microbe.getMaxEnergy()) * 100),
                String.format("Age: %d cycles", microbe.getAge())
        });
        y += 20;

        y = drawSection(g2d, y, "GENETIC PROFILE", new String[]{
                String.format("Heat Resistance: %.2f", microbe.getHeatResistance()),
                String.format("Toxin Resistance: %.2f", microbe.getToxinResistance()),
                String.format("Speed Factor: %.2f", microbe.getSpeed())
        });
        y += 20;

        if (!ancestry.isEmpty()) {
            y = drawAncestrySection(g2d, y, ancestry, microbe);
            y += 40; // Extra spacing after ancestry chart
        } else {
            y += 25; // Extra spacing when no ancestry
        }

            drawColorIndicator(g2d, y, microbe);
        } finally {
            g2d.dispose();
        }
    }

    private void drawNoSelection(Graphics2D g2d) {
        int panelHeight = 150;

        g2d.setColor(NO_SELECTION_BG);
        g2d.fillRoundRect(MARGIN, MARGIN, PANEL_WIDTH - (2 * MARGIN), panelHeight, 15, 15);

        g2d.setColor(NO_SELECTION_BORDER);
        g2d.setStroke(STROKE_2);
        g2d.drawRoundRect(MARGIN, MARGIN, PANEL_WIDTH - (2 * MARGIN), panelHeight, 15, 15);

        g2d.setColor(NO_SELECTION_TEXT);
        g2d.setFont(LABEL_FONT);
        drawCenteredString(g2d, "No specimen selected", PANEL_WIDTH / 2, MARGIN + 60);
        g2d.setFont(MONO_FONT);
        drawCenteredString(g2d, "Click on a microbe to inspect", PANEL_WIDTH / 2, MARGIN + 85);
    }

    private int drawSection(Graphics2D g2d, int y, String title, String[] lines) {
        g2d.setColor(ACCENT_COLOR);
        g2d.setFont(LABEL_FONT);

        // Draw simple triangle icon instead of Unicode
        drawTriangleIcon(g2d, y - 8);
        g2d.drawString(title, MARGIN + CONTENT_PADDING + 20, y);
        y += 20;

        g2d.setColor(TEXT_COLOR);
        g2d.setFont(MONO_FONT);
        for (String line : lines) {
            g2d.drawString(line, MARGIN + CONTENT_PADDING + 35, y);
            y += 20;
        }

        return y;
    }

    /**
     * Draws a simple triangle icon using Graphics2D instead of Unicode.
     */
    private void drawTriangleIcon(Graphics2D g2d, int y) {
        int[] xPoints = {TRIANGLE_X, TRIANGLE_X + 8, TRIANGLE_X};
        int[] yPoints = {y, y + 4, y + 8};
        g2d.fillPolygon(xPoints, yPoints, 3);
    }

    private int drawAncestrySection(Graphics2D g2d, int y, List<AncestorSnapshot> ancestry, Microbe microbe) {
        g2d.setColor(ACCENT_COLOR);
        g2d.setFont(LABEL_FONT);

        // Draw triangle icon instead of Unicode
        drawTriangleIcon(g2d, y - 8);
        g2d.drawString("LINEAGE EVOLUTION", MARGIN + CONTENT_PADDING + 20, y);
        y += 25;

        int chartX = MARGIN + CONTENT_PADDING + 10;
        int chartY = y;
        int chartWidth = PANEL_WIDTH - (2 * MARGIN) - (2 * CONTENT_PADDING) - 20;
        int chartHeight = 120;

        g2d.setColor(CHART_BG_COLOR);
        g2d.fillRect(chartX, chartY, chartWidth, chartHeight);

        g2d.setColor(GRID_COLOR);
        g2d.setStroke(STROKE_1);
        for (int i = 0; i <= 4; i++) {
            int gridY = chartY + (chartHeight * i / 4);
            g2d.drawLine(chartX, gridY, chartX + chartWidth, gridY);
        }

        g2d.setColor(GRID_COLOR);
        g2d.drawRect(chartX, chartY, chartWidth, chartHeight);

        // Build data for evolution chart (current microbe + ancestors)
        List<DataPoint> heatData = new java.util.ArrayList<>();
        List<DataPoint> toxinData = new java.util.ArrayList<>();
        List<DataPoint> speedData = new java.util.ArrayList<>();

        heatData.add(new DataPoint(0, microbe.getHeatResistance()));
        toxinData.add(new DataPoint(0, microbe.getToxinResistance()));
        speedData.add(new DataPoint(0, microbe.getSpeed()));

        for (AncestorSnapshot ancestor : ancestry) {
            int gen = ancestor.generation() + 1;
            heatData.add(new DataPoint(gen, ancestor.heatResistance()));
            toxinData.add(new DataPoint(gen, ancestor.toxinResistance()));
            speedData.add(new DataPoint(gen, ancestor.speed()));
        }

        heatData.sort((a, b) -> Integer.compare(b.generation, a.generation));
        toxinData.sort((a, b) -> Integer.compare(b.generation, a.generation));
        speedData.sort((a, b) -> Integer.compare(b.generation, a.generation));

        drawLineChart(g2d, heatData, chartX, chartY, chartWidth, chartHeight, CHART_HEAT_COLOR);
        drawLineChart(g2d, toxinData, chartX, chartY, chartWidth, chartHeight, CHART_TOXIN_COLOR);
        drawLineChart(g2d, speedData, chartX, chartY, chartWidth, chartHeight, CHART_SPEED_COLOR);

        y = chartY + chartHeight + 20;
        g2d.setFont(MONO_FONT);
        int legendX = MARGIN + CONTENT_PADDING + 15;
        drawLegendItem(g2d, legendX, y, CHART_HEAT_COLOR, "Heat Res");
        drawLegendItem(g2d, legendX + 85, y, CHART_TOXIN_COLOR, "Toxin Res");
        drawLegendItem(g2d, legendX + 175, y, CHART_SPEED_COLOR, "Speed");

        return y + 10;
    }

    private void drawLineChart(Graphics2D g2d, List<DataPoint> data, int x, int y, int width, int height, Color color) {
        if (data.size() < 2) return;

        Path2D path = new Path2D.Double();
        boolean first = true;

        for (int i = 0; i < data.size(); i++) {
            DataPoint point = data.get(i);
            double px = x + (width * i / (double) (data.size() - 1));
            double py = y + height - (point.value() * height);

            if (first) {
                path.moveTo(px, py);
                first = false;
            } else {
                path.lineTo(px, py);
            }
        }

        // Use AlphaComposite for glow effect
        java.awt.Composite originalComposite = g2d.getComposite();

        // Glow pass
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 100 / 255f));
        g2d.setColor(color);
        g2d.setStroke(STROKE_3);
        g2d.draw(path);

        // Solid pass
        g2d.setComposite(originalComposite);
        g2d.setColor(color);
        g2d.setStroke(STROKE_2);
        g2d.draw(path);

        // Data points
        for (int i = 0; i < data.size(); i++) {
            DataPoint point = data.get(i);
            double px = x + (width * i / (double) (data.size() - 1));
            double py = y + height - (point.value() * height);

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 150 / 255f));
            g2d.setColor(color);
            g2d.fillOval((int) px - 4, (int) py - 4, 8, 8);
            g2d.setComposite(originalComposite);
            g2d.setColor(color);
            g2d.fillOval((int) px - 3, (int) py - 3, 6, 6);
        }
    }

    private void drawLegendItem(Graphics2D g2d, int x, int y, Color color, String label) {
        g2d.setColor(color);
        g2d.fillRect(x, y - 6, 12, 3);
        g2d.setColor(TEXT_COLOR);
        g2d.drawString(label, x + 16, y);
    }

    private void drawColorIndicator(Graphics2D g2d, int y, Microbe microbe) {
        int centerX = PANEL_WIDTH / 2;

        g2d.setColor(ACCENT_COLOR);
        g2d.setFont(LABEL_FONT);
        drawCenteredString(g2d, "COLOR CODE", centerX, y);
        y += 35;

        Color microbeColor = microbe.getColor();

        // Glow effect using AlphaComposite
        java.awt.Composite originalComposite = g2d.getComposite();
        g2d.setColor(microbeColor);
        for (int i = 3; i > 0; i--) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 30 / 255f));
            g2d.fillOval(centerX - 15 - i * 2, y - 15 - i * 2, 30 + i * 4, 30 + i * 4);
        }
        g2d.setComposite(originalComposite);

        // Draw main circle
        g2d.setColor(microbeColor);
        g2d.fillOval(centerX - 15, y - 15, 30, 30);
    }

    private void drawCenteredString(Graphics2D g2d, String text, int x, int y) {
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, x - textWidth / 2, y);
    }

    private record DataPoint(int generation, double value) {
    }
}

