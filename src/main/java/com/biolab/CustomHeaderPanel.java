package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.util.function.BooleanSupplier;

/**
 * Custom header panel that visually replaces the OS title bar.
 *
 * <p>Placed in {@code BorderLayout.NORTH} of the content pane. The parent
 * window uses {@code FlatLaf.fullWindowContent = true} so that the content
 * pane extends under the native title-bar area, and
 * {@code JComponent.titleBarCaption = true} on this panel tells FlatLaf's
 * native hit-test to treat blank areas here as {@code HTCAPTION}. This
 * enables window dragging and all Aero Snap gestures natively without any
 * Java-level drag handler. {@link javax.swing.AbstractButton} descendants
 * are automatically treated as {@code HTCLIENT} so button clicks reach the
 * action listeners.</p>
 */
public class CustomHeaderPanel extends JPanel {
    // Pre-allocated rendering constants
    private static final Color HEADER_GLOW_COLOR = new Color(0, 255, 255, 60);
    private static final Color HEADER_LINE_COLOR = new Color(0, 255, 255);
    private static final BasicStroke HEADER_STROKE_3 = new BasicStroke(3);
    private static final BasicStroke HEADER_STROKE_1_5 = new BasicStroke(1.5f);

    /**
     * Creates the custom header panel.
     *
     * @param headerWidth  preferred width
     * @param headerHeight preferred height
     * @param onSettings   callback for settings button click
     * @param onMinimize   callback for minimize button click
     * @param onMaximize   callback for maximize/restore button click
     * @param onClose      callback for close button click
     * @param isMaximized  supplier that returns true when the window is currently maximized
     */
    public CustomHeaderPanel(int headerWidth, int headerHeight,
                             Runnable onSettings, Runnable onMinimize,
                             Runnable onMaximize, Runnable onClose,
                             BooleanSupplier isMaximized) {
        setPreferredSize(new Dimension(headerWidth, headerHeight));
        setBackground(new Color(20, 20, 28));
        setOpaque(false);
        setLayout(new BorderLayout());

        // Left section: Settings button (10 px left inset)
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 10, 0, 0);
        ModernButton settingsButton = new ModernButton("", ModernButton.ButtonIcon.GEAR);
        settingsButton.setPreferredSize(new Dimension(45, 45));
        settingsButton.addActionListener(e -> onSettings.run());
        leftPanel.add(settingsButton, gbc);
        add(leftPanel, BorderLayout.WEST);

        // Center section: title label (also a drag target – FlatLaf handles the drag natively)
        JLabel titleLabel = new JLabel("BIO-LAB EVOLUTION SIMULATOR");
        titleLabel.setForeground(new Color(0, 255, 255));
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(titleLabel, BorderLayout.CENTER);

        // Right section: Minimize / Maximize / Close (10 px right inset on close button)
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        GridBagConstraints gbcR = new GridBagConstraints();
        gbcR.anchor = GridBagConstraints.EAST;
        gbcR.insets = new Insets(0, 4, 0, 0);

        ModernButton minimizeButton = new ModernButton("", ModernButton.ButtonIcon.MINIMIZE);
        minimizeButton.setPreferredSize(new Dimension(45, 45));
        minimizeButton.addActionListener(e -> onMinimize.run());
        rightPanel.add(minimizeButton, gbcR);

        ModernButton maximizeButton = new ModernButton("",
                (g2d, pos) -> drawDynamicMaximizeIcon(g2d, pos, isMaximized.getAsBoolean()));
        maximizeButton.setPreferredSize(new Dimension(45, 45));
        maximizeButton.addActionListener(e -> {
            onMaximize.run();
            maximizeButton.repaint();
        });
        rightPanel.add(maximizeButton, gbcR);

        GridBagConstraints gbcClose = new GridBagConstraints();
        gbcClose.anchor = GridBagConstraints.EAST;
        gbcClose.insets = new Insets(0, 4, 0, 10);
        ModernButton closeButton = new ModernButton("", ModernButton.ButtonIcon.CLOSE);
        closeButton.setPreferredSize(new Dimension(45, 45));
        closeButton.addActionListener(e -> onClose.run());
        rightPanel.add(closeButton, gbcClose);

        add(rightPanel, BorderLayout.EAST);

        // Window dragging and Aero Snap are handled natively by FlatLaf
        // (JComponent.titleBarCaption = true, set in BioLabSimulatorApp).
    }

    /**
     * Draws the maximize/restore icon dynamically.
     * Shows a single rectangle in windowed mode and two stacked rectangles (restore icon)
     * when maximized.
     */
    private static void drawDynamicMaximizeIcon(Graphics2D g2d, Point pos, boolean isMaximized) {
        int x = pos.x;
        int y = pos.y;
        g2d.setStroke(new BasicStroke(2f));
        if (isMaximized) {
            int s = 6;
            // Back rectangle (top-right offset)
            g2d.drawRect(x - s + 3, y - s - 3, s * 2 - 1, s * 2 - 1);
            // Front rectangle – fill with header bg colour to erase the overlapping corner
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
            // Bottom separator line with glow effect
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
