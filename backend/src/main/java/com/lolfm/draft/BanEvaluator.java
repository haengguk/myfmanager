package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import com.lolfm.composition.CompositionCapability;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.util.EnumMap;
import java.util.Set;

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
        boolean opponentCanComplete = availability.canComplete(state, side.opposite(), candidate);
        Set<Position> enemyPositions = assignments.feasibleCandidatePositions(
                state.picks(side.opposite()), candidate);
        double enemyExpected = opponentCanComplete
                ? opponentExpectedValue(state, side, candidate, enemy, enemyPortfolio) : 0.0;
        double threat = opponentCanComplete ? structuralThreat(candidate, ownPortfolio, enemyPositions) : 0.0;
        double metaValue = opponentCanComplete
                ? best(candidate, enemyPositions, key -> meta.priority(key)) : 0.0;
        double flex = opponentCanComplete
                ? assignments.practicalFlexValue(state.picks(side.opposite()), candidate, enemy) : 0.0;
        if (!Double.isFinite(flex)) flex = 0.0;
        double compression = opponentCanComplete
                ? availability.rolePoolCompression(state, side.opposite(), candidate) : 0.0;
        double protection = opponentCanComplete
                ? protectionValue(state, side, candidate, ownPortfolio, enemyPositions) : 0.0;
        Set<Position> ownPositions = assignments.feasibleCandidatePositions(state.picks(side), candidate);
        double lost = availability.canComplete(state, side, candidate)
                ? best(candidate, ownPositions, key -> meta.priority(key) * 0.55 + own.proficiency(key) * 0.45)
                : 0.0;
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
        Set<Position> feasiblePositions = assignments.feasibleCandidatePositions(
                state.picks(side.opposite()), candidate);
        double base = best(candidate, feasiblePositions,
                key -> meta.priority(key) * 0.48 + enemy.proficiency(key) * 0.30);
        double plan = enemyPortfolio.plans().stream().filter(value -> value.coreCandidates().contains(candidate))
                .mapToDouble(DraftPlan::viability).max().orElse(0.0);
        double flex = assignments.practicalFlexValue(state.picks(side.opposite()), candidate, enemy);
        double fit = draftComposition.compositionFit(state.picks(side.opposite()), candidate, enemy, enemyPortfolio);
        return base + plan * 0.10 + Math.max(0.0, flex) * 0.10 + Math.max(0.0, fit) * 0.10;
    }

    private double protectionValue(DraftState state, TeamSide side, ChampionId threat,
                                   DraftPlanPortfolio ownPortfolio, Set<Position> threatPositions) {
        double pickedProtection = state.picks(side).stream().mapToDouble(core -> directCoreThreat(
                threat, core, threatPositions,
                assignments.feasiblePickedPositions(state.picks(side), core))).max().orElse(0.0);
        double futureProtection = ownPortfolio.plans().stream()
                .flatMap(plan -> plan.coreCandidates().stream().limit(5))
                .filter(core -> !state.unavailableChampions().contains(core))
                .filter(core -> availability.canComplete(state, side, core))
                .mapToDouble(core -> directCoreThreat(threat, core, threatPositions,
                        assignments.feasibleCandidatePositions(state.picks(side), core)))
                .max().orElse(0.0);
        return Math.max(pickedProtection, futureProtection);
    }

    private double directCoreThreat(ChampionId threat, ChampionId core, Set<Position> threatPositions,
                                    Set<Position> protectedPositions) {
        return threatPositions.stream()
                .filter(protectedPositions::contains)
                .mapToDouble(position -> DraftMatchupEvaluator.positiveThreatScore(matchup.roleEdge(
                        new ChampionRoleKey(threat, position), new ChampionRoleKey(core, position))))
                .max().orElse(0.0);
    }

    private double structuralThreat(ChampionId candidate, DraftPlanPortfolio portfolio,
                                    Set<Position> threatPositions) {
        return portfolio.plans().stream().mapToDouble(plan -> threatPositions.stream()
                .map(position -> composition.profiles().get(new ChampionRoleKey(candidate, position)))
                .mapToDouble(profile -> plan.structuralVulnerabilities().stream().mapToInt(profile::capability).average().orElse(0.0))
                .max().orElse(0.0) * viabilityWeight(plan, portfolio)).max().orElse(0.0);
    }
    private static double viabilityWeight(DraftPlan plan, DraftPlanPortfolio portfolio) {
        double preferred = Math.max(1.0, portfolio.preferred().viability());
        return Math.max(0.45, Math.min(1.2, plan.viability() / preferred));
    }
    private double best(ChampionId candidate, Set<Position> positions,
                        java.util.function.ToDoubleFunction<ChampionRoleKey> function) {
        return positions.stream().map(position -> new ChampionRoleKey(candidate, position))
                .mapToDouble(function).max().orElse(0.0);
    }
}
