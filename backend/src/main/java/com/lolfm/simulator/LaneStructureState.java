package com.lolfm.simulator;

import java.util.Optional;

public class LaneStructureState {
    private boolean outerTowerAlive = true;
    private boolean innerTowerAlive = true;
    private boolean inhibitorTowerAlive = true;
    private boolean inhibitorAlive = true;
    private double outerRemainingIntegrity = LanePhaseRuleConfig.OUTER_TURRET_MAX_INTEGRITY;
    private int outerDestroyedAtSeconds = -1;
    private TeamSide outerDestroyedBySide;
    private StructureActionSource outerDestroyedBySource;
    private int inhibitorDestroyedAtSeconds = -1;

    public boolean isOuterTowerAlive() { return outerTowerAlive; }
    public boolean isInnerTowerAlive() { return innerTowerAlive; }
    public boolean isInhibitorTowerAlive() { return inhibitorTowerAlive; }
    public boolean isInhibitorAlive() { return inhibitorAlive; }
    public double getOuterRemainingIntegrity() { return outerRemainingIntegrity; }
    public int getOuterDestroyedAtSeconds() { return outerDestroyedAtSeconds; }
    public TeamSide getOuterDestroyedBySide() { return outerDestroyedBySide; }
    public StructureActionSource getOuterDestroyedBySource() { return outerDestroyedBySource; }
    public int getInhibitorDestroyedAtSeconds() { return inhibitorDestroyedAtSeconds; }
    public boolean isInhibitorDestroyed() { return !inhibitorAlive; }

    public double applyOuterDamage(double damage) {
        if (!outerTowerAlive || damage <= 0) return outerRemainingIntegrity;
        outerRemainingIntegrity = Math.max(0, outerRemainingIntegrity - damage);
        return outerRemainingIntegrity;
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
                outerRemainingIntegrity=0;
                outerDestroyedAtSeconds=time;
                outerDestroyedBySide=destroyingSide;
                outerDestroyedBySource=source;
            }
            case INNER -> innerTowerAlive=false;
            case INHIBITOR -> inhibitorTowerAlive=false;
        }
    }
    public boolean isInhibitorVulnerable() { return nextAliveTower().isEmpty() && inhibitorAlive; }
    public boolean destroyInhibitor(int currentTimeSeconds) {
        if (!isInhibitorVulnerable()) return false;
        inhibitorAlive=false; inhibitorDestroyedAtSeconds=currentTimeSeconds; return true;
    }
    public boolean refreshAt(int currentTimeSeconds) {
        if (inhibitorAlive || inhibitorDestroyedAtSeconds < 0
                || currentTimeSeconds < inhibitorDestroyedAtSeconds
                + StructureRuleConfig.INHIBITOR_RESPAWN_SECONDS) return false;
        inhibitorAlive = true;
        inhibitorDestroyedAtSeconds = -1;
        return true;
    }
    public int destroyedTowerCount() { int value=0; if(!outerTowerAlive)value++; if(!innerTowerAlive)value++; if(!inhibitorTowerAlive)value++; return value; }
}
