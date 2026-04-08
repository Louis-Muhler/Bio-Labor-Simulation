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
    private static final long DEFAULT_STOP_TIMEOUT_MS = 3_000L;
    // Avoid OS timer quantization for short waits.
    private static final long OS_SLEEP_THRESHOLD_NS = 25_000_000L;
    private static final long OS_SLEEP_GUARD_NS = 5_000_000L;
    private static final long YIELD_THRESHOLD_NS = 1_000_000L;

    // ── Tick-speed (simulation updates / second) ──────────────────────────
    private static final int BASE_TPS = 30;
    private static final int[] SPEED_MULTIPLIERS = {1, 2, 5, 10, 25, 50, 100};
    private static final String MAX_SPEED_LABEL = "MAX";
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
        this.frameIntervalNs = frameIntervalForCurrentSpeed();
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
        int speedStageCount = SPEED_MULTIPLIERS.length + 1; // +1 for uncapped MAX stage
        currentSpeedIndex = (currentSpeedIndex + 1) % speedStageCount;
        frameIntervalNs = frameIntervalForCurrentSpeed();
        return speedLabelForCurrentSpeed();
    }

    /**
     * Resets simulation tick speed to the default 1x preset.
     */
    public String resetSpeedToDefault() {
        currentSpeedIndex = 0;
        frameIntervalNs = frameIntervalForCurrentSpeed();
        return speedLabelForCurrentSpeed();
    }

    private long frameIntervalForCurrentSpeed() {
        if (isMaxSpeedMode()) {
            return 0L;
        }
        int multiplier = SPEED_MULTIPLIERS[currentSpeedIndex];
        return 1_000_000_000L / (BASE_TPS * multiplier);
    }

    private String speedLabelForCurrentSpeed() {
        if (isMaxSpeedMode()) {
            return MAX_SPEED_LABEL;
        }
        return SPEED_MULTIPLIERS[currentSpeedIndex] + "x";
    }

    private boolean isMaxSpeedMode() {
        return currentSpeedIndex == SPEED_MULTIPLIERS.length;
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
                    try {
                        engine.update();
                        onDeadMicrobeCheck.run();
                    } catch (RuntimeException e) {
                        LOGGER.log(Level.SEVERE, "Unhandled runtime exception in simulation loop; stopping loop", e);
                        running = false;
                        break;
                    }

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
                    long intervalNs = frameIntervalNs;
                    if (intervalNs <= 0L) {
                        // MAX mode: run updates as fast as possible without artificial wait.
                        continue;
                    }
                    long targetEndNs = startTime + intervalNs;
                    if (!waitUntil(targetEndNs)) {
                        break;
                    }
                }
            }
        }, "SimulationLoop");

        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    /**
     * Waits until the given deadline using a hybrid strategy:
     * coarse OS sleep for long waits, then yield/spin for short waits.
     *
     * @return false when interrupted and loop should terminate
     */
    private boolean waitUntil(long deadlineNs) {
        while (running) {
            long remainingNs = deadlineNs - System.nanoTime();
            if (remainingNs <= 0L) {
                return true;
            }
            try {
                if (remainingNs > OS_SLEEP_THRESHOLD_NS) {
                    long coarseNs = remainingNs - OS_SLEEP_GUARD_NS;
                    long sleepMs = coarseNs / 1_000_000L;
                    if (sleepMs > 0L) {
                        //noinspection BusyWait
                        Thread.sleep(sleepMs);
                        continue;
                    }
                }
                if (remainingNs > YIELD_THRESHOLD_NS) {
                    Thread.yield();
                    continue;
                }
                Thread.onSpinWait();
            } catch (InterruptedException e) {
                if (running) {
                    LOGGER.log(Level.FINE, "Simulation loop interrupted while active", e);
                }
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
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
        stopAndAwait(DEFAULT_STOP_TIMEOUT_MS);
    }

    synchronized boolean stopAndAwait(long timeoutMs) {
        running = false;
        Thread thread = simulationThread;
        if (thread == null) {
            simulationThread = null;
            return true;
        }

        thread.interrupt();
        long remainingMs = Math.max(0L, timeoutMs);
        long deadline = System.nanoTime() + remainingMs * 1_000_000L;
        while (thread.isAlive() && remainingMs > 0L) {
            try {
                thread.join(Math.min(remainingMs, 250L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            remainingMs = Math.max(0L, (deadline - System.nanoTime()) / 1_000_000L);
        }

        boolean stopped = !thread.isAlive();
        if (!stopped) {
            LOGGER.warning("Simulation loop thread did not stop before timeout");
        }
        if (stopped) {
            simulationThread = null;
        }
        return stopped;
    }
}
