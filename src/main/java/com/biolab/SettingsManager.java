package com.biolab;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages application settings with persistent storage.
 * Handles saving/loading settings to/from a configuration file with proper error handling.
 */
public class SettingsManager {
    private static final Logger LOGGER = Logger.getLogger(SettingsManager.class.getName());

    private static final Path DEFAULT_CONFIG_DIR = AppPaths.getSettingsDir();

    private final Path configDir;
    private final Path configFile;

    // Default values
    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;
    private static final boolean DEFAULT_FULLSCREEN = false;
    private static final int DEFAULT_SIMULATION_FPS = 60;
    private static final int DEFAULT_WORLD_STATS_WIDTH = 640;
    private static final int DEFAULT_WORLD_STATS_HEIGHT = 420;
    private static final int DEFAULT_AUTOSAVE_INTERVAL_SECONDS = 8;
    private static final int MIN_AUTOSAVE_INTERVAL_SECONDS = 1;
    private static final int MAX_AUTOSAVE_INTERVAL_SECONDS = 3600;
    private static final String DEFAULT_WORLD_STATS_METRICS =
            WorldMetricId.POPULATION_ALIVE.name() + "," + WorldMetricId.FOOD_PELLETS_AVAILABLE.name();
    private static final String DEFAULT_WORLD_STATS_PRESET = WorldStatsRangePreset.SINCE_BEGINNING.name();
    private static final long DEFAULT_WORLD_STATS_CUSTOM_START_VALUE = 0;
    private static final String DEFAULT_WORLD_STATS_CUSTOM_START_UNIT = WorldStatsTimeUnit.MIN.name();
    private static final long DEFAULT_WORLD_STATS_CUSTOM_END_VALUE = 150;
    private static final String DEFAULT_WORLD_STATS_CUSTOM_END_UNIT = WorldStatsTimeUnit.SEC.name();

    // Settings
    private int windowWidth;
    private int windowHeight;
    private boolean fullscreen;
    private int simulationFps;
    private int worldStatsViewerWidth;
    private int worldStatsViewerHeight;
    private int autosaveIntervalSeconds;
    private String worldStatsSelectedMetrics;
    private String worldStatsRangePreset;
    private long worldStatsCustomStartValue;
    private String worldStatsCustomStartUnit;
    private long worldStatsCustomEndValue;
    private String worldStatsCustomEndUnit;

    /**
     * Creates a SettingsManager and immediately loads persisted settings (or defaults).
     */
    public SettingsManager() {
        this(DEFAULT_CONFIG_DIR);
    }

    /**
     * Creates a SettingsManager using a custom configuration directory.
     * Primarily intended for tests to avoid touching user-home files.
     */
    public SettingsManager(Path configDir) {
        this.configDir = Objects.requireNonNull(configDir, "configDir must not be null");
        this.configFile = configDir.resolve("settings.properties");
        loadSettings();
    }
    
