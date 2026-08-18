package com.lolfm.draft;

import java.util.Map;

public record DraftScoringPolicy(
        int candidateLimit, int structuralRepairSlots, int searchDepth, int beamWidth,
        Map<PickScoreComponent, Double> pickWeights,
        Map<BanScoreComponent, Double> banWeights
) {
    public DraftScoringPolicy {
        if (candidateLimit < 1 || structuralRepairSlots < 0 || searchDepth < 1 || beamWidth < 1) throw new IllegalArgumentException("Invalid DraftScoringPolicy bounds");
        pickWeights = Map.copyOf(pickWeights); banWeights = Map.copyOf(banWeights);
    }
    public static DraftScoringPolicy standard() {
        return new DraftScoringPolicy(12, 4, 3, 2,
                Map.of(
                        PickScoreComponent.META_PRIORITY, 1.00,
                        PickScoreComponent.PLAYER_FIT, 0.85,
                        PickScoreComponent.MATCHUP, 0.55,
                        PickScoreComponent.COMPOSITION_FIT, 0.90,
                        PickScoreComponent.COMPOSITION_RESPONSE, 0.90,
                        PickScoreComponent.FLEXIBILITY, 0.65,
                        PickScoreComponent.DENIAL, 0.35,
                        PickScoreComponent.FUTURE_FEASIBILITY, 0.80),
                Map.of(
                        BanScoreComponent.OPPONENT_EXPECTED_PICK_VALUE, 1.00,
                        BanScoreComponent.THREAT_TO_OUR_PLAN_PORTFOLIO, 0.90,
                        BanScoreComponent.META_PRIORITY, 0.60,
                        BanScoreComponent.OPPONENT_FLEX_VALUE, 0.55,
                        BanScoreComponent.ROLE_POOL_COMPRESSION, 0.45,
                        BanScoreComponent.PROTECTION_VALUE, 0.75,
                        BanScoreComponent.OUR_LOST_PICK_OPPORTUNITY, -0.80));
    }
}
