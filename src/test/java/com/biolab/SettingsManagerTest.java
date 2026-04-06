package com.biolab;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the SettingsManager class: defaults, validation, and save/load cycle.
 */
class SettingsManagerTest {

    private SettingsManager newIsolatedManager() {
        try {
            Path dir = Files.createTempDirectory("biolab-settings-test-");
            return new SettingsManager(dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ===== Defaults =====

    @Test
    void defaultsShouldBeReasonable() {
        SettingsManager sm = newIsolatedManager();
        assertTrue(sm.getWindowWidth() >= 800, "Default width should be >= 800");
        assertTrue(sm.getWindowHeight() >= 600, "Default height should be >= 600");
        assertEquals(8, sm.getAutosaveIntervalSeconds());
        assertEquals(0L, sm.getWorldStatsCustomStartValue());
        assertEquals(WorldStatsTimeUnit.MIN.name(), sm.getWorldStatsCustomStartUnit());
        assertEquals(150L, sm.getWorldStatsCustomEndValue());
        assertEquals(WorldStatsTimeUnit.SEC.name(), sm.getWorldStatsCustomEndUnit());
    }

    @Test
    void setDefaultsShouldResetAllValues() {
        SettingsManager sm = newIsolatedManager();
        sm.setWindowWidth(999);
        sm.setWindowHeight(777);
        sm.setFullscreen(true);
        sm.setAutosaveIntervalSeconds(30);
        sm.setDefaults();
        assertEquals(1920, sm.getWindowWidth());
        assertEquals(1080, sm.getWindowHeight());
        assertFalse(sm.isFullscreen());
        assertEquals(8, sm.getAutosaveIntervalSeconds());
    }

    // ===== Getters/Setters =====

    @Test
    void gettersAndSettersShouldWork() {
        SettingsManager sm = newIsolatedManager();
        sm.setWindowWidth(1280);
        assertEquals(1280, sm.getWindowWidth());
        sm.setWindowHeight(720);
        assertEquals(720, sm.getWindowHeight());
        sm.setFullscreen(true);
        assertTrue(sm.isFullscreen());
        sm.setFullscreen(false);
        assertFalse(sm.isFullscreen());
        sm.setAutosaveIntervalSeconds(22);
        assertEquals(22, sm.getAutosaveIntervalSeconds());
    }

    // ===== Save/Load Cycle =====

    @Test
    void saveAndLoadShouldPreserveSettings() {
        Path dir;
        try {
            dir = Files.createTempDirectory("biolab-settings-cycle-");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        SettingsManager sm1 = new SettingsManager(dir);
        sm1.setWindowWidth(1600);
        sm1.setWindowHeight(900);
        sm1.setFullscreen(false);
        sm1.setAutosaveIntervalSeconds(17);
        sm1.saveSettings();

        SettingsManager sm2 = new SettingsManager(dir);
        assertEquals(1600, sm2.getWindowWidth());
        assertEquals(900, sm2.getWindowHeight());
        assertFalse(sm2.isFullscreen());
        assertEquals(17, sm2.getAutosaveIntervalSeconds());

        sm2.setDefaults();
        sm2.saveSettings();
    }

    // ===== Validation =====

    @Test
    void loadSettingsShouldResetInvalidWidth() {
        Path dir;
        try {
            dir = Files.createTempDirectory("biolab-settings-width-");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        SettingsManager sm = new SettingsManager(dir);
        sm.setWindowWidth(100);
        sm.saveSettings();

        SettingsManager sm2 = new SettingsManager(dir);
        assertEquals(1920, sm2.getWindowWidth(), "Invalid width should be reset to default");

        sm2.setDefaults();
        sm2.saveSettings();
    }

    @Test
    void loadSettingsShouldResetInvalidHeight() {
        Path dir;
        try {
            dir = Files.createTempDirectory("biolab-settings-height-");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        SettingsManager sm = new SettingsManager(dir);
        sm.setWindowHeight(100);
        sm.saveSettings();

        SettingsManager sm2 = new SettingsManager(dir);
        assertEquals(1080, sm2.getWindowHeight(), "Invalid height should be reset to default");

        sm2.setDefaults();
        sm2.saveSettings();
    }

    @Test
    void autosaveIntervalSetterShouldClampToAllowedRange() {
        SettingsManager sm = newIsolatedManager();
        sm.setAutosaveIntervalSeconds(0);
        assertEquals(1, sm.getAutosaveIntervalSeconds());

        sm.setAutosaveIntervalSeconds(10_000);
        assertEquals(3600, sm.getAutosaveIntervalSeconds());
    }

    // ===== Thread-Safety =====

    @Test
    void synchronizedGettersSettersShouldNotThrow() throws InterruptedException {
        SettingsManager sm = newIsolatedManager();
        int threads = 4;
        int iterations = 1000;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.atomic.AtomicReference<Throwable> error =
                new java.util.concurrent.atomic.AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        sm.setWindowWidth(1280 + (i % 100));
                        sm.setWindowHeight(720 + (i % 100));
                        // Feed isFullscreen() result back into setFullscreen() so the
                        // return value is consumed and no "result ignored" warning fires
                        sm.setFullscreen(!sm.isFullscreen());
                        int w = sm.getWindowWidth();
                        int h = sm.getWindowHeight();
                        assertTrue(w >= 1280 && w <= 1380, "Width out of expected range: " + w);
                        assertTrue(h >= 720 && h <= 820, "Height out of expected range: " + h);
                    }
                } catch (Throwable e) {
                    error.set(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        assertNull(error.get(), () -> "Concurrent access threw an exception: " + error.get());
    }
}
