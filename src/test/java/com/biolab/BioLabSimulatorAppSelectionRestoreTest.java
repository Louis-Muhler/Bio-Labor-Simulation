package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BioLabSimulatorAppSelectionRestoreTest {

    @Test
    void findPersistedSelectedMicrobeIdShouldReturnEmptyWhenStateIsNull() {
        OptionalLong selected = BioLabSimulatorApp.findPersistedSelectedMicrobeId(null);
        assertTrue(selected.isEmpty());
    }

    @Test
    void findPersistedSelectedMicrobeIdShouldReturnEmptyWhenNothingIsSelected() {
        Microbe m1 = new Microbe(10, 10);
        Microbe m2 = new Microbe(20, 20);

        SimulationState state = new SimulationState(
                100,
                100,
                0.2,
                0.1,
                0.5,
                0L,
                List.of(),
                List.of(m1.toPersistedState(), m2.toPersistedState()),
                List.of(),
                false
        );

        OptionalLong selected = BioLabSimulatorApp.findPersistedSelectedMicrobeId(state);
        assertTrue(selected.isEmpty());
    }

    @Test
    void findPersistedSelectedMicrobeIdShouldReturnFirstSelectedMicrobeId() {
        Microbe m1 = new Microbe(10, 10);
        Microbe m2 = new Microbe(20, 20);
        Microbe m3 = new Microbe(30, 30);
        m2.setSelected(true);
        m3.setSelected(true);

        SimulationState state = new SimulationState(
                100,
                100,
                0.2,
                0.1,
                0.5,
                0L,
                List.of(),
                List.of(m1.toPersistedState(), m2.toPersistedState(), m3.toPersistedState()),
                List.of(),
                false
        );

        OptionalLong selected = BioLabSimulatorApp.findPersistedSelectedMicrobeId(state);
        assertTrue(selected.isPresent());
        assertEquals(m2.getId(), selected.getAsLong());
    }
}

