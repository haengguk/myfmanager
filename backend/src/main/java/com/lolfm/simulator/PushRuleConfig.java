package com.lolfm.simulator;

public final class PushRuleConfig {
    public static final int MACRO_START_SECONDS = 480;
    public static final int MACRO_EVALUATION_INTERVAL_SECONDS = 30;
    public static final int MACRO_ATTEMPT_INTERVAL_SECONDS = 60;
    public static final int BARON_ATTEMPT_INTERVAL_SECONDS = 45;
    public static final int BASE_PRESSURE_ATTEMPT_INTERVAL_SECONDS = 45;
    public static final int BASE_PRESSURE_DURATION_SECONDS = 120;
    public static final double SMALL_WIN_PUSH_CHANCE = 0.10;
    public static final double NORMAL_WIN_PUSH_CHANCE = 0.35;
    public static final double BIG_WIN_PUSH_CHANCE = 0.75;
    public static final double ACE_PUSH_CHANCE = 0.95;
    public static final double BARON_PUSH_BONUS = 0.20;
    public static final double BASE_PRESSURE_PUSH_BONUS = 0.15;
    public static final double MACRO_BASE_CHANCE = 0.08;
    public static final double FOUR_ALIVE_PUSH_BONUS = 0.04;
    public static final double ONE_OR_FEWER_DEFENDER_BONUS = 0.06;
    public static final double RESPAWN_PUSH_BONUS_CAP = 0.10;
    public static final double RESPAWN_PUSH_BONUS_DIVISOR = 400.0;
    public static final double MAX_POST_FIGHT_PUSH_CHANCE = 0.98;
    public static final int SMALL_GOLD_LEAD = 3_000;
    public static final double SMALL_GOLD_LEAD_BONUS = 0.04;
    public static final int LARGE_GOLD_LEAD = 6_000;
    public static final double LARGE_GOLD_LEAD_BONUS = 0.06;
    public static final double ALIVE_LEAD_BONUS = 0.05;
    public static final double DEEPEST_LANE_BONUS = 0.05;
    public static final double OUTER_TARGET_PUSH_CHANCE_BONUS = 0.12;
    public static final double STRUCTURE_DEPTH_PUSH_CHANCE_PENALTY = 0.025;
    public static final double MACRO_BARON_BONUS = 0.20;
    public static final double MACRO_BASE_PRESSURE_BONUS = 0.15;
    public static final double MAX_MACRO_PUSH_CHANCE = 0.45;
    public static final int RECENT_FIGHT_BASE_WINDOW_SECONDS = 120;
    public static final int STANDARD_STRUCTURE_ATTACK_SECONDS = 15;
    public static final int BARON_STRUCTURE_ATTACK_SECONDS = 10;
    public static final int DRAGON_OBJECTIVE_TIME_SECONDS = 15;
    public static final int BARON_OBJECTIVE_TIME_SECONDS = 20;
    private PushRuleConfig() { }
}
