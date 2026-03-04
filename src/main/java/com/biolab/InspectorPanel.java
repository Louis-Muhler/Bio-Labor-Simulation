package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.function.Consumer;

/**
 * Inspector panel that displays live data for a selected {@link Microbe}.
 *
 * <h3>Tab system</h3>
 * A narrow sidebar on the left holds two icon buttons:
 * <ol>
 *   <li><b>Info tab</b> – statistics, vital signs, genetic profile, lineage
 *       evolution chart, and colour specimen preview (original view).</li>
 *   <li><b>Lineage tab</b> – interactive family tree showing parents,
 *       grandparents, and children as connected nodes.</li>
 * </ol>
 *
 * <h3>Layout architecture (frame vs. scrollable content)</h3>
 * <ol>
 *   <li><b>Outer panel</b> ({@code InspectorPanel} itself) – paints the static
 *       rounded dark background and neon-cyan border. Never scrolls.</li>
 *   <li><b>Tab sidebar</b> – a narrow vertical strip with icon buttons.</li>
 *   <li><b>Card panel</b> – a {@link CardLayout} holding the two content
 *       scroll-panes (Info and Lineage).</li>
 * </ol>
 */
public class InspectorPanel extends JPanel {

    // ── Frame geometry ───────────────────────────────────────────────────
    static final int PANEL_WIDTH = 320;
    // ── Tab identifiers ──────────────────────────────────────────────────
    private static final String TAB_INFO = "INFO";
    private static final String TAB_LINEAGE = "LINEAGE";
    private static final int SIDEBAR_WIDTH = 36;
    private static final int FRAME_MARGIN = 20;
    private static final int CORNER_RADIUS = 15;
    private static final int FRAME_PADDING = 15;
    private static final int INSET = FRAME_MARGIN + FRAME_PADDING;
    private static final int NO_SELECTION_HEIGHT = 150;

    // ── Frame colours ────────────────────────────────────────────────────
    private static final Color BG_COLOR = new Color(18, 18, 18, 240);
    private static final Color ACCENT_COLOR = new Color(0, 255, 255);
    private static final Color BORDER_GLOW_COLOR = new Color(0, 255, 255, 80);
    private static final Color NO_SELECTION_BG = new Color(18, 18, 18, 150);
    private static final Color NO_SELECTION_BORDER = new Color(40, 40, 50, 200);
    private static final Color NO_SELECTION_TEXT = new Color(220, 220, 220, 180);
    private static final Color TAB_ACTIVE_BG = new Color(0, 255, 255, 40);
    private static final Color TAB_HOVER_BG = new Color(0, 255, 255, 20);

    // ── Frame fonts & strokes ────────────────────────────────────────────
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font MONO_FONT = new Font("Consolas", Font.PLAIN, 11);
    private static final BasicStroke STROKE_1 = new BasicStroke(1);
    private static final BasicStroke STROKE_2 = new BasicStroke(2);
    private static final BasicStroke STROKE_3 = new BasicStroke(3);

    // ── Components ───────────────────────────────────────────────────────
    private final InfoCanvas infoCanvas;
    private final LineageCanvas lineageCanvas;
    private final JScrollPane infoScrollPane;
    private final JScrollPane lineageScrollPane;
    private final JPanel cardPanel;
    private final CardLayout cardLayout;
    private final TabButton infoTabBtn;
    private final TabButton lineageTabBtn;

    private String activeTab = TAB_INFO;

    /**
     * Callback invoked when the user clicks a living relative in the tree.
     */
    private Consumer<Microbe> onRelativeClicked;

    // ────────────────────────────────────────────────────────────────────
    // Construction
    // ────────────────────────────────────────────────────────────────────

