package com.biolab;

import java.nio.file.Path;

/**
 * Resolves application data directories in a platform-aware way.
 */
public final class AppPaths {
    private static final String APP_DIR_NAME = "BioLabSimulator";

    private AppPaths() {
    }

    public static Path getAppRoot() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, APP_DIR_NAME);
            }
        }
        return Path.of(System.getProperty("user.home"), ".biolabsim");
    }

    public static Path getSettingsDir() {
        return getAppRoot().resolve("settings");
    }

    public static Path getSavesDir() {
        return getAppRoot().resolve("saves");
    }
}

