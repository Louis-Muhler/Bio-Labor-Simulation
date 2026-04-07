package com.biolab;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Shared overlay control styling helpers to avoid duplicated inline UI code.
 */
final class OverlayControlFactory {
    private static final Font FIELD_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font COMBO_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font CHECKBOX_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final int SCROLLBAR_THICKNESS = 8;

    private OverlayControlFactory() {
    }

    static void styleTextField(JTextField field) {
        field.setFont(FIELD_FONT);
        field.setForeground(OverlayTheme.ACCENT);
        field.setBackground(OverlayTheme.CONTROL_BG);
        field.setCaretColor(OverlayTheme.ACCENT);
        field.setOpaque(true);
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundedOutlineBorder(10),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
    }

    static JCheckBox createStyledCheckBox(String text, boolean selected) {
        JCheckBox checkBox = new JCheckBox(text);
        checkBox.setSelected(selected);
        checkBox.setOpaque(false);
        checkBox.setForeground(OverlayTheme.ACCENT);
        checkBox.setFont(CHECKBOX_FONT);
        checkBox.setFocusPainted(false);
        checkBox.setIcon(new CheckBoxIcon(false));
        checkBox.setSelectedIcon(new CheckBoxIcon(true));
        return checkBox;
    }

    static JCheckBox createSettingsCheckBox(String text, boolean selected) {
        JCheckBox checkBox = new JCheckBox(text);
        checkBox.setSelected(selected);
        checkBox.setForeground(OverlayTheme.ACCENT);
        checkBox.setBackground(OverlayTheme.PANEL_BG);
        checkBox.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        checkBox.setFocusPainted(false);
        checkBox.setOpaque(true);
        checkBox.setIcon(new SettingsCheckBoxIcon(false));
        checkBox.setSelectedIcon(new SettingsCheckBoxIcon(true));
        return checkBox;
    }

    static JScrollPane createStyledScrollPane(JComponent content) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        vertical.setBackground(OverlayTheme.PANEL_BG);
        Dimension verticalSize = new Dimension(SCROLLBAR_THICKNESS, Integer.MAX_VALUE);
        vertical.setPreferredSize(verticalSize);
        vertical.setMinimumSize(verticalSize);
        vertical.setMaximumSize(verticalSize);
        vertical.setBorder(BorderFactory.createEmptyBorder());
        vertical.setUI(new RoundedScrollBarUI());

        JScrollBar horizontal = scrollPane.getHorizontalScrollBar();
        horizontal.setBackground(OverlayTheme.PANEL_BG);
        Dimension horizontalSize = new Dimension(Integer.MAX_VALUE, SCROLLBAR_THICKNESS);
        horizontal.setPreferredSize(horizontalSize);
        horizontal.setMinimumSize(horizontalSize);
        horizontal.setMaximumSize(horizontalSize);
        horizontal.setBorder(BorderFactory.createEmptyBorder());
        horizontal.setUI(new RoundedScrollBarUI());

