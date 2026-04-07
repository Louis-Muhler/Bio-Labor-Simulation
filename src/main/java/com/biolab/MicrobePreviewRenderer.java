package com.biolab;

import java.awt.*;

/**
 * Shared renderer for enlarged single-microbe previews used by overlays.
 */
final class MicrobePreviewRenderer {
    private MicrobePreviewRenderer() {
    }

    static void paintPreview(Graphics2D g2,
                             Microbe.RenderState renderState,
                             int centerX,
                             int centerY,
                             double scale) {
        if (renderState == null) {
            return;
        }

        int scaledCarnivoreBonus = (int) Math.max(1, Math.round(2.0 * scale));
        int size = MicrobeRenderStyle.sizeWithCarnivoreBonus(
                (int) (renderState.size() * scale),
                renderState.carnivore(),
                scaledCarnivoreBonus
        );
        int x = centerX - size / 2;
        int y = centerY - size / 2;

        Composite original = g2.getComposite();
        MicrobeRenderStyle.drawGlow(g2, x, y, size, renderState.color(), renderState.healthRatio(), scale, original);
        MicrobeRenderStyle.drawPredatorSpikes(
                g2,
                centerX,
                centerY,
                size,
                renderState.defense(),
                renderState.strength(),
                renderState.color(),
                1.35,
                scale,
                2,
                original
        );
        MicrobeRenderStyle.drawBody(
                g2,
                x,
                y,
                size,
                renderState.color(),
                renderState.brightColor(),
                MicrobeRenderStyle.computeRimStrokeWidth(size, renderState.defense(), 2.75),
                2f,
                original
        );
        MicrobeRenderStyle.drawCombatAndSelectionOverlays(
                g2,
                x,
                y,
                size,
                renderState.carnivore(),
                renderState.lastAttackTime(),
                System.currentTimeMillis(),
                renderState.selected(),
                false,
                original
        );
    }
}

