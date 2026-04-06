package com.biolab;

import java.awt.*;

/**
 * Computes hover tooltip bounds for the chart area.
 */
public final class WorldStatsTooltipLayout {
    private WorldStatsTooltipLayout() {
    }

    public static Rectangle computeBounds(Rectangle plot, Point mouse, int tooltipWidth, int tooltipHeight, int offset) {
        if (plot == null || mouse == null) {
            return new Rectangle(0, 0, Math.max(1, tooltipWidth), Math.max(1, tooltipHeight));
        }

        int width = Math.max(1, Math.min(tooltipWidth, plot.width));
        int height = Math.max(1, Math.min(tooltipHeight, plot.height));

        int horizontalMidpoint = plot.x + (plot.width / 2);
        int preferredX = mouse.x <= horizontalMidpoint ? mouse.x + offset : mouse.x - width - offset;

        int minX = plot.x;
        int maxX = plot.x + plot.width - width;
        int x = Math.max(minX, Math.min(preferredX, maxX));

        int preferredY = mouse.y - (height / 2);
        int minY = plot.y;
        int maxY = plot.y + plot.height - height;
        int y = Math.max(minY, Math.min(preferredY, maxY));

        return new Rectangle(x, y, width, height);
    }
}

