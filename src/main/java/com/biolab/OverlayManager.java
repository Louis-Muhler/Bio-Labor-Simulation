package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

/**
 * Manages all floating overlay panels on the JLayeredPane.
 * Handles positioning, visibility, and re-adding of overlays after display mode changes.
 */
public class OverlayManager {
    private static final int OVERLAY_EDGE_MARGIN = 15;
    private static final int SPEED_BUTTON_WIDTH = 100;
    private static final int SPEED_BUTTON_HEIGHT = 45;
    private static final int POP_OVERLAY_WIDTH = 280;
    private static final int POP_OVERLAY_HEIGHT = 100;
    private static final int BTN_SIZE = 45;
    /** Vertical gap between settings button and environment button to visually separate them. */
    private static final int SETTINGS_ENV_GAP = 12;
    /** Height of the environment panel. */
    private static final int ENV_PANEL_HEIGHT = 310;

    // Supplier to always get the current layered pane (may change after dispose/setVisible)
    private final Supplier<JLayeredPane> layeredPaneSupplier;

    private final InspectorPanel inspectorPanel;
    private final EnvironmentPanel environmentPanel;
    private final ModernButton envToggleButton;
    private final ModernButton settingsButton;
    private final ModernButton speedButton;
    private final JPanel populationOverlay;
    private final JLabel populationLabel;

