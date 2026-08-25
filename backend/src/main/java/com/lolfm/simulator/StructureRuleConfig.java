package com.lolfm.simulator;

/** Time-based structure rules for the current Summoner's Rift ruleset. */
public final class StructureRuleConfig {
    public static final int INHIBITOR_RESPAWN_SECONDS = 300;
    public static final int NEXUS_TURRET_RESPAWN_SECONDS = 180;

    // Summoner's Rift 26.1 structure durability. Resolvers consume these values
    // through the common structure-health path; they must not duplicate them.
    public static final double OUTER_TURRET_MAX_HEALTH = 9_000.0;
    public static final double INNER_TURRET_MAX_HEALTH = 5_000.0;
    public static final double INHIBITOR_TURRET_MAX_HEALTH = 4_750.0;
    public static final double NEXUS_TURRET_MAX_HEALTH = 3_500.0;
    public static final double INHIBITOR_MAX_HEALTH = 4_000.0;
    public static final double NEXUS_MAX_HEALTH = 5_500.0;
    public static final double NEXUS_TURRET_RESPAWN_HEALTH_RATIO = 0.40;

    public static final int TURRET_PLATE_COUNT = 5;
    public static final double[] TURRET_PLATE_MISSING_HEALTH_THRESHOLDS =
            {0.10, 0.25, 0.45, 0.70, 1.00};
    public static final int TURRET_PLATE_LOCAL_GOLD = 120;
    public static final int TURRET_GLOBAL_GOLD_PER_PLAYER = 50;
    public static final int FIRST_TURRET_LOCAL_GOLD = 300;

    public static final double EFFECTIVE_DAMAGE_PER_ATTACKER = 900.0;
    public static final double POST_FIGHT_DAMAGE_MULTIPLIER = 1.15;
    public static final double BARON_DAMAGE_MULTIPLIER = 1.25;
    public static final double OBJECTIVE_TRADE_DAMAGE_MULTIPLIER = 0.75;
    public static final double MID_GAME_DAMAGE_MULTIPLIER = 0.95;
    public static final double LATE_GAME_DAMAGE_MULTIPLIER = 1.05;
    public static final double BACKDOOR_DAMAGE_MULTIPLIER = 0.10;
    public static final double LOCAL_DEFENDER_DAMAGE_REDUCTION_PER_PLAYER = 0.12;
    public static final double MIN_LOCAL_DEFENSE_DAMAGE_MULTIPLIER = 0.45;
    public static final int EARLY_OUTER_PROTECTION_END_SECONDS = 840;
    public static final double EARLY_OUTER_DAMAGE_MULTIPLIER = 0.70;

    public static final int STANDARD_WAVE_ATTACKS = 2;
    public static final int LATE_GAME_SIEGE_WAVE_ATTACKS = 3;
    public static final int BARON_WAVE_ATTACKS = 3;
    public static final int POST_FIGHT_WAVE_ATTACKS = 7;
    public static final int NEXUS_FINISH_WAVE_ATTACKS = 6;
    public static final int BACKDOOR_ATTACK_OPPORTUNITIES = 1;
    public static final int NEXUS_COMMIT_BONUS_ATTACKS = 3;
    public static final int WAVE_ACTIVE_SECONDS = 25;
    public static final int NEXT_WAVE_SECONDS = 30;
    public static final int WAVE_ATTACK_EXPIRY_GRACE_SECONDS = 5;
    public static final int STANDARD_SIEGE_DURATION_SECONDS = 30;
    public static final int LATE_GAME_SIEGE_DURATION_SECONDS = 40;
    public static final int BARON_SIEGE_DURATION_SECONDS = 60;
    public static final int POST_FIGHT_SIEGE_DURATION_SECONDS = 90;
    public static final int NEXUS_FINISH_SIEGE_DURATION_SECONDS = 80;
    public static final int NEXUS_COMMIT_GRACE_SECONDS = 40;
    public static final int STRUCTURE_ATTACK_INTERVAL_SECONDS = 10;
    public static final int MIN_LANE_SIEGE_ATTACKERS = 1;
    public static final int MIN_BASE_SIEGE_ATTACKERS = 3;
    public static final int BASE_DEFENSE_RETURN_COUNT = 3;
    public static final int BASE_SIEGE_RECENT_FIGHT_WINDOW_SECONDS = 120;

    public static int waveAttackOpportunities(PushReason reason, boolean baronEmpowered) {
        int reasonBudget = switch (reason) {
            case POST_FIGHT -> POST_FIGHT_WAVE_ATTACKS;
            case BARON_PRESSURE -> BARON_WAVE_ATTACKS;
            case LATE_GAME_SIEGE -> LATE_GAME_SIEGE_WAVE_ATTACKS;
            case NEXUS_FINISH -> NEXUS_FINISH_WAVE_ATTACKS;
            case MACRO_PLAY, MID_GAME_MACRO, OBJECTIVE_TRADE, LATE_GAME_CROSS_MAP ->
                    STANDARD_WAVE_ATTACKS;
        };
        return baronEmpowered ? Math.max(reasonBudget, BARON_WAVE_ATTACKS) : reasonBudget;
    }

    public static int siegeDurationSeconds(PushReason reason, boolean baronEmpowered) {
        int reasonDuration = switch (reason) {
            case POST_FIGHT -> POST_FIGHT_SIEGE_DURATION_SECONDS;
            case BARON_PRESSURE -> BARON_SIEGE_DURATION_SECONDS;
            case LATE_GAME_SIEGE -> LATE_GAME_SIEGE_DURATION_SECONDS;
            case NEXUS_FINISH -> NEXUS_FINISH_SIEGE_DURATION_SECONDS;
            case MACRO_PLAY, MID_GAME_MACRO, OBJECTIVE_TRADE, LATE_GAME_CROSS_MAP ->
                    STANDARD_SIEGE_DURATION_SECONDS;
        };
        return baronEmpowered ? Math.max(reasonDuration, BARON_SIEGE_DURATION_SECONDS)
                : reasonDuration;
    }

    private StructureRuleConfig() {
    }
}
