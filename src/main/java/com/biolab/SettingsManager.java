package com.biolab;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages application settings with persistent storage.
 * Handles saving/loading settings to/from a configuration file with proper error handling.
 */
public class SettingsManager {
    private static final Logger LOGGER = Logger.getLogger(SettingsManager.class.getName());

    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".biolabsim");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("settings.properties");

    // Default values
    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;
    private static final boolean DEFAULT_FULLSCREEN = false;

    // Settings
    private int windowWidth;
    private int windowHeight;
    private boolean fullscreen;

    /**
     * Creates a SettingsManager and immediately loads persisted settings (or defaults).
     */
    public SettingsManager() {
        // Load settings or use defaults
        loadSettings();
    }
    
    /**
     * Loads settings from the configuration file.
     * If the file doesn't exist or is corrupted, uses default values.
     */
    public synchronized void loadSettings() {
        Properties props = new Properties();

        if (Files.exists(CONFIG_FILE)) {
            try (InputStream input = Files.newInputStream(CONFIG_FILE)) {
                props.load(input);
                
                // Parse settings with fallback to defaults
                windowWidth = parseIntOrDefault(props.getProperty("window.width"), DEFAULT_WIDTH);
                windowHeight = parseIntOrDefault(props.getProperty("window.height"), DEFAULT_HEIGHT);
                fullscreen = Boolean.parseBoolean(props.getProperty("window.fullscreen", String.valueOf(DEFAULT_FULLSCREEN)));

                // Validate settings
                validateSettings();
                
                LOGGER.info("Settings loaded successfully from " + CONFIG_FILE);
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

        try {
            // Create config directory if it doesn't exist
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            
            // Save properties to file
            try (OutputStream output = Files.newOutputStream(CONFIG_FILE)) {
                props.store(output, "Bio-Lab Simulator Settings");
            }
            
            LOGGER.info("Settings saved successfully to " + CONFIG_FILE);
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
}
