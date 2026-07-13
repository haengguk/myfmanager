package com.lolfm.simulator;

/** Centralizes the deliberately small, MVP-scale player attribute adjustments. */
public final class PlayerImpactRuleConfig {

    public static final int MIN_ATTRIBUTE = 1;
    public static final int MAX_ATTRIBUTE = 20;
    public static final int BASELINE_ATTRIBUTE = 14;

    public static final int PASSIVE_GOLD_BASE_PER_TICK = 14;
    public static final int PASSIVE_GOLD_STEP_POINTS = 8;

    public static final double SKIRMISH_CHANCE_PER_AVERAGE_AGGRESSION_POINT = 0.003;
    public static final double SKIRMISH_INITIATIVE_AGGRESSION_WEIGHT = 2.6;
    public static final double SKIRMISH_INITIATIVE_MECHANICS_WEIGHT = 1.8;
    public static final double SKIRMISH_INITIATIVE_TEAMFIGHTING_WEIGHT = 0.4;

    public static final double KILLER_MECHANICS_WEIGHT = 3.2;
    public static final double KILLER_AGGRESSION_WEIGHT = 1.5;
    public static final double KILLER_TEAMFIGHTING_WEIGHT = 1.0;
    public static final double VICTIM_AGGRESSION_RISK_WEIGHT = 0.12;
    public static final double VICTIM_MECHANICS_PROTECTION_WEIGHT = 0.10;

    public static final double TEAMFIGHTING_SCORE_WEIGHT = 1.2;
    public static final double TEAMFIGHT_MECHANICS_SCORE_WEIGHT = 0.5;
    public static final double ALIVE_PLAYER_SCORE_WEIGHT = 16.0;
    public static final double BARON_TEAMFIGHT_SCORE_BONUS = 22.0;
    public static final double TEAMFIGHT_GRADE_GAP_DIVISOR = 180.0;

    private PlayerImpactRuleConfig() {
    }

    public static int normalize(int attribute) {
        return Math.max(MIN_ATTRIBUTE, Math.min(MAX_ATTRIBUTE, attribute));
    }
}
