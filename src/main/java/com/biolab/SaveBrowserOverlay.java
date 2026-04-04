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
    private static final String VIEW_LIST = "list";
    private static final String VIEW_CREATE = "create";

    private final DefaultListModel<SaveGameMetadata> model = new DefaultListModel<>();
    private final JList<SaveGameMetadata> list = new JList<>(model);
    private final SaveGameListCellRenderer renderer = new SaveGameListCellRenderer();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardContent = new JPanel(cardLayout);

    private final JTextField mapName = new JTextField("New World");
    private final JSpinner width = new JSpinner(new SpinnerNumberModel(10_000, 500, 50_000, 500));
    private final JSpinner height = new JSpinner(new SpinnerNumberModel(10_000, 500, 50_000, 500));
    private final JSpinner initialPop = new JSpinner(new SpinnerNumberModel(1_500, 0, 100_000, 50));
    private final JSpinner maxPop = new JSpinner(new SpinnerNumberModel(20_000, 100, 500_000, 100));
    private final JSpinner temp = new JSpinner(new SpinnerNumberModel(0.3, 0.0, 1.0, 0.01));
    private final JSpinner tox = new JSpinner(new SpinnerNumberModel(0.3, 0.0, 1.0, 0.01));
    private final JSpinner food = new JSpinner(new SpinnerNumberModel(0.75, 0.0, 1.0, 0.01));
    private final JLabel title = new JLabel("SELECT GAME", SwingConstants.CENTER);
    private final ModernButton createButton = new ModernButton("CREATE GAME");

    public SaveBrowserOverlay(Listener listener) {
        setOpaque(false);
        setLayout(new GridBagLayout());

        NeonCardPanel card = new NeonCardPanel(OverlayTheme.CARD_ARC, new Insets(16, 16, 16, 16));
        card.setLayout(new BorderLayout(0, 12));
        card.setPreferredSize(new Dimension(850, 520));

        title.setForeground(OverlayTheme.ACCENT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));

        createButton.setFont(new Font("Segoe UI", Font.BOLD, 18));
        createButton.setPreferredSize(new Dimension(220, 44));
        createButton.addActionListener(e -> showCreateForm());

        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.setOpaque(false);
        north.add(title, BorderLayout.NORTH);
        north.add(createButton, BorderLayout.SOUTH);
        card.add(north, BorderLayout.NORTH);

        cardContent.setOpaque(false);
        cardContent.add(createListView(listener), VIEW_LIST);
        cardContent.add(createWorldFormView(listener), VIEW_CREATE);
        card.add(cardContent, BorderLayout.CENTER);

        add(card);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        int topY = 0;
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) {
            topY = root.getContentPane().getY();
        }
        g2.setColor(OVERLAY_BG);
        g2.fillRect(0, topY, getWidth(), getHeight() - topY);
        g2.dispose();
    }

    private JPanel createListView(Listener listener) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(new Color(10, 12, 16, 150));
        list.setForeground(OverlayTheme.ACCENT);
        list.setFixedCellHeight(64);
        list.setCellRenderer(renderer);
        list.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                Rectangle rowBounds = (idx >= 0) ? list.getCellBounds(idx, idx) : null;
                renderer.setHoveredIndex(rowBounds != null && rowBounds.contains(e.getPoint()) ? idx : -1);
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
        panel.add(scrollPane, BorderLayout.CENTER);

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
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createWorldFormView(Listener listener) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 8));
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(4, 6, 4, 6));

        styleField(mapName);
        styleSpinner(width);
        styleSpinner(height);
        styleSpinner(initialPop);
        styleSpinner(maxPop);
        styleSpinner(temp);
        styleSpinner(tox);
        styleSpinner(food);

        addField(form, "Map Name", mapName);
        addField(form, "Map Width", width);
        addField(form, "Map Height", height);
        addField(form, "Initial Microbes", initialPop);
        addField(form, "Max Microbes", maxPop);
        addField(form, "Temperature", temp);
        addField(form, "Toxicity", tox);
        addField(form, "Food Spawn Rate", food);

        panel.add(form, BorderLayout.CENTER);

        ModernButton play = new ModernButton("PLAY", ModernButton.ButtonIcon.PLAY);
        ModernButton back = new ModernButton("BACK");
        play.addActionListener(e -> {
            try {
                listener.onCreateRequested(new WorldConfig(
                        mapName.getText().trim(),
                        (int) width.getValue(),
                        (int) height.getValue(),
                        (int) initialPop.getValue(),
                        (int) maxPop.getValue(),
                        ((Number) temp.getValue()).doubleValue(),
                        ((Number) tox.getValue()).doubleValue(),
                        ((Number) food.getValue()).doubleValue()
                ));
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid World Config", JOptionPane.ERROR_MESSAGE);
            }
        });
        back.addActionListener(e -> showListView());

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(play, BorderLayout.WEST);
        actions.add(back, BorderLayout.EAST);
        panel.add(actions, BorderLayout.SOUTH);

        return panel;
    }

    private void addField(JPanel panel, String label, JComponent field) {
        JLabel lbl = new JLabel(label);
        lbl.setForeground(OverlayTheme.ACCENT);
        panel.add(lbl);
        panel.add(field);
    }

    private void styleField(JTextField field) {
        field.setForeground(OverlayTheme.ACCENT);
        field.setBackground(OverlayTheme.CONTROL_BG);
        field.setCaretColor(OverlayTheme.ACCENT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(OverlayTheme.ACCENT_GLOW, 1),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
    }

    private void styleSpinner(JSpinner spinner) {
        spinner.setBorder(BorderFactory.createLineBorder(OverlayTheme.ACCENT_GLOW, 1));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField tf = defaultEditor.getTextField();
            styleField(tf);
            tf.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        }
    }

    public void showCreateForm() {
        title.setText("CREATE GAME");
        createButton.setVisible(false);
        cardLayout.show(cardContent, VIEW_CREATE);
    }

    public void showListView() {
        title.setText("SELECT GAME");
        createButton.setVisible(true);
        cardLayout.show(cardContent, VIEW_LIST);
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
        void onCreateRequested(WorldConfig config);

        void onPlayRequested(SaveGameMetadata metadata);

        void onDeleteRequested(SaveGameMetadata metadata);

        void onBackRequested();
    }
}


