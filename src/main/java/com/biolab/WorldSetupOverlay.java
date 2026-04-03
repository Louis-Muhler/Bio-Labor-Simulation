package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Overlay form used to create a new game/world configuration.
 */
public class WorldSetupOverlay extends JPanel {
    private static final Color OVERLAY_BG = new Color(0, 0, 0, 170);

    public WorldSetupOverlay(CreateWorldListener onCreate, Runnable onCancel) {
        setOpaque(false);
        setLayout(new GridBagLayout());

        JPanel backdrop = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(OVERLAY_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        backdrop.setOpaque(false);

        JPanel card = new JPanel(new GridLayout(0, 2, 10, 8));
        card.setBorder(new EmptyBorder(20, 20, 20, 20));
        card.setBackground(OverlayTheme.PANEL_BG_ALPHA);

        JTextField mapName = new JTextField("New World");
        JSpinner width = new JSpinner(new SpinnerNumberModel(10_000, 500, 50_000, 500));
        JSpinner height = new JSpinner(new SpinnerNumberModel(10_000, 500, 50_000, 500));
        JSpinner initialPop = new JSpinner(new SpinnerNumberModel(1_500, 0, 100_000, 50));
        JSpinner maxPop = new JSpinner(new SpinnerNumberModel(20_000, 100, 500_000, 100));
        JSpinner temp = new JSpinner(new SpinnerNumberModel(0.3, 0.0, 1.0, 0.01));
        JSpinner tox = new JSpinner(new SpinnerNumberModel(0.3, 0.0, 1.0, 0.01));
        JSpinner food = new JSpinner(new SpinnerNumberModel(0.75, 0.0, 1.0, 0.01));

        addField(card, "Map Name", mapName);
        addField(card, "Map Width", width);
        addField(card, "Map Height", height);
        addField(card, "Initial Microbes", initialPop);
        addField(card, "Max Microbes", maxPop);
        addField(card, "Temperature", temp);
        addField(card, "Toxicity", tox);
        addField(card, "Food Spawn Rate", food);

        ModernButton create = new ModernButton("CREATE", ModernButton.ButtonIcon.PLAY);
        ModernButton cancel = new ModernButton("CANCEL", ModernButton.ButtonIcon.CLOSE);
        create.addActionListener(e -> {
            try {
                WorldConfig cfg = new WorldConfig(
                        mapName.getText().trim(),
                        (int) width.getValue(),
                        (int) height.getValue(),
                        (int) initialPop.getValue(),
                        (int) maxPop.getValue(),
                        ((Number) temp.getValue()).doubleValue(),
                        ((Number) tox.getValue()).doubleValue(),
                        ((Number) food.getValue()).doubleValue()
                );
                onCreate.onCreate(cfg);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid World Config", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancel.addActionListener(e -> onCancel.run());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(create);

        JPanel wrapper = new JPanel(new BorderLayout(0, 12));
        wrapper.setOpaque(false);
        JLabel title = new JLabel("CREATE GAME", SwingConstants.CENTER);
        title.setForeground(OverlayTheme.ACCENT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        wrapper.add(title, BorderLayout.NORTH);
        wrapper.add(card, BorderLayout.CENTER);
        wrapper.add(buttons, BorderLayout.SOUTH);

        backdrop.add(wrapper);
        add(backdrop);
    }

    private static void addField(JPanel panel, String label, JComponent field) {
        JLabel lbl = new JLabel(label);
        lbl.setForeground(OverlayTheme.ACCENT);
        panel.add(lbl);
        panel.add(field);
    }

    public interface CreateWorldListener {
        void onCreate(WorldConfig config);
    }
}

