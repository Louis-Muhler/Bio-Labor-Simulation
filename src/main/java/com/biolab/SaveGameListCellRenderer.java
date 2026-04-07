package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Minecraft-inspired save list renderer with left title and right metadata block.
 */
public class SaveGameListCellRenderer implements ListCellRenderer<SaveGameMetadata> {
    private int hoveredIndex = -1;
    private final RowPanel row = new RowPanel();
    private final EmptyBorder basePadding = new EmptyBorder(8, 12, 8, 12);
    private final JLabel name = new JLabel();
    private final JPanel right = new MetaPanel();
    private final JLabel metaTop = new JLabel();
    private final JLabel metaBottom = new JLabel();

    public SaveGameListCellRenderer() {
        row.setOpaque(false);
        row.setBorder(basePadding);
        row.setLayout(new BorderLayout(12, 4));

        name.setFont(new Font("Segoe UI", Font.BOLD, 17));
        name.setForeground(OverlayTheme.ACCENT);
        name.setVerticalAlignment(SwingConstants.CENTER);

        right.setOpaque(false);
        right.setLayout(new GridLayout(2, 1, 0, 2));
        right.setBorder(new EmptyBorder(6, 10, 6, 10));
        metaTop.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        metaTop.setForeground(OverlayTheme.ACCENT);
        metaTop.setHorizontalAlignment(SwingConstants.RIGHT);

        metaBottom.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        metaBottom.setForeground(OverlayTheme.ACCENT);
        metaBottom.setHorizontalAlignment(SwingConstants.RIGHT);

        right.add(metaTop);
        right.add(metaBottom);
        row.add(name, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
    }

    public void setHoveredIndex(int hoveredIndex) {
        this.hoveredIndex = hoveredIndex;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends SaveGameMetadata> list,
                                                  SaveGameMetadata value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
        boolean highlighted = isSelected || index == hoveredIndex;

        row.setHighlighted(highlighted);
        row.setBorder(highlighted
                ? BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 0, 0),
                new EmptyBorder(7, 11, 7, 11))
                : basePadding);
        name.setText(value.listName());
        metaTop.setText(value.listMetaPrimary());
        metaBottom.setText(value.listMetaSecondary());

        return row;
    }

    private static final class RowPanel extends JPanel {
        private static final Color ROW_IDLE = new Color(16, 18, 24, 180);
        private static final Color ROW_HIGHLIGHT = new Color(20, 28, 36, 215);
        private boolean highlighted;

        private void setHighlighted(boolean highlighted) {
            this.highlighted = highlighted;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            g2.setColor(highlighted ? ROW_HIGHLIGHT : ROW_IDLE);
            g2.fillRoundRect(0, 0, w, h, 14, 14);
            g2.setColor(highlighted ? OverlayTheme.ACCENT_GLOW : new Color(0, 255, 255, 70));
            g2.setStroke(new BasicStroke(highlighted ? 1.4f : 1f));
            g2.drawRoundRect(0, 0, w, h, 14, 14);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class MetaPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            g2.setColor(new Color(0, 0, 0, 72));
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setColor(new Color(0, 255, 255, 80));
            g2.drawRoundRect(0, 0, w, h, 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}

