package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.util.function.BooleanSupplier;

/**
 * Custom header panel that visually replaces the OS title bar.
 *
 * <p>Works with FlatLaf's {@code fullWindowContent} mode. The panel is marked
 * with the client property {@code FlatLaf.titleBarCaption = true} so that
 * FlatLaf treats it as a native drag area. Buttons inside the panel are
 * excluded from the caption zone via {@code FlatLaf.titleBarCaption = false}
 * so they still receive normal Swing click events.</p>
 *
 * <p>This gives us native OS behaviour: drag-to-move, Aero Snap, double-click
 * maximise, drop shadow, and resize handles – all without any JNA hooks.</p>
 */
public class CustomHeaderPanel extends JPanel {

    private static final Color HEADER_GLOW_COLOR = new Color(0, 255, 255, 60);
    private static final Color HEADER_LINE_COLOR = new Color(0, 255, 255);
    private static final BasicStroke HEADER_STROKE_3 = new BasicStroke(3);
    private static final BasicStroke HEADER_STROKE_1_5 = new BasicStroke(1.5f);

    /**
     * Creates the custom header panel.
     *
     * @param headerWidth  preferred width
     * @param headerHeight preferred height
     * @param onSettings   callback for the settings (gear) button
     * @param onMinimize   callback for the minimise button
     * @param onMaximize   callback for the maximise/restore button
     * @param onClose      callback for the close button
     * @param isMaximized  supplier that returns {@code true} while the window is maximised
     */
    public CustomHeaderPanel(int headerWidth, int headerHeight,
                             Runnable onSettings, Runnable onMinimize,
                             Runnable onMaximize, Runnable onClose,
                             BooleanSupplier isMaximized) {
        setPreferredSize(new Dimension(headerWidth, headerHeight));
        setBackground(new Color(20, 20, 28));
        setOpaque(false);
        setLayout(new BorderLayout());

        // Mark this entire panel as a FlatLaf title bar caption area.
        // FlatLaf will treat it as a native drag zone (Aero Snap, double-click maximize etc.)
        putClientProperty("FlatLaf.titleBarCaption", true);

        // --- Left: settings (gear) button ---
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 10, 0, 0);
        ModernButton settingsButton = new ModernButton("", ModernButton.ButtonIcon.GEAR);
        settingsButton.setPreferredSize(new Dimension(45, 45));
        settingsButton.addActionListener(e -> onSettings.run());
        markAsNonCaption(settingsButton);
        leftPanel.add(settingsButton, gbc);
        add(leftPanel, BorderLayout.WEST);

        // --- Centre: title label (part of the drag zone) ---
        JLabel titleLabel = new JLabel("BIO-LAB EVOLUTION SIMULATOR");
        titleLabel.setForeground(new Color(0, 255, 255));
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(titleLabel, BorderLayout.CENTER);

        // --- Right: minimise / maximise / close ---
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);

        GridBagConstraints gbcR = new GridBagConstraints();
        gbcR.anchor = GridBagConstraints.EAST;
        gbcR.insets = new Insets(0, 4, 0, 0);

        ModernButton minimizeButton = new ModernButton("", ModernButton.ButtonIcon.MINIMIZE);
        minimizeButton.setPreferredSize(new Dimension(45, 45));
        minimizeButton.addActionListener(e -> onMinimize.run());
        markAsNonCaption(minimizeButton);
        rightPanel.add(minimizeButton, gbcR);

        ModernButton maximizeButton = new ModernButton("",
                (g2d, pos) -> drawDynamicMaximizeIcon(g2d, pos, isMaximized.getAsBoolean()));
        maximizeButton.setPreferredSize(new Dimension(45, 45));
        maximizeButton.addActionListener(e -> {
            onMaximize.run();
            maximizeButton.repaint();
        });
        markAsNonCaption(maximizeButton);
        rightPanel.add(maximizeButton, gbcR);

        GridBagConstraints gbcClose = new GridBagConstraints();
        gbcClose.anchor = GridBagConstraints.EAST;
        gbcClose.insets = new Insets(0, 4, 0, 10);
        ModernButton closeButton = new ModernButton("", ModernButton.ButtonIcon.CLOSE);
        closeButton.setPreferredSize(new Dimension(45, 45));
        closeButton.addActionListener(e -> onClose.run());
        markAsNonCaption(closeButton);
        rightPanel.add(closeButton, gbcClose);
        add(rightPanel, BorderLayout.EAST);
    }

    /**
     * Marks a component so FlatLaf does NOT treat it as part of the title bar
     * caption drag zone. Without this, clicks on buttons would start a window drag.
     */
    private static void markAsNonCaption(JComponent comp) {
        comp.putClientProperty("FlatLaf.titleBarCaption", false);
    }

    /**
     * Draws the maximise/restore icon.
     * Single square = windowed; two overlapping squares = restore (maximised).
     */
    private static void drawDynamicMaximizeIcon(Graphics2D g2d, Point pos, boolean isMaximized) {
        int x = pos.x;
        int y = pos.y;
        g2d.setStroke(new BasicStroke(2f));
        if (isMaximized) {
            int s = 6;
            g2d.drawRect(x - s + 3, y - s - 3, s * 2 - 1, s * 2 - 1);
            g2d.setColor(new Color(20, 20, 28));
            g2d.fillRect(x - s - 1, y - s + 2, s * 2 - 1, s * 2 - 1);
            g2d.setColor(new Color(0, 255, 255));
            g2d.drawRect(x - s - 1, y - s + 2, s * 2 - 1, s * 2 - 1);
        } else {
            int s = 7;
            g2d.drawRect(x - s, y - s, s * 2, s * 2);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(getBackground());
            g2d.fillRect(0, 0, getWidth(), getHeight());
            // Neon-cyan separator line with glow effect at the bottom edge
            g2d.setColor(HEADER_GLOW_COLOR);
            g2d.setStroke(HEADER_STROKE_3);
            g2d.drawLine(0, getHeight() - 2, getWidth(), getHeight() - 2);
            g2d.setColor(HEADER_LINE_COLOR);
            g2d.setStroke(HEADER_STROKE_1_5);
            g2d.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        } finally {
            g2d.dispose();
        }
    }
}
