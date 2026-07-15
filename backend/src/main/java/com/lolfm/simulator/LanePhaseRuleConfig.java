package com.lolfm.simulator;

public final class LanePhaseRuleConfig {
    private LanePhaseRuleConfig() { }
    public static final int OUTER_SIEGE_START_SECONDS = 300;
    public static final int OUTER_SIEGE_END_SECONDS = 840;
    public static final int OUTER_SIEGE_INTERVAL_SECONDS = 30;
    public static final double OUTER_TURRET_MAX_INTEGRITY = 100.0;
    public static final double MIN_SIEGE_PRESSURE = 20.0;
    public static final double BASE_OUTER_SIEGE_DAMAGE = 7.0;
    public static final double PRESSURE_DAMAGE_PER_POINT_OVER_THRESHOLD = 0.12;
    public static final double DEFENDER_PRIMARY_ABSENT_DAMAGE_BONUS = 4.0;
    public static final double BOT_SUPPORT_PRESENT_DAMAGE_BONUS = 1.5;
    public static final double OUTER_SIEGE_RANDOM_VARIANCE = 2.0;
    public static final double MIN_OUTER_SIEGE_DAMAGE = 2.0;
    public static final double MAX_OUTER_SIEGE_DAMAGE = 22.0;
    public static final double OPEN_LANE_PRESSURE_DECAY_MULTIPLIER = 0.65;
    public static final int MIDGAME_TRANSITION_SECONDS = 840;
    public static final double PRESSURE_SCORE_MIN = -100.0;
    public static final double PRESSURE_SCORE_MAX = 100.0;
}