    public InspectorPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());

        // ── Info tab content ─────────────────────────────────────────────
        infoCanvas = new InfoCanvas();
        infoScrollPane = createInvisibleScrollPane(infoCanvas);

        // ── Lineage tab content ──────────────────────────────────────────
        lineageCanvas = new LineageCanvas();
        lineageScrollPane = createInvisibleScrollPane(lineageCanvas);

        // ── Card panel holding both tabs ─────────────────────────────────
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setOpaque(false);
        cardPanel.add(infoScrollPane, TAB_INFO);
        cardPanel.add(lineageScrollPane, TAB_LINEAGE);

        // ── Sidebar with tab buttons ─────────────────────────────────────
        JPanel sidebar = new JPanel();
        sidebar.setOpaque(false);
        sidebar.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        sidebar.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 8));

        infoTabBtn = new TabButton("I", true);
        lineageTabBtn = new TabButton("T", false);

        infoTabBtn.addActionListener(e -> switchTab(TAB_INFO));
        lineageTabBtn.addActionListener(e -> switchTab(TAB_LINEAGE));

        sidebar.add(infoTabBtn);
        sidebar.add(lineageTabBtn);

        add(sidebar, BorderLayout.WEST);
        add(cardPanel, BorderLayout.CENTER);
    }

    /**
     * Creates a transparent, scrollbar-hidden {@link JScrollPane} for the given content.
     */
    private JScrollPane createInvisibleScrollPane(JPanel content) {
        JScrollPane sp = new JScrollPane(content);
        sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder(INSET, FRAME_PADDING, INSET, INSET));
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    // ────────────────────────────────────────────────────────────────────
    // Tab switching
    // ────────────────────────────────────────────────────────────────────

    private void switchTab(String tab) {
        activeTab = tab;
        cardLayout.show(cardPanel, tab);
        infoTabBtn.setActive(TAB_INFO.equals(tab));
        lineageTabBtn.setActive(TAB_LINEAGE.equals(tab));
        repaint();
    }

    // ────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────

    /**
     * Sets the callback for when a living relative is clicked in the lineage tree.
     */
    public void setOnRelativeClicked(Consumer<Microbe> callback) {
        this.onRelativeClicked = callback;
    }

    private static void drawCenteredString(Graphics2D g2, String text, int x, int y) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, x - fm.stringWidth(text) / 2, y);
    }

    public void hidePanel() {
        infoCanvas.selectedMicrobe = null;
        lineageCanvas.selectedMicrobe = null;
        setVisible(false);
        if (getParent() != null) getParent().repaint();
    }

    public void setSelectedMicrobe(Microbe microbe) {
        infoCanvas.selectedMicrobe = microbe;
        lineageCanvas.selectedMicrobe = microbe;
        infoCanvas.recalculatePreferredSize();
        lineageCanvas.recalculatePreferredSize();
        infoScrollPane.revalidate();
        lineageScrollPane.revalidate();
        repaint();
    }

    public void showPanel() {
        setVisible(true);
        infoCanvas.recalculatePreferredSize();
        lineageCanvas.recalculatePreferredSize();
        infoScrollPane.revalidate();
        lineageScrollPane.revalidate();
        repaint();
    }

    // ────────────────────────────────────────────────────────────────────
    // Outer frame painting – static, never scrolls
    // ────────────────────────────────────────────────────────────────────

    /**
     * Returns the ideal panel height that fits all content without scrolling.
     */
    public int getPreferredPanelHeight() {
        Microbe m = infoCanvas.selectedMicrobe;
        if (m == null || m.isDead()) {
            return NO_SELECTION_HEIGHT + 2 * FRAME_MARGIN;
        }
        int h = TAB_INFO.equals(activeTab)
                ? infoCanvas.computeContentHeight()
                : lineageCanvas.computeContentHeight();
        return h + 2 * INSET;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Microbe microbe = infoCanvas.selectedMicrobe;
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
    // Tab button – small square icon button in the sidebar
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Small icon button used in the tab sidebar. Draws a single character
     * icon and highlights when active or hovered.
     */
    private static class TabButton extends JButton {
        private boolean active;

        TabButton(String iconChar, boolean active) {
            super(iconChar);
            this.active = active;
            setPreferredSize(new Dimension(28, 28));
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setForeground(ACCENT_COLOR);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (active) {
                g2.setColor(TAB_ACTIVE_BG);
            } else if (getModel().isRollover()) {
                g2.setColor(TAB_HOVER_BG);
            } else {
                g2.setColor(new Color(0, 0, 0, 0));
            }
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

            g2.setFont(getFont());
            g2.setColor(active ? ACCENT_COLOR : new Color(150, 150, 150));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(getText())) / 2;
            int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(getText(), tx, ty);

            // Active tab indicator bar on the left edge
            if (active) {
                g2.setColor(ACCENT_COLOR);
                g2.fillRoundRect(0, 4, 3, getHeight() - 8, 2, 2);
            }

            g2.dispose();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // INFO TAB – original content canvas
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Renders the Info tab content: title, vital signs, genetic profile,
     * lineage evolution chart, and specimen colour preview.
     */
    private static class InfoCanvas extends JPanel {
        private static final Color ACCENT = new Color(0, 255, 255);
        private static final Color TEXT_COLOR = new Color(220, 220, 220);
        private static final Color GRID_COLOR = new Color(40, 40, 50);
        private static final Color CHART_BG = new Color(0, 0, 0, 100);
        private static final Color CHART_HEAT = new Color(255, 100, 100);
        private static final Color CHART_TOXIN = new Color(100, 255, 100);
        private static final Color CHART_SPEED = new Color(100, 150, 255);
        private static final BasicStroke STROKE_1 = new BasicStroke(1);
        private static final BasicStroke STROKE_2 = new BasicStroke(2);
        private static final BasicStroke STROKE_3 = new BasicStroke(3);
        private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 16);
        private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 12);
        private static final Font MONO_FONT = new Font("Consolas", Font.PLAIN, 13);
        private static final AlphaComposite GLOW_COMPOSITE =
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 100 / 255f);
        private static final AlphaComposite DOT_GLOW_COMPOSITE =
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 150 / 255f);
        private static final int LINE_H = 22;
        private static final int SECTION_GAP = 20;
        private static final int CHART_HEIGHT = 120;
        private static final int INDENT = 15;
        private static final int DEEP_INDENT = 30;
        private static final int COLOR_CODE_GAP = 25;
        private static final double PREVIEW_SCALE = 5.0;
        private static final int CW = PANEL_WIDTH - 2 * FRAME_MARGIN - 2 * FRAME_PADDING - SIDEBAR_WIDTH;
        private Microbe selectedMicrobe;

        InfoCanvas() {
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

        int computeContentHeight() {
            Microbe m = this.selectedMicrobe;
            if (m == null || m.isDead()) return 0;

            int h = 0;
            h += LINE_H + 5;
            h += LINE_H;
            h += sectionHeight();
            h += SECTION_GAP;
            h += sectionHeight();
            h += SECTION_GAP;

            List<AncestorSnapshot> ancestry = m.getAncestry();
            if (!ancestry.isEmpty()) {
                h += LINE_H + 5;
                h += CHART_HEIGHT;
                h += 15;
                h += LINE_H;
                h += COLOR_CODE_GAP;
            }

            h += SECTION_GAP;
            int previewSize = (int) (m.getSize() * PREVIEW_SCALE);
            int maxGlowRadius = (int) (3 * 2 * PREVIEW_SCALE);
            h += LINE_H + 40 + previewSize + maxGlowRadius + 20;
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
                    y += COLOR_CODE_GAP;
                }

                // Colour indicator
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

            java.util.List<DataPoint> heat = new java.util.ArrayList<>();
            java.util.List<DataPoint> toxin = new java.util.ArrayList<>();
            java.util.List<DataPoint> speed = new java.util.ArrayList<>();

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

        private void drawLineChart(Graphics2D g2, java.util.List<DataPoint> data,
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
            drawCentered(g2, "SPECIMEN", CW / 2, y);
            y += 40;

            int size = (int) (microbe.getSize() * PREVIEW_SCALE);
            int cx = CW / 2;
            int cy = y + size;
            int x = cx - size / 2;
            int baseY = cy - size / 2;

            Color mc = microbe.getColor();
            Color brightColor = new Color(
                    Math.min(255, mc.getRed() + 40),
                    Math.min(255, mc.getGreen() + 40),
                    Math.min(255, mc.getBlue() + 40));

            Composite orig = g2.getComposite();
            double healthRatio = microbe.getHealthRatio();

            for (int i = 3; i > 0; i--) {
                float alpha = (float) ((20 + i * 15) * healthRatio / 255f);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
                g2.setColor(mc);
                int glowSize = size + (int) (i * 4 * PREVIEW_SCALE);
                g2.fillOval(x - (int) (i * 2 * PREVIEW_SCALE), baseY - (int) (i * 2 * PREVIEW_SCALE), glowSize, glowSize);
            }

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 220 / 255f));
            g2.setColor(brightColor);
            g2.fillOval(x, baseY, size, size);

            g2.setComposite(orig);
            g2.setColor(mc);
            g2.fillOval(x + 1, baseY + 1, size - 2, size - 2);
        }

        private record DataPoint(int generation, double value) {
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // LINEAGE TAB – family tree visualization
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Renders an interactive family tree for the selected microbe.
     * <ul>
     *   <li>Ancestors are drawn upward (parent, grandparent, …).</li>
     *   <li>Children are drawn downward.</li>
     *   <li>Dead relatives show a red "✕" overlay.</li>
     *   <li>Hovering a node shows a tooltip with stats.</li>
     *   <li>Clicking a living node fires the {@link #onRelativeClicked} callback.</li>
     * </ul>
     */
    private class LineageCanvas extends JPanel {
        private static final Color ACCENT = new Color(0, 255, 255);
        private static final Color DIM_TEXT = new Color(140, 140, 140);
        private static final Color GRID_COLOR = new Color(40, 40, 50);
        private static final Color NODE_BG = new Color(30, 30, 40);
        private static final Color NODE_BORDER = new Color(0, 255, 255, 120);
        private static final Color DEAD_CROSS_COLOR = new Color(255, 80, 80);
        private static final Color LINE_COLOR = new Color(0, 255, 255, 60);
        private static final Color SELF_GLOW = new Color(0, 255, 255, 50);

        private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 16);
        private static final Font NODE_FONT = new Font("Segoe UI", Font.PLAIN, 10);
        private static final BasicStroke STROKE_1 = new BasicStroke(1);
        private static final BasicStroke STROKE_2 = new BasicStroke(2);
        private static final BasicStroke DASH_STROKE = new BasicStroke(
                1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 4}, 0);

        /**
         * Diameter of tree nodes in pixels.
         */
        private static final int NODE_SIZE = 32;
        /**
         * Vertical spacing between tree layers.
         */
        private static final int LAYER_GAP = 55;
        /**
         * Horizontal spacing between sibling nodes.
         */
        private static final int SIBLING_GAP = 42;
        /**
         * Maximum children shown before a "+N more" label appears.
         */
        private static final int MAX_CHILDREN_SHOWN = 5;

        private static final int CW = PANEL_WIDTH - 2 * FRAME_MARGIN - 2 * FRAME_PADDING - SIDEBAR_WIDTH;
        /**
         * Flat list of all drawable nodes, rebuilt every paint cycle.
         */
        private final java.util.List<TreeNode> nodes = new java.util.ArrayList<>();
        Microbe selectedMicrobe;
        /**
         * Index of the node currently under the mouse, or -1 for none.
         */
        private int hoveredNodeIndex = -1;

        LineageCanvas() {
            setOpaque(false);
            ToolTipManager.sharedInstance().setInitialDelay(200);

            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int prev = hoveredNodeIndex;
                    hoveredNodeIndex = findNodeAt(e.getX(), e.getY());
                    if (hoveredNodeIndex != prev) {
                        if (hoveredNodeIndex >= 0) {
                            TreeNode tn = nodes.get(hoveredNodeIndex);
                            setToolTipText(buildTooltip(tn.microbe));
                            setCursor(Cursor.getPredefinedCursor(
                                    tn.microbe.isDead() ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                        } else {
                            setToolTipText(null);
                            setCursor(Cursor.getDefaultCursor());
                        }
                        repaint();
                    }
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int idx = findNodeAt(e.getX(), e.getY());
                    if (idx >= 0) {
                        Microbe clicked = nodes.get(idx).microbe;
                        if (!clicked.isDead() && clicked != selectedMicrobe && onRelativeClicked != null) {
                            onRelativeClicked.accept(clicked);
                        }
                    }
                }
            });
        }

        /**
         * Returns the index in {@link #nodes} that contains the screen point, or -1.
         */
        private int findNodeAt(int sx, int sy) {
            int halfNode = NODE_SIZE / 2;
            for (int i = 0; i < nodes.size(); i++) {
                TreeNode tn = nodes.get(i);
                int dx = sx - tn.cx;
                int dy = sy - tn.cy;
                if (dx * dx + dy * dy <= halfNode * halfNode) return i;
            }
            return -1;
        }

        /**
         * Builds an HTML tooltip showing a microbe's key stats.
         */
        private String buildTooltip(Microbe m) {
            String status = m.isDead()
                    ? "<b style='color:#FF5050;'>DEAD</b>"
                    : "<b style='color:#00FF00;'>ALIVE</b>";
            return String.format(
                    "<html><body style='padding:4px;font-family:Consolas;font-size:10px;'>"
                            + "ID: %d &nbsp; %s<br>"
                            + "Heat: %.2f &nbsp; Toxin: %.2f<br>"
                            + "Speed: %.2f &nbsp; Age: %d"
                            + "</body></html>",
                    m.getId(), status, m.getHeatResistance(), m.getToxinResistance(),
                    m.getSpeed(), m.getAge());
        }

        void recalculatePreferredSize() {
            setPreferredSize(new Dimension(CW, computeContentHeight()));
        }

        int computeContentHeight() {
            Microbe m = this.selectedMicrobe;
            if (m == null || m.isDead()) return 0;

            int layers = 1; // self layer

            // Count ancestor layers upward
            Microbe ancestor = m.getParent();
            while (ancestor != null) {
                layers++;
                ancestor = ancestor.getParent();
            }

            // Children layer (only if there are children)
            if (!m.getChildren().isEmpty()) layers++;

            // Title + separator + tree layers + bottom padding
            return 22 + 5 + 22 + layers * LAYER_GAP + 30;
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

                nodes.clear();
                int y = 0;

                // Title
                g2.setColor(ACCENT);
                g2.setFont(TITLE_FONT);
                InfoCanvas.drawCentered(g2, "LINEAGE TREE", CW / 2, y + 15);
                y += 22 + 5;

                // Separator
                g2.setColor(GRID_COLOR);
                g2.fillRect(0, y, CW, 2);
                y += 22;

                // ── Collect ancestor chain (self → parent → grandparent …) ──
                java.util.List<Microbe> ancestors = new java.util.ArrayList<>();
                Microbe cur = microbe.getParent();
                while (cur != null) {
                    ancestors.add(cur);
                    cur = cur.getParent();
                }

                int cx = CW / 2;
                int prevCy = -1;

                // ── Ancestor layers (oldest at top, newest at bottom) ────
                for (int i = ancestors.size() - 1; i >= 0; i--) {
                    Microbe anc = ancestors.get(i);
                    int nodeCy = y + NODE_SIZE / 2;
                    drawConnector(g2, cx, prevCy, cx, nodeCy);
                    String label = "Gen -" + (i + 1);
                    drawNode(g2, cx, nodeCy, anc, label, false, nodes.size() == hoveredNodeIndex);
                    nodes.add(new TreeNode(cx, nodeCy, anc));
                    prevCy = nodeCy;
                    y += LAYER_GAP;
                }

                // ── Self node (highlighted) ──────────────────────────────
                int selfCy = y + NODE_SIZE / 2;
                drawConnector(g2, cx, prevCy, cx, selfCy);
                drawNode(g2, cx, selfCy, microbe, "SELF", true, false);
                nodes.add(new TreeNode(cx, selfCy, microbe));
                prevCy = selfCy;
                y += LAYER_GAP;

                // ── Children layer ───────────────────────────────────────
                List<Microbe> children = microbe.getChildren();
                if (!children.isEmpty()) {
                    int shownCount = Math.min(children.size(), MAX_CHILDREN_SHOWN);
                    int totalWidth = shownCount * NODE_SIZE + (shownCount - 1) * (SIBLING_GAP - NODE_SIZE);
                    int startX = cx - totalWidth / 2 + NODE_SIZE / 2;

                    for (int i = 0; i < shownCount; i++) {
                        Microbe child = children.get(i);
                        int childCx = startX + i * SIBLING_GAP;
                        int childCy = y + NODE_SIZE / 2;
                        drawConnector(g2, cx, prevCy, childCx, childCy);
                        drawNode(g2, childCx, childCy, child, "C" + (i + 1),
                                false, nodes.size() == hoveredNodeIndex);
                        nodes.add(new TreeNode(childCx, childCy, child));
                    }

                    // "+N more" label if children are truncated
                    if (children.size() > MAX_CHILDREN_SHOWN) {
                        g2.setColor(DIM_TEXT);
                        g2.setFont(NODE_FONT);
                        String moreText = "+" + (children.size() - MAX_CHILDREN_SHOWN) + " more";
                        g2.drawString(moreText,
                                startX + shownCount * SIBLING_GAP - 5,
                                y + NODE_SIZE / 2 + 4);
                    }
                }
            } finally {
                g2.dispose();
            }
        }

        /**
         * Draws a dashed connector line between a parent node and a child node.
         */
        private void drawConnector(Graphics2D g2, int fromCx, int fromCy, int toCx, int toCy) {
            if (fromCy < 0) return;
            g2.setColor(LINE_COLOR);
            g2.setStroke(DASH_STROKE);
            int midY = (fromCy + NODE_SIZE / 2 + toCy - NODE_SIZE / 2) / 2;
            g2.drawLine(fromCx, fromCy + NODE_SIZE / 2, fromCx, midY);
            g2.drawLine(fromCx, midY, toCx, midY);
            g2.drawLine(toCx, midY, toCx, toCy - NODE_SIZE / 2);
            g2.setStroke(STROKE_1);
        }

        /**
         * Draws a single tree node circle with colour fill, label, and status overlays.
         *
         * @param cx      centre x of the node
         * @param cy      centre y of the node
         * @param m       the microbe this node represents
         * @param label   short text below the node (e.g. "SELF", "Gen -1", "C1")
         * @param isSelf  whether this is the currently selected microbe (extra glow)
         * @param hovered whether the mouse is hovering over this node
         */
        private void drawNode(Graphics2D g2, int cx, int cy, Microbe m,
                              String label, boolean isSelf, boolean hovered) {
            int r = NODE_SIZE / 2;
            int x = cx - r;
            int y = cy - r;

            // Glow ring for self node
            if (isSelf) {
                g2.setColor(SELF_GLOW);
                g2.fillOval(x - 6, y - 6, NODE_SIZE + 12, NODE_SIZE + 12);
            }

            // Hover highlight
            if (hovered) {
                g2.setColor(new Color(0, 255, 255, 30));
                g2.fillOval(x - 4, y - 4, NODE_SIZE + 8, NODE_SIZE + 8);
            }

            // Node background
            g2.setColor(NODE_BG);
            g2.fillOval(x, y, NODE_SIZE, NODE_SIZE);

            // Microbe colour fill (inner ring)
            g2.setColor(m.getColor());
            g2.fillOval(x + 3, y + 3, NODE_SIZE - 6, NODE_SIZE - 6);

            // Border
            g2.setColor(isSelf ? ACCENT : NODE_BORDER);
            g2.setStroke(isSelf ? STROKE_2 : STROKE_1);
            g2.drawOval(x, y, NODE_SIZE, NODE_SIZE);

            // Dead overlay: red "✕"
            if (m.isDead()) {
                g2.setColor(DEAD_CROSS_COLOR);
                g2.setStroke(STROKE_2);
                int offset = 6;
                g2.drawLine(x + offset, y + offset, x + NODE_SIZE - offset, y + NODE_SIZE - offset);
                g2.drawLine(x + NODE_SIZE - offset, y + offset, x + offset, y + NODE_SIZE - offset);
            }

            // Label below the node
            g2.setColor(isSelf ? ACCENT : DIM_TEXT);
            g2.setFont(NODE_FONT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, cx - fm.stringWidth(label) / 2, cy + r + 14);
        }

        /** Data holder for a drawn node position and its associated microbe. */
        private record TreeNode(int cx, int cy, Microbe microbe) {
        }
    }
}
