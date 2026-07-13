package com.lolfm.simulator;

public final class PushRuleConfig {
    public static final int MACRO_ATTEMPT_INTERVAL_SECONDS = 45;
    public static final int BARON_ATTEMPT_INTERVAL_SECONDS = 30;
    public static final int BASE_PRESSURE_ATTEMPT_INTERVAL_SECONDS = 25;
    public static final int BASE_PRESSURE_DURATION_SECONDS = 120;
    public static final double SMALL_WIN_PUSH_CHANCE = 0.10;
    public static final double NORMAL_WIN_PUSH_CHANCE = 0.35;
    public static final double BIG_WIN_PUSH_CHANCE = 0.75;
    public static final double ACE_PUSH_CHANCE = 0.95;
    public static final double BARON_PUSH_BONUS = 0.20;
    public static final double BASE_PRESSURE_PUSH_BONUS = 0.15;
    public static final double MACRO_BASE_CHANCE = 0.08;
    public static final int STANDARD_STRUCTURE_ATTACK_SECONDS = 15;
    public static final int BARON_STRUCTURE_ATTACK_SECONDS = 10;
    public static final int DRAGON_OBJECTIVE_TIME_SECONDS = 15;
    public static final int BARON_OBJECTIVE_TIME_SECONDS = 20;
    private PushRuleConfig() { }
}
