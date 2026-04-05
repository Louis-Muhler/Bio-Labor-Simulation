package com.biolab;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * Shared overlay control styling helpers to avoid duplicated inline UI code.
 */
final class OverlayControlFactory {
    private static final Font FIELD_FONT = new Font("Segoe UI", Font.BOLD, 16);
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

