package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;

/**
 * Universal modern button with sci-fi styling.
 * Usage:
 * - new ModernButton("Text")                           // Text only
 * - new ModernButton("Text", ButtonIcon.GEAR)          // Text + predefined icon
 * - new ModernButton("", ButtonIcon.CLOSE)             // Icon only
 * - new ModernButton("Text", customIconDrawer)         // Custom icon function
 */
public class ModernButton extends JButton {
    private static final Color BACKGROUND_COLOR = new Color(12, 12, 14);
    private static final Color BACKGROUND_HOVER_COLOR = new Color(25, 28, 32);
    private static final Color BORDER_COLOR = new Color(0, 255, 255, 80);
    private static final Color BORDER_HOVER_COLOR = new Color(0, 255, 255, 200);
    private static final Color TEXT_COLOR = new Color(0, 255, 255);

    // Pre-allocated rendering constants for hover glow
    private static final Color HOVER_GLOW_OUTER = new Color(0, 255, 255, 30);
    private static final Color HOVER_GLOW_INNER = new Color(0, 255, 255, 60);
    private static final BasicStroke STROKE_4 = new BasicStroke(4);
    private static final BasicStroke STROKE_2_5 = new BasicStroke(2.5f);
    private static final BasicStroke STROKE_1_5 = new BasicStroke(1.5f);

    // Pre-allocated icon strokes
    private static final BasicStroke ICON_STROKE_2_5_ROUND =
            new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke ICON_STROKE_1_5_ROUND =
            new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke ICON_STROKE_3_ROUND =
            new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private boolean isHovered = false;
    private boolean isDimmed = false;
    private final BiConsumer<Graphics2D, Point> iconDrawer;
    private String displayText;

    // Constructor 1: Text only
    public ModernButton(String text) {
        this(text, ButtonIcon.NONE);
    }

    // Constructor 2: Text + predefined icon
    public ModernButton(String text, ButtonIcon icon) {
        this(text, getIconDrawer(icon));
    }