    /**
     * Creates the overlay manager and initialises the population label overlay.
     *
     * @param layeredPaneSupplier supplies the JLayeredPane of the parent window
     * @param inspectorPanel      the microbe inspector panel
     * @param environmentPanel    the environment controls panel
     * @param envToggleButton     button that shows/hides the environment panel
     * @param settingsButton      button that opens the settings overlay
     * @param speedButton         the simulation speed toggle button
     */
    public OverlayManager(Supplier<JLayeredPane> layeredPaneSupplier,
                          InspectorPanel inspectorPanel, EnvironmentPanel environmentPanel,
                          ModernButton envToggleButton, ModernButton settingsButton,
                          ModernButton speedButton) {
        this.layeredPaneSupplier = layeredPaneSupplier;
        this.inspectorPanel = inspectorPanel;
        this.environmentPanel = environmentPanel;
        this.envToggleButton = envToggleButton;
        this.settingsButton = settingsButton;
        this.speedButton = speedButton;

        // Create population overlay
        this.populationOverlay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                // Transparent background - no box
            }
        };
        populationOverlay.setOpaque(false);
        populationOverlay.setBorder(null);
        populationOverlay.setLayout(new BorderLayout());

        populationLabel = new JLabel(formatPopulationHtml(0));
        populationLabel.setFont(new Font("Segoe UI", Font.BOLD, 30));
        populationLabel.setForeground(new Color(0, 255, 255));
        populationLabel.setHorizontalAlignment(SwingConstants.CENTER);
        populationLabel.setBorder(null);
        populationOverlay.add(populationLabel, BorderLayout.CENTER);
    }

    // ===== Positioning Methods =====

    /**
     * Centralized HTML generation for population label.
     */
    static String formatPopulationHtml(int population) {
        return String.format(
                "<html><center><span style='font-size:20px;color:#00CCCC;letter-spacing:3px;'>POPULATION</span><br>" +
                        "<span style='font-size:30px;color:#00FFFF;font-weight:bold;'>%,d</span></center></html>",
                population);
    }

    /**
     * Returns the Y coordinate on the JLayeredPane where the content area begins
     * (i.e. just below the title bar). Overlays positioned at this Y will appear
     * directly below the title bar, not behind it.
     */
    private int getContentTopY(JLayeredPane lp) {
        JRootPane root = SwingUtilities.getRootPane(lp);
        if (root != null) {
            Container content = root.getContentPane();
            // content.getY() gives the Y position of the content pane within the root pane,
            // which accounts for the title bar height.
            return content.getY();
        }
        return 0;
    }

    /**
     * Positions (and re-adds if needed) the inspector panel on the right edge of the window.
     */
    public void positionInspectorPanel() {
        JLayeredPane lp = layeredPaneSupplier.get();
        int lpW = lp.getWidth();
        int lpH = lp.getHeight();
        if (lpW <= 0 || lpH <= 0) return;

        int contentTop = getContentTopY(lp);

        int panelWidth = 320;
        int topY = contentTop + OVERLAY_EDGE_MARGIN;
        // Fill available vertical space so the panel content is never clipped
        int panelHeight = lpH - topY - OVERLAY_EDGE_MARGIN;
        int panelX = lpW - panelWidth - OVERLAY_EDGE_MARGIN;

        if (inspectorPanel.getParent() != lp) {
            lp.add(inspectorPanel, JLayeredPane.PALETTE_LAYER);
        }
        inspectorPanel.setBounds(panelX, topY, panelWidth, panelHeight);
        inspectorPanel.revalidate();
        inspectorPanel.repaint();
    }

    /**
     * Positions (and re-adds if needed) the environment panel to the right of the toggle button.
     */
    public void positionEnvironmentPanel() {
        JLayeredPane lp = layeredPaneSupplier.get();

        int contentTop = getContentTopY(lp);

        // Same top Y as inspector panel for visual consistency
        int topY = contentTop + OVERLAY_EDGE_MARGIN;
        int gap = 4;

        int panelWidth = 300;
        int panelX = OVERLAY_EDGE_MARGIN + BTN_SIZE + gap;

        if (environmentPanel.getParent() != lp) {
            lp.add(environmentPanel, JLayeredPane.PALETTE_LAYER);
        }
        environmentPanel.setBounds(panelX, topY, panelWidth, ENV_PANEL_HEIGHT);
        environmentPanel.revalidate();
        environmentPanel.repaint();
    }

    /**
     * Positions (and re-adds if needed) the settings button on the top-left corner.
     */
    public void positionSettingsButton() {
        JLayeredPane lp = layeredPaneSupplier.get();

        int contentTop = getContentTopY(lp);
        int topY = contentTop + OVERLAY_EDGE_MARGIN;

        if (settingsButton.getParent() != lp) {
            lp.add(settingsButton, JLayeredPane.PALETTE_LAYER);
        }
        settingsButton.setBounds(OVERLAY_EDGE_MARGIN, topY, BTN_SIZE, BTN_SIZE);
        settingsButton.revalidate();
        settingsButton.repaint();
    }

    /**
     * Positions (and re-adds if needed) the environment toggle button below the settings button.
     */
    public void positionEnvToggleButton() {
        JLayeredPane lp = layeredPaneSupplier.get();

        int contentTop = getContentTopY(lp);

        // Below the settings button with a gap
        int topY = contentTop + OVERLAY_EDGE_MARGIN + BTN_SIZE + SETTINGS_ENV_GAP;

        if (envToggleButton.getParent() != lp) {
            lp.add(envToggleButton, JLayeredPane.PALETTE_LAYER);
        }
        envToggleButton.setBounds(OVERLAY_EDGE_MARGIN, topY, BTN_SIZE, BTN_SIZE);
        envToggleButton.revalidate();
        envToggleButton.repaint();
    }

    /**
     * Positions (and re-adds if needed) the speed button and population overlay.
     */
    public void positionFloatingControls() {
        JLayeredPane lp = layeredPaneSupplier.get();
        int lpW = lp.getWidth();
        int lpH = lp.getHeight();
        if (lpW <= 0 || lpH <= 0) return;

        int contentTop = getContentTopY(lp);

        // Speed button - bottom right
        int speedX = lpW - SPEED_BUTTON_WIDTH - OVERLAY_EDGE_MARGIN;
        int speedY = lpH - SPEED_BUTTON_HEIGHT - OVERLAY_EDGE_MARGIN;

        if (speedButton.getParent() != lp) {
            lp.add(speedButton, JLayeredPane.PALETTE_LAYER);
        }
        speedButton.setBounds(speedX, speedY, SPEED_BUTTON_WIDTH, SPEED_BUTTON_HEIGHT);
        speedButton.setVisible(true);
        speedButton.revalidate();
        speedButton.repaint();

        // Population overlay - top center
        int popX = (lpW - POP_OVERLAY_WIDTH) / 2;
        int popY = contentTop + OVERLAY_EDGE_MARGIN + 5;

        if (populationOverlay.getParent() != lp) {
            lp.add(populationOverlay, JLayeredPane.PALETTE_LAYER);
        }
        populationOverlay.setBounds(popX, popY, POP_OVERLAY_WIDTH, POP_OVERLAY_HEIGHT);
        populationOverlay.setVisible(true);
        populationOverlay.revalidate();
        populationOverlay.repaint();
    }

    // ===== Toggle =====

    /**
     * Re-adds and repositions all floating overlay components.
     * Must be called after dispose()/setVisible() since those remove layered pane children.
     */
    public void repositionAllOverlays() {
        positionInspectorPanel();
        positionSettingsButton();
        positionEnvToggleButton();
        positionFloatingControls();
        if (environmentPanel.isVisible()) {
            positionEnvironmentPanel();
        }
    }

    // ===== Population Display =====

    /**
     * Shows or hides the environment panel and updates the toggle button's dimmed state.
     */
    public void toggleEnvironmentPanel() {
        JLayeredPane lp = layeredPaneSupplier.get();
        if (environmentPanel.isVisible()) {
            environmentPanel.setVisible(false);
            envToggleButton.setDimmed(false);
            lp.repaint();
        } else {
            environmentPanel.setVisible(true);
            positionEnvironmentPanel();
            envToggleButton.setDimmed(true);
            lp.repaint();
        }
    }

    /**
     * Updates the population label text. Must be called on the EDT.
     */
    public void updatePopulationLabel(int population) {
        populationLabel.setText(formatPopulationHtml(population));
    }

    // ===== Accessors =====

    /**
     * Returns the inspector panel managed by this overlay manager.
     */
    public InspectorPanel getInspectorPanel() {
        return inspectorPanel;
    }
}

