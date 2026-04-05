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
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Overlay panel showing world-level metrics as multi-series timeline chart.
 */
public class WorldStatsPanel extends JPanel {
    static final int PANEL_WIDTH = 640;
    static final int PANEL_HEIGHT = 420;
    private static final int MIN_WIDTH = 500;
    private static final int MIN_HEIGHT = 320;
    private static final int EDGE_DRAG = 8;

    private static final Color BG_COLOR = OverlayTheme.PANEL_BG_ALPHA;
    private static final Color BORDER_GLOW_COLOR = OverlayTheme.ACCENT_GLOW;
    private static final Color ACCENT_COLOR = OverlayTheme.ACCENT;
    private static final Color TEXT_COLOR = new Color(220, 220, 220);

    private final WorldStatsStore store;
    private final SettingsManager settingsManager;

    private final JTextField searchField = new JTextField();
    private final JPanel metricListPanel = new JPanel();
    private final JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JPanel yAxisPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final ChartCanvas chartCanvas = new ChartCanvas();

    private final ModernButton exportCsvButton = new ModernButton("CSV");
    private final ModernButton exportPngButton = new ModernButton("PNG");
    private final ModernButton exportJsonButton = new ModernButton("JSON");

    private final Map<WorldMetricId, JCheckBox> metricCheckboxes = new EnumMap<>(WorldMetricId.class);
    private final Set<WorldMetricId> selectedMetrics = new LinkedHashSet<>();
    private final Map<WorldStatsRangePreset, ModernButton> presetButtons = new EnumMap<>(WorldStatsRangePreset.class);
    private final Map<WorldStatsYAxisMode, ModernButton> yAxisButtons = new EnumMap<>(WorldStatsYAxisMode.class);

    private WorldStatsRangePreset activePreset = WorldStatsRangePreset.SINCE_BEGINNING;
    private final Timer refreshTimer;
    private WorldStatsYAxisMode yAxisMode = WorldStatsYAxisMode.RELATIV_PRO_SERIE;
    private long customStartValue = 10;
    private WorldStatsTimeUnit customStartUnit = WorldStatsTimeUnit.MIN;
    private long customEndValue = 0;
    private WorldStatsTimeUnit customEndUnit = WorldStatsTimeUnit.MIN;
    private List<WorldStatsSample> currentSamples = List.of();
    private List<WorldMetricDefinition> currentDefinitions = List.of();
    private int panelWidth = PANEL_WIDTH;
    private int panelHeight = PANEL_HEIGHT;
    private ResizeEdge resizeEdge = ResizeEdge.NONE;
    private Point dragStart;
    private Dimension sizeAtDragStart;

    public WorldStatsPanel(SimulationRuntime runtime) {
        this(runtime, null);
    }

