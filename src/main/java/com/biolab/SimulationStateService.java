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

    public void save(Path file, SimulationState state) throws IOException {
        if (file == null) throw new IllegalArgumentException("file must not be null");
        if (state == null) throw new IllegalArgumentException("state must not be null");

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) {
            out.writeObject(state);
        }
    }

    public SimulationState load(Path file) throws IOException {
        if (file == null) throw new IllegalArgumentException("file must not be null");

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            Object obj = in.readObject();
            if (obj instanceof SimulationState state) {
                return state;
            }
            throw new IOException("Invalid save file type: " + obj.getClass().getName());
        } catch (ClassNotFoundException e) {
            throw new IOException("Could not deserialize SimulationState", e);
        }
    }
}

