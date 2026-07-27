package com.lolfm.champion;

import java.util.Objects;

public record ChampionMatchupRuleContribution(
        ChampionMatchupRuleType ruleType,
        double directionalSourceToOpponent,
        double directionalOpponentToSource,
        double antisymmetricRuleEdge,
        double contextWeight,
        double weightedContribution
) {
    public ChampionMatchupRuleContribution {
        Objects.requireNonNull(ruleType, "ruleType");
        if (!Double.isFinite(directionalSourceToOpponent)
                || !Double.isFinite(directionalOpponentToSource)
                || !Double.isFinite(antisymmetricRuleEdge)
                || !Double.isFinite(contextWeight)
                || !Double.isFinite(weightedContribution)) {
            throw new IllegalArgumentException("Non-finite rule contribution");
        }
        directionalSourceToOpponent = zero(directionalSourceToOpponent);
        directionalOpponentToSource = zero(directionalOpponentToSource);
        antisymmetricRuleEdge = zero(antisymmetricRuleEdge);
        weightedContribution = zero(weightedContribution);
    }

    private static double zero(double value) {
        return Math.abs(value) < 1e-12 ? 0.0 : value;
    }
}
