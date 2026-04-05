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
    private static final Runnable POISON = () -> {
    };

    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private final WorldStatsStore store;
    private final Logger logger;

    private volatile boolean running = true;

    WorldStatsSampleAppender(WorldStatsStore store, Logger logger) {
        this.store = store;
        this.logger = logger;
        this.worker = new Thread(this::runWorker, "WorldStatsAppender");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    void submit(WorldStatsSample sample) {
        if (!running || sample == null) {
            return;
        }
        queue.offer(() -> store.append(sample));
    }

    void flush() {
        if (!running) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        queue.offer(latch::countDown);
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void shutdown() {
        if (!running) {
            return;
        }
        flush();
        running = false;
        queue.offer(POISON);
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
