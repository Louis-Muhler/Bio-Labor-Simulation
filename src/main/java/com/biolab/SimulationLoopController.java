package com.biolab;

import java.util.function.IntConsumer;
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
    private static final int[] SPEED_MULTIPLIERS = {1, 2, 5, 10, 20, 50, 100, 250, 500, 1000, 2500, 5000};
    private int currentSpeedIndex = 0;

    private final SimulationRuntime engine;
    private final SimulationCanvas canvas;
    private final Runnable onDeadMicrobeCheck;
    private final IntConsumer onPopulationUpdated;

    private volatile boolean running = false;
    private volatile boolean paused = false;
    private volatile Thread simulationThread;

    /**
     * Interval (ns) between engine.update() calls – the tick/simulation speed.
     */
    private volatile long frameIntervalNs;
    /**
     * Interval (ns) between canvas.repaint() calls – the visual render FPS.
     */
    private volatile long renderIntervalNs;

    private long lastPopulationUpdateTime;
    private long lastRenderTime;

    public SimulationLoopController(SimulationRuntime engine, SimulationCanvas canvas,
                                    Runnable onDeadMicrobeCheck,
                                    IntConsumer onPopulationUpdated) {
        this.engine = engine;
        this.canvas = canvas;
        this.onDeadMicrobeCheck = onDeadMicrobeCheck;
        this.onPopulationUpdated = onPopulationUpdated;
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
     * Resets simulation tick speed to the default 1x preset.
     */
    public String resetSpeedToDefault() {
        currentSpeedIndex = 0;
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
    public synchronized void start() {
        if (simulationThread != null && simulationThread.isAlive()) {
            return;
        }

        running = true;
        paused = false;
        lastPopulationUpdateTime = System.nanoTime();
        lastRenderTime = lastPopulationUpdateTime;

        simulationThread = new Thread(() -> {
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
                            if (running) {
                                LOGGER.log(Level.FINE, "Simulation loop interrupted while active", e);
                            }
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
     * Emits the population count once per second to the optional observer callback.
     */
    private void updatePopulationDisplay() {
        if (onPopulationUpdated == null) return;
        long currentTime = System.nanoTime();
        if (currentTime - lastPopulationUpdateTime >= 1_000_000_000) {
            lastPopulationUpdateTime = currentTime;
            int population = engine.getPopulationCount();
            onPopulationUpdated.accept(population);
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
    public synchronized void stop() {
        running = false;
        Thread thread = simulationThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        simulationThread = null;
    }
}
