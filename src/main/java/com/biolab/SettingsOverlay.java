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
 * Settings overlay panel (v2.2) that appears on top of the simulation
 * with a semi-transparent dark overlay. Pauses the simulation while
 * settings are being adjusted.
 *
 * <p>All controls are custom-painted to match the dark / neon-cyan theme
 * used throughout the application ({@link ModernButton}).</p>
 */
public class SettingsOverlay extends JPanel {
    private final SettingsManager settingsManager;
    private final Runnable onClose;

    // ── Shared colour / font constants ──────────────────────────────────
    private static final Color OVERLAY_BG = new Color(0, 0, 0, 220);
    private static final Color PANEL_BG = new Color(18, 18, 18);
    private static final Color ACCENT = new Color(0, 255, 255);
    private static final Color CONTROL_BG = new Color(12, 12, 14);
    private static final Color CONTROL_HOVER = new Color(25, 28, 32);
    private static final Color BORDER_COLOR = new Color(0, 255, 255, 80);

    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font SECTION_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    // Resolution options – LinkedHashMap preserves insertion order
    private static final Map<String, Dimension> RESOLUTIONS = new LinkedHashMap<>();
    private JCheckBox fullscreenCheck;

    static {
        RESOLUTIONS.put("1280x720 (HD)", new Dimension(1280, 720));
        RESOLUTIONS.put("1366x768 (WXGA)", new Dimension(1366, 768));
        RESOLUTIONS.put("1600x900 (HD+)", new Dimension(1600, 900));
        RESOLUTIONS.put("1920x1080 (FHD)", new Dimension(1920, 1080));
        RESOLUTIONS.put("2560x1440 (QHD)", new Dimension(2560, 1440));
        RESOLUTIONS.put("3840x2160 (UHD)", new Dimension(3840, 2160));
        RESOLUTIONS.put("2560x1080 (WFHD)", new Dimension(2560, 1080));
        RESOLUTIONS.put("3440x1440 (WQHD)", new Dimension(3440, 1440));
        RESOLUTIONS.put("1440x900 (WXGA+)", new Dimension(1440, 900));
        RESOLUTIONS.put("1680x1050 (WSXGA+)", new Dimension(1680, 1050));
        RESOLUTIONS.put("1920x1200 (WUXGA)", new Dimension(1920, 1200));
        RESOLUTIONS.put("1024x768 (XGA)", new Dimension(1024, 768));
        RESOLUTIONS.put("1280x960 (SXGA-)", new Dimension(1280, 960));
        RESOLUTIONS.put("1600x1200 (UXGA)", new Dimension(1600, 1200));
    }

    // ── UI components ───────────────────────────────────────────────────
    private JComboBox<String> resolutionCombo;

    // ────────────────────────────────────────────────────────────────────
    // Construction
    // ────────────────────────────────────────────────────────────────────

    public SettingsOverlay(SettingsManager settingsManager, Runnable onClose) {
        this.settingsManager = settingsManager;
        this.onClose = onClose;
        setupUI();
        loadCurrentSettings();
    }

    // ────────────────────────────────────────────────────────────────────
    // UI assembly
    // ────────────────────────────────────────────────────────────────────

