package com.biolab;

import java.awt.*;

/**
 * Shared microbe rendering primitives to avoid style drift across UI components.
 */
final class MicrobeRenderStyle {
    private static final AlphaComposite AC_BRIGHT_FILL = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 220f / 255f);
    private static final AlphaComposite[][] GLOW_COMPOSITES = buildGlowTable();

    private MicrobeRenderStyle() {
    }

    private static AlphaComposite[][] buildGlowTable() {
        AlphaComposite[][] table = new AlphaComposite[11][3];
        for (int h = 0; h <= 10; h++) {
            double healthRatio = h / 10.0;
            for (int i = 0; i < 3; i++) {
                int layer = 3 - i;
                float alpha = (float) ((20 + layer * 15) * healthRatio / 255.0);
                alpha = Math.max(0.0f, Math.min(1.0f, alpha));
                table[h][i] = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha);
            }
        }
        return table;
    }

    static void drawCore(Graphics2D g2d,
                         int x,
                         int y,
                         int size,
                         Color baseColor,
                         Color brightColor,
                         double healthRatio,
                         Composite defaultComposite) {
        int healthBucket = Math.max(0, Math.min(10, (int) (healthRatio * 10)));
        for (int i = 0; i < 3; i++) {
            int layer = 3 - i;
            g2d.setComposite(GLOW_COMPOSITES[healthBucket][i]);
            g2d.setColor(baseColor);
            int gs = size + (layer * 4);
            g2d.fillOval(x - layer * 2, y - layer * 2, gs, gs);
        }
        g2d.setComposite(AC_BRIGHT_FILL);
        g2d.setColor(brightColor);
        g2d.fillOval(x, y, size, size);
        g2d.setComposite(defaultComposite);
        g2d.setColor(baseColor);
        g2d.fillOval(x + 1, y + 1, size - 2, size - 2);
    }
}

