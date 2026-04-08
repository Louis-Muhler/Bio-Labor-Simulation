package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FrameMutationCoordinatorTest {

    @Test
    void beginFrameShouldSerializeConcurrentFrameStarts() throws Exception {
        FrameMutationCoordinator coordinator = new FrameMutationCoordinator();
        coordinator.beginFrame();

        AtomicBoolean secondFrameEntered = new AtomicBoolean(false);
        CountDownLatch secondThreadStarted = new CountDownLatch(1);

        Thread second = new Thread(() -> {
            secondThreadStarted.countDown();
            try {
                coordinator.beginFrame();
                secondFrameEntered.set(true);
                coordinator.endFrame();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "frame-second-thread");

        second.start();
        assertTrue(secondThreadStarted.await(1, TimeUnit.SECONDS));

        Thread.sleep(120);
        assertFalse(secondFrameEntered.get(), "Second frame must wait while first frame is in progress");

        coordinator.endFrame();
        second.join(1_000);

        assertTrue(secondFrameEntered.get(), "Second frame should start after first frame ends");
        assertFalse(second.isAlive(), "Second frame thread should terminate");
    }

    @Test
    void runExclusiveShouldAbortActionWhenInterruptedWhileWaiting() throws Exception {
        FrameMutationCoordinator coordinator = new FrameMutationCoordinator();
        coordinator.beginFrame();

        AtomicBoolean actionExecuted = new AtomicBoolean(false);
        AtomicBoolean interruptObserved = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread exclusive = new Thread(() -> {
            try {
                coordinator.runExclusive(() -> {
                    actionExecuted.set(true);
                    return null;
                });
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                interruptObserved.set(Thread.currentThread().isInterrupted());
            }
        }, "exclusive-waiter");

        exclusive.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (exclusive.isAlive() && exclusive.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(Thread.State.WAITING, exclusive.getState(), "Exclusive thread should be blocked in coordinator wait");

        exclusive.interrupt();
        exclusive.join(1_000);
        assertFalse(exclusive.isAlive(), "Interrupted exclusive waiter should abort without frame release");

        coordinator.endFrame();

        assertFalse(actionExecuted.get(), "Interrupted waiter must not execute exclusive action");
        assertInstanceOf(IllegalStateException.class, failure.get(), "Interrupted wait should fail fast");
        assertTrue(interruptObserved.get(), "Interrupt status should be preserved");
    }
}




