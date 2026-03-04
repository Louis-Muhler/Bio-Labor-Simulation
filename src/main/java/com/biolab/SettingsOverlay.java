package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * Full-screen settings overlay that appears above the simulation canvas.
 *
 * <p>The overlay dims the screen with a semi-transparent backdrop and centers
 * a rounded settings card. While open, the simulation loop is paused by the
 * caller via the {@code onApply}/{@code onCancel} callbacks.</p>
 *
 * <p>Two distinct close paths exist so the caller can react accordingly:</p>
 * <ul>
 *   <li>{@code onApply} – settings were saved; caller must re-apply display mode.</li>
 *   <li>{@code onCancel} – user dismissed without saving; no display change needed.</li>
 * </ul>
 */
public class SettingsOverlay extends JPanel {
    private final SettingsManager settingsManager;
    // ── Theme constants ──────────────────────────────────────────────────
    private static final Color OVERLAY_BG = new Color(0, 0, 0, 160);
    /**
     * Matches the dark panel background used by InspectorPanel and EnvironmentPanel.
     */
    private static final Color CARD_BG = new Color(18, 18, 18, 240);
    /**
     * Available window resolutions. Insertion order does not matter because
     * the combo box sorts them at construction time (width desc, height desc).
     */
    private static final Map<String, Dimension> RESOLUTIONS = new LinkedHashMap<>();
    private static final Color PANEL_BG = new Color(18, 18, 18);
    private static final Color ACCENT = new Color(0, 255, 255);
    private static final Color CONTROL_BG = new Color(12, 12, 14);
    private static final Color CONTROL_HOVER = new Color(25, 28, 32);
    private static final Color BORDER_COLOR = new Color(0, 255, 255, 80);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font SECTION_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    static {
        RESOLUTIONS.put("1024x768 (XGA)", new Dimension(1024, 768));
        RESOLUTIONS.put("1280x720 (HD)", new Dimension(1280, 720));
        RESOLUTIONS.put("1280x960 (SXGA-)", new Dimension(1280, 960));
        RESOLUTIONS.put("1366x768 (WXGA)", new Dimension(1366, 768));
        RESOLUTIONS.put("1440x900 (WXGA+)", new Dimension(1440, 900));
        RESOLUTIONS.put("1600x900 (HD+)", new Dimension(1600, 900));
        RESOLUTIONS.put("1600x1200 (UXGA)", new Dimension(1600, 1200));
        RESOLUTIONS.put("1680x1050 (WSXGA+)", new Dimension(1680, 1050));
        RESOLUTIONS.put("1920x1080 (FHD)", new Dimension(1920, 1080));
        RESOLUTIONS.put("1920x1200 (WUXGA)", new Dimension(1920, 1200));
        RESOLUTIONS.put("2560x1080 (WFHD)", new Dimension(2560, 1080));
        RESOLUTIONS.put("2560x1440 (QHD)", new Dimension(2560, 1440));
        RESOLUTIONS.put("3440x1440 (WQHD)", new Dimension(3440, 1440));
        RESOLUTIONS.put("3840x2160 (UHD)", new Dimension(3840, 2160));
    }

    /**
     * Invoked after the user clicks APPLY and settings have been persisted.
     */
    private final Runnable onApply;
    /**
     * Invoked when the user clicks CANCEL or presses ESC without saving.
     */
    private final Runnable onCancel;
    // ── Stateful UI controls ─────────────────────────────────────────────
    private JCheckBox fullscreenCheck;
    private JComboBox<String> resolutionCombo;

    // ────────────────────────────────────────────────────────────────────
    // Construction
    // ────────────────────────────────────────────────────────────────────

    /**
     * @param settingsManager the settings model to read from and write to
     * @param onApply         callback invoked after saving – caller re-applies display mode
     * @param onCancel        callback invoked on dismiss without saving
     */
    public SettingsOverlay(SettingsManager settingsManager, Runnable onApply, Runnable onCancel) {
        this.settingsManager = settingsManager;
        this.onApply = onApply;
        this.onCancel = onCancel;
        setupUI();
        loadCurrentSettings();
    }

    // ────────────────────────────────────────────────────────────────────
    // UI assembly
    // ────────────────────────────────────────────────────────────────────

