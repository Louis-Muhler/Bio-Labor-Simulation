package com.biolab;

import java.util.List;

/**
 * Immutable render snapshot shared from simulation thread to EDT.
 */
public record SimulationSnapshot(List<Microbe.RenderState> microbes, List<FoodPellet> food) {
}

