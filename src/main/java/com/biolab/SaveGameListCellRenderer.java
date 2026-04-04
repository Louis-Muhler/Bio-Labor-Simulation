package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Minecraft-inspired save list renderer with left title and right metadata block.
 */
public class SaveGameListCellRenderer implements ListCellRenderer<SaveGameMetadata> {
    private int hoveredIndex = -1;

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

        JPanel row = new JPanel(new BorderLayout(12, 4));
        row.setOpaque(true);
        row.setBorder(new EmptyBorder(10, 12, 10, 12));
        row.setBackground(highlighted ? OverlayTheme.CONTROL_HOVER : new Color(10, 12, 16, 205));

        JLabel name = new JLabel(value.listName());
        name.setForeground(OverlayTheme.ACCENT);
        name.setFont(new Font("Segoe UI", Font.BOLD, 17));

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JLabel metaTop = new JLabel(value.listMetaPrimary());
        metaTop.setForeground(OverlayTheme.ACCENT);
        metaTop.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        metaTop.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel metaBottom = new JLabel(value.listMetaSecondary());
        metaBottom.setForeground(new Color(170, 220, 220));
        metaBottom.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        metaBottom.setAlignmentX(Component.RIGHT_ALIGNMENT);

        right.add(metaTop);
        right.add(metaBottom);

        row.add(name, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);

        return row;
    }
}

