package com.lolfm.simulator;

/** Production tuning for player-skill contributions in the objective flow. */
public final class ObjectivePlayerSkillRuleConfig {
    public static final double BASELINE_SKILL = PlayerImpactRuleConfig.BASELINE_ATTRIBUTE;

    public static final double OBJECTIVE_SECURE_SELECTION_WEIGHT_PER_POINT = 12.0;
    public static final double AREA_SETUP_SELECTION_WEIGHT_PER_POINT = 6.0;
    public static final double VISION_CONTROL_SELECTION_WEIGHT_PER_POINT = 4.0;

    public static final double DECISION_PRIORITY_FAVORABILITY_WEIGHT = .35;
    public static final double DECISION_ALIVE_FAVORABILITY_WEIGHT = .30;
    public static final double DECISION_GOLD_FAVORABILITY_WEIGHT = .15;
    public static final double DECISION_TEAMFIGHT_FAVORABILITY_WEIGHT = .20;
    public static final double DECISION_WEIGHT_PER_SKILL_POINT = .05;

    public static final double AREA_SETUP_FIGHT_SCORE_PER_POINT = .30;
    public static final double VISION_CONTROL_FIGHT_SCORE_PER_POINT = .20;

    public static final double SECURE_SETUP_AREA_WEIGHT = .60;
    public static final double SECURE_SETUP_VISION_WEIGHT = .40;
    public static final double BASE_STEAL_CHANCE = .04;
    public static final double STEAL_CHANCE_PER_SECURE_EDGE_POINT = .012;
    public static final double STEAL_CHANCE_PER_SETUP_EDGE_POINT = .004;
    public static final double MIN_STEAL_CHANCE = .01;
    public static final double MAX_STEAL_CHANCE = .18;

    private ObjectivePlayerSkillRuleConfig() { }
}
