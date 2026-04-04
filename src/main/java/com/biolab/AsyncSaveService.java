package com.biolab;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serial background worker for save I/O with explicit flush support.
 */
public final class AsyncSaveService {
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BioLab-SaveWorker");
        t.setDaemon(true);
        return t;
    });

    public boolean submit(Runnable task) {
        if (closed.get()) {
            return false;
        }
        try {
            worker.submit(task);
            return true;
        } catch (RejectedExecutionException ex) {
            return false;
        }
    }

    public boolean flushAndWait(long timeout, TimeUnit unit) {
        if (closed.get() || worker.isShutdown()) {
            return true;
        }
        Future<?> marker;
        try {
            marker = worker.submit(() -> {
            });
        } catch (RejectedExecutionException ex) {
            return true;
        }
        try {
            marker.get(timeout, unit);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        }
    }

    public void shutdownAndFlush(long timeout, TimeUnit unit) {
        closed.set(true);
        flushAndWait(timeout, unit);
        worker.shutdown();
        try {
            if (!worker.awaitTermination(timeout, unit)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }
}

