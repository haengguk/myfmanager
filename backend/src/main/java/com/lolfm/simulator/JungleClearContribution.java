package com.lolfm.simulator;

/** Explicit runtime-profile contribution of Champion Jungle Clear data. */
public enum JungleClearContribution {
    /** Frozen pre-Jungle semantics; the simulator must not read clear data or consume Random. */
    DISABLED_NOT_INTEGRATED(false),

    /** Champion clear and player jungle-resource efficiency affect only CS, gold and XP. */
    ECONOMY_V1(true);

    private final boolean economyEnabled;

    JungleClearContribution(boolean economyEnabled) {
        this.economyEnabled = economyEnabled;
    }

    public boolean economyEnabled() {
        return economyEnabled;
    }
}
