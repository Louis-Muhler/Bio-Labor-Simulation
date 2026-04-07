package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Locale;

/**
 * Left viewer panel used to configure spawn tools for microbes and food.
 */
public class MicrobeCreatorPanel extends JPanel {
    public static final int PANEL_WIDTH = 320;

    private static final int FRAME_MARGIN = 20;
    private static final int CORNER_RADIUS = 15;
    private static final Color BG_COLOR = OverlayTheme.PANEL_BG_ALPHA;
    private static final Color ACCENT_COLOR = OverlayTheme.ACCENT;
    private static final Color BORDER_GLOW_COLOR = OverlayTheme.ACCENT_GLOW;
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font VALUE_FONT = new Font("Consolas", Font.PLAIN, 12);

    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final JCheckBox randomCheck = OverlayControlFactory.createStyledCheckBox("Random", false);
    private final JSpinner microbeAmountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 250, 1));
    private final JSpinner foodAmountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
    private final TraitControl heatTrait = new TraitControl("Heat Resistance", 50, 0, 100, true);
    private final TraitControl toxinTrait = new TraitControl("Toxin Resistance", 50, 0, 100, true);
    private final TraitControl speedTrait = new TraitControl("Speed", 50, 0, 100, true);
    private final TraitControl dietTrait = new TraitControl("Diet", 50, 0, 100, true);
    private final TraitControl maxHealthTrait = new TraitControl("Max Health", 100, 20, 400, false);
    private final TraitControl maxEnergyTrait = new TraitControl("Max Energy", 100, 20, 400, false);

    private final PreviewCanvas previewCanvas = new PreviewCanvas();
    private Runnable activateSpawnToolAction;

    public MicrobeCreatorPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(PANEL_WIDTH, 560));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(FRAME_MARGIN + 10, FRAME_MARGIN + 8, FRAME_MARGIN + 10, FRAME_MARGIN + 8));

        JLabel title = new JLabel("MICROBE CREATOR", SwingConstants.CENTER);
        title.setForeground(ACCENT_COLOR);
        title.setFont(TITLE_FONT);
        content.add(title, BorderLayout.NORTH);

        tabbedPane.addTab("Microbe", buildMicrobeTab());
        tabbedPane.addTab("Food", buildFoodTab());
        tabbedPane.setOpaque(false);
        tabbedPane.setForeground(ACCENT_COLOR);
        tabbedPane.setFont(LABEL_FONT);

        JScrollPane scrollPane = OverlayControlFactory.createStyledScrollPane(tabbedPane);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        content.add(scrollPane, BorderLayout.CENTER);

        add(content, BorderLayout.CENTER);
        applyRandomDefaults();
    }

    public SpawnMode selectedMode() {
        return tabbedPane.getSelectedIndex() == 0 ? SpawnMode.MICROBE : SpawnMode.FOOD;
    }

    public int currentAmount() {
        return selectedMode() == SpawnMode.MICROBE
                ? ((Number) microbeAmountSpinner.getValue()).intValue()
                : ((Number) foodAmountSpinner.getValue()).intValue();
    }

    public void setActivateSpawnToolAction(Runnable action) {
        this.activateSpawnToolAction = action;
    }

    public SimulationCommand buildSpawnCommand(double worldX, double worldY) {
        if (selectedMode() == SpawnMode.FOOD) {
            return SimulationCommand.spawnFood(new FoodSpawnRequest(worldX, worldY, currentAmount()));
        }

        MicrobeGeneProfile baseProfile = new MicrobeGeneProfile(
                heatTrait.ratioValue(),
                toxinTrait.ratioValue(),
                speedTrait.ratioValue(),
                dietTrait.ratioValue(),
                maxHealthTrait.numericValue(),
                maxEnergyTrait.numericValue()
        );
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
        if (enabled) {
            applyRandomDefaults();
        }
    }

    void setSelectedMode(SpawnMode mode) {
        tabbedPane.setSelectedIndex(mode == SpawnMode.FOOD ? 1 : 0);
    }

    void setMicrobeAmount(int amount) {
        microbeAmountSpinner.setValue(Math.max(1, amount));
    }

    void setFoodAmount(int amount) {
        foodAmountSpinner.setValue(Math.max(1, amount));
    }

    MicrobeGeneProfile currentMicrobeProfile() {
        return new MicrobeGeneProfile(
                heatTrait.ratioValue(),
                toxinTrait.ratioValue(),
                speedTrait.ratioValue(),
                dietTrait.ratioValue(),
                maxHealthTrait.numericValue(),
                maxEnergyTrait.numericValue()
        );
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

    private JPanel buildMicrobeTab() {
        JPanel root = new JPanel();
        root.setOpaque(false);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(8, 6, 8, 6));

        randomCheck.addActionListener(e -> {
            if (randomCheck.isSelected()) {
                applyRandomDefaults();
            }
        });
        root.add(randomCheck);
        root.add(Box.createVerticalStrut(8));

        root.add(rowWithLabel("Amount", microbeAmountSpinner));
        root.add(Box.createVerticalStrut(10));

        root.add(heatTrait.component());
        root.add(Box.createVerticalStrut(6));
        root.add(toxinTrait.component());
        root.add(Box.createVerticalStrut(6));
        root.add(speedTrait.component());
        root.add(Box.createVerticalStrut(6));
        root.add(dietTrait.component());
        root.add(Box.createVerticalStrut(6));
        root.add(maxHealthTrait.component());
        root.add(Box.createVerticalStrut(6));
        root.add(maxEnergyTrait.component());
        root.add(Box.createVerticalStrut(10));

        JPanel previewShell = OverlayControlFactory.wrapInInnerFrame(previewCanvas);
        previewShell.setPreferredSize(new Dimension(250, 180));
        previewShell.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        root.add(previewShell);
        root.add(Box.createVerticalStrut(10));

        ModernButton activate = new ModernButton("Activate Spawn Tool");
        activate.setPreferredSize(new Dimension(210, 36));
        activate.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        activate.addActionListener(e -> {
            if (activateSpawnToolAction != null) {
                activateSpawnToolAction.run();
            }
        });
        root.add(activate);

        return root;
    }

    private JPanel buildFoodTab() {
        JPanel root = new JPanel();
        root.setOpaque(false);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(8, 6, 8, 6));

        root.add(rowWithLabel("Amount", foodAmountSpinner));
        root.add(Box.createVerticalStrut(10));

        JLabel hint = new JLabel("Use the same placement tool on the canvas.");
        hint.setFont(VALUE_FONT);
        hint.setForeground(ACCENT_COLOR);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(hint);
        root.add(Box.createVerticalStrut(10));

        ModernButton activate = new ModernButton("Activate Spawn Tool");
        activate.setPreferredSize(new Dimension(210, 36));
        activate.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        activate.addActionListener(e -> {
            if (activateSpawnToolAction != null) {
                activateSpawnToolAction.run();
            }
        });
        root.add(activate);

        return root;
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

    private void applyRandomDefaults() {
        MicrobeGeneProfile defaults = MicrobeSpawnRequest.defaultProfile();
        heatTrait.setRatioValue(defaults.heatResistance());
        toxinTrait.setRatioValue(defaults.toxinResistance());
        speedTrait.setRatioValue(defaults.speed());
        dietTrait.setRatioValue(defaults.diet());
        maxHealthTrait.setNumericValue(defaults.maxHealth());
        maxEnergyTrait.setNumericValue(defaults.maxEnergy());
        previewCanvas.setPreview(defaults.createMicrobe(0, 0).toRenderState());
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
        private final boolean ratio;

        private TraitControl(String label, int initial, int min, int max, boolean ratio) {
            this.ratio = ratio;
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
            });
            spinner.addChangeListener(e -> {
                int value = ((Number) spinner.getValue()).intValue();
                if (slider.getValue() != value) {
                    slider.setValue(value);
                }
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

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "TraitControl[%s=%s]", ratio ? "ratio" : "absolute", spinner.getValue());
        }
    }
}