    /**
     * Parses the pixel width from a resolution label like "1920x1080 (Full HD)".
     */
    private static int parseWidth(String label) {
        try {
            return Integer.parseInt(label.split("x")[0].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static JLabel createSectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(SECTION_FONT);
        lbl.setForeground(ACCENT);
        return lbl;
    }

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

    private static JComboBox<String> createStyledComboBox(String[] items) {
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setFont(BODY_FONT);
        combo.setForeground(ACCENT);
        combo.setBackground(CONTROL_BG);
        combo.setMaximumRowCount(8);

        // Install custom UI (arrow button + popup styling)
        combo.setUI(new DarkComboBoxUI());

        // Custom cell renderer for popup list items
        combo.setRenderer(new DarkComboListRenderer());

        // Border is painted by DarkComboBoxUI as a rounded rect – remove the default one
        combo.setBorder(null);
        combo.setOpaque(false);

        return combo;
    }

    // ────────────────────────────────────────────────────────────────────
    // Section label (plain text, no Unicode symbols)
    // ────────────────────────────────────────────────────────────────────

    private void setupUI() {
        setLayout(new GridBagLayout());
        setOpaque(false);

        // Semi-transparent backdrop
        JPanel backdrop = createBackdrop();

        // Inner settings card
        JPanel card = createSettingsCard();
        backdrop.add(card);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        add(backdrop, gbc);

        setupKeyBindings();
    }

    // ────────────────────────────────────────────────────────────────────
    // Custom CheckBox
    // ────────────────────────────────────────────────────────────────────

    /**
     * Dark semi-transparent fullscreen backdrop.
     */
    private JPanel createBackdrop() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(OVERLAY_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        return panel;
    }

    /**
     * Builds the bordered settings card with GridBagLayout for left-aligned rows.
     */
    private JPanel createSettingsCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(PANEL_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT, 2),
                new EmptyBorder(24, 36, 24, 36)
        ));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(0, 0, 20, 0);

        // ── Title ───────────────────────────────────────────────────────
        JLabel title = new JLabel("SYSTEM SETTINGS");
        title.setFont(TITLE_FONT);
        title.setForeground(ACCENT);
        card.add(title, c);

