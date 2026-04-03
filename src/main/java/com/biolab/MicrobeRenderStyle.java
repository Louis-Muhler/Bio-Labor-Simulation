package com.biolab;

import java.awt.*;

/**
 * Shared microbe rendering primitives to avoid style drift across UI components.
 */
final class MicrobeRenderStyle {
    private static final AlphaComposite AC_BRIGHT_FILL = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 220f / 255f);
    private static final AlphaComposite[][] GLOW_COMPOSITES = buildGlowTable();
    private static final int DEFAULT_CARNIVORE_SIZE_BONUS = 2;
    private static final long ATTACK_FLASH_DURATION_MS = 300;

    private static final Color DEFENSE_RING_COLOR_BASE = new Color(160, 230, 255);
    private static final Color STRENGTH_SPIKE_COLOR = new Color(255, 210, 120, 160);
    private static final Color ATTACK_RING_COLOR = new Color(255, 30, 30);
    private static final Color ATTACK_RING_GLOW = new Color(255, 60, 60, 120);
    private static final Color SELECTION_GLOW_COLOR = new Color(0, 255, 255, 100);
    private static final Color SELECTION_SOLID_COLOR = new Color(0, 255, 255);

    private static final BasicStroke STROKE_1 = new BasicStroke(1f);
    private static final BasicStroke STROKE_2 = new BasicStroke(2f);
    private static final BasicStroke STROKE_3 = new BasicStroke(3f);
    private static final BasicStroke STROKE_ATTACK = new BasicStroke(2.5f);

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

    static int sizeWithCarnivoreBonus(int baseSize, boolean carnivore) {
        return sizeWithCarnivoreBonus(baseSize, carnivore, DEFAULT_CARNIVORE_SIZE_BONUS);
    }

    static int sizeWithCarnivoreBonus(int baseSize, boolean carnivore, int carnivoreBonus) {
        return baseSize + (carnivore ? Math.max(0, carnivoreBonus) : 0);
    }

    static void drawCombatAndSelectionOverlays(Graphics2D g2d,
                                               int x,
                                               int y,
                                               int size,
                                               double centerX,
                                               double centerY,
                                               double defense,
                                               double strength,
                                               boolean carnivore,
                                               long lastAttackTime,
                                               long nowMs,
                                               boolean selected,
                                               Composite defaultComposite) {
        float clampedDefense = (float) Math.max(0.0, Math.min(1.0, defense));
        if (clampedDefense > 0.2f) {
            g2d.setColor(new Color(
                    DEFENSE_RING_COLOR_BASE.getRed(),
                    DEFENSE_RING_COLOR_BASE.getGreen(),
                    DEFENSE_RING_COLOR_BASE.getBlue(),
                    (int) (40 + clampedDefense * 100)
            ));
            g2d.setStroke(new BasicStroke(1.0f + clampedDefense * 1.8f));
            g2d.drawOval(x - 2, y - 2, size + 4, size + 4);
            g2d.setStroke(STROKE_1);
        }

        float clampedStrength = (float) Math.max(0.0, Math.min(1.0, strength));
        if (clampedStrength > 0.45f) {
            int spikes = 4 + (int) Math.round(clampedStrength * 4.0);
            int outerR = (int) Math.round(size * 0.65 + 2 + clampedStrength * 3.0);
            int innerR = Math.max(2, outerR - 3);
            int cx = (int) centerX;
            int cy = (int) centerY;
            g2d.setColor(STRENGTH_SPIKE_COLOR);
            for (int s = 0; s < spikes; s++) {
                double angle = (Math.PI * 2.0 * s) / spikes;
                int x1 = cx + (int) Math.round(Math.cos(angle) * innerR);
                int y1 = cy + (int) Math.round(Math.sin(angle) * innerR);
                int x2 = cx + (int) Math.round(Math.cos(angle) * (outerR + 2));
                int y2 = cy + (int) Math.round(Math.sin(angle) * (outerR + 2));
                g2d.drawLine(x1, y1, x2, y2);
            }
        }

        long msSinceAttack = nowMs - lastAttackTime;
        if (carnivore && msSinceAttack < ATTACK_FLASH_DURATION_MS) {
            float flashAlpha = Math.max(0.0f, Math.min(1.0f,
                    1.0f - (float) msSinceAttack / ATTACK_FLASH_DURATION_MS));
            int ringPad = 5;
            int ringX = x - ringPad;
            int ringY = y - ringPad;
            int ringW = size + ringPad * 2;
            int ringH = size + ringPad * 2;

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha * 0.55f));
            g2d.setColor(ATTACK_RING_GLOW);
            g2d.setStroke(STROKE_3);
            g2d.drawOval(ringX - 2, ringY - 2, ringW + 4, ringH + 4);

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha * 0.95f));
            g2d.setColor(ATTACK_RING_COLOR);
            g2d.setStroke(STROKE_ATTACK);
            g2d.drawOval(ringX, ringY, ringW, ringH);

            g2d.setComposite(defaultComposite);
            g2d.setStroke(STROKE_1);
        }

        if (selected) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(SELECTION_GLOW_COLOR);
            g2d.setStroke(STROKE_3);
            g2d.drawOval(x - 5, y - 5, size + 10, size + 10);
            g2d.setColor(SELECTION_SOLID_COLOR);
            g2d.setStroke(STROKE_2);
            g2d.drawOval(x - 4, y - 4, size + 8, size + 8);
            g2d.setStroke(STROKE_1);
            g2d.drawOval(x - 3, y - 3, size + 6, size + 6);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }
    }
}

