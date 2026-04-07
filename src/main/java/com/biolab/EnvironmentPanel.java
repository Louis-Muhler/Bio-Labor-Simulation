package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Locale;

/**
 * Floating overlay panel that exposes two environment sliders plus one food-per-tick control.
 *
 * <p>Temperature and Toxicity map to normalised [0.0, 1.0] values.
 * Food spawn is configured as a direct amount per tick (fractional allowed).
 * Slider positions are stored as {@link Rectangle} instances so mouse hit-tests
 * can be performed without re-computing layout on every event.</p>
 *
 * <p>The visual style mirrors {@link InspectorPanel}: dark background
 * ({@code #121212}), 15 px corner radius, neon-cyan glow border.</p>
 */
public class EnvironmentPanel extends JPanel {
    private final SimulationRuntime engine;

    private static final int PANEL_WIDTH = 300;
    private static final int MARGIN = 20;
    private static final int CONTENT_PADDING = 15;

    // ── Shared colours ────────────────────────────────────────────────────
    private static final Color BG_COLOR = OverlayTheme.PANEL_BG_ALPHA;
    private static final Color ACCENT_COLOR = OverlayTheme.ACCENT;
    private static final Color BORDER_GLOW_COLOR = OverlayTheme.ACCENT_GLOW;
    /**
     * Separator and empty-bar background – matches the InspectorPanel grid colour.
     */
    private static final Color SEPARATOR_COLOR = new Color(40, 40, 50);
    private static final Color BAR_BG_COLOR = new Color(40, 40, 50);

    // ── Fonts (identical hierarchy to InspectorPanel) ─────────────────────
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font VALUE_FONT = new Font("Consolas", Font.BOLD, 13);

    // ── Strokes ───────────────────────────────────────────────────────────
    private static final BasicStroke STROKE_1 = new BasicStroke(1);
    private static final BasicStroke STROKE_3 = new BasicStroke(3);

    // ── Per-slider colours (order: Temperature, Toxicity) ─
    private static final Color[] SLIDER_COLORS = {
            new Color(255, 100, 100),
            new Color(100, 255, 100)
    };

    private static final String[] SLIDER_LABELS = {"Temperature", "Toxicity"};
    private static final String FOOD_LABEL = "Food Spawn";
    private static final double FOOD_STEP_PER_CLICK = 0.25;
    private static final double MAX_FOOD_SPAWN_PER_TICK = 1000000;
    private static final int FOOD_SPINNER_HEIGHT = 30;
    private static final Color FOOD_SPINNER_BORDER = new Color(95, 145, 255, 220);

    // ── Slider layout constants ───────────────────────────────────────────

    private final JSlider temperatureSlider;
    private final JSlider toxicitySlider;
    private final JLabel temperatureLabel;
    private final JLabel toxicityLabel;
    private final JLabel foodLabel;
    private final JLabel temperatureValueLabel;
    private final JLabel toxicityValueLabel;
    private final JLabel foodValueLabel;
    private final JSpinner foodSpawnSpinner;
    private boolean syncingFoodSpinner;
    private boolean syncingControls;

    // ────────────────────────────────────────────────────────────────────
    // Construction
    // ────────────────────────────────────────────────────────────────────

