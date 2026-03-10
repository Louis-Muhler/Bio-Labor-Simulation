package com.biolab;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Alternative entry point for a controlled debug / sandbox environment.
 *
 * <h3>Sandbox parameters</h3>
 * <ul>
 *   <li>World size: 800 × 800 world units (tiny – easy to observe)</li>
 *   <li>Initial population: 0 (all microbes are spawned manually below)</li>
 *   <li>Microbes: exactly 2 forced Carnivores + 8 forced Herbivores</li>
 *   <li>{@link SimulationEngine#DEBUG_MODE} is {@code true} by default</li>
 * </ul>
 *
 * <p>Use this launcher to verify Predator/Prey AI behaviour without the chaos
 * of the full simulation.  Press <b>D</b> at any time to toggle the
 * Developer Vision overlay (target lines, ID labels, vision radii).</p>
 */
public class DebugSandboxApp {

    private static final Logger LOGGER = Logger.getLogger(DebugSandboxApp.class.getName());

    /**
     * Sandbox world dimensions – intentionally tiny for easy observation.
     */
    private static final int SANDBOX_WORLD_SIZE = 800;

    /**
     * Microbe counts to spawn manually.
     */
    private static final int CARNIVORE_COUNT = 2;
    private static final int HERBIVORE_COUNT = 8;

    /**
     * Entry point.  Bootstraps the Swing UI on the EDT with debug mode enabled.
     */
    public static void main(String[] args) {
        // Enable FlatLaf native window decorations
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        UIManager.put("RootPane.background", new Color(18, 18, 18));
        UIManager.put("TitlePane.background", new Color(18, 18, 18));
        UIManager.put("TitlePane.inactiveBackground", new Color(18, 18, 18));
        UIManager.put("TitlePane.foreground", new Color(200, 200, 200));
        UIManager.put("TitlePane.inactiveForeground", new Color(130, 130, 130));
        UIManager.put("TitlePane.unifiedBackground", true);

        FlatDarkLaf.setup();

        // Activate debug mode BEFORE the engine starts so the first frame is already annotated
        SimulationEngine.DEBUG_MODE = true;

        SwingUtilities.invokeLater(() -> {
            try {
                launchSandbox();
                LOGGER.info("Debug Sandbox started successfully");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to start Debug Sandbox", e);
                JOptionPane.showMessageDialog(null,
                        "Failed to start sandbox: " + e.getMessage(),
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Sandbox setup
    // -------------------------------------------------------------------------

    private static void launchSandbox() {
        // 1. Build an engine with a tiny world and zero initial population
        SimulationEngine engine = new SimulationEngine(
                SANDBOX_WORLD_SIZE, SANDBOX_WORLD_SIZE, /* initialPopulation = */ 0);

        // 2. Manually spawn forced microbes
        spawnSandboxMicrobes(engine);

        // 3. Build and show the window
        buildWindow(engine);
    }

    /**
     * Spawns {@value #CARNIVORE_COUNT} carnivores and {@value #HERBIVORE_COUNT} herbivores
     * spread evenly across the sandbox world using a grid with random jitter.
     */
    private static void spawnSandboxMicrobes(SimulationEngine engine) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int total = CARNIVORE_COUNT + HERBIVORE_COUNT;

        for (int i = 0; i < total; i++) {
            // Distribute starting positions in a grid-like pattern with jitter
            double col = (i % 5) + 1.0;   // 5 columns (cast to double before arithmetic)
            double row = ((double) i / 5) + 1.0;   // rows as needed
            double cellW = SANDBOX_WORLD_SIZE / 6.0;
            double cellH = SANDBOX_WORLD_SIZE / 4.0;
            double x = col * cellW + (rng.nextDouble() - 0.5) * cellW * 0.5;
            double y = row * cellH + (rng.nextDouble() - 0.5) * cellH * 0.5;

            // Clamp to world bounds
            x = Math.max(20, Math.min(SANDBOX_WORLD_SIZE - 20, x));
            y = Math.max(20, Math.min(SANDBOX_WORLD_SIZE - 20, y));

            // First CARNIVORE_COUNT slots → carnivores (diet = 1.0), rest → herbivores (diet = 0.0)
            double forcedDiet = (i < CARNIVORE_COUNT) ? 1.0 : 0.0;
            engine.spawnMicrobe(new Microbe(x, y, forcedDiet));
        }

        LOGGER.info(String.format("Sandbox: spawned %d carnivores + %d herbivores",
                CARNIVORE_COUNT, HERBIVORE_COUNT));
    }

    /**
     * Builds and shows the application window using the provided engine.
     * A minimal {@link SimulationCanvas.SelectionListener} wires selection
     * highlights and follow-camera without needing the full inspector panel.
     */
    private static void buildWindow(SimulationEngine engine) {
        final int WIN_W = 900;
        final int WIN_H = 700;

        JFrame frame = new JFrame("Bio-Lab  ⚠ DEBUG SANDBOX  [D = toggle overlay]");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setBackground(new Color(18, 18, 18));
        frame.setMinimumSize(new Dimension(600, 500));
        frame.setSize(WIN_W, WIN_H);
        frame.setResizable(true);

        // Use an array so the anonymous listener can capture the canvas reference
        // after it has been constructed.
        final SimulationCanvas[] ref = new SimulationCanvas[1];

        SimulationCanvas canvas = new SimulationCanvas(
                SANDBOX_WORLD_SIZE, SANDBOX_WORLD_SIZE,
                WIN_W, WIN_H,
                engine,
                new SimulationCanvas.SelectionListener() {
                    private Microbe selected;

                    @Override
                    public void onMicrobeSelected(Microbe microbe) {
                        if (selected != null) selected.setSelected(false);
                        selected = microbe;
                        microbe.setSelected(true);
                        ref[0].startFollowing(microbe);
                    }

                    @Override
                    public void onSelectionCleared() {
                        if (selected != null) selected.setSelected(false);
                        selected = null;
                        ref[0].stopFollowing();
                    }
                });

        ref[0] = canvas;

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(new Color(18, 18, 18));
        content.add(canvas, BorderLayout.CENTER);
        frame.setContentPane(content);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Clamp zoom after the window is visible so the world fits the viewport
        SwingUtilities.invokeLater(canvas::clampZoomAndCamera);

        // Start simulation loop (overlayManager = null → no population label update)
        SimulationLoopController loop = new SimulationLoopController(
                engine, canvas, /* overlayManager = */ null, () -> {
        });
        loop.start();

        // Shutdown hook – stop loop and engine gracefully on window close
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                loop.stop();
                if (engine.isRunning()) engine.shutdown();
            }
        });
    }
}

