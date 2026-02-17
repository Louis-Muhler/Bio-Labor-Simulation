package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;

/**
 * Universeller moderner Button - funktioniert überall!
 *
 * Verwendung:
 * - new ModernButton("Text")                           // Nur Text
 * - new ModernButton("Text", ButtonIcon.GEAR)          // Text + Icon
 * - new ModernButton("", ButtonIcon.CLOSE)             // Nur Icon
 * - new ModernButton("Text", customIconDrawer)         // Custom Icon Funktion
 */
public class ModernButton extends JButton {
    private static final Color BACKGROUND_COLOR = new Color(12, 12, 14);
    private static final Color BACKGROUND_HOVER_COLOR = new Color(25, 28, 32);
    private static final Color BORDER_COLOR = new Color(0, 255, 255, 80);
    private static final Color BORDER_HOVER_COLOR = new Color(0, 255, 255, 200);
    private static final Color TEXT_COLOR = new Color(0, 255, 255);

    private boolean isHovered = false;
    private String displayText = "";
    private BiConsumer<Graphics2D, Point> iconDrawer = null;

    /**
     * Vordefinierte Icons
     */
    public enum ButtonIcon {
        NONE,
        SPEED_UP,
        GEAR,
        CLOSE,
        PLAY,
        PAUSE,
        STOP
    }

    // Konstruktor 1: Nur Text
    public ModernButton(String text) {
        this(text, ButtonIcon.NONE);
    }

    // Konstruktor 2: Text + vordefiniertes Icon
    public ModernButton(String text, ButtonIcon icon) {
        this(text, getIconDrawer(icon));
    }

    // Konstruktor 3: Text + custom Icon-Funktion
    public ModernButton(String text, BiConsumer<Graphics2D, Point> customIconDrawer) {
        super(text);
        this.displayText = text;
        this.iconDrawer = customIconDrawer;

        setFont(new Font("Segoe UI", Font.BOLD, 16));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Add mouse listener for hover effect
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }
        });
    }

    public void setDisplayText(String text) {
        this.displayText = text;
        repaint();
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        this.displayText = text;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cornerRadius = 6;

        // Background
        g2d.setColor(isHovered ? BACKGROUND_HOVER_COLOR : BACKGROUND_COLOR);
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);

        // Border with glow
        if (isHovered) {
            g2d.setColor(new Color(0, 255, 255, 30));
            g2d.setStroke(new BasicStroke(4));
            g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, cornerRadius, cornerRadius);

            g2d.setColor(new Color(0, 255, 255, 60));
            g2d.setStroke(new BasicStroke(2.5f));
            g2d.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, cornerRadius, cornerRadius);

            g2d.setColor(BORDER_HOVER_COLOR);
        } else {
            g2d.setColor(BORDER_COLOR);
        }
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, cornerRadius, cornerRadius);

        // Draw icon and text
        g2d.setColor(TEXT_COLOR);

        if (iconDrawer != null) {
            if (!displayText.isEmpty()) {
                // Icon + Text: Icon links, Text rechts
                Point iconPos = new Point(25, getHeight() / 2);
                iconDrawer.accept(g2d, iconPos);

                g2d.setFont(getFont());
                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(displayText);
                g2d.drawString(displayText, getWidth() - textWidth - 20, getHeight() / 2 + 6);
            } else {
                // Nur Icon: zentriert
                Point iconPos = new Point(getWidth() / 2, getHeight() / 2);
                iconDrawer.accept(g2d, iconPos);
            }
        } else {
            // Kein Icon, nur zentrierter Text
            if (!displayText.isEmpty()) {
                g2d.setFont(getFont());
                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(displayText);
                g2d.drawString(displayText, (getWidth() - textWidth) / 2, getHeight() / 2 + 6);
            }
        }

        g2d.dispose();
    }

    /**
     * Gibt die Icon-Zeichenfunktion für vordefinierte Icons zurück
     */
    private static BiConsumer<Graphics2D, Point> getIconDrawer(ButtonIcon icon) {
        switch (icon) {
            case SPEED_UP:
                return ModernButton::drawSpeedUpIcon;
            case GEAR:
                return ModernButton::drawGearIcon;
            case CLOSE:
                return ModernButton::drawCloseIcon;
            case PLAY:
                return ModernButton::drawPlayIcon;
            case PAUSE:
                return ModernButton::drawPauseIcon;
            case STOP:
                return ModernButton::drawStopIcon;
            default:
                return null;
        }
    }

    // ========== Icon-Zeichenfunktionen ==========

    private static void drawSpeedUpIcon(Graphics2D g2d, Point pos) {
        g2d.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int size = 8;
        int x = pos.x;
        int y = pos.y;

        // Erster Pfeil
        int[] xPoints1 = {x - 8, x, x - 8};
        int[] yPoints1 = {y - size, y, y + size};
        g2d.fillPolygon(xPoints1, yPoints1, 3);

        // Zweiter Pfeil
        int[] xPoints2 = {x + 2, x + 10, x + 2};
        int[] yPoints2 = {y - size, y, y + size};
        g2d.fillPolygon(xPoints2, yPoints2, 3);
    }

    private static void drawGearIcon(Graphics2D g2d, Point pos) {
        int size = 10;
        int x = pos.x;
        int y = pos.y;
        g2d.setStroke(new BasicStroke(1.5f));

        // Zahnrad mit 12 Zähnen
        int[] xPoints = new int[12];
        int[] yPoints = new int[12];
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30);
            int radius = (i % 2 == 0) ? size : (size - 2);
            xPoints[i] = x + (int)(Math.cos(angle) * radius);
            yPoints[i] = y + (int)(Math.sin(angle) * radius);
        }
        g2d.drawPolygon(xPoints, yPoints, 12);
        g2d.fillOval(x - 3, y - 3, 6, 6);
    }

    private static void drawCloseIcon(Graphics2D g2d, Point pos) {
        int size = 7;
        int x = pos.x;
        int y = pos.y;
        g2d.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(x - size, y - size, x + size, y + size);
        g2d.drawLine(x + size, y - size, x - size, y + size);
    }

    private static void drawPlayIcon(Graphics2D g2d, Point pos) {
        int size = 8;
        int x = pos.x;
        int y = pos.y;
        int[] xPoints = {x - size/2, x + size, x - size/2};
        int[] yPoints = {y - size, y, y + size};
        g2d.fillPolygon(xPoints, yPoints, 3);
    }

    private static void drawPauseIcon(Graphics2D g2d, Point pos) {
        int x = pos.x;
        int y = pos.y;
        g2d.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(x - 4, y - 6, x - 4, y + 6);
        g2d.drawLine(x + 4, y - 6, x + 4, y + 6);
    }

    private static void drawStopIcon(Graphics2D g2d, Point pos) {
        int size = 6;
        int x = pos.x;
        int y = pos.y;
        g2d.fillRect(x - size, y - size, size * 2, size * 2);
    }
}

