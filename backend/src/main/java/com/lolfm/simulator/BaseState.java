package com.lolfm.simulator;

public class BaseState {
    private int nexusTurretsRemaining = 2;
    private boolean nexusAlive = true;
    private int nexusDestroyedAtSeconds = -1;

    public int getNexusTurretsRemaining() { return nexusTurretsRemaining; }
    public boolean hasNexusTurrets() { return nexusTurretsRemaining > 0; }
    public boolean areAllNexusTurretsDestroyed() { return nexusTurretsRemaining == 0; }
    public boolean isNexusAlive() { return nexusAlive; }
    public int getNexusDestroyedAtSeconds() { return nexusDestroyedAtSeconds; }
    public boolean destroyOneNexusTurret() {
        if (nexusTurretsRemaining <= 0 || !nexusAlive) return false;
        nexusTurretsRemaining--;
        return true;
    }
    public boolean destroyNexus(int currentTimeSeconds) {
        if (!nexusAlive || nexusTurretsRemaining > 0) return false;
        nexusAlive = false;
        nexusDestroyedAtSeconds = currentTimeSeconds;
        return true;
    }
}
