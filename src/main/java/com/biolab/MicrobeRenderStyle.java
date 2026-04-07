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
        drawCore(g2d, x, y, size, baseColor, brightColor, healthRatio, 1.0, defaultComposite);
    }

    static void drawCore(Graphics2D g2d,
                         int x,
                         int y,
                         int size,
                         Color baseColor,
                         Color brightColor,
                         double healthRatio,
                         double glowScale,
                         Composite defaultComposite) {
        drawGlow(g2d, x, y, size, baseColor, healthRatio, glowScale, defaultComposite);
        drawBody(g2d, x, y, size, baseColor, brightColor, defaultComposite);
    }

    static void drawGlow(Graphics2D g2d,
                         int x,
                         int y,
                         int size,
                         Color baseColor,
                         double healthRatio,
                         double glowScale,
                         Composite defaultComposite) {
        int healthBucket = Math.max(0, Math.min(10, (int) (healthRatio * 10)));
        int perSideGrowth = Math.max(2, (int) Math.round(2.0 * Math.max(1.0, glowScale)));
        for (int i = 0; i < 3; i++) {
            int layer = 3 - i;
            g2d.setComposite(GLOW_COMPOSITES[healthBucket][i]);
            g2d.setColor(baseColor);
            int gs = size + (layer * perSideGrowth * 2);
            g2d.fillOval(x - layer * perSideGrowth, y - layer * perSideGrowth, gs, gs);
        }
        g2d.setComposite(defaultComposite);
    }

    static void drawBody(Graphics2D g2d,
                         int x,
                         int y,
                         int size,
                         Color baseColor,
                         Color brightColor,
                         Composite defaultComposite) {
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

    static void drawPredatorSpikes(Graphics2D g2d,
                                   double centerX,
                                   double centerY,
                                   int size,
                                   double defense,
                                   double strength,
                                   Color coreColor,
                                   Composite defaultComposite) {
        float clampedStrength = (float) Math.max(0.0, Math.min(1.0, strength));
        float clampedDefense = (float) Math.max(0.0, Math.min(1.0, defense));
        float aggression = (float) Math.max(0.0, Math.min(1.0,
                clampedStrength * 0.80f + clampedDefense * 0.20f));
        if (aggression > 0.01f) {
            int spikes = 4 + (int) Math.round(aggression * 6.0);
            double coreRadius = size * 0.50;
            int innerR = Math.max(1, (int) Math.round(coreRadius * 0.20));
            int tipR = (int) Math.round(coreRadius + size * (0.10 + aggression * 0.18));
            int cx = (int) centerX;
            int cy = (int) centerY;
            int spikeAlpha = Math.max(100, Math.min(220, (int) (90 + aggression * 130)));
            int lineAlpha = Math.max(120, Math.min(235, (int) (120 + aggression * 115)));
            Color spikeColor = new Color(
                    Math.min(255, coreColor.getRed() + 25),
                    Math.min(255, coreColor.getGreen() + 25),
                    Math.min(255, coreColor.getBlue() + 25),
                    spikeAlpha
            );
            Color spikeLineColor = new Color(
                    Math.min(255, coreColor.getRed() + 45),
                    Math.min(255, coreColor.getGreen() + 45),
                    Math.min(255, coreColor.getBlue() + 45),
                    lineAlpha
            );

            Object oldAa = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setStroke(STROKE_1);
            for (int s = 0; s < spikes; s++) {
                double angle = (Math.PI * 2.0 * s) / spikes;
                double wing = (Math.PI / spikes) * 0.40;

                int xLeft = cx + (int) Math.round(Math.cos(angle - wing) * innerR);
                int yLeft = cy + (int) Math.round(Math.sin(angle - wing) * innerR);
                int xRight = cx + (int) Math.round(Math.cos(angle + wing) * innerR);
                int yRight = cy + (int) Math.round(Math.sin(angle + wing) * innerR);
                int xTip = cx + (int) Math.round(Math.cos(angle) * tipR);
                int yTip = cy + (int) Math.round(Math.sin(angle) * tipR);

                Polygon spike = new Polygon(
                        new int[]{xLeft, xTip, xRight},
                        new int[]{yLeft, yTip, yRight},
                        3
                );
                g2d.setColor(spikeColor);
                g2d.fillPolygon(spike);

                g2d.setColor(spikeLineColor);
                g2d.drawLine(cx, cy, xTip, yTip);
            }
            g2d.setComposite(defaultComposite);
            g2d.setStroke(STROKE_1);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
        }
    }

    static void drawCombatAndSelectionOverlays(Graphics2D g2d,
                                               int x,
                                               int y,
                                               int size,
                                               boolean carnivore,
                                               long lastAttackTime,
                                               long nowMs,
                                               boolean selected,
                                               boolean showSelectionRing,
                                               Composite defaultComposite) {

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

        if (showSelectionRing && selected) {
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

