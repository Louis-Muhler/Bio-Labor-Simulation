package com.biolab;

/**
 * Spawn payload for creating one or more food pellets at a world position.
 */
public record FoodSpawnRequest(double worldX, double worldY, int amount) {
    public FoodSpawnRequest {
        amount = Math.max(1, amount);
    }
}

