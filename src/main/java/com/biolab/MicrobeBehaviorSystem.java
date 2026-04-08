package com.biolab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Applies per-microbe behaviour for one simulation frame chunk.
 */
final class MicrobeBehaviorSystem {
    private static final double BASE_COMBAT_DAMAGE = 6.4;
    private static final double MIN_COMBAT_DAMAGE = 1.5;
    private static final double MAX_COMBAT_DAMAGE = 14.0;
    private static final double ATTACK_RANGE_FACTOR = 1.45;
    private static final double BASE_KNOCKBACK_FORCE = 4.2;
    private static final double ENERGY_ABSORB_BASE = 0.64;
    private static final double ENERGY_ABSORB_DIET_FACTOR = 0.20;
    private static final double ENERGY_ABSORB_STRENGTH_FACTOR = 0.16;
    private static final double ENERGY_ABSORB_LOAD_PENALTY = 0.14;
    private static final int MAX_REPRODUCTION_ATTEMPTS = 5;
    private static final int MIN_RETRIES_BEFORE_BACKOFF = 2;
    private static final double HUNT_STEER_STRENGTH = 0.12;
    private static final double FLEE_STEER_STRENGTH = 0.18;
    private static final double FORAGE_STEER_BASE = 0.10;
    private static final double FORAGE_STEER_HUNGER_BOOST = 0.26;
    private static final double MAX_STEER_DELTA = 1.2;
    private static final long BASE_ATTACK_COOLDOWN_MS = 330;
    private static final long ATTACK_COOLDOWN_PER_LOAD_MS = 80;
    private static final int MAX_MICROBE_SIZE = 13;

    private final int worldWidth;
    private final int worldHeight;
    private final AtomicInteger availableReproductionSlots;
    private final List<Microbe> newMicrobes;

    MicrobeBehaviorSystem(int worldWidth,
                          int worldHeight,
                          AtomicInteger availableReproductionSlots,
                          List<Microbe> newMicrobes) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.availableReproductionSlots = availableReproductionSlots;
        this.newMicrobes = newMicrobes;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long attackCooldownMs(Microbe microbe) {
        return BASE_ATTACK_COOLDOWN_MS + Math.round(microbe.getCapacityLoad() * ATTACK_COOLDOWN_PER_LOAD_MS);
    }

    private static double computeScaledDamage(Microbe attacker, Microbe prey) {
        double offense = 0.70 + attacker.getStrengthTrait() * 0.85 + attacker.getSpeed() * 0.20;
        double offenseBulkPenalty = 1.0 - attacker.getCapacityLoad() * 0.16;
        double mitigation = 0.78 + prey.getDefenseTrait() * 0.60 + prey.getCapacityLoad() * 0.24;
        double sizeMultiplier = clamp(attacker.getSize() / (double) Math.max(1, prey.getSize()), 0.75, 1.45);
        double scaled = BASE_COMBAT_DAMAGE * offense * Math.max(0.70, offenseBulkPenalty) * sizeMultiplier / mitigation;
        return clamp(scaled, MIN_COMBAT_DAMAGE, MAX_COMBAT_DAMAGE);
    }

    private static double computeAbsorbEfficiency(Microbe attacker) {
        double efficiency = ENERGY_ABSORB_BASE
                + attacker.getDiet() * ENERGY_ABSORB_DIET_FACTOR
                + attacker.getStrengthTrait() * ENERGY_ABSORB_STRENGTH_FACTOR
                - attacker.getCapacityLoad() * ENERGY_ABSORB_LOAD_PENALTY;
        return clamp(efficiency, 0.42, 0.92);
    }

    void processChunk(List<Microbe> snapshot,
                      SpatialGrid foodGrid,
                      MicrobeGrid microbeGrid,
                      int start,
                      int end,
                      double temperature,
                      double toxicity) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Microbe> nearbyMicrobes = new ArrayList<>(64);
        List<FoodPellet> nearbyFood = new ArrayList<>(64);
        List<Microbe> newbornBuffer = new ArrayList<>(16);

