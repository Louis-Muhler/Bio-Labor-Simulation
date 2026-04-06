package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSpinnerUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
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
    private static final BasicStroke STROKE_1_5 = new BasicStroke(1.5f);
    private static final BasicStroke STROKE_3 = new BasicStroke(3);

    // ── Per-slider colours (order: Temperature, Toxicity) ─
    private static final Color[] SLIDER_COLORS = {
            new Color(255, 100, 100),
            new Color(100, 255, 100)
    };
    /**
     * Semi-transparent glow drawn 1 px outside the filled bar.
     */
    private static final Color[] SLIDER_GLOW_COLORS = {
            new Color(255, 100, 100, 60),
            new Color(100, 255, 100, 60)
    };
    /** Glow ring drawn around the slider thumb. */
    private static final Color[] SLIDER_THUMB_GLOW_COLORS = {
            new Color(255, 100, 100, 80),
            new Color(100, 255, 100, 80)
    };

    private static final String[] SLIDER_LABELS = {"Temperature", "Toxicity"};
    private static final String FOOD_LABEL = "Food Spawn/Tick";
    private static final double FOOD_STEP_PER_CLICK = 0.25;
    private static final double MAX_FOOD_SPAWN_PER_TICK = 1000000;
    private static final int FOOD_SPINNER_HEIGHT = 30;
    private static final Color FOOD_SPINNER_BORDER = new Color(95, 145, 255, 220);

    // ── Slider layout constants ───────────────────────────────────────────
    private static final int BAR_HEIGHT = 12;
    private static final int TRIANGLE_WIDTH = 7;
    private static final int TRIANGLE_HEIGHT = 10;
    private static final int LABEL_OFFSET_X = 12;
    private static final int LABEL_SPACING_Y = 18;
    private static final int VALUE_SPACING_Y     = 20;
    private static final int SLIDER_BOTTOM_SPACING = 16;
    private static final int THUMB_RADIUS        = 7;
    private static final int SLIDER_CORNER_RADIUS = 6;

    /**
     * Bounding rectangles of the two slider bars, updated each paint cycle.
     * Used for mouse hit-testing on press and drag events.
     */
    private final Rectangle[] sliderBars = new Rectangle[2];
    private final JSpinner foodSpawnSpinner;
    private boolean syncingFoodSpinner;

    /**
     * Index of the slider currently being dragged, or -1 when idle.
     */
    private int draggingSlider = -1;

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
        });
        add(foodSpawnSpinner);

        for (int i = 0; i < sliderBars.length; i++) {
            sliderBars[i] = new Rectangle();
        }

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                for (int i = 0; i < sliderBars.length; i++) {
                    // Expand the hit area by 5 px vertically for easier interaction
                    Rectangle hitArea = new Rectangle(
                        sliderBars[i].x, sliderBars[i].y - 5,
                        sliderBars[i].width, sliderBars[i].height + 10
                    );
                    if (hitArea.contains(e.getPoint())) {
                        draggingSlider = i;
                        updateSliderValue(i, e.getX());
                        break;
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingSlider >= 0) updateSliderValue(draggingSlider, e.getX());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggingSlider = -1;
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    // ────────────────────────────────────────────────────────────────────
    // Slider logic
    // ────────────────────────────────────────────────────────────────────

    /**
     * Converts the raw mouse X position to a [0.0, 1.0] value and propagates
     * it to the engine. Safe to call from the EDT; the engine uses volatile
     * fields and synchronized setters.
     */
    private void updateSliderValue(int sliderIndex, int mouseX) {
        Rectangle bar = sliderBars[sliderIndex];
        if (bar.width <= 0) return;

        double value = Math.max(0.0, Math.min(1.0, (double) (mouseX - bar.x) / bar.width));

        switch (sliderIndex) {
            case 0 -> engine.enqueueCommand(SimulationCommand.setTemperature(value));
            case 1 -> engine.enqueueCommand(SimulationCommand.setToxicity(value));
            default -> {}
        }
        repaint();
    }

    private static void styleFoodSpinner(JSpinner spinner) {
        spinner.setUI(new BlueSpinnerUI());
        spinner.setOpaque(false);
        spinner.setBorder(BorderFactory.createEmptyBorder());
        spinner.setBackground(OverlayTheme.CONTROL_BG);

        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField field = defaultEditor.getTextField();
            field.setFont(new Font("Consolas", Font.BOLD, 13));
            field.setForeground(OverlayTheme.ACCENT);
            field.setCaretColor(OverlayTheme.ACCENT);
            field.setOpaque(false);
            field.setHorizontalAlignment(SwingConstants.LEFT);
            field.setBorder(new EmptyBorder(6, 10, 6, 10));
            defaultEditor.setBorder(BorderFactory.createEmptyBorder());
            defaultEditor.setOpaque(false);
        }
    }

    /** Returns the current normalised value for the given slider index. */
    private double getSliderValue(int index) {
        return switch (index) {
            case 0 -> engine.getEnvironment().getTemperature();
            case 1 -> engine.getEnvironment().getToxicity();
            default -> 0;
        };
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
        } finally {
            syncingFoodSpinner = false;
        }
    }

    /**
     * Draws a single slider section: label with triangle icon, numeric value,
     * filled bar with glow, and a circular thumb handle.
     *
     * @return the Y coordinate directly below the rendered section
     */
    private int drawSliderSection(Graphics2D g2d, int x, int y, int contentWidth, int index) {
        Color color = SLIDER_COLORS[index];
        double value = getSliderValue(index);

        // Triangle bullet + label
        g2d.setFont(LABEL_FONT);
        g2d.setColor(color);
        int[] xPts = {x, x + TRIANGLE_WIDTH, x};
        int[] yPts = {y, y + TRIANGLE_HEIGHT / 2, y + TRIANGLE_HEIGHT};
        g2d.fillPolygon(xPts, yPts, 3);
        g2d.drawString(SLIDER_LABELS[index], x + LABEL_OFFSET_X, y + 10);
        y += LABEL_SPACING_Y;

        // Percentage value
        g2d.setFont(VALUE_FONT);
        g2d.setColor(ACCENT_COLOR);
        g2d.drawString(String.format("%.0f%%", value * 100), x, y + 12);
        y += VALUE_SPACING_Y;

        // Store bar bounds for mouse interaction
        int barY = y;
        sliderBars[index].setBounds(x, barY, contentWidth, BAR_HEIGHT);

        // Empty track
        g2d.setColor(BAR_BG_COLOR);
        g2d.fillRoundRect(x, barY, contentWidth, BAR_HEIGHT, SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);

        // Filled portion with soft glow halo
        int fillWidth = (int) (contentWidth * value);
        if (fillWidth > 0) {
            g2d.setColor(SLIDER_GLOW_COLORS[index]);
            g2d.fillRoundRect(x - 1, barY - 1, fillWidth + 2, BAR_HEIGHT + 2,
                    SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);
            g2d.setColor(color);
            g2d.fillRoundRect(x, barY, fillWidth, BAR_HEIGHT,
                    SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);
        }

        // Track border
        g2d.setColor(color);
        g2d.setStroke(STROKE_1);
        g2d.drawRoundRect(x, barY, contentWidth, BAR_HEIGHT, SLIDER_CORNER_RADIUS, SLIDER_CORNER_RADIUS);

        // Circular thumb at the fill end point
        int thumbX = x + fillWidth;
        int thumbCY = barY + BAR_HEIGHT / 2;
        g2d.setColor(SLIDER_THUMB_GLOW_COLORS[index]);
        g2d.fillOval(thumbX - THUMB_RADIUS - 1, thumbCY - THUMB_RADIUS - 1,
                (THUMB_RADIUS + 1) * 2, (THUMB_RADIUS + 1) * 2);
        g2d.setColor(BG_COLOR);
        g2d.fillOval(thumbX - THUMB_RADIUS + 1, thumbCY - THUMB_RADIUS + 1,
                (THUMB_RADIUS - 1) * 2, (THUMB_RADIUS - 1) * 2);
        g2d.setColor(color);
        g2d.setStroke(STROKE_1_5);
        g2d.drawOval(thumbX - THUMB_RADIUS + 1, thumbCY - THUMB_RADIUS + 1,
                (THUMB_RADIUS - 1) * 2, (THUMB_RADIUS - 1) * 2);

        return y + BAR_HEIGHT + SLIDER_BOTTOM_SPACING;
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

            for (int i = 0; i < sliderBars.length; i++) {
                y = drawSliderSection(g2d, x, y, contentWidth, i);
            }
            drawFoodSpawnSection(g2d, x, y, contentWidth);
            syncFoodSpinnerFromEngine();
        } finally {
            g2d.dispose();
        }
    }

    private void drawFoodSpawnSection(Graphics2D g2d, int x, int y, int contentWidth) {
        Color color = new Color(100, 150, 255);

        g2d.setFont(LABEL_FONT);
        g2d.setColor(color);
        int[] xPts = {x, x + TRIANGLE_WIDTH, x};
        int[] yPts = {y, y + TRIANGLE_HEIGHT / 2, y + TRIANGLE_HEIGHT};
        g2d.fillPolygon(xPts, yPts, 3);
        g2d.drawString(FOOD_LABEL, x + LABEL_OFFSET_X, y + 10);
        y += LABEL_SPACING_Y;

        g2d.setFont(VALUE_FONT);
        g2d.setColor(ACCENT_COLOR);
        g2d.drawString(String.format(Locale.ROOT, "%.2f / tick", engine.getFoodSpawnRate()), x, y + 12);
        y += VALUE_SPACING_Y;

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
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(FOOD_SPINNER_BORDER);
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
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
