package com.lolfm.simulator;

import java.util.Optional;

public class LaneStructureState {
    private boolean outerTowerAlive = true;
    private boolean innerTowerAlive = true;
    private boolean inhibitorTowerAlive = true;
    private boolean inhibitorAlive = true;
    private final StructureHealthState outerTowerHealth =
            new StructureHealthState(StructureRuleConfig.OUTER_TURRET_MAX_HEALTH);
    private final StructureHealthState innerTowerHealth =
            new StructureHealthState(StructureRuleConfig.INNER_TURRET_MAX_HEALTH);
    private final StructureHealthState inhibitorTowerHealth =
            new StructureHealthState(StructureRuleConfig.INHIBITOR_TURRET_MAX_HEALTH);
    private final StructureHealthState inhibitorHealth =
            new StructureHealthState(StructureRuleConfig.INHIBITOR_MAX_HEALTH);
    private int outerDestroyedAtSeconds = -1;
    private TeamSide outerDestroyedBySide;
    private StructureActionSource outerDestroyedBySource;
    private int inhibitorDestroyedAtSeconds = -1;

    public boolean isOuterTowerAlive() { return outerTowerAlive; }
    public boolean isInnerTowerAlive() { return innerTowerAlive; }
    public boolean isInhibitorTowerAlive() { return inhibitorTowerAlive; }
    public boolean isInhibitorAlive() { return inhibitorAlive; }
    public double getOuterRemainingIntegrity() {
        return outerTowerHealth.getCurrentHealth() / outerTowerHealth.getMaxHealth()
                * LanePhaseRuleConfig.OUTER_TURRET_MAX_INTEGRITY;
    }
    public double getTowerCurrentHealth(TowerTier tier) { return health(tier).getCurrentHealth(); }
    public double getTowerMaxHealth(TowerTier tier) { return health(tier).getMaxHealth(); }
    public double getInhibitorCurrentHealth() { return inhibitorHealth.getCurrentHealth(); }
    public double getInhibitorMaxHealth() { return inhibitorHealth.getMaxHealth(); }
    public int getOuterDestroyedAtSeconds() { return outerDestroyedAtSeconds; }
    public TeamSide getOuterDestroyedBySide() { return outerDestroyedBySide; }
    public StructureActionSource getOuterDestroyedBySource() { return outerDestroyedBySource; }
    public int getInhibitorDestroyedAtSeconds() { return inhibitorDestroyedAtSeconds; }
    public boolean isInhibitorDestroyed() { return !inhibitorAlive; }

    public double applyOuterDamage(double damage) {
        if (!outerTowerAlive || damage <= 0) return getOuterRemainingIntegrity();
        double rawDamage = damage / LanePhaseRuleConfig.OUTER_TURRET_MAX_INTEGRITY
                * outerTowerHealth.getMaxHealth();
        outerTowerHealth.applyDamage(rawDamage);
        return getOuterRemainingIntegrity();
    }
    public double applyTowerDamage(TowerTier tier, double damage) {
        if (!canDestroy(tier)) return health(tier).getCurrentHealth();
        return health(tier).applyDamage(damage);
    }
    public int claimReachedTowerPlates(TowerTier tier) { return health(tier).claimReachedPlates(); }
    public double applyInhibitorDamage(double damage) {
        if (!isInhibitorVulnerable()) return inhibitorHealth.getCurrentHealth();
        return inhibitorHealth.applyDamage(damage);
    }
    public Optional<TowerTier> nextAliveTower() {
        if (outerTowerAlive) return Optional.of(TowerTier.OUTER);
        if (innerTowerAlive) return Optional.of(TowerTier.INNER);
        if (inhibitorTowerAlive) return Optional.of(TowerTier.INHIBITOR);
        return Optional.empty();
    }
    public boolean canDestroy(TowerTier tier) { return nextAliveTower().filter(next -> next == tier).isPresent(); }
    void destroy(TowerTier tier) { destroy(tier, -1, null, StructureActionSource.MACRO_PLAY); }
    public void destroy(TowerTier tier,int time,TeamSide destroyingSide,StructureActionSource source) {
        if (!canDestroy(tier)) throw new IllegalStateException("Tower destruction order violation: " + tier);
        switch (tier) {
            case OUTER -> {
                outerTowerAlive=false;
                outerTowerHealth.forceDestroy();
                outerDestroyedAtSeconds=time;
                outerDestroyedBySide=destroyingSide;
                outerDestroyedBySource=source;
            }
            case INNER -> {
                innerTowerAlive=false;
                innerTowerHealth.forceDestroy();
            }
            case INHIBITOR -> {
                inhibitorTowerAlive=false;
                inhibitorTowerHealth.forceDestroy();
            }
        }
    }
    public boolean isInhibitorVulnerable() { return nextAliveTower().isEmpty() && inhibitorAlive; }
    public boolean destroyInhibitor(int currentTimeSeconds) {
        if (!isInhibitorVulnerable()) return false;
        inhibitorAlive=false;
        inhibitorHealth.forceDestroy();
        inhibitorDestroyedAtSeconds=currentTimeSeconds;
        return true;
    }
    public boolean refreshAt(int currentTimeSeconds) {
        if (inhibitorAlive || inhibitorDestroyedAtSeconds < 0
                || currentTimeSeconds < inhibitorDestroyedAtSeconds
                + StructureRuleConfig.INHIBITOR_RESPAWN_SECONDS) return false;
        inhibitorAlive = true;
        inhibitorHealth.restoreFull();
        inhibitorDestroyedAtSeconds = -1;
        return true;
    }
    public int destroyedTowerCount() { int value=0; if(!outerTowerAlive)value++; if(!innerTowerAlive)value++; if(!inhibitorTowerAlive)value++; return value; }

    private StructureHealthState health(TowerTier tier) {
        return switch (tier) {
            case OUTER -> outerTowerHealth;
            case INNER -> innerTowerHealth;
            case INHIBITOR -> inhibitorTowerHealth;
        };
    }
}
