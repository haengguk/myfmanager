package com.lolfm.simulator;

public final class ObjectiveRuleConfig {

    public static final int FIRST_DRAGON_SPAWN_SECONDS = 300;
    public static final int DRAGON_RESPAWN_SECONDS = 300;
    public static final int FIRST_BARON_SPAWN_SECONDS = 1_200;
    public static final int BARON_RESPAWN_SECONDS = 360;
    public static final int OBJECTIVE_FIRST_ATTEMPT_DELAY_SECONDS = 40;
    public static final int OBJECTIVE_ATTEMPT_INTERVAL_SECONDS = 20;
    public static final double DRAGON_GENERAL_BASE_CAPTURE_CHANCE = 0.17;
    public static final double DRAGON_CAPTURE_CHANCE_AFTER_180_SECONDS = 0.10;
    public static final double DRAGON_CAPTURE_CHANCE_AFTER_300_SECONDS = 0.18;
    public static final int DRAGON_GOLD_PER_PLAYER = 90;
    public static final int BARON_GOLD_PER_PLAYER = 150;

    private ObjectiveRuleConfig() {
    }
}
