package com.biolab;

import java.time.Instant;
import java.util.Properties;

/**
 * Metadata shown in the save browser list.
 */
public record SaveGameMetadata(
        String saveId,
        String mapName,
        long createdAtEpochSeconds,
        long lastPlayedAtEpochSeconds,
        long playtimeSeconds,
        int worldWidth,
        int worldHeight,
        int population
) {
    public SaveGameMetadata {
        if (saveId == null || saveId.isBlank()) throw new IllegalArgumentException("saveId is required");
        if (mapName == null || mapName.isBlank()) throw new IllegalArgumentException("mapName is required");
    }

    public static SaveGameMetadata createNew(String saveId, WorldConfig config, int population) {
        long now = Instant.now().getEpochSecond();
        return new SaveGameMetadata(
                saveId,
                config.mapName(),
                now,
                now,
                0,
                config.worldWidth(),
                config.worldHeight(),
                population
        );
    }

    private static String formatPlaytime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static SaveGameMetadata fromProperties(Properties props) {
        String id = props.getProperty("save.id", "");
        String mapName = props.getProperty("map.name", "Unnamed World");
        long created = parseLong(props.getProperty("created.at"), Instant.now().getEpochSecond());
        long lastPlayed = parseLong(props.getProperty("last.played.at"), created);
        long playtime = parseLong(props.getProperty("playtime.seconds"), 0);
        int width = parseInt(props.getProperty("world.width"), 10_000);
        int height = parseInt(props.getProperty("world.height"), 10_000);
        int population = parseInt(props.getProperty("population"), 0);
        return new SaveGameMetadata(id, mapName, created, lastPlayed, playtime, width, height, population);
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    public SaveGameMetadata withSessionUpdate(long additionalPlaytimeSeconds, int updatedPopulation) {
        long now = Instant.now().getEpochSecond();
        return new SaveGameMetadata(
                saveId,
                mapName,
                createdAtEpochSeconds,
                now,
                Math.max(0, playtimeSeconds + Math.max(0, additionalPlaytimeSeconds)),
                worldWidth,
                worldHeight,
                Math.max(0, updatedPopulation)
        );
    }

    public SaveGameMetadata withMapName(String updatedMapName) {
        return new SaveGameMetadata(
                saveId,
                updatedMapName,
                createdAtEpochSeconds,
                lastPlayedAtEpochSeconds,
                playtimeSeconds,
                worldWidth,
                worldHeight,
                population
        );
    }

    public String toDisplayLine() {
        return mapName + "  |  "
                + worldWidth + "x" + worldHeight
                + "  |  Pop " + String.format("%,d", population)
                + "  |  Playtime " + formatPlaytime(playtimeSeconds);
    }

    public String listName() {
        return mapName;
    }

    public String listMetaPrimary() {
        return String.format("Map %dx%d  |  Pop %,d", worldWidth, worldHeight, population);
    }

    public String listMetaSecondary() {
        return "Playtime " + formatPlaytime(playtimeSeconds);
    }

    public Properties toProperties() {
        Properties props = new Properties();
        props.setProperty("save.id", saveId);
        props.setProperty("map.name", mapName);
        props.setProperty("created.at", String.valueOf(createdAtEpochSeconds));
        props.setProperty("last.played.at", String.valueOf(lastPlayedAtEpochSeconds));
        props.setProperty("playtime.seconds", String.valueOf(playtimeSeconds));
        props.setProperty("world.width", String.valueOf(worldWidth));
        props.setProperty("world.height", String.valueOf(worldHeight));
        props.setProperty("population", String.valueOf(population));
        return props;
    }
}

