package com.biolab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseWheelEvent;

/**
 * Shared wheel-only scroll behavior for overlay panels.
 */
final class OverlayScrollSupport {
    private OverlayScrollSupport() {
    }

    static JScrollPane createWheelOnlyScrollPane(JComponent content, Insets insets, int unitIncrement) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(new EmptyBorder(insets));
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        int unit = Math.max(8, unitIncrement);
        vertical.setUnitIncrement(unit);
        vertical.setBlockIncrement(unit * 3);

        java.awt.event.MouseWheelListener wheelHandler = e -> applyWheelDelta(scrollPane, e, unit);
        scrollPane.addMouseWheelListener(wheelHandler);
        scrollPane.getViewport().addMouseWheelListener(wheelHandler);
        content.addMouseWheelListener(wheelHandler);
        return scrollPane;
    }

    private static void applyWheelDelta(JScrollPane scrollPane, MouseWheelEvent e, int unit) {
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        if (bar == null) {
            return;
        }
        int maxValue = Math.max(bar.getMinimum(), bar.getMaximum() - bar.getVisibleAmount());
        double preciseRotation = e.getPreciseWheelRotation();
        double effectiveRotation = preciseRotation != 0.0
                ? preciseRotation
                : (double) e.getWheelRotation();

        int baseIncrement;
        if (e.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL) {
            baseIncrement = Math.max(1, bar.getBlockIncrement());
        } else {
            baseIncrement = Math.max(1, unit * Math.max(1, e.getScrollAmount()));
        }

        int delta = (int) Math.round(effectiveRotation * baseIncrement);
        if (delta == 0 && effectiveRotation != 0.0) {
            delta = effectiveRotation > 0.0 ? 1 : -1;
        }

        int next = Math.max(bar.getMinimum(), Math.min(maxValue, bar.getValue() + delta));
        if (next != bar.getValue()) {
            bar.setValue(next);
        }
        // Always consume to keep wheel events inside the overlay and avoid canvas zoom side effects.
        e.consume();
    }
}

