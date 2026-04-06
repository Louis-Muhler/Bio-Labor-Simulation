package com.biolab;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.ParseException;
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
    private static final int FRAME_MARGIN = 20;
    private static final int FRAME_ARC = 15;
    private static final int LEFT_VIEWER_TO_SPECIMEN_GAP = 12;
    private static final int LEFT_COL_MIN_WIDTH = 150;
    private static final int LEFT_COL_MAX_WIDTH = 220;
    private static final int LIST_SIDE_PADDING = 12;
    private static final int CHECKBOX_ICON_AND_GAP = 28;
    private static final int RIGHT_SCROLLBAR_WIDTH = 14;

    private static final Color BG_COLOR = OverlayTheme.PANEL_BG_ALPHA;
    private static final Color BORDER_GLOW_COLOR = OverlayTheme.ACCENT_GLOW;
    private static final Color ACCENT_COLOR = OverlayTheme.ACCENT;
    private static final Color SEPARATOR_COLOR = new Color(40, 40, 50);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final int HEADER_TITLE_HEIGHT = 34;
    private static final int HEADER_SEPARATOR_HEIGHT = 2;
    private static final int HEADER_TO_CONTENT_GAP = 6;
    private static final int HEADER_TITLE_BOTTOM_INSET = 6;

    private final WorldStatsStore store;
    private final SettingsManager settingsManager;

    private final JTextField searchField = new JTextField();
    private final JPanel metricListPanel = new JPanel();
    private final JPanel leftColumn = new JPanel(new BorderLayout(0, 6));
    private final JPanel presetPanel = new JPanel();
    private final JPanel presetMainRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JPanel customRangeInlineRow = new JPanel(new GridBagLayout());
    private final JComboBox<WorldStatsRangePreset> rangePresetDropdown = OverlayControlFactory.createStyledComboBox(WorldStatsRangePreset.values());
    private final ModernButton customRangeSettingsButton = new ModernButton("", ModernButton.ButtonIcon.GEAR);
    private final JSpinner customStartInput = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
    private final JComboBox<WorldStatsTimeUnit> customStartUnitDropdown = OverlayControlFactory.createStyledComboBox(WorldStatsTimeUnit.values());
    private final JSpinner customEndInput = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
    private final JComboBox<WorldStatsTimeUnit> customEndUnitDropdown = OverlayControlFactory.createStyledComboBox(WorldStatsTimeUnit.values());
    private final JLabel customRangeSeparator = new JLabel("-");
    private final ChartCanvas chartCanvas = new ChartCanvas();

    private final ModernButton exportMenuButton = new ModernButton("", WorldStatsPanel::drawExportShareIcon);
    private final JPopupMenu exportMenu = new JPopupMenu();

    private final Map<WorldMetricId, JCheckBox> metricCheckboxes = new EnumMap<>(WorldMetricId.class);
    private final Set<WorldMetricId> selectedMetrics = new LinkedHashSet<>();
    private WorldStatsRangePreset activePreset = WorldStatsRangePreset.SINCE_BEGINNING;
    private final Timer refreshTimer;
    private long customStartValue = 0;
    private WorldStatsTimeUnit customStartUnit = WorldStatsTimeUnit.MIN;
    private long customEndValue = 150;
    private WorldStatsTimeUnit customEndUnit = WorldStatsTimeUnit.SEC;
    private long currentRangeFromTick;
    private long currentRangeToTick;
    private boolean suppressCustomRangeEvents;
    private boolean customRangeInlineVisible;
    private boolean customRangeLayoutListenerInstalled;
    private boolean exportMenuInitialized;

    private List<WorldStatsSample> currentSamples = List.of();
    private List<WorldMetricDefinition> currentDefinitions = List.of();
    private int panelWidth = PANEL_WIDTH;
    private int panelHeight = PANEL_HEIGHT;
    private ResizeEdge resizeEdge = ResizeEdge.NONE;
    private Point dragStart;
    private Dimension sizeAtDragStart;

    public WorldStatsPanel(SimulationRuntime runtime, SettingsManager settingsManager) {
        this.store = runtime.getWorldStatsStore();
        this.settingsManager = settingsManager;

        loadUiSettings();

        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));
        setPreferredSize(new Dimension(panelWidth, panelHeight));
        setVisible(false);
        setLayout(new BorderLayout(10, 8));
        setBorder(new EmptyBorder(35, 35, 35, 35));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        rebuildMetricOptions();
        refreshChart();

        refreshTimer = new Timer(350, e -> refreshChart());
        refreshTimer.setRepeats(true);
        installResizeHandler();
    }

    void hidePanel() {
        setVisible(false);
        refreshTimer.stop();
        setCustomRangeInlineVisible(false);
        persistUiSettings(true);
    }

    private JPanel buildPresetPanel() {
        presetPanel.setOpaque(false);
        presetPanel.setLayout(new BoxLayout(presetPanel, BoxLayout.Y_AXIS));
        presetMainRow.setOpaque(false);
        customRangeInlineRow.setOpaque(false);
        presetMainRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        customRangeInlineRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        customRangeInlineRow.setBorder(new EmptyBorder(10, 0, 0, 0));

        Dimension controlSize = new Dimension(computeRangeDropdownWidth(), 30);
        Dimension unitControlSize = new Dimension(computeUnitDropdownWidth(), 30);
        rangePresetDropdown.setPreferredSize(controlSize);
        rangePresetDropdown.setSelectedItem(activePreset);
        rangePresetDropdown.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setForeground(ACCENT_COLOR);
                label.setBackground(isSelected ? OverlayTheme.CONTROL_HOVER : OverlayTheme.CONTROL_BG);
                label.setBorder(new EmptyBorder(6, 10, 6, 10));
                if (value instanceof WorldStatsRangePreset preset) {
                    if (index < 0 && preset == WorldStatsRangePreset.CUSTOM) {
                        label.setText(WorldStatsRangeLabelFormatter.formatCurrentRange(preset, currentRangeFromTick, currentRangeToTick));
                    } else {
                        label.setText(preset.label());
                    }
                }
                return label;
            }
        });
        rangePresetDropdown.addActionListener(e -> onPresetSelectionChanged());

        customRangeSettingsButton.setPreferredSize(new Dimension(30, 30));
        customRangeSettingsButton.setFont(new Font("Segoe UI", Font.BOLD, 11));
        customRangeSettingsButton.addActionListener(e -> toggleCustomRangeInlineRowFromButton());

        customStartInput.setPreferredSize(controlSize);
        customEndInput.setPreferredSize(controlSize);
        customStartUnitDropdown.setPreferredSize(unitControlSize);
        customEndUnitDropdown.setPreferredSize(unitControlSize);
        customStartUnitDropdown.setMinimumSize(unitControlSize);
        customStartUnitDropdown.setMaximumSize(unitControlSize);
        customEndUnitDropdown.setMinimumSize(unitControlSize);
        customEndUnitDropdown.setMaximumSize(unitControlSize);
        OverlayControlFactory.styleSpinner(customStartInput);
        OverlayControlFactory.styleSpinner(customEndInput);
        configureNumericSpinnerInput(customStartInput);
        configureNumericSpinnerInput(customEndInput);
        customStartInput.setValue((int) customStartValue);
        customEndInput.setValue((int) customEndValue);
        customStartUnitDropdown.setSelectedItem(customStartUnit);
        customEndUnitDropdown.setSelectedItem(customEndUnit);

        DefaultListCellRenderer unitRenderer = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setForeground(ACCENT_COLOR);
                label.setBackground(isSelected ? OverlayTheme.CONTROL_HOVER : OverlayTheme.CONTROL_BG);
                label.setBorder(new EmptyBorder(6, 10, 6, 10));
                if (value instanceof WorldStatsTimeUnit unit) {
                    label.setText(unit.label());
                }
                return label;
            }
        };
        customStartUnitDropdown.setRenderer(unitRenderer);
        customEndUnitDropdown.setRenderer(unitRenderer);

        ChangeListener customChange = e -> onCustomRangeInputChanged();
        customStartInput.addChangeListener(customChange);
        customEndInput.addChangeListener(customChange);
        customStartUnitDropdown.addActionListener(e -> onCustomRangeInputChanged());
        customEndUnitDropdown.addActionListener(e -> onCustomRangeInputChanged());

        customRangeSeparator.setForeground(ACCENT_COLOR);

        presetMainRow.removeAll();
        presetMainRow.add(rangePresetDropdown);
        presetMainRow.add(customRangeSettingsButton);

        presetPanel.removeAll();
        presetPanel.add(presetMainRow);
        presetPanel.add(customRangeInlineRow);
        if (!customRangeLayoutListenerInstalled) {
            presetPanel.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    updateCustomRangeInlineLayout();
                }
            });
            customRangeLayoutListenerInstalled = true;
        }
        updateCustomRangeInlineLayout();
        setCustomRangeInlineVisible(false);
        syncCustomRangeSettingsButtonState();
        updateCurrentRangeLabel(0L, 0L);
        return presetPanel;
    }

    private int computeUnitDropdownWidth() {
        FontMetrics fm = customStartUnitDropdown.getFontMetrics(customStartUnitDropdown.getFont());
        int widest = fm.stringWidth(WorldStatsTimeUnit.HOUR.label());
        int twoChars = fm.stringWidth("00");
        return widest + 36 + twoChars;
    }

    private void updateCustomRangeInlineLayout() {
        customRangeInlineRow.removeAll();

        int gap = 6;
        int startWidth = customStartInput.getPreferredSize().width;
        int startUnitWidth = customStartUnitDropdown.getPreferredSize().width;
        int endWidth = customEndInput.getPreferredSize().width;
        int endUnitWidth = customEndUnitDropdown.getPreferredSize().width;
        int separatorWidth = customRangeSeparator.getPreferredSize().width;

        int neededSingleRow = startWidth + gap + startUnitWidth + gap + separatorWidth + gap + endWidth + gap + endUnitWidth;
        int available = Math.max(0, presetPanel.getWidth() - 2);
        boolean wrap = available > 0 && neededSingleRow > available;

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, gap);
        c.gridy = 0;
        c.weightx = 0;

        c.gridx = 0;
        customRangeInlineRow.add(customStartInput, c);
        c.gridx = 1;
        customRangeInlineRow.add(customStartUnitDropdown, c);
        c.gridx = 2;
        customRangeInlineRow.add(customRangeSeparator, c);

        if (wrap) {
            c.gridy = 1;
            c.gridx = 0;
            c.insets = new Insets(8, 0, 0, gap);
            customRangeInlineRow.add(customEndInput, c);
            c.gridx = 1;
            c.insets = new Insets(8, 0, 0, 0);
            customRangeInlineRow.add(customEndUnitDropdown, c);
        } else {
            c.gridy = 0;
            c.gridx = 3;
            customRangeInlineRow.add(customEndInput, c);
            c.gridx = 4;
            c.insets = new Insets(0, 0, 0, 0);
            customRangeInlineRow.add(customEndUnitDropdown, c);
        }

        // Keep the complete custom row left-aligned even when the row panel is wider.
        GridBagConstraints spacer = new GridBagConstraints();
        spacer.gridx = 99;
        spacer.gridy = 0;
        spacer.gridheight = wrap ? 2 : 1;
        spacer.weightx = 1.0;
        spacer.fill = GridBagConstraints.HORIZONTAL;
        customRangeInlineRow.add(Box.createHorizontalStrut(0), spacer);

        customRangeInlineRow.revalidate();
        customRangeInlineRow.repaint();
    }

    public WorldStatsPanel(SimulationRuntime runtime) {
        this(runtime, null);
    }

    private void configureNumericSpinnerInput(JSpinner spinner) {
        JComponent editor = spinner.getEditor();
        if (!(editor instanceof JSpinner.DefaultEditor defaultEditor)) {
            return;
        }

        JFormattedTextField textField = defaultEditor.getTextField();
        textField.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);

        DecimalFormat integerFormat = new DecimalFormat("#");
        integerFormat.setGroupingUsed(false);
        NumberFormatter formatter = new NumberFormatter(integerFormat);
        formatter.setValueClass(Integer.class);
        formatter.setMinimum(0);
        formatter.setMaximum(Integer.MAX_VALUE);
        formatter.setAllowsInvalid(false);
        formatter.setCommitsOnValidEdit(true);
        textField.setFormatterFactory(new DefaultFormatterFactory(formatter));
        textField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textField.setBorder(new EmptyBorder(4, 10, 4, 10));

        textField.addActionListener(e -> {
            try {
                textField.commitEdit();
            } catch (ParseException ex) {
                textField.setValue(spinner.getValue());
            }
        });
    }

    void showPanel() {
        setVisible(true);
        if (!refreshTimer.isRunning()) refreshTimer.start();
        refreshChart();
    }

    private int computeRangeDropdownWidth() {
        FontMetrics fm = rangePresetDropdown.getFontMetrics(rangePresetDropdown.getFont());
        int widest = 0;
        for (WorldStatsRangePreset preset : WorldStatsRangePreset.values()) {
            widest = Math.max(widest, fm.stringWidth(preset.label()));
        }
        // Include internal padding and arrow button area.
        return widest + 46;
    }

    private void onPresetSelectionChanged() {
        WorldStatsRangePreset selected = (WorldStatsRangePreset) rangePresetDropdown.getSelectedItem();
        if (selected == null || selected == activePreset) return;
        activePreset = selected;
        if (activePreset != WorldStatsRangePreset.CUSTOM) {
            setCustomRangeInlineVisible(false);
        }
        syncCustomRangeSettingsButtonState();
        persistUiSettings(false);
        refreshChart();
    }

    int getPanelWidthSetting() {
        return panelWidth;
    }

    int getPanelHeightSetting() {
        return panelHeight;
    }

    Dimension clampToAvailableArea(int maxWidth, int maxHeight) {
        int clampedWidth = clampLength(panelWidth, maxWidth, MIN_WIDTH);
        int clampedHeight = clampLength(panelHeight, maxHeight, MIN_HEIGHT);
        panelWidth = clampedWidth;
        panelHeight = clampedHeight;
        return new Dimension(clampedWidth, clampedHeight);
    }

    int getRenderedMetricOptionCountForTest() {
        return metricCheckboxes.size();
    }

    Set<WorldMetricId> getRenderedMetricIdsForTest() {
        return Set.copyOf(metricCheckboxes.keySet());
    }

    static double[] resolveYAxisRangeForMetric(WorldMetricDefinition definition, double[] autoRange) {
        if (definition != null && "%".equals(definition.unit())) {
            return new double[]{0.0, 100.0};
        }
        return autoRange == null ? new double[]{0.0, 1.0} : autoRange;
    }

    private static void drawExportShareIcon(Graphics2D g2, Point pos) {
        int x = pos.x;
        int y = pos.y;
        int r = 2;
        int leftX = x - 6;
        int rightTopX = x + 4;
        int rightTopY = y - 4;
        int rightBottomX = x + 4;
        int rightBottomY = y + 4;

        g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(leftX, y, rightTopX, rightTopY);
        g2.drawLine(leftX, y, rightBottomX, rightBottomY);

        g2.fillOval(leftX - r, y - r, r * 2, r * 2);
        g2.fillOval(rightTopX - r, rightTopY - r, r * 2, r * 2);
        g2.fillOval(rightBottomX - r, rightBottomY - r, r * 2, r * 2);
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(10, 8));
        content.setOpaque(false);

        leftColumn.setOpaque(false);
        leftColumn.setPreferredSize(new Dimension(LEFT_COL_MIN_WIDTH, 20));

        configureSearchField();

        metricListPanel.setOpaque(false);
        metricListPanel.setLayout(new BoxLayout(metricListPanel, BoxLayout.Y_AXIS));
        metricListPanel.setBorder(new EmptyBorder(8, LIST_SIDE_PADDING, 8, LIST_SIDE_PADDING));

        JScrollPane scrollPane = OverlayControlFactory.createStyledScrollPane(metricListPanel);
        JPanel metricListContainer = OverlayControlFactory.wrapInInnerFrame(scrollPane);

        leftColumn.add(searchField, BorderLayout.NORTH);
        leftColumn.add(metricListContainer, BorderLayout.CENTER);

        JPanel topControls = new JPanel();
        topControls.setLayout(new BoxLayout(topControls, BoxLayout.Y_AXIS));
        topControls.setOpaque(false);
        topControls.add(buildPresetPanel());
        topControls.add(Box.createVerticalStrut(2));

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setOpaque(false);
        right.add(topControls, BorderLayout.NORTH);
        right.add(chartCanvas, BorderLayout.CENTER);

        content.add(leftColumn, BorderLayout.WEST);
        content.add(right, BorderLayout.CENTER);
        return content;
    }

    static String formatTickRangeValue(long ticks) {
        return WorldStatsRangeLabelFormatter.formatTickRangeValue(ticks);
    }

    private void configureSearchField() {
        OverlayControlFactory.styleTextField(searchField);
        searchField.setToolTipText("Search metrics...");
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

    static Rectangle computeHoverTooltipBounds(Rectangle plot, Point mouse, int tooltipWidth, int tooltipHeight, int offset) {
        return WorldStatsTooltipLayout.computeBounds(plot, mouse, tooltipWidth, tooltipHeight, offset);
    }

    private void toggleCustomRangeInlineRowFromButton() {
        if (activePreset != WorldStatsRangePreset.CUSTOM) {
            return;
        }
        setCustomRangeInlineVisible(!customRangeInlineVisible);
    }

    private void onCustomRangeInputChanged() {
        if (suppressCustomRangeEvents) return;
        customStartValue = ((Number) customStartInput.getValue()).longValue();
        customEndValue = ((Number) customEndInput.getValue()).longValue();
        customStartUnit = (WorldStatsTimeUnit) customStartUnitDropdown.getSelectedItem();
        customEndUnit = (WorldStatsTimeUnit) customEndUnitDropdown.getSelectedItem();

        if (customStartUnit == null || customEndUnit == null) return;

        long startTick = customStartUnit.toTicks(customStartValue);
        long endTick = customEndUnit.toTicks(customEndValue);
        if (endTick < startTick) {
            suppressCustomRangeEvents = true;
            try {
                customEndValue = customStartValue;
                customEndUnit = customStartUnit;
                customEndInput.setValue((int) customEndValue);
                customEndUnitDropdown.setSelectedItem(customEndUnit);
            } finally {
                suppressCustomRangeEvents = false;
            }
        }

        persistUiSettings(false);
        refreshChart();
        rangePresetDropdown.repaint();
    }

    private void setCustomRangeInlineVisible(boolean visible) {
        customRangeInlineVisible = visible && activePreset == WorldStatsRangePreset.CUSTOM;
        customRangeInlineRow.setVisible(customRangeInlineVisible);
        if (customRangeInlineVisible) {
            updateCustomRangeInlineLayout();
        }
        presetPanel.revalidate();
        presetPanel.repaint();
        syncCustomRangeSettingsButtonState();
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

        int columnWidth = computeDesiredLeftColumnWidth(grouped);
        leftColumn.setPreferredSize(new Dimension(columnWidth, 20));

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
                JCheckBox cb = OverlayControlFactory.createStyledCheckBox(
                        def.label(),
                        selectedMetrics.contains(def.id())
                );
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
        leftColumn.revalidate();
    }

    private int computeDesiredLeftColumnWidth(Map<WorldMetricCategory, List<WorldMetricDefinition>> grouped) {
        FontMetrics fmMetric = metricListPanel.getFontMetrics(new Font("Segoe UI", Font.PLAIN, 12));
        FontMetrics fmCategory = metricListPanel.getFontMetrics(new Font("Segoe UI", Font.BOLD, 12));
        int widest = 0;

        for (Map.Entry<WorldMetricCategory, List<WorldMetricDefinition>> entry : grouped.entrySet()) {
            widest = Math.max(widest, fmCategory.stringWidth(entry.getKey().label()));
            for (WorldMetricDefinition def : entry.getValue()) {
                widest = Math.max(widest, fmMetric.stringWidth(def.label()));
            }
        }

        int desired = widest + (LIST_SIDE_PADDING * 2) + CHECKBOX_ICON_AND_GAP + RIGHT_SCROLLBAR_WIDTH;
        return Math.max(LEFT_COL_MIN_WIDTH, Math.min(desired, LEFT_COL_MAX_WIDTH));
    }

    private void syncCustomRangeSettingsButtonState() {
        boolean dimmed = activePreset != WorldStatsRangePreset.CUSTOM || customRangeInlineVisible;
        customRangeSettingsButton.setDimmed(dimmed);
    }

    private void setupExportMenuIfNeeded() {
        if (!exportMenuInitialized) {
            JMenuItem pngItem = new JMenuItem("PNG");
            pngItem.addActionListener(e -> exportPng());
            JMenuItem csvItem = new JMenuItem("CSV");
            csvItem.addActionListener(e -> exportCsv());
            JMenuItem jsonItem = new JMenuItem("JSON");
            jsonItem.addActionListener(e -> exportJson());

            styleExportMenuItem(pngItem);
            styleExportMenuItem(csvItem);
            styleExportMenuItem(jsonItem);

            exportMenu.add(pngItem);
            exportMenu.add(csvItem);
            exportMenu.add(jsonItem);

            exportMenu.setBorder(BorderFactory.createLineBorder(ACCENT_COLOR, 1));
            exportMenu.setBackground(OverlayTheme.CONTROL_BG);

            exportMenuButton.setPreferredSize(new Dimension(34, 34));
            exportMenuButton.setToolTipText("Export");
            exportMenuButton.addActionListener(e -> exportMenu.show(exportMenuButton, 0, exportMenuButton.getHeight()));
            exportMenuInitialized = true;
        }
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, HEADER_TO_CONTENT_GAP, 0));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setPreferredSize(new Dimension(1, HEADER_TITLE_HEIGHT));
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, HEADER_TITLE_HEIGHT));

        JLabel title = new JLabel("WORLD STATISTICS", SwingConstants.CENTER);
        title.setForeground(ACCENT_COLOR);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        // Nudge the title slightly upward to match the other overlay headers.
        title.setBorder(new EmptyBorder(0, 0, HEADER_TITLE_BOTTOM_INSET, 0));

        JPanel exportButtons = new JPanel(new BorderLayout());
        exportButtons.setOpaque(false);

        setupExportMenuIfNeeded();
        exportButtons.add(exportMenuButton, BorderLayout.EAST);

        JPanel leftBalance = new JPanel();
        leftBalance.setOpaque(false);
        int sideSlotWidth = exportMenuButton.getPreferredSize().width;
        int sideSlotHeight = exportMenuButton.getPreferredSize().height;
        leftBalance.setPreferredSize(new Dimension(sideSlotWidth, sideSlotHeight));
        exportButtons.setPreferredSize(new Dimension(sideSlotWidth, sideSlotHeight));

        titleRow.add(leftBalance, BorderLayout.WEST);
        titleRow.add(title, BorderLayout.CENTER);
        titleRow.add(exportButtons, BorderLayout.EAST);

        JPanel separator = new JPanel();
        separator.setOpaque(true);
        separator.setBackground(SEPARATOR_COLOR);
        separator.setPreferredSize(new Dimension(1, HEADER_SEPARATOR_HEIGHT));
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, HEADER_SEPARATOR_HEIGHT));

        header.add(titleRow, BorderLayout.NORTH);
        header.add(separator, BorderLayout.SOUTH);
        return header;
    }

    private void styleExportMenuItem(JMenuItem item) {
        item.setUI(new BasicMenuItemUI() {
            @Override
            protected void installDefaults() {
                super.installDefaults();
                selectionBackground = OverlayTheme.CONTROL_HOVER;
                selectionForeground = ACCENT_COLOR;
            }
        });
        item.setOpaque(true);
        item.setForeground(ACCENT_COLOR);
        item.setBackground(OverlayTheme.CONTROL_BG);
        item.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        item.setBorder(new EmptyBorder(6, 10, 6, 10));
        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                item.setBackground(OverlayTheme.CONTROL_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                item.setBackground(OverlayTheme.CONTROL_BG);
            }
        });
    }

    private void refreshChart() {
        if (customStartUnit == null) customStartUnit = WorldStatsTimeUnit.MIN;
        if (customEndUnit == null) customEndUnit = WorldStatsTimeUnit.SEC;

        long latestTick = store.lastTick();
        long earliestTick = store.firstTick();
        long customStartTick = customStartUnit.toTicks(customStartValue);
        long customEndTick = customEndUnit.toTicks(customEndValue);

        long fromTick = activePreset.resolveStartTick(latestTick, earliestTick, customStartTick, customEndTick);
        long toTick = activePreset.resolveEndTick(latestTick, customStartTick, customEndTick);
        if (toTick < fromTick) toTick = fromTick;
        currentRangeFromTick = fromTick;
        currentRangeToTick = toTick;
        updateCurrentRangeLabel(fromTick, toTick);

        if (selectedMetrics.isEmpty()) {
            currentSamples = List.of();
            currentDefinitions = List.of();
            chartCanvas.setChartData(currentSamples, currentDefinitions);
            return;
        }

        int maxPoints = Math.max(80, chartCanvas.getWidth() - 280);
        currentSamples = store.queryRangeByTick(selectedMetrics, fromTick, toTick, maxPoints);
        currentDefinitions = selectedMetrics.stream()
                .map(WorldMetricRegistry::definition)
                .filter(java.util.Objects::nonNull)
                .toList();

        chartCanvas.setChartData(currentSamples, currentDefinitions);
    }

    private static final class ChartCanvas extends JPanel {
        private static final Color GRID = new Color(42, 44, 52);
        private static final Color AXIS_TEXT = new Color(160, 170, 180);
        private static final Color CHART_BG = new Color(10, 10, 14, 170);
        private static final int LEFT_PAD = 16;
        private static final int TOP_PAD = 16;
        private static final int RIGHT_PAD = 16;
        private static final int LEGEND_ITEM_GAP = 22;
        private static final int LEGEND_RIGHT_WRAP_PADDING = RIGHT_PAD;
        private static final int AXIS_AND_GAP_HEIGHT = 30;
        private static final int LEGEND_BOTTOM_GAP = 0;
        private static final int MIN_PLOT_HEIGHT = 80;
        private static final int HOVER_TOOLTIP_OFFSET = 12;

        private List<WorldStatsSample> samples = List.of();
        private List<WorldMetricDefinition> definitions = List.of();
        private int hoveredIndex = -1;
        private Point hoverMouse;

        ChartCanvas() {
            setOpaque(false);
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    Rectangle plot = plotRect();
                    if (!plot.contains(e.getPoint())) {
                        hoverMouse = null;
                        hoveredIndex = -1;
                        repaint();
                        return;
                    }
                    hoverMouse = e.getPoint();
                    hoveredIndex = findNearestIndex(e.getX(), plot);
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverMouse = null;
                    hoveredIndex = -1;
                    repaint();
                }
            };
            addMouseMotionListener(adapter);
            addMouseListener(adapter);
        }

        void setChartData(List<WorldStatsSample> samples, List<WorldMetricDefinition> definitions) {
            this.samples = samples == null ? List.of() : samples;
            this.definitions = definitions == null ? List.of() : definitions;
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
                    g2.drawString("No data in selected range", plot.x + 8, plot.y + 20);
                    return;
                }

                Map<WorldMetricId, double[]> ranges = perSeriesRanges();

                long minTick = samples.get(0).tick();
                long maxTick = samples.get(samples.size() - 1).tick();
                long span = Math.max(1L, maxTick - minTick);

                for (WorldMetricDefinition def : definitions) {
                    double[] range = resolveYAxisRangeForMetric(def, ranges.get(def.id()));
                    drawSeries(g2, plot, def, range, minTick, span);
                }

                drawAxisLabels(g2, plot, minTick, maxTick);
                drawLegend(g2, plot);
                if (hoveredIndex >= 0 && hoveredIndex < samples.size() && hoverMouse != null && plot.contains(hoverMouse)) {
                    drawHover(g2, plot, hoveredIndex, minTick, span, hoverMouse);
                }
            } finally {
                g2.dispose();
            }
        }

        private Rectangle plotRect() {
            int bottomPad = computeDynamicBottomPad();
            return new Rectangle(LEFT_PAD, TOP_PAD,
                    Math.max(1, getWidth() - LEFT_PAD - RIGHT_PAD),
                    Math.max(MIN_PLOT_HEIGHT, getHeight() - TOP_PAD - bottomPad));
        }

        private int computeDynamicBottomPad() {
            FontMetrics fm = getFontMetrics(new Font("Segoe UI", Font.PLAIN, 11));
            int rows = computeLegendRows(Math.max(1, getWidth() - LEFT_PAD - RIGHT_PAD), fm);
            int legendHeight = rows <= 0 ? 0 : (fm.getAscent() + Math.max(0, rows - 1) * fm.getHeight());
            int preferred = AXIS_AND_GAP_HEIGHT + legendHeight + LEGEND_BOTTOM_GAP;
            int minPad = AXIS_AND_GAP_HEIGHT + LEGEND_BOTTOM_GAP;
            int maxAllowed = Math.max(minPad, getHeight() - TOP_PAD - MIN_PLOT_HEIGHT);
            return Math.max(minPad, Math.min(preferred, maxAllowed));
        }

        private int computeLegendRows(int plotWidth, FontMetrics fm) {
            if (definitions.isEmpty()) {
                return 0;
            }
            int rowWidthLimit = Math.max(80, plotWidth - LEGEND_RIGHT_WRAP_PADDING);
            int rows = 1;
            int x = 0;
            for (WorldMetricDefinition def : definitions) {
                int itemWidth = LEGEND_ITEM_GAP + fm.stringWidth(def.label());
                if (x > 0 && x + itemWidth > rowWidthLimit) {
                    rows++;
                    x = 0;
                }
                x += itemWidth;
            }
            return rows;
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
            g2.drawString("X: Simulation time (tick-based)", plot.x, plot.y - 2);
        }

        private void drawLegend(Graphics2D g2, Rectangle plot) {
            int x = plot.x;
            int y = plot.y + plot.height + AXIS_AND_GAP_HEIGHT;
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            for (WorldMetricDefinition def : definitions) {
                int itemWidth = LEGEND_ITEM_GAP + g2.getFontMetrics().stringWidth(def.label());
                if (x > plot.x && x + itemWidth > plot.x + plot.width - LEGEND_RIGHT_WRAP_PADDING) {
                    x = plot.x;
                    y += g2.getFontMetrics().getHeight();
                }
                g2.setColor(def.color());
                g2.fillRect(x, y - 7, 12, 3);
                g2.setColor(TEXT_COLOR);
                g2.drawString(def.label(), x + 15, y);
                x += itemWidth;
            }
        }

        private void drawHover(Graphics2D g2, Rectangle plot, int index, long minTick, long span, Point mouse) {
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

            g2.setFont(new Font("Consolas", Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();
            int width = 0;
            for (String line : lines) {
                width = Math.max(width, fm.stringWidth(line));
            }
            width += 14;
            int height = lines.size() * fm.getHeight() + 10;

            Rectangle tooltipBounds = computeHoverTooltipBounds(plot, mouse, width, height, HOVER_TOOLTIP_OFFSET);
            int ttX = tooltipBounds.x;
            int ttY = tooltipBounds.y;

            g2.setColor(new Color(18, 18, 18, 235));
            g2.fillRoundRect(ttX, ttY, tooltipBounds.width, tooltipBounds.height, 8, 8);
            g2.setColor(ACCENT_COLOR);
            g2.drawRoundRect(ttX, ttY, tooltipBounds.width, tooltipBounds.height, 8, 8);

            int y = ttY + fm.getAscent() + 5;
            for (int i = 0; i < lines.size(); i++) {
                g2.setColor(i == 0 ? ACCENT_COLOR : TEXT_COLOR);
                g2.drawString(lines.get(i), ttX + 6, y);
                y += fm.getHeight();
                if (y > ttY + tooltipBounds.height - 2) {
                    break;
                }
            }
        }
    }

    private void updateCurrentRangeLabel(long fromTick, long toTick) {
        rangePresetDropdown.repaint();
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
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
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
        settingsManager.setWorldStatsCustomStartValue(customStartValue);
        settingsManager.setWorldStatsCustomStartUnit((customStartUnit == null ? WorldStatsTimeUnit.MIN : customStartUnit).name());
        settingsManager.setWorldStatsCustomEndValue(customEndValue);
        settingsManager.setWorldStatsCustomEndUnit((customEndUnit == null ? WorldStatsTimeUnit.SEC : customEndUnit).name());
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
        int frameRight = getWidth() - FRAME_MARGIN;
        int frameBottom = getHeight() - FRAME_MARGIN;
        boolean right = Math.abs(p.x - frameRight) <= EDGE_DRAG;
        boolean bottom = Math.abs(p.y - frameBottom) <= EDGE_DRAG;
        if (right && bottom) return ResizeEdge.CORNER;
        if (right) return ResizeEdge.RIGHT;
        if (bottom) return ResizeEdge.BOTTOM;
        return ResizeEdge.NONE;
    }

    private Dimension clampSize(int w, int h) {
        int maxW = w;
        int maxH = h;
        if (getParent() != null) {
            int rightReserved = InspectorPanel.PANEL_WIDTH + 15 + LEFT_VIEWER_TO_SPECIMEN_GAP;
            maxW = getParent().getWidth() - getX() - rightReserved;
            maxH = getParent().getHeight() - getY() - 15;
        }
        return new Dimension(
                clampLength(w, maxW, MIN_WIDTH),
                clampLength(h, maxH, MIN_HEIGHT)
        );
    }

    private int clampLength(int desired, int maxAllowed, int preferredMin) {
        int safeMax = Math.max(120, maxAllowed);
        int effectiveMin = Math.min(preferredMin, safeMax);
        return Math.max(effectiveMin, Math.min(desired, safeMax));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = FRAME_MARGIN;
            int y = FRAME_MARGIN;
            int w = Math.max(0, getWidth() - 2 * FRAME_MARGIN);
            int h = Math.max(0, getHeight() - 2 * FRAME_MARGIN);

            g2.setColor(BG_COLOR);
            g2.fillRoundRect(x, y, w, h, FRAME_ARC, FRAME_ARC);
            g2.setColor(BORDER_GLOW_COLOR);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(x, y, w, h, FRAME_ARC, FRAME_ARC);
            g2.setColor(ACCENT_COLOR);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(x, y, w, h, FRAME_ARC, FRAME_ARC);
        } finally {
            g2.dispose();
        }
    }

    private enum ResizeEdge {NONE, RIGHT, BOTTOM, CORNER}

    private interface ThrowingPathConsumer {
        void accept(Path path) throws IOException;
    }


}
