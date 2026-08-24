package com.lolfm.simulator;

/** Production tuning for the initial mid-game team macro approximation. */
public final class MidGameMacroRuleConfig {
    private MidGameMacroRuleConfig() { }

    public static final int FIRST_EVALUATION_DELAY_SECONDS = 30;
    public static final int EVALUATION_INTERVAL_SECONDS = 60;
    public static final int PLAN_DURATION_SECONDS = 60;
    public static final int OBJECTIVE_SETUP_WINDOW_SECONDS = 120;

    public static final double SAME_PLAN_REPEAT_MULTIPLIER = 0.65;
    public static final double MIN_PLAN_WEIGHT = 0.10;
    public static final double MAX_PLAN_WEIGHT = 3.00;

    public static final double GROUP_MID_BASE_WEIGHT = 1.00;
    public static final double SIDE_LANE_TOP_BASE_WEIGHT = 0.85;
    public static final double SIDE_LANE_BOT_BASE_WEIGHT = 0.85;
    public static final double DRAGON_SETUP_BASE_WEIGHT = 0.90;
    public static final double BARON_SETUP_BASE_WEIGHT = 0.90;
    public static final double RESET_AND_FARM_BASE_WEIGHT = 0.70;

    public static final double GOLD_EDGE_WEIGHT = 0.30;
    public static final double TEAMFIGHT_EDGE_WEIGHT = 0.45;
    public static final double SIDE_LANE_SKILL_EDGE_WEIGHT = 0.45;
    /** Compatibility alias for existing diagnostics; side plans now use SIDE_LANE skill. */
    public static final double SIDE_FARMING_EDGE_WEIGHT = SIDE_LANE_SKILL_EDGE_WEIGHT;
    public static final double OBJECTIVE_PRIORITY_WEIGHT = 0.50;
    public static final double RESET_BEHIND_GOLD_WEIGHT = 0.35;
    public static final double RESET_MISSING_PLAYER_WEIGHT = 0.15;
    public static final double DRAGON_SOUL_POINT_WEIGHT_BONUS = 0.35;
    public static final double MACRO_SETUP_CONTROL = 12.0;

    public static final double GROUP_MID_PUSH_BASE_CHANCE = 0.32;
    public static final double SIDE_LANE_PUSH_BASE_CHANCE = 0.28;
    public static final double PUSH_GOLD_EDGE_BONUS_MAX = 0.08;
    public static final double PUSH_ALIVE_EDGE_BONUS_PER_PLAYER = 0.03;
    public static final double GROUP_TEAMFIGHT_EDGE_BONUS = 0.08;
    public static final double SIDE_LANE_SKILL_EDGE_BONUS = 0.08;
    /** Compatibility alias for existing diagnostics; side plans now use SIDE_LANE skill. */
    public static final double SIDE_FARMING_EDGE_BONUS = SIDE_LANE_SKILL_EDGE_BONUS;
    public static final double BARON_PUSH_CHANCE_BONUS = 0.20;
    public static final double MIN_MACRO_PUSH_CHANCE = 0.08;
    public static final double MAX_MACRO_PUSH_CHANCE = 0.75;

    public static final int GROUP_MID_FARM_BLOCK_SECONDS = 10;
    public static final int OBJECTIVE_SETUP_FARM_BLOCK_SECONDS = 20;
    public static final double GOLD_EDGE_NORMALIZER = 5000.0;
    public static final double ATTRIBUTE_EDGE_NORMALIZER = 8.0;
}