        // ── DISPLAY MODE label ──────────────────────────────────────────
        c.gridy++;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 6, 0);
        card.add(createSectionLabel("DISPLAY MODE"), c);

        // ── Fullscreen checkbox ─────────────────────────────────────────
        c.gridy++;
        c.insets = new Insets(0, 0, 18, 0);
        fullscreenCheck = createStyledCheckBox("Fullscreen");
        card.add(fullscreenCheck, c);

        // ── RESOLUTION label ────────────────────────────────────────────
        c.gridy++;
        c.insets = new Insets(0, 0, 6, 0);
        card.add(createSectionLabel("RESOLUTION"), c);

        // ── Resolution combo ────────────────────────────────────────────
        c.gridy++;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 24, 0);
        // Sort resolution labels numerically by width (number before the 'x')
        List<String> sortedKeys = new ArrayList<>(RESOLUTIONS.keySet());
        sortedKeys.sort(Comparator.comparingInt(SettingsOverlay::parseWidth).reversed());
        resolutionCombo = createStyledComboBox(sortedKeys.toArray(new String[0]));
        card.add(resolutionCombo, c);

        // ── Button row ──────────────────────────────────────────────────
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
    // Custom ComboBox
    // ────────────────────────────────────────────────────────────────────

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

    // ── Custom ComboBox UI ──────────────────────────────────────────────

    private void loadCurrentSettings() {
        fullscreenCheck.setSelected(settingsManager.isFullscreen());

        int w = settingsManager.getWindowWidth();
        int h = settingsManager.getWindowHeight();
        String key = findResolutionKey(w, h);
        if (key != null) {
            resolutionCombo.setSelectedItem(key);
        }
    }

    // ── Custom List Cell Renderer ───────────────────────────────────────

    private String findResolutionKey(int width, int height) {
        for (Map.Entry<String, Dimension> entry : RESOLUTIONS.entrySet()) {
            Dimension d = entry.getValue();
            if (d.width == width && d.height == height) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ── Custom Scrollbar UI ─────────────────────────────────────────────

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
        closeOverlay();
    }

    // ────────────────────────────────────────────────────────────────────
    // Key bindings
    // ────────────────────────────────────────────────────────────────────

    /**
         * Custom painted checkbox icon – hollow cyan box or filled cyan square.
         */
        private record CheckBoxIcon(boolean checked) implements Icon {
            private static final int SIZE = 18;

        @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                // Dark fill
                g2.setColor(CONTROL_BG);
                g2.fillRoundRect(x, y, SIZE, SIZE, 4, 4);

                // Cyan border
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(x, y, SIZE - 1, SIZE - 1, 4, 4);

                if (checked) {
                    // Neon cyan "X" drawn as two thick diagonal lines
                    int pad = 4;
                    g2.setColor(ACCENT);
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

    // ────────────────────────────────────────────────────────────────────
    // Settings persistence
    // ────────────────────────────────────────────────────────────────────

    /**
     * Overrides the default BasicComboBoxUI to provide a dark arrow button
     * and a dark-themed popup.
     */
    private static class DarkComboBoxUI extends BasicComboBoxUI {

        private static final int ARC = 8; // corner radius matching ModernButton

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            // Make the combo itself non-opaque so our rounded paint shows
            c.setOpaque(false);
            // Make the editor component transparent (text-field inside editable combos)
            if (editor instanceof JComponent jc) {
                jc.setOpaque(false);
            }
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = c.getWidth();
            int h = c.getHeight();

            // Rounded dark fill
            g2.setColor(CONTROL_BG);
            g2.fillRoundRect(0, 0, w - 1, h - 1, ARC, ARC);

            // Rounded cyan border
            g2.setColor(BORDER_COLOR);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);

            g2.dispose();

            // Let BasicComboBoxUI paint the current value text and arrow button on top
            super.paint(g, c);
        }

        @Override
        protected JButton createArrowButton() {
            JButton btn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

                    // Transparent – the parent combo paints the rounded background.
                    // Just draw the cyan chevron arrow.
                    g2.setColor(ACCENT);
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2;
                    int aw = 5;
                    int ah = 3;
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

                    // Style the scroll bar
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
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds,
                                                boolean hasFocus) {
            // No-op: the rounded background is already painted in paint()
        }

        @Override
        public void paintCurrentValue(Graphics g, Rectangle bounds,
                                      boolean hasFocus) {
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
     * Renders each dropdown item with the dark/neon theme.
     */
    private static class DarkComboListRenderer extends JLabel
            implements ListCellRenderer<String> {

        DarkComboListRenderer() {
            setOpaque(true);
            setFont(BODY_FONT);
            setBorder(new EmptyBorder(6, 10, 6, 10));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list,
                                                      String value, int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            setText(value);
            setForeground(ACCENT);

            if (isSelected) {
                setBackground(CONTROL_HOVER);
            } else {
                setBackground(CONTROL_BG);
            }
            return this;
        }
    }

    /**
     * Minimal, modern scrollbar: no arrow buttons, rounded thumb,
     * plain dark track. Thumb brightens while dragging / hovering.
     */
    private static class RoundedScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {

        // Idle thumb: dim cyan; active thumb: bright cyan
        private static final Color THUMB_IDLE = new Color(0, 255, 255, 70);
        private static final Color THUMB_ACTIVE = new Color(0, 255, 255, 180);

        // ── Hide arrow buttons completely ──────────────────────────────
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
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        // ── Track: just a flat dark fill, no border ────────────────────
        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(PANEL_BG);
            g.fillRect(trackBounds.x, trackBounds.y,
                    trackBounds.width, trackBounds.height);
        }

        // ── Thumb: rounded pill, brightens on hover / drag ────────────
        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty()) return;

            boolean active = isDragging || isThumbRollover();
            Color thumbCol = active ? THUMB_ACTIVE : THUMB_IDLE;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // Slight horizontal inset so the pill floats in the track
            int inset = 2;
            int x = thumbBounds.x + inset;
            int y = thumbBounds.y + 2;
            int w = thumbBounds.width - inset * 2;
            int h = thumbBounds.height - 4;
            int arc = w; // fully rounded ends (pill shape)

            g2.setColor(thumbCol);
            g2.fillRoundRect(x, y, w, h, arc, arc);
            g2.dispose();
        }
    }

    private void closeOverlay() {
        if (onClose != null) {
            onClose.run();
        }
    }
}
