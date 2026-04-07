package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.*;
import java.util.List;
import java.util.function.ToDoubleFunction;

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

    static final int MAX_VISIBLE_LINEAGE_POINTS = 10;

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
    private static final Color BG_COLOR = OverlayTheme.PANEL_BG_ALPHA;
    private static final Color ACCENT_COLOR = OverlayTheme.ACCENT;
    private static final Color BORDER_GLOW_COLOR = OverlayTheme.ACCENT_GLOW;
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

        scrollPane = OverlayScrollSupport.createWheelOnlyScrollPane(
                contentCanvas,
                new Insets(INSET, INSET, INSET, INSET),
                18
        );

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

    static List<Integer> selectEvenlyDistributedIndices(int totalPoints, int maxVisiblePoints) {
        if (totalPoints <= 0 || maxVisiblePoints <= 0) {
            return List.of();
        }

        int targetCount = Math.min(totalPoints, maxVisiblePoints);
        if (targetCount == totalPoints) {
            List<Integer> all = new ArrayList<>(totalPoints);
            for (int i = 0; i < totalPoints; i++) {
                all.add(i);
            }
            return all;
        }

        TreeSet<Integer> selected = new TreeSet<>();
        selected.add(0);
        selected.add(totalPoints - 1);

        for (int slot = 1; slot < targetCount - 1; slot++) {
            double normalized = slot / (double) (targetCount - 1);
            int idx = (int) Math.round(normalized * (totalPoints - 1));
            selected.add(Math.max(0, Math.min(totalPoints - 1, idx)));
        }

        while (selected.size() < targetCount) {
            Integer prev = null;
            int bestLeft = -1;
            int bestGap = 0;
            for (Integer current : selected) {
                if (prev != null) {
                    int gap = current - prev;
                    if (gap > bestGap) {
                        bestGap = gap;
                        bestLeft = prev;
                    }
                }
                prev = current;
            }
            if (bestGap <= 1 || bestLeft < 0) {
                break;
            }
            selected.add(bestLeft + bestGap / 2);
        }

        if (selected.size() < targetCount) {
            for (int i = 0; i < totalPoints && selected.size() < targetCount; i++) {
                selected.add(i);
            }
        }
        return new ArrayList<>(selected);
    }

    static List<Integer> computeUniformSlotXPositions(int chartX, int chartW, int pointCount) {
        if (pointCount <= 0) {
            return List.of();
        }
        if (pointCount == 1) {
            return List.of(chartX + chartW / 2);
        }

        List<Integer> xs = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            int x = chartX + (int) Math.round(i * (double) chartW / (pointCount - 1));
            xs.add(x);
        }
        return xs;
    }

    static String formatCurrentMaxWithPercent(double current, double max) {
        double safeMax = Math.max(0.0, max);
        double clampedCurrent = Math.max(0.0, Math.min(current, safeMax));
        double percent = safeMax <= 0.0 ? 0.0 : (clampedCurrent / safeMax) * 100.0;
        return String.format(Locale.ROOT, "%.0f/%.0f | %4.1f %%", clampedCurrent, safeMax, percent);
    }

    static String[] buildGeneticProfileLines(Microbe microbe) {
        return new String[]{
                String.format("%-20s %6.1f %%", "Heat Resistance:", microbe.getHeatResistance() * 100),
                String.format("%-20s %6.1f %%", "Toxin Resistance:", microbe.getToxinResistance() * 100),
                String.format("%-20s %6.1f %%", "Speed Factor:", microbe.getSpeed() * 100),
                String.format("%-20s %6.1f %%", "Diet:", microbe.getDiet() * 100)
        };
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
        private static final Color ACCENT = OverlayTheme.ACCENT;
        private static final Color TEXT_COLOR = new Color(220, 220, 220);
        private static final Color GRID_COLOR = new Color(40, 40, 50);
        private static final Color CHART_BG = new Color(0, 0, 0, 100);
        private static final Color CHART_HEAT = new Color(255, 100, 100);
        private static final Color CHART_TOXIN = new Color(100, 255, 100);
        private static final Color CHART_SPEED = new Color(100, 150, 255);
        private static final Color CHART_DIET = new Color(255, 180, 50);
        private static final Color CHART_MAX_HEALTH = new Color(255, 120, 180);
        private static final Color CHART_MAX_ENERGY = new Color(120, 200, 255);
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
                    if (!Objects.equals(prev, hoveredPoint)) {
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

        private static double[] resolveLineageRange(List<LineagePoint> points) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (LineagePoint point : points) {
                min = Math.min(min, Math.min(point.maxHealth(), point.maxEnergy()));
                max = Math.max(max, Math.max(point.maxHealth(), point.maxEnergy()));
            }
            if (!Double.isFinite(min) || !Double.isFinite(max)) {
                return new double[]{0.0, 1.0};
            }
            if (max <= min) {
                return new double[]{min, min + 1.0};
            }
            return new double[]{min, max};
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
                        String.format("%-12s %16s", "Age:", microbe.getAge() + " cycles"),
                        String.format("%-12s %16s", "Health:", formatCurrentMaxWithPercent(microbe.getHealth(), microbe.getMaxHealth())),
                        String.format("%-12s %16s", "Energy:", formatCurrentMaxWithPercent(microbe.getEnergy(), microbe.getMaxEnergy())),
                        String.format("%-20s %6.1f %%", "Strength:", microbe.getStrengthTrait() * 100),
                        String.format("%-20s %6.1f %%", "Defense:", microbe.getDefenseTrait() * 100)
                });
                y += SECTION_GAP;

                // Genetic Profile
                y = drawSection(g2, y, "GENETIC PROFILE", buildGeneticProfileLines(microbe));
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

        private static double normalize(double value, double min, double max) {
            if (max <= min) {
                return 0.0;
            }
            double n = (value - min) / (max - min);
            return Math.max(0.0, Math.min(1.0, n));
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
                    best.generation(),
                    best.heatResistance(), best.toxinResistance(), best.speed(), best.diet(),
                    best.maxHealth(), best.maxEnergy());
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

            List<LineagePoint> fullTimeline = buildLineageTimeline(ancestry, microbe);
            List<LineagePoint> visibleTimeline = downsampleForChart(fullTimeline);

            double[] chartRange = resolveLineageRange(visibleTimeline);
            double percentMin = 0.0;
            double percentMax = 1.0;

            List<ChartPoint> heat = buildChartPoints(visibleTimeline, chartX, chartY, chartW, CHART_HEIGHT,
                    percentMin, percentMax, LineagePoint::heatResistance);
            List<ChartPoint> toxin = buildChartPoints(visibleTimeline, chartX, chartY, chartW, CHART_HEIGHT,
                    percentMin, percentMax, LineagePoint::toxinResistance);
            List<ChartPoint> speed = buildChartPoints(visibleTimeline, chartX, chartY, chartW, CHART_HEIGHT,
                    percentMin, percentMax, LineagePoint::speed);
            List<ChartPoint> diet = buildChartPoints(visibleTimeline, chartX, chartY, chartW, CHART_HEIGHT,
                    percentMin, percentMax, LineagePoint::diet);

            List<ChartPoint> maxHealth = buildChartPoints(visibleTimeline, chartX, chartY, chartW, CHART_HEIGHT,
                    chartRange[0], chartRange[1], LineagePoint::maxHealth);
            List<ChartPoint> maxEnergy = buildChartPoints(visibleTimeline, chartX, chartY, chartW, CHART_HEIGHT,
                    chartRange[0], chartRange[1], LineagePoint::maxEnergy);

            for (int i = 0; i < visibleTimeline.size() && i < maxHealth.size(); i++) {
                LineagePoint point = visibleTimeline.get(i);
                ChartPoint healthPoint = maxHealth.get(i);
                chartHitboxes.add(new ChartHitbox(
                        healthPoint.screenX(),
                        healthPoint.screenY(),
                        chartY,
                        chartY + CHART_HEIGHT,
                        point.generation(),
                        point.heatResistance(),
                        point.toxinResistance(),
                        point.speed(),
                        point.diet(),
                        point.maxHealth(),
                        point.maxEnergy()
                ));
            }

            drawLineChart(g2, heat, CHART_HEAT);
            drawLineChart(g2, toxin, CHART_TOXIN);
            drawLineChart(g2, speed, CHART_SPEED);
            drawLineChart(g2, diet, CHART_DIET);
            drawLineChart(g2, maxHealth, CHART_MAX_HEALTH);
            drawLineChart(g2, maxEnergy, CHART_MAX_ENERGY);

            y = chartY + CHART_HEIGHT + 15;
            g2.setFont(MONO_FONT);
            drawLegendItem(g2, 5, y, CHART_MAX_HEALTH, "Health");
            drawLegendItem(g2, 80, y, CHART_MAX_ENERGY, "Energy");
            drawLegendItem(g2, 165, y, CHART_HEAT, "Heat");
            y += 14;
            drawLegendItem(g2, 5, y, CHART_DIET, "Diet");
            drawLegendItem(g2, 80, y, CHART_TOXIN, "Toxin");
            drawLegendItem(g2, 165, y, CHART_SPEED, "Speed");

            return y + 10;
        }

        private List<LineagePoint> downsampleForChart(List<LineagePoint> timeline) {
            List<Integer> visibleIndices = selectEvenlyDistributedIndices(
                    timeline.size(),
                    MAX_VISIBLE_LINEAGE_POINTS
            );
            List<LineagePoint> sampled = new ArrayList<>(visibleIndices.size());
            for (Integer idx : visibleIndices) {
                sampled.add(timeline.get(idx));
            }
            return sampled;
        }

        private int computeContentHeight() {
            Microbe m = this.selectedMicrobe;
            if (m == null || m.isDead()) return 0;

            int h = 0;
            h += LINE_H + 5;       // title
            h += LINE_H;           // separator
            h += sectionHeight();    // vital signs (3 lines)
            h += SECTION_GAP;
            h += sectionHeight(6);   // genetic profile (6 lines: incl. derived strength/defense)
            h += SECTION_GAP;

            List<AncestorSnapshot> ancestry = m.getAncestry();
            if (!ancestry.isEmpty()) {
                h += LINE_H + 5;   // section header + gap
                h += CHART_HEIGHT;
                h += 15;           // gap before legend
                h += LINE_H * 2;   // legend (2 rows)
                h += COLOR_CODE_GAP; // extra breath after chart legend
            }

            h += SECTION_GAP;
            // Dynamic height: label + scaled size + max glow radius (3 layers * 2 * SCALE on each side) + padding
            int previewSize = (int) (m.getSize() * PREVIEW_SCALE);
            int maxGlowRadius = (int) (3 * 2 * PREVIEW_SCALE);
            h += LINE_H + 40 + previewSize + maxGlowRadius + 20; // label gap + body + glow + bottom pad
            return h;
        }

        private List<LineagePoint> buildLineageTimeline(List<AncestorSnapshot> ancestry, Microbe microbe) {
            List<LineagePoint> timeline = new ArrayList<>(ancestry.size() + 1);
            for (AncestorSnapshot a : ancestry) {
                timeline.add(new LineagePoint(
                        a.generation(),
                        a.heatResistance(),
                        a.toxinResistance(),
                        a.speed(),
                        a.diet(),
                        a.maxHealth(),
                        a.maxEnergy()
                ));
            }
            timeline.add(new LineagePoint(
                    microbe.getAbsoluteGeneration(),
                    microbe.getHeatResistance(),
                    microbe.getToxinResistance(),
                    microbe.getSpeed(),
                    microbe.getDiet(),
                    microbe.getMaxHealth(),
                    microbe.getMaxEnergy()
            ));
            return timeline;
        }

        private List<ChartPoint> buildChartPoints(List<LineagePoint> points,
                                                  int chartX, int chartY, int chartW, int chartH,
                                                  double minValue, double maxValue,
                                                  ToDoubleFunction<LineagePoint> valueSelector) {
            List<Integer> slotXs = computeUniformSlotXPositions(chartX, chartW, points.size());
            List<ChartPoint> chartPoints = new ArrayList<>(points.size());
            for (int i = 0; i < points.size(); i++) {
                int x = slotXs.get(i);
                double value = valueSelector.applyAsDouble(points.get(i));
                double normalized = normalize(value, minValue, maxValue);
                int y = chartY + chartH - (int) Math.round(normalized * chartH);
                chartPoints.add(new ChartPoint(x, y));
            }
            return chartPoints;
        }

        private void drawLineChart(Graphics2D g2, List<ChartPoint> data, Color color) {
            if (data.size() < 2) return;

            Path2D path = new Path2D.Double();
            for (int i = 0; i < data.size(); i++) {
                double px = data.get(i).screenX();
                double py = data.get(i).screenY();
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
                double px = data.get(i).screenX();
                double py = data.get(i).screenY();

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

            double strength = Math.max(0.0, Math.min(1.0, 0.70 * hp.diet() + 0.30 * (1.0 - hp.speed())));
            double defense = Math.max(0.0, Math.min(1.0, (hp.heatResistance() + hp.toxinResistance()) * 0.5));

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
                    String.format("Health:     %.1f", hp.maxHealth()),
                    String.format("Energy:     %.1f", hp.maxEnergy()),
                    String.format("Heat:       %.1f %%", hp.heatResistance() * 100),
                    String.format("Toxin:      %.1f %%", hp.toxinResistance() * 100),
                    String.format("Speed:      %.1f %%", hp.speed() * 100),
                    String.format("Diet:       %.1f %%", hp.diet() * 100),
                    String.format("Strength:   %.1f %%", strength * 100),
                    String.format("Defense:    %.1f %%", defense * 100)
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
            Color[] lineColors = {
                    ACCENT,
                    CHART_MAX_HEALTH,
                    CHART_MAX_ENERGY,
                    CHART_HEAT,
                    CHART_TOXIN,
                    CHART_SPEED,
                    CHART_DIET,
                    CHART_MAX_HEALTH,
                    CHART_MAX_ENERGY
            };
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
                                   double heatResistance, double toxinResistance, double speed, double diet,
                                   double maxHealth, double maxEnergy) {
        }

        /**
         * The single snapshot that the mouse is currently hovering over.
         */
        private record HoveredPoint(int screenX, int screenY,
                                    int chartTop, int chartBottom,
                                    int generation,
                                    double heatResistance, double toxinResistance, double speed, double diet,
                                    double maxHealth, double maxEnergy) {
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

            Microbe.RenderState renderState = microbe.toRenderState();
            int previewSize = (int) (renderState.size() * PREVIEW_SCALE);
            int cx = CW / 2;
            int cy = y + previewSize;
            MicrobePreviewRenderer.paintPreview(g2, renderState, cx, cy, PREVIEW_SCALE);
        }

        private record LineagePoint(int generation,
                                    double heatResistance,
                                    double toxinResistance,
                                    double speed,
                                    double diet,
                                    double maxHealth,
                                    double maxEnergy) {
        }

        private record ChartPoint(int screenX, int screenY) {
        }
    }
}