    /**
     * Loads settings from the configuration file.
     * If the file doesn't exist or is corrupted, uses default values.
     */
    public synchronized void loadSettings() {
        Properties props = new Properties();

        if (Files.exists(configFile)) {
            try (InputStream input = Files.newInputStream(configFile)) {
                props.load(input);
                
                // Parse settings with fallback to defaults
                windowWidth = parseIntOrDefault(props.getProperty("window.width"), DEFAULT_WIDTH);
                windowHeight = parseIntOrDefault(props.getProperty("window.height"), DEFAULT_HEIGHT);
                fullscreen = Boolean.parseBoolean(props.getProperty("window.fullscreen", String.valueOf(DEFAULT_FULLSCREEN)));
                simulationFps = parseIntOrDefault(props.getProperty("simulation.fps"), DEFAULT_SIMULATION_FPS);
                worldStatsViewerWidth = parseIntOrDefault(props.getProperty("worldstats.viewer.width"), DEFAULT_WORLD_STATS_WIDTH);
                worldStatsViewerHeight = parseIntOrDefault(props.getProperty("worldstats.viewer.height"), DEFAULT_WORLD_STATS_HEIGHT);
                autosaveIntervalSeconds = parseIntOrDefault(props.getProperty("autosave.interval.seconds"), DEFAULT_AUTOSAVE_INTERVAL_SECONDS);
                worldStatsSelectedMetrics = props.getProperty("worldstats.metrics", DEFAULT_WORLD_STATS_METRICS);
                worldStatsRangePreset = props.getProperty("worldstats.range.preset", DEFAULT_WORLD_STATS_PRESET);
                worldStatsCustomStartValue = parseLongOrDefault(props.getProperty("worldstats.custom.start.value"), DEFAULT_WORLD_STATS_CUSTOM_START_VALUE);
                worldStatsCustomStartUnit = props.getProperty("worldstats.custom.start.unit", DEFAULT_WORLD_STATS_CUSTOM_START_UNIT);
                worldStatsCustomEndValue = parseLongOrDefault(props.getProperty("worldstats.custom.end.value"), DEFAULT_WORLD_STATS_CUSTOM_END_VALUE);
                worldStatsCustomEndUnit = props.getProperty("worldstats.custom.end.unit", DEFAULT_WORLD_STATS_CUSTOM_END_UNIT);

                // Validate settings
                validateSettings();

                LOGGER.info("Settings loaded successfully from " + configFile);
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.log(Level.WARNING, "Failed to load settings, using defaults", e);
                setDefaults();
            }
        } else {
            LOGGER.info("No settings file found, using defaults");
            setDefaults();
        }
    }
    
    /**
     * Saves current settings to the configuration file.
     * Creates the config directory if it doesn't exist.
     */
    public synchronized void saveSettings() {
        Properties props = new Properties();
        props.setProperty("window.width", String.valueOf(windowWidth));
        props.setProperty("window.height", String.valueOf(windowHeight));
        props.setProperty("window.fullscreen", String.valueOf(fullscreen));
        props.setProperty("simulation.fps", String.valueOf(simulationFps));
        props.setProperty("worldstats.viewer.width", String.valueOf(worldStatsViewerWidth));
        props.setProperty("worldstats.viewer.height", String.valueOf(worldStatsViewerHeight));
        props.setProperty("autosave.interval.seconds", String.valueOf(autosaveIntervalSeconds));
        props.setProperty("worldstats.metrics", worldStatsSelectedMetrics == null ? DEFAULT_WORLD_STATS_METRICS : worldStatsSelectedMetrics);
        props.setProperty("worldstats.range.preset", worldStatsRangePreset == null ? DEFAULT_WORLD_STATS_PRESET : worldStatsRangePreset);
        props.setProperty("worldstats.custom.start.value", String.valueOf(worldStatsCustomStartValue));
        props.setProperty("worldstats.custom.start.unit", worldStatsCustomStartUnit == null ? DEFAULT_WORLD_STATS_CUSTOM_START_UNIT : worldStatsCustomStartUnit);
        props.setProperty("worldstats.custom.end.value", String.valueOf(worldStatsCustomEndValue));
        props.setProperty("worldstats.custom.end.unit", worldStatsCustomEndUnit == null ? DEFAULT_WORLD_STATS_CUSTOM_END_UNIT : worldStatsCustomEndUnit);

        try {
            // Create config directory if it doesn't exist
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            
            // Save properties to file
            try (OutputStream output = Files.newOutputStream(configFile)) {
                props.store(output, "Bio-Lab Simulator Settings");
            }

            LOGGER.info("Settings saved successfully to " + configFile);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save settings", e);
        }
    }
    
