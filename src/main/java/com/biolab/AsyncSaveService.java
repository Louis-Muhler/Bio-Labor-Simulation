package com.biolab;

import java.util.concurrent.*;

/**
 * Serial background worker for save I/O with explicit flush support.
 */
public final class AsyncSaveService {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BioLab-SaveWorker");
        t.setDaemon(true);
        return t;
    });

    public void submit(Runnable task) {
        worker.submit(task);
    }

    public boolean flushAndWait(long timeout, TimeUnit unit) {
        Future<?> marker = worker.submit(() -> {
        });
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

