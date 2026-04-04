package com.biolab;

import javax.swing.*;
import java.awt.*;

/**
 * Main menu overlay rendered above the simulation preview.
 */
public class MainMenuOverlay extends JPanel {
    private static final Color GLASS_BG = new Color(0, 0, 0, 95);
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 62);
    private static final Font BUTTON_FONT = new Font("Segoe UI", Font.BOLD, 28);

    private final TitleLabel title;
    private final ModernButton startButton;
    private final ModernButton resumeButton;

    public MainMenuOverlay(Runnable onStart, Runnable onResume) {
        setOpaque(false);
        setLayout(null);

        title = new TitleLabel("BIO-LAB EVOLUTION");
        title.setFont(TITLE_FONT);
        add(title);

        resumeButton = new ModernButton("RESUME");
        resumeButton.setFont(BUTTON_FONT);
        resumeButton.addActionListener(e -> onResume.run());
        add(resumeButton);

        startButton = new ModernButton("START");
        startButton.setFont(BUTTON_FONT);
        startButton.addActionListener(e -> onStart.run());
        add(startButton);
    }

    public void setResumeEnabled(boolean enabled) {
        resumeButton.setEnabled(enabled);
        resumeButton.setDimmed(!enabled);
        resumeButton.setCursor(enabled
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        revalidate();
        repaint();
    }

    @Override
    public void doLayout() {
        int topY = 0;
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) {
            topY = root.getContentPane().getY();
        }

        int titleW = Math.min(980, getWidth() - 80);
        title.setBounds((getWidth() - titleW) / 2, topY + 58, titleW, 88);

        int startW = 240;
        int startH = 66;
        int startY = topY + (int) ((getHeight() - topY) * 0.80) - startH;
        startButton.setBounds((getWidth() - startW) / 2, startY, startW, startH);

        int resumeW = startW;
        int resumeH = startH;
        int resumeY = startY - resumeH - 14;
        resumeButton.setBounds((getWidth() - resumeW) / 2, resumeY, resumeW, resumeH);
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

