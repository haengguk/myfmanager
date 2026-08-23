package com.lolfm.simulator;

import java.util.ArrayList;
import java.util.List;

public class BaseState {
    private int nexusTurretsRemaining = 2;
    private boolean nexusAlive = true;
    private int nexusDestroyedAtSeconds = -1;
    private final List<Integer> destroyedNexusTurretTimes = new ArrayList<>();

    public int getNexusTurretsRemaining() { return nexusTurretsRemaining; }
    public boolean hasNexusTurrets() { return nexusTurretsRemaining > 0; }
    public boolean areAllNexusTurretsDestroyed() { return nexusTurretsRemaining == 0; }
    public boolean isNexusAlive() { return nexusAlive; }
    public int getNexusDestroyedAtSeconds() { return nexusDestroyedAtSeconds; }
    public boolean destroyOneNexusTurret() {
        return destroyOneNexusTurret(-1);
    }
    public boolean destroyOneNexusTurret(int currentTimeSeconds) {
        if (nexusTurretsRemaining <= 0 || !nexusAlive) return false;
        nexusTurretsRemaining--;
        destroyedNexusTurretTimes.add(currentTimeSeconds);
        return true;
    }
    public int refreshAt(int currentTimeSeconds) {
        if (!nexusAlive) return 0;
        int restored = 0;
        for (int index = destroyedNexusTurretTimes.size() - 1; index >= 0; index--) {
            int destroyedAt = destroyedNexusTurretTimes.get(index);
            if (destroyedAt >= 0 && currentTimeSeconds >= destroyedAt
                    + StructureRuleConfig.NEXUS_TURRET_RESPAWN_SECONDS) {
                destroyedNexusTurretTimes.remove(index);
                nexusTurretsRemaining = Math.min(2, nexusTurretsRemaining + 1);
                restored++;
            }
        }
        return restored;
    }
    public boolean destroyNexus(int currentTimeSeconds) {
        if (!nexusAlive || nexusTurretsRemaining > 0) return false;
        nexusAlive = false;
        nexusDestroyedAtSeconds = currentTimeSeconds;
        return true;
    }
}
