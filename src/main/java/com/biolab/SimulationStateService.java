package com.biolab;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence service for SimulationState snapshots.
 */
public class SimulationStateService {
    private static final int MAGIC = 0x424C5331; // BLS1
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_SAVE_FILE_BYTES = 50L * 1024L * 1024L;

    private static void writeState(DataOutputStream out, SimulationState state) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(FORMAT_VERSION);
        out.writeInt(state.worldWidth());
        out.writeInt(state.worldHeight());
        out.writeDouble(state.temperature());
        out.writeDouble(state.toxicity());
        out.writeDouble(state.foodSpawnRate());
        out.writeBoolean(state.debugMode());

        out.writeInt(state.microbes().size());
        for (Microbe.PersistedState microbe : state.microbes()) {
            out.writeLong(microbe.id());
            out.writeLong(microbe.parentId());
            out.writeInt(microbe.absoluteGeneration());
            out.writeDouble(microbe.x());
            out.writeDouble(microbe.y());
            out.writeDouble(microbe.velocityX());
            out.writeDouble(microbe.velocityY());
            out.writeDouble(microbe.heatResistance());
            out.writeDouble(microbe.toxinResistance());
            out.writeDouble(microbe.speed());
            out.writeDouble(microbe.diet());
            out.writeDouble(microbe.health());
            out.writeDouble(microbe.energy());
            out.writeInt(microbe.age());
            out.writeBoolean(microbe.selected());
            out.writeLong(microbe.lastAttackTime());
            out.writeDouble(microbe.targetX());
            out.writeDouble(microbe.targetY());
            out.writeUTF(microbe.aiState().name());
            out.writeLong(microbe.adrenalineTimer());

            List<AncestorSnapshot> ancestry = microbe.ancestry();
            out.writeInt(ancestry.size());
            for (AncestorSnapshot a : ancestry) {
                out.writeDouble(a.heatResistance());
                out.writeDouble(a.toxinResistance());
                out.writeDouble(a.speed());
                out.writeDouble(a.diet());
                out.writeInt(a.generation());
            }
        }

        out.writeInt(state.food().size());
        for (SimulationState.FoodState food : state.food()) {
            out.writeDouble(food.x());
            out.writeDouble(food.y());
        }
    }

    private static SimulationState readState(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC) {
            throw new IOException("Invalid save file magic");
        }

        int version = in.readInt();
        if (version != FORMAT_VERSION) {
            throw new IOException("Unsupported save format version: " + version);
        }

        int worldWidth = in.readInt();
        int worldHeight = in.readInt();
        double temperature = in.readDouble();
        double toxicity = in.readDouble();
        double foodSpawnRate = in.readDouble();
        boolean debugMode = in.readBoolean();

        int microbeCount = in.readInt();
        if (microbeCount < 0 || microbeCount > 200_000) {
            throw new IOException("Invalid microbe count: " + microbeCount);
        }

        List<Microbe.PersistedState> microbes = new ArrayList<>(microbeCount);
        for (int i = 0; i < microbeCount; i++) {
            long id = in.readLong();
            long parentId = in.readLong();
            int absoluteGeneration = in.readInt();
            double x = in.readDouble();
            double y = in.readDouble();
            double velocityX = in.readDouble();
            double velocityY = in.readDouble();
            double heatResistance = in.readDouble();
            double toxinResistance = in.readDouble();
            double speed = in.readDouble();
            double diet = in.readDouble();
            double health = in.readDouble();
            double energy = in.readDouble();
            int age = in.readInt();
            boolean selected = in.readBoolean();
            long lastAttackTime = in.readLong();
            double targetX = in.readDouble();
            double targetY = in.readDouble();
            AiState aiState = AiState.valueOf(in.readUTF());
            long adrenalineTimer = in.readLong();

            int ancestryCount = in.readInt();
            if (ancestryCount < 0 || ancestryCount > 1024) {
                throw new IOException("Invalid ancestry size: " + ancestryCount);
            }
            List<AncestorSnapshot> ancestry = new ArrayList<>(ancestryCount);
            for (int a = 0; a < ancestryCount; a++) {
                ancestry.add(new AncestorSnapshot(
                        in.readDouble(),
                        in.readDouble(),
                        in.readDouble(),
                        in.readDouble(),
                        in.readInt()
                ));
            }

            microbes.add(new Microbe.PersistedState(
                    id,
                    parentId,
                    absoluteGeneration,
                    x,
                    y,
                    velocityX,
                    velocityY,
                    heatResistance,
                    toxinResistance,
                    speed,
                    diet,
                    health,
                    energy,
                    age,
                    selected,
                    lastAttackTime,
                    targetX,
                    targetY,
                    aiState,
                    adrenalineTimer,
                    ancestry
            ));
        }

        int foodCount = in.readInt();
        if (foodCount < 0 || foodCount > 1_000_000) {
            throw new IOException("Invalid food count: " + foodCount);
        }
        List<SimulationState.FoodState> food = new ArrayList<>(foodCount);
        for (int i = 0; i < foodCount; i++) {
            food.add(new SimulationState.FoodState(in.readDouble(), in.readDouble()));
        }

        return new SimulationState(
                worldWidth,
                worldHeight,
                temperature,
                toxicity,
                foodSpawnRate,
                microbes,
                food,
                debugMode
        );
    }

    public void save(Path file, SimulationState state) throws IOException {
        if (file == null) throw new IllegalArgumentException("file must not be null");
        if (state == null) throw new IllegalArgumentException("state must not be null");

        Path normalized = file.toAbsolutePath().normalize();

        Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tmp = normalized.resolveSibling(normalized.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            writeState(out, state);
        }
        Files.move(tmp, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public SimulationState load(Path file) throws IOException {
        if (file == null) throw new IllegalArgumentException("file must not be null");
        Path normalized = file.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            throw new IOException("Save file does not exist: " + normalized);
        }

        long size = Files.size(normalized);
        if (size <= 0 || size > MAX_SAVE_FILE_BYTES) {
            throw new IOException("Invalid save file size: " + size + " bytes");
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(normalized)))) {
            return readState(in);
        } catch (EOFException e) {
            throw new IOException("Corrupted save file (unexpected EOF)", e);
        }
    }
}

