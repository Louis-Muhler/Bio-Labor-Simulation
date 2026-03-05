package com.biolab;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Microbe class: gene generation, mutation, movement, energy,
 * reproduction, death, and ancestry tracking.
 */
class MicrobeTest {

    // ===== Gene Generation =====


    @RepeatedTest(20)
    void randomGenesShouldBeInZeroOneRange() {
        Microbe m = new Microbe(100, 100);
        assertAll(
                () -> assertTrue(m.getHeatResistance() >= 0.0 && m.getHeatResistance() <= 1.0,
                        "heatResistance out of range: " + m.getHeatResistance()),
                () -> assertTrue(m.getToxinResistance() >= 0.0 && m.getToxinResistance() <= 1.0,
                        "toxinResistance out of range: " + m.getToxinResistance()),
                () -> assertTrue(m.getSpeed() >= 0.0 && m.getSpeed() <= 1.0,
                        "speed out of range: " + m.getSpeed())
        );
    }

    // ===== Mutation =====

    @Test
    void newMicrobeShouldHaveFullHealthAndInitialEnergy() {
        Microbe m = new Microbe(50, 50);
        assertEquals(Microbe.getMaxHealth(), m.getHealth(), 0.001);
        assertTrue(m.getEnergy() > 0, "Energy should be positive");
        assertEquals(0, m.getAge());
    }

    // ===== Movement =====

    @RepeatedTest(50)
    void mutationShouldKeepGenesInBounds() {
        Microbe parent = new Microbe(100, 100);
        Microbe child = new Microbe(parent, 100, 100);
        assertAll(
                () -> assertTrue(child.getHeatResistance() >= 0.0 && child.getHeatResistance() <= 1.0),
                () -> assertTrue(child.getToxinResistance() >= 0.0 && child.getToxinResistance() <= 1.0),
                () -> assertTrue(child.getSpeed() >= 0.0 && child.getSpeed() <= 1.0)
        );
    }

    @Test
    void moveShouldKeepPositionWithinWorldBounds() {
        // Place microbe at a corner and move many times
        Microbe m = new Microbe(0, 0);
        int worldSize = 1000;
        for (int i = 0; i < 1000; i++) {
            m.move(worldSize, worldSize);
        }
        assertTrue(m.getX() >= 0 && m.getX() <= worldSize,
                "X out of bounds: " + m.getX());
        assertTrue(m.getY() >= 0 && m.getY() <= worldSize,
                "Y out of bounds: " + m.getY());
    }

    // ===== Health & Death =====

    @Test
    void moveShouldConsumeEnergy() {
        Microbe m = new Microbe(500, 500);
        double initialEnergy = m.getEnergy();
        m.move(1000, 1000);
        assertTrue(m.getEnergy() < initialEnergy, "Energy should decrease after moving");
    }

    @Test
    void highEnvironmentalDamageShouldReduceHealth() {
        Microbe m = new Microbe(100, 100);
        double initialHealth = m.getHealth();
        // Extreme environment
        for (int i = 0; i < 100; i++) {
            m.updateHealth(1.0, 1.0);
        }
        assertTrue(m.getHealth() < initialHealth, "Health should decrease under extreme conditions");
    }

    @Test
    void microbeWithZeroHealthShouldBeDead() {
        Microbe m = new Microbe(100, 100);
        // Drain health via extreme environment
        for (int i = 0; i < 10000; i++) {
            m.updateHealth(1.0, 1.0);
            if (m.isDead()) break;
        }
        assertTrue(m.isDead(), "Microbe should eventually die under extreme conditions");
    }

    // ===== Reproduction =====

    @Test
    void microbeWithZeroEnergyShouldBeDead() {
        Microbe m = new Microbe(100, 100);
        // Drain energy via movement
        for (int i = 0; i < 100000; i++) {
            m.move(10000, 10000);
            if (m.isDead()) break;
        }
        assertTrue(m.isDead(), "Microbe should eventually die from energy depletion");
    }

