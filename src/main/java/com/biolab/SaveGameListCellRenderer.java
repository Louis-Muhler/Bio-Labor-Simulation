package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Minecraft-inspired save list renderer with left title and right metadata block.
 */
public class SaveGameListCellRenderer implements ListCellRenderer<SaveGameMetadata> {
    private int hoveredIndex = -1;
    private final JPanel row = new JPanel(new BorderLayout(12, 4));
    private final EmptyBorder basePadding = new EmptyBorder(8, 12, 8, 12);
    private final JLabel name = new JLabel();
    private final JPanel right = new JPanel(new GridLayout(2, 1, 0, 2));
    private final JLabel metaTop = new JLabel();
    private final JLabel metaBottom = new JLabel();

    public SaveGameListCellRenderer() {
        row.setOpaque(true);
        row.setBorder(basePadding);

        name.setFont(new Font("Segoe UI", Font.BOLD, 17));
        name.setForeground(OverlayTheme.ACCENT);
        name.setVerticalAlignment(SwingConstants.CENTER);

        right.setOpaque(false);
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

        row.setBackground(new Color(0, 0, 0, 0));
        row.setBorder(highlighted
                ? BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(OverlayTheme.ACCENT_GLOW, 1),
                new EmptyBorder(7, 11, 7, 11))
                : basePadding);
        name.setText(value.listName());
        metaTop.setText(value.listMetaPrimary());
        metaBottom.setText(value.listMetaSecondary());

        return row;
    }
}

