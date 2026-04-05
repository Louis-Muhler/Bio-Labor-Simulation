package com.biolab;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Overlay panel showing world-level metrics as multi-series timeline chart.
 */
public class WorldStatsPanel extends JPanel {
    static final int PANEL_WIDTH = 640;
    static final int PANEL_HEIGHT = 420;

    private static final Color BG_COLOR = OverlayTheme.PANEL_BG_ALPHA;
    private static final Color BORDER_GLOW_COLOR = OverlayTheme.ACCENT_GLOW;
    private static final Color ACCENT_COLOR = OverlayTheme.ACCENT;
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color SUBTEXT_COLOR = new Color(145, 150, 160);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault());

    private final WorldStatsStore store;

    private final JTextField searchField = new JTextField();
    private final JPanel metricListPanel = new JPanel();
    private final JPanel chipsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    private final JSpinner customStartMinutes = new JSpinner(new SpinnerNumberModel(10, 1, 1_440, 1));
    private final JSpinner customEndMinutes = new JSpinner(new SpinnerNumberModel(0, 0, 1_439, 1));
    private final ChartCanvas chartCanvas = new ChartCanvas();

    private final Map<WorldMetricId, JCheckBox> metricCheckboxes = new EnumMap<>(WorldMetricId.class);
    private final Set<WorldMetricId> selectedMetrics = new LinkedHashSet<>();
    private final Timer refreshTimer;
    private WorldStatsRangePreset activePreset = WorldStatsRangePreset.SINCE_BEGINNING;

    public WorldStatsPanel(SimulationRuntime runtime) {
        this.store = runtime.getWorldStatsStore();

        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setVisible(false);

        setLayout(new BorderLayout(12, 8));
        setBorder(new EmptyBorder(14, 14, 14, 14));

        add(buildLeftControls(), BorderLayout.WEST);
        add(buildCenterPanel(), BorderLayout.CENTER);

        selectedMetrics.add(WorldMetricId.POPULATION_ALIVE);
        selectedMetrics.add(WorldMetricId.FOOD_PELLETS_AVAILABLE);

        rebuildMetricOptions();
        updateChips();
        refreshChart();

        refreshTimer = new Timer(1000, e -> refreshChart());
        refreshTimer.setRepeats(true);
    }

    void showPanel() {
        setVisible(true);
        if (!refreshTimer.isRunning()) {
            refreshTimer.start();
        }
        refreshChart();
    }

    void hidePanel() {
        setVisible(false);
        refreshTimer.stop();
    }

    int getRenderedMetricOptionCountForTest() {
        return metricCheckboxes.size();
    }

    Set<WorldMetricId> getRenderedMetricIdsForTest() {
        return Set.copyOf(metricCheckboxes.keySet());
    }

    private JPanel buildLeftControls() {
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(250, PANEL_HEIGHT - 25));

        searchField.setBackground(OverlayTheme.CONTROL_BG);
        searchField.setForeground(TEXT_COLOR);
        searchField.setCaretColor(ACCENT_COLOR);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(45, 45, 60), 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        searchField.setToolTipText("Metrik suchen...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuildMetricOptions();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuildMetricOptions();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuildMetricOptions();
            }
        });

        metricListPanel.setOpaque(false);
        metricListPanel.setLayout(new BoxLayout(metricListPanel, BoxLayout.Y_AXIS));
        JScrollPane metricScroll = new JScrollPane(metricListPanel);
        metricScroll.setOpaque(false);
        metricScroll.getViewport().setOpaque(false);
        metricScroll.setBorder(BorderFactory.createLineBorder(new Color(45, 45, 60), 1));
        metricScroll.getVerticalScrollBar().setUnitIncrement(14);

        left.add(searchField, BorderLayout.NORTH);
        left.add(metricScroll, BorderLayout.CENTER);
        return left;
    }

    private JPanel buildCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);

        chipsPanel.setOpaque(false);

        JPanel customPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        customPanel.setOpaque(false);
        customPanel.add(makeSubLabel("Custom start (min ago):"));
        customPanel.add(customStartMinutes);
        customPanel.add(makeSubLabel("end:"));
        customPanel.add(customEndMinutes);
        customStartMinutes.addChangeListener(e -> {
            if (activePreset == WorldStatsRangePreset.CUSTOM) refreshChart();
        });
        customEndMinutes.addChangeListener(e -> {
            if (activePreset == WorldStatsRangePreset.CUSTOM) refreshChart();
        });

        JPanel top = new JPanel(new GridLayout(3, 1, 0, 6));
        top.setOpaque(false);
        top.add(chipsPanel);
        top.add(buildPresetPanel());
        top.add(customPanel);

        center.add(top, BorderLayout.NORTH);
        center.add(chartCanvas, BorderLayout.CENTER);
        return center;
    }

    private JPanel buildPresetPanel() {
        presetPanel.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (WorldStatsRangePreset preset : WorldStatsRangePreset.values()) {
            JToggleButton button = new JToggleButton(preset.label());
            stylePresetButton(button);
            if (preset == activePreset) {
                button.setSelected(true);
            }
            button.addActionListener(e -> {
                activePreset = preset;
                refreshChart();
            });
            group.add(button);
            presetPanel.add(button);
        }
        return presetPanel;
    }

    private JLabel makeSubLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(SUBTEXT_COLOR);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        return label;
    }

    private void stylePresetButton(AbstractButton button) {
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBackground(OverlayTheme.CONTROL_BG);
        button.setForeground(TEXT_COLOR);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        button.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 80), 1));
    }

    private void rebuildMetricOptions() {
        metricListPanel.removeAll();
        metricCheckboxes.clear();

        String needle = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);

        Map<WorldMetricCategory, List<WorldMetricDefinition>> grouped = WorldMetricRegistry.definitions().stream()
                .filter(def -> needle.isEmpty() || def.label().toLowerCase(Locale.ROOT).contains(needle))
                .collect(Collectors.groupingBy(
                        WorldMetricDefinition::category,
                        () -> new EnumMap<>(WorldMetricCategory.class),
                        Collectors.toList()));

        for (WorldMetricCategory category : WorldMetricCategory.values()) {
            List<WorldMetricDefinition> defs = grouped.get(category);
            if (defs == null || defs.isEmpty()) {
                continue;
            }

            JLabel title = new JLabel(category.label());
            title.setForeground(ACCENT_COLOR);
            title.setFont(new Font("Segoe UI", Font.BOLD, 12));
            title.setBorder(new EmptyBorder(8, 0, 4, 0));
            metricListPanel.add(title);

            defs.sort(Comparator.comparing(WorldMetricDefinition::label));
            for (WorldMetricDefinition def : defs) {
                JCheckBox checkBox = new JCheckBox(def.label());
                checkBox.setOpaque(false);
                checkBox.setForeground(TEXT_COLOR);
                checkBox.setFocusPainted(false);
                checkBox.setSelected(selectedMetrics.contains(def.id()));
                checkBox.addActionListener(e -> {
                    if (checkBox.isSelected()) {
                        selectedMetrics.add(def.id());
                    } else {
                        selectedMetrics.remove(def.id());
                    }
                    updateChips();
                    refreshChart();
                });
                metricCheckboxes.put(def.id(), checkBox);
                metricListPanel.add(checkBox);
            }
        }

        metricListPanel.revalidate();
        metricListPanel.repaint();
    }

    private void updateChips() {
        chipsPanel.removeAll();
        if (selectedMetrics.isEmpty()) {
            JLabel empty = new JLabel("Keine Metrik ausgewahlt");
            empty.setForeground(SUBTEXT_COLOR);
            chipsPanel.add(empty);
        } else {
            for (WorldMetricId id : selectedMetrics) {
                WorldMetricDefinition def = WorldMetricRegistry.definition(id);
                JButton chip = new JButton(def.label() + "  x");
                chip.setFocusPainted(false);
                chip.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                chip.setForeground(Color.BLACK);
                chip.setBackground(def.color());
                chip.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                chip.addActionListener(e -> {
                    selectedMetrics.remove(id);
                    JCheckBox cb = metricCheckboxes.get(id);
                    if (cb != null) cb.setSelected(false);
                    updateChips();
                    refreshChart();
                });
                chipsPanel.add(chip);
            }
        }
        chipsPanel.revalidate();
        chipsPanel.repaint();
    }

    private void refreshChart() {
        if (selectedMetrics.isEmpty()) {
            chartCanvas.setChartData(List.of(), List.of(), false);
            return;
        }

        long now = System.currentTimeMillis();
        long earliest = store.firstTimestampMillis();
        if (earliest == 0L) {
            earliest = now;
        }

        long customStart = now - ((Number) customStartMinutes.getValue()).longValue() * 60_000L;
        long customEnd = now - ((Number) customEndMinutes.getValue()).longValue() * 60_000L;
        if (customStart > customEnd) {
            long tmp = customStart;
            customStart = customEnd;
            customEnd = tmp;
        }

        long from = activePreset.resolveStartMillis(now, earliest, customStart, customEnd);
        long to = activePreset.resolveEndMillis(now, customStart, customEnd);

        int maxPoints = Math.max(80, chartCanvas.getWidth() - 50);
        List<WorldStatsSample> currentSamples = store.queryRange(selectedMetrics, from, to, maxPoints);

        List<WorldMetricDefinition> selectedDefs = selectedMetrics.stream()
                .map(WorldMetricRegistry::definition)
                .filter(def -> def != null)
                .toList();

        boolean mixedUnits = selectedDefs.stream().map(WorldMetricDefinition::unit).distinct().count() > 1;
        chartCanvas.setChartData(currentSamples, selectedDefs, mixedUnits);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_COLOR);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g2.setColor(BORDER_GLOW_COLOR);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
            g2.setColor(ACCENT_COLOR);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 16, 16);
        } finally {
            g2.dispose();
        }
    }

    private static final class ChartCanvas extends JPanel {
        private static final Color GRID = new Color(42, 44, 52);
        private static final Color AXIS_TEXT = new Color(160, 170, 180);
        private static final Color CHART_BG = new Color(10, 10, 14, 170);
        private static final int LEFT_PAD = 45;
        private static final int RIGHT_PAD = 12;
        private static final int TOP_PAD = 14;
        private static final int BOTTOM_PAD = 25;

        private List<WorldStatsSample> samples = List.of();
        private List<WorldMetricDefinition> definitions = List.of();
        private boolean normalize = false;
        private int hoveredIndex = -1;

        ChartCanvas() {
            setOpaque(false);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    hoveredIndex = findNearestIndex(e.getX());
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoveredIndex = -1;
                    repaint();
                }
            };
            addMouseMotionListener(mouse);
        }

        void setChartData(List<WorldStatsSample> samples,
                          List<WorldMetricDefinition> definitions,
                          boolean normalize) {
            this.samples = samples == null ? List.of() : samples;
            this.definitions = definitions == null ? List.of() : definitions;
            this.normalize = normalize;
            repaint();
        }

        private int findNearestIndex(int mouseX) {
            if (samples.isEmpty()) return -1;
            int chartW = Math.max(1, getWidth() - LEFT_PAD - RIGHT_PAD);
            int best = -1;
            int bestDx = Integer.MAX_VALUE;
            long minTs = samples.get(0).timestampMillis();
            long maxTs = samples.get(samples.size() - 1).timestampMillis();
            long tsRange = Math.max(1L, maxTs - minTs);
            for (int i = 0; i < samples.size(); i++) {
                int x = LEFT_PAD + (int) ((samples.get(i).timestampMillis() - minTs) * chartW / tsRange);
                int dx = Math.abs(mouseX - x);
                if (dx < bestDx) {
                    bestDx = dx;
                    best = i;
                }
            }
            return bestDx <= 18 ? best : -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int chartW = Math.max(1, w - LEFT_PAD - RIGHT_PAD);
                int chartH = Math.max(1, h - TOP_PAD - BOTTOM_PAD);
                int chartX = LEFT_PAD;
                int chartY = TOP_PAD;

                g2.setColor(CHART_BG);
                g2.fillRoundRect(chartX, chartY, chartW, chartH, 10, 10);

                drawGrid(g2, chartX, chartY, chartW, chartH);

                if (samples.isEmpty() || definitions.isEmpty()) {
                    g2.setColor(AXIS_TEXT);
                    g2.drawString("Keine Daten fur den gewahlten Bereich", chartX + 12, chartY + 24);
                    return;
                }

                long minTs = samples.get(0).timestampMillis();
                long maxTs = samples.get(samples.size() - 1).timestampMillis();
                long tsRange = Math.max(1L, maxTs - minTs);

                Map<WorldMetricId, double[]> ranges = computeRanges();
                double[] globalRange = computeGlobalRange();
                for (WorldMetricDefinition def : definitions) {
                    drawSeries(g2, def, ranges.get(def.id()), globalRange,
                            chartX, chartY, chartW, chartH, minTs, tsRange);
                }

                drawLegend(g2, chartX, chartY, chartW);
                drawAxesLabels(g2, chartX, chartY, chartW, chartH, minTs, maxTs);

                if (hoveredIndex >= 0 && hoveredIndex < samples.size()) {
                    drawHover(g2, hoveredIndex, chartX, chartY, chartW, chartH, minTs, tsRange);
                }
            } finally {
                g2.dispose();
            }
        }

        private void drawGrid(Graphics2D g2, int x, int y, int w, int h) {
            g2.setColor(GRID);
            g2.setStroke(new BasicStroke(1f));
            for (int i = 0; i <= 4; i++) {
                int gy = y + i * h / 4;
                g2.drawLine(x, gy, x + w, gy);
            }
            for (int i = 0; i <= 5; i++) {
                int gx = x + i * w / 5;
                g2.drawLine(gx, y, gx, y + h);
            }
        }

        private Map<WorldMetricId, double[]> computeRanges() {
            Map<WorldMetricId, double[]> out = new EnumMap<>(WorldMetricId.class);
            for (WorldMetricDefinition def : definitions) {
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                for (WorldStatsSample sample : samples) {
                    double v = sample.metricValues().getOrDefault(def.id(), 0.0);
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
                if (Double.isInfinite(min) || Double.isInfinite(max)) {
                    min = 0.0;
                    max = 1.0;
                }
                if (Math.abs(max - min) < 1e-9) {
                    max = min + 1.0;
                }
                out.put(def.id(), new double[]{min, max});
            }
            return out;
        }

        private void drawSeries(Graphics2D g2,
                                WorldMetricDefinition def,
                                double[] range,
                                double[] globalRange,
                                int chartX,
                                int chartY,
                                int chartW,
                                int chartH,
                                long minTs,
                                long tsRange) {
            Path2D path = new Path2D.Double();
            for (int i = 0; i < samples.size(); i++) {
                WorldStatsSample sample = samples.get(i);
                double raw = sample.metricValues().getOrDefault(def.id(), 0.0);
                double normalized = normalize
                        ? normalize(raw, range[0], range[1])
                        : normalize(raw, globalRange[0], globalRange[1]);
                double px = chartX + ((sample.timestampMillis() - minTs) * 1.0 / tsRange) * chartW;
                double py = chartY + chartH - normalized * chartH;
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }

            Composite original = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
            g2.setColor(def.color());
            g2.setStroke(new BasicStroke(3f));
            g2.draw(path);

            g2.setComposite(original);
            g2.setColor(def.color());
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(path);
        }

        private double[] computeGlobalRange() {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (WorldMetricDefinition def : definitions) {
                for (WorldStatsSample sample : samples) {
                    min = Math.min(min, sample.metricValues().getOrDefault(def.id(), 0.0));
                    max = Math.max(max, sample.metricValues().getOrDefault(def.id(), 0.0));
                }
            }
            if (Double.isInfinite(min) || Double.isInfinite(max)) {
                return new double[]{0.0, 1.0};
            }
            if (Math.abs(max - min) < 1e-9) {
                max = min + 1.0;
            }
            return new double[]{min, max};
        }

        private double normalize(double value, double min, double max) {
            double span = Math.max(1e-9, max - min);
            return (value - min) / span;
        }

        private void drawLegend(Graphics2D g2, int chartX, int chartY, int chartW) {
            int x = chartX + 8;
            int y = chartY + 14;
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            for (WorldMetricDefinition def : definitions) {
                g2.setColor(def.color());
                g2.fillRect(x, y - 8, 10, 3);
                g2.setColor(new Color(210, 215, 220));
                g2.drawString(def.label(), x + 14, y);
                x += g2.getFontMetrics().stringWidth(def.label()) + 28;
                if (x > chartX + chartW - 120) {
                    x = chartX + 8;
                    y += 14;
                }
            }
        }

        private void drawAxesLabels(Graphics2D g2, int chartX, int chartY, int chartW, int chartH, long minTs, long maxTs) {
            g2.setColor(AXIS_TEXT);
            g2.setFont(new Font("Consolas", Font.PLAIN, 11));
            g2.drawString(TIME_FORMAT.format(Instant.ofEpochMilli(minTs)), chartX, chartY + chartH + 16);
            String end = TIME_FORMAT.format(Instant.ofEpochMilli(maxTs));
            int endW = g2.getFontMetrics().stringWidth(end);
            g2.drawString(end, chartX + chartW - endW, chartY + chartH + 16);
            if (normalize) {
                g2.drawString("Normalized", chartX, chartY - 2);
            }
        }

        private void drawHover(Graphics2D g2,
                               int index,
                               int chartX,
                               int chartY,
                               int chartW,
                               int chartH,
                               long minTs,
                               long tsRange) {
            WorldStatsSample sample = samples.get(index);
            int x = chartX + (int) (((sample.timestampMillis() - minTs) * 1.0 / tsRange) * chartW);
            g2.setColor(new Color(255, 255, 255, 65));
            g2.drawLine(x, chartY, x, chartY + chartH);

            List<String> lines = new ArrayList<>();
            lines.add(TIME_FORMAT.format(Instant.ofEpochMilli(sample.timestampMillis())) + "  (tick " + sample.tick() + ")");
            for (WorldMetricDefinition def : definitions) {
                double v = sample.metricValues().getOrDefault(def.id(), 0.0);
                lines.add(def.label() + ": " + String.format(Locale.ROOT, "%.2f", v) + " " + def.unit());
            }

            g2.setFont(new Font("Consolas", Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();
            int width = lines.stream().mapToInt(fm::stringWidth).max().orElse(120) + 12;
            int height = lines.size() * fm.getHeight() + 8;

            int tx = x + 10;
            if (tx + width > getWidth()) {
                tx = x - width - 10;
            }
            int ty = Math.max(4, chartY + 8);

            g2.setColor(new Color(18, 18, 18, 235));
            g2.fillRoundRect(tx, ty, width, height, 8, 8);
            g2.setColor(OverlayTheme.ACCENT);
            g2.drawRoundRect(tx, ty, width, height, 8, 8);

            int y = ty + fm.getAscent() + 4;
            for (int i = 0; i < lines.size(); i++) {
                if (i == 0) g2.setColor(OverlayTheme.ACCENT);
                else g2.setColor(TEXT_COLOR);
                g2.drawString(lines.get(i), tx + 6, y);
                y += fm.getHeight();
            }
        }
    }
}


