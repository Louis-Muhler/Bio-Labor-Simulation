package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Comparator;
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
        private static final Color CHART_DIET = new Color(255, 180, 50);
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
         * Scale factor applied to the on-canvas microbe size (5 px) for the specimen preview.
         */
        private static final double PREVIEW_SCALE = 5.0;
        /**
         * Content width – frame margin and padding are handled by the scroll-pane border.
         */
        private static final int CW = PANEL_WIDTH - 2 * FRAME_MARGIN - 2 * FRAME_PADDING;
        private Microbe selectedMicrobe;

        /**
         * All chart dot positions rebuilt every paint pass for hit-testing.
         */
        private final List<ChartHitbox> chartHitboxes = new ArrayList<>();
        // ── Hover / tooltip state ────────────────────────────────────────
        private Point mousePos = null;
        private HoveredPoint hoveredPoint = null;

        ContentCanvas() {
            setOpaque(false);
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mousePos = e.getPoint();
                    HoveredPoint prev = hoveredPoint;
                    hoveredPoint = findHoveredPoint(mousePos);
                    if (prev != hoveredPoint &&
                            (prev == null || !prev.equals(hoveredPoint))) {
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    mousePos = null;
                    if (hoveredPoint != null) {
                        hoveredPoint = null;
                        repaint();
                    }
                }
            });
        }

        /**
         * Finds the nearest chart column within X_SLICE pixels of {@code p}
         * whose chart area also contains the mouse Y. Returns null if none.
         */
        private HoveredPoint findHoveredPoint(Point p) {
            if (p == null) return null;
            final int X_SLICE = 10;
            ChartHitbox best = null;
            int bestDx = Integer.MAX_VALUE;
            for (ChartHitbox hb : chartHitboxes) {
                // Mouse must be inside the chart's vertical range
                if (p.y < hb.chartTop() || p.y > hb.chartBottom()) continue;
                int dx = Math.abs(p.x - hb.screenX());
                if (dx <= X_SLICE && dx < bestDx) {
                    bestDx = dx;
                    best = hb;
                }
            }
            if (best == null) return null;
            return new HoveredPoint(best.screenX(), best.screenY(),
                    best.chartTop(), best.chartBottom(),
                    best.generation(), best.heat(), best.toxin(), best.speed(), best.diet());
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Microbe microbe = this.selectedMicrobe;
            if (microbe == null || microbe.isDead()) return;

            // Rebuild hit-boxes each frame so they always match what was just drawn
            chartHitboxes.clear();

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
                        String.format("Health: %.1f %%", microbe.getHealth() / Microbe.getMaxHealth() * 100),
                        String.format("Energy: %.1f %%", microbe.getEnergy() / Microbe.getMaxEnergy() * 100),
                        String.format("Age: %d cycles", microbe.getAge())
                });
                y += SECTION_GAP;

                // Genetic Profile
                y = drawSection(g2, y, "GENETIC PROFILE", new String[]{
                        String.format("%-20s %6.1f %%", "Heat Resistance:", microbe.getHeatResistance() * 100),
                        String.format("%-20s %6.1f %%", "Toxin Resistance:", microbe.getToxinResistance() * 100),
                        String.format("%-20s %6.1f %%", "Speed Factor:", microbe.getSpeed() * 100),
                        String.format("%-20s %6.1f %%", "Diet:", microbe.getDiet() * 100)
                });
                y += SECTION_GAP;

                // Lineage Evolution
                List<AncestorSnapshot> ancestry = microbe.getAncestry();
                if (!ancestry.isEmpty()) {
                    y = drawAncestrySection(g2, y, ancestry, microbe);
                    y += COLOR_CODE_GAP;
                }

                // Colour indicator
                y += SECTION_GAP;
                drawColorIndicator(g2, y, microbe);

                // ── Tooltip – drawn on top of everything else ──────────────
                drawTooltip(g2);
            } finally {
                g2.dispose();
            }
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

            List<DataPoint> heat = new ArrayList<>();
            List<DataPoint> toxin = new ArrayList<>();
            List<DataPoint> speed = new ArrayList<>();
            List<DataPoint> diet = new ArrayList<>();

            for (AncestorSnapshot a : ancestry) {
                heat.add(new DataPoint(a.generation(), a.heatResistance()));
                toxin.add(new DataPoint(a.generation(), a.toxinResistance()));
                speed.add(new DataPoint(a.generation(), a.speed()));
                diet.add(new DataPoint(a.generation(), a.diet()));
            }

            int currentGen = microbe.getAbsoluteGeneration();
            heat.add(new DataPoint(currentGen, microbe.getHeatResistance()));
            toxin.add(new DataPoint(currentGen, microbe.getToxinResistance()));
            speed.add(new DataPoint(currentGen, microbe.getSpeed()));
            diet.add(new DataPoint(currentGen, microbe.getDiet()));

            heat.sort(Comparator.comparingInt(DataPoint::generation));
            toxin.sort(Comparator.comparingInt(DataPoint::generation));
            speed.sort(Comparator.comparingInt(DataPoint::generation));
            diet.sort(Comparator.comparingInt(DataPoint::generation));

            // Build hitboxes: all series share the same generation → same X position.
            // Y is taken from the heat series dot position as a consistent representative.
            if (!heat.isEmpty()) {
                int minGen = heat.get(0).generation();
                int maxGen = heat.get(heat.size() - 1).generation();
                int range = Math.max(1, maxGen - minGen);
                for (int i = 0; i < heat.size(); i++) {
                    int gen = heat.get(i).generation();
                    int sx = chartX + (int) (chartW * (double) (gen - minGen) / range);
                    int sy = chartY + CHART_HEIGHT - (int) (heat.get(i).value() * CHART_HEIGHT);
                    chartHitboxes.add(new ChartHitbox(
                            sx, sy,
                            chartY, chartY + CHART_HEIGHT,   // vertical bounds for slice detection
                            gen,
                            heat.get(i).value(),
                            toxin.get(i).value(),
                            speed.get(i).value(),
                            diet.get(i).value()
                    ));
                }
            }

            drawLineChart(g2, heat,  chartX, chartY, chartW, CHART_HEIGHT, CHART_HEAT);
            drawLineChart(g2, toxin, chartX, chartY, chartW, CHART_HEIGHT, CHART_TOXIN);
            drawLineChart(g2, speed, chartX, chartY, chartW, CHART_HEIGHT, CHART_SPEED);
            drawLineChart(g2, diet,  chartX, chartY, chartW, CHART_HEIGHT, CHART_DIET);

            y = chartY + CHART_HEIGHT + 15;
            g2.setFont(MONO_FONT);
            drawLegendItem(g2, 5, y, CHART_HEAT,  "Heat");
            drawLegendItem(g2,  60, y, CHART_TOXIN, "Toxin");
            drawLegendItem(g2, 120, y, CHART_SPEED, "Speed");
            drawLegendItem(g2, 185, y, CHART_DIET,  "Diet");

            return y + 10;
        }

        private static int sectionHeight() {
            return sectionHeight(3);
        }

        private static int sectionHeight(int dataLines) {
            return LINE_H + dataLines * LINE_H;
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
            h += sectionHeight();    // vital signs (3 lines)
            h += SECTION_GAP;
            h += sectionHeight(4);   // genetic profile (4 lines: heat, toxin, speed, diet)
            h += SECTION_GAP;

            List<AncestorSnapshot> ancestry = m.getAncestry();
            if (!ancestry.isEmpty()) {
                h += LINE_H + 5;   // section header + gap
                h += CHART_HEIGHT;
                h += 15;           // gap before legend
                h += LINE_H;       // legend
                h += COLOR_CODE_GAP; // extra breath after chart legend
            }

            h += SECTION_GAP;
            // Dynamic height: label + scaled size + max glow radius (3 layers * 2 * SCALE on each side) + padding
            int previewSize = (int) (m.getSize() * PREVIEW_SCALE);
            int maxGlowRadius = (int) (3 * 2 * PREVIEW_SCALE);
            h += LINE_H + 40 + previewSize + maxGlowRadius + 20; // label gap + body + glow + bottom pad
            return h;
        }

        private void drawLineChart(Graphics2D g2, List<DataPoint> data,
                                   int x, int y, int w, int h, Color color) {
            if (data.size() < 2) return;

            // X axis is proportional to the absolute generation timeline
            int minGen = data.get(0).generation();
            int maxGen = data.get(data.size() - 1).generation();
            int range = Math.max(1, maxGen - minGen);

            Path2D path = new Path2D.Double();
            for (int i = 0; i < data.size(); i++) {
                double px = x + w * (double) (data.get(i).generation() - minGen) / range;
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
                double px = x + w * (double) (data.get(i).generation() - minGen) / range;
                double py = y + h - (data.get(i).value() * h);

                g2.setComposite(DOT_GLOW_COMPOSITE);
                g2.setColor(color);
                g2.fillOval((int) px - 4, (int) py - 4, 8, 8);
                g2.setComposite(orig);
                g2.setColor(color);
                g2.fillOval((int) px - 3, (int) py - 3, 6, 6);
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

        private void drawTooltip(Graphics2D g2) {
            HoveredPoint hp = hoveredPoint;
            if (hp == null) return;

            // ── Crosshair vertical line ──────────────────────────────────
            Composite orig = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 40 / 255f));
            g2.setColor(Color.WHITE);
            g2.setStroke(STROKE_1);
            g2.drawLine(hp.screenX(), hp.chartTop(), hp.screenX(), hp.chartBottom());
            g2.setComposite(orig);

            // ── Tooltip appearance constants ─────────────────────────────
            final Color TT_BG = new Color(18, 18, 18, 230);
            final Color TT_BORDER = ACCENT;
            final Font TT_FONT = new Font("Consolas", Font.PLAIN, 11);
            final int PAD = 6;
            final int OFFSET = 12;

            g2.setFont(TT_FONT);
            FontMetrics fm = g2.getFontMetrics();

            String[] lines = {
                    String.format("Gen:   %d", hp.generation()),
                    String.format("Heat:  %.1f %%", hp.heat() * 100),
                    String.format("Toxin: %.1f %%", hp.toxin() * 100),
                    String.format("Speed: %.1f %%", hp.speed() * 100),
                    String.format("Diet:  %.1f %%", hp.diet() * 100)
            };

            int lineH = fm.getHeight();
            int boxW = 0;
            for (String l : lines) boxW = Math.max(boxW, fm.stringWidth(l));
            boxW += PAD * 2;
            int boxH = lineH * lines.length + PAD * 2;

            // Anchor tooltip to the crosshair X (snapped column), not raw mouse X –
            // gives the "stock chart callout" feel. Flip left if too close to right edge.
            int tx = hp.screenX() + OFFSET;
            if (tx + boxW > getWidth()) tx = hp.screenX() - boxW - OFFSET;

            // Anchor tooltip Y to the mouse position, clamped inside panel
            int rawY = (mousePos != null ? mousePos.y : hp.screenY()) - boxH / 2;
            int ty = Math.max(0, Math.min(rawY, getHeight() - boxH));

            // Background fill
            g2.setColor(TT_BG);
            g2.fillRoundRect(tx, ty, boxW, boxH, 6, 6);

            // Cyan border
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
            g2.setColor(TT_BORDER);
            g2.setStroke(STROKE_1);
            g2.drawRoundRect(tx, ty, boxW, boxH, 6, 6);
            g2.setComposite(orig);

            // Text lines – Gen row in accent colour, gene rows in their chart colours
            int textX = tx + PAD;
            int textY = ty + PAD + fm.getAscent();
            Color[] lineColors = {ACCENT, CHART_HEAT, CHART_TOXIN, CHART_SPEED, CHART_DIET};
            for (int i = 0; i < lines.length; i++) {
                g2.setColor(lineColors[i]);
                g2.drawString(lines[i], textX, textY);
                textY += lineH;
            }
        }

        /**
         * Screen position + stat values for a single chart dot.
         */
        private record ChartHitbox(int screenX, int screenY,
                                   int chartTop, int chartBottom,
                                   int generation,
                                   double heat, double toxin, double speed, double diet) {
        }

        /**
         * The single snapshot that the mouse is currently hovering over.
         */
        private record HoveredPoint(int screenX, int screenY,
                                    int chartTop, int chartBottom,
                                    int generation,
                                    double heat, double toxin, double speed, double diet) {
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
            drawCentered(g2, "SPECIMEN", CW / 2, y);
            y += 40;

            // Scale factor so the on-canvas microbe (size=5 px) appears large here
            int size = (int) (microbe.getSize() * PREVIEW_SCALE);
            int cx = CW / 2;
            int cy = y + size;  // leave room for glow above
            int x = cx - size / 2;
            int baseY = cy - size / 2;

            Color mc = microbe.getColor();
            Color brightColor = new Color(
                    Math.min(255, mc.getRed() + 40),
                    Math.min(255, mc.getGreen() + 40),
                    Math.min(255, mc.getBlue() + 40));

            Composite orig = g2.getComposite();
            double healthRatio = microbe.getHealthRatio();

            // ── Exact copy of SimulationCanvas glow loop ──────────────────
            for (int i = 3; i > 0; i--) {
                float alpha = (float) ((20 + i * 15) * healthRatio / 255f);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
                g2.setColor(mc);
                int glowSize = size + (int) (i * 4 * PREVIEW_SCALE);
                g2.fillOval(x - (int) (i * 2 * PREVIEW_SCALE), baseY - (int) (i * 2 * PREVIEW_SCALE), glowSize, glowSize);
            }

            // ── Bright inner layer ─────────────────────────────────────────
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 220 / 255f));
            g2.setColor(brightColor);
            g2.fillOval(x, baseY, size, size);

            // ── Base colour ────────────────────────────────────────────────
            g2.setComposite(orig);
            g2.setColor(mc);
            g2.fillOval(x + 1, baseY + 1, size - 2, size - 2);
        }

        private record DataPoint(int generation, double value) {
        }
    }
}

