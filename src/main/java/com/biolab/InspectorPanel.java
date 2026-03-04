package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * Inspector panel that displays live data for a selected {@link Microbe}.
 *
 * <h3>Layout architecture (frame vs. scrollable content)</h3>
 * <ol>
 *   <li><b>Outer panel</b> ({@code InspectorPanel} itself) – paints the static
 *       rounded dark background and neon-cyan border. The frame dimensions are
 *       driven by the available height assigned by {@link OverlayManager} via
 *       {@code setBounds()}, and it <em>never</em> scrolls.</li>
 *   <li><b>{@code ContentCanvas}</b> – a transparent inner panel that renders
 *       all data sections (title, vital signs, genetic profile, lineage chart,
 *       colour code). Its preferred height is recomputed whenever the selected
 *       microbe changes.</li>
 *   <li><b>{@code JScrollPane}</b> – wraps the content canvas with the scrollbar
 *       hidden but mouse-wheel scrolling active. The scroll pane border provides
 *       the inner padding so text never touches the cyan frame.</li>
 * </ol>
 *
 * <p>When the window is tall enough, {@code drawActiveFrame} draws the border
 * only as high as the content requires (shrink-to-fit). When the window is too
 * small, the border fills the available space and the content canvas scrolls.</p>
 */
public class InspectorPanel extends JPanel {

    // ── Frame geometry ────────────────────────────────────────────────────
    /**
     * Fixed pixel width of the panel, also referenced by the inner ContentCanvas.
     */
    static final int PANEL_WIDTH = 320;
    /**
     * Gap between the outer panel edge and the rounded border rectangle.
     */
    private static final int FRAME_MARGIN = 20;
    private static final int CORNER_RADIUS = 15;
    /**
     * Gap between the rounded border and the scrollable content area.
     */
    private static final int FRAME_PADDING = 15;
    /**
     * Combined inset (FRAME_MARGIN + FRAME_PADDING) applied to all four sides of the scroll pane border.
     */
    private static final int INSET = FRAME_MARGIN + FRAME_PADDING;
    /**
     * Height of the placeholder box shown when no microbe is selected.
     */
    private static final int NO_SELECTION_HEIGHT = 150;

    // ── Frame colours ─────────────────────────────────────────────────────
    private static final Color BG_COLOR = new Color(18, 18, 18, 240);
    private static final Color ACCENT_COLOR = new Color(0, 255, 255);
    private static final Color BORDER_GLOW_COLOR = new Color(0, 255, 255, 80);
    private static final Color NO_SELECTION_BG = new Color(18, 18, 18, 150);
    private static final Color NO_SELECTION_BORDER = new Color(40, 40, 50, 200);
    private static final Color NO_SELECTION_TEXT = new Color(220, 220, 220, 180);

    // ── Frame fonts & strokes (used only for the "no selection" placeholder) ─
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font MONO_FONT = new Font("Consolas", Font.PLAIN, 11);
    private static final BasicStroke STROKE_1 = new BasicStroke(1);
    private static final BasicStroke STROKE_2 = new BasicStroke(2);
    private static final BasicStroke STROKE_3 = new BasicStroke(3);

    private final ContentCanvas contentCanvas;
    private final JScrollPane scrollPane;

    // ────────────────────────────────────────────────────────────────────
    // Construction
    // ────────────────────────────────────────────────────────────────────

