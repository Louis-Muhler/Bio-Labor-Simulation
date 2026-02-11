package com.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Main application window for the Bio-Lab Evolution Simulator.
 */
public class BioLabSimulatorApp extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(BioLabSimulatorApp.class.getName());

    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 800;
    private static final int CANVAS_HEIGHT = 650;
    private static final int INITIAL_POPULATION = 1500;
    private static final int TARGET_FPS = 60;

    private final SimulationEngine engine;
    private final SimulationCanvas canvas;
    private final ControlPanel controlPanel;
    private volatile boolean running = true;

    // FPS tracking
    private long lastFpsTime = System.nanoTime();
    private int frameCount = 0;
    private int currentFps = 0;

    public BioLabSimulatorApp() {
        super("Bio-Lab Evolution Simulator");

        // Initialize simulation engine
        engine = new SimulationEngine(WINDOW_WIDTH, CANVAS_HEIGHT, INITIAL_POPULATION);

        // Setup UI components
        canvas = new SimulationCanvas();
        controlPanel = new ControlPanel();

        setupUI();
        setupShutdownHook();

        // Start simulation loop in a separate thread
        startSimulationLoop();
    }

    private void setupUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setResizable(false);
        setLayout(new BorderLayout());

        // Add canvas (where microbes are rendered)
        add(canvas, BorderLayout.CENTER);

        // Add control panel at bottom
        add(controlPanel, BorderLayout.SOUTH);

        setLocationRelativeTo(null);
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
            long frameTime = 1_000_000_000 / TARGET_FPS; // nanoseconds per frame

            while (running) {
                long startTime = System.nanoTime();

                // ===== MULTITHREADING: Engine.update() uses ExecutorService =====
                // This is where thousands of microbes are updated concurrently
                engine.update();

                // Repaint canvas
                canvas.repaint();

                // Update FPS counter
                updateFPS();

                // Sleep to maintain target FPS
                long elapsed = System.nanoTime() - startTime;
                long sleepTime = frameTime - elapsed;
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                    } catch (InterruptedException e) {
                        LOGGER.log(Level.INFO, "Simulation loop interrupted, shutting down...", e);
                        Thread.currentThread().interrupt(); // Restore interrupt status
                        break;
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
            setPreferredSize(new Dimension(WINDOW_WIDTH, CANVAS_HEIGHT));
            setBackground(new Color(20, 20, 30)); // Dark background like a petri dish
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Enable anti-aliasing for smoother circles
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
            int startX = WINDOW_WIDTH - 200;
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

        public ControlPanel() {
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(WINDOW_WIDTH, 120));
            setBackground(new Color(40, 40, 50));

            // Sliders panel
            JPanel slidersPanel = new JPanel(new GridLayout(2, 1, 5, 5));
            slidersPanel.setBackground(new Color(40, 40, 50));

            // Temperature slider
            JPanel tempPanel = createSliderPanel("Temperature (Cold ← → Hot)");
            temperatureSlider = new JSlider(0, 100, 30);
            temperatureSlider.setMajorTickSpacing(25);
            temperatureSlider.setPaintTicks(true);
            temperatureSlider.setPaintLabels(true);
            temperatureSlider.setBackground(new Color(40, 40, 50));
            temperatureSlider.setForeground(Color.WHITE);
            temperatureSlider.addChangeListener(e -> {
                double temp = temperatureSlider.getValue() / 100.0;
                engine.getEnvironment().setTemperature(temp);
            });
            tempPanel.add(temperatureSlider);
            slidersPanel.add(tempPanel);

            // Toxicity slider
            JPanel toxPanel = createSliderPanel("Toxicity (Clean ← → Poisonous)");
            toxicitySlider = new JSlider(0, 100, 30);
            toxicitySlider.setMajorTickSpacing(25);
            toxicitySlider.setPaintTicks(true);
            toxicitySlider.setPaintLabels(true);
            toxicitySlider.setBackground(new Color(40, 40, 50));
            toxicitySlider.setForeground(Color.WHITE);
            toxicitySlider.addChangeListener(e -> {
                double tox = toxicitySlider.getValue() / 100.0;
                engine.getEnvironment().setToxicity(tox);
            });
            toxPanel.add(toxicitySlider);
            slidersPanel.add(toxPanel);

            add(slidersPanel, BorderLayout.CENTER);

            // Stats label
            statsLabel = new JLabel("Population: 0 | FPS: 0", SwingConstants.CENTER);
            statsLabel.setFont(new Font("Arial", Font.BOLD, 16));
            statsLabel.setForeground(Color.CYAN);
            add(statsLabel, BorderLayout.SOUTH);
        }

        private JPanel createSliderPanel(String title) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(new Color(40, 40, 50));
            JLabel label = new JLabel(title, SwingConstants.CENTER);
            label.setForeground(Color.WHITE);
            label.setFont(new Font("Arial", Font.BOLD, 12));
            panel.add(label, BorderLayout.NORTH);
            return panel;
        }

        public void updateStats(int population, int fps) {
            statsLabel.setText(String.format("Population: %d | FPS: %d", population, fps));
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
