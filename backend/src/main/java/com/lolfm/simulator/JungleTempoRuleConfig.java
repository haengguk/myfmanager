package com.lolfm.simulator;

/** Fixed initial rules for the bounded Jungle Clear -> gank-tempo candidate. */
public final class JungleTempoRuleConfig {
    public static final double MIN_CREDIT_EFFICIENCY = 0.85;
    public static final double MAX_CREDIT_EFFICIENCY = 1.15;
    /** Twelve 10-second clear ticks from the 70-second economy start to 180 seconds. */
    public static final double FIRST_ACTION_READINESS_SECONDS = 120.0;
    public static final double REPEAT_ACTION_READINESS_SECONDS = 150.0;
    public static final double ACTION_COST_SECONDS = 150.0;
    public static final double MAX_BANKED_CREDIT_SECONDS = 240.0;
    public static final int CONTINUITY_GRACE_SECONDS = 30;

    private JungleTempoRuleConfig() {
    }
}
