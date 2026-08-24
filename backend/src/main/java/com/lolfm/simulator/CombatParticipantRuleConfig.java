package com.lolfm.simulator;

import com.lolfm.domain.Position;

/** Production tuning for ability-aware combat attribution. */
public final class CombatParticipantRuleConfig {
    public static final double ATTRIBUTE_MULTIPLIER_PER_POINT = .04;
    public static final double MIN_ATTRIBUTE_MULTIPLIER = .70;
    public static final double MAX_ATTRIBUTE_MULTIPLIER = 1.30;

    public static final double VICTIM_EXPOSURE_MULTIPLIER_PER_POINT = .06;
    public static final double VICTIM_REPEAT_DEATH_MULTIPLIER_PER_DEATH = .04;
    public static final double LEGACY_TEAMFIGHT_REPEAT_DEATH_WEIGHT = .08;
    public static final double MIN_VICTIM_MULTIPLIER = .55;
    public static final double MAX_VICTIM_MULTIPLIER = 1.60;

    public static final double EXPOSURE_POSITIONING_WEIGHT = .80;
    public static final double EXPOSURE_MAP_AWARENESS_WEIGHT = .20;
    public static final double ASSIST_MAP_AWARENESS_WEIGHT = .45;
    public static final double ASSIST_DECISION_MAKING_WEIGHT = .30;
    public static final double ASSIST_ROLE_JOIN_WEIGHT = .25;

    public static final double TEAMFIGHT_EXECUTION_ROLE_PRIOR_WEIGHT = .80;
    public static final double TEAMFIGHT_CARRY_KILLER_ROLE_PRIOR_BONUS = 8.0;
    public static final double COMPOSITION_OFF_ENGAGE_SCORE_PER_POINT = .12;
    public static final double COMPOSITION_OFF_PROTECTION_SCORE_PER_POINT = .12;

    /** A power-of-two bucket keeps one {@code nextInt} draw for each assist selection. */
    public static final int ASSIST_SELECTION_BUCKETS = 1 << 20;

    private CombatParticipantRuleConfig() { }

    public static double teamfightKillerRolePrior(Position position, boolean teamfight) {
        double prior = PlayerImpactRuleConfig.BASELINE_ATTRIBUTE
                * (PlayerImpactRuleConfig.KILLER_MECHANICS_WEIGHT
                + PlayerImpactRuleConfig.KILLER_AGGRESSION_WEIGHT
                + PlayerImpactRuleConfig.KILLER_TEAMFIGHTING_WEIGHT);
        if (teamfight) {
            prior += PlayerImpactRuleConfig.BASELINE_ATTRIBUTE
                    * TEAMFIGHT_EXECUTION_ROLE_PRIOR_WEIGHT;
        }
        if (position == Position.ADC || position == Position.MID) {
            prior += TEAMFIGHT_CARRY_KILLER_ROLE_PRIOR_BONUS;
        }
        return prior * CombatRealismRuleConfig.killerRoleMultiplier(position);
    }

    public static double teamfightVictimRolePrior(Position position) {
        return switch (position) {
            case ADC -> 1.45;
            case MID -> 1.25;
            case SUPPORT -> 1.15;
            case JUNGLE -> 1.00;
            case TOP -> .90;
        };
    }

}
