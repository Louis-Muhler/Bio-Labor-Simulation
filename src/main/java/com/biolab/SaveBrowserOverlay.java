package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Overlay that lists all save games and exposes create/play/delete actions.
 */
public class SaveBrowserOverlay extends JPanel {
    private final DefaultListModel<SaveGameMetadata> model = new DefaultListModel<>();
    private final JList<SaveGameMetadata> list = new JList<>(model);

    public SaveBrowserOverlay(Listener listener) {
        setOpaque(false);
        setLayout(new GridBagLayout());

        JPanel backdrop = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        backdrop.setOpaque(false);

        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setBackground(OverlayTheme.PANEL_BG_ALPHA);
        card.setPreferredSize(new Dimension(850, 520));

        JLabel title = new JLabel("SELECT GAME", SwingConstants.CENTER);
        title.setForeground(OverlayTheme.ACCENT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        ModernButton create = new ModernButton("CREATE GAME", ModernButton.ButtonIcon.PLAY);
        create.addActionListener(e -> listener.onCreateRequested());

        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.setOpaque(false);
        north.add(title, BorderLayout.NORTH);
        north.add(create, BorderLayout.SOUTH);
        card.add(north, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(OverlayTheme.CONTROL_BG);
        list.setForeground(OverlayTheme.ACCENT);
        list.setCellRenderer((jList, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.toDisplayLine());
            label.setOpaque(true);
            label.setBorder(new EmptyBorder(8, 8, 8, 8));
            label.setForeground(OverlayTheme.ACCENT);
            label.setBackground(isSelected ? OverlayTheme.CONTROL_HOVER : OverlayTheme.CONTROL_BG);
            return label;
        });
        card.add(new JScrollPane(list), BorderLayout.CENTER);

        ModernButton play = new ModernButton("PLAY", ModernButton.ButtonIcon.PLAY);
        ModernButton delete = new ModernButton("DELETE", ModernButton.ButtonIcon.CLOSE);
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