    @Test
    void newMicrobeShouldNotBeAbleToReproduce() {
        Microbe m = new Microbe(100, 100);
        assertFalse(m.canReproduce(), "New microbe should not be able to reproduce immediately");
    }

    // ===== Ancestry =====

    @Test
    void resetReproductionShouldReduceHealthAndEnergy() {
        Microbe m = new Microbe(100, 100);
        double healthBefore = m.getHealth();
        double energyBefore = m.getEnergy();
        m.resetReproduction();
        assertTrue(m.getHealth() < healthBefore);
        assertTrue(m.getEnergy() < energyBefore);
        assertEquals(0, m.getAge());
    }

    @Test
    void newMicrobeShouldHaveEmptyAncestry() {
        Microbe m = new Microbe(100, 100);
        assertTrue(m.getAncestry().isEmpty());
    }

    @Test
    void childShouldHaveParentInAncestry() {
        Microbe parent = new Microbe(100, 100);
        Microbe child = new Microbe(parent, 100, 100);

        assertFalse(child.getAncestry().isEmpty(), "Child should have ancestry");
        assertEquals(0, child.getAncestry().get(0).generation(), "First ancestor should be generation 0 (parent)");
    }

    @Test
    void ancestryDepthShouldBeLimited() {
        // Create a chain of generations
        Microbe current = new Microbe(100, 100);
        for (int i = 0; i < 20; i++) {
            current = new Microbe(current, 100, 100);
        }
        assertTrue(current.getAncestry().size() <= 5,
                "Ancestry should be limited to MAX_ANCESTRY_DEPTH (5), was: " + current.getAncestry().size());
    }

    @Test
    void ancestryShouldBeUnmodifiable() throws Exception {
        Microbe parent = new Microbe(100, 100);
        Microbe child = new Microbe(parent, 100, 100);
        java.lang.reflect.Method addMethod = java.util.List.class.getMethod("add", Object.class);
        try {
            addMethod.invoke(child.getAncestry(), new AncestorSnapshot(0, 0, 0, 0, 0));
            fail("Expected UnsupportedOperationException – ancestry list must be unmodifiable");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertInstanceOf(UnsupportedOperationException.class, e.getCause(),
                    "getAncestry() must return an unmodifiable list");
        }
    }

    @Test
    void getColorShouldReturnSameInstance() {
        Microbe m = new Microbe(100, 100);
        Color first = m.getColor();
        Color second = m.getColor();
        assertSame(first, second, "Color should be cached (same instance)");
    }

    @Test
    void getBrightColorShouldReturnSameInstance() {
        Microbe m = new Microbe(100, 100);
        Color first = m.getBrightColor();
        Color second = m.getBrightColor();
        assertSame(first, second, "BrightColor should be cached");
    }

    // ===== Selection =====

    @Test
    void selectionShouldToggle() {
        Microbe m = new Microbe(100, 100);
        assertFalse(m.isSelected());
        m.setSelected(true);
        assertTrue(m.isSelected());
        m.setSelected(false);
        assertFalse(m.isSelected());
    }

    // ===== Contains (click detection) =====

    @Test
    void containsShouldDetectPointInsideMicrobe() {
        Microbe m = new Microbe(100, 100);
        assertTrue(m.contains(100, 100), "Center point should be inside");
        assertTrue(m.contains(102, 102), "Point within SIZE radius should be inside");
    }

    @Test
    void containsShouldRejectDistantPoint() {
        Microbe m = new Microbe(100, 100);
        assertFalse(m.contains(200, 200), "Distant point should be outside");
    }

    // ===== Eating =====

    @Test
    void eatShouldIncreaseEnergy() {
        Microbe m = new Microbe(100, 100);
        // First drain some energy
        m.move(1000, 1000);
        double before = m.getEnergy();
        m.eat(10.0);
        assertTrue(m.getEnergy() > before, "Energy should increase after eating");
    }

    @Test
    void eatShouldNotExceedMaxEnergy() {
        Microbe m = new Microbe(100, 100);
        m.eat(99999);
        assertTrue(m.getEnergy() <= Microbe.getMaxEnergy(),
                "Energy should be capped at MAX_ENERGY");
    }
}