    /**
     * Resets all settings to default values.
     */
    public synchronized void setDefaults() {
        windowWidth = DEFAULT_WIDTH;
        windowHeight = DEFAULT_HEIGHT;
        fullscreen = DEFAULT_FULLSCREEN;
        simulationFps = DEFAULT_SIMULATION_FPS;
        worldStatsViewerWidth = DEFAULT_WORLD_STATS_WIDTH;
        worldStatsViewerHeight = DEFAULT_WORLD_STATS_HEIGHT;
        autosaveIntervalSeconds = DEFAULT_AUTOSAVE_INTERVAL_SECONDS;
        worldStatsSelectedMetrics = DEFAULT_WORLD_STATS_METRICS;
        worldStatsRangePreset = DEFAULT_WORLD_STATS_PRESET;
        worldStatsCustomStartValue = DEFAULT_WORLD_STATS_CUSTOM_START_VALUE;
        worldStatsCustomStartUnit = DEFAULT_WORLD_STATS_CUSTOM_START_UNIT;
        worldStatsCustomEndValue = DEFAULT_WORLD_STATS_CUSTOM_END_VALUE;
        worldStatsCustomEndUnit = DEFAULT_WORLD_STATS_CUSTOM_END_UNIT;
    }

    /**
     * Validates settings to ensure they are within acceptable ranges.
     */
    private void validateSettings() {
        // Clamp resolution to reasonable values
        if (windowWidth < 800 || windowWidth > 7680) {
            LOGGER.warning("Invalid width " + windowWidth + ", resetting to default");
            windowWidth = DEFAULT_WIDTH;
        }
        if (windowHeight < 600 || windowHeight > 4320) {
            LOGGER.warning("Invalid height " + windowHeight + ", resetting to default");
            windowHeight = DEFAULT_HEIGHT;
        }
        if (simulationFps < 10 || simulationFps > 240) {
            LOGGER.warning("Invalid simulationFps " + simulationFps + ", resetting to default");
            simulationFps = DEFAULT_SIMULATION_FPS;
        }
        if (worldStatsViewerWidth < 420 || worldStatsViewerWidth > 5000) {
            worldStatsViewerWidth = DEFAULT_WORLD_STATS_WIDTH;
        }
        if (worldStatsViewerHeight < 280 || worldStatsViewerHeight > 3000) {
            worldStatsViewerHeight = DEFAULT_WORLD_STATS_HEIGHT;
        }
        if (autosaveIntervalSeconds < MIN_AUTOSAVE_INTERVAL_SECONDS || autosaveIntervalSeconds > MAX_AUTOSAVE_INTERVAL_SECONDS) {
            autosaveIntervalSeconds = DEFAULT_AUTOSAVE_INTERVAL_SECONDS;
        }
        if (!isValidEnum(WorldStatsRangePreset.class, worldStatsRangePreset)) {
            worldStatsRangePreset = DEFAULT_WORLD_STATS_PRESET;
        }
        if (!isValidEnum(WorldStatsTimeUnit.class, worldStatsCustomStartUnit)) {
            worldStatsCustomStartUnit = DEFAULT_WORLD_STATS_CUSTOM_START_UNIT;
        }
        if (!isValidEnum(WorldStatsTimeUnit.class, worldStatsCustomEndUnit)) {
            worldStatsCustomEndUnit = DEFAULT_WORLD_STATS_CUSTOM_END_UNIT;
        }
        if (worldStatsCustomStartValue < 0) {
            worldStatsCustomStartValue = DEFAULT_WORLD_STATS_CUSTOM_START_VALUE;
        }
        if (worldStatsCustomEndValue < 0) {
            worldStatsCustomEndValue = DEFAULT_WORLD_STATS_CUSTOM_END_VALUE;
        }
    }
    