        for (int i = start; i < end; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            Microbe microbe = snapshot.get(i);
            if (microbe.isDead()) continue;

            microbe.move(worldWidth, worldHeight);
            microbe.updateHealth(temperature, toxicity);

            double microbeQueryRadius = (microbe.getSize() + MAX_MICROBE_SIZE) * ATTACK_RANGE_FACTOR;
            microbeGrid.fillNearbyMicrobes(microbe.getX(), microbe.getY(), microbeQueryRadius, nearbyMicrobes);
            if (microbe.isCarnivore()) {
                processCarnivoreBehaviour(microbe, nearbyMicrobes);
            } else {
                foodGrid.fillNearbyFood(microbe.getX(), microbe.getY(), nearbyFood);
                processHerbivoreBehaviour(microbe, nearbyMicrobes, nearbyFood);
            }

            tryReproduce(microbe, random, newbornBuffer);
        }

        if (!newbornBuffer.isEmpty()) {
            synchronized (newMicrobes) {
                newMicrobes.addAll(newbornBuffer);
            }
        }
    }

    private void processCarnivoreBehaviour(Microbe microbe, List<Microbe> neighbours) {
        TargetCandidate preyCandidate = findNearestPrey(microbe, neighbours);
        Microbe prey = preyCandidate.target();
        if (prey == null) {
            microbe.setAiState(AiState.WANDER);
            return;
        }

        double dx = prey.getX() - microbe.getX();
        double dy = prey.getY() - microbe.getY();
        double distSq = preyCandidate.distSq();
        if (distSq <= 1e-12) {
            microbe.setAiState(AiState.WANDER);
            return;
        }
        double dist = Math.sqrt(distSq);

        microbe.setAiState(AiState.HUNT);
        microbe.setTargetX(prey.getX());
        microbe.setTargetY(prey.getY());

        double steerX = clamp((dx / dist) * microbe.getSpeed() * HUNT_STEER_STRENGTH,
                -MAX_STEER_DELTA, MAX_STEER_DELTA);
        double steerY = clamp((dy / dist) * microbe.getSpeed() * HUNT_STEER_STRENGTH,
                -MAX_STEER_DELTA, MAX_STEER_DELTA);
        microbe.applyKnockback(steerX, steerY);

        double attackRange = (microbe.getSize() + prey.getSize()) * ATTACK_RANGE_FACTOR;
        long now = System.currentTimeMillis();
        long cooldownMs = attackCooldownMs(microbe);
        if (dist >= attackRange || prey.isDead() || (now - microbe.getLastAttackTime()) < cooldownMs) {
            return;
        }

        double scaledDamage = computeScaledDamage(microbe, prey);

        double kbDist = Math.max(0.1, dist);
        double preyMass = 1.0 + prey.getCapacityLoad() * 0.50;
        double kScale = BASE_KNOCKBACK_FORCE * (0.75 + microbe.getStrengthTrait() * 0.40) / preyMass;
        double kx = (dx / kbDist) * kScale;
        double ky = (dy / kbDist) * kScale;

        double stolenEnergy = prey.takeDamageAndTransferEnergyWithKnockback(scaledDamage, kx, ky);
        microbe.eat(stolenEnergy * computeAbsorbEfficiency(microbe));
        microbe.markAttack();
    }

    private void processHerbivoreBehaviour(Microbe microbe, List<Microbe> neighbours, List<FoodPellet> nearbyFood) {
        for (FoodPellet food : nearbyFood) {
            if (food.checkCollision(microbe)) {
                double energyGain = food.consume();
                if (energyGain > 0) microbe.eat(energyGain);
                break;
            }
        }

        FoodCandidate bestFood = findNearestFood(microbe, nearbyFood);
        if (bestFood.food() != null) {
            double hunger = 1.0 - microbe.getEnergyRatio();
            double forageStrength = FORAGE_STEER_BASE + hunger * FORAGE_STEER_HUNGER_BOOST;
            microbe.steerTowards(bestFood.food().getX(), bestFood.food().getY(), forageStrength);
            microbe.setAiState(AiState.FORAGE);
            microbe.setTargetX(bestFood.food().getX());
            microbe.setTargetY(bestFood.food().getY());
        }

        TargetCandidate threatCandidate = findNearestThreat(microbe, neighbours);
        Microbe threat = threatCandidate.target();
        if (threat == null) {
            if (bestFood.food() == null) {
                microbe.setAiState(AiState.WANDER);
            }
            return;
        }

        double dx = microbe.getX() - threat.getX();
        double dy = microbe.getY() - threat.getY();
        double distSq = threatCandidate.distSq();
        if (distSq <= 1e-12) {
            microbe.setAiState(AiState.WANDER);
            return;
        }
        double dist = Math.sqrt(distSq);

        microbe.setAiState(AiState.FLEE);
        microbe.setTargetX(threat.getX());
        microbe.setTargetY(threat.getY());

        double steerX = clamp((dx / dist) * microbe.getSpeed() * FLEE_STEER_STRENGTH,
                -MAX_STEER_DELTA, MAX_STEER_DELTA);
        double steerY = clamp((dy / dist) * microbe.getSpeed() * FLEE_STEER_STRENGTH,
                -MAX_STEER_DELTA, MAX_STEER_DELTA);
        microbe.applyKnockback(steerX, steerY);
    }

    private FoodCandidate findNearestFood(Microbe microbe, List<FoodPellet> nearbyFood) {
        FoodPellet best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (FoodPellet food : nearbyFood) {
            if (food == null || food.isConsumed()) continue;
            double dx = food.getX() - microbe.getX();
            double dy = food.getY() - microbe.getY();
            double dSq = dx * dx + dy * dy;
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = food;
            }
        }
        return new FoodCandidate(best, bestDistSq);
    }

    private void tryReproduce(Microbe microbe, ThreadLocalRandom random, List<Microbe> newbornBuffer) {
        if (!microbe.canReproduce()) return;

        int retryCount = 0;
        while (retryCount < MAX_REPRODUCTION_ATTEMPTS) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            int currentSlots = availableReproductionSlots.get();
            if (currentSlots <= 0) break;

            if (availableReproductionSlots.compareAndSet(currentSlots, currentSlots - 1)) {
                double offsetX = (random.nextDouble() - 0.5) * 20;
                double offsetY = (random.nextDouble() - 0.5) * 20;
                Microbe child = new Microbe(
                        microbe,
                        microbe.getX() + offsetX,
                        microbe.getY() + offsetY
                );
                newbornBuffer.add(child);
                microbe.resetReproduction();
                break;
            }

            retryCount++;
            if (retryCount > MIN_RETRIES_BEFORE_BACKOFF) {
                Thread.yield();
            }
        }
    }

    private TargetCandidate findNearestPrey(Microbe microbe, List<Microbe> neighbours) {
        Microbe best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Microbe other : neighbours) {
            if (other == microbe || other.isDead() || other.isCarnivore()) continue;
            double dx = other.getX() - microbe.getX();
            double dy = other.getY() - microbe.getY();
            double dSq = dx * dx + dy * dy;
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = other;
            }
        }
        return new TargetCandidate(best, bestDistSq);
    }

    private TargetCandidate findNearestThreat(Microbe microbe, List<Microbe> neighbours) {
        Microbe best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Microbe other : neighbours) {
            if (other == microbe || other.isDead() || !other.isCarnivore()) continue;
            double dx = other.getX() - microbe.getX();
            double dy = other.getY() - microbe.getY();
            double dSq = dx * dx + dy * dy;
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = other;
            }
        }
        return new TargetCandidate(best, bestDistSq);
    }

    private record TargetCandidate(Microbe target, double distSq) {
    }

    private record FoodCandidate(FoodPellet food, double distSq) {
    }
}

