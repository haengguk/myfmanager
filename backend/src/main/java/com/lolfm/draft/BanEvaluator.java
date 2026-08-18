package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import com.lolfm.composition.CompositionCapability;
import com.lolfm.simulator.TeamSide;
import java.util.EnumMap;

public final class BanEvaluator {
    private final ChampionCatalog champions;
    private final DraftMetaCatalog meta;
    private final ChampionCompositionProfileCatalog composition;
    private final RoleAssignmentSolver assignments;
    private final DraftAvailability availability;
    private final DraftCompositionEvaluator draftComposition;
    private final DraftMatchupEvaluator matchup;
    private final DraftScoringPolicy policy;

    public BanEvaluator(ChampionCatalog champions, DraftMetaCatalog meta,
                        ChampionCompositionProfileCatalog composition,
                        RoleAssignmentSolver assignments, DraftAvailability availability,
                        DraftCompositionEvaluator draftComposition, DraftMatchupEvaluator matchup,
                        DraftScoringPolicy policy) {
        this.champions = champions; this.meta = meta; this.composition = composition;
        this.assignments = assignments; this.availability = availability;
        this.draftComposition = draftComposition; this.matchup = matchup; this.policy = policy;
    }

    public BanEvaluation evaluate(DraftState state, TeamSide side, ChampionId candidate,
                                  DraftTeamContext own, DraftTeamContext enemy,
                                  DraftPlanPortfolio ownPortfolio, DraftPlanPortfolio enemyPortfolio) {
        double enemyExpected = opponentExpectedValue(state, side, candidate, enemy, enemyPortfolio);
        double threat = structuralThreat(candidate, ownPortfolio);
        double metaValue = best(candidate, key -> meta.priority(key));
        double flex = assignments.practicalFlexValue(state.picks(side.opposite()), candidate, enemy);
        if (!Double.isFinite(flex)) flex = 0.0;
        double compression = availability.rolePoolCompression(state, side.opposite(), candidate);
        double protection = protectionValue(candidate, ownPortfolio);
        double lost = best(candidate, key -> meta.priority(key) * 0.55 + own.proficiency(key) * 0.45);
        if (side == TeamSide.RED || state.nextTurnIndex() >= 6) lost *= 0.82;
        EnumMap<BanScoreComponent, Double> components = new EnumMap<>(BanScoreComponent.class);
        components.put(BanScoreComponent.OPPONENT_EXPECTED_PICK_VALUE, enemyExpected);
        components.put(BanScoreComponent.THREAT_TO_OUR_PLAN_PORTFOLIO, threat);
        components.put(BanScoreComponent.META_PRIORITY, metaValue);
        components.put(BanScoreComponent.OPPONENT_FLEX_VALUE, flex);
        components.put(BanScoreComponent.ROLE_POOL_COMPRESSION, compression);
        components.put(BanScoreComponent.PROTECTION_VALUE, protection);
        components.put(BanScoreComponent.OUR_LOST_PICK_OPPORTUNITY, lost);
        double total = components.entrySet().stream().mapToDouble(entry -> entry.getValue() * policy.banWeights().get(entry.getKey())).sum();
        return new BanEvaluation(candidate, total, components);
    }

    private double opponentExpectedValue(DraftState state, TeamSide side, ChampionId candidate,
                                         DraftTeamContext enemy, DraftPlanPortfolio enemyPortfolio) {
        java.util.ArrayList<ChampionId> next = new java.util.ArrayList<>(state.picks(side.opposite()));
        next.add(candidate);
        if (!assignments.isFeasible(next)) return 0.0;
        double base = best(candidate, key -> meta.priority(key) * 0.48 + enemy.proficiency(key) * 0.30);
        double plan = enemyPortfolio.plans().stream().filter(value -> value.coreCandidates().contains(candidate))
                .mapToDouble(DraftPlan::viability).max().orElse(0.0);
        double flex = assignments.practicalFlexValue(state.picks(side.opposite()), candidate, enemy);
        double fit = draftComposition.compositionFit(state.picks(side.opposite()), candidate, enemy, enemyPortfolio);
        return base + plan * 0.10 + Math.max(0.0, flex) * 0.10 + Math.max(0.0, fit) * 0.10;
    }

    private double protectionValue(ChampionId threat, DraftPlanPortfolio ownPortfolio) {
        return ownPortfolio.plans().stream().flatMap(plan -> plan.coreCandidates().stream().limit(5))
                .mapToDouble(core -> directCoreThreat(threat, core)).max().orElse(0.0);
    }

    private double directCoreThreat(ChampionId threat, ChampionId core) {
        return champions.get(threat).supportedPositions().stream()
                .filter(champions.get(core).supportedPositions()::contains)
                .mapToDouble(position -> DraftMatchupEvaluator.normalize(matchup.roleEdge(
                        new ChampionRoleKey(threat, position), new ChampionRoleKey(core, position))))
                .max().orElse(10.0);
    }

    private double structuralThreat(ChampionId candidate, DraftPlanPortfolio portfolio) {
        return portfolio.plans().stream().mapToDouble(plan -> champions.get(candidate).supportedPositions().stream()
                .map(position -> composition.profiles().get(new ChampionRoleKey(candidate, position)))
                .mapToDouble(profile -> plan.structuralVulnerabilities().stream().mapToInt(profile::capability).average().orElse(0.0))
                .max().orElse(0.0) * viabilityWeight(plan, portfolio)).max().orElse(0.0);
    }
    private static double viabilityWeight(DraftPlan plan, DraftPlanPortfolio portfolio) {
        double preferred = Math.max(1.0, portfolio.preferred().viability());
        return Math.max(0.45, Math.min(1.2, plan.viability() / preferred));
    }
    private double best(ChampionId candidate, java.util.function.ToDoubleFunction<ChampionRoleKey> function) {
        return champions.get(candidate).supportedPositions().stream().map(position -> new ChampionRoleKey(candidate, position)).mapToDouble(function).max().orElse(0.0);
    }
}
