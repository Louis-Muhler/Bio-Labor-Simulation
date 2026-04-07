package com.biolab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineLookupTest {

    @Test
    void findRandomLivingMicrobeShouldNeverReturnDeadMicrobe() {
        SimulationEngine engine = new SimulationEngine(300, 300, 0);

        Microbe dead = new Microbe(100, 100, 0.0);
        Microbe alive = new Microbe(120, 120, 0.0);

        engine.spawnMicrobe(dead);
        engine.spawnMicrobe(alive);

        // Force a dead specimen that still exists in the world list until frame commit.
        dead.takeDamageAndTransferEnergy(10_000.0);
        assertTrue(dead.isDead());

        for (int i = 0; i < 250; i++) {
            Microbe picked = engine.findRandomLivingMicrobe();
            assertNotNull(picked);
            assertFalse(picked.isDead(), "Lookup returned a dead microbe");
            assertEquals(alive.getId(), picked.getId(), "Only the living microbe should be selectable");
        }

        engine.shutdown();
    }

    @Test
    void findRandomLivingMicrobeShouldReturnNullWhenNoLivingMicrobeExists() {
        SimulationEngine engine = new SimulationEngine(300, 300, 0);

        Microbe dead = new Microbe(100, 100, 0.0);
        engine.spawnMicrobe(dead);
        dead.takeDamageAndTransferEnergy(10_000.0);
        assertTrue(dead.isDead());

        assertNull(engine.findRandomLivingMicrobe());

        engine.shutdown();
    }

    @Test
    void spawnMicrobeShouldPreserveIdentityAndInstance() {
        SimulationEngine engine = new SimulationEngine(300, 300, 0);
        try {
            Microbe external = new Microbe(-25, 999, 0.2);
            long expectedId = external.getId();

            engine.spawnMicrobe(external);

            Microbe lookedUp = engine.findMicrobeById(expectedId);
            assertNotNull(lookedUp, "Spawned microbe should be indexed under its original ID");
            assertSame(external, lookedUp, "spawnMicrobe must not recreate a different Microbe instance");
            assertEquals(0.0, lookedUp.getX(), 0.0001, "X should be clamped to world min bound");
            assertEquals(300.0, lookedUp.getY(), 0.0001, "Y should be clamped to world max bound");
        } finally {
            engine.shutdown();
        }
    }
}