    /**
     * @param engine the simulation engine whose environment parameters are controlled
     */
    public EnvironmentPanel(SimulationRuntime engine) {
        this.engine = engine;
        setPreferredSize(new Dimension(PANEL_WIDTH, 310));
        setBackground(new Color(0, 0, 0, 0));
        setOpaque(false);
        setLayout(null);

        temperatureSlider = createStyledSlider(SLIDER_COLORS[0]);
        toxicitySlider = createStyledSlider(SLIDER_COLORS[1]);
        temperatureLabel = createSectionLabel(SLIDER_LABELS[0], SLIDER_COLORS[0]);
        toxicityLabel = createSectionLabel(SLIDER_LABELS[1], SLIDER_COLORS[1]);
        foodLabel = createSectionLabel(FOOD_LABEL, FOOD_SPINNER_BORDER);
        temperatureValueLabel = createValueLabel(SLIDER_COLORS[0]);
        toxicityValueLabel = createValueLabel(SLIDER_COLORS[1]);
        foodValueLabel = createValueLabel(FOOD_SPINNER_BORDER);

        add(temperatureLabel);
        add(toxicityLabel);
        add(foodLabel);
        add(temperatureSlider);
        add(toxicitySlider);
        add(temperatureValueLabel);
        add(toxicityValueLabel);
        add(foodValueLabel);

        foodSpawnSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(0.0, engine.getFoodSpawnRate()),
                0.0,
                MAX_FOOD_SPAWN_PER_TICK,
                FOOD_STEP_PER_CLICK
        ));
        styleFoodSpinner(foodSpawnSpinner);
        foodSpawnSpinner.addChangeListener(e -> {
            if (syncingFoodSpinner) {
                return;
            }
            double next = ((Number) foodSpawnSpinner.getValue()).doubleValue();
            engine.enqueueCommand(SimulationCommand.setFoodSpawnRate(next));
            updateFoodValueLabel(next);
        });
        add(foodSpawnSpinner);

        temperatureSlider.addChangeListener(e -> {
            if (syncingControls) {
                return;
            }
            double value = temperatureSlider.getValue() / 100.0;
            temperatureValueLabel.setText(String.format(Locale.ROOT, "%d %%", temperatureSlider.getValue()));
            engine.enqueueCommand(SimulationCommand.setTemperature(value));
        });

        toxicitySlider.addChangeListener(e -> {
            if (syncingControls) {
                return;
            }
            double value = toxicitySlider.getValue() / 100.0;
            toxicityValueLabel.setText(String.format(Locale.ROOT, "%d %%", toxicitySlider.getValue()));
            engine.enqueueCommand(SimulationCommand.setToxicity(value));
        });

        syncControlsFromEngine();
    }

    // ────────────────────────────────────────────────────────────────────
    // Slider logic
    // ────────────────────────────────────────────────────────────────────

    private static JSlider createStyledSlider(Color color) {
        JSlider slider = new JSlider(0, 100, 30);
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setBackground(new Color(0, 0, 0, 0));
        slider.setUI(new FlatColorSliderUI(slider, color));
        return slider;
    }

    private static JLabel createValueLabel(Color color) {
        JLabel label = new JLabel("0%", SwingConstants.RIGHT);
        label.setFont(VALUE_FONT);
        label.setForeground(color);
        return label;
    }

    private static JLabel createSectionLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(LABEL_FONT);
        label.setForeground(color);
        return label;
    }

    private static void styleFoodSpinner(JSpinner spinner) {
        spinner.setUI(new BlueSpinnerUI());
        spinner.setOpaque(false);
        spinner.setBorder(BorderFactory.createEmptyBorder());
        spinner.setBackground(OverlayTheme.CONTROL_BG);

        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField field = defaultEditor.getTextField();
            field.setFont(new Font("Segoe UI", Font.BOLD, 14));
            field.setForeground(FOOD_SPINNER_BORDER);
            field.setCaretColor(FOOD_SPINNER_BORDER);
            field.setOpaque(false);
            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.setBorder(new EmptyBorder(6, 0, 6, 0));
            defaultEditor.setBorder(BorderFactory.createEmptyBorder());
            defaultEditor.setOpaque(false);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Painting
    // ────────────────────────────────────────────────────────────────────

    private void syncFoodSpinnerFromEngine() {
        double runtimeValue = Math.max(0.0, Math.min(MAX_FOOD_SPAWN_PER_TICK, engine.getFoodSpawnRate()));
        double spinnerValue = ((Number) foodSpawnSpinner.getValue()).doubleValue();
        if (Math.abs(runtimeValue - spinnerValue) < 0.0001) {
            return;
        }
        syncingFoodSpinner = true;
        try {
            foodSpawnSpinner.setValue(runtimeValue);
            updateFoodValueLabel(runtimeValue);
        } finally {
            syncingFoodSpinner = false;
        }
    }

    private void syncControlsFromEngine() {
        syncingControls = true;
        try {
            int temp = (int) Math.round(engine.getEnvironment().getTemperature() * 100.0);
            int tox = (int) Math.round(engine.getEnvironment().getToxicity() * 100.0);
            temperatureSlider.setValue(Math.max(0, Math.min(100, temp)));
            toxicitySlider.setValue(Math.max(0, Math.min(100, tox)));
            temperatureValueLabel.setText(String.format(Locale.ROOT, "%d%%", temperatureSlider.getValue()));
            toxicityValueLabel.setText(String.format(Locale.ROOT, "%d%%", toxicitySlider.getValue()));
        } finally {
            syncingControls = false;
        }
    }

    private void updateFoodValueLabel(double value) {
        foodValueLabel.setText(String.format(Locale.ROOT, "%.2f per tick", value));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int panelW = getWidth() - 2 * MARGIN;
            int panelH = getHeight() - 2 * MARGIN;

            // Dark rounded background
            g2d.setColor(BG_COLOR);
            g2d.fillRoundRect(MARGIN, MARGIN, panelW, panelH, 15, 15);

            // Outer glow, then crisp cyan border
            g2d.setColor(BORDER_GLOW_COLOR);
            g2d.setStroke(STROKE_3);
            g2d.drawRoundRect(MARGIN, MARGIN, panelW, panelH, 15, 15);
            g2d.setColor(ACCENT_COLOR);
            g2d.setStroke(STROKE_1);
            g2d.drawRoundRect(MARGIN, MARGIN, panelW, panelH, 15, 15);

            int x = MARGIN + CONTENT_PADDING;
            int y = MARGIN + CONTENT_PADDING;
            int contentWidth = panelW - 2 * CONTENT_PADDING;

            // Centred title
            g2d.setFont(TITLE_FONT);
            g2d.setColor(ACCENT_COLOR);
            FontMetrics fm = g2d.getFontMetrics();
            String titleStr = "ENVIRONMENT SETTINGS";
            g2d.drawString(titleStr, MARGIN + (panelW - fm.stringWidth(titleStr)) / 2, y + 15);
            y += 25;

            // 2 px separator
            g2d.setColor(SEPARATOR_COLOR);
            g2d.fillRect(x, y, contentWidth, 2);
            y += 14;

            layoutControls(x, y, contentWidth);
            syncControlsFromEngine();
            syncFoodSpinnerFromEngine();
        } finally {
            g2d.dispose();
        }
    }

    private void layoutControls(int x, int y, int contentWidth) {
        int valueW = 64;
        int labelW = Math.max(120, contentWidth / 2);
        int sliderY;

        temperatureLabel.setBounds(x, y, labelW, 18);
        temperatureValueLabel.setBounds(x + contentWidth - valueW, y, valueW, 18);
        y += 18;
        sliderY = y + 2;
        temperatureSlider.setBounds(x, sliderY, contentWidth, 26);
        y += 34;

        toxicityLabel.setBounds(x, y, labelW, 18);
        toxicityValueLabel.setBounds(x + contentWidth - valueW, y, valueW, 18);
        y += 18;
        sliderY = y + 2;
        toxicitySlider.setBounds(x, sliderY, contentWidth, 26);
        y += 40;

        foodLabel.setBounds(x, y, labelW, 18);
        foodValueLabel.setBounds(x + contentWidth - valueW, y, valueW, 18);
        y += 22;
        foodSpawnSpinner.setBounds(x, y, contentWidth, FOOD_SPINNER_HEIGHT);
    }

    private static final class BlueSpinnerUI extends BasicSpinnerUI {
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
            g2.setColor(FOOD_SPINNER_BORDER);
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
                    g2.setColor(hovered ? new Color(95, 145, 255, 255) : FOOD_SPINNER_BORDER);
                    g2.setStroke(new BasicStroke(hovered ? 2.8f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
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

    private static final class FlatColorSliderUI extends BasicSliderUI {
        private final Color accent;

        private FlatColorSliderUI(JSlider slider, Color accent) {
            super(slider);
            this.accent = accent;
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int trackHeight = 8;
            int y = trackRect.y + (trackRect.height - trackHeight) / 2;
            g2.setColor(BAR_BG_COLOR);
            g2.fillRoundRect(trackRect.x, y, trackRect.width, trackHeight, 8, 8);
            int fill = thumbRect.x - trackRect.x + thumbRect.width / 2;
            fill = Math.max(0, Math.min(trackRect.width, fill));
            g2.setColor(accent);
            g2.fillRoundRect(trackRect.x, y, fill, trackHeight, 8, 8);
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 110));
            g2.fillOval(thumbRect.x - 1, thumbRect.y - 1, thumbRect.width + 2, thumbRect.height + 2);
            g2.setColor(new Color(18, 18, 18, 230));
            g2.fillOval(thumbRect.x + 1, thumbRect.y + 1, thumbRect.width - 2, thumbRect.height - 2);
            g2.setColor(accent);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(thumbRect.x + 1, thumbRect.y + 1, thumbRect.width - 2, thumbRect.height - 2);
            g2.dispose();
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(14, 14);
        }
    }
}
