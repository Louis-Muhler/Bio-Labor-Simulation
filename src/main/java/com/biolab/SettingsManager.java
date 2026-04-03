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

    // Settings
    private int windowWidth;
    private int windowHeight;
    private boolean fullscreen;
    private int simulationFps;

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
}
