package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.util.function.BooleanSupplier;

/**
 * Custom header panel with window controls, title, and dragging functionality.
 */
public class CustomHeaderPanel extends JPanel {
    // Pre-allocated rendering constants
    private static final Color HEADER_GLOW_COLOR = new Color(0, 255, 255, 60);
    private static final Color HEADER_LINE_COLOR = new Color(0, 255, 255);
    private static final BasicStroke HEADER_STROKE_3 = new BasicStroke(3);
    private static final BasicStroke HEADER_STROKE_1_5 = new BasicStroke(1.5f);
    private Point initialClick = new Point(0, 0);


    /**
     * Creates the custom header panel.
     *
     * @param headerWidth  preferred width
     * @param headerHeight preferred height
     * @param parentFrame  the parent JFrame for dragging and close/settings actions
     * @param onSettings   callback for settings button click
     * @param onMinimize   callback for minimize button click
     * @param onMaximize   callback for maximize/restore button click
     * @param onClose      callback for close button click
     * @param isMaximized  supplier that returns true when the window is currently maximized
     */
    public CustomHeaderPanel(int headerWidth, int headerHeight, JFrame parentFrame,
                             Runnable onSettings, Runnable onMinimize,
                             Runnable onMaximize, Runnable onClose,
                             BooleanSupplier isMaximized) {
        setPreferredSize(new Dimension(headerWidth, headerHeight));
        setBackground(new Color(20, 20, 28));
        setOpaque(false); // Parent contentPane paints the background; we only paint our own decorations
        setLayout(new BorderLayout());

        // Left section: Settings button  (10px left inset, matching right side)
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

        // Center section: Title (draggable area)
        JLabel titleLabel = new JLabel("BIO-LAB EVOLUTION SIMULATOR");
        titleLabel.setForeground(new Color(0, 255, 255));
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(titleLabel, BorderLayout.CENTER);

        // Right section: Minimize, Maximize, Close  (same 10px right inset as left side)
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);

        GridBagConstraints gbcR = new GridBagConstraints();
        gbcR.anchor = GridBagConstraints.EAST;
        gbcR.insets = new Insets(0, 4, 0, 0);

        // Minimize button
        ModernButton minimizeButton = new ModernButton("", ModernButton.ButtonIcon.MINIMIZE);
        minimizeButton.setPreferredSize(new Dimension(45, 45));
        minimizeButton.addActionListener(e -> onMinimize.run());
        rightPanel.add(minimizeButton, gbcR);

        // Maximize/Restore button â€“ icon changes dynamically
        ModernButton maximizeButton = new ModernButton("",
                (g2d, pos) -> drawDynamicMaximizeIcon(g2d, pos, isMaximized.getAsBoolean()));
        maximizeButton.setPreferredSize(new Dimension(45, 45));
        maximizeButton.addActionListener(e -> {
            onMaximize.run();
            maximizeButton.repaint(); // Refresh icon after state change
        });
        rightPanel.add(maximizeButton, gbcR);

        // Close button â€“ last item gets the 10px right margin
        GridBagConstraints gbcClose = new GridBagConstraints();
        gbcClose.anchor = GridBagConstraints.EAST;
        gbcClose.insets = new Insets(0, 4, 0, 10); // 10px from right edge
        ModernButton closeButton = new ModernButton("", ModernButton.ButtonIcon.CLOSE);
        closeButton.setPreferredSize(new Dimension(45, 45));
        closeButton.addActionListener(e -> onClose.run());
        rightPanel.add(closeButton, gbcClose);

        add(rightPanel, BorderLayout.EAST);

        // Make the header draggable
        setupDragging(parentFrame, titleLabel);
    }

    /**
     * Draws the maximize icon dynamically:
     * â€“ Single rectangle  when in normal/windowed state
     * â€“ Two stacked rectangles when maximized (restore icon)
     */
    private static void drawDynamicMaximizeIcon(Graphics2D g2d, Point pos, boolean isMaximized) {
        int x = pos.x;
        int y = pos.y;
        g2d.setStroke(new BasicStroke(2f));
        if (isMaximized) {
            // Restore icon: two overlapping rectangles (back one offset top-right)
            int s = 6;
            // Back rectangle (top-right offset)
            g2d.drawRect(x - s + 3, y - s - 3, s * 2 - 1, s * 2 - 1);
            // Front rectangle (bottom-left, filled with bg to "cut" the corner)
            g2d.setColor(new Color(20, 20, 28)); // same as header bg â€“ erase overlap
            g2d.fillRect(x - s - 1, y - s + 2, s * 2 - 1, s * 2 - 1);
            g2d.setColor(new Color(0, 255, 255)); // restore cyan
            g2d.drawRect(x - s - 1, y - s + 2, s * 2 - 1, s * 2 - 1);
        } else {
            // Normal maximize icon: single rectangle
            int s = 7;
            g2d.drawRect(x - s, y - s, s * 2, s * 2);
        }
    }

    // ===== Dragging =====

    private void setupDragging(JFrame parentFrame, JLabel titleLabel) {
        makeDraggable(this, parentFrame, this);
        makeDraggable(titleLabel, parentFrame, this);
    }

    private void makeDraggable(Component component, JFrame parentFrame, JPanel referencePanel) {
        component.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                initialClick = (component == referencePanel)
                        ? e.getPoint()
                        : SwingUtilities.convertPoint(component, e.getPoint(), referencePanel);
            }
        });

        component.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                Point current = (component == referencePanel)
                        ? e.getPoint()
                        : SwingUtilities.convertPoint(component, e.getPoint(), referencePanel);
                int thisX = parentFrame.getLocation().x;
                int thisY = parentFrame.getLocation().y;
                parentFrame.setLocation(thisX + current.x - initialClick.x,
                        thisY + current.y - initialClick.y);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Don't call super.paintComponent – we fill the background ourselves
        // so the parent's rounded clip (from paintChildren) correctly trims our corners.
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Fill header background
            g2d.setColor(getBackground());
            g2d.fillRect(0, 0, getWidth(), getHeight());
            // Bottom glow line
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
