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
    private final JLabel foodHint;
    private final ModernButton activateButton;
    private Runnable activateSpawnToolAction;
    private boolean spawnToolActive;
    private SpawnMode spawnMode = SpawnMode.MICROBE;

    public MicrobeCreatorPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(PANEL_WIDTH, 620));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(FRAME_MARGIN + 10, FRAME_MARGIN + 8, FRAME_MARGIN + 10, FRAME_MARGIN + 8));

        JLabel title = new JLabel("ENTITY SPAWNER", SwingConstants.CENTER);
        title.setForeground(ACCENT_COLOR);
        title.setFont(TITLE_FONT);
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(title);
        header.add(Box.createVerticalStrut(8));

        JPanel separator = new JPanel();
        separator.setOpaque(true);
        separator.setBackground(new Color(40, 40, 50));
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        separator.setPreferredSize(new Dimension(10, 2));
        header.add(separator);
        header.add(Box.createVerticalStrut(10));

        JPanel modeRow = new JPanel(new GridLayout(1, 2, 8, 0));
        modeRow.setOpaque(false);
        microbeModeButton.setPreferredSize(new Dimension(120, 34));
        foodModeButton.setPreferredSize(new Dimension(120, 34));
        microbeModeButton.addActionListener(e -> setSelectedMode(SpawnMode.MICROBE));
        foodModeButton.addActionListener(e -> setSelectedMode(SpawnMode.FOOD));
        modeRow.add(microbeModeButton);
        modeRow.add(foodModeButton);
        header.add(modeRow);
        content.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(8, 6, 8, 6));

        randomCheck.addActionListener(e -> applyGeneratedProfile(MicrobeSpawnRequest.randomizedProfile(MicrobeSpawnRequest.defaultProfile())));
        body.add(randomCheck);
        body.add(Box.createVerticalStrut(8));

        body.add(rowWithLabel("Amount", amountSpinner));
        body.add(Box.createVerticalStrut(10));

        body.add(heatTrait.component());
        body.add(Box.createVerticalStrut(6));
        body.add(toxinTrait.component());
        body.add(Box.createVerticalStrut(6));
        body.add(speedTrait.component());
        body.add(Box.createVerticalStrut(6));
        body.add(dietTrait.component());
        body.add(Box.createVerticalStrut(8));

        microbeHealthRow = rowWithLabel("Max Health", maxHealthInput);
        microbeEnergyRow = rowWithLabel("Max Energy", maxEnergyInput);
        body.add(microbeHealthRow);
        body.add(Box.createVerticalStrut(6));
        body.add(microbeEnergyRow);
        body.add(Box.createVerticalStrut(10));

        previewShell = OverlayControlFactory.wrapInInnerFrame(previewCanvas);
        previewShell.setPreferredSize(new Dimension(280, 190));
        previewShell.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        body.add(previewShell);
        body.add(Box.createVerticalStrut(10));

        foodHint = new JLabel("Food mode uses Amount + Spawn Tool only.");
        foodHint.setFont(VALUE_FONT);
        foodHint.setForeground(ACCENT_COLOR);
        foodHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(foodHint);
        body.add(Box.createVerticalStrut(10));

        activateButton = new ModernButton("Activate Spawn Tool");
        activateButton.setPreferredSize(new Dimension(220, 38));
        activateButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
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
        heatTrait.component().setVisible(microbeMode);
        toxinTrait.component().setVisible(microbeMode);
        speedTrait.component().setVisible(microbeMode);
        dietTrait.component().setVisible(microbeMode);
        microbeHealthRow.setVisible(microbeMode);
        microbeEnergyRow.setVisible(microbeMode);
        previewShell.setVisible(microbeMode);
        foodHint.setVisible(!microbeMode);

        microbeModeButton.setDimmed(microbeMode);
        foodModeButton.setDimmed(!microbeMode);

        if (microbeMode) {
            updatePreviewFromControls();
        }
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
        spinner.setMaximumSize(new Dimension(110, 30));
        row.add(spinner, BorderLayout.EAST);

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
        private final JPanel panel = new JPanel(new BorderLayout(6, 2));
        private final JSlider slider;
        private final JSpinner spinner;
        private Runnable onValueChanged;

        private TraitControl(String label, int initial, int min, int max, boolean ratio) {
            panel.setOpaque(false);
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel title = new JLabel(label);
            title.setFont(LABEL_FONT);
            title.setForeground(ACCENT_COLOR);

            slider = new JSlider(min, max, initial);
            slider.setOpaque(false);
            slider.setForeground(ACCENT_COLOR);

            spinner = new JSpinner(new SpinnerNumberModel(initial, min, max, 1));
            OverlayControlFactory.styleSpinner(spinner);
            spinner.setPreferredSize(new Dimension(78, 28));

            slider.addChangeListener(e -> {
                int value = slider.getValue();
                if (!spinner.getValue().equals(value)) {
                    spinner.setValue(value);
                }
                fireChanged();
            });
            spinner.addChangeListener(e -> {
                int value = ((Number) spinner.getValue()).intValue();
                if (slider.getValue() != value) {
                    slider.setValue(value);
                }
                fireChanged();
            });

            JPanel top = new JPanel(new BorderLayout(6, 0));
            top.setOpaque(false);
            top.add(title, BorderLayout.WEST);
            top.add(spinner, BorderLayout.EAST);

            panel.add(top, BorderLayout.NORTH);
            panel.add(slider, BorderLayout.CENTER);
        }

        private JComponent component() {
            return panel;
        }

        private double ratioValue() {
            return ((Number) spinner.getValue()).doubleValue() / 100.0;
        }

        private void setRatioValue(double value) {
            spinner.setValue((int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0));
        }

        private double numericValue() {
            return ((Number) spinner.getValue()).doubleValue();
        }

        private void setNumericValue(double value) {
            int next = (int) Math.round(value);
            int min = slider.getMinimum();
            int max = slider.getMaximum();
            spinner.setValue(Math.max(min, Math.min(max, next)));
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
            return String.format(Locale.ROOT, "TraitControl[value=%s]", spinner.getValue());
        }
    }
}


