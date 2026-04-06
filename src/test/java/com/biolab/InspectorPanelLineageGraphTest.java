package com.biolab;

import org.junit.jupiter.api.Test;

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
}

