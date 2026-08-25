package com.lolfm.simulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BaseState {
    private final StructureHealthState[] nexusTurrets = {
            new StructureHealthState(StructureRuleConfig.NEXUS_TURRET_MAX_HEALTH),
            new StructureHealthState(StructureRuleConfig.NEXUS_TURRET_MAX_HEALTH)
    };
    private final int[] destroyedNexusTurretTimes = {-1, -1};
    private final List<Integer> respawnedNexusTurretIndices = new ArrayList<>();
    private final StructureHealthState nexusHealth =
            new StructureHealthState(StructureRuleConfig.NEXUS_MAX_HEALTH);
    private boolean nexusAlive = true;
    private int nexusDestroyedAtSeconds = -1;

    public int getNexusTurretsRemaining() {
        return (int) Arrays.stream(nexusTurrets).filter(StructureHealthState::isAlive).count();
    }
    public boolean hasNexusTurrets() { return getNexusTurretsRemaining() > 0; }
    public boolean areAllNexusTurretsDestroyed() { return getNexusTurretsRemaining() == 0; }
    public boolean isNexusAlive() { return nexusAlive; }
    public int getNexusDestroyedAtSeconds() { return nexusDestroyedAtSeconds; }
    public double getNexusCurrentHealth() { return nexusHealth.getCurrentHealth(); }
    public double getNexusMaxHealth() { return nexusHealth.getMaxHealth(); }
    public List<Double> getNexusTurretCurrentHealths() {
        return Arrays.stream(nexusTurrets).map(StructureHealthState::getCurrentHealth).toList();
    }
    public double getNexusTurretCurrentHealth(int index) { return nexusTurret(index).getCurrentHealth(); }
    public double getNexusTurretMaxHealth(int index) { return nexusTurret(index).getMaxHealth(); }
    public int nextAliveNexusTurretIndex() {
        for (int index = 0; index < nexusTurrets.length; index++) if (nexusTurrets[index].isAlive()) return index;
        return -1;
    }
    public boolean destroyOneNexusTurret() {
        return destroyOneNexusTurret(-1);
    }
    public boolean destroyOneNexusTurret(int currentTimeSeconds) {
        int index = nextAliveNexusTurretIndex();
        if (index < 0 || !nexusAlive) return false;
        nexusTurrets[index].forceDestroy();
        destroyedNexusTurretTimes[index] = currentTimeSeconds;
        return true;
    }
    public double applyNexusTurretDamage(int index, double damage, int currentTimeSeconds) {
        StructureHealthState turret = nexusTurret(index);
        if (!nexusAlive || !turret.isAlive()) return turret.getCurrentHealth();
        turret.applyDamage(damage);
        if (!turret.isAlive()) destroyedNexusTurretTimes[index] = currentTimeSeconds;
        return turret.getCurrentHealth();
    }
    public int claimReachedNexusTurretPlates(int index) { return nexusTurret(index).claimReachedPlates(); }
    public double applyNexusDamage(double damage) {
        if (!nexusAlive || !areAllNexusTurretsDestroyed()) return nexusHealth.getCurrentHealth();
        return nexusHealth.applyDamage(damage);
    }
    public int refreshAt(int currentTimeSeconds) {
        if (!nexusAlive) return 0;
        int restored = 0;
        for (int index = 0; index < destroyedNexusTurretTimes.length; index++) {
            int destroyedAt = destroyedNexusTurretTimes[index];
            if (destroyedAt >= 0 && currentTimeSeconds >= destroyedAt
                    + StructureRuleConfig.NEXUS_TURRET_RESPAWN_SECONDS) {
                destroyedNexusTurretTimes[index] = -1;
                nexusTurrets[index].restoreAtRatio(
                        StructureRuleConfig.NEXUS_TURRET_RESPAWN_HEALTH_RATIO);
                respawnedNexusTurretIndices.add(index);
                restored++;
            }
        }
        return restored;
    }
    public List<Integer> drainRespawnedNexusTurretIndices() {
        List<Integer> result = List.copyOf(respawnedNexusTurretIndices);
        respawnedNexusTurretIndices.clear();
        return result;
    }
    public boolean destroyNexus(int currentTimeSeconds) {
        if (!nexusAlive || hasNexusTurrets()) return false;
        nexusAlive = false;
        nexusHealth.forceDestroy();
        nexusDestroyedAtSeconds = currentTimeSeconds;
        return true;
    }

    private StructureHealthState nexusTurret(int index) {
        if (index < 0 || index >= nexusTurrets.length) throw new IllegalArgumentException("nexus turret index");
        return nexusTurrets[index];
    }
}
