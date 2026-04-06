package com.biolab;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStatsPanelTooltipLayoutTest {

    @Test
    void tooltipShouldBePlacedRightOfMouseWhenMouseIsOnLeftHalf() {
        Rectangle plot = new Rectangle(40, 16, 420, 240);
        Point mouse = new Point(100, 100);

        Rectangle bounds = WorldStatsTooltipLayout.computeBounds(plot, mouse, 180, 90, 12);

        assertTrue(bounds.x > mouse.x, "Tooltip should be right of mouse on left side");
        assertTrue(plot.contains(bounds.x, bounds.y));
    }

    @Test
    void tooltipShouldBePlacedLeftOfMouseWhenMouseIsOnRightHalf() {
        Rectangle plot = new Rectangle(40, 16, 420, 240);
        Point mouse = new Point(420, 120);

        Rectangle bounds = WorldStatsTooltipLayout.computeBounds(plot, mouse, 180, 90, 12);

        assertTrue(bounds.x + bounds.width < mouse.x, "Tooltip should be left of mouse on right side");
    }

    @Test
    void tooltipShouldClampInsidePlot() {
        Rectangle plot = new Rectangle(40, 16, 300, 180);
        Point mouse = new Point(338, 24);

        Rectangle bounds = WorldStatsTooltipLayout.computeBounds(plot, mouse, 260, 160, 12);

        assertTrue(bounds.x >= plot.x);
        assertTrue(bounds.y >= plot.y);
        assertTrue(bounds.x + bounds.width <= plot.x + plot.width);
        assertTrue(bounds.y + bounds.height <= plot.y + plot.height);
    }

    @Test
    void nullInputsShouldReturnSafeFallbackBounds() {
        Rectangle bounds = WorldStatsTooltipLayout.computeBounds(null, null, 0, 0, 12);
        assertEquals(1, bounds.width);
        assertEquals(1, bounds.height);
    }
}


