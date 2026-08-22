package com.lolfm.simulator;

import java.util.Map;

/** Immutable structured diagnostics for the unified jungle economy path. */
public record JungleEconomyExecutionStatsSnapshot(
        int evaluations,
        int eligibleOutcomes,
        int duplicateCalls,
        Map<JungleEconomySkipReason, Integer> skippedByReason,
        int awardedCs,
        int awardedGold,
        int awardedExperience,
        Map<TeamSide, JungleEconomyOutcome> latestOutcomeBySide
) {
    public JungleEconomyExecutionStatsSnapshot {
        skippedByReason = Map.copyOf(skippedByReason);
        latestOutcomeBySide = Map.copyOf(latestOutcomeBySide);
    }
}
