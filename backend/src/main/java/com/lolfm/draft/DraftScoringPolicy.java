package com.lolfm.draft;

import java.util.Map;

public record DraftScoringPolicy(
        int candidateLimit, int structuralRepairSlots, int searchDepth, int beamWidth,
        Map<PickScoreComponent, Double> pickWeights,
        Map<BanScoreComponent, Double> banWeights, boolean abilityBased, double metaScale
) {
    public DraftScoringPolicy {
        if (candidateLimit < 1 || structuralRepairSlots < 0 || searchDepth < 1 || beamWidth < 1) throw new IllegalArgumentException("Invalid DraftScoringPolicy bounds");
        pickWeights = Map.copyOf(pickWeights); banWeights = Map.copyOf(banWeights);
    }
    public DraftScoringPolicy(int candidateLimit, int repair, int depth, int beam,
            Map<PickScoreComponent,Double> picks, Map<BanScoreComponent,Double> bans) {
        this(candidateLimit, repair, depth, beam, picks, bans, false, 1.0);
    }
    public static DraftScoringPolicy ability() { return ability(0.25); }
    public static DraftScoringPolicy ability(double metaScale) {
        if (!Double.isFinite(metaScale) || metaScale < 0 || metaScale > 0.25)
            throw new IllegalArgumentException("Ability meta influence must be 0..0.25");
        return new DraftScoringPolicy(12, 4, 3, 2,
            Map.ofEntries(Map.entry(PickScoreComponent.META_PRIORITY,1.0),
                Map.entry(PickScoreComponent.PLAYER_FIT,0.8), Map.entry(PickScoreComponent.MATCHUP,0.55),
                Map.entry(PickScoreComponent.COMPOSITION_FIT,0.65), Map.entry(PickScoreComponent.COMPOSITION_RESPONSE,0.55),
                Map.entry(PickScoreComponent.FLEXIBILITY,0.25), Map.entry(PickScoreComponent.DENIAL,0.15),
                Map.entry(PickScoreComponent.FUTURE_FEASIBILITY,0.30), Map.entry(PickScoreComponent.EARLY_POWER,1.0),
                Map.entry(PickScoreComponent.MID_POWER,1.0),Map.entry(PickScoreComponent.LATE_POWER,1.0),
                Map.entry(PickScoreComponent.JUNGLE_CLEAR,0.5), Map.entry(PickScoreComponent.RESOURCE_RISK,-0.5)),
            Map.of(BanScoreComponent.OPPONENT_EXPECTED_PICK_VALUE,1.0,
                BanScoreComponent.THREAT_TO_OUR_PLAN_PORTFOLIO,0.2,BanScoreComponent.META_PRIORITY,1.0,
                BanScoreComponent.OPPONENT_FLEX_VALUE,0.15,BanScoreComponent.ROLE_POOL_COMPRESSION,0.15,
                BanScoreComponent.PROTECTION_VALUE,0.3,BanScoreComponent.OUR_LOST_PICK_OPPORTUNITY,-0.8),true,metaScale);
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