    /**
     * Parses an integer from a string, returning a default value if parsing fails.
     */
    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid integer value: " + value + ", using default: " + defaultValue);
            return defaultValue;
        }
    }

    private long parseLongOrDefault(String value, long defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid long value: " + value + ", using default: " + defaultValue);
            return defaultValue;
        }
    }

    private <E extends Enum<E>> boolean isValidEnum(Class<E> enumClass, String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            Enum.valueOf(enumClass, raw);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    // ===== Getters =====

    /**
     * Returns the configured window width in pixels.
     */
    public synchronized int getWindowWidth() {
        return windowWidth;
    }

    /**
     * Sets the window width. Does not persist until {@link #saveSettings()} is called.
     */
    public synchronized void setWindowWidth(int width) {
        this.windowWidth = width;
    }

    /**
     * Returns the configured window height in pixels.
     */
    public synchronized int getWindowHeight() {
        return windowHeight;
    }

    // ===== Setters =====

    /**
     * Sets the window height. Does not persist until {@link #saveSettings()} is called.
     */
    public synchronized void setWindowHeight(int height) {
        this.windowHeight = height;
    }

    /**
     * Returns {@code true} if fullscreen mode is enabled.
     */
    public synchronized boolean isFullscreen() {
        return fullscreen;
    }

    /**
     * Sets the fullscreen flag. Does not persist until {@link #saveSettings()} is called.
     */
    public synchronized void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    /**
     * Returns the target simulation FPS (10–240). Default is 60.
     */
    public synchronized int getSimulationFps() {
        return simulationFps;
    }

    /**
     * Sets the target simulation FPS. Does not persist until {@link #saveSettings()} is called.
     *
     * @param fps value clamped to [10, 240]
     */
    public synchronized void setSimulationFps(int fps) {
        this.simulationFps = Math.max(10, Math.min(240, fps));
    }

    public synchronized int getWorldStatsViewerWidth() {
        return worldStatsViewerWidth;
    }

    public synchronized void setWorldStatsViewerWidth(int width) {
        this.worldStatsViewerWidth = width;
    }

    public synchronized int getWorldStatsViewerHeight() {
        return worldStatsViewerHeight;
    }

    public synchronized void setWorldStatsViewerHeight(int height) {
        this.worldStatsViewerHeight = height;
    }

    public synchronized int getAutosaveIntervalSeconds() {
        return autosaveIntervalSeconds;
    }

    public synchronized void setAutosaveIntervalSeconds(int seconds) {
        this.autosaveIntervalSeconds = Math.max(MIN_AUTOSAVE_INTERVAL_SECONDS,
                Math.min(MAX_AUTOSAVE_INTERVAL_SECONDS, seconds));
    }

    public synchronized String getWorldStatsSelectedMetrics() {
        return worldStatsSelectedMetrics;
    }

    public synchronized void setWorldStatsSelectedMetrics(String csvMetricIds) {
        this.worldStatsSelectedMetrics = csvMetricIds;
    }

    public synchronized String getWorldStatsRangePreset() {
        return worldStatsRangePreset;
    }

    public synchronized void setWorldStatsRangePreset(String rangePreset) {
        this.worldStatsRangePreset = rangePreset;
    }


    public synchronized long getWorldStatsCustomStartValue() {
        return worldStatsCustomStartValue;
    }

    public synchronized void setWorldStatsCustomStartValue(long value) {
        this.worldStatsCustomStartValue = value;
    }

    public synchronized String getWorldStatsCustomStartUnit() {
        return worldStatsCustomStartUnit;
    }

    public synchronized void setWorldStatsCustomStartUnit(String unit) {
        this.worldStatsCustomStartUnit = unit;
    }

    public synchronized long getWorldStatsCustomEndValue() {
        return worldStatsCustomEndValue;
    }

    public synchronized void setWorldStatsCustomEndValue(long value) {
        this.worldStatsCustomEndValue = value;
    }

    public synchronized String getWorldStatsCustomEndUnit() {
        return worldStatsCustomEndUnit;
    }

    public synchronized void setWorldStatsCustomEndUnit(String unit) {
        this.worldStatsCustomEndUnit = unit;
    }
}
