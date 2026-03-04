package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

/**
 * Manages all floating overlay components on the application's {@link JLayeredPane}.
 *
 * <p>All overlays are placed on {@link JLayeredPane#PALETTE_LAYER} so they always
 * appear above the simulation canvas. The population overlay is placed on
 * {@link JLayeredPane#DEFAULT_LAYER} so it renders behind the other panels.</p>
 *
 * <p>A {@link Supplier} is used for the layered pane instead of storing a direct
 * reference because FlatLaf may recreate the root pane when switching between
 * windowed and full-screen mode.</p>
 */
public class OverlayManager {

    // ── Positioning constants ─────────────────────────────────────────────
    /**
     * Uniform margin between the window edge and any overlay panel or button.
     */
    private static final int OVERLAY_EDGE_MARGIN = 15;
    private static final int SPEED_BUTTON_WIDTH = 100;
    private static final int SPEED_BUTTON_HEIGHT = 45;
    private static final int POP_OVERLAY_WIDTH = 280;
    private static final int POP_OVERLAY_HEIGHT = 100;
    /**
     * Pixel size of the square settings and environment toggle buttons.
     */
    private static final int BTN_SIZE = 45;
    /**
     * Vertical gap between the settings button and the environment toggle button.
     */
    private static final int SETTINGS_ENV_GAP = 12;
    /**
     * Fixed height of the environment panel (content does not change).
     */
    private static final int ENV_PANEL_HEIGHT = 310;

    private final Supplier<JLayeredPane> layeredPaneSupplier;

    private final InspectorPanel inspectorPanel;
    private final EnvironmentPanel environmentPanel;
    private final ModernButton envToggleButton;
    private final ModernButton settingsButton;
    private final ModernButton speedButton;
    private final JPanel populationOverlay;
    private final JLabel populationLabel;

    // ────────────────────────────────────────────────────────────────────
    // Construction
    // ────────────────────────────────────────────────────────────────────

