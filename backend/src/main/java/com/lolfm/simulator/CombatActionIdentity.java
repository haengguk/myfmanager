package com.lolfm.simulator;

/** Structured match-local identity for the single actual major-combat attempt in a tick. */
public final class CombatActionIdentity {
    private CombatActionIdentity() { }

    public static String actualAt(int simulationTimeSeconds) {
        if (simulationTimeSeconds < 0) {
            throw new IllegalArgumentException("Combat action time must not be negative");
        }
        return "COMBAT_AT:" + simulationTimeSeconds;
    }
}