    // Constructor 3: Text + custom icon function
    public ModernButton(String text, BiConsumer<Graphics2D, Point> customIconDrawer) {
        super(text);
        this.displayText = text;
        this.iconDrawer = customIconDrawer;

        setFont(new Font("Segoe UI", Font.BOLD, 16));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

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

    /**
     * Returns the icon drawing function for predefined icon types.
     */
    private static BiConsumer<Graphics2D, Point> getIconDrawer(ButtonIcon icon) {
        return switch (icon) {
            case SPEED_UP -> ModernButton::drawSpeedUpIcon;
            case GEAR -> ModernButton::drawGearIcon;
            case CLOSE -> ModernButton::drawCloseIcon;
            case PLAY -> ModernButton::drawPlayIcon;
            case PAUSE -> ModernButton::drawPauseIcon;
            case STOP -> ModernButton::drawStopIcon;
            case ENVIRONMENT -> ModernButton::drawEnvironmentIcon;
            case MINIMIZE -> ModernButton::drawMinimizeIcon;
            case MAXIMIZE -> ModernButton::drawMaximizeIcon;
            case NONE -> null;
        };
    }

    public void setDisplayText(String text) {
        this.displayText = text;
        repaint();
    }

    public void setDimmed(boolean dimmed) {
        this.isDimmed = dimmed;
        repaint();
    }


    @Override
    public void setText(String text) {
        super.setText(text);
        this.displayText = text;
        repaint();
    }

    private static void drawSpeedUpIcon(Graphics2D g2d, Point pos) {
        g2d.setStroke(ICON_STROKE_2_5_ROUND);
        int size = 8;
        int x = pos.x;
        int y = pos.y;

        // First arrow
        int[] xPoints1 = {x - 8, x, x - 8};
        int[] yPoints1 = {y - size, y, y + size};
        g2d.fillPolygon(xPoints1, yPoints1, 3);

        // Second arrow
        int[] xPoints2 = {x + 2, x + 10, x + 2};
        int[] yPoints2 = {y - size, y, y + size};
        g2d.fillPolygon(xPoints2, yPoints2, 3);
    }

    private static void drawGearIcon(Graphics2D g2d, Point pos) {
        int size = 10;
        int x = pos.x;
        int y = pos.y;
        g2d.setStroke(STROKE_1_5);

        // Gear with 12 teeth
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

    // ========== Icon Drawing Functions ==========

    private static void drawCloseIcon(Graphics2D g2d, Point pos) {
        int size = 7;
        int x = pos.x;
        int y = pos.y;
        g2d.setStroke(ICON_STROKE_2_5_ROUND);
        g2d.drawLine(x - size, y - size, x + size, y + size);
        g2d.drawLine(x + size, y - size, x - size, y + size);
    }

    private static void drawPauseIcon(Graphics2D g2d, Point pos) {
        int x = pos.x;
        int y = pos.y;
        g2d.setStroke(ICON_STROKE_3_ROUND);
        g2d.drawLine(x - 4, y - 6, x - 4, y + 6);
        g2d.drawLine(x + 4, y - 6, x + 4, y + 6);
    }

    private static void drawEnvironmentIcon(Graphics2D g2d, Point pos) {
        int x = pos.x;
        int y = pos.y;
        g2d.setStroke(ICON_STROKE_1_5_ROUND);

        // Draw a stylized tree/leaf
        // Trunk
        g2d.drawLine(x, y + 8, x, y - 2);

        // Leaf/crown - three layered triangles (like a pine tree)
        int[] xTop = {x, x - 5, x + 5};
        int[] yTop = {y - 10, y - 3, y - 3};
        g2d.fillPolygon(xTop, yTop, 3);

        int[] xMid = {x, x - 7, x + 7};
        int[] yMid = {y - 6, y + 1, y + 1};
        g2d.fillPolygon(xMid, yMid, 3);

        int[] xBot = {x, x - 9, x + 9};
        int[] yBot = {y - 2, y + 5, y + 5};
        g2d.fillPolygon(xBot, yBot, 3);
    }

    private static void drawPlayIcon(Graphics2D g2d, Point pos) {
        int size = 8;
        int x = pos.x;
        int y = pos.y;
        int[] xPoints = {x - size / 2, x + size, x - size / 2};
        int[] yPoints = {y - size, y, y + size};
        g2d.fillPolygon(xPoints, yPoints, 3);
    }

    private static void drawMinimizeIcon(Graphics2D g2d, Point pos) {
        int x = pos.x;
        int y = pos.y;
        g2d.setStroke(ICON_STROKE_2_5_ROUND);
        // Simple horizontal line (minimize)
        g2d.drawLine(x - 7, y, x + 7, y);
    }

    private static void drawStopIcon(Graphics2D g2d, Point pos) {
        int size = 6;
        int x = pos.x;
        int y = pos.y;
        g2d.fillRect(x - size, y - size, size * 2, size * 2);
    }

    private static void drawMaximizeIcon(Graphics2D g2d, Point pos) {
        int x = pos.x;
        int y = pos.y;
        int size = 7;
        g2d.setStroke(ICON_STROKE_2_5_ROUND);
        // Rectangle outline (maximize)
        g2d.drawRect(x - size, y - size, size * 2, size * 2);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Apply dimmed effect if active
        if (isDimmed) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        }

        int cornerRadius = 6;

        // Background
        g2d.setColor(isHovered ? BACKGROUND_HOVER_COLOR : BACKGROUND_COLOR);
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);

        // Border with glow
        if (isHovered) {
            g2d.setColor(HOVER_GLOW_OUTER);
            g2d.setStroke(STROKE_4);
            g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, cornerRadius, cornerRadius);

            g2d.setColor(HOVER_GLOW_INNER);
            g2d.setStroke(STROKE_2_5);
            g2d.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, cornerRadius, cornerRadius);

            g2d.setColor(BORDER_HOVER_COLOR);
        } else {
            g2d.setColor(BORDER_COLOR);
        }
        g2d.setStroke(STROKE_1_5);
        g2d.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, cornerRadius, cornerRadius);

        // Draw icon and text
        g2d.setColor(TEXT_COLOR);

        if (iconDrawer != null) {
            if (!displayText.isEmpty()) {
                // Icon + Text: icon left, text right
                Point iconPos = new Point(25, getHeight() / 2);
                iconDrawer.accept(g2d, iconPos);

                g2d.setFont(getFont());
                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(displayText);
                g2d.drawString(displayText, getWidth() - textWidth - 20, getHeight() / 2 + 6);
            } else {
                // Icon only: centered
                Point iconPos = new Point(getWidth() / 2, getHeight() / 2);
                iconDrawer.accept(g2d, iconPos);
            }
        } else {
            // No icon, centered text only
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
     * Predefined icon types.
     */
    public enum ButtonIcon {
        NONE,
        SPEED_UP,
        GEAR,
        CLOSE,
        PLAY,
        PAUSE,
        STOP,
        ENVIRONMENT,
        MINIMIZE,
        MAXIMIZE
    }
}