    /**
     * @param layeredPaneSupplier supplies the current {@link JLayeredPane}; evaluated lazily
     * @param inspectorPanel      right-side microbe detail panel
     * @param environmentPanel    left-side environment slider panel
     * @param envToggleButton     button that shows / hides the environment panel
     * @param settingsButton      button that opens the settings overlay
     * @param speedButton         simulation speed toggle in the bottom-right corner
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

        // Transparent panel – no background box, only the label itself is visible
        this.populationOverlay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) { /* fully transparent */ }
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

    // ────────────────────────────────────────────────────────────────────
    // Utility
    // ────────────────────────────────────────────────────────────────────

    /**
     * Builds the HTML string used by the population label.
     * Extracted as a static method so it can be tested without a UI.
     */
    static String formatPopulationHtml(int population) {
        return String.format(
                "<html><center>" +
                        "<span style='font-size:20px;color:#00CCCC;letter-spacing:3px;'>POPULATION</span><br>" +
                        "<span style='font-size:30px;color:#00FFFF;font-weight:bold;'>%,d</span>" +
                        "</center></html>",
                population);
    }

    /**
     * Returns the Y coordinate at the top of the content area (below the title bar).
     * Overlays positioned at this Y will appear directly below the FlatLaf title bar.
     */
    private int getContentTopY(JLayeredPane lp) {
        JRootPane root = SwingUtilities.getRootPane(lp);
        if (root != null) {
            return root.getContentPane().getY();
        }
        return 0;
    }

    // ────────────────────────────────────────────────────────────────────
    // Positioning
    // ────────────────────────────────────────────────────────────────────

    /**
     * Places the inspector panel on the right edge of the window.
     * The panel is given the full vertical space between the top margin and
     * the speed button; the panel's internal scroll pane handles overflow.
     */
    public void positionInspectorPanel() {
        JLayeredPane lp = layeredPaneSupplier.get();
        int lpW = lp.getWidth();
        int lpH = lp.getHeight();
        if (lpW <= 0 || lpH <= 0) return;

        int contentTop = getContentTopY(lp);
        int topY = contentTop + OVERLAY_EDGE_MARGIN;
        int bottomMargin = SPEED_BUTTON_HEIGHT + 2 * OVERLAY_EDGE_MARGIN;
        int panelHeight = lpH - topY - bottomMargin;
        int panelX = lpW - InspectorPanel.PANEL_WIDTH - OVERLAY_EDGE_MARGIN;

        if (inspectorPanel.getParent() != lp) {
            lp.add(inspectorPanel, JLayeredPane.PALETTE_LAYER);
        }
        inspectorPanel.setBounds(panelX, topY, InspectorPanel.PANEL_WIDTH, panelHeight);
        inspectorPanel.revalidate();
        inspectorPanel.repaint();
    }

    /**
     * Places the environment panel immediately to the right of the toggle button.
     */
    public void positionEnvironmentPanel() {
        JLayeredPane lp = layeredPaneSupplier.get();
        int topY = getContentTopY(lp) + OVERLAY_EDGE_MARGIN;
        int panelX = OVERLAY_EDGE_MARGIN + BTN_SIZE + 4;

        if (environmentPanel.getParent() != lp) {
            lp.add(environmentPanel, JLayeredPane.PALETTE_LAYER);
        }
        environmentPanel.setBounds(panelX, topY, 300, ENV_PANEL_HEIGHT);
        environmentPanel.revalidate();
        environmentPanel.repaint();
    }

    /**
     * Places the settings (gear) button in the top-left corner.
     */
    public void positionSettingsButton() {
        JLayeredPane lp = layeredPaneSupplier.get();
        int topY = getContentTopY(lp) + OVERLAY_EDGE_MARGIN;

        if (settingsButton.getParent() != lp) {
            lp.add(settingsButton, JLayeredPane.PALETTE_LAYER);
        }
        settingsButton.setBounds(OVERLAY_EDGE_MARGIN, topY, BTN_SIZE, BTN_SIZE);
        settingsButton.revalidate();
        settingsButton.repaint();
    }

    /** Places the environment toggle button directly below the settings button. */
    public void positionEnvToggleButton() {
        JLayeredPane lp = layeredPaneSupplier.get();
        int topY = getContentTopY(lp) + OVERLAY_EDGE_MARGIN + BTN_SIZE + SETTINGS_ENV_GAP;

        if (envToggleButton.getParent() != lp) {
            lp.add(envToggleButton, JLayeredPane.PALETTE_LAYER);
        }
        envToggleButton.setBounds(OVERLAY_EDGE_MARGIN, topY, BTN_SIZE, BTN_SIZE);
        envToggleButton.revalidate();
        envToggleButton.repaint();
    }

    /**
     * Places the speed button in the bottom-right corner and the population
     * counter at the top-center of the window.
     */
    public void positionFloatingControls() {
        JLayeredPane lp = layeredPaneSupplier.get();
        int lpW = lp.getWidth();
        int lpH = lp.getHeight();
        if (lpW <= 0 || lpH <= 0) return;

        int contentTop = getContentTopY(lp);

        int speedX = lpW - SPEED_BUTTON_WIDTH - OVERLAY_EDGE_MARGIN;
        int speedY = lpH - SPEED_BUTTON_HEIGHT - OVERLAY_EDGE_MARGIN;

        if (speedButton.getParent() != lp) {
            lp.add(speedButton, JLayeredPane.PALETTE_LAYER);
        }
        speedButton.setBounds(speedX, speedY, SPEED_BUTTON_WIDTH, SPEED_BUTTON_HEIGHT);
        speedButton.setVisible(true);
        speedButton.revalidate();
        speedButton.repaint();

        int popX = (lpW - POP_OVERLAY_WIDTH) / 2;
        int popY = contentTop + OVERLAY_EDGE_MARGIN + 5;

        if (populationOverlay.getParent() != lp) {
            // DEFAULT_LAYER renders below PALETTE_LAYER panels
            lp.add(populationOverlay, JLayeredPane.DEFAULT_LAYER);
        }
        populationOverlay.setBounds(popX, popY, POP_OVERLAY_WIDTH, POP_OVERLAY_HEIGHT);
        populationOverlay.setVisible(true);
        populationOverlay.revalidate();
        populationOverlay.repaint();
    }

    // ────────────────────────────────────────────────────────────────────
    // Coordinated actions
    // ────────────────────────────────────────────────────────────────────

    /**
     * Re-adds and repositions all overlays. Must be called after window resize
     * or after switching display modes, because those operations remove children
     * from the layered pane.
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

    /**
     * Toggles the environment panel visibility and updates the toggle button's
     * dimmed state to indicate whether the panel is open.
     */
    public void toggleEnvironmentPanel() {
        JLayeredPane lp = layeredPaneSupplier.get();
        if (environmentPanel.isVisible()) {
            environmentPanel.setVisible(false);
            envToggleButton.setDimmed(false);
        } else {
            environmentPanel.setVisible(true);
            positionEnvironmentPanel();
            envToggleButton.setDimmed(true);
        }
        lp.repaint();
    }

    // ────────────────────────────────────────────────────────────────────
    // Population display
    // ────────────────────────────────────────────────────────────────────

    /** Updates the population counter. Must be called on the EDT. */
    public void updatePopulationLabel(int population) {
        populationLabel.setText(formatPopulationHtml(population));
    }

    // ────────────────────────────────────────────────────────────────────
    // Accessors
    // ────────────────────────────────────────────────────────────────────

    /** Returns the {@link InspectorPanel} managed by this instance. */
    public InspectorPanel getInspectorPanel() {
        return inspectorPanel;
    }
}
