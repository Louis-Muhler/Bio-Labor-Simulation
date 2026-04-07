package com.biolab;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Microbe class: gene generation, mutation, movement, energy,
 * reproduction, death, and ancestry tracking.
 */
class MicrobeTest {
    private static long persistedIdCounter = 1_000_000L;

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

    private static Microbe createFromPersistedState(double maxHealth,
                                                    double maxEnergy,
                                                    double health,
                                                    double energy,
                                                    int age) {
        return createFromPersistedState(maxHealth, maxEnergy, health, energy, age, 0.5, 0.5, 0.5, 0.5);
    }

    private static Microbe createFromPersistedState(double maxHealth,
                                                    double maxEnergy,
                                                    double health,
                                                    double energy,
                                                    int age,
                                                    double heatResistance,
                                                    double toxinResistance,
                                                    double speed,
                                                    double diet) {
        long id = persistedIdCounter++;
        Microbe.PersistedState state = new Microbe.PersistedState(
                id,
                -1,
                1,
                100.0,
                100.0,
                0.0,
                0.0,
                heatResistance,
                toxinResistance,
                speed,
                diet,
                maxHealth,
                maxEnergy,
                health,
                energy,
                age,
                false,
                0L,
                -1.0,
                -1.0,
                AiState.WANDER,
                0L,
                List.of()
        );
        return Microbe.fromPersistedState(state);
    }

