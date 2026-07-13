package com.lolfm.simulator;

/**
 * Tunable approximation of the public shutdown-bounty behaviour.  These are
 * deliberately not presented as Riot's internal thresholds.
 */
public final class BountyRuleConfig {

    public static final double KILL_ASSIST_BOUNTY_PROGRESS_RATE = 0.2925;
    public static final double FARM_BOUNTY_PROGRESS_RATE = 0.05;
    public static final int BOUNTY_FREE_BUFFER = 100;
    public static final int MIN_VISIBLE_SHUTDOWN_GOLD = 150;
    public static final int BOUNTY_DISPLAY_STEP = 50;
    public static final int MAX_SHUTDOWN_PAYOUT = 700;
    public static final double DEATH_PROGRESS_REDUCTION_RATE = 0.25;
    public static final int SUPPRESSION_START_SECONDS = 360;
    public static final double FULL_SUPPRESSION_MAX_LEAD_RATIO = 0.02;
    public static final double FULL_BOUNTY_MIN_LEAD_RATIO = 0.08;

    private BountyRuleConfig() {
    }
}
