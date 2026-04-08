package com.biolab;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single-consumer append worker that decouples stats-store writes from the simulation thread.
 */
final class WorldStatsSampleAppender {
    private static final long FLUSH_TIMEOUT_SECONDS = 5L;
    private static final Runnable POISON = () -> {
    };

    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private final WorldStatsStore store;
    private final Logger logger;

    private volatile boolean running = true;
    private volatile boolean acceptingSubmissions = true;

    WorldStatsSampleAppender(WorldStatsStore store, Logger logger) {
        this.store = store;
        this.logger = logger;
        this.worker = new Thread(this::runWorker, "WorldStatsAppender");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    void submit(WorldStatsSample sample) {
        if (!acceptingSubmissions || sample == null) {
            return;
        }
        boolean enqueued = queue.offer(() -> store.append(sample));
        if (!enqueued) {
            logger.warning("Failed to enqueue world stats sample; sample will be dropped");
        }
    }

    /**
     * Drains all tasks enqueued before this call returns true.
     */
    boolean flush() {
        if (!running) {
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        if (!queue.offer(latch::countDown)) {
            logger.warning("Failed to enqueue world stats flush barrier");
            return false;
        }
        try {
            boolean completed = latch.await(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                logger.warning("Timed out while flushing world stats appender");
            }
            return completed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Interrupted while flushing world stats appender", e);
            return false;
        }
    }

    void shutdown() {
        if (!running) {
            return;
        }
        acceptingSubmissions = false;
        if (!flush()) {
            logger.warning("Proceeding with world stats appender shutdown after incomplete flush");
        }
        running = false;
        if (!queue.offer(POISON)) {
            logger.warning("Failed to enqueue world stats appender shutdown marker");
        }
        try {
            worker.join(1_500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runWorker() {
        while (true) {
            try {
                Runnable task = queue.take();
                if (task == POISON) {
                    return;
                }
                task.run();
            } catch (InterruptedException e) {
                if (!running) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "World stats append task failed", ex);
            }
        }
    }
}
