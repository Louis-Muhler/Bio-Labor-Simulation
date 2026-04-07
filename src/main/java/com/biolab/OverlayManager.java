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
    private static final int SPEED_BUTTON_WIDTH = 112;
    private static final int SPEED_BUTTON_HEIGHT = 45;
    private static final int TOOL_BUTTON_WIDTH = 220;
    private static final int TOOL_BUTTON_HEIGHT = SPEED_BUTTON_HEIGHT;
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
    private static final int ENV_STATS_GAP = 8;
    private static final int STATS_CREATOR_GAP = 8;
    private static final int LEFT_VIEWER_TO_SPECIMEN_GAP = 12;
    /**
     * Fixed height of the environment panel (content does not change).
     */
    private static final int ENV_PANEL_HEIGHT = 285;

    private final Supplier<JLayeredPane> layeredPaneSupplier;

    private final InspectorPanel inspectorPanel;
    private final EnvironmentPanel environmentPanel;
    private final WorldStatsPanel worldStatsPanel;
    private final MicrobeCreatorPanel microbeCreatorPanel;
    private final ModernButton envToggleButton;
    private final ModernButton statsToggleButton;
    private final ModernButton creatorToggleButton;
    private final ModernButton speedButton;
    private final ModernButton spawnToolDeactivateButton;
    private final JPanel populationOverlay;
    private final JLabel populationLabel;
    private boolean inspectorVisibleBeforeHide;
    private boolean environmentVisibleBeforeHide;
    private boolean statsVisibleBeforeHide;
    private boolean creatorVisibleBeforeHide;
    private boolean gameplayOverlaysVisible = true;
    private boolean spawnToolActive;

    // ────────────────────────────────────────────────────────────────────
    // Construction
    // ────────────────────────────────────────────────────────────────────

    /**
     * @param layeredPaneSupplier supplies the current {@link JLayeredPane}; evaluated lazily
     * @param inspectorPanel      right-side microbe detail panel
     * @param environmentPanel    left-side environment slider panel
     * @param worldStatsPanel     left-side world statistics panel
     * @param envToggleButton     button that shows / hides the environment panel
     * @param statsToggleButton   button that shows / hides the world stats panel
     * @param speedButton         simulation speed toggle in the bottom-right corner
     */
    public OverlayManager(Supplier<JLayeredPane> layeredPaneSupplier,
                          InspectorPanel inspectorPanel, EnvironmentPanel environmentPanel,
                          WorldStatsPanel worldStatsPanel,
                          ModernButton envToggleButton,
                          ModernButton statsToggleButton,
                          ModernButton speedButton) {
        this(layeredPaneSupplier,
                inspectorPanel,
                environmentPanel,
                worldStatsPanel,
                null,
                envToggleButton,
                statsToggleButton,
                null,
                speedButton,
                null);
    }

    public OverlayManager(Supplier<JLayeredPane> layeredPaneSupplier,
                          InspectorPanel inspectorPanel,
                          EnvironmentPanel environmentPanel,
                          WorldStatsPanel worldStatsPanel,
                          MicrobeCreatorPanel microbeCreatorPanel,
                          ModernButton envToggleButton,
                          ModernButton statsToggleButton,
                          ModernButton creatorToggleButton,
                          ModernButton speedButton) {
        this(layeredPaneSupplier,
                inspectorPanel,
                environmentPanel,
                worldStatsPanel,
                microbeCreatorPanel,
                envToggleButton,
                statsToggleButton,
                creatorToggleButton,
                speedButton,
                null);
    }

    public OverlayManager(Supplier<JLayeredPane> layeredPaneSupplier,
                          InspectorPanel inspectorPanel,
                          EnvironmentPanel environmentPanel,
                          WorldStatsPanel worldStatsPanel,
                          MicrobeCreatorPanel microbeCreatorPanel,
                          ModernButton envToggleButton,
                          ModernButton statsToggleButton,
                          ModernButton creatorToggleButton,
                          ModernButton speedButton,
                          ModernButton spawnToolDeactivateButton) {
        this.layeredPaneSupplier = layeredPaneSupplier;
        this.inspectorPanel = inspectorPanel;
        this.environmentPanel = environmentPanel;
        this.worldStatsPanel = worldStatsPanel;
        this.microbeCreatorPanel = microbeCreatorPanel;
        this.envToggleButton = envToggleButton;
        this.statsToggleButton = statsToggleButton;
        this.creatorToggleButton = creatorToggleButton;
        this.speedButton = speedButton;
        this.spawnToolDeactivateButton = spawnToolDeactivateButton;

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

        int topY = overlayTopY(lp);
        int panelHeight = viewerAvailableHeight(lp);
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
        int topY = overlayTopY(lp);
        int panelX = leftViewerX();
        int panelHeight = Math.min(ENV_PANEL_HEIGHT, viewerAvailableHeight(lp));
        panelHeight = Math.max(220, panelHeight);

        if (environmentPanel.getParent() != lp) {
            lp.add(environmentPanel, JLayeredPane.PALETTE_LAYER);
        }
        environmentPanel.setBounds(panelX, topY, 300, panelHeight);
        environmentPanel.revalidate();
        environmentPanel.repaint();
    }

    public void positionWorldStatsPanel() {
        JLayeredPane lp = layeredPaneSupplier.get();
        int topY = overlayTopY(lp);
        int panelX = leftViewerX();
        int maxWidth = maxLeftViewerWidth(lp);
        int maxHeight = viewerAvailableHeight(lp);

        if (worldStatsPanel.getParent() != lp) {
            lp.add(worldStatsPanel, JLayeredPane.PALETTE_LAYER);
        }
        Dimension clamped = worldStatsPanel.clampToAvailableArea(maxWidth, maxHeight);
        worldStatsPanel.setBounds(panelX, topY, clamped.width, clamped.height);
        worldStatsPanel.revalidate();
        worldStatsPanel.repaint();
    }

    public void positionMicrobeCreatorPanel() {
        if (microbeCreatorPanel == null) {
            return;
        }
        JLayeredPane lp = layeredPaneSupplier.get();
        int topY = overlayTopY(lp);
        int panelX = leftViewerX();
        int availableHeight = viewerAvailableHeight(lp);
        int preferredHeight = microbeCreatorPanel.getPreferredSize() == null
                ? availableHeight
                : microbeCreatorPanel.getPreferredSize().height;
        int panelHeight;
        if (microbeCreatorPanel.selectedMode() == MicrobeCreatorPanel.SpawnMode.MICROBE) {
            panelHeight = availableHeight;
        } else {
            panelHeight = Math.min(availableHeight, Math.max(180, preferredHeight));
        }
        int panelWidth = Math.min(MicrobeCreatorPanel.PANEL_WIDTH, maxLeftViewerWidth(lp));
        panelWidth = Math.max(260, panelWidth);

        if (microbeCreatorPanel.getParent() != lp) {
            lp.add(microbeCreatorPanel, JLayeredPane.PALETTE_LAYER);
        }
        microbeCreatorPanel.setBounds(panelX, topY, panelWidth, panelHeight);
        microbeCreatorPanel.revalidate();
        microbeCreatorPanel.repaint();
    }

    /** Places the environment toggle button directly below the settings button. */
    public void positionEnvToggleButton() {
        JLayeredPane lp = layeredPaneSupplier.get();
        int topY = overlayTopY(lp) + BTN_SIZE + SETTINGS_ENV_GAP;

        if (envToggleButton.getParent() != lp) {
            lp.add(envToggleButton, JLayeredPane.PALETTE_LAYER);
        }
        envToggleButton.setBounds(OVERLAY_EDGE_MARGIN, topY, BTN_SIZE, BTN_SIZE);
        envToggleButton.revalidate();
        envToggleButton.repaint();
    }

    public void positionStatsToggleButton() {
        JLayeredPane lp = layeredPaneSupplier.get();
        int topY = overlayTopY(lp) + (BTN_SIZE * 2) + SETTINGS_ENV_GAP + ENV_STATS_GAP;

        if (statsToggleButton.getParent() != lp) {
            lp.add(statsToggleButton, JLayeredPane.PALETTE_LAYER);
        }
        statsToggleButton.setBounds(OVERLAY_EDGE_MARGIN, topY, BTN_SIZE, BTN_SIZE);
        statsToggleButton.revalidate();
        statsToggleButton.repaint();
    }

    public void positionCreatorToggleButton() {
        if (creatorToggleButton == null) {
            return;
        }
        JLayeredPane lp = layeredPaneSupplier.get();
        int topY = overlayTopY(lp) + (BTN_SIZE * 3) + SETTINGS_ENV_GAP + ENV_STATS_GAP + STATS_CREATOR_GAP;

        if (creatorToggleButton.getParent() != lp) {
            lp.add(creatorToggleButton, JLayeredPane.PALETTE_LAYER);
        }
        creatorToggleButton.setBounds(OVERLAY_EDGE_MARGIN, topY, BTN_SIZE, BTN_SIZE);
        creatorToggleButton.revalidate();
        creatorToggleButton.repaint();
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

        if (speedButton != null) {
            if (speedButton.getParent() != lp) {
                lp.add(speedButton, JLayeredPane.PALETTE_LAYER);
            }
            speedButton.setBounds(speedX, speedY, SPEED_BUTTON_WIDTH, SPEED_BUTTON_HEIGHT);
            speedButton.revalidate();
            speedButton.repaint();
        }

        if (spawnToolDeactivateButton != null) {
            int toolX = OVERLAY_EDGE_MARGIN;
            int toolY = speedY;
            if (spawnToolDeactivateButton.getParent() != lp) {
                // Keep this below left viewers so it never overlays the World Stats panel.
                lp.add(spawnToolDeactivateButton, JLayeredPane.DEFAULT_LAYER);
            }
            spawnToolDeactivateButton.setBounds(toolX, toolY, TOOL_BUTTON_WIDTH, TOOL_BUTTON_HEIGHT);
            spawnToolDeactivateButton.revalidate();
            spawnToolDeactivateButton.repaint();
        }

        int popX = (lpW - POP_OVERLAY_WIDTH) / 2;
        int popY = contentTop + OVERLAY_EDGE_MARGIN + 5;

        if (populationOverlay.getParent() != lp) {
            // DEFAULT_LAYER renders below PALETTE_LAYER panels
            lp.add(populationOverlay, JLayeredPane.DEFAULT_LAYER);
        }
        populationOverlay.setBounds(popX, popY, POP_OVERLAY_WIDTH, POP_OVERLAY_HEIGHT);
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
        positionEnvToggleButton();
        positionStatsToggleButton();
        positionCreatorToggleButton();
        positionFloatingControls();
        if (environmentPanel.isVisible()) {
            positionEnvironmentPanel();
        }
        if (worldStatsPanel.isVisible()) {
            positionWorldStatsPanel();
        }
        if (microbeCreatorPanel != null && microbeCreatorPanel.isVisible()) {
            positionMicrobeCreatorPanel();
        }
        updateSpawnToolDeactivateButtonVisibility();
    }

    private int overlayTopY(JLayeredPane lp) {
        return getContentTopY(lp) + OVERLAY_EDGE_MARGIN;
    }

    private int overlayBottomMargin() {
        return SPEED_BUTTON_HEIGHT + 2 * OVERLAY_EDGE_MARGIN;
    }

    private int viewerAvailableHeight(JLayeredPane lp) {
        return Math.max(220, lp.getHeight() - overlayTopY(lp) - overlayBottomMargin());
    }

    private int leftViewerX() {
        return OVERLAY_EDGE_MARGIN + BTN_SIZE + 4;
    }

    private int inspectorLeftX(JLayeredPane lp) {
        return lp.getWidth() - InspectorPanel.PANEL_WIDTH - OVERLAY_EDGE_MARGIN;
    }

    private int maxLeftViewerWidth(JLayeredPane lp) {
        int maxWidth = inspectorLeftX(lp) - leftViewerX() - LEFT_VIEWER_TO_SPECIMEN_GAP;
        return Math.max(260, maxWidth);
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
            if (worldStatsPanel.isVisible()) {
                worldStatsPanel.hidePanel();
                statsToggleButton.setDimmed(false);
            }
            if (microbeCreatorPanel != null && microbeCreatorPanel.isVisible()) {
                microbeCreatorPanel.setVisible(false);
                if (creatorToggleButton != null) creatorToggleButton.setDimmed(false);
            }
            environmentPanel.setVisible(true);
            positionEnvironmentPanel();
            envToggleButton.setDimmed(true);
        }
        updateSpawnToolDeactivateButtonVisibility();
        lp.repaint();
    }

    public void toggleWorldStatsPanel() {
        JLayeredPane lp = layeredPaneSupplier.get();
        if (worldStatsPanel.isVisible()) {
            worldStatsPanel.hidePanel();
            statsToggleButton.setDimmed(false);
        } else {
            if (environmentPanel.isVisible()) {
                environmentPanel.setVisible(false);
                envToggleButton.setDimmed(false);
            }
            if (microbeCreatorPanel != null && microbeCreatorPanel.isVisible()) {
                microbeCreatorPanel.setVisible(false);
                if (creatorToggleButton != null) creatorToggleButton.setDimmed(false);
            }
            worldStatsPanel.showPanel();
            positionWorldStatsPanel();
            statsToggleButton.setDimmed(true);
        }
        updateSpawnToolDeactivateButtonVisibility();
        lp.repaint();
    }

    public void toggleMicrobeCreatorPanel() {
        if (microbeCreatorPanel == null || creatorToggleButton == null) {
            return;
        }
        JLayeredPane lp = layeredPaneSupplier.get();
        if (microbeCreatorPanel.isVisible()) {
            microbeCreatorPanel.setVisible(false);
            creatorToggleButton.setDimmed(false);
        } else {
            if (environmentPanel.isVisible()) {
                environmentPanel.setVisible(false);
                envToggleButton.setDimmed(false);
            }
            if (worldStatsPanel.isVisible()) {
                worldStatsPanel.hidePanel();
                statsToggleButton.setDimmed(false);
            }
            microbeCreatorPanel.setVisible(true);
            positionMicrobeCreatorPanel();
            creatorToggleButton.setDimmed(true);
        }
        updateSpawnToolDeactivateButtonVisibility();
        lp.repaint();
    }

    public void setSpawnToolActive(boolean active) {
        spawnToolActive = active;
        if (microbeCreatorPanel != null) {
            microbeCreatorPanel.setSpawnToolActive(active);
        }
        updateSpawnToolDeactivateButtonVisibility();
    }

    private void updateSpawnToolDeactivateButtonVisibility() {
        if (spawnToolDeactivateButton == null) {
            return;
        }
        boolean creatorVisible = microbeCreatorPanel != null && microbeCreatorPanel.isVisible();
        spawnToolDeactivateButton.setVisible(gameplayOverlaysVisible && spawnToolActive && !creatorVisible);
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

    /**
     * Shows or hides all gameplay overlays/buttons managed by this instance.
     */
    public void setGameplayOverlaysVisible(boolean visible) {
        gameplayOverlaysVisible = visible;
        if (visible) {
            inspectorPanel.setVisible(inspectorVisibleBeforeHide);
            boolean wantStats = statsVisibleBeforeHide;
            boolean wantCreator = creatorVisibleBeforeHide;
            boolean wantEnv = environmentVisibleBeforeHide && !wantStats && !wantCreator;
            environmentPanel.setVisible(wantEnv);
            if (wantStats) {
                worldStatsPanel.showPanel();
            } else {
                worldStatsPanel.hidePanel();
            }
            if (microbeCreatorPanel != null) {
                microbeCreatorPanel.setVisible(wantCreator && !wantStats && !wantEnv);
            }
            envToggleButton.setDimmed(wantEnv);
            statsToggleButton.setDimmed(wantStats);
            if (creatorToggleButton != null) {
                creatorToggleButton.setDimmed(microbeCreatorPanel != null && microbeCreatorPanel.isVisible());
            }
        } else {
            inspectorVisibleBeforeHide = inspectorPanel.isVisible();
            environmentVisibleBeforeHide = environmentPanel.isVisible();
            statsVisibleBeforeHide = worldStatsPanel.isVisible();
            creatorVisibleBeforeHide = microbeCreatorPanel != null && microbeCreatorPanel.isVisible();
            inspectorPanel.setVisible(false);
            environmentPanel.setVisible(false);
            worldStatsPanel.hidePanel();
            if (microbeCreatorPanel != null) {
                microbeCreatorPanel.setVisible(false);
            }
        }
        envToggleButton.setVisible(visible);
        statsToggleButton.setVisible(visible);
        if (creatorToggleButton != null) creatorToggleButton.setVisible(visible);
        speedButton.setVisible(visible);
        if (spawnToolDeactivateButton != null) {
            spawnToolDeactivateButton.setVisible(false);
        }
        populationOverlay.setVisible(visible);
        if (!visible) {
            envToggleButton.setDimmed(false);
            statsToggleButton.setDimmed(false);
            if (creatorToggleButton != null) creatorToggleButton.setDimmed(false);
            inspectorPanel.hidePanel();
        }
        JLayeredPane lp = layeredPaneSupplier.get();
        updateSpawnToolDeactivateButtonVisibility();
        lp.repaint();
    }

    /**
     * Removes all managed components from the current layered pane.
     */
    public void removeAllOverlays() {
        JLayeredPane lp = layeredPaneSupplier.get();
        lp.remove(inspectorPanel);
        lp.remove(environmentPanel);
        lp.remove(worldStatsPanel);
        if (microbeCreatorPanel != null) {
            lp.remove(microbeCreatorPanel);
        }
        lp.remove(envToggleButton);
        lp.remove(statsToggleButton);
        if (creatorToggleButton != null) {
            lp.remove(creatorToggleButton);
        }
        lp.remove(speedButton);
        if (spawnToolDeactivateButton != null) {
            lp.remove(spawnToolDeactivateButton);
        }
        lp.remove(populationOverlay);
        lp.repaint();
    }
}