    @Test
    void newMicrobeShouldHaveFullHealthAndInitialEnergy() {
        Microbe m = new Microbe(50, 50);
        assertEquals(m.getMaxHealth(), m.getHealth(), 0.001);
        assertTrue(m.getEnergy() > 0, "Energy should be positive");
        assertTrue(m.getEnergy() <= m.getMaxEnergy(), "Energy should be capped by individual maxEnergy");
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
    void seedMicrobesShouldHaveIndividualMaxValues() {
        Microbe first = new Microbe(50, 50);
        Microbe second = new Microbe(60, 60);
        assertNotEquals(first.getMaxHealth(), second.getMaxHealth(), "Seed microbes should not share a fixed maxHealth cap");
        assertNotEquals(first.getMaxEnergy(), second.getMaxEnergy(), "Seed microbes should not share a fixed maxEnergy cap");
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
        assertEquals(parent.getAbsoluteGeneration(), child.getAncestry().get(0).generation(),
                "First ancestry entry should be the direct parent generation for first child");
    }

    @Test
    void ancestryShouldUseSmartThinningAndKeepPatientZeroAnchor() {
        // Create a chain of generations
        Microbe current = new Microbe(100, 100);
        for (int i = 0; i < 20; i++) {
            current = new Microbe(current, 100, 100);
        }

        var ancestry = current.getAncestry();
        assertFalse(ancestry.isEmpty(), "Deep lineage should retain ancestry snapshots");
        assertTrue(ancestry.size() <= 32,
                "Ancestry should be limited to MAX_SNAPSHOTS (32), was: " + ancestry.size());

        int patientZeroGeneration = ancestry.get(0).generation();
        assertTrue(patientZeroGeneration == 0 || patientZeroGeneration == 1,
                "0th ancestry index should be Patient Zero (gen 0 or 1), was: " + patientZeroGeneration);

        int lastGeneration = ancestry.get(ancestry.size() - 1).generation();
        assertEquals(current.getAbsoluteGeneration() - 1, lastGeneration,
                "Newest ancestry snapshot should stay anchored to the direct parent generation");

        for (int i = 1; i < ancestry.size(); i++) {
            assertTrue(ancestry.get(i).generation() > ancestry.get(i - 1).generation(),
                    "Smart-thinned ancestry must stay in strictly increasing absolute-generation order");
        }
    }

    @Test
    void ancestryOverflowShouldRemapToFixedThirtyTwoWithGlobalCoverageInvariants() {
        Microbe current = new Microbe(100, 100);
        for (int i = 0; i < 900; i++) {
            current = new Microbe(current, 100, 100);
        }

        List<AncestorSnapshot> ancestry = current.getAncestry();
        assertEquals(32, ancestry.size(), "Bei tiefer Linie muss auf 32 Snapshots remapped werden");
        assertEquals(1, ancestry.get(0).generation(), "Erste Generation muss als Anchor erhalten bleiben");
        assertEquals(current.getAbsoluteGeneration() - 1, ancestry.get(ancestry.size() - 1).generation(),
                "Letzter Snapshot muss der direkte Parent sein");

        HashSet<Integer> generations = new HashSet<>();
        for (int i = 0; i < ancestry.size(); i++) {
            int gen = ancestry.get(i).generation();
            assertTrue(generations.add(gen), "Keine doppelten Generationen in der remappten Ancestry");
            if (i > 0) {
                assertTrue(gen > ancestry.get(i - 1).generation(),
                        "Remap muss strikt aufsteigende absolute Generationen liefern");
            }
        }
    }

    @Test
    void ancestryShouldBeUnmodifiable() throws Exception {
        Microbe parent = new Microbe(100, 100);
        Microbe child = new Microbe(parent, 100, 100);
        java.lang.reflect.Method addMethod = java.util.List.class.getMethod("add", Object.class);
        try {
            addMethod.invoke(child.getAncestry(), new AncestorSnapshot(0, 0, 0, 0, 0, 0, 0));
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

    @Test
    void spawnedProfilesShouldProduceDistinctTraitColors() {
        Microbe heat = Microbe.createSpawned(0, 0, 1.0, 0.0, 0.0, 0.0, 100, 100);
        Microbe toxin = Microbe.createSpawned(0, 0, 0.0, 1.0, 0.0, 0.0, 100, 100);
        Microbe speed = Microbe.createSpawned(0, 0, 0.0, 0.0, 1.0, 0.0, 100, 100);
        Microbe diet = Microbe.createSpawned(0, 0, 0.0, 0.0, 0.0, 1.0, 100, 100);

        assertNotEquals(heat.getColor().getRGB(), toxin.getColor().getRGB());
        assertNotEquals(heat.getColor().getRGB(), speed.getColor().getRGB());
        assertNotEquals(heat.getColor().getRGB(), diet.getColor().getRGB());
        assertNotEquals(toxin.getColor().getRGB(), speed.getColor().getRGB());
        assertNotEquals(toxin.getColor().getRGB(), diet.getColor().getRGB());
    }

    @Test
    void higherToxinShouldAppearDarkerAndGreenerThanLowToxin() {
        Microbe lowToxin = Microbe.createSpawned(0, 0, 0.0, 0.2, 0.0, 0.0, 100, 100);
        Microbe highToxin = Microbe.createSpawned(0, 0, 0.0, 1.0, 0.0, 0.0, 100, 100);

        Color low = lowToxin.getColor();
        Color high = highToxin.getColor();

        assertTrue(high.getGreen() >= high.getRed(), "High toxin should keep a green-dominant hue");
        int lowLum = low.getRed() + low.getGreen() + low.getBlue();
        int highLum = high.getRed() + high.getGreen() + high.getBlue();
        assertTrue(highLum < lowLum, "High toxin should render darker than low toxin");
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

    @RepeatedTest(30)
    void childShouldInheritAndMutateIndividualMaxValues() {
        Microbe parent = new Microbe(100, 100);
        Microbe child = new Microbe(parent, 100, 100);

        assertTrue(child.getMaxHealth() > 0);
        assertTrue(child.getMaxEnergy() > 0);
        assertTrue(Math.abs(child.getMaxHealth() - parent.getMaxHealth()) <= parent.getMaxHealth() * 0.06 + 0.001,
                "maxHealth mutation should stay close to the parent");
        assertTrue(Math.abs(child.getMaxEnergy() - parent.getMaxEnergy()) <= parent.getMaxEnergy() * 0.06 + 0.001,
                "maxEnergy mutation should stay close to the parent");
    }

    @Test
    void eatShouldNotExceedMaxEnergy() {
        Microbe m = new Microbe(100, 100);
        m.eat(99999);
        assertTrue(m.getEnergy() <= m.getMaxEnergy(),
                "Energy should be capped at MAX_ENERGY");
    }

    @Test
    void ratioCalculationShouldUseIndividualMaxValues() {
        Microbe m = createFromPersistedState(200.0, 320.0, 50.0, 80.0, 12);
        assertEquals(0.25, m.getHealthRatio(), 0.0001);
        assertEquals(0.25, m.getEnergyRatio(), 0.0001);
    }

    @Test
    void eatShouldRespectIndividualMaxEnergyCap() {
        Microbe m = createFromPersistedState(180.0, 300.0, 90.0, 20.0, 10);
        m.eat(500.0);
        assertEquals(300.0, m.getEnergy(), 0.001, "Energy should clamp to the individual maxEnergy");
    }

    @Test
    void reproductionThresholdsAndCostsShouldUseIndividualMaxValues() {
        Microbe m = createFromPersistedState(220.0, 150.0, 150.0, 130.0, 80);
        assertTrue(m.canReproduce(), "Reproduction should use ratio thresholds over individual max caps");

        double healthBefore = m.getHealth();
        double energyBefore = m.getEnergy();
        m.resetReproduction();
        assertEquals(0, m.getAge());
        assertTrue(m.getHealth() < healthBefore, "Reproduction should consume health");
        assertTrue(m.getEnergy() < energyBefore, "Reproduction should consume energy");
        assertTrue((healthBefore - m.getHealth()) / m.getMaxHealth() > 0.30,
                "High-capacity builds should pay extra health cost");
        assertTrue((energyBefore - m.getEnergy()) / m.getMaxEnergy() > 0.32,
                "High-capacity builds should pay extra energy cost");
    }

    @Test
    void reproductionThresholdShouldScaleWithIndividualCapacityLoad() {
        Microbe baseline = createFromPersistedState(100.0, 100.0, 60.0, 66.0, 80);
        Microbe highCapacity = createFromPersistedState(220.0, 220.0, 140.0, 145.0, 80);

        assertTrue(baseline.canReproduce(), "Baseline build should pass at 66% energy");
        assertFalse(highCapacity.canReproduce(),
                "High-capacity build should need a higher relative energy threshold");
    }

    @Test
    void movementCostShouldIncreaseForHighCapacityBuilds() {
        Microbe baseline = createFromPersistedState(100.0, 100.0, 100.0, 92.0, 0, 0.5, 0.5, 0.5, 0.5);
        Microbe highCapacity = createFromPersistedState(220.0, 220.0, 220.0, 202.0, 0, 0.5, 0.5, 0.5, 0.5);

        double baselineBefore = baseline.getEnergy();
        double highBefore = highCapacity.getEnergy();

        baseline.move(2000, 2000);
        highCapacity.move(2000, 2000);

        double baselineCost = baselineBefore - baseline.getEnergy();
        double highCost = highBefore - highCapacity.getEnergy();
        assertTrue(highCost > baselineCost,
                "High maxHealth/maxEnergy should increase movement upkeep cost");
    }

    @Test
    void damageTransferShouldNotFavorTankVictimsPerDamagePoint() {
        Microbe baselineVictim = createFromPersistedState(100.0, 100.0, 100.0, 100.0, 20);
        Microbe tankVictim = createFromPersistedState(220.0, 220.0, 220.0, 220.0, 20);

        double baselineTransfer = baselineVictim.takeDamageAndTransferEnergy(20.0);
        double tankTransfer = tankVictim.takeDamageAndTransferEnergy(20.0);

        assertTrue(baselineTransfer >= 0.0 && tankTransfer >= 0.0);
        assertTrue(tankTransfer < baselineTransfer,
                "Tanky victims should leak less energy per equal raw damage");
        assertTrue(baselineTransfer <= 100.0 && tankTransfer <= 220.0,
                "Transferred energy must never exceed victim's available energy");
    }

    @Test
    void combinedDamageAndKnockbackShouldApplyBothEffects() {
        Microbe victim = createFromPersistedState(180.0, 180.0, 160.0, 140.0, 20, 0.8, 0.8, 0.2, 0.2);
        Microbe.PersistedState before = victim.toPersistedState();

        double transferred = victim.takeDamageAndTransferEnergyWithKnockback(22.0, 3.0, -2.0);
        Microbe.PersistedState after = victim.toPersistedState();

        assertTrue(transferred >= 0.0, "Transfer should be non-negative");
        assertTrue(after.health() < before.health(), "Combined call should apply damage");
        assertTrue(after.velocityX() != before.velocityX() || after.velocityY() != before.velocityY(),
                "Combined call should also apply knockback velocity");
    }
}

