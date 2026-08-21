package com.lolfm.player;

import java.util.Map;

/** Counts measured from the loaded resource, never constants used as gameplay rules. */
public record ChampionProficiencyPopulationMetrics(
        int teamCount,
        int playerCount,
        int legalRoleKeyCount,
        int potentialPlayerRoleKeyCount,
        int authoredOverrideCount,
        int neutralFallbackKeyCount,
        Map<Integer, Integer> scoreDistribution,
        int highProficiencyCount,
        int eliteProficiencyCount,
        int worldBenchmarkCount,
        int scopeInexpressibleEvidenceCount
) {
    public ChampionProficiencyPopulationMetrics {
        scoreDistribution = Map.copyOf(scoreDistribution);
    }
}
