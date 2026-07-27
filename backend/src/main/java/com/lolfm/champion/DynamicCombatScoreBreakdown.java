package com.lolfm.champion;

/** Diagnostic view of the existing deterministic interaction score. */
public record DynamicCombatScoreBreakdown(
        double mechanicsContribution,
        double aggressionContribution,
        double farmingContribution,
        double teamfightingContribution,
        double playerAttributeContribution,
        double currentGoldContribution,
        double commonLevelContribution,
        double commonItemContribution,
        double championLevelContribution,
        double championItemContribution,
        double championContextContribution,
        double championPowerContribution,
        double scoreBeforeMatchup,
        double championMatchupContribution,
        double scoreAfterMatchup,
        double finalCombatScore,
        ChampionPowerBreakdown championBreakdown
) {
}
