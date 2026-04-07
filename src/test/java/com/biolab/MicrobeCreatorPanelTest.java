package com.biolab;

import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MicrobeCreatorPanelTest {

    private static void onEdt(ThrowingRunnable runnable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        final Exception[] thrown = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                runnable.run();
            } catch (Exception ex) {
                thrown[0] = ex;
            }
        });
        if (thrown[0] != null) {
            throw thrown[0];
        }
    }

    private static <T> T onEdt(ThrowingSupplier<T> supplier) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return supplier.get();
        }
        final Object[] out = new Object[1];
        final Exception[] thrown = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                out[0] = supplier.get();
            } catch (Exception ex) {
                thrown[0] = ex;
            }
        });
        if (thrown[0] != null) {
            throw thrown[0];
        }
        @SuppressWarnings("unchecked")
        T value = (T) out[0];
        return value;
    }

    @Test
    void tabSwitchShouldChangeSpawnModeAndAmountSource() throws Exception {
        MicrobeCreatorPanel panel = onEdt(MicrobeCreatorPanel::new);

        onEdt(() -> {
            panel.setMicrobeAmount(3);
            panel.setFoodAmount(9);
            panel.setSelectedMode(MicrobeCreatorPanel.SpawnMode.MICROBE);
        });
        assertEquals(MicrobeCreatorPanel.SpawnMode.MICROBE, onEdt(panel::selectedMode));
        assertEquals(3, onEdt(panel::currentAmount));

        onEdt(() -> panel.setSelectedMode(MicrobeCreatorPanel.SpawnMode.FOOD));
        assertEquals(MicrobeCreatorPanel.SpawnMode.FOOD, onEdt(panel::selectedMode));
        assertEquals(9, onEdt(panel::currentAmount));
    }

    @Test
    void randomPrefillShouldResetTraitInputsToDefaults() throws Exception {
        MicrobeCreatorPanel panel = onEdt(MicrobeCreatorPanel::new);

        onEdt(() -> {
            panel.setSelectedMode(MicrobeCreatorPanel.SpawnMode.MICROBE);
            panel.setRandomEnabled(true);
        });

        MicrobeGeneProfile profile = onEdt(panel::currentMicrobeProfile);
        MicrobeGeneProfile expected = MicrobeSpawnRequest.defaultProfile();

        assertEquals(expected.heatResistance(), profile.heatResistance(), 0.0001);
        assertEquals(expected.toxinResistance(), profile.toxinResistance(), 0.0001);
        assertEquals(expected.speed(), profile.speed(), 0.0001);
        assertEquals(expected.diet(), profile.diet(), 0.0001);
        assertEquals(expected.maxHealth(), profile.maxHealth(), 0.0001);
        assertEquals(expected.maxEnergy(), profile.maxEnergy(), 0.0001);
    }

    @Test
    void amountValidationShouldClampToMinimumOne() throws Exception {
        MicrobeCreatorPanel panel = onEdt(MicrobeCreatorPanel::new);

        onEdt(() -> {
            panel.setSelectedMode(MicrobeCreatorPanel.SpawnMode.MICROBE);
            panel.setMicrobeAmount(0);
        });
        assertEquals(1, onEdt(panel::currentAmount));

        onEdt(() -> {
            panel.setSelectedMode(MicrobeCreatorPanel.SpawnMode.FOOD);
            panel.setFoodAmount(-5);
        });
        assertEquals(1, onEdt(panel::currentAmount));
        assertNotNull(onEdt(() -> panel.buildSpawnCommand(10, 20)));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}


