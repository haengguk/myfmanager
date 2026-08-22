package com.lolfm.simulator;

/** Explicit runtime-profile contribution of Champion Jungle Clear data. */
public enum JungleClearContribution {
    /** Frozen pre-Jungle semantics; the simulator must not read clear data or consume Random. */
    DISABLED_NOT_INTEGRATED(false, false),

    /** Champion clear and player jungle-resource efficiency affect only CS, gold and XP. */
    ECONOMY_V1(true, false),

    /** Economy V1 plus deterministic, bounded readiness for gank and counter-gank attempts. */
    ECONOMY_AND_GANK_TEMPO_V1(true, true);

    private final boolean economyEnabled;
    private final boolean gankTempoEnabled;

    JungleClearContribution(boolean economyEnabled, boolean gankTempoEnabled) {
        if (gankTempoEnabled && !economyEnabled) {
            throw new IllegalArgumentException("Jungle gank tempo requires Jungle Economy");
        }
        this.economyEnabled = economyEnabled;
        this.gankTempoEnabled = gankTempoEnabled;
    }

    public boolean economyEnabled() {
        return economyEnabled;
    }

    public boolean gankTempoEnabled() {
        return gankTempoEnabled;
    }
}
