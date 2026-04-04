package com.biolab;

import javax.swing.*;
import java.awt.*;

/**
 * Main menu overlay rendered above the simulation preview.
 */
public class MainMenuOverlay extends JPanel {
    private static final Color GLASS_BG = new Color(14, 18, 24, 130);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 62);

    private final JLabel title;
    private final ModernButton start;
    private final ModernButton settings;

    public MainMenuOverlay(Runnable onStart, Runnable onSettings) {
        setOpaque(false);
        setLayout(null);

        title = new JLabel("BIO-LAB EVOLUTION", SwingConstants.CENTER);
        title.setFont(TITLE_FONT);
        title.setForeground(OverlayTheme.ACCENT);
        add(title);

        start = new ModernButton("START");
        start.setFont(new Font("Segoe UI", Font.BOLD, 28));
        start.addActionListener(e -> onStart.run());
        add(start);

        settings = new ModernButton("", ModernButton.ButtonIcon.GEAR);
        settings.addActionListener(e -> onSettings.run());
        add(settings);
    }

    @Override
    public void doLayout() {
        int topInset = 16;
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) {
            topInset += root.getContentPane().getY();
        }

        settings.setBounds(15, topInset, 45, 45);

        int titleW = Math.min(getWidth() - 60, 900);
        title.setBounds((getWidth() - titleW) / 2, topInset + 50, titleW, 84);

        FontMetrics fm = start.getFontMetrics(start.getFont());
        int textWidth = fm.stringWidth(start.getText());
        int btnW = Math.max(180, textWidth + 56);
        int btnH = 62;
        start.setBounds((getWidth() - btnW) / 2, topInset + 190, btnW, btnH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(GLASS_BG);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(new Color(0, 255, 255, 28));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }
}

