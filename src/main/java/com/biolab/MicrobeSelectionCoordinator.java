package com.biolab;

import javax.swing.*;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Centralizes microbe selection state, inspector synchronization, and follow-camera behavior.
 */
final class MicrobeSelectionCoordinator {
    private final BooleanSupplier selectionBlockedSupplier;
    private final Supplier<SimulationRuntime> engineSupplier;
    private final Supplier<SimulationCanvas> canvasSupplier;
    private final Supplier<OverlayManager> overlaySupplier;
    private final Supplier<JLayeredPane> layeredPaneSupplier;

    private volatile Microbe selectedMicrobe;

    MicrobeSelectionCoordinator(BooleanSupplier selectionBlockedSupplier,
                                Supplier<SimulationRuntime> engineSupplier,
                                Supplier<SimulationCanvas> canvasSupplier,
                                Supplier<OverlayManager> overlaySupplier,
                                Supplier<JLayeredPane> layeredPaneSupplier) {
        this.selectionBlockedSupplier = selectionBlockedSupplier;
        this.engineSupplier = engineSupplier;
        this.canvasSupplier = canvasSupplier;
        this.overlaySupplier = overlaySupplier;
        this.layeredPaneSupplier = layeredPaneSupplier;
    }

    static OptionalLong findPersistedSelectedMicrobeId(SimulationState state) {
        if (state == null) {
            return OptionalLong.empty();
        }
        for (Microbe.PersistedState microbe : state.microbes()) {
            if (microbe.selected()) {
                return OptionalLong.of(microbe.id());
            }
        }
        return OptionalLong.empty();
    }

    void onMicrobeSelected(Microbe microbe) {
        if (selectionBlockedSupplier.getAsBoolean()) {
            return;
        }
        if (microbe == null) {
            forceClearSelection();
            return;
        }
        Microbe previous = selectedMicrobe;
        if (previous != null) {
            previous.setSelected(false);
        }
        selectedMicrobe = microbe;
        microbe.setSelected(true);
        OverlayManager overlayManager = overlaySupplier.get();
        if (overlayManager != null) {
            overlayManager.getInspectorPanel().setSelectedMicrobe(microbe);
            overlayManager.getInspectorPanel().showPanel();
        }
        SimulationCanvas canvas = canvasSupplier.get();
        if (canvas != null) {
            canvas.startFollowing(microbe);
        }
    }

    void onSelectionCleared() {
        if (selectionBlockedSupplier.getAsBoolean()) {
            return;
        }
        forceClearSelection();
    }

    void forceClearSelection() {
        Microbe previous = selectedMicrobe;
        if (previous != null) {
            previous.setSelected(false);
        }
        selectedMicrobe = null;

        SimulationCanvas canvas = canvasSupplier.get();
        if (canvas != null) {
            canvas.stopFollowing();
        }

        OverlayManager overlayManager = overlaySupplier.get();
        if (overlayManager != null) {
            overlayManager.getInspectorPanel().hidePanel();
        }

        JLayeredPane layeredPane = layeredPaneSupplier.get();
        if (layeredPane != null) {
            layeredPane.repaint();
        }
    }

    void restoreInspectorAfterWindowMotionIfNeeded() {
        if (selectionBlockedSupplier.getAsBoolean()) {
            return;
        }
        OverlayManager overlayManager = overlaySupplier.get();
        if (overlayManager == null) {
            return;
        }
        Microbe current = selectedMicrobe;
        if (current == null || current.isDead()) {
            return;
        }
        overlayManager.getInspectorPanel().setSelectedMicrobe(current);
        overlayManager.getInspectorPanel().showPanel();
    }

    void restoreInspectorSelectionFromLoadedState(SimulationState state) {
        OptionalLong selectedId = findPersistedSelectedMicrobeId(state);
        if (selectedId.isEmpty()) {
            forceClearSelection();
            return;
        }

        SimulationRuntime engine = engineSupplier.get();
        if (engine == null) {
            forceClearSelection();
            return;
        }

        Microbe selected = engine.findMicrobeById(selectedId.getAsLong());
        if (selected == null || selected.isDead()) {
            forceClearSelection();
            return;
        }
        onMicrobeSelected(selected);
    }

    void checkDeadSelectedMicrobe() {
        Microbe current = selectedMicrobe;
        if (current == null || !current.isDead()) {
            return;
        }

        SimulationRuntime engine = engineSupplier.get();
        if (engine == null) {
            forceClearSelection();
            return;
        }

        Microbe replacement = engine.findLivingChild(current.getId());
        if (replacement == null) {
            replacement = engine.findRandomLivingMicrobe();
        }

        final Microbe next = replacement;
        selectedMicrobe = null;

        SwingUtilities.invokeLater(() -> {
            current.setSelected(false);
            if (next != null) {
                onMicrobeSelected(next);
            } else {
                forceClearSelection();
            }
        });
    }
}

