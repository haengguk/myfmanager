package com.lolfm.simulator;

import java.util.Optional;

public class LaneStructureState {
    private boolean outerTowerAlive = true;
    private boolean innerTowerAlive = true;
    private boolean inhibitorTowerAlive = true;
    private boolean inhibitorAlive = true;
    private int inhibitorDestroyedAtSeconds = -1;

    public boolean isOuterTowerAlive() { return outerTowerAlive; }
    public boolean isInnerTowerAlive() { return innerTowerAlive; }
    public boolean isInhibitorTowerAlive() { return inhibitorTowerAlive; }
    public boolean isInhibitorAlive() { return inhibitorAlive; }
    public int getInhibitorDestroyedAtSeconds() { return inhibitorDestroyedAtSeconds; }
    public boolean isInhibitorDestroyed() { return !inhibitorAlive; }

    public Optional<TowerTier> nextAliveTower() {
        if (outerTowerAlive) return Optional.of(TowerTier.OUTER);
        if (innerTowerAlive) return Optional.of(TowerTier.INNER);
        if (inhibitorTowerAlive) return Optional.of(TowerTier.INHIBITOR);
        return Optional.empty();
    }
    public boolean canDestroy(TowerTier tier) { return nextAliveTower().filter(next -> next == tier).isPresent(); }
    public void destroy(TowerTier tier) {
        if (!canDestroy(tier)) throw new IllegalStateException("Tower destruction order violation: " + tier);
        switch (tier) { case OUTER -> outerTowerAlive=false; case INNER -> innerTowerAlive=false; case INHIBITOR -> inhibitorTowerAlive=false; }
    }
    public boolean isInhibitorVulnerable() { return nextAliveTower().isEmpty() && inhibitorAlive; }
    public boolean destroyInhibitor(int currentTimeSeconds) {
        if (!isInhibitorVulnerable()) return false;
        inhibitorAlive=false; inhibitorDestroyedAtSeconds=currentTimeSeconds; return true;
    }
    public int destroyedTowerCount() { int value=0; if(!outerTowerAlive)value++; if(!innerTowerAlive)value++; if(!inhibitorTowerAlive)value++; return value; }
}
