package com.biolab;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectorPanelLineageGraphTest {

    @Test
    void downsamplingShouldKeepExactlyTenPointsWithFirstLastAndNoDuplicates() {
        List<Integer> indices = InspectorPanel.selectEvenlyDistributedIndices(101, InspectorPanel.MAX_VISIBLE_LINEAGE_POINTS);

        assertEquals(10, indices.size());
        assertEquals(0, indices.get(0));
        assertEquals(100, indices.get(indices.size() - 1));
        assertEquals(indices.size(), new HashSet<>(indices).size(), "Indizes duerfen keine Duplikate enthalten");

        for (int i = 1; i < indices.size(); i++) {
            assertTrue(indices.get(i) > indices.get(i - 1), "Indizes muessen streng aufsteigend sein");
        }
    }

    @Test
    void downsamplingShouldShowAllPointsWhenFewerThanTenExist() {
        List<Integer> indices = InspectorPanel.selectEvenlyDistributedIndices(7, InspectorPanel.MAX_VISIBLE_LINEAGE_POINTS);

        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), indices);
    }

    @Test
    void slotXPositionsShouldBeUniformlyDistributedForTenPoints() {
        List<Integer> xs = InspectorPanel.computeUniformSlotXPositions(5, 300, 10);

        assertEquals(10, xs.size());
        assertEquals(5, xs.get(0));
        assertEquals(305, xs.get(xs.size() - 1));

        int minStep = Integer.MAX_VALUE;
        int maxStep = Integer.MIN_VALUE;
        for (int i = 1; i < xs.size(); i++) {
            int step = xs.get(i) - xs.get(i - 1);
            minStep = Math.min(minStep, step);
            maxStep = Math.max(maxStep, step);
        }

        // Bei Integer-Rundung duerfen sich die Steps maximal um 1 px unterscheiden.
        assertTrue(maxStep - minStep <= 1, "X-Abstaende muessen praktisch gleichmaessig sein");
    }

    private static List<Integer> pickVisibleGenerations(List<Integer> timelineGenerations, int maxVisible) {
        List<Integer> indices = InspectorPanel.selectEvenlyDistributedIndices(timelineGenerations.size(), maxVisible);
        List<Integer> visible = new ArrayList<>(indices.size());
        for (Integer idx : indices) {
            visible.add(timelineGenerations.get(idx));
        }
        return visible;
    }

    private static double meanAbsIdealSlotError(List<Integer> generations, int firstGen, int lastGen) {
        int n = generations.size();
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double ideal = firstGen + i * (double) (lastGen - firstGen) / (n - 1);
            sum += Math.abs(generations.get(i) - ideal);
        }
        return sum / n;
    }

    private static List<Integer> simulateOldLocalRemoveHeuristic(int currentGeneration, int capacity) {
        List<Integer> ancestry = new ArrayList<>();
        for (int parentGeneration = 1; parentGeneration < currentGeneration; parentGeneration++) {
            ancestry.add(parentGeneration);
            if (ancestry.size() > capacity) {
                double idealGap = parentGeneration / (double) (capacity - 1);
                double minCost = Double.MAX_VALUE;
                int indexToRemove = 1;
                for (int i = 1; i < ancestry.size() - 1; i++) {
                    int gapIfRemoved = ancestry.get(i + 1) - ancestry.get(i - 1);
                    double cost = Math.abs(gapIfRemoved - idealGap);
                    if (cost < minCost) {
                        minCost = cost;
                        indexToRemove = i;
                    }
                }
                ancestry.remove(indexToRemove);
            }
        }
        return ancestry;
    }

    @Test
    void highGenerationLineageShouldBeCloserToIdealDistributionThanOldTenSnapshotHeuristic() {
        final int targetGeneration = 635;

        // Neue reale Basis: Microbe-intern 32 Snapshots, Inspector zeigt weiter 10.
        Microbe current = new Microbe(100, 100);
        for (int g = 2; g <= targetGeneration; g++) {
            current = new Microbe(current, 100, 100);
        }
        List<Integer> newTimeline = new ArrayList<>();
        for (AncestorSnapshot a : current.getAncestry()) {
            newTimeline.add(a.generation());
        }
        newTimeline.add(current.getAbsoluteGeneration());
        List<Integer> newVisible = pickVisibleGenerations(newTimeline, InspectorPanel.MAX_VISIBLE_LINEAGE_POINTS);

        // Alte Referenz: fruehere lokale Remove-Heuristik mit interner Kapazitaet 10.
        List<Integer> oldAncestry = simulateOldLocalRemoveHeuristic(targetGeneration, 10);
        List<Integer> oldTimeline = new ArrayList<>(oldAncestry);
        oldTimeline.add(targetGeneration);
        List<Integer> oldVisible = pickVisibleGenerations(oldTimeline, InspectorPanel.MAX_VISIBLE_LINEAGE_POINTS);

        double oldError = meanAbsIdealSlotError(oldVisible, 1, targetGeneration);
        double newError = meanAbsIdealSlotError(newVisible, 1, targetGeneration);

        assertEquals(1, newVisible.get(0));
        assertEquals(targetGeneration, newVisible.get(newVisible.size() - 1));
        assertTrue(newError < oldError,
                "Die neue 32er Basis soll die sichtbaren 10 Punkte naeher an die ideale Historienverteilung bringen");
    }

    @Test
    void vitalSignsFormatterShouldShowCurrentMaxAndPercent() {
        assertEquals("133/556 (23.9%)", InspectorPanel.formatCurrentMaxWithPercent(133.0, 556.0));
        assertEquals("0/0 (0.0%)", InspectorPanel.formatCurrentMaxWithPercent(5.0, 0.0));
    }
}

