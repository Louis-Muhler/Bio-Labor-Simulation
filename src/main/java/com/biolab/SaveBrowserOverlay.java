package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Overlay that lists all save games and exposes create/play/delete actions.
 */
public class SaveBrowserOverlay extends JPanel {
    private static final Color OVERLAY_BG = new Color(0, 0, 0, 95);
    private final DefaultListModel<SaveGameMetadata> model = new DefaultListModel<>();
    private final JList<SaveGameMetadata> list = new JList<>(model);

    public SaveBrowserOverlay(Listener listener) {
        setOpaque(false);
        setLayout(new GridBagLayout());

        JPanel backdrop = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                int topY = 0;
                JRootPane root = SwingUtilities.getRootPane(this);
                if (root != null) {
                    topY = root.getContentPane().getY();
                }
                g2.setColor(OVERLAY_BG);
                g2.fillRect(0, topY, getWidth(), getHeight() - topY);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        backdrop.setOpaque(false);

        JPanel card = new JPanel(new BorderLayout(0, 12)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth() - 1;
                int h = getHeight() - 1;
                g2.setColor(OverlayTheme.PANEL_BG_ALPHA);
                g2.fillRoundRect(0, 0, w, h, 16, 16);
                g2.setColor(OverlayTheme.ACCENT_GLOW);
                g2.setStroke(new BasicStroke(3f));
                g2.drawRoundRect(0, 0, w, h, 16, 16);
                g2.setColor(OverlayTheme.ACCENT);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w, h, 16, 16);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(850, 520));

        JLabel title = new JLabel("SELECT GAME", SwingConstants.CENTER);
        title.setForeground(OverlayTheme.ACCENT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        ModernButton create = new ModernButton("CREATE GAME");
        create.setFont(new Font("Segoe UI", Font.BOLD, 18));
        create.setPreferredSize(new Dimension(220, 44));
        create.addActionListener(e -> listener.onCreateRequested());

        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.setOpaque(false);
        north.add(title, BorderLayout.NORTH);
        north.add(create, BorderLayout.SOUTH);
        card.add(north, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(new Color(10, 12, 16, 200));
        list.setForeground(OverlayTheme.ACCENT);
        list.setCellRenderer((jList, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.toDisplayLine());
            label.setOpaque(true);
            label.setBorder(new EmptyBorder(10, 12, 10, 12));
            label.setForeground(OverlayTheme.ACCENT);
            label.setBackground(isSelected ? OverlayTheme.CONTROL_HOVER : new Color(10, 12, 16, 200));
            return label;
        });
        card.add(new JScrollPane(list), BorderLayout.CENTER);

        ModernButton play = new ModernButton("PLAY");
        ModernButton delete = new ModernButton("DELETE");
        ModernButton back = new ModernButton("BACK");

        play.addActionListener(e -> {
            SaveGameMetadata selected = list.getSelectedValue();
            if (selected != null) listener.onPlayRequested(selected);
        });
        delete.addActionListener(e -> {
            SaveGameMetadata selected = list.getSelectedValue();
            if (selected != null) listener.onDeleteRequested(selected);
        });
        back.addActionListener(e -> listener.onBackRequested());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(play);
        actions.add(delete);
        actions.add(back);

        card.add(actions, BorderLayout.SOUTH);

        backdrop.add(card);
        add(backdrop);
    }

    public void setSaves(List<SaveGameMetadata> saves) {
        model.clear();
        for (SaveGameMetadata save : saves) {
            model.addElement(save);
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    public interface Listener {
        void onCreateRequested();

        void onPlayRequested(SaveGameMetadata metadata);

        void onDeleteRequested(SaveGameMetadata metadata);

        void onBackRequested();
    }
}


