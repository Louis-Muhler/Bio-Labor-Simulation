package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Locale;

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
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font VALUE_FONT = new Font("Consolas", Font.PLAIN, 12);
    private static final int MICROBE_MODE_HEIGHT = 620;
    private static final int FOOD_MODE_HEIGHT = 380;

    private final ModernButton microbeModeButton = new ModernButton("MICROBE");
    private final ModernButton foodModeButton = new ModernButton("FOOD");
    private final JCheckBox randomCheck = OverlayControlFactory.createStyledCheckBox("Random", false);
    private final JSpinner amountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
    private final TraitControl heatTrait = new TraitControl("Heat Resistance", 50, 0, 100, true);
    private final TraitControl toxinTrait = new TraitControl("Toxin Resistance", 50, 0, 100, true);
    private final TraitControl speedTrait = new TraitControl("Speed", 50, 0, 100, true);
    private final TraitControl dietTrait = new TraitControl("Diet", 50, 0, 100, true);
    private final JSpinner maxHealthInput = new JSpinner(new SpinnerNumberModel(100, 20, 400, 1));
    private final JSpinner maxEnergyInput = new JSpinner(new SpinnerNumberModel(100, 20, 400, 1));

    private final PreviewCanvas previewCanvas = new PreviewCanvas();
    private final JPanel microbeHealthRow;
    private final JPanel microbeEnergyRow;
    private final JPanel previewShell;
    private final ModernButton activateButton;
    private final JPanel randomAmountRow;
    private final JPanel amountOnlyRow;
    private final JPanel body;
    private final JPanel heatRow;
    private final JPanel toxinRow;
    private final JPanel speedRow;
    private final JPanel dietRow;
    private Runnable activateSpawnToolAction;
    private boolean spawnToolActive;
    private SpawnMode spawnMode = SpawnMode.MICROBE;

    public MicrobeCreatorPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(PANEL_WIDTH, MICROBE_MODE_HEIGHT));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(FRAME_MARGIN + 10, FRAME_MARGIN + 8, FRAME_MARGIN + 10, FRAME_MARGIN + 8));

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
        content.add(header, BorderLayout.NORTH);

        body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(8, 0, 8, 0));

        randomCheck.addActionListener(e -> applyGeneratedProfile(MicrobeSpawnRequest.randomizedProfile(MicrobeSpawnRequest.defaultProfile())));
        randomAmountRow = new JPanel(new BorderLayout(8, 0));
        randomAmountRow.setOpaque(false);
        randomAmountRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        randomAmountRow.add(randomCheck, BorderLayout.WEST);
        JPanel amountCompact = rowWithLabel("Amount", amountSpinner);
        amountCompact.setMaximumSize(new Dimension(165, 36));
        randomAmountRow.add(amountCompact, BorderLayout.EAST);
        body.add(randomAmountRow);
        body.add(Box.createVerticalStrut(10));

        amountOnlyRow = rowWithLabel("Amount", amountSpinner);
        amountOnlyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(amountOnlyRow);
        body.add(Box.createVerticalStrut(10));

        heatRow = wrapTraitRow(heatTrait);
        body.add(heatRow);
        body.add(Box.createVerticalStrut(6));
        toxinRow = wrapTraitRow(toxinTrait);
        body.add(toxinRow);
        body.add(Box.createVerticalStrut(6));
        speedRow = wrapTraitRow(speedTrait);
        body.add(speedRow);
        body.add(Box.createVerticalStrut(6));
        dietRow = wrapTraitRow(dietTrait);
        body.add(dietRow);
        body.add(Box.createVerticalStrut(8));

        microbeHealthRow = rowWithLabel("Max Health", maxHealthInput);
        microbeEnergyRow = rowWithLabel("Max Energy", maxEnergyInput);
        body.add(microbeHealthRow);
        body.add(Box.createVerticalStrut(6));
        body.add(microbeEnergyRow);
        body.add(Box.createVerticalStrut(10));

        previewShell = OverlayControlFactory.wrapInInnerFrame(previewCanvas);
        previewShell.setPreferredSize(new Dimension(1, 168));
        previewShell.setMaximumSize(new Dimension(Integer.MAX_VALUE, 168));
        previewShell.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(previewShell);
        body.add(Box.createVerticalStrut(10));

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

        JScrollPane scrollPane = OverlayScrollSupport.createWheelOnlyScrollPane(body, new Insets(0, 0, 0, 0), 18);
        content.add(scrollPane, BorderLayout.CENTER);

        add(content, BorderLayout.CENTER);
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

    boolean isSpawnToolActive() {
        return spawnToolActive;
    }

    public void setSpawnToolActive(boolean active) {
        spawnToolActive = active;
        String label = active ? "Deactivate Spawn Tool" : "Activate Spawn Tool";
        activateButton.setDisplayText(label);
        activateButton.setDimmed(active);
    }

    public SimulationCommand buildSpawnCommand(double worldX, double worldY) {
        if (selectedMode() == SpawnMode.FOOD) {
            return SimulationCommand.spawnFood(new FoodSpawnRequest(worldX, worldY, currentAmount()));
        }

        MicrobeGeneProfile baseProfile = currentMicrobeProfile();
        boolean randomEnabled = randomCheck.isSelected();
        MicrobeGeneProfile firstProfile = randomEnabled
                ? MicrobeSpawnRequest.randomizedProfile(baseProfile)
                : baseProfile;

        previewCanvas.setPreview(firstProfile.createMicrobe(worldX, worldY).toRenderState());
        return SimulationCommand.spawnMicrobes(new MicrobeSpawnRequest(
                worldX,
                worldY,
                currentAmount(),
                randomEnabled,
                baseProfile,
                firstProfile
        ));
    }

    public boolean isRandomEnabled() {
        return randomCheck.isSelected();
    }

    void setRandomEnabled(boolean enabled) {
        randomCheck.setSelected(enabled);
        applyGeneratedProfile(MicrobeSpawnRequest.randomizedProfile(MicrobeSpawnRequest.defaultProfile()));
    }

    void setSelectedMode(SpawnMode mode) {
        spawnMode = mode == null ? SpawnMode.MICROBE : mode;
        boolean microbeMode = spawnMode == SpawnMode.MICROBE;

        randomCheck.setVisible(microbeMode);
        randomAmountRow.setVisible(microbeMode);
        amountOnlyRow.setVisible(!microbeMode);
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
        setPreferredSize(new Dimension(PANEL_WIDTH, microbeMode ? MICROBE_MODE_HEIGHT : FOOD_MODE_HEIGHT));
        body.revalidate();
        revalidate();
        repaint();
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

    private JPanel rowWithLabel(String label, JSpinner spinner) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel amountLabel = new JLabel(label);
        amountLabel.setFont(LABEL_FONT);
        amountLabel.setForeground(ACCENT_COLOR);
        row.add(amountLabel, BorderLayout.WEST);

        OverlayControlFactory.styleSpinner(spinner);
        spinner.setPreferredSize(new Dimension(105, 30));
        spinner.setMaximumSize(new Dimension(105, 30));
        row.add(spinner, BorderLayout.EAST);

        return row;
    }

    private JPanel wrapTraitRow(TraitControl control) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(control.component(), BorderLayout.CENTER);
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
            setPreferredSize(new Dimension(240, 165));
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
                int cy = Math.max(50, getHeight() / 2 + 10);
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

        private TraitControl(String label, int initial, int min, int max, boolean ratio) {
            this.min = min;
            this.max = max;
            this.value = Math.max(min, Math.min(max, initial));
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel title = new JLabel(label);
            title.setFont(LABEL_FONT);
            title.setForeground(ACCENT_COLOR);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);

            slider = new JSlider(min, max, this.value);
            slider.setOpaque(false);
            slider.setForeground(ACCENT_COLOR);
            slider.setAlignmentX(Component.LEFT_ALIGNMENT);

            valueLabel = new JLabel(formatValue(ratio, this.value), SwingConstants.RIGHT);
            valueLabel.setFont(VALUE_FONT);
            valueLabel.setForeground(ACCENT_COLOR);
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
            panel.add(Box.createVerticalStrut(2));
            panel.add(slider);
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
}


