package com.biolab;

import javax.swing.*;
import java.awt.*;

/**
 * Main menu overlay rendered above the simulation preview.
 */
public class MainMenuOverlay extends JPanel {
    private static final Color GLASS_BG = new Color(0, 0, 0, 95);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 62);

    public MainMenuOverlay(Runnable onStart) {
        setOpaque(false);
        setLayout(new BorderLayout());

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);

        TitleLabel title = new TitleLabel("BIO-LAB EVOLUTION");
        title.setFont(TITLE_FONT);

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        titlePanel.setOpaque(false);
        titlePanel.add(title);
        titlePanel.setBorder(BorderFactory.createEmptyBorder(54, 0, 0, 0));

        ModernButton start = new ModernButton("START");
        start.setFont(new Font("Segoe UI", Font.BOLD, 30));
        start.setPreferredSize(new Dimension(220, 66));
        start.addActionListener(e -> onStart.run());

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(start);

        content.add(titlePanel, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
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
        g2.setColor(GLASS_BG);
        g2.fillRect(0, topY, getWidth(), getHeight() - topY);
        g2.dispose();
    }

    private static final class TitleLabel extends JLabel {
        TitleLabel(String text) {
            super(text);
            setForeground(new Color(10, 10, 10));
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics fm = g2.getFontMetrics(getFont());
            String text = getText();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(OverlayTheme.ACCENT);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    g2.drawString(text, x + dx, y + dy);
                }
            }
            g2.setColor(new Color(8, 8, 8));
            g2.drawString(text, x, y);
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + 20, fm.getHeight() + 14);
        }
    }
}

