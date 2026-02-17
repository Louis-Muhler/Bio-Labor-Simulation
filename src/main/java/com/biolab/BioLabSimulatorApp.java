package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Main application window for the Bio-Lab Evolution Simulator.
 * Features a custom dark UI with multithreading, energy system, and ancestry tracking.
 */
public class BioLabSimulatorApp extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(BioLabSimulatorApp.class.getName());

    private static final int INITIAL_POPULATION = 1500;
    private static final int CUSTOM_HEADER_HEIGHT = 55;
    private static final int CONTROL_PANEL_HEIGHT = 150;
    private static final int TOTAL_UI_HEIGHT = CONTROL_PANEL_HEIGHT + CUSTOM_HEADER_HEIGHT;
    private static final int UNLIMITED_FPS = 999;
    private static final int BASE_FPS = 30;
    private static final int[] SPEED_MULTIPLIERS = {1, 2, 5, 10, 20, 50, 100};

    private final SettingsManager settingsManager;
    private final SimulationEngine engine;
    private final SimulationCanvas canvas;
    private final ControlPanel controlPanel;
    private final CustomHeaderPanel headerPanel;
    private final InspectorPanel inspectorPanel;
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private SettingsOverlay settingsOverlay;
    private Microbe selectedMicrobe = null;

    private int windowWidth;
    private int windowHeight;
    private int canvasHeight;
    private int targetFps;
    private int currentSpeedIndex = 0;

    private long lastFpsTime = System.nanoTime();
    private int frameCount = 0;
    private int currentFps = 0;

    public BioLabSimulatorApp() {
        super("Bio-Lab Evolution Simulator");

        // Initialize settings manager and load settings
        settingsManager = new SettingsManager();
        windowWidth = settingsManager.getWindowWidth();
        windowHeight = settingsManager.getWindowHeight();
        targetFps = BASE_FPS * SPEED_MULTIPLIERS[currentSpeedIndex];
        // Calculate canvas height (leave room for control panel)
        canvasHeight = windowHeight - TOTAL_UI_HEIGHT;

        // Initialize simulation engine with fixed 10k x 10k world
        int worldWidth = 10000;
        int worldHeight = 10000;
        engine = new SimulationEngine(worldWidth, worldHeight, INITIAL_POPULATION);

        // Setup UI components
        canvas = new SimulationCanvas(worldWidth, worldHeight);
        controlPanel = new ControlPanel();
        headerPanel = new CustomHeaderPanel();
        inspectorPanel = new InspectorPanel();

        setupUI();
        setupShutdownHook();
        
        // Apply initial display mode
        applyDisplayMode();

        // Start simulation loop in a separate thread
        startSimulationLoop();
    }

    private void setupUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setUndecorated(true); // Remove system window decorations
        setSize(windowWidth, windowHeight);
        setResizable(true); // Resizing aktivieren
        setLayout(new BorderLayout());

        // Set dark background
        getContentPane().setBackground(new Color(18, 18, 18));

        // Add custom header at top
        add(headerPanel, BorderLayout.NORTH);

        // Add canvas (where microbes are rendered)
        add(canvas, BorderLayout.CENTER);

        // Add control panel at bottom
        add(controlPanel, BorderLayout.SOUTH);


        // Add inspector panel as overlay on top of canvas
        // Do NOT add to layered pane yet - we'll position it after the window is shown
        inspectorPanel.setVisible(true);

        setLocationRelativeTo(null);

        // Add component listener to reposition inspector panel on resize
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent evt) {
                positionInspectorPanel();
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent evt) {
                positionInspectorPanel();
            }
        });
    }

    private void positionInspectorPanel() {
        // Calculate inspector panel position (top-right corner with margin)
        int panelWidth = 320;
        int rightMargin = 20; // Margin zur rechten Seite
        int topMargin = CUSTOM_HEADER_HEIGHT + rightMargin; // Gleicher Abstand wie rechts, aber unter dem Header

        int panelHeight = Math.min(windowHeight - TOTAL_UI_HEIGHT - topMargin - rightMargin, 700);
        int panelX = getContentPane().getWidth() - panelWidth - rightMargin;
        int panelY = topMargin;

        // Remove and re-add to ensure it's on top
        getLayeredPane().remove(inspectorPanel);
        getLayeredPane().add(inspectorPanel, JLayeredPane.PALETTE_LAYER);
        inspectorPanel.setBounds(panelX, panelY, panelWidth, panelHeight);
        inspectorPanel.revalidate();
        inspectorPanel.repaint();
    }
    
    /**
     * Custom header panel with window controls and dragging functionality.
     */
    private class CustomHeaderPanel extends JPanel {
        private Point initialClick;

        public CustomHeaderPanel() {
            setPreferredSize(new Dimension(windowWidth, CUSTOM_HEADER_HEIGHT));
            setBackground(new Color(20, 20, 28)); // Wie Control Panel
            setLayout(new BorderLayout());
            // Border wie Control Panel - mehrschichtig unten
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(0, 255, 255, 100)),
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0, 255, 255))
            ));

            // Left section: Settings button
            JPanel leftPanel = new JPanel(new GridBagLayout());
            leftPanel.setOpaque(false);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(0, 10, 0, 0);

            ModernButton settingsButton = new ModernButton("", ModernButton.ButtonIcon.GEAR);
            settingsButton.setPreferredSize(new Dimension(65, 35));
            settingsButton.addActionListener(e -> showSettingsOverlay());
            leftPanel.add(settingsButton, gbc);

            add(leftPanel, BorderLayout.WEST);

            // Center section: Title (draggable area)
            JLabel titleLabel = new JLabel("BIO-LAB EVOLUTION SIMULATOR");
            titleLabel.setForeground(new Color(0, 255, 255));
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14)); // Größere Schrift
            titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
            add(titleLabel, BorderLayout.CENTER);

            // Right section: Close button
            JPanel rightPanel = new JPanel(new GridBagLayout());
            rightPanel.setOpaque(false);

            GridBagConstraints gbcRight = new GridBagConstraints();
            gbcRight.anchor = GridBagConstraints.EAST;
            gbcRight.insets = new Insets(0, 0, 0, 10);

            ModernButton closeButton = new ModernButton("", ModernButton.ButtonIcon.CLOSE);
            closeButton.setPreferredSize(new Dimension(50, 35));
            closeButton.addActionListener(e -> {
                running = false;
                engine.shutdown();
                System.exit(0);
            });
            rightPanel.add(closeButton, gbcRight);

            add(rightPanel, BorderLayout.EAST);

            // Make the header draggable
            addMouseListener(new java.awt.event.MouseAdapter() {
                public void mousePressed(java.awt.event.MouseEvent e) {
                    initialClick = e.getPoint();
                }
            });

            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    // Get location of Window
                    int thisX = BioLabSimulatorApp.this.getLocation().x;
                    int thisY = BioLabSimulatorApp.this.getLocation().y;

                    // Determine how much the mouse moved since the initial click
                    int xMoved = e.getX() - initialClick.x;
                    int yMoved = e.getY() - initialClick.y;

                    // Move window to this position
                    int X = thisX + xMoved;
                    int Y = thisY + yMoved;
                    BioLabSimulatorApp.this.setLocation(X, Y);
                }
            });

            // Make title label also draggable
            titleLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mousePressed(java.awt.event.MouseEvent e) {
                    initialClick = SwingUtilities.convertPoint(titleLabel, e.getPoint(), CustomHeaderPanel.this);
                }
            });

            titleLabel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    Point convertedPoint = SwingUtilities.convertPoint(titleLabel, e.getPoint(), CustomHeaderPanel.this);
                    int thisX = BioLabSimulatorApp.this.getLocation().x;
                    int thisY = BioLabSimulatorApp.this.getLocation().y;

                    int xMoved = convertedPoint.x - initialClick.x;
                    int yMoved = convertedPoint.y - initialClick.y;

                    int X = thisX + xMoved;
                    int Y = thisY + yMoved;
                    BioLabSimulatorApp.this.setLocation(X, Y);
                }
            });
        }
    }
    
    private void applyDisplayMode() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        
        if (settingsManager.isFullscreen() && gd.isFullScreenSupported()) {
            dispose();
            setUndecorated(true);
            gd.setFullScreenWindow(this);
            setVisible(true);
            LOGGER.info("Switched to fullscreen mode");
        } else {
            if (gd.getFullScreenWindow() == this) {
                gd.setFullScreenWindow(null);
            }
            dispose();
            setUndecorated(false);
            setSize(windowWidth, windowHeight);
            setLocationRelativeTo(null);
            setVisible(true);
            LOGGER.info("Switched to windowed mode: " + windowWidth + "x" + windowHeight);
        }
    }
    
    private void showSettingsOverlay() {
        if (settingsOverlay != null) {
            return; // Already showing
        }
        
        // Pause simulation
        paused = true;
        
        // Create and show overlay
        settingsOverlay = new SettingsOverlay(settingsManager, this::closeSettingsOverlay);
        
        // Add overlay on top of everything
        getLayeredPane().add(settingsOverlay, JLayeredPane.POPUP_LAYER);
        settingsOverlay.setBounds(0, 0, getWidth(), getHeight());
        settingsOverlay.setVisible(true);
        settingsOverlay.requestFocusInWindow();
        
        // Force repaint to make sure overlay is visible immediately even if loop is paused
        revalidate();
        repaint();

        LOGGER.info("Settings overlay opened");
    }
    
    private void closeSettingsOverlay() {
        if (settingsOverlay == null) {
            return;
        }
        
        // Remove overlay
        getLayeredPane().remove(settingsOverlay);
        settingsOverlay = null;
        
        // Check if settings changed and need to apply
        boolean settingsChanged = false;
        boolean fullscreenChanged = false;

        if (windowWidth != settingsManager.getWindowWidth() ||
            windowHeight != settingsManager.getWindowHeight()) {
            windowWidth = settingsManager.getWindowWidth();
            windowHeight = settingsManager.getWindowHeight();
            canvasHeight = windowHeight - TOTAL_UI_HEIGHT;
            settingsChanged = true;
        }

        // Check if fullscreen mode changed
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        boolean currentlyFullscreen = (gd.getFullScreenWindow() == this);
        boolean wantsFullscreen = settingsManager.isFullscreen();
        if (currentlyFullscreen != wantsFullscreen) {
            fullscreenChanged = true;
            settingsChanged = true;
        }

        // Apply display mode changes if needed
        if (settingsChanged) {
            applyDisplayMode();
            if (!fullscreenChanged) {
                // Only update preferred sizes if not switching fullscreen
                canvas.setPreferredSize(new Dimension(windowWidth, canvasHeight));
                controlPanel.setPreferredSize(new Dimension(windowWidth, CONTROL_PANEL_HEIGHT));
            }
        }
        
        // Resume simulation
        paused = false;
        
        // Force repaint to remove overlay logic visually
        revalidate();
        repaint();
        LOGGER.info("Settings overlay closed");
    }

    private void setupShutdownHook() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                running = false;
                engine.shutdown();
            }
        });
    }

    /**
     * Starts the main simulation loop in a dedicated thread.
     * This keeps the UI responsive while simulation runs.
     */
    private void startSimulationLoop() {
        Thread simulationThread = new Thread(() -> {
            while (running) {
                long startTime = System.nanoTime();
                
                // Calculate frame time, handling unlimited FPS case
                long frameTime;
                if (targetFps >= UNLIMITED_FPS) {
                    frameTime = 0; // No frame limiting for unlimited FPS
                } else {
                    frameTime = 1_000_000_000 / targetFps; // nanoseconds per frame
                }

                // Only update if not paused
                if (!paused) {
                    // ===== MULTITHREADING: Engine.update() uses ExecutorService =====
                    // This is where thousands of microbes are updated concurrently
                    engine.update();

                    // Check if selected microbe is dead and hide inspector
                    if (selectedMicrobe != null && selectedMicrobe.isDead()) {
                        selectedMicrobe.setSelected(false);
                        selectedMicrobe = null;
                        inspectorPanel.hidePanel();
                        getLayeredPane().repaint();
                    }

                    // Repaint canvas
                    canvas.repaint();

                    // Update FPS counter
                    updateFPS();
                }

                // Sleep to maintain target FPS (skip for unlimited FPS)
                if (paused) {
                    // Save CPU cycles when paused
                    try {
                        //noinspection BusyWait
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else if (frameTime > 0) {
                    long elapsed = System.nanoTime() - startTime;
                    long sleepTime = frameTime - elapsed;
                    if (sleepTime > 0) {
                        try {
                            //noinspection BusyWait
                            Thread.sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                        } catch (InterruptedException e) {
                            LOGGER.log(Level.INFO, "Simulation loop interrupted, shutting down...", e);
                            Thread.currentThread().interrupt(); // Restore interrupt status
                            break;
                        }
                    }
                }
            }
        }, "SimulationLoop");

        simulationThread.start();
    }

    private void updateFPS() {
        frameCount++;
        long currentTime = System.nanoTime();
        if (currentTime - lastFpsTime >= 1_000_000_000) { // 1 second
            currentFps = frameCount;
            frameCount = 0;
            lastFpsTime = currentTime;
            controlPanel.updateStats(engine.getPopulationCount(), currentFps);
        }
    }

    /**
     * Canvas for rendering the simulation.
     */
    private class SimulationCanvas extends JPanel {
        private final int worldWidth;
        private final int worldHeight;

        // Camera system
        private double cameraX;
        private double cameraY;
        private double zoom = 1.0;
        private static final double MIN_ZOOM = 0.05;
        private static final double MAX_ZOOM = 3.0;

        // Mouse drag state
        private int lastMouseX;
        private int lastMouseY;
        private boolean isDragging = false;

        public SimulationCanvas(int worldWidth, int worldHeight) {
            this.worldWidth = worldWidth;
            this.worldHeight = worldHeight;
            setPreferredSize(new Dimension(windowWidth, canvasHeight));
            setBackground(new Color(18, 18, 18)); // Dark background - Sci-Fi style

            // Center camera initially
            cameraX = worldWidth / 2.0;
            cameraY = worldHeight / 2.0;

            // Setup mouse listeners for camera control
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                        // Check if clicking on a microbe for selection
                        Microbe clicked = findMicrobeAtScreenPos(e.getX(), e.getY());
                        if (clicked != null) {
                            // Deselect previous
                            if (selectedMicrobe != null) {
                                selectedMicrobe.setSelected(false);
                            }
                            // Select new
                            selectedMicrobe = clicked;
                            selectedMicrobe.setSelected(true);
                            inspectorPanel.setSelectedMicrobe(selectedMicrobe);
                            inspectorPanel.showPanel();
                        } else {
                            // Deselect if clicking on empty space
                            if (selectedMicrobe != null) {
                                selectedMicrobe.setSelected(false);
                                selectedMicrobe = null;
                            }
                            // Clear inspector and force repaint to remove ghost border
                            inspectorPanel.hidePanel();
                            getLayeredPane().repaint();

                            // Start dragging camera
                            lastMouseX = e.getX();
                            lastMouseY = e.getY();
                            isDragging = true;
                            setCursor(new Cursor(Cursor.MOVE_CURSOR));
                        }
                    }
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                        isDragging = false;
                        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    }
                }
            });

            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (isDragging) {
                        int dx = e.getX() - lastMouseX;
                        int dy = e.getY() - lastMouseY;

                        // Move camera (inverse of drag direction)
                        cameraX -= dx / zoom;
                        cameraY -= dy / zoom;

                        // Clamp camera to world bounds
                        clampCamera();

                        lastMouseX = e.getX();
                        lastMouseY = e.getY();
                        repaint();
                    }
                }
            });

            addMouseWheelListener(e -> {
                // Zoom in/out
                if (e.getWheelRotation() < 0) {
                    zoom *= 1.1; // Zoom in
                } else {
                    zoom /= 1.1; // Zoom out
                }

                // Clamp zoom
                zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));

                // Prevent zooming out beyond world bounds
                double minZoomForWorld = Math.max(
                    (double) getWidth() / worldWidth,
                    (double) getHeight() / worldHeight
                );
                zoom = Math.max(minZoomForWorld, zoom);

                clampCamera();
                repaint();
            });
        }

        private void clampCamera() {
            // Calculate visible area
            double visibleWidth = getWidth() / zoom;
            double visibleHeight = getHeight() / zoom;

            // Clamp camera so we don't go outside world bounds
            cameraX = Math.max(visibleWidth / 2, Math.min(worldWidth - visibleWidth / 2, cameraX));
            cameraY = Math.max(visibleHeight / 2, Math.min(worldHeight - visibleHeight / 2, cameraY));
        }

        /**
         * Finds a microbe at the given screen coordinates.
         * Converts screen position to world position and checks collision.
         */
        private Microbe findMicrobeAtScreenPos(int screenX, int screenY) {
            // Convert screen to world coordinates
            double worldX = (screenX - getWidth() / 2.0) / zoom + cameraX;
            double worldY = (screenY - getHeight() / 2.0) / zoom + cameraY;

            // Find closest microbe within click radius
            List<Microbe> microbes = engine.getMicrobes();
            for (Microbe microbe : microbes) {
                if (microbe.contains(worldX, worldY)) {
                    return microbe;
                }
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Enable antialiasing for smoother circles
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Save original transform
            java.awt.geom.AffineTransform originalTransform = g2d.getTransform();

            // Apply camera transformation
            // 1. Translate to center of screen
            g2d.translate(getWidth() / 2.0, getHeight() / 2.0);
            // 2. Apply zoom
            g2d.scale(zoom, zoom);
            // 3. Translate to camera position (negative to move world)
            g2d.translate(-cameraX, -cameraY);

            // Draw world boundary with sci-fi grid pattern
            g2d.setColor(new Color(15, 15, 20));
            g2d.fillRect(0, 0, worldWidth, worldHeight);

            // Draw grid pattern (only visible portion for performance)
            g2d.setColor(new Color(25, 25, 35, 100));
            int gridSize = 100;
            double visibleX1 = cameraX - (getWidth() / (2 * zoom));
            double visibleX2 = cameraX + (getWidth() / (2 * zoom));
            double visibleY1 = cameraY - (getHeight() / (2 * zoom));
            double visibleY2 = cameraY + (getHeight() / (2 * zoom));

            int startGridX = ((int) visibleX1 / gridSize) * gridSize;
            int endGridX = ((int) visibleX2 / gridSize + 1) * gridSize;
            int startGridY = ((int) visibleY1 / gridSize) * gridSize;
            int endGridY = ((int) visibleY2 / gridSize + 1) * gridSize;

            for (int gx = startGridX; gx <= endGridX; gx += gridSize) {
                g2d.drawLine(gx, (int) visibleY1, gx, (int) visibleY2);
            }
            for (int gy = startGridY; gy <= endGridY; gy += gridSize) {
                g2d.drawLine((int) visibleX1, gy, (int) visibleX2, gy);
            }

            // Draw world border
            g2d.setColor(new Color(0, 255, 255, 150)); // Cyan border
            g2d.setStroke(new BasicStroke(3));
            g2d.drawRect(0, 0, worldWidth, worldHeight);
            g2d.setStroke(new BasicStroke(1));

            // ===== RENDER FOOD PELLETS =====
            List<FoodPellet> foodPellets = engine.getFoodPellets();
            for (FoodPellet food : foodPellets) {
                if (food.isConsumed()) continue;

                // Multi-layer glow effect for food
                for (int i = 3; i > 0; i--) {
                    g2d.setColor(new Color(50, 255, 100, 15 + (i * 10)));
                    int glowSize = food.getSize() + (i * 3);
                    g2d.fillOval(
                        (int) food.getX() - glowSize / 2,
                        (int) food.getY() - glowSize / 2,
                        glowSize, glowSize
                    );
                }

                // Draw food pellet with bright center
                g2d.setColor(new Color(150, 255, 150, 200));
                int x = (int) food.getX() - food.getSize() / 2;
                int y = (int) food.getY() - food.getSize() / 2;
                g2d.fillOval(x, y, food.getSize(), food.getSize());

                g2d.setColor(food.getColor());
                g2d.fillOval(x + 1, y + 1, food.getSize() - 2, food.getSize() - 2);

                // Bright center point
                g2d.setColor(new Color(200, 255, 200));
                g2d.fillOval(x + 2, y + 2, food.getSize() - 4, food.getSize() - 4);
            }

            // ===== RENDER MICROBES WITH GLOW =====
            List<Microbe> microbes = engine.getMicrobes();
            for (Microbe microbe : microbes) {
                Color microbeColor = microbe.getColor();
                int size = microbe.getSize();
                int x = (int) microbe.getX() - size / 2;
                int y = (int) microbe.getY() - size / 2;

                // Draw multi-layer glow effect (outer to inner)
                for (int i = 3; i > 0; i--) {
                    int alpha = 20 + (i * 15);
                    g2d.setColor(new Color(
                        microbeColor.getRed(),
                        microbeColor.getGreen(),
                        microbeColor.getBlue(),
                        alpha
                    ));
                    int glowSize = size + (i * 4);
                    g2d.fillOval(x - i * 2, y - i * 2, glowSize, glowSize);
                }

                // Draw main microbe with slight inner glow
                g2d.setColor(new Color(
                    Math.min(255, microbeColor.getRed() + 40),
                    Math.min(255, microbeColor.getGreen() + 40),
                    Math.min(255, microbeColor.getBlue() + 40),
                    220
                ));
                g2d.fillOval(x, y, size, size);

                g2d.setColor(microbeColor);
                g2d.fillOval(x + 1, y + 1, size - 2, size - 2);

                // Draw selection indicator with pulsing effect
                if (microbe.isSelected()) {
                    g2d.setColor(new Color(0, 255, 255, 100)); // Cyan glow
                    g2d.setStroke(new BasicStroke(3));
                    g2d.drawOval(x - 5, y - 5, size + 10, size + 10);

                    g2d.setColor(new Color(0, 255, 255)); // Solid cyan
                    g2d.setStroke(new BasicStroke(2));
                    g2d.drawOval(x - 4, y - 4, size + 8, size + 8);

                    g2d.setStroke(new BasicStroke(1));
                    g2d.drawOval(x - 3, y - 3, size + 6, size + 6);
                }
            }

            // Restore transform for UI elements
            g2d.setTransform(originalTransform);
        }

        private void drawLegend(Graphics2D g2d) {
            int startX = windowWidth - 200;
            int startY = 10;

            g2d.setColor(Color.WHITE);
            g2d.drawString("Gene Color Coding:", startX, startY);

            g2d.setColor(Color.RED);
            g2d.fillOval(startX, startY + 10, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.drawString("= Heat Resistance", startX + 15, startY + 20);

            g2d.setColor(Color.GREEN);
            g2d.fillOval(startX, startY + 30, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.drawString("= Toxin Resistance", startX + 15, startY + 40);
        }
    }

    /**
     * Control panel with user controls (sliders and stats).
     */
    private class ControlPanel extends JPanel {
        private final JSlider temperatureSlider;
        private final JSlider toxicitySlider;
        private final JSlider foodSpawnSlider;
        private final JLabel statsLabel;
        private final ModernButton speedButton;

        public ControlPanel() {
            setLayout(new GridBagLayout());
            setPreferredSize(new Dimension(windowWidth, CONTROL_PANEL_HEIGHT));
            setBackground(new Color(20, 20, 28));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(3, 0, 0, 0, new Color(0, 255, 255, 100)),
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0, 255, 255))
            ));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 15, 5, 15);
            gbc.fill = GridBagConstraints.BOTH;

            // --- Left Section: Sliders ---
            JPanel slidersPanel = new JPanel(new GridLayout(3, 1, 0, 5));
            slidersPanel.setOpaque(false);

            // Temperature slider
            JPanel tempPanel = createSliderPanel("Temperature", new Color(255, 100, 100));
            temperatureSlider = createStyledSlider(30);
            temperatureSlider.addChangeListener(e -> {
                double temp = temperatureSlider.getValue() / 100.0;
                engine.getEnvironment().setTemperature(temp);
            });
            tempPanel.add(temperatureSlider, BorderLayout.CENTER);
            slidersPanel.add(tempPanel);

            // Toxicity slider
            JPanel toxPanel = createSliderPanel("Toxicity", new Color(100, 255, 100));
            toxicitySlider = createStyledSlider(30);
            toxicitySlider.addChangeListener(e -> {
                double tox = toxicitySlider.getValue() / 100.0;
                engine.getEnvironment().setToxicity(tox);
            });
            toxPanel.add(toxicitySlider, BorderLayout.CENTER);
            slidersPanel.add(toxPanel);

            // Food Spawn Rate slider (NEW)
            JPanel foodPanel = createSliderPanel("Food Spawn Rate", new Color(50, 255, 100));
            foodSpawnSlider = createStyledSlider(30);
            foodSpawnSlider.addChangeListener(e -> {
                double rate = foodSpawnSlider.getValue() / 100.0;
                engine.setFoodSpawnRate(rate);
            });
            foodPanel.add(foodSpawnSlider, BorderLayout.CENTER);
            slidersPanel.add(foodPanel);

            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 0.4;
            gbc.weighty = 1.0;
            add(slidersPanel, gbc);

            // --- Center Section: Stats ---
            statsLabel = new JLabel("<html><center>POPULATION<br><font size='6' color='#00FFFF'>0</font></center></html>");
            statsLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            statsLabel.setForeground(new Color(0, 255, 255));
            statsLabel.setHorizontalAlignment(SwingConstants.CENTER);

            gbc.gridx = 1;
            gbc.weightx = 0.2;
            add(statsLabel, gbc);

            // --- Right Section: Speed Control ---
            JPanel controlsPanel = new JPanel(new GridBagLayout());
            controlsPanel.setOpaque(false);

            speedButton = new ModernButton("1x", ModernButton.ButtonIcon.SPEED_UP);
            speedButton.setPreferredSize(new Dimension(140, 50)); // Schmaler: 140 statt 200

            speedButton.addActionListener(e -> {
                currentSpeedIndex = (currentSpeedIndex + 1) % SPEED_MULTIPLIERS.length;
                int multiplier = SPEED_MULTIPLIERS[currentSpeedIndex];
                targetFps = BASE_FPS * multiplier;
                speedButton.setDisplayText(multiplier + "x");
            });

            GridBagConstraints gbcSpeed = new GridBagConstraints();
            gbcSpeed.anchor = GridBagConstraints.EAST;
            gbcSpeed.insets = new Insets(0, 0, 0, 20);
            controlsPanel.add(speedButton, gbcSpeed);

            gbc.gridx = 2;
            gbc.weightx = 0.4;
            add(controlsPanel, gbc);
        }

        private JSlider createStyledSlider(int initialValue) {
            JSlider slider = new JSlider(0, 100, initialValue);
            slider.setOpaque(false);
            slider.setForeground(Color.WHITE);
            return slider;
        }

        private JPanel createSliderPanel(String title, Color titleColor) {
            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.setOpaque(false);

            // Create custom label with drawn triangle icon
            JPanel labelContainer = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Draw triangle icon
                    g2d.setColor(titleColor);
                    int[] xPoints = {5, 13, 5};
                    int[] yPoints = {3, 10, 17};
                    g2d.fillPolygon(xPoints, yPoints, 3);
                }
            };
            labelContainer.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
            labelContainer.setOpaque(false);

            JLabel label = new JLabel("   " + title); // Spacing for triangle
            label.setForeground(titleColor);
            label.setFont(new Font("Segoe UI", Font.BOLD, 13));
            label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
            labelContainer.add(label);

            panel.add(labelContainer, BorderLayout.NORTH);
            return panel;
        }

        public void updateStats(int population, int fps) {
            statsLabel.setText(String.format("<html><center>POPULATION<br><font size='6' color='#00FFFF'>%,d</font><br><font size='3' color='#00CCCC'>TPS: %d</font></center></html>", population, fps));
        }
    }

    public static void main(String[] args) {
        // Set system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not set system look and feel, using default", e);
        }

        // Launch on EDT (Event Dispatch Thread)
        SwingUtilities.invokeLater(() -> {
            try {
                BioLabSimulatorApp app = new BioLabSimulatorApp();
                app.setVisible(true);
                LOGGER.info("Bio-Lab Simulator started successfully");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to start Bio-Lab Simulator", e);
                // Show user-friendly error dialog
                JOptionPane.showMessageDialog(
                    null,
                    "Failed to start the Bio-Lab Simulator.\nPlease check the logs for details.\n\nError: " + e.getMessage(),
                    "Startup Error",
                    JOptionPane.ERROR_MESSAGE
                );
                System.exit(1);
            }
        });
    }
}
