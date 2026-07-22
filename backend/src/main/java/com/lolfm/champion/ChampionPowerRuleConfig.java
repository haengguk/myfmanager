package com.lolfm.champion;

public final class ChampionPowerRuleConfig {
    public static final double MAX_ABS_PLAYER_CHAMPION_POWER = 1.10;
    public static final double MAX_ABS_TEAM_CHAMPION_EDGE = 0.90;
    public static final double PROFILE_VALUE_MIN = -0.50;
    public static final double PROFILE_VALUE_MAX = 0.50;
    public static final boolean LEVEL_INTERPOLATION_ENABLED = true;
    private ChampionPowerRuleConfig() { }
    public static double clampPlayer(double value) { return Math.max(-MAX_ABS_PLAYER_CHAMPION_POWER, Math.min(MAX_ABS_PLAYER_CHAMPION_POWER, value)); }
    public static double clampTeamEdge(double value) { return Math.max(-MAX_ABS_TEAM_CHAMPION_EDGE, Math.min(MAX_ABS_TEAM_CHAMPION_EDGE, value)); }
}
