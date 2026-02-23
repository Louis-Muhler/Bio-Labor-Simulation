package com.biolab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the SettingsManager class: defaults, validation, and save/load cycle.
 */
class SettingsManagerTest {

    // ===== Defaults =====

    @Test
    void defaultsShouldBeReasonable() {
        SettingsManager sm = new SettingsManager();
        assertTrue(sm.getWindowWidth() >= 800, "Default width should be >= 800");
        assertTrue(sm.getWindowHeight() >= 600, "Default height should be >= 600");
    }

    @Test
    void setDefaultsShouldResetAllValues() {
        SettingsManager sm = new SettingsManager();
        sm.setWindowWidth(999);
        sm.setWindowHeight(777);
        sm.setFullscreen(true);
        sm.setDefaults();
        assertEquals(1920, sm.getWindowWidth());
        assertEquals(1080, sm.getWindowHeight());
        assertFalse(sm.isFullscreen());
    }

    // ===== Getters/Setters =====

    @Test
    void gettersAndSettersShouldWork() {
        SettingsManager sm = new SettingsManager();
        sm.setWindowWidth(1280);
        assertEquals(1280, sm.getWindowWidth());
        sm.setWindowHeight(720);
        assertEquals(720, sm.getWindowHeight());
        sm.setFullscreen(true);
        assertTrue(sm.isFullscreen());
        sm.setFullscreen(false);
        assertFalse(sm.isFullscreen());
    }

    // ===== Save/Load Cycle =====

    @Test
    void saveAndLoadShouldPreserveSettings() {
        SettingsManager sm1 = new SettingsManager();
        sm1.setWindowWidth(1600);
        sm1.setWindowHeight(900);
        sm1.setFullscreen(false);
        sm1.saveSettings();

        SettingsManager sm2 = new SettingsManager();
        assertEquals(1600, sm2.getWindowWidth());
        assertEquals(900, sm2.getWindowHeight());
        assertFalse(sm2.isFullscreen());

        sm2.setDefaults();
        sm2.saveSettings();
    }

    // ===== Validation =====

    @Test
    void loadSettingsShouldResetInvalidWidth() {
        SettingsManager sm = new SettingsManager();
        sm.setWindowWidth(100);
        sm.saveSettings();

        SettingsManager sm2 = new SettingsManager();
        assertEquals(1920, sm2.getWindowWidth(), "Invalid width should be reset to default");

        sm2.setDefaults();
        sm2.saveSettings();
    }

    @Test
    void loadSettingsShouldResetInvalidHeight() {
        SettingsManager sm = new SettingsManager();
        sm.setWindowHeight(100);
        sm.saveSettings();

        SettingsManager sm2 = new SettingsManager();
        assertEquals(1080, sm2.getWindowHeight(), "Invalid height should be reset to default");

        sm2.setDefaults();
        sm2.saveSettings();
    }

    // ===== Thread-Safety =====

    @Test
    void synchronizedGettersSettersShouldNotThrow() throws InterruptedException {
        SettingsManager sm = new SettingsManager();
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
