package com.biolab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationLoopControllerTest {

    @Test
    void cycleSpeedShouldIncludeMaxStageAndWrapAround() {
        SimulationLoopController controller = new SimulationLoopController(
                null,
                null,
                () -> {
                },
                population -> {
                }
        );

        assertEquals("2x", controller.cycleSpeed());
        assertEquals("5x", controller.cycleSpeed());
        assertEquals("10x", controller.cycleSpeed());
        assertEquals("25x", controller.cycleSpeed());
        assertEquals("50x", controller.cycleSpeed());
        assertEquals("100x", controller.cycleSpeed());
        assertEquals("MAX", controller.cycleSpeed());
        assertEquals("1x", controller.cycleSpeed());
    }

    @Test
    void resetSpeedShouldAlwaysReturnOneX() {
        SimulationLoopController controller = new SimulationLoopController(
                null,
                null,
                () -> {
                },
                population -> {
                }
        );

        controller.cycleSpeed();
        controller.cycleSpeed();
        assertEquals("1x", controller.resetSpeedToDefault());
        assertEquals("2x", controller.cycleSpeed());
    }
}


