package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSpinnerUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Left viewer panel used to configure spawn tools for microbes and food.
 */
public class MicrobeCreatorPanel extends JPanel {
    public static final int PANEL_WIDTH = 380;

    private static final int FRAME_MARGIN = 20;
    private static final int CORNER_RADIUS = 15;
    private static final Color BG_COLOR = OverlayTheme.PANEL_BG_ALPHA;
    private static final Color ACCENT_COLOR = OverlayTheme.ACCENT;
    private static final Color BORDER_GLOW_COLOR = OverlayTheme.ACCENT_GLOW;
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font LABEL_FONT_PLAIN = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font VALUE_FONT = new Font("Segoe UI", Font.BOLD, 14);
    private static final int MICROBE_MODE_HEIGHT = 620;
    private static final int FOOD_MODE_HEIGHT = 230;
    private static final int ROW_HEIGHT = 30;
    private static final int INPUT_WIDTH = 112;
    private static final int CONTENT_PADDING = 35;
    private static final int TRAIT_BLOCK_TOP_GAP = 16;
    private static final int TRAIT_ROW_GAP = 8;
    private static final int HEALTH_ENERGY_GAP = 10;
    private static final int TRAIT_TITLE_TO_SLIDER_GAP = 4;
    private static final Color TRAIT_HEAT_COLOR = new Color(255, 100, 100);
    private static final Color TRAIT_TOXIN_COLOR = new Color(100, 255, 100);
    private static final Color TRAIT_SPEED_COLOR = new Color(100, 150, 255);
    private static final Color TRAIT_DIET_COLOR = new Color(255, 180, 50);
    private static final Color MAX_HEALTH_COLOR = new Color(255, 120, 180);
    private static final Color MAX_ENERGY_COLOR = new Color(120, 200, 255);

    private final ModernButton microbeModeButton = new ModernButton("MICROBE");
    private final ModernButton foodModeButton = new ModernButton("FOOD");
    private final JCheckBox randomCheck = OverlayControlFactory.createSettingsCheckBox("Random", false);
    private final JSpinner amountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
    private final TraitControl heatTrait = new TraitControl("Heat Resistance", 50, 0, 100, true, TRAIT_HEAT_COLOR);
    private final TraitControl toxinTrait = new TraitControl("Toxin Resistance", 50, 0, 100, true, TRAIT_TOXIN_COLOR);
    private final TraitControl speedTrait = new TraitControl("Speed", 50, 0, 100, true, TRAIT_SPEED_COLOR);
    private final TraitControl dietTrait = new TraitControl("Diet", 50, 0, 100, true, TRAIT_DIET_COLOR);
    private final JSpinner maxHealthInput = new JSpinner(new SpinnerNumberModel(100, 1, 400, 1));
    private final JSpinner maxEnergyInput = new JSpinner(new SpinnerNumberModel(100, 1, 400, 1));

    private final PreviewCanvas previewCanvas = new PreviewCanvas();
    private final JPanel microbeHealthRow;
    private final JPanel microbeEnergyRow;
    private final JPanel previewShell;
    private final ModernButton activateButton;
    private final JPanel randomRow;
    private final JPanel amountRow;
    private final JPanel centeredAmountRow;
    private final JPanel microbeSection;
    private final JPanel body;
    private final JPanel contentPanel;
    private final JPanel heatRow;
    private final JPanel toxinRow;
    private final JPanel speedRow;
    private final JPanel dietRow;
    private Runnable activateSpawnToolAction;
    private Runnable layoutRefreshAction;
    private Supplier<MicrobeGeneProfile> randomProfileSupplier;
    private boolean spawnToolActive;
    private SpawnMode spawnMode = SpawnMode.MICROBE;

