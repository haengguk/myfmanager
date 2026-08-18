package com.lolfm.simulator;

import com.lolfm.domain.PlayerRatings;

public final class PlayerRatingRuleConfig {
    public static final double PROFICIENCY_EXECUTION_POINT = 0.30;
    public static final double MAX_PROFICIENCY_PENALTY = -3.90;
    public static final double MAX_PROFICIENCY_BONUS = 1.80;
    public static final double REALIZATION_SPREAD_PER_MISSING_CONSISTENCY = 0.25;

    private PlayerRatingRuleConfig() {}

    public static double clampRating(double value) {
        return Math.max(PlayerRatings.MIN, Math.min(PlayerRatings.MAX, value));
    }

    public static double proficiencyAdjustment(int proficiency) {
        return Math.max(MAX_PROFICIENCY_PENALTY, Math.min(MAX_PROFICIENCY_BONUS,
                (proficiency - 14) * PROFICIENCY_EXECUTION_POINT));
    }
}
