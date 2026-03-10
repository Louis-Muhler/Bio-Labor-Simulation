package com.biolab;

import javax.swing.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controls the simulation loop thread, frame timing, and speed management.
 */
public class SimulationLoopController {
    private static final Logger LOGGER = Logger.getLogger(SimulationLoopController.class.getName());

    private static final int UNLIMITED_FPS = 999;
    private static final int BASE_FPS = 30;
    private static final int[] SPEED_MULTIPLIERS = {1, 2, 5, 10, 20, 50, 100};

    private final SimulationEngine engine;
    private final SimulationCanvas canvas;
    private final OverlayManager overlayManager;
    private final Runnable onDeadMicrobeCheck;

    private volatile boolean running = true;
    private volatile boolean paused = false;
    private volatile int targetFps;
    private int currentSpeedIndex = 0;
    private long lastPopulationUpdateTime = System.nanoTime();

    /**
     * Creates a new loop controller.
     *
     * @param engine             the simulation engine
     * @param canvas             the rendering canvas
     * @param overlayManager     the overlay manager (for population display updates)
     * @param onDeadMicrobeCheck callback executed each frame to check/clear dead selected microbe
     */
    public SimulationLoopController(SimulationEngine engine, SimulationCanvas canvas,
                                    OverlayManager overlayManager, Runnable onDeadMicrobeCheck) {
        this.engine = engine;
        this.canvas = canvas;
        this.overlayManager = overlayManager;
        this.onDeadMicrobeCheck = onDeadMicrobeCheck;
        this.targetFps = BASE_FPS * SPEED_MULTIPLIERS[currentSpeedIndex];
    }

    /**
     * Starts the simulation loop in a daemon thread.
     */
    public void start() {
        Thread simulationThread = new Thread(() -> {
            while (running) {
                long startTime = System.nanoTime();

                long frameTime;
                if (targetFps >= UNLIMITED_FPS) {
                    frameTime = 0;
                } else {
                    frameTime = 1_000_000_000 / targetFps;
                }

                if (!paused) {
                    engine.update();
                    onDeadMicrobeCheck.run();
                    canvas.repaint();
                    updatePopulationDisplay();
                }

                // Sleep to maintain target FPS
                if (paused) {
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
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }, "SimulationLoop");

        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    /**
     * Cycles to the next speed multiplier and updates the target FPS.
     * Must only be called from the EDT (button click).
     *
     * @return the display string for the new speed (e.g. "2x")
     */
    public String cycleSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % SPEED_MULTIPLIERS.length;
        int multiplier = SPEED_MULTIPLIERS[currentSpeedIndex];
        targetFps = BASE_FPS * multiplier;
        return multiplier + "x";
    }

    /**
     * Periodically updates the population count display (once per second).
     */
    private void updatePopulationDisplay() {
        if (overlayManager == null) return;
        long currentTime = System.nanoTime();
        if (currentTime - lastPopulationUpdateTime >= 1_000_000_000) {
            lastPopulationUpdateTime = currentTime;
            int population = engine.getPopulationCount();
            SwingUtilities.invokeLater(() -> overlayManager.updatePopulationLabel(population));

            // Append a CSV snapshot of the current simulation state (runs once per second,
            // on the simulation loop thread – never on the EDT so blocking I/O is safe).
            DataExporter.logSimulationData(engine.getMicrobes(), engine.getEnvironment());
        }
    }

    /**
     * Pauses the simulation loop (engine updates are skipped until {@link #resume()} is called).
     */
    public void pause() {
        paused = true;
    }

    /**
     * Resumes a previously paused simulation loop.
     */
    public void resume() {
        paused = false;
    }

    /**
     * Signals the simulation loop thread to exit cleanly.
     */
    public void stop() {
        running = false;
    }
}