    public MicrobeCreatorPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(PANEL_WIDTH, MICROBE_MODE_HEIGHT));

        contentPanel = new JPanel(new BorderLayout(0, 8));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING));

        JLabel title = new JLabel("ENTITY SPAWNER", SwingConstants.CENTER);
        title.setForeground(ACCENT_COLOR);
        title.setFont(TITLE_FONT);
        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);

        JPanel separator = new JPanel();
        separator.setOpaque(true);
        separator.setBackground(new Color(40, 40, 50));
        separator.setPreferredSize(new Dimension(1, 2));

        JPanel modeRow = new JPanel(new GridLayout(1, 2, 8, 0));
        modeRow.setOpaque(false);
        microbeModeButton.setPreferredSize(new Dimension(1, 34));
        foodModeButton.setPreferredSize(new Dimension(1, 34));
        microbeModeButton.addActionListener(e -> setSelectedMode(SpawnMode.MICROBE));
        foodModeButton.addActionListener(e -> setSelectedMode(SpawnMode.FOOD));
        modeRow.add(microbeModeButton);
        modeRow.add(foodModeButton);
        JPanel lowerHeader = new JPanel();
        lowerHeader.setOpaque(false);
        lowerHeader.setLayout(new BoxLayout(lowerHeader, BoxLayout.Y_AXIS));
        lowerHeader.add(separator);
        lowerHeader.add(Box.createVerticalStrut(10));
        lowerHeader.add(modeRow);
        header.add(lowerHeader, BorderLayout.CENTER);
        contentPanel.add(header, BorderLayout.NORTH);

        body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(0, 0, 0, 0));

        randomCheck.addActionListener(e -> {
            if (randomCheck.isSelected()) {
                applyGeneratedProfile(generatedRandomProfile());
            }
        });
        amountRow = rowWithLabel("Amount", amountSpinner, LABEL_FONT_PLAIN, ACCENT_COLOR);
        amountRow.setMaximumSize(new Dimension(198, ROW_HEIGHT));

        centeredAmountRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centeredAmountRow.setOpaque(false);

        randomRow = new JPanel(new BorderLayout());
        randomRow.setOpaque(false);
        randomRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        randomRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        applyTopRowLayout(true);
        body.add(randomRow);
        body.add(Box.createVerticalStrut(TRAIT_BLOCK_TOP_GAP));

        microbeSection = new JPanel();
        microbeSection.setOpaque(false);
        microbeSection.setLayout(new BoxLayout(microbeSection, BoxLayout.Y_AXIS));
        microbeSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        heatRow = wrapTraitRow(heatTrait);
        microbeSection.add(heatRow);
        microbeSection.add(Box.createVerticalStrut(TRAIT_ROW_GAP));
        toxinRow = wrapTraitRow(toxinTrait);
        microbeSection.add(toxinRow);
        microbeSection.add(Box.createVerticalStrut(TRAIT_ROW_GAP));
        speedRow = wrapTraitRow(speedTrait);
        microbeSection.add(speedRow);
        microbeSection.add(Box.createVerticalStrut(TRAIT_ROW_GAP));
        dietRow = wrapTraitRow(dietTrait);
        microbeSection.add(dietRow);
        microbeSection.add(Box.createVerticalStrut(TRAIT_ROW_GAP));

        microbeHealthRow = rowWithLabel("Max Health", maxHealthInput, LABEL_FONT, MAX_HEALTH_COLOR);
        microbeEnergyRow = rowWithLabel("Max Energy", maxEnergyInput, LABEL_FONT, MAX_ENERGY_COLOR);
        microbeSection.add(microbeHealthRow);
        microbeSection.add(Box.createVerticalStrut(HEALTH_ENERGY_GAP));
        microbeSection.add(microbeEnergyRow);
        microbeSection.add(Box.createVerticalStrut(TRAIT_BLOCK_TOP_GAP));

        previewShell = OverlayControlFactory.wrapInInnerFrame(previewCanvas);
        previewShell.setPreferredSize(new Dimension(1, 186));
        previewShell.setMaximumSize(new Dimension(Integer.MAX_VALUE, 186));
        previewShell.setAlignmentX(Component.LEFT_ALIGNMENT);
        microbeSection.add(previewShell);

        body.add(microbeSection);
        body.add(Box.createVerticalStrut(8));

        activateButton = new ModernButton("Activate Spawn Tool");
        activateButton.setPreferredSize(new Dimension(1, 38));
        activateButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        activateButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        activateButton.addActionListener(e -> {
            if (activateSpawnToolAction != null) {
                activateSpawnToolAction.run();
            }
        });
        body.add(activateButton);

        registerLivePreviewListeners();
        styleSpinnerChrome(maxHealthInput, MAX_HEALTH_COLOR);
        styleSpinnerChrome(maxEnergyInput, MAX_ENERGY_COLOR);
        styleSpinnerField(amountSpinner);
        styleSpinnerField(maxHealthInput);
        styleSpinnerField(maxEnergyInput);
        tintSpinnerText(maxHealthInput, MAX_HEALTH_COLOR);
        tintSpinnerText(maxEnergyInput, MAX_ENERGY_COLOR);
        randomProfileSupplier = MicrobeSpawnRequest::defaultProfile;

        JScrollPane scrollPane = OverlayScrollSupport.createWheelOnlyScrollPane(body, new Insets(0, 0, 0, 0), 18);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        add(contentPanel, BorderLayout.CENTER);
        applyGeneratedProfile(MicrobeSpawnRequest.defaultProfile());
        setSelectedMode(SpawnMode.MICROBE);
        setSpawnToolActive(false);
    }

    public SpawnMode selectedMode() {
        return spawnMode;
    }

    public int currentAmount() {
        return ((Number) amountSpinner.getValue()).intValue();
    }

    public void setActivateSpawnToolAction(Runnable action) {
        this.activateSpawnToolAction = action;
    }

    private static void styleSpinnerField(JSpinner spinner) {
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField textField = defaultEditor.getTextField();
            textField.setFont(new Font("Segoe UI", Font.BOLD, 14));
            textField.setBorder(new EmptyBorder(6, 10, 6, 10));
            textField.setHorizontalAlignment(SwingConstants.LEFT);
        }
    }

    boolean isSpawnToolActive() {
        return spawnToolActive;
    }

    public void setSpawnToolActive(boolean active) {
        spawnToolActive = active;
        String label = active ? "Deactivate Spawn Tool" : "Activate Spawn Tool";
        activateButton.setDisplayText(label);
        activateButton.setDimmed(active);
    }

    private static void tintSpinnerText(JSpinner spinner, Color color) {
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField textField = defaultEditor.getTextField();
            textField.setForeground(color);
            textField.setCaretColor(color);
        }
    }

    private static void styleSpinnerChrome(JSpinner spinner, Color color) {
        spinner.setUI(new ColoredSpinnerUI(color));
        spinner.setOpaque(false);
        spinner.setBorder(BorderFactory.createEmptyBorder());
        spinner.setBackground(OverlayTheme.CONTROL_BG);
    }

    public boolean isRandomEnabled() {
        return randomCheck.isSelected();
    }

    void setLayoutRefreshAction(Runnable action) {
        this.layoutRefreshAction = action;
    }

    private static double randomCenteredTrait() {
        // Gaussian around 0.5 gives a natural tendency toward middle values.
        return Math.max(0.0, Math.min(1.0, ThreadLocalRandom.current().nextGaussian(0.5, 0.22)));
    }

    private static MicrobeGeneProfile widenProfileVariance(MicrobeGeneProfile base) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new MicrobeGeneProfile(
                jitterTrait(base.heatResistance(), random),
                jitterTrait(base.toxinResistance(), random),
                jitterTrait(base.speed(), random),
                jitterTrait(base.diet(), random),
                jitterCap(base.maxHealth(), random),
                jitterCap(base.maxEnergy(), random)
        );
    }

    private static double jitterTrait(double origin, ThreadLocalRandom random) {
        double delta = random.nextDouble(-0.35, 0.35);
        return Math.max(0.0, Math.min(1.0, origin + delta));
    }

    void setMicrobeAmount(int amount) {
        amountSpinner.setValue(Math.max(1, amount));
    }

    void setFoodAmount(int amount) {
        amountSpinner.setValue(Math.max(1, amount));
    }

    MicrobeGeneProfile currentMicrobeProfile() {
        return new MicrobeGeneProfile(
                heatTrait.ratioValue(),
                toxinTrait.ratioValue(),
                speedTrait.ratioValue(),
                dietTrait.ratioValue(),
                ((Number) maxHealthInput.getValue()).doubleValue(),
                ((Number) maxEnergyInput.getValue()).doubleValue()
        );
    }

    String currentActivateButtonText() {
        return activateButton.getDisplayText();
    }

    boolean isCurrentActivateButtonDimmed() {
        return activateButton.isDimmed();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 2 * FRAME_MARGIN;
            int h = getHeight() - 2 * FRAME_MARGIN;
            g2.setColor(BG_COLOR);
            g2.fillRoundRect(FRAME_MARGIN, FRAME_MARGIN, w, h, CORNER_RADIUS, CORNER_RADIUS);
            g2.setColor(BORDER_GLOW_COLOR);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(FRAME_MARGIN, FRAME_MARGIN, w, h, CORNER_RADIUS, CORNER_RADIUS);
            g2.setColor(ACCENT_COLOR);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(FRAME_MARGIN, FRAME_MARGIN, w, h, CORNER_RADIUS, CORNER_RADIUS);
        } finally {
            g2.dispose();
        }
    }

    private static double jitterCap(double origin, ThreadLocalRandom random) {
        double ratio = random.nextDouble(0.55, 1.55);
        return Math.max(1.0, origin * ratio);
    }

    void setRandomEnabled(boolean enabled) {
        randomCheck.setSelected(enabled);
        if (enabled) {
            applyGeneratedProfile(generatedRandomProfile());
        }
    }

    private static void lockPreferredHeight(JComponent component) {
        Dimension pref = component.getPreferredSize();
        if (pref == null) {
            return;
        }
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
    }

    private JPanel wrapTraitRow(TraitControl control) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(control.component(), BorderLayout.CENTER);
        lockPreferredHeight(row);
        return row;
    }

    private void applyGeneratedProfile(MicrobeGeneProfile profile) {
        if (profile == null) {
            profile = MicrobeSpawnRequest.defaultProfile();
        }
        heatTrait.setRatioValue(profile.heatResistance());
        toxinTrait.setRatioValue(profile.toxinResistance());
        speedTrait.setRatioValue(profile.speed());
        dietTrait.setRatioValue(profile.diet());
        maxHealthInput.setValue((int) Math.round(profile.maxHealth()));
        maxEnergyInput.setValue((int) Math.round(profile.maxEnergy()));
        updatePreviewFromControls();
    }

    void setRandomProfileSupplier(Supplier<MicrobeGeneProfile> randomProfileSupplier) {
        this.randomProfileSupplier = randomProfileSupplier == null
                ? MicrobeSpawnRequest::defaultProfile
                : randomProfileSupplier;
    }

    void setSelectedMode(SpawnMode mode) {
        spawnMode = mode == null ? SpawnMode.MICROBE : mode;
        boolean microbeMode = spawnMode == SpawnMode.MICROBE;

        randomCheck.setVisible(microbeMode);
        randomRow.setVisible(true);
        amountRow.setVisible(true);
        applyTopRowLayout(microbeMode);
        microbeSection.setVisible(microbeMode);
        heatRow.setVisible(microbeMode);
        toxinRow.setVisible(microbeMode);
        speedRow.setVisible(microbeMode);
        dietRow.setVisible(microbeMode);
        microbeHealthRow.setVisible(microbeMode);
        microbeEnergyRow.setVisible(microbeMode);
        previewShell.setVisible(microbeMode);

        microbeModeButton.setDimmed(microbeMode);
        foodModeButton.setDimmed(!microbeMode);

        if (microbeMode) {
            updatePreviewFromControls();
        }
        setPreferredSize(new Dimension(PANEL_WIDTH, computePreferredPanelHeight(microbeMode)));
        if (layoutRefreshAction != null) {
            layoutRefreshAction.run();
        }
        body.revalidate();
        revalidate();
        repaint();
    }

    private JPanel rowWithLabel(String label, JSpinner spinner, Font labelFont, Color labelColor) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));

        JLabel amountLabel = new JLabel(label);
        amountLabel.setFont(labelFont);
        amountLabel.setForeground(labelColor);
        row.add(amountLabel, BorderLayout.WEST);

        OverlayControlFactory.styleSpinner(spinner);
        spinner.setPreferredSize(new Dimension(INPUT_WIDTH, ROW_HEIGHT));
        spinner.setMinimumSize(new Dimension(INPUT_WIDTH, ROW_HEIGHT));
        spinner.setMaximumSize(new Dimension(INPUT_WIDTH, ROW_HEIGHT));
        row.add(spinner, BorderLayout.EAST);

        return row;
    }

    private void applyTopRowLayout(boolean microbeMode) {
        randomRow.removeAll();
        if (microbeMode) {
            randomRow.add(randomCheck, BorderLayout.WEST);
            randomRow.add(amountRow, BorderLayout.EAST);
        } else {
            centeredAmountRow.removeAll();
            centeredAmountRow.add(amountRow);
            randomRow.add(centeredAmountRow, BorderLayout.CENTER);
        }
        randomRow.revalidate();
        randomRow.repaint();
    }

    public SimulationCommand buildSpawnCommand(double worldX, double worldY) {
        if (selectedMode() == SpawnMode.FOOD) {
            return SimulationCommand.spawnFood(new FoodSpawnRequest(worldX, worldY, currentAmount()));
        }

        MicrobeGeneProfile baseProfile = currentMicrobeProfile();
        boolean randomEnabled = randomCheck.isSelected();
        MicrobeGeneProfile randomAnchor = randomEnabled
                ? resolveRandomAnchorProfile(baseProfile)
                : baseProfile;
        MicrobeGeneProfile randomSpawnBase = randomEnabled
                ? new MicrobeGeneProfile(0.5, 0.5, 0.5, 0.5, randomAnchor.maxHealth(), randomAnchor.maxEnergy())
                : baseProfile;
        MicrobeGeneProfile firstProfile = randomEnabled
                ? buildRandomProfileFromAnchor(randomAnchor)
                : baseProfile;

        if (randomEnabled) {
            // Keep controls in sync with the randomized profile that will be spawned first.
            applyGeneratedProfile(firstProfile);
        }

        previewCanvas.setPreview(firstProfile.createMicrobe(worldX, worldY).toRenderState());
        return SimulationCommand.spawnMicrobes(new MicrobeSpawnRequest(
                worldX,
                worldY,
                currentAmount(),
                randomEnabled,
                randomSpawnBase,
                firstProfile
        ));
    }

    private MicrobeGeneProfile generatedRandomProfile() {
        return buildRandomProfileFromAnchor(resolveRandomAnchorProfile(MicrobeSpawnRequest.defaultProfile()));
    }

    private MicrobeGeneProfile buildRandomProfileFromAnchor(MicrobeGeneProfile anchor) {
        return new MicrobeGeneProfile(
                randomCenteredTrait(),
                randomCenteredTrait(),
                randomCenteredTrait(),
                randomCenteredTrait(),
                jitterCap(anchor.maxHealth(), ThreadLocalRandom.current()),
                jitterCap(anchor.maxEnergy(), ThreadLocalRandom.current())
        );
    }

    private MicrobeGeneProfile resolveRandomAnchorProfile(MicrobeGeneProfile fallback) {
        Supplier<MicrobeGeneProfile> supplier = randomProfileSupplier;
        MicrobeGeneProfile anchor = supplier == null ? null : supplier.get();
        if (anchor != null) {
            return anchor;
        }
        return fallback == null ? MicrobeSpawnRequest.defaultProfile() : fallback;
    }

    private int computePreferredPanelHeight(boolean microbeMode) {
        body.revalidate();
        contentPanel.revalidate();
        Dimension pref = contentPanel.getPreferredSize();
        int fallback = microbeMode ? MICROBE_MODE_HEIGHT : FOOD_MODE_HEIGHT;
        int height = pref == null ? fallback : pref.height;
        int minimum = microbeMode ? 520 : 250;
        return Math.max(minimum, height);
    }

    private void registerLivePreviewListeners() {
        Runnable update = this::updatePreviewFromControls;
        heatTrait.setOnValueChanged(update);
        toxinTrait.setOnValueChanged(update);
        speedTrait.setOnValueChanged(update);
        dietTrait.setOnValueChanged(update);
        maxHealthInput.addChangeListener(e -> updatePreviewFromControls());
        maxEnergyInput.addChangeListener(e -> updatePreviewFromControls());
    }

    private void updatePreviewFromControls() {
        if (selectedMode() != SpawnMode.MICROBE) {
            return;
        }
        previewCanvas.setPreview(currentMicrobeProfile().createMicrobe(0, 0).toRenderState());
    }

    public enum SpawnMode {
        MICROBE,
        FOOD
    }

    private static final class PreviewCanvas extends JPanel {
        private static final Font LABEL = new Font("Segoe UI", Font.BOLD, 12);
        private Microbe.RenderState preview;

        private PreviewCanvas() {
            setOpaque(false);
            setPreferredSize(new Dimension(240, 182));
        }

        private void setPreview(Microbe.RenderState renderState) {
            this.preview = renderState;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(OverlayTheme.ACCENT);
                g2.setFont(LABEL);
                g2.drawString("Preview", 8, 14);
                if (preview == null) {
                    return;
                }
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                MicrobePreviewRenderer.paintPreview(g2, preview, cx, cy, 5.0);
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class TraitControl {
        private final JPanel panel = new JPanel();
        private final JSlider slider;
        private final JLabel valueLabel;
        private Runnable onValueChanged;
        private final int min;
        private final int max;
        private int value;

        private TraitControl(String label, int initial, int min, int max, boolean ratio, Color traitColor) {
            this.min = min;
            this.max = max;
            this.value = Math.max(min, Math.min(max, initial));
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel title = new JLabel(label);
            title.setFont(LABEL_FONT);
            title.setForeground(traitColor);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);

            slider = new JSlider(min, max, this.value);
            slider.setOpaque(false);
            slider.setForeground(traitColor);
            slider.setAlignmentX(Component.LEFT_ALIGNMENT);

            valueLabel = new JLabel(formatValue(ratio, this.value), SwingConstants.RIGHT);
            valueLabel.setFont(VALUE_FONT);
            valueLabel.setForeground(traitColor);
            valueLabel.setPreferredSize(new Dimension(60, 22));

            slider.addChangeListener(e -> {
                this.value = slider.getValue();
                valueLabel.setText(formatValue(ratio, this.value));
                fireChanged();
            });

            JPanel top = new JPanel(new BorderLayout(8, 0));
            top.setOpaque(false);
            top.setAlignmentX(Component.LEFT_ALIGNMENT);
            top.add(title, BorderLayout.WEST);
            top.add(valueLabel, BorderLayout.EAST);

            panel.add(top);
            panel.add(Box.createVerticalStrut(TRAIT_TITLE_TO_SLIDER_GAP));
            panel.add(slider);
            lockPreferredHeight(panel);
        }

        private JComponent component() {
            return panel;
        }

        private static String formatValue(boolean percent, int value) {
            return percent ? (value + " %") : String.valueOf(value);
        }

        private double ratioValue() {
            return value / 100.0;
        }

        private void setRatioValue(double value) {
            int next = (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0);
            setValue(next, true);
        }

        private double numericValue() {
            return value;
        }

        private void setNumericValue(double value) {
            int next = (int) Math.round(value);
            setValue(next, false);
        }

        private void setValue(int next, boolean percent) {
            value = Math.max(min, Math.min(max, next));
            slider.setValue(value);
            valueLabel.setText(formatValue(percent, value));
        }

        private void setOnValueChanged(Runnable onValueChanged) {
            this.onValueChanged = onValueChanged;
        }

        private void fireChanged() {
            if (onValueChanged != null) {
                onValueChanged.run();
            }
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "TraitControl[value=%s]", value);
        }
    }

    private static final class ColoredSpinnerUI extends BasicSpinnerUI {
        private final Color accent;

        private ColoredSpinnerUI(Color accent) {
            this.accent = accent;
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            c.setOpaque(false);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth();
            int h = c.getHeight();
            g2.setColor(OverlayTheme.CONTROL_BG);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.setColor(accent);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.dispose();
            super.paint(g, c);
        }

        @Override
        protected Component createNextButton() {
            JButton button = createArrowButton(true);
            button.setName("Spinner.nextButton");
            installNextButtonListeners(button);
            return button;
        }

        @Override
        protected Component createPreviousButton() {
            JButton button = createArrowButton(false);
            button.setName("Spinner.previousButton");
            installPreviousButtonListeners(button);
            return button;
        }

        private JButton createArrowButton(boolean up) {
            JButton btn = new JButton() {
                private boolean hovered;

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color hoverColor = new Color(
                            Math.min(255, accent.getRed() + 35),
                            Math.min(255, accent.getGreen() + 35),
                            Math.min(255, accent.getBlue() + 35)
                    );
                    g2.setColor(hovered ? hoverColor : accent);
                    g2.setStroke(new BasicStroke(hovered ? 3f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2;
                    int aw = 4;
                    int ah = 3;
                    if (up) {
                        g2.drawLine(cx - aw, cy + ah, cx, cy - ah);
                        g2.drawLine(cx, cy - ah, cx + aw, cy + ah);
                    } else {
                        g2.drawLine(cx - aw, cy - ah, cx, cy + ah);
                        g2.drawLine(cx, cy + ah, cx + aw, cy - ah);
                    }
                    g2.dispose();
                }

                @Override
                protected void processMouseEvent(MouseEvent e) {
                    super.processMouseEvent(e);
                    switch (e.getID()) {
                        case MouseEvent.MOUSE_ENTERED -> {
                            hovered = true;
                            repaint();
                        }
                        case MouseEvent.MOUSE_EXITED -> {
                            hovered = false;
                            repaint();
                        }
                        default -> {
                        }
                    }
                }
            };
            btn.setOpaque(false);
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setPreferredSize(new Dimension(24, 14));
            return btn;
        }
    }
}


