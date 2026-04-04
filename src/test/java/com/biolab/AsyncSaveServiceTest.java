package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncSaveServiceTest {

    @Test
    void flushWaitsForQueuedTaskAndRejectsAfterShutdown() throws InterruptedException {
        AsyncSaveService service = new AsyncSaveService();
        CountDownLatch latch = new CountDownLatch(1);

        assertTrue(service.submit(latch::countDown));
        assertTrue(service.flushAndWait(1, TimeUnit.SECONDS));
        assertTrue(latch.await(1, TimeUnit.SECONDS));

        service.shutdownAndFlush(1, TimeUnit.SECONDS);
        assertFalse(service.submit(() -> {
        }));
    }
}

