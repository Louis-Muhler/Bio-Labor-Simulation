package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Main menu overlay rendered above the simulation preview.
 */
public class MainMenuOverlay extends JPanel {
    private static final Color GLASS_BG = new Color(0, 0, 0, 95);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 52);

    public MainMenuOverlay(Runnable onStart, Runnable onSettings) {
        setOpaque(false);
        setLayout(new BorderLayout());

        JPanel glass = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(GLASS_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        glass.setOpaque(false);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(40, 0, 40, 0));

        JLabel title = new JLabel("BIO-LAB EVOLUTION");
        title.setFont(TITLE_FONT);
        title.setForeground(OverlayTheme.ACCENT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        ModernButton start = new ModernButton("START GAME", ModernButton.ButtonIcon.PLAY);
        start.setPreferredSize(new Dimension(360, 60));
        start.setMaximumSize(new Dimension(360, 60));
        start.setAlignmentX(Component.CENTER_ALIGNMENT);
        start.addActionListener(e -> onStart.run());

        center.add(Box.createVerticalGlue());
        center.add(title);
        center.add(Box.createVerticalStrut(40));
        center.add(start);
        center.add(Box.createVerticalGlue());

        glass.add(center);
        add(glass, BorderLayout.CENTER);

        ModernButton settings = new ModernButton("", ModernButton.ButtonIcon.GEAR);
        settings.setPreferredSize(new Dimension(45, 45));
        settings.addActionListener(e -> onSettings.run());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
        top.setOpaque(false);
        top.add(settings);
        add(top, BorderLayout.NORTH);
    }
}