    /**
     * Creates a cyan section-heading label (e.g. "DISPLAY MODE").
     */
    private static JLabel createSectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(SECTION_FONT);
        lbl.setForeground(ACCENT);
        return lbl;
    }

    /**
     * Creates a styled checkbox with a custom-painted cyan icon.
     *
     * @param text label shown next to the checkbox
     */
    private static JCheckBox createStyledCheckBox(String text) {
        JCheckBox cb = new JCheckBox(text);
        cb.setFont(BODY_FONT);
        cb.setForeground(ACCENT);
        cb.setBackground(PANEL_BG);
        cb.setFocusPainted(false);
        cb.setOpaque(true);
        cb.setIcon(new CheckBoxIcon(false));
        cb.setSelectedIcon(new CheckBoxIcon(true));
        return cb;
    }

    /**
     * Creates a fully themed combo box using {@link DarkComboBoxUI} for custom
     * painting and {@link DarkComboListRenderer} for the dropdown items.
     */
    private static JComboBox<String> createStyledComboBox(String[] items) {
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setFont(BODY_FONT);
        combo.setForeground(ACCENT);
        combo.setBackground(CONTROL_BG);
        combo.setMaximumRowCount(8);
        combo.setUI(new DarkComboBoxUI());
        combo.setRenderer(new DarkComboListRenderer());
        combo.setBorder(null);
        combo.setOpaque(false);
        return combo;
    }

    // ────────────────────────────────────────────────────────────────────
    // Factory helpers for themed controls
    // ────────────────────────────────────────────────────────────────────

    /**
     * Extracts the pixel width from a label such as {@code "1920x1080 (FHD)"}.
     * Returns 0 on parse failure so malformed entries sort last.
     */
    private static int parseWidth(String label) {
        try {
            return Integer.parseInt(label.split("x")[0].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Extracts the pixel height from a label such as {@code "1920x1080 (FHD)"}.
     * Returns 0 on parse failure so malformed entries sort last.
     */
    private static int parseHeight(String label) {
        try {
            String after = label.split("x")[1].trim();
            return Integer.parseInt(after.split("[\\s(]")[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    private void setupUI() {
        setLayout(new GridBagLayout());
        setOpaque(false);

        JPanel backdrop = createBackdrop();
        backdrop.add(createSettingsCard());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        add(backdrop, gbc);

        setupKeyBindings();
    }

    // ────────────────────────────────────────────────────────────────────
    // Resolution label parsing
    // ────────────────────────────────────────────────────────────────────

    /**
     * Creates the semi-transparent backdrop that dims the simulation canvas.
     * The fill starts below the FlatLaf title bar so the header stays fully visible.
     */
    private JPanel createBackdrop() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(OVERLAY_BG);
                int topY = 0;
                JRootPane root = SwingUtilities.getRootPane(this);
                if (root != null) {
                    topY = root.getContentPane().getY();
                }
                g2.fillRect(0, topY, getWidth(), getHeight() - topY);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        return panel;
    }

    /**
     * Builds the floating settings card with a rounded cyan border, matching the
     * visual style of {@link InspectorPanel} and {@link EnvironmentPanel}.
     */
    private JPanel createSettingsCard() {
        JPanel card = new JPanel(new GridBagLayout()) {
            private static final int R = 15;
            private static final BasicStroke S1 = new BasicStroke(1f);
            private static final BasicStroke S3 = new BasicStroke(3f);

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth() - 1;
                int h = getHeight() - 1;
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, w, h, R, R);
                g2.setColor(new Color(0, 255, 255, 80));
                g2.setStroke(S3);
                g2.drawRoundRect(0, 0, w, h, R, R);
                g2.setColor(new Color(0, 255, 255));
                g2.setStroke(S1);
                g2.drawRoundRect(0, 0, w, h, R, R);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(24, 36, 24, 36));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(0, 0, 20, 0);

        JLabel title = new JLabel("SYSTEM SETTINGS");
        title.setFont(TITLE_FONT);
        title.setForeground(ACCENT);
        card.add(title, c);

        c.gridy++;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 6, 0);
        card.add(createSectionLabel("DISPLAY MODE"), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 18, 0);
        fullscreenCheck = createStyledCheckBox("Fullscreen");
        card.add(fullscreenCheck, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 6, 0);
        card.add(createSectionLabel("RESOLUTION"), c);

        c.gridy++;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 24, 0);
        // Sort by width descending; use height descending as tie-breaker
        List<String> sortedKeys = new ArrayList<>(RESOLUTIONS.keySet());
        sortedKeys.sort(
                Comparator.comparingInt(SettingsOverlay::parseWidth).reversed()
                        .thenComparing(
                                Comparator.comparingInt(SettingsOverlay::parseHeight).reversed()));
        resolutionCombo = createStyledComboBox(sortedKeys.toArray(new String[0]));
        card.add(resolutionCombo, c);

        c.gridy++;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(0, 0, 0, 0);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttons.setOpaque(false);
        ModernButton apply = new ModernButton("APPLY");
        apply.setPreferredSize(new Dimension(120, 40));
        apply.addActionListener(e -> applySettings());
        ModernButton cancel = new ModernButton("CANCEL");
        cancel.setPreferredSize(new Dimension(120, 40));
        cancel.addActionListener(e -> closeOverlay());
        buttons.add(apply);
        buttons.add(cancel);
        card.add(buttons, c);

        return card;
    }

    // ────────────────────────────────────────────────────────────────────
    // Settings persistence
    // ────────────────────────────────────────────────────────────────────

    /**
     * Populates the controls from the current {@link SettingsManager} state.
     * Called once at construction time.
     */
    private void loadCurrentSettings() {
        fullscreenCheck.setSelected(settingsManager.isFullscreen());
        String key = findResolutionKey(settingsManager.getWindowWidth(),
                settingsManager.getWindowHeight());
        if (key != null) resolutionCombo.setSelectedItem(key);
    }

    /**
     * Finds the display label for a given pixel dimension.
     * Returns {@code null} if no matching preset exists.
     */
    private String findResolutionKey(int width, int height) {
        for (Map.Entry<String, Dimension> entry : RESOLUTIONS.entrySet()) {
            Dimension d = entry.getValue();
            if (d.width == width && d.height == height) return entry.getKey();
        }
        return null;
    }

    /**
     * Persists the current control values to {@link SettingsManager} and
     * invokes {@link #onApply} so the caller can re-apply the display mode.
     */
    private void applySettings() {
        settingsManager.setFullscreen(fullscreenCheck.isSelected());
        String sel = (String) resolutionCombo.getSelectedItem();
        if (sel != null) {
            Dimension dim = RESOLUTIONS.get(sel);
            if (dim != null) {
                settingsManager.setWindowWidth(dim.width);
                settingsManager.setWindowHeight(dim.height);
            }
        }
        settingsManager.saveSettings();
        if (onApply != null) onApply.run();
    }

    // ────────────────────────────────────────────────────────────────────
    // Key bindings
    // ────────────────────────────────────────────────────────────────────

    /**
     * Binds the ESC key to {@link #closeOverlay()} for keyboard dismissal.
     */
    private void setupKeyBindings() {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeSettings");
        getActionMap().put("closeSettings", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeOverlay();
            }
        });
    }

    /**
     * Dismisses the overlay without saving, invoking {@link #onCancel}.
     */
    private void closeOverlay() {
        if (onCancel != null) onCancel.run();
    }

    // ────────────────────────────────────────────────────────────────────
    // Inner classes – themed controls
    // ────────────────────────────────────────────────────────────────────

    /**
     * Custom checkbox icon: a rounded dark square with a cyan border,
     * filled with a cyan "×" cross when checked.
     */
    private record CheckBoxIcon(boolean checked) implements Icon {
        private static final int SIZE = 18;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(CONTROL_BG);
            g2.fillRoundRect(x, y, SIZE, SIZE, 4, 4);
            g2.setColor(ACCENT);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, SIZE - 1, SIZE - 1, 4, 4);

            if (checked) {
                int pad = 4;
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + pad, y + pad, x + SIZE - pad, y + SIZE - pad);
                g2.drawLine(x + SIZE - pad, y + pad, x + pad, y + SIZE - pad);
            }

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    /**
     * Replaces the default Swing combo box UI with a dark-themed variant:
     * rounded rectangle background, cyan border, custom arrow button, and
     * a dark dropdown popup with a themed scrollbar.
     */
    private static class DarkComboBoxUI extends BasicComboBoxUI {

        /**
         * Corner arc radius, matching the rounded style used across all overlays.
         */
        private static final int ARC = 8;

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            c.setOpaque(false);
            if (editor instanceof JComponent jc) jc.setOpaque(false);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth();
            int h = c.getHeight();
            g2.setColor(CONTROL_BG);
            g2.fillRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
            g2.setColor(BORDER_COLOR);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
            g2.dispose();
            super.paint(g, c);
        }

        @Override
        protected JButton createArrowButton() {
            JButton btn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Draw a cyan chevron; the rounded background is painted by the parent combo.
                    g2.setColor(ACCENT);
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    int aw = 5, ah = 3;
                    g2.drawLine(cx - aw, cy - ah, cx, cy + ah);
                    g2.drawLine(cx, cy + ah, cx + aw, cy - ah);
                    g2.dispose();
                }
            };
            btn.setOpaque(false);
            btn.setContentAreaFilled(false);
            btn.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_COLOR));
            btn.setPreferredSize(new Dimension(28, 28));
            return btn;
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox) {
                @Override
                protected JScrollPane createScroller() {
                    JScrollPane sp = super.createScroller();
                    sp.setBorder(BorderFactory.createLineBorder(ACCENT, 1));
                    sp.getViewport().setBackground(CONTROL_BG);
                    JScrollBar vb = sp.getVerticalScrollBar();
                    vb.setBackground(PANEL_BG);
                    vb.setPreferredSize(new Dimension(8, Integer.MAX_VALUE));
                    vb.setUI(new RoundedScrollBarUI());
                    return sp;
                }
            };
            popup.setBorder(BorderFactory.createLineBorder(ACCENT, 1));
            return popup;
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            // Background is already painted in paint(); nothing to do here.
        }

        @Override
        public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(comboBox.getFont());
            g2.setColor(ACCENT);
            String text = String.valueOf(comboBox.getSelectedItem());
            FontMetrics fm = g2.getFontMetrics();
            int textY = bounds.y + (bounds.height + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, bounds.x + 6, textY);
            g2.dispose();
        }
    }

    /**
     * Renders each resolution entry in the dropdown with the dark/neon theme:
     * cyan text on a dark background, highlighted row on hover/selection.
     */
    private static class DarkComboListRenderer extends JLabel implements ListCellRenderer<String> {

        DarkComboListRenderer() {
            setOpaque(true);
            setFont(BODY_FONT);
            setBorder(new EmptyBorder(6, 10, 6, 10));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            setText(value);
            setForeground(ACCENT);
            setBackground(isSelected ? CONTROL_HOVER : CONTROL_BG);
            return this;
        }
    }

    /**
     * Minimal scrollbar UI for the resolution dropdown: no arrow buttons,
     * rounded pill-shaped thumb that brightens on hover or drag.
     */
    private static class RoundedScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {

        private static final Color THUMB_IDLE = new Color(0, 255, 255,  70);
        private static final Color THUMB_ACTIVE = new Color(0, 255, 255, 180);

        /** Returns an invisible zero-size button to eliminate the arrow buttons. */
        private static JButton zeroButton() {
            JButton b = new JButton();
            Dimension zero = new Dimension(0, 0);
            b.setPreferredSize(zero);
            b.setMinimumSize(zero);
            b.setMaximumSize(zero);
            b.setVisible(false);
            return b;
        }

        @Override
        protected void configureScrollBarColors() {
            trackColor = PANEL_BG;
            thumbColor = THUMB_IDLE;
        }

        @Override
        protected JButton createDecreaseButton(int o) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int o) {
            return zeroButton();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(PANEL_BG);
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty()) return;
            Color thumbCol = (isDragging || isThumbRollover()) ? THUMB_ACTIVE : THUMB_IDLE;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int inset = 2;
            int x = thumbBounds.x + inset;
            int y = thumbBounds.y + 2;
            int w = thumbBounds.width  - inset * 2;
            int h = thumbBounds.height - 4;
            g2.setColor(thumbCol);
            g2.fillRoundRect(x, y, w, h, w, w);
            g2.dispose();
        }
    }
}
