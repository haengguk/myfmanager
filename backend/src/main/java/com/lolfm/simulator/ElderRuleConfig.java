package com.lolfm.simulator;
public final class ElderRuleConfig {
    public static final int FIRST_ELDER_SPAWN_DELAY_SECONDS = 360;
    public static final int ELDER_RESPAWN_SECONDS = 360;
    public static final int ELDER_BUFF_DURATION_SECONDS = 150;
    public static final int ELDER_FIRST_ATTEMPT_DELAY_SECONDS = 30;
    public static final int ELDER_ATTEMPT_INTERVAL_SECONDS = 20;
    public static final double GENERAL_CAPTURE_BASE_CHANCE = 0.12;
    public static final double TEAMFIGHT_TRIGGER_CHANCE = 0.040;
    public static final double TEAMFIGHT_SCORE_BONUS_PER_PLAYER = 3.0;
    public static final double MAX_TEAMFIGHT_SCORE_BONUS = 15.0;
    public static final double BIG_WIN_CHANCE_BONUS = 0.12;
    public static final double ACE_CHANCE_BONUS = 0.08;
    public static final double PUSH_CHANCE_BONUS = 0.20;
    private ElderRuleConfig() {}
}
