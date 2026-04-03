package com.biolab;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistence service for SimulationState snapshots.
 */
public class SimulationStateService {
    private static final int FORMAT_VERSION = 1;

    public void save(Path file, SimulationState state) throws IOException {
        if (file == null) throw new IllegalArgumentException("file must not be null");
        if (state == null) throw new IllegalArgumentException("state must not be null");

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) {
            out.writeObject(new SaveEnvelope(FORMAT_VERSION, state));
        }
    }

    public SimulationState load(Path file) throws IOException {
        if (file == null) throw new IllegalArgumentException("file must not be null");

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            Object obj = in.readObject();
            if (obj instanceof SaveEnvelope env) {
                if (env.version() != FORMAT_VERSION) {
                    throw new IOException("Unsupported save format version: " + env.version());
                }
                if (env.payload() == null) {
                    throw new IOException("Save file payload is empty");
                }
                return env.payload();
            }
            if (obj instanceof SimulationState state) {
                // Legacy format (pre-versioned envelope)
                return state;
            }
            throw new IOException("Invalid save file type: " + obj.getClass().getName());
        } catch (ClassNotFoundException e) {
            throw new IOException("Could not deserialize SimulationState", e);
        }
    }

    private record SaveEnvelope(int version, SimulationState payload) implements java.io.Serializable {
    }
}

