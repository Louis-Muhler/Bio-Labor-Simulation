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
 */
public class BioLabSimulatorApp extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(BioLabSimulatorApp.class.getName());

    private static final int INITIAL_POPULATION = 1500;
    private static final int CONTROL_PANEL_HEIGHT = 120;
    private static final int MENU_BAR_HEIGHT = 30;
    private static final int TOTAL_UI_HEIGHT = CONTROL_PANEL_HEIGHT + MENU_BAR_HEIGHT;
    private static final int UNLIMITED_FPS = 999;
    private static final int BASE_FPS = 30;
    private static final int[] SPEED_MULTIPLIERS = {1, 2, 5, 10, 20, 50, 100};

    private final SettingsManager settingsManager;
    private final SimulationEngine engine;
    private final SimulationCanvas canvas;
    private final ControlPanel controlPanel;
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private SettingsOverlay settingsOverlay;
    
    // Dynamic settings
    private int windowWidth;
    private int windowHeight;
    private int canvasHeight;
    private int targetFps;
    private int currentSpeedIndex = 0;

    // FPS tracking
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

        // Initialize simulation engine
        engine = new SimulationEngine(windowWidth, canvasHeight, INITIAL_POPULATION);

        // Setup UI components
        canvas = new SimulationCanvas();
        controlPanel = new ControlPanel();

        setupUI();
        setupShutdownHook();
        
        // Apply initial display mode
        applyDisplayMode();

        // Start simulation loop in a separate thread
        startSimulationLoop();
    }

    private void setupUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(windowWidth, windowHeight);
        setResizable(false);
        setLayout(new BorderLayout());

        // Add canvas (where microbes are rendered)
        add(canvas, BorderLayout.CENTER);

        // Add control panel at bottom
        add(controlPanel, BorderLayout.SOUTH);

        // Add menu bar with settings button
        setJMenuBar(createMenuBar());

        setLocationRelativeTo(null);
    }
    
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        JMenu fileMenu = new JMenu("File");
        
        JMenuItem settingsItem = new JMenuItem("Settings");
        settingsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, 
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        settingsItem.addActionListener(e -> showSettingsOverlay());
        fileMenu.add(settingsItem);
        
        fileMenu.addSeparator();
        
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            running = false;
            engine.shutdown();
            System.exit(0);
        });
        fileMenu.add(exitItem);
        
        menuBar.add(fileMenu);
        return menuBar;
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
        if (windowWidth != settingsManager.getWindowWidth() || 
            windowHeight != settingsManager.getWindowHeight()) {
            windowWidth = settingsManager.getWindowWidth();
            windowHeight = settingsManager.getWindowHeight();
            canvasHeight = windowHeight - TOTAL_UI_HEIGHT;
            settingsChanged = true;
        }
        // Removed targetFPS check as it is now handled by speed button

        // Apply display mode changes if needed
        if (settingsChanged) {
            applyDisplayMode();
            canvas.setPreferredSize(new Dimension(windowWidth, canvasHeight));
            controlPanel.setPreferredSize(new Dimension(windowWidth, CONTROL_PANEL_HEIGHT));
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
        public SimulationCanvas() {
            setPreferredSize(new Dimension(windowWidth, canvasHeight));
            setBackground(new Color(20, 20, 30)); // Dark background like a petri dish
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Enable antialiasing for smoother circles
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Get microbes snapshot (thread-safe)
            List<Microbe> microbes = engine.getMicrobes();

            // Render each microbe
            for (Microbe microbe : microbes) {
                g2d.setColor(microbe.getColor());
                int x = (int) microbe.getX() - microbe.getSize() / 2;
                int y = (int) microbe.getY() - microbe.getSize() / 2;
                g2d.fillOval(x, y, microbe.getSize(), microbe.getSize());
            }

            // Draw info overlay
            g2d.setColor(Color.WHITE);
            g2d.drawString("Microbes: " + microbes.size(), 10, 20);
            g2d.drawString("FPS: " + currentFps, 10, 40);

            // Draw color legend
            drawLegend(g2d);
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
        private final JLabel statsLabel;
        private final JButton speedButton;

        public ControlPanel() {
            setLayout(new GridBagLayout());
            setPreferredSize(new Dimension(windowWidth, CONTROL_PANEL_HEIGHT));
            setBackground(new Color(30, 30, 40));
            setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(60, 60, 70)));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 15, 5, 15);
            gbc.fill = GridBagConstraints.BOTH;

            // --- Left Section: Sliders ---
            JPanel slidersPanel = new JPanel(new GridLayout(2, 1, 0, 10));
            slidersPanel.setOpaque(false);

            // Temperature slider
            JPanel tempPanel = createSliderPanel("Temperature", new Color(255, 100, 100));
            temperatureSlider = createStyledSlider();
            temperatureSlider.addChangeListener(e -> {
                double temp = temperatureSlider.getValue() / 100.0;
                engine.getEnvironment().setTemperature(temp);
            });
            tempPanel.add(temperatureSlider, BorderLayout.CENTER);
            slidersPanel.add(tempPanel);

            // Toxicity slider
            JPanel toxPanel = createSliderPanel("Toxicity", new Color(100, 255, 100));
            toxicitySlider = createStyledSlider();
            toxicitySlider.addChangeListener(e -> {
                double tox = toxicitySlider.getValue() / 100.0;
                engine.getEnvironment().setToxicity(tox);
            });
            toxPanel.add(toxicitySlider, BorderLayout.CENTER);
            slidersPanel.add(toxPanel);

            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 0.4;
            gbc.weighty = 1.0;
            add(slidersPanel, gbc);

            // --- Center Section: Stats ---
            statsLabel = new JLabel("<html><center>Population<br><font size='6' color='#4dbecf'>0</font></center></html>");
            statsLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            statsLabel.setForeground(Color.LIGHT_GRAY);
            statsLabel.setHorizontalAlignment(SwingConstants.CENTER);

            gbc.gridx = 1;
            gbc.weightx = 0.2;
            add(statsLabel, gbc);

            // --- Right Section: Speed Control ---
            JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 25));
            controlsPanel.setOpaque(false);

            speedButton = new JButton("Speed: 1x");
            speedButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
            speedButton.setForeground(Color.WHITE);
            speedButton.setBackground(new Color(50, 50, 60));
            speedButton.setFocusPainted(false);
            speedButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 120), 2),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
            ));
            speedButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

            speedButton.addActionListener(e -> {
                currentSpeedIndex = (currentSpeedIndex + 1) % SPEED_MULTIPLIERS.length;
                int multiplier = SPEED_MULTIPLIERS[currentSpeedIndex];
                targetFps = BASE_FPS * multiplier;
                speedButton.setText("Speed: " + multiplier + "x");
            });

            speedButton.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseEntered(java.awt.event.MouseEvent evt) {
                    speedButton.setBackground(new Color(70, 70, 80));
                }
                public void mouseExited(java.awt.event.MouseEvent evt) {
                    speedButton.setBackground(new Color(50, 50, 60));
                }
            });

            controlsPanel.add(speedButton);

            gbc.gridx = 2;
            gbc.weightx = 0.4;
            add(controlsPanel, gbc);
        }

        private JSlider createStyledSlider() {
            JSlider slider = new JSlider(0, 100, 30);
            slider.setOpaque(false);
            slider.setForeground(Color.WHITE);
            return slider;
        }

        private JPanel createSliderPanel(String title, Color titleColor) {
            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.setOpaque(false);
            JLabel label = new JLabel(title);
            label.setForeground(titleColor);
            label.setFont(new Font("Segoe UI", Font.BOLD, 12));
            panel.add(label, BorderLayout.NORTH);
            return panel;
        }

        public void updateStats(int population, int fps) {
            statsLabel.setText(String.format("<html><center>Population<br><font size='6' color='#4dbecf'>%d</font><br><font size='3' color='gray'>TPS: %d</font></center></html>", population, fps));
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
