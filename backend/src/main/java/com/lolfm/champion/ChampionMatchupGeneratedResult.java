package com.lolfm.champion;

import com.lolfm.simulator.ProgressionCombatContext;
import java.util.List;
import java.util.Objects;

public record ChampionMatchupGeneratedResult(
        ChampionRoleKey source,
        ChampionRoleKey opponent,
        ProgressionCombatContext context,
        boolean sourceProfileFound,
        boolean opponentProfileFound,
        List<ChampionMatchupRuleContribution> ruleContributions,
        double weightedRawEdge,
        double contextIntensity,
        double generatedBaseEdge,
        double overrideAdjustment,
        double finalGeneratedEdge,
        boolean clamped,
        double unclampedEdge,
        String profileVersion,
        String ruleVersion,
        String overrideVersion
) {
    public ChampionMatchupGeneratedResult {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(opponent, "opponent");
        Objects.requireNonNull(context, "context");
        ruleContributions = List.copyOf(ruleContributions);
        for (double value : new double[]{
                weightedRawEdge, contextIntensity, generatedBaseEdge,
                overrideAdjustment, finalGeneratedEdge, unclampedEdge}) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Non-finite generated matchup value");
            }
        }
        weightedRawEdge = zero(weightedRawEdge);
        generatedBaseEdge = zero(generatedBaseEdge);
        overrideAdjustment = zero(overrideAdjustment);
        finalGeneratedEdge = zero(finalGeneratedEdge);
        unclampedEdge = zero(unclampedEdge);
        Objects.requireNonNull(profileVersion, "profileVersion");
        Objects.requireNonNull(ruleVersion, "ruleVersion");
        Objects.requireNonNull(overrideVersion, "overrideVersion");
    }

    public boolean neutralFallback() {
        return !sourceProfileFound || !opponentProfileFound;
    }

    private static double zero(double value) {
        return Math.abs(value) < 1e-12 ? 0.0 : value;
    }
}