    public WorldStatsPanel(SimulationRuntime runtime, SettingsManager settingsManager) {
        this.store = runtime.getWorldStatsStore();
        this.settingsManager = settingsManager;

        loadUiSettings();

        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));
        setPreferredSize(new Dimension(panelWidth, panelHeight));
        setVisible(false);
        setLayout(new BorderLayout(10, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        rebuildMetricOptions();
        refreshChart();

        refreshTimer = new Timer(350, e -> refreshChart());
        refreshTimer.setRepeats(true);
        installResizeHandler();
    }

    void showPanel() {
        setVisible(true);
        if (!refreshTimer.isRunning()) refreshTimer.start();
        refreshChart();
    }

    void hidePanel() {
        setVisible(false);
        refreshTimer.stop();
        persistUiSettings(true);
    }

    int getPanelWidthSetting() {
        return panelWidth;
    }

    int getPanelHeightSetting() {
        return panelHeight;
    }

    int getRenderedMetricOptionCountForTest() {
        return metricCheckboxes.size();
    }

    Set<WorldMetricId> getRenderedMetricIdsForTest() {
        return Set.copyOf(metricCheckboxes.keySet());
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);

        JLabel title = new JLabel("WORLD STATISTICS");
        title.setForeground(ACCENT_COLOR);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));

        JPanel exportButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        exportButtons.setOpaque(false);

        styleSmallButton(exportCsvButton);
        styleSmallButton(exportPngButton);
        styleSmallButton(exportJsonButton);

        exportCsvButton.addActionListener(e -> exportCsv());
        exportPngButton.addActionListener(e -> exportPng());
        exportJsonButton.addActionListener(e -> exportJson());

        exportButtons.add(exportCsvButton);
        exportButtons.add(exportPngButton);
        exportButtons.add(exportJsonButton);

        header.add(title, BorderLayout.WEST);
        header.add(exportButtons, BorderLayout.EAST);
        return header;
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(10, 8));
        content.setOpaque(false);

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(220, 20));

        styleSearchField();

        metricListPanel.setOpaque(false);
        metricListPanel.setLayout(new BoxLayout(metricListPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(metricListPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(50, 55, 70), 1));
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, Integer.MAX_VALUE));

        left.add(searchField, BorderLayout.NORTH);
        left.add(scrollPane, BorderLayout.CENTER);

        JPanel topControls = new JPanel(new GridLayout(2, 1, 0, 6));
        topControls.setOpaque(false);
        topControls.add(buildPresetPanel());
        topControls.add(buildYAxisPanel());

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setOpaque(false);
        right.add(topControls, BorderLayout.NORTH);
        right.add(chartCanvas, BorderLayout.CENTER);

        content.add(left, BorderLayout.WEST);
        content.add(right, BorderLayout.CENTER);
        return content;
    }

    private JPanel buildPresetPanel() {
        presetPanel.setOpaque(false);
        for (WorldStatsRangePreset preset : WorldStatsRangePreset.values()) {
            ModernButton button = new ModernButton(preset.label());
            button.setPreferredSize(new Dimension(90, 34));
            button.setFont(new Font("Segoe UI", Font.BOLD, 12));
            button.addActionListener(e -> onPresetClicked(preset));
            presetButtons.put(preset, button);
            presetPanel.add(button);
        }
        syncPresetButtons();
        return presetPanel;
    }

    private JPanel buildYAxisPanel() {
        yAxisPanel.setOpaque(false);
        for (WorldStatsYAxisMode mode : WorldStatsYAxisMode.values()) {
            ModernButton button = new ModernButton(mode.label());
            button.setPreferredSize(new Dimension(130, 34));
            button.setFont(new Font("Segoe UI", Font.BOLD, 12));
            button.addActionListener(e -> {
                yAxisMode = mode;
                syncYAxisButtons();
                persistUiSettings(false);
                refreshChart();
            });
            yAxisButtons.put(mode, button);
            yAxisPanel.add(button);
        }
        syncYAxisButtons();
        return yAxisPanel;
    }

    private void styleSearchField() {
        searchField.setFont(new Font("Segoe UI", Font.BOLD, 14));
        searchField.setForeground(OverlayTheme.ACCENT);
        searchField.setBackground(OverlayTheme.CONTROL_BG);
        searchField.setCaretColor(OverlayTheme.ACCENT);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(OverlayTheme.ACCENT_GLOW, 1),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
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
    }

    private void styleSmallButton(ModernButton button) {
        button.setPreferredSize(new Dimension(64, 34));
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
    }

    private void onPresetClicked(WorldStatsRangePreset preset) {
        WorldStatsRangePreset previous = activePreset;
        activePreset = preset;
        if (preset == WorldStatsRangePreset.CUSTOM && !showCustomRangeDialog()) {
            activePreset = previous;
        }
        syncPresetButtons();
        persistUiSettings(false);
        refreshChart();
    }

    private boolean showCustomRangeDialog() {
        JSpinner startValue = new JSpinner(new SpinnerNumberModel((int) customStartValue, 0, Integer.MAX_VALUE, 1));
        JSpinner endValue = new JSpinner(new SpinnerNumberModel((int) customEndValue, 0, Integer.MAX_VALUE, 1));
        JComboBox<WorldStatsTimeUnit> startUnit = new JComboBox<>(WorldStatsTimeUnit.values());
        JComboBox<WorldStatsTimeUnit> endUnit = new JComboBox<>(WorldStatsTimeUnit.values());
        startUnit.setSelectedItem(customStartUnit);
        endUnit.setSelectedItem(customEndUnit);

        JPanel panel = new JPanel(new GridLayout(2, 4, 8, 6));
        panel.add(new JLabel("Start"));
        panel.add(startValue);
        panel.add(new JLabel("Einheit"));
        panel.add(startUnit);
        panel.add(new JLabel("Ende"));
        panel.add(endValue);
        panel.add(new JLabel("Einheit"));
        panel.add(endUnit);

        int result = JOptionPane.showConfirmDialog(this, panel, "Custom Zeitbereich (Tick-basiert)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return false;

        customStartValue = ((Number) startValue.getValue()).longValue();
        customStartUnit = (WorldStatsTimeUnit) startUnit.getSelectedItem();
        customEndValue = ((Number) endValue.getValue()).longValue();
        customEndUnit = (WorldStatsTimeUnit) endUnit.getSelectedItem();

        long startTick = customStartUnit.toTicks(customStartValue);
        long endTick = customEndUnit.toTicks(customEndValue);
        if (endTick < startTick) {
            customEndValue = customStartValue;
            customEndUnit = customStartUnit;
        }
        return true;
    }

    private void syncPresetButtons() {
        for (Map.Entry<WorldStatsRangePreset, ModernButton> e : presetButtons.entrySet()) {
            e.getValue().setDimmed(e.getKey() != activePreset);
        }
    }

    private void syncYAxisButtons() {
        for (Map.Entry<WorldStatsYAxisMode, ModernButton> e : yAxisButtons.entrySet()) {
            e.getValue().setDimmed(e.getKey() != yAxisMode);
        }
    }

    private void rebuildMetricOptions() {
        metricListPanel.removeAll();
        metricCheckboxes.clear();

        String needle = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        Map<WorldMetricCategory, List<WorldMetricDefinition>> grouped = WorldMetricRegistry.definitions().stream()
                .filter(def -> needle.isEmpty() || def.label().toLowerCase(Locale.ROOT).contains(needle))
                .collect(Collectors.groupingBy(WorldMetricDefinition::category,
                        () -> new EnumMap<>(WorldMetricCategory.class),
                        Collectors.toList()));

        for (WorldMetricCategory category : WorldMetricCategory.values()) {
            List<WorldMetricDefinition> defs = grouped.get(category);
            if (defs == null || defs.isEmpty()) continue;

            JLabel title = new JLabel(category.label());
            title.setForeground(ACCENT_COLOR);
            title.setFont(new Font("Segoe UI", Font.BOLD, 12));
            title.setBorder(new EmptyBorder(6, 0, 2, 0));
            metricListPanel.add(title);

            defs.sort(Comparator.comparing(WorldMetricDefinition::label));
            for (WorldMetricDefinition def : defs) {
                JCheckBox cb = new JCheckBox(def.label());
                cb.setOpaque(false);
                cb.setForeground(TEXT_COLOR);
                cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                cb.setFocusPainted(false);
                cb.setSelected(selectedMetrics.contains(def.id()));
                cb.addActionListener(e -> {
                    if (cb.isSelected()) selectedMetrics.add(def.id());
                    else selectedMetrics.remove(def.id());
                    persistUiSettings(false);
                    refreshChart();
                });
                metricCheckboxes.put(def.id(), cb);
                metricListPanel.add(cb);
            }
        }

        metricListPanel.revalidate();
        metricListPanel.repaint();
    }

    private void refreshChart() {
        if (selectedMetrics.isEmpty()) {
            currentSamples = List.of();
            currentDefinitions = List.of();
            chartCanvas.setChartData(currentSamples, currentDefinitions, yAxisMode);
            return;
        }

        long latestTick = store.lastTick();
        long earliestTick = store.firstTick();
        long customStartTick = customStartUnit.toTicks(customStartValue);
        long customEndTick = customEndUnit.toTicks(customEndValue);

        long fromTick = activePreset.resolveStartTick(latestTick, earliestTick, customStartTick, customEndTick);
        long toTick = activePreset.resolveEndTick(latestTick, customStartTick, customEndTick);
        if (toTick < fromTick) toTick = fromTick;

        int maxPoints = Math.max(80, chartCanvas.getWidth() - 280);
        currentSamples = store.queryRangeByTick(selectedMetrics, fromTick, toTick, maxPoints);
        currentDefinitions = selectedMetrics.stream()
                .map(WorldMetricRegistry::definition)
                .filter(java.util.Objects::nonNull)
                .toList();

        chartCanvas.setChartData(currentSamples, currentDefinitions, yAxisMode);
    }

    private void exportCsv() {
        exportWithChooser("csv", path -> WorldStatsExportService.exportCsv(path, currentSamples, currentDefinitions));
    }

    private void exportPng() {
        exportWithChooser("png", path -> WorldStatsExportService.exportPng(path, chartCanvas));
    }

    private void exportJson() {
        exportWithChooser("json", path -> WorldStatsExportService.exportJson(path, currentSamples, currentDefinitions));
    }

    private void exportWithChooser(String ext, ThrowingPathConsumer consumer) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(WorldStatsExportService.defaultFileName("world-stats", ext)));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            consumer.accept(chooser.getSelectedFile().toPath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export fehlgeschlagen: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadUiSettings() {
        if (settingsManager == null) {
            selectedMetrics.add(WorldMetricId.POPULATION_ALIVE);
            selectedMetrics.add(WorldMetricId.FOOD_PELLETS_AVAILABLE);
            return;
        }

        panelWidth = settingsManager.getWorldStatsViewerWidth();
        panelHeight = settingsManager.getWorldStatsViewerHeight();

        String csv = settingsManager.getWorldStatsSelectedMetrics();
        if (csv != null && !csv.isBlank()) {
            for (String raw : csv.split(",")) {
                try {
                    selectedMetrics.add(WorldMetricId.valueOf(raw.trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (selectedMetrics.isEmpty()) {
            selectedMetrics.add(WorldMetricId.POPULATION_ALIVE);
            selectedMetrics.add(WorldMetricId.FOOD_PELLETS_AVAILABLE);
        }

        try {
            activePreset = WorldStatsRangePreset.valueOf(settingsManager.getWorldStatsRangePreset());
        } catch (IllegalArgumentException ignored) {
            activePreset = WorldStatsRangePreset.SINCE_BEGINNING;
        }
        try {
            yAxisMode = WorldStatsYAxisMode.valueOf(settingsManager.getWorldStatsYAxisMode());
        } catch (IllegalArgumentException ignored) {
            yAxisMode = WorldStatsYAxisMode.RELATIV_PRO_SERIE;
        }

        customStartValue = settingsManager.getWorldStatsCustomStartValue();
        customEndValue = settingsManager.getWorldStatsCustomEndValue();
        try {
            customStartUnit = WorldStatsTimeUnit.valueOf(settingsManager.getWorldStatsCustomStartUnit());
            customEndUnit = WorldStatsTimeUnit.valueOf(settingsManager.getWorldStatsCustomEndUnit());
        } catch (IllegalArgumentException ignored) {
            customStartUnit = WorldStatsTimeUnit.MIN;
            customEndUnit = WorldStatsTimeUnit.MIN;
        }
    }

    private void persistUiSettings(boolean save) {
        if (settingsManager == null) return;
        settingsManager.setWorldStatsViewerWidth(panelWidth);
        settingsManager.setWorldStatsViewerHeight(panelHeight);
        settingsManager.setWorldStatsSelectedMetrics(selectedMetrics.stream().map(Enum::name).collect(Collectors.joining(",")));
        settingsManager.setWorldStatsRangePreset(activePreset.name());
        settingsManager.setWorldStatsYAxisMode(yAxisMode.name());
        settingsManager.setWorldStatsCustomStartValue(customStartValue);
        settingsManager.setWorldStatsCustomStartUnit(customStartUnit.name());
        settingsManager.setWorldStatsCustomEndValue(customEndValue);
        settingsManager.setWorldStatsCustomEndUnit(customEndUnit.name());
        if (save) settingsManager.saveSettings();
    }

    private void installResizeHandler() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                resizeEdge = detectResizeEdge(e.getPoint());
                setCursor(switch (resizeEdge) {
                    case RIGHT -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
                    case BOTTOM -> Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
                    case CORNER -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
                    default -> Cursor.getDefaultCursor();
                });
            }

            @Override
            public void mousePressed(MouseEvent e) {
                resizeEdge = detectResizeEdge(e.getPoint());
                if (resizeEdge != ResizeEdge.NONE) {
                    dragStart = e.getPoint();
                    sizeAtDragStart = getSize();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (resizeEdge == ResizeEdge.NONE || dragStart == null || sizeAtDragStart == null) return;
                int newW = sizeAtDragStart.width;
                int newH = sizeAtDragStart.height;
                int dx = e.getX() - dragStart.x;
                int dy = e.getY() - dragStart.y;
                if (resizeEdge == ResizeEdge.RIGHT || resizeEdge == ResizeEdge.CORNER) newW += dx;
                if (resizeEdge == ResizeEdge.BOTTOM || resizeEdge == ResizeEdge.CORNER) newH += dy;
                Dimension d = clampSize(newW, newH);
                panelWidth = d.width;
                panelHeight = d.height;
                setBounds(getX(), getY(), panelWidth, panelHeight);
                revalidate();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
                sizeAtDragStart = null;
                persistUiSettings(true);
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
    }

    private ResizeEdge detectResizeEdge(Point p) {
        boolean right = p.x >= getWidth() - EDGE_DRAG;
        boolean bottom = p.y >= getHeight() - EDGE_DRAG;
        if (right && bottom) return ResizeEdge.CORNER;
        if (right) return ResizeEdge.RIGHT;
        if (bottom) return ResizeEdge.BOTTOM;
        return ResizeEdge.NONE;
    }

    private Dimension clampSize(int w, int h) {
        int maxW = w;
        int maxH = h;
        if (getParent() != null) {
            maxW = Math.max(MIN_WIDTH, getParent().getWidth() - getX() - 15);
            maxH = Math.max(MIN_HEIGHT, getParent().getHeight() - getY() - 15);
        }
        return new Dimension(Math.max(MIN_WIDTH, Math.min(w, maxW)), Math.max(MIN_HEIGHT, Math.min(h, maxH)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_COLOR);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
            g2.setColor(BORDER_GLOW_COLOR);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 15, 15);
            g2.setColor(ACCENT_COLOR);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 15, 15);
        } finally {
            g2.dispose();
        }
    }

    private enum ResizeEdge {NONE, RIGHT, BOTTOM, CORNER}

    private interface ThrowingPathConsumer {
        void accept(Path path) throws IOException;
    }

    private static final class ChartCanvas extends JPanel {
        private static final Color GRID = new Color(42, 44, 52);
        private static final Color AXIS_TEXT = new Color(160, 170, 180);
        private static final Color CHART_BG = new Color(10, 10, 14, 170);
        private static final int LEFT_PAD = 40;
        private static final int TOP_PAD = 16;
        private static final int BOTTOM_PAD = 60;
        private static final int RIGHT_PAD = 210;

        private List<WorldStatsSample> samples = List.of();
        private List<WorldMetricDefinition> definitions = List.of();
        private WorldStatsYAxisMode yAxisMode = WorldStatsYAxisMode.RELATIV_PRO_SERIE;
        private int hoveredIndex = -1;

        ChartCanvas() {
            setOpaque(false);
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    Rectangle plot = plotRect();
                    if (!plot.contains(e.getPoint())) {
                        hoveredIndex = -1;
                        repaint();
                        return;
                    }
                    hoveredIndex = findNearestIndex(e.getX(), plot);
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoveredIndex = -1;
                    repaint();
                }
            };
            addMouseMotionListener(adapter);
        }

        void setChartData(List<WorldStatsSample> samples, List<WorldMetricDefinition> definitions, WorldStatsYAxisMode mode) {
            this.samples = samples == null ? List.of() : samples;
            this.definitions = definitions == null ? List.of() : definitions;
            this.yAxisMode = mode == null ? WorldStatsYAxisMode.RELATIV_PRO_SERIE : mode;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                Rectangle plot = plotRect();
                g2.setColor(CHART_BG);
                g2.fillRoundRect(plot.x, plot.y, plot.width, plot.height, 10, 10);
                drawGrid(g2, plot);

                if (samples.isEmpty() || definitions.isEmpty()) {
                    g2.setColor(AXIS_TEXT);
                    g2.drawString("Keine Daten im gewahlten Bereich", plot.x + 8, plot.y + 20);
                    return;
                }

                Map<WorldMetricId, double[]> ranges = perSeriesRanges();
                double[] global = globalRange();

                long minTick = samples.get(0).tick();
                long maxTick = samples.get(samples.size() - 1).tick();
                long span = Math.max(1L, maxTick - minTick);

                for (WorldMetricDefinition def : definitions) {
                    double[] range = yAxisMode == WorldStatsYAxisMode.GLOBAL ? global : ranges.get(def.id());
                    drawSeries(g2, plot, def, range, minTick, span);
                }

                drawAxisLabels(g2, plot, minTick, maxTick);
                drawLegend(g2, plot);
                if (hoveredIndex >= 0 && hoveredIndex < samples.size()) {
                    drawHover(g2, plot, hoveredIndex, minTick, span);
                }
            } finally {
                g2.dispose();
            }
        }

        private Rectangle plotRect() {
            return new Rectangle(LEFT_PAD, TOP_PAD,
                    Math.max(1, getWidth() - LEFT_PAD - RIGHT_PAD),
                    Math.max(1, getHeight() - TOP_PAD - BOTTOM_PAD));
        }

        private int findNearestIndex(int mouseX, Rectangle plot) {
            long minTick = samples.get(0).tick();
            long maxTick = samples.get(samples.size() - 1).tick();
            long span = Math.max(1L, maxTick - minTick);
            int best = -1;
            int bestDx = Integer.MAX_VALUE;
            for (int i = 0; i < samples.size(); i++) {
                int x = plot.x + (int) ((samples.get(i).tick() - minTick) * plot.width / span);
                int dx = Math.abs(mouseX - x);
                if (dx < bestDx) {
                    bestDx = dx;
                    best = i;
                }
            }
            return bestDx <= 16 ? best : -1;
        }

        private void drawGrid(Graphics2D g2, Rectangle plot) {
            g2.setColor(GRID);
            for (int i = 0; i <= 4; i++) {
                int y = plot.y + i * plot.height / 4;
                g2.drawLine(plot.x, y, plot.x + plot.width, y);
            }
            for (int i = 0; i <= 5; i++) {
                int x = plot.x + i * plot.width / 5;
                g2.drawLine(x, plot.y, x, plot.y + plot.height);
            }
        }

        private Map<WorldMetricId, double[]> perSeriesRanges() {
            Map<WorldMetricId, double[]> out = new EnumMap<>(WorldMetricId.class);
            for (WorldMetricDefinition def : definitions) {
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                for (WorldStatsSample sample : samples) {
                    double v = sample.metricValues().getOrDefault(def.id(), 0.0);
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
                out.put(def.id(), WorldStatsChartScaler.ensureRange(min, max));
            }
            return out;
        }

        private double[] globalRange() {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (WorldMetricDefinition def : definitions) {
                for (WorldStatsSample sample : samples) {
                    double v = sample.metricValues().getOrDefault(def.id(), 0.0);
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
            }
            return WorldStatsChartScaler.ensureRange(min, max);
        }

        private void drawSeries(Graphics2D g2, Rectangle plot, WorldMetricDefinition def, double[] range, long minTick, long span) {
            Path2D path = new Path2D.Double();
            for (int i = 0; i < samples.size(); i++) {
                WorldStatsSample sample = samples.get(i);
                double raw = sample.metricValues().getOrDefault(def.id(), 0.0);
                double ny = WorldStatsChartScaler.normalize(raw, range[0], range[1]);
                double px = plot.x + ((sample.tick() - minTick) * 1.0 / span) * plot.width;
                double py = plot.y + plot.height - ny * plot.height;
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }

            Composite orig = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            g2.setColor(def.color());
            g2.setStroke(new BasicStroke(3f));
            g2.draw(path);
            g2.setComposite(orig);
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(path);
        }

        private void drawAxisLabels(Graphics2D g2, Rectangle plot, long minTick, long maxTick) {
            g2.setColor(AXIS_TEXT);
            g2.setFont(new Font("Consolas", Font.PLAIN, 11));
            g2.drawString(WorldStatsTimeUnit.formatTickDuration(minTick), plot.x, plot.y + plot.height + 16);
            String end = WorldStatsTimeUnit.formatTickDuration(maxTick);
            int w = g2.getFontMetrics().stringWidth(end);
            g2.drawString(end, plot.x + plot.width - w, plot.y + plot.height + 16);
            g2.drawString("X: Simulationszeit (Tick-basiert)", plot.x, plot.y - 2);
        }

        private void drawLegend(Graphics2D g2, Rectangle plot) {
            int x = plot.x;
            int y = plot.y + plot.height + 36;
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            for (WorldMetricDefinition def : definitions) {
                g2.setColor(def.color());
                g2.fillRect(x, y - 7, 12, 3);
                g2.setColor(TEXT_COLOR);
                g2.drawString(def.label(), x + 15, y);
                x += 22 + g2.getFontMetrics().stringWidth(def.label());
                if (x > plot.x + plot.width - 120) {
                    x = plot.x;
                    y += 14;
                }
            }
        }

        private void drawHover(Graphics2D g2, Rectangle plot, int index, long minTick, long span) {
            WorldStatsSample sample = samples.get(index);
            int x = plot.x + (int) (((sample.tick() - minTick) * 1.0 / span) * plot.width);
            g2.setColor(new Color(255, 255, 255, 70));
            g2.drawLine(x, plot.y, x, plot.y + plot.height);

            List<String> lines = new ArrayList<>();
            lines.add(WorldStatsTimeUnit.formatTickDuration(sample.tick()));
            for (WorldMetricDefinition def : definitions) {
                double v = sample.metricValues().getOrDefault(def.id(), 0.0);
                lines.add(def.label() + ": " + String.format(Locale.ROOT, "%.2f", v) + " " + def.unit());
            }

            int ttX = plot.x + plot.width + 10;
            int ttY = plot.y + 8;
            g2.setFont(new Font("Consolas", Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();
            int width = 190;
            int height = lines.size() * fm.getHeight() + 10;

            g2.setColor(new Color(18, 18, 18, 235));
            g2.fillRoundRect(ttX, ttY, width, height, 8, 8);
            g2.setColor(ACCENT_COLOR);
            g2.drawRoundRect(ttX, ttY, width, height, 8, 8);

            int y = ttY + fm.getAscent() + 5;
            for (int i = 0; i < lines.size(); i++) {
                g2.setColor(i == 0 ? ACCENT_COLOR : TEXT_COLOR);
                g2.drawString(lines.get(i), ttX + 6, y);
                y += fm.getHeight();
            }
        }
    }
}
