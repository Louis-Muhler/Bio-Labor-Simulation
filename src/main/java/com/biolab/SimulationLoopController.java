package com.biolab;

import javax.swing.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controls the simulation loop thread, tick speed, and render rate.
 *
 * <p>Two independent rates are managed:</p>
 * <ul>
 *   <li><b>Tick speed</b> ({@link #frameIntervalNs}) – how often
 *       {@code engine.update()} is called.  Controlled by the in-game
 *       speed button via {@link #cycleSpeed()}.</li>
 *   <li><b>Render FPS</b> ({@link #renderIntervalNs}) – how often
 *       {@code canvas.repaint()} is called.  Controlled by the Settings
 *       overlay via {@link #setRenderFps(int)}.</li>
 * </ul>
 */
public class SimulationLoopController {
    private static final Logger LOGGER = Logger.getLogger(SimulationLoopController.class.getName());

    // ── Tick-speed (simulation updates / second) ──────────────────────────
    private static final int BASE_TPS = 30;
    private static final int[] SPEED_MULTIPLIERS = {1, 2, 5, 10, 20, 50, 100};
    private int currentSpeedIndex = 0;

    private final SimulationEngine engine;
    private final SimulationCanvas canvas;
    private final OverlayManager overlayManager;
    private final Runnable onDeadMicrobeCheck;

    private volatile boolean running = true;
    private volatile boolean paused = false;

    /**
     * Interval (ns) between engine.update() calls – the tick/simulation speed.
     */
    private volatile long frameIntervalNs;
    /**
     * Interval (ns) between canvas.repaint() calls – the visual render FPS.
     */
    private volatile long renderIntervalNs;

    private long lastPopulationUpdateTime = System.nanoTime();
    private long lastRenderTime = System.nanoTime();

    public SimulationLoopController(SimulationEngine engine, SimulationCanvas canvas,
                                    OverlayManager overlayManager, Runnable onDeadMicrobeCheck) {
        this.engine = engine;
        this.canvas = canvas;
        this.overlayManager = overlayManager;
        this.onDeadMicrobeCheck = onDeadMicrobeCheck;
        // Both default to 30 TPS / 60 render-FPS until callers set them explicitly.
        this.frameIntervalNs = 1_000_000_000L / (BASE_TPS * SPEED_MULTIPLIERS[0]);
        this.renderIntervalNs = 1_000_000_000L / 60;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Cycles to the next simulation speed preset and returns the label for the button.
     * Only the tick rate changes; the render FPS is unaffected.
     *
     * @return display string, e.g. {@code "2x"}
     */
    public String cycleSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % SPEED_MULTIPLIERS.length;
        int multiplier = SPEED_MULTIPLIERS[currentSpeedIndex];
        frameIntervalNs = 1_000_000_000L / (BASE_TPS * multiplier);
        return multiplier + "x";
    }

    /**
     * Sets the visual render FPS (i.e. how often the canvas is redrawn).
     * Called when the user applies Settings.  Safe to call from any thread.
     *
     * @param fps clamped to [10, 240]
     */
    public void setRenderFps(int fps) {
        fps = Math.max(10, Math.min(240, fps));
        this.renderIntervalNs = 1_000_000_000L / fps;
    }

    /**
     * Starts the simulation loop in a daemon thread.
     */
    public void start() {
        Thread simulationThread = new Thread(() -> {
            while (running) {
                long startTime = System.nanoTime();

                if (!paused) {
                    engine.update();
                    onDeadMicrobeCheck.run();

                    // Repaint only when enough time has elapsed for the chosen render FPS.
                    long now = System.nanoTime();
                    if (now - lastRenderTime >= renderIntervalNs) {
                        lastRenderTime = now;
                        canvas.repaint();
                    }

                    updatePopulationDisplay();
                }

                if (paused) {
                    try {
                        //noinspection BusyWait
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    long elapsed = System.nanoTime() - startTime;
                    long sleepTime = frameIntervalNs - elapsed;
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
     * Periodically updates the population count display (once per second).
     */
    private void updatePopulationDisplay() {
        if (overlayManager == null) return;
        long currentTime = System.nanoTime();
        if (currentTime - lastPopulationUpdateTime >= 1_000_000_000) {
            lastPopulationUpdateTime = currentTime;
            int population = engine.getPopulationCount();
            SwingUtilities.invokeLater(() -> overlayManager.updatePopulationLabel(population));
            DataExporter.logSimulationData(engine.getMicrobes(), engine.getEnvironment());
        }
    }

    /**
     * Pauses simulation updates (rendering also stops).
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

    /** Signals the simulation loop thread to exit cleanly. */
    public void stop()   { running = false; }
}
