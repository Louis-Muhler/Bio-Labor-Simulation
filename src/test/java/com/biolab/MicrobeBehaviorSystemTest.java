package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrobeBehaviorSystemTest {
    private static long persistedIdCounter = 2_000_000L;

    private static Microbe createPersistedMicrobe(double x,
                                                  double y,
                                                  double heat,
                                                  double toxin,
                                                  double speed,
                                                  double diet,
                                                  double maxHealth,
                                                  double maxEnergy,
                                                  double health,
                                                  double energy) {
        Microbe.PersistedState state = new Microbe.PersistedState(
                persistedIdCounter++,
                -1,
                1,
                x,
                y,
                0.0,
                0.0,
                heat,
                toxin,
                speed,
                diet,
                maxHealth,
                maxEnergy,
                health,
                energy,
                200,
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

    private static void runOneChunk(List<Microbe> microbes) {
        runOneChunk(microbes, 30);
    }

    private static void runOneChunk(List<Microbe> microbes, int cellSize) {
        SpatialGrid foodGrid = new SpatialGrid(600, 600, cellSize);
        foodGrid.rebuild(List.of());

        MicrobeGrid microbeGrid = new MicrobeGrid(600, 600, cellSize);
        microbeGrid.rebuild(microbes);

        MicrobeBehaviorSystem behaviorSystem = new MicrobeBehaviorSystem(
                600,
                600,
                new AtomicInteger(0),
                new ArrayList<>()
        );

        behaviorSystem.processChunk(microbes, foodGrid, microbeGrid, 0, microbes.size(), 0.0, 0.0);
    }

    @Test
    void combatShouldDealLessDamageToHigherBulkTargets() {
        Microbe attackerA = createPersistedMicrobe(100, 100, 0.3, 0.3, 0.6, 1.0, 100.0, 100.0, 100.0, 95.0);
        Microbe lightPrey = createPersistedMicrobe(103, 102, 0.2, 0.2, 0.4, 0.0, 100.0, 100.0, 100.0, 80.0);
        List<Microbe> scenarioLight = new ArrayList<>(List.of(attackerA, lightPrey));

        double lightBefore = lightPrey.getHealth();
        runOneChunk(scenarioLight);
        double lightDamage = lightBefore - lightPrey.getHealth();

        Microbe attackerB = createPersistedMicrobe(100, 100, 0.3, 0.3, 0.6, 1.0, 100.0, 100.0, 100.0, 95.0);
        Microbe tankPrey = createPersistedMicrobe(103, 102, 0.8, 0.8, 0.3, 0.0, 220.0, 220.0, 220.0, 175.0);
        List<Microbe> scenarioTank = new ArrayList<>(List.of(attackerB, tankPrey));

        double tankBefore = tankPrey.getHealth();
        runOneChunk(scenarioTank);
        double tankDamage = tankBefore - tankPrey.getHealth();

        assertTrue(lightDamage > 0.0, "Attack should deal damage to light prey");
        assertTrue(tankDamage > 0.0, "Attack should still deal some damage to tank prey");
        assertTrue(tankDamage < lightDamage, "Tank prey should mitigate more incoming damage");
    }

    @Test
    void cooldownShouldBlockImmediateSecondAttack() {
        Microbe attacker = createPersistedMicrobe(100, 100, 0.3, 0.3, 0.4, 1.0, 100.0, 100.0, 100.0, 25.0);
        Microbe prey = createPersistedMicrobe(102, 100, 0.3, 0.3, 0.2, 0.0, 120.0, 120.0, 120.0, 80.0);
        List<Microbe> microbes = new ArrayList<>(List.of(attacker, prey));

        runOneChunk(microbes);
        double healthAfterFirstHit = prey.getHealth();

        runOneChunk(microbes);
        double healthAfterSecondStep = prey.getHealth();

        assertTrue(healthAfterFirstHit < 120.0, "First step should land one attack");
        assertEquals(healthAfterFirstHit, healthAfterSecondStep, 0.0001,
                "Second immediate step should be blocked by attack cooldown");
    }

    @Test
    void attackRangeShouldNotMissPreyAcrossSecondCellRing() {
        // distance=32 is inside attack range for large bodies, but outside a strict 3x3 lookup from col 0.
        Microbe attacker = createPersistedMicrobe(29, 100, 1.0, 1.0, 0.0, 1.0,
                220.0, 220.0, 220.0, 20.0);
        Microbe prey = createPersistedMicrobe(61, 100, 1.0, 1.0, 0.0, 0.0,
                220.0, 220.0, 220.0, 20.0);
        List<Microbe> microbes = new ArrayList<>(List.of(attacker, prey));

        double preyBefore = prey.getHealth();
        runOneChunk(microbes, 30);

        assertTrue(prey.getHealth() < preyBefore,
                "Carnivore should damage reachable prey even when it is in the second cell ring");
    }
}