    public InspectorPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());

        contentCanvas = new ContentCanvas();

        scrollPane = new JScrollPane(contentCanvas);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(INSET, INSET, INSET, INSET));
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    // ────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────

    private static void drawCenteredString(Graphics2D g2, String text, int x, int y) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, x - fm.stringWidth(text) / 2, y);
    }

    public void hidePanel() {
        contentCanvas.selectedMicrobe = null;
        setVisible(false);
        if (getParent() != null) {
            getParent().repaint();
        }
    }

    public void setSelectedMicrobe(Microbe microbe) {
        contentCanvas.selectedMicrobe = microbe;
        contentCanvas.recalculatePreferredSize();
        scrollPane.revalidate();
        repaint();
    }

    public void showPanel() {
        setVisible(true);
        contentCanvas.recalculatePreferredSize();
        scrollPane.revalidate();
        repaint();
    }

    // ────────────────────────────────────────────────────────────────────
    // Outer frame painting – static, never scrolls
    // ────────────────────────────────────────────────────────────────────

    /**
     * Returns the ideal panel height that fits all content without scrolling.
     * {@link OverlayManager} uses this to shrink-to-fit when content is
     * shorter than the available space.
     */
    public int getPreferredPanelHeight() {
        Microbe m = contentCanvas.selectedMicrobe;
        if (m == null || m.isDead()) {
            return NO_SELECTION_HEIGHT + 2 * FRAME_MARGIN;
        }
        return contentCanvas.computeContentHeight() + 2 * INSET;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Microbe microbe = contentCanvas.selectedMicrobe;
            if (microbe == null || microbe.isDead()) {
                drawNoSelectionFrame(g2);
            } else {
                drawActiveFrame(g2);
            }
        } finally {
            g2.dispose();
        }
    }

    private void drawNoSelectionFrame(Graphics2D g2) {
        int boxH = 150;
        int w = PANEL_WIDTH - 2 * FRAME_MARGIN;

        g2.setColor(NO_SELECTION_BG);
        g2.fillRoundRect(FRAME_MARGIN, FRAME_MARGIN, w, boxH, CORNER_RADIUS, CORNER_RADIUS);

        g2.setColor(NO_SELECTION_BORDER);
        g2.setStroke(STROKE_2);
        g2.drawRoundRect(FRAME_MARGIN, FRAME_MARGIN, w, boxH, CORNER_RADIUS, CORNER_RADIUS);

        g2.setColor(NO_SELECTION_TEXT);
        g2.setFont(LABEL_FONT);
        drawCenteredString(g2, "No specimen selected", PANEL_WIDTH / 2, FRAME_MARGIN + 60);
        g2.setFont(MONO_FONT);
        drawCenteredString(g2, "Click on a microbe to inspect", PANEL_WIDTH / 2, FRAME_MARGIN + 85);
    }

    private void drawActiveFrame(Graphics2D g2) {
        int w = PANEL_WIDTH - 2 * FRAME_MARGIN;
        // Shrink-to-fit: frame wraps the content, never stretches beyond it
        int preferredH = getPreferredPanelHeight() - 2 * FRAME_MARGIN;
        int availableH = getHeight() - 2 * FRAME_MARGIN;
        int h = Math.min(preferredH, availableH);

        g2.setColor(BG_COLOR);
        g2.fillRoundRect(FRAME_MARGIN, FRAME_MARGIN, w, h, CORNER_RADIUS, CORNER_RADIUS);

        g2.setColor(BORDER_GLOW_COLOR);
        g2.setStroke(STROKE_3);
        g2.drawRoundRect(FRAME_MARGIN, FRAME_MARGIN, w, h, CORNER_RADIUS, CORNER_RADIUS);

        g2.setColor(ACCENT_COLOR);
        g2.setStroke(STROKE_1);
        g2.drawRoundRect(FRAME_MARGIN, FRAME_MARGIN, w, h, CORNER_RADIUS, CORNER_RADIUS);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Inner content canvas – transparent, only draws data sections.
    // ─────────────────────────────────────────────────────────────────────

    private static class ContentCanvas extends JPanel {
        // ── Cached colours ──────────────────────────────────────────────
        private static final Color ACCENT = new Color(0, 255, 255);
        private static final Color TEXT_COLOR = new Color(220, 220, 220);
        private static final Color GRID_COLOR = new Color(40, 40, 50);
        private static final Color CHART_BG = new Color(0, 0, 0, 100);
        private static final Color CHART_HEAT = new Color(255, 100, 100);
        private static final Color CHART_TOXIN = new Color(100, 255, 100);
        private static final Color CHART_SPEED = new Color(100, 150, 255);
        // ── Cached strokes ──────────────────────────────────────────────
        private static final BasicStroke STROKE_1 = new BasicStroke(1);
        private static final BasicStroke STROKE_2 = new BasicStroke(2);
        private static final BasicStroke STROKE_3 = new BasicStroke(3);
        // ── Cached fonts ────────────────────────────────────────────────
        private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 16);
        private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 12);
        private static final Font MONO_FONT = new Font("Consolas", Font.PLAIN, 13);
        // ── Cached AlphaComposite instances (avoid per-frame allocation) ─
        private static final AlphaComposite GLOW_COMPOSITE =
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 100 / 255f);
        private static final AlphaComposite DOT_GLOW_COMPOSITE =
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 150 / 255f);
        private static final AlphaComposite COLOR_GLOW_COMPOSITE =
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 30 / 255f);
        // ── Layout spacing ──────────────────────────────────────────────
        private static final int LINE_H = 22;
        private static final int SECTION_GAP = 20;
        private static final int CHART_HEIGHT = 120;
        private static final int INDENT = 15;
        private static final int DEEP_INDENT = 30;
        /**
         * Extra vertical breathing room above the COLOR CODE section.
         */
        private static final int COLOR_CODE_GAP = 25;
        /**
         * Content width – frame margin and padding are handled by the scroll-pane border.
         */
        private static final int CW = PANEL_WIDTH - 2 * FRAME_MARGIN - 2 * FRAME_PADDING;
        private Microbe selectedMicrobe;

        ContentCanvas() {
            setOpaque(false);
        }

        private static int sectionHeight() {
            return LINE_H + 3 * LINE_H;
        }

        private static void drawCentered(Graphics2D g2, String text, int x, int y) {
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, x - fm.stringWidth(text) / 2, y);
        }

        void recalculatePreferredSize() {
            setPreferredSize(new Dimension(CW, computeContentHeight()));
        }

        private int computeContentHeight() {
            Microbe m = this.selectedMicrobe;
            if (m == null || m.isDead()) return 0;

            int h = 0;
            h += LINE_H + 5;       // title
            h += LINE_H;           // separator
            h += sectionHeight();  // vital signs
            h += SECTION_GAP;
            h += sectionHeight();  // genetic profile
            h += SECTION_GAP;

            List<AncestorSnapshot> ancestry = m.getAncestry();
            if (!ancestry.isEmpty()) {
                h += LINE_H + 5;   // section header + gap
                h += CHART_HEIGHT;
                h += 15;           // gap before legend
                h += LINE_H;       // legend
                h += COLOR_CODE_GAP; // extra breath after chart legend
            }

            h += SECTION_GAP;        // consistent gap before COLOR CODE
            h += LINE_H + 45;        // colour indicator label + circle + pad
            return h;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Microbe microbe = this.selectedMicrobe;
            if (microbe == null || microbe.isDead()) return;

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int y = 0;

                // Title
                g2.setColor(ACCENT);
                g2.setFont(TITLE_FONT);
                drawCentered(g2, "SPECIMEN ANALYSIS", CW / 2, y + 15);
                y += LINE_H + 5;

                // Separator
                g2.setColor(GRID_COLOR);
                g2.fillRect(0, y, CW, 2);
                y += LINE_H;

                // Vital Signs
                y = drawSection(g2, y, "VITAL SIGNS", new String[]{
                        String.format("Health: %.1f%%", microbe.getHealth() / Microbe.getMaxHealth() * 100),
                        String.format("Energy: %.1f%%", microbe.getEnergy() / Microbe.getMaxEnergy() * 100),
                        String.format("Age: %d cycles", microbe.getAge())
                });
                y += SECTION_GAP;

                // Genetic Profile
                y = drawSection(g2, y, "GENETIC PROFILE", new String[]{
                        String.format("Heat Resistance: %.2f", microbe.getHeatResistance()),
                        String.format("Toxin Resistance: %.2f", microbe.getToxinResistance()),
                        String.format("Speed Factor: %.2f", microbe.getSpeed())
                });
                y += SECTION_GAP;

                // Lineage Evolution
                List<AncestorSnapshot> ancestry = microbe.getAncestry();
                if (!ancestry.isEmpty()) {
                    y = drawAncestrySection(g2, y, ancestry, microbe);
                    y += COLOR_CODE_GAP; // extra breath after chart legend
                }

                // Colour indicator – one consistent SECTION_GAP from whatever came before
                y += SECTION_GAP;
                drawColorIndicator(g2, y, microbe);
            } finally {
                g2.dispose();
            }
        }

        private int drawSection(Graphics2D g2, int y, String title, String[] lines) {
            g2.setColor(ACCENT);
            g2.setFont(LABEL_FONT);
            drawTriangle(g2, INDENT, y - 3);
            g2.drawString(title, INDENT + 14, y + 4);
            y += LINE_H;

            g2.setColor(TEXT_COLOR);
            g2.setFont(MONO_FONT);
            for (String line : lines) {
                g2.drawString(line, DEEP_INDENT, y + 4);
                y += LINE_H;
            }
            return y;
        }

        private void drawTriangle(Graphics2D g2, int x, int y) {
            g2.fillPolygon(new int[]{x, x + 8, x}, new int[]{y, y + 4, y + 8}, 3);
        }

        private int drawAncestrySection(Graphics2D g2, int y,
                                        List<AncestorSnapshot> ancestry, Microbe microbe) {
            g2.setColor(ACCENT);
            g2.setFont(LABEL_FONT);
            drawTriangle(g2, INDENT, y - 3);
            g2.drawString("LINEAGE EVOLUTION", INDENT + 14, y + 4);
            y += LINE_H + 5;

            int chartX = 5;
            int chartW = CW - 10;
            int chartY = y;

            g2.setColor(CHART_BG);
            g2.fillRect(chartX, chartY, chartW, CHART_HEIGHT);

            g2.setColor(GRID_COLOR);
            g2.setStroke(STROKE_1);
            for (int i = 0; i <= 4; i++) {
                int gy = chartY + CHART_HEIGHT * i / 4;
                g2.drawLine(chartX, gy, chartX + chartW, gy);
            }
            g2.drawRect(chartX, chartY, chartW, CHART_HEIGHT);

            List<DataPoint> heat = new java.util.ArrayList<>();
            List<DataPoint> toxin = new java.util.ArrayList<>();
            List<DataPoint> speed = new java.util.ArrayList<>();

            heat.add(new DataPoint(0, microbe.getHeatResistance()));
            toxin.add(new DataPoint(0, microbe.getToxinResistance()));
            speed.add(new DataPoint(0, microbe.getSpeed()));

            for (AncestorSnapshot a : ancestry) {
                int gen = a.generation() + 1;
                heat.add(new DataPoint(gen, a.heatResistance()));
                toxin.add(new DataPoint(gen, a.toxinResistance()));
                speed.add(new DataPoint(gen, a.speed()));
            }

            heat.sort((a, b) -> Integer.compare(b.generation, a.generation));
            toxin.sort((a, b) -> Integer.compare(b.generation, a.generation));
            speed.sort((a, b) -> Integer.compare(b.generation, a.generation));

            drawLineChart(g2, heat, chartX, chartY, chartW, CHART_HEIGHT, CHART_HEAT);
            drawLineChart(g2, toxin, chartX, chartY, chartW, CHART_HEIGHT, CHART_TOXIN);
            drawLineChart(g2, speed, chartX, chartY, chartW, CHART_HEIGHT, CHART_SPEED);

            y = chartY + CHART_HEIGHT + 15;
            g2.setFont(MONO_FONT);
            drawLegendItem(g2, 5, y, CHART_HEAT, "Heat Res");
            drawLegendItem(g2, 90, y, CHART_TOXIN, "Toxin Res");
            drawLegendItem(g2, 180, y, CHART_SPEED, "Speed");

            return y + 10;
        }

        private void drawLineChart(Graphics2D g2, List<DataPoint> data,
                                   int x, int y, int w, int h, Color color) {
            if (data.size() < 2) return;

            Path2D path = new Path2D.Double();
            for (int i = 0; i < data.size(); i++) {
                double px = x + (w * i / (double) (data.size() - 1));
                double py = y + h - (data.get(i).value() * h);
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }

            Composite orig = g2.getComposite();

            g2.setComposite(GLOW_COMPOSITE);
            g2.setColor(color);
            g2.setStroke(STROKE_3);
            g2.draw(path);

            g2.setComposite(orig);
            g2.setColor(color);
            g2.setStroke(STROKE_2);
            g2.draw(path);

            for (int i = 0; i < data.size(); i++) {
                double px = x + (w * i / (double) (data.size() - 1));
                double py = y + h - (data.get(i).value() * h);

                g2.setComposite(DOT_GLOW_COMPOSITE);
                g2.setColor(color);
                g2.fillOval((int) px - 4, (int) py - 4, 8, 8);
                g2.setComposite(orig);
                g2.setColor(color);
                g2.fillOval((int) px - 3, (int) py - 3, 6, 6);
            }
        }

        private void drawLegendItem(Graphics2D g2, int x, int y, Color color, String label) {
            g2.setColor(color);
            g2.fillRect(x, y - 6, 12, 3);
            g2.setColor(TEXT_COLOR);
            g2.drawString(label, x + 16, y);
        }

        private void drawColorIndicator(Graphics2D g2, int y, Microbe microbe) {
            g2.setColor(ACCENT);
            g2.setFont(LABEL_FONT);
            drawCentered(g2, "COLOR CODE", CW / 2, y);
            y += 35;

            Color mc = microbe.getColor();
            Composite orig = g2.getComposite();
            g2.setColor(mc);
            for (int i = 3; i > 0; i--) {
                g2.setComposite(COLOR_GLOW_COMPOSITE);
                g2.fillOval(CW / 2 - 15 - i * 2, y - 15 - i * 2, 30 + i * 4, 30 + i * 4);
            }
            g2.setComposite(orig);
            g2.setColor(mc);
            g2.fillOval(CW / 2 - 15, y - 15, 30, 30);
        }

        private record DataPoint(int generation, double value) {
        }
    }
}

