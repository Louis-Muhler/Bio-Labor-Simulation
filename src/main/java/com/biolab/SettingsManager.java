package com.biolab;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Manages application settings with persistent storage.
 * Handles saving/loading settings to/from a configuration file with proper error handling.
 */
public class SettingsManager {
    private static final Logger LOGGER = Logger.getLogger(SettingsManager.class.getName());
    
    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".biolabsim";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "settings.properties";
    
    // Default values
    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;
    private static final boolean DEFAULT_FULLSCREEN = false;
    private static final int DEFAULT_FPS = 60;
    private static final int UNLIMITED_FPS = 999;
    
    // Settings
    private int windowWidth;
    private int windowHeight;
    private boolean fullscreen;
    private int targetFps;
    
    public SettingsManager() {
        // Load settings or use defaults
        loadSettings();
    }
    
    /**
     * Loads settings from the configuration file.
     * If the file doesn't exist or is corrupted, uses default values.
     */
    public void loadSettings() {
        Properties props = new Properties();
        Path configPath = Paths.get(CONFIG_FILE);
        
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                props.load(input);
                
                // Parse settings with fallback to defaults
                windowWidth = parseIntOrDefault(props.getProperty("window.width"), DEFAULT_WIDTH);
                windowHeight = parseIntOrDefault(props.getProperty("window.height"), DEFAULT_HEIGHT);
                fullscreen = Boolean.parseBoolean(props.getProperty("window.fullscreen", String.valueOf(DEFAULT_FULLSCREEN)));
                targetFps = parseIntOrDefault(props.getProperty("simulation.fps"), DEFAULT_FPS);
                
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
        props.setProperty("simulation.fps", String.valueOf(targetFps));
        
        try {
            // Create config directory if it doesn't exist
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            
            // Save properties to file
            try (OutputStream output = Files.newOutputStream(Paths.get(CONFIG_FILE))) {
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
    public void setDefaults() {
        windowWidth = DEFAULT_WIDTH;
        windowHeight = DEFAULT_HEIGHT;
        fullscreen = DEFAULT_FULLSCREEN;
        targetFps = DEFAULT_FPS;
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
        
        // Clamp FPS to reasonable values (allow unlimited FPS)
        if ((targetFps < 15 || targetFps > 240) && targetFps != UNLIMITED_FPS) {
            LOGGER.warning("Invalid FPS " + targetFps + ", resetting to default");
            targetFps = DEFAULT_FPS;
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
    
    // Getters
    public int getWindowWidth() { return windowWidth; }
    public int getWindowHeight() { return windowHeight; }
    public boolean isFullscreen() { return fullscreen; }
    public int getTargetFps() { return targetFps; }
    
    // Setters
    public void setWindowWidth(int width) { this.windowWidth = width; }
    public void setWindowHeight(int height) { this.windowHeight = height; }
    public void setFullscreen(boolean fullscreen) { this.fullscreen = fullscreen; }
    public void setTargetFps(int fps) { this.targetFps = fps; }
}
