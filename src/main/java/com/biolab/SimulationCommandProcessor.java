package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe command ingress with optional coalescing for high-frequency UI writes.
 */
final class SimulationCommandProcessor {
    private final int maxQueuedCommands;
    private final ConcurrentLinkedQueue<SimulationCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<SimulationCommand.CommandKey, SimulationCommand> coalescedCommands =
            new ConcurrentHashMap<>();
    private final AtomicInteger queuedCommandCount = new AtomicInteger();

    SimulationCommandProcessor(int maxQueuedCommands) {
        this.maxQueuedCommands = Math.max(1, maxQueuedCommands);
    }

    void enqueue(SimulationCommand command) {
        if (command == null) return;

        SimulationCommand.CommandKey key = command.coalescingKey();
        if (key != null) {
            coalescedCommands.put(key, command);
            return;
        }

        int size = queuedCommandCount.incrementAndGet();
        if (size > maxQueuedCommands) {
            queuedCommandCount.decrementAndGet();
            return;
        }
        commandQueue.offer(command);
    }

    void processPending(SimulationRuntime runtime, Logger logger) {
        SimulationCommand command;
        while ((command = commandQueue.poll()) != null) {
            queuedCommandCount.decrementAndGet();
            try {
                command.apply(runtime);
            } catch (RuntimeException ex) {
                logger.log(Level.WARNING, "Failed to execute simulation command", ex);
            }
        }

        if (!coalescedCommands.isEmpty()) {
            List<SimulationCommand.CommandKey> keys = new ArrayList<>(coalescedCommands.keySet());
            for (SimulationCommand.CommandKey key : keys) {
                SimulationCommand latest = coalescedCommands.remove(key);
                if (latest == null) continue;
                try {
                    latest.apply(runtime);
                } catch (RuntimeException ex) {
                    logger.log(Level.WARNING, "Failed to execute coalesced simulation command", ex);
                }
            }
        }
    }
}

