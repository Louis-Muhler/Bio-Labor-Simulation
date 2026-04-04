package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Reusable rounded neon card container used by overlays.
 */
public class NeonCardPanel extends JPanel {
    private final int arc;

    public NeonCardPanel(int arc, Insets padding) {
        this.arc = arc;
        setOpaque(false);
        setBorder(new EmptyBorder(padding));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth() - 1;
        int h = getHeight() - 1;

        g2.setColor(OverlayTheme.PANEL_BG_SOFT_ALPHA);
        g2.fillRoundRect(0, 0, w, h, arc, arc);

        g2.setColor(OverlayTheme.ACCENT_GLOW);
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(0, 0, w, h, arc, arc);

        g2.setColor(OverlayTheme.ACCENT);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(0, 0, w, h, arc, arc);
        g2.dispose();

        super.paintComponent(g);
    }
}

