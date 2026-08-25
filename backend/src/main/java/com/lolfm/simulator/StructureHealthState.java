package com.lolfm.simulator;

/** Mutable durability owned by one match-scoped structure state. */
public final class StructureHealthState {
    private final double maxHealth;
    private double currentHealth;
    private int claimedPlateCount;

    public StructureHealthState(double maxHealth) {
        if (maxHealth <= 0) throw new IllegalArgumentException("maxHealth must be positive");
        this.maxHealth = maxHealth;
        this.currentHealth = maxHealth;
    }

    public double getMaxHealth() { return maxHealth; }
    public double getCurrentHealth() { return currentHealth; }
    public boolean isAlive() { return currentHealth > 0; }
    public int getClaimedPlateCount() { return claimedPlateCount; }

    public double applyDamage(double damage) {
        if (!isAlive() || damage <= 0) return currentHealth;
        currentHealth = Math.max(0.0, currentHealth - damage);
        return currentHealth;
    }

    public int claimReachedPlates() {
        double missingRatio = 1.0 - currentHealth / maxHealth;
        int reached = 0;
        for (double threshold : StructureRuleConfig.TURRET_PLATE_MISSING_HEALTH_THRESHOLDS) {
            if (missingRatio + 1e-9 >= threshold) reached++;
        }
        int newlyClaimed = Math.max(0, reached - claimedPlateCount);
        claimedPlateCount = Math.max(claimedPlateCount, reached);
        return newlyClaimed;
    }

    public void forceDestroy() {
        currentHealth = 0.0;
        claimedPlateCount = StructureRuleConfig.TURRET_PLATE_COUNT;
    }

    public void restoreFull() {
        currentHealth = maxHealth;
        claimedPlateCount = 0;
    }

    public void restoreAtRatio(double ratio) {
        if (ratio <= 0 || ratio > 1) throw new IllegalArgumentException("ratio must be in (0, 1]");
        currentHealth = maxHealth * ratio;
        // A regenerated Nexus turret must not pay the same plate rewards again.
        claimedPlateCount = Math.max(claimedPlateCount, reachedPlateCount(currentHealth));
    }

    private int reachedPlateCount(double health) {
        double missingRatio = 1.0 - health / maxHealth;
        int reached = 0;
        for (double threshold : StructureRuleConfig.TURRET_PLATE_MISSING_HEALTH_THRESHOLDS) {
            if (missingRatio + 1e-9 >= threshold) reached++;
        }
        return reached;
    }
}
