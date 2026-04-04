package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Overlay that lists all save games and exposes create/play/delete actions.
 */
public class SaveBrowserOverlay extends JPanel {
    private static final Color OVERLAY_BG = new Color(0, 0, 0, 95);
    private final DefaultListModel<SaveGameMetadata> model = new DefaultListModel<>();
    private final JList<SaveGameMetadata> list = new JList<>(model);
    private final SaveGameListCellRenderer renderer = new SaveGameListCellRenderer();

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

        NeonCardPanel card = new NeonCardPanel(16, new Insets(16, 16, 16, 16));
        card.setLayout(new BorderLayout(0, 12));
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
        list.setFixedCellHeight(64);
        list.setCellRenderer(renderer);
        list.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                renderer.setHoveredIndex(idx);
                list.repaint();
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                renderer.setHoveredIndex(-1);
                list.repaint();
            }
        });

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createLineBorder(OverlayTheme.ACCENT_GLOW, 1));
        card.add(scrollPane, BorderLayout.CENTER);

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