        return scrollPane;
    }

    static <T> JComboBox<T> createStyledComboBox(T[] items) {
        JComboBox<T> combo = new JComboBox<>(items);
        combo.setFont(COMBO_FONT);
        combo.setForeground(OverlayTheme.ACCENT);
        combo.setBackground(OverlayTheme.CONTROL_BG);
        combo.setMaximumRowCount(8);
        combo.setUI(new RoundedComboBoxUI());
        combo.setRenderer(new DarkComboListRenderer<>());
        combo.setBorder(null);
        combo.setOpaque(false);
        return combo;
    }

    static void styleSpinner(JSpinner spinner) {
        spinner.setUI(new NeonSpinnerUI());
        spinner.setOpaque(false);
        spinner.setBorder(BorderFactory.createEmptyBorder());
        spinner.setBackground(OverlayTheme.CONTROL_BG);
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField field = defaultEditor.getTextField();
            styleTextField(field);
            field.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            field.setOpaque(false);
            field.setHorizontalAlignment(SwingConstants.LEFT);
            defaultEditor.setBorder(BorderFactory.createEmptyBorder());
            defaultEditor.setOpaque(false);
        }
    }

    static JPanel wrapInInnerFrame(JComponent centerContent) {
        JPanel shell = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth() - 1;
                int h = getHeight() - 1;
                g2.setColor(new Color(12, 12, 14, 190));
                g2.fillRoundRect(0, 0, w, h, 10, 10);
                g2.setColor(OverlayTheme.ACCENT_GLOW);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, w, h, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        shell.setOpaque(false);
        shell.setBorder(new EmptyBorder(4, 4, 4, 4));
        shell.add(centerContent, BorderLayout.CENTER);
        return shell;
    }

    private static final class RoundedOutlineBorder extends AbstractBorder {
        private final int arc;

        private RoundedOutlineBorder(int arc) {
            this.arc = arc;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(OverlayTheme.ACCENT_GLOW);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(1, 1, 1, 1);
            return insets;
        }
    }

    private record CheckBoxIcon(boolean checked) implements Icon {
        private static final int SIZE = 16;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(OverlayTheme.CONTROL_BG);
            g2.fillRoundRect(x, y, SIZE, SIZE, 4, 4);
            g2.setColor(OverlayTheme.ACCENT);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawRoundRect(x, y, SIZE - 1, SIZE - 1, 4, 4);
            if (checked) {
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 4, y + 8, x + 7, y + 11);
                g2.drawLine(x + 7, y + 11, x + 12, y + 5);
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

    private record SettingsCheckBoxIcon(boolean checked) implements Icon {
        private static final int SIZE = 18;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(OverlayTheme.CONTROL_BG);
            g2.fillRoundRect(x, y, SIZE, SIZE, 4, 4);
            g2.setColor(OverlayTheme.ACCENT);
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

    private static final class RoundedComboBoxUI extends BasicComboBoxUI {
        private static final int ARC = 8;

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            c.setOpaque(false);
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
            g2.setColor(OverlayTheme.CONTROL_BG);
            g2.fillRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
            g2.setColor(OverlayTheme.ACCENT);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
            g2.dispose();
            super.paint(g, c);
        }

        @Override
        protected JButton createArrowButton() {
            JButton button = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(OverlayTheme.ACCENT);
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2;
                    int aw = 5;
                    int ah = 3;
                    g2.drawLine(cx - aw, cy - ah, cx, cy + ah);
                    g2.drawLine(cx, cy + ah, cx + aw, cy - ah);
                    g2.dispose();
                }
            };
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, OverlayTheme.ACCENT));
            button.setPreferredSize(new Dimension(28, 28));
            return button;
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox) {
                @Override
                protected JScrollPane createScroller() {
                    JScrollPane scrollPane = super.createScroller();
                    scrollPane.setBorder(BorderFactory.createLineBorder(OverlayTheme.ACCENT, 1));
                    scrollPane.getViewport().setBackground(OverlayTheme.CONTROL_BG);
                    JScrollBar vertical = scrollPane.getVerticalScrollBar();
                    vertical.setBackground(OverlayTheme.PANEL_BG);
                    vertical.setPreferredSize(new Dimension(8, Integer.MAX_VALUE));
                    vertical.setUI(new RoundedScrollBarUI());
                    return scrollPane;
                }
            };
            popup.setBorder(BorderFactory.createLineBorder(OverlayTheme.ACCENT, 1));
            return popup;
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            // Background is painted in paint().
        }

        @Override
        public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(comboBox.getFont());
            g2.setColor(OverlayTheme.ACCENT);
            String text = String.valueOf(comboBox.getSelectedItem());
            ListCellRenderer<Object> renderer = comboBox.getRenderer();
            if (renderer != null) {
                Component rendered = renderer.getListCellRendererComponent(new JList<>(), comboBox.getSelectedItem(), -1, false, false);
                if (rendered instanceof JLabel label) {
                    text = label.getText();
                }
            }
            FontMetrics fm = g2.getFontMetrics();
            int textY = bounds.y + (bounds.height + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, bounds.x + 8, textY);
            g2.dispose();
        }
    }

    private static final class DarkComboListRenderer<T> extends JLabel implements ListCellRenderer<T> {
        private DarkComboListRenderer() {
            setOpaque(true);
            setFont(COMBO_FONT);
            setBorder(new EmptyBorder(6, 10, 6, 10));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends T> list, T value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            setText(String.valueOf(value));
            setForeground(OverlayTheme.ACCENT);
            setBackground(isSelected ? OverlayTheme.CONTROL_HOVER : OverlayTheme.CONTROL_BG);
            return this;
        }
    }

    private static final class NeonSpinnerUI extends BasicSpinnerUI {
        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            c.setOpaque(false);
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

        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth();
            int h = c.getHeight();
            g2.setColor(OverlayTheme.CONTROL_BG);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.setColor(OverlayTheme.ACCENT_GLOW);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.dispose();
            super.paint(g, c);
        }

        private JButton createArrowButton(boolean up) {
            JButton btn = new JButton() {
                private boolean hovered;

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(hovered ? new Color(0, 255, 255, 230) : OverlayTheme.ACCENT);
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

    private static final class RoundedScrollBarUI extends BasicScrollBarUI {
        private static final Color THUMB_IDLE = new Color(0, 255, 255, 70);
        private static final Color THUMB_ACTIVE = new Color(0, 255, 255, 180);

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
            trackColor = OverlayTheme.PANEL_BG;
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

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(OverlayTheme.PANEL_BG);
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty()) return;
            Color thumbCol = (isDragging || isThumbRollover()) ? THUMB_ACTIVE : THUMB_IDLE;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = thumbBounds.x + 2;
            int y = thumbBounds.y + 2;
            int w = thumbBounds.width - 4;
            int h = thumbBounds.height - 4;
            if (w <= 0 || h <= 0) {
                g2.dispose();
                return;
            }
            int arc = Math.max(4, Math.min(w, h));
            g2.setColor(thumbCol);
            g2.fillRoundRect(x, y, w, h, arc, arc);
            g2.dispose();
        }
    }
}

