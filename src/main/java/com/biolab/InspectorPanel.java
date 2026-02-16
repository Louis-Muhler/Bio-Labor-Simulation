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
    private static final Color BG_COLOR = new Color(18, 18, 18, 240); // Semi-transparent dark
    private static final Color ACCENT_COLOR = new Color(0, 255, 255); // Cyan neon
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color GRID_COLOR = new Color(40, 40, 50);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font MONO_FONT = new Font("Consolas", Font.PLAIN, 11);

    public InspectorPanel() {
        setPreferredSize(new Dimension(PANEL_WIDTH, 600));
        setBackground(new Color(0, 0, 0, 0)); // Transparent for custom painting
        setOpaque(false);
    }

    public void setSelectedMicrobe(Microbe microbe) {
        this.selectedMicrobe = microbe;
        repaint();
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
     * Shows the inspector panel.
     */
    public void showPanel() {
        setVisible(true);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (selectedMicrobe == null || selectedMicrobe.isDead()) {
            drawNoSelection(g2d);
            return;
        }

        // Background panel
        g2d.setColor(BG_COLOR);
        g2d.fillRoundRect(10, 10, PANEL_WIDTH - 20, getHeight() - 20, 15, 15);

        // Border with glow effect
        g2d.setColor(new Color(ACCENT_COLOR.getRed(), ACCENT_COLOR.getGreen(), ACCENT_COLOR.getBlue(), 80));
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRoundRect(10, 10, PANEL_WIDTH - 20, getHeight() - 20, 15, 15);
        g2d.setColor(ACCENT_COLOR);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRoundRect(10, 10, PANEL_WIDTH - 20, getHeight() - 20, 15, 15);

        int y = 40;

        g2d.setColor(ACCENT_COLOR);
        g2d.setFont(TITLE_FONT);
        drawCenteredString(g2d, "SPECIMEN ANALYSIS", PANEL_WIDTH / 2, y);
        y += 30;

        g2d.setColor(GRID_COLOR);
        g2d.fillRect(30, y, PANEL_WIDTH - 60, 2);
        y += 20;

        drawSection(g2d, y, "VITAL SIGNS", new String[]{
            String.format("Health: %.1f%%", (selectedMicrobe.getHealth() / Microbe.getMaxHealth()) * 100),
            String.format("Energy: %.1f%%", (selectedMicrobe.getEnergy() / Microbe.getMaxEnergy()) * 100),
            String.format("Age: %d cycles", selectedMicrobe.getAge())
        });
        y += 110;

        drawSection(g2d, y, "GENETIC PROFILE", new String[]{
            String.format("Heat Resistance: %.2f", selectedMicrobe.getHeatResistance()),
            String.format("Toxin Resistance: %.2f", selectedMicrobe.getToxinResistance()),
            String.format("Speed Factor: %.2f", selectedMicrobe.getSpeed())
        });
        y += 110;

        List<AncestorSnapshot> ancestry = selectedMicrobe.getAncestry();
        if (!ancestry.isEmpty()) {
            drawAncestrySection(g2d, y, ancestry);
            y += 200;
        }

        drawColorIndicator(g2d, y);
    }

    private void drawNoSelection(Graphics2D g2d) {
        g2d.setColor(new Color(BG_COLOR.getRed(), BG_COLOR.getGreen(), BG_COLOR.getBlue(), 150));
        g2d.fillRoundRect(10, 10, PANEL_WIDTH - 20, 150, 15, 15);

        g2d.setColor(new Color(GRID_COLOR.getRed(), GRID_COLOR.getGreen(), GRID_COLOR.getBlue(), 200));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(10, 10, PANEL_WIDTH - 20, 150, 15, 15);

        g2d.setColor(new Color(TEXT_COLOR.getRed(), TEXT_COLOR.getGreen(), TEXT_COLOR.getBlue(), 180));
        g2d.setFont(LABEL_FONT);
        drawCenteredString(g2d, "No specimen selected", PANEL_WIDTH / 2, 70);
        g2d.setFont(MONO_FONT);
        drawCenteredString(g2d, "Click on a microbe to inspect", PANEL_WIDTH / 2, 95);
    }

    private void drawSection(Graphics2D g2d, int y, String title, String[] lines) {
        g2d.setColor(ACCENT_COLOR);
        g2d.setFont(LABEL_FONT);
        g2d.drawString("\u25B8 " + title, 30, y);
        y += 20;

        g2d.setColor(TEXT_COLOR);
        g2d.setFont(MONO_FONT);
        for (String line : lines) {
            g2d.drawString(line, 45, y);
            y += 18;
        }
    }

    private void drawAncestrySection(Graphics2D g2d, int y, List<AncestorSnapshot> ancestry) {
        g2d.setColor(ACCENT_COLOR);
        g2d.setFont(LABEL_FONT);
        g2d.drawString("\u25B8 LINEAGE EVOLUTION", 30, y);
        y += 25;

        int chartX = 40;
        int chartY = y;
        int chartWidth = PANEL_WIDTH - 80;
        int chartHeight = 120;

        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.fillRect(chartX, chartY, chartWidth, chartHeight);

        g2d.setColor(GRID_COLOR);
        g2d.setStroke(new BasicStroke(1));
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

        heatData.add(new DataPoint(0, selectedMicrobe.getHeatResistance()));
        toxinData.add(new DataPoint(0, selectedMicrobe.getToxinResistance()));
        speedData.add(new DataPoint(0, selectedMicrobe.getSpeed()));

        for (AncestorSnapshot ancestor : ancestry) {
            int gen = ancestor.getGeneration() + 1;
            heatData.add(new DataPoint(gen, ancestor.getHeatResistance()));
            toxinData.add(new DataPoint(gen, ancestor.getToxinResistance()));
            speedData.add(new DataPoint(gen, ancestor.getSpeed()));
        }

        heatData.sort((a, b) -> Integer.compare(b.generation, a.generation));
        toxinData.sort((a, b) -> Integer.compare(b.generation, a.generation));
        speedData.sort((a, b) -> Integer.compare(b.generation, a.generation));

        drawLineChart(g2d, heatData, chartX, chartY, chartWidth, chartHeight, new Color(255, 100, 100));
        drawLineChart(g2d, toxinData, chartX, chartY, chartWidth, chartHeight, new Color(100, 255, 100));
        drawLineChart(g2d, speedData, chartX, chartY, chartWidth, chartHeight, new Color(100, 150, 255));

        y = chartY + chartHeight + 15;
        g2d.setFont(MONO_FONT);
        drawLegendItem(g2d, 45, y, new Color(255, 100, 100), "Heat Res");
        drawLegendItem(g2d, 130, y, new Color(100, 255, 100), "Toxin Res");
        drawLegendItem(g2d, 220, y, new Color(100, 150, 255), "Speed");
    }

    private void drawLineChart(Graphics2D g2d, List<DataPoint> data, int x, int y, int width, int height, Color color) {
        if (data.size() < 2) return;

        Path2D path = new Path2D.Double();
        boolean first = true;

        for (int i = 0; i < data.size(); i++) {
            DataPoint point = data.get(i);
            double px = x + (width * i / (double) (data.size() - 1));
            double py = y + height - (point.value * height);

            if (first) {
                path.moveTo(px, py);
                first = false;
            } else {
                path.lineTo(px, py);
            }
        }

        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 100));
        g2d.setStroke(new BasicStroke(3));
        g2d.draw(path);

        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2));
        g2d.draw(path);

        for (int i = 0; i < data.size(); i++) {
            DataPoint point = data.get(i);
            double px = x + (width * i / (double) (data.size() - 1));
            double py = y + height - (point.value * height);

            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
            g2d.fillOval((int) px - 4, (int) py - 4, 8, 8);
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

    private void drawColorIndicator(Graphics2D g2d, int y) {
        int centerX = PANEL_WIDTH / 2;

        g2d.setColor(ACCENT_COLOR);
        g2d.setFont(LABEL_FONT);
        drawCenteredString(g2d, "COLOR CODE", centerX, y);
        y += 20;

        Color microbeColor = selectedMicrobe.getColor();

        for (int i = 3; i > 0; i--) {
            g2d.setColor(new Color(
                microbeColor.getRed(),
                microbeColor.getGreen(),
                microbeColor.getBlue(),
                30
            ));
            g2d.fillOval(centerX - 15 - i * 2, y - 15 - i * 2, 30 + i * 4, 30 + i * 4);
        }

        g2d.setColor(microbeColor);
        g2d.fillOval(centerX - 15, y - 15, 30, 30);
        g2d.setColor(ACCENT_COLOR);
        g2d.drawOval(centerX - 15, y - 15, 30, 30);
    }

    private void drawCenteredString(Graphics2D g2d, String text, int x, int y) {
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, x - textWidth / 2, y);
    }

    private static class DataPoint {
        int generation;
        double value;

        DataPoint(int generation, double value) {
            this.generation = generation;
            this.value = value;
        }
    }
}

