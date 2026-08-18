package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class PickEvaluator {
    private final ChampionCatalog champions;
    private final DraftMetaCatalog meta;
    private final DraftMatchupEvaluator matchup;
    private final RoleAssignmentSolver assignments;
    private final DraftCompositionEvaluator composition;
    private final DraftAvailability availability;
    private final DraftScoringPolicy policy;

    public PickEvaluator(ChampionCatalog champions, DraftMetaCatalog meta,
                         DraftMatchupEvaluator matchup, RoleAssignmentSolver assignments,
                         DraftCompositionEvaluator composition, DraftAvailability availability,
                         DraftScoringPolicy policy) {
        this.champions = champions; this.meta = meta; this.matchup = matchup;
        this.assignments = assignments; this.composition = composition;
        this.availability = availability; this.policy = policy;
    }

    public PickEvaluation evaluate(DraftState state, TeamSide side, ChampionId candidate,
                                   DraftTeamContext own, DraftTeamContext enemy,
                                   DraftPlanPortfolio ownPortfolio, DraftPlanPortfolio enemyPortfolio) {
        if (state.unavailableChampions().contains(candidate)) return illegal(candidate);
        ArrayList<ChampionId> next = new ArrayList<>(state.picks(side)); next.add(candidate);
        List<RoleAssignmentSolver.RoleAssignment> feasible = assignments.feasibleAssignments(next);
        if (feasible.isEmpty() || !availability.canComplete(state, side, candidate)) return illegal(candidate);
        double metaPriority = bestRoleValue(candidate, key -> meta.priority(key));
        double playerFit = feasible.stream().mapToDouble(value -> assignments.proficiencyScore(value, own)).max().orElse(0.0);
        double matchupValue = matchup.robustScore(next, state.picks(side.opposite()));
        double compFit = composition.compositionFit(state.picks(side), candidate, own, ownPortfolio);
        double compResponse = composition.compositionResponse(state.picks(side), state.picks(side.opposite()), candidate, own, enemy);
        double flexibility = assignments.practicalFlexValue(state.picks(side), candidate, own);
        double denial = opponentValue(state, side, candidate, enemy, enemyPortfolio);
        double future = availability.poolHealth(state, side, candidate);
        EnumMap<PickScoreComponent, Double> components = new EnumMap<>(PickScoreComponent.class);
        components.put(PickScoreComponent.META_PRIORITY, metaPriority);
        components.put(PickScoreComponent.PLAYER_FIT, playerFit);
        components.put(PickScoreComponent.MATCHUP, matchupValue);
        components.put(PickScoreComponent.COMPOSITION_FIT, compFit);
        components.put(PickScoreComponent.COMPOSITION_RESPONSE, compResponse);
        components.put(PickScoreComponent.FLEXIBILITY, flexibility);
        components.put(PickScoreComponent.DENIAL, denial);
        components.put(PickScoreComponent.FUTURE_FEASIBILITY, future);
        double total = components.entrySet().stream().mapToDouble(entry -> entry.getValue() * policy.pickWeights().get(entry.getKey())).sum();
        return new PickEvaluation(candidate, total, components, true);
    }

    private double opponentValue(DraftState state, TeamSide side, ChampionId candidate,
                                 DraftTeamContext opponent, DraftPlanPortfolio enemyPortfolio) {
        if (!assignments.isFeasible(append(state.picks(side.opposite()), candidate))) return 0.0;
        double roleValue = champions.get(candidate).supportedPositions().stream()
                .map(position -> new ChampionRoleKey(candidate, position))
                .mapToDouble(key -> meta.priority(key) * 0.50 + opponent.proficiency(key) * 0.28).max().orElse(0.0);
        double flex = assignments.practicalFlexValue(state.picks(side.opposite()), candidate, opponent);
        double plan = planRelevance(candidate, enemyPortfolio);
        double fit = composition.compositionFit(state.picks(side.opposite()), candidate, opponent, enemyPortfolio);
        return Math.max(0.0, roleValue + Math.max(0.0, flex) * 0.10 + plan * 0.07 + fit * 0.08);
    }
    private double planRelevance(ChampionId candidate, DraftPlanPortfolio portfolio) {
        return portfolio.plans().stream().filter(plan -> plan.coreCandidates().contains(candidate)).mapToDouble(DraftPlan::viability).max().orElse(0.0);
    }
    private static List<ChampionId> append(List<ChampionId> values, ChampionId value) {
        ArrayList<ChampionId> result = new ArrayList<>(values); result.add(value); return result;
    }
    private double bestRoleValue(ChampionId candidate, java.util.function.ToDoubleFunction<ChampionRoleKey> value) {
        return champions.get(candidate).supportedPositions().stream().map(position -> new ChampionRoleKey(candidate, position)).mapToDouble(value).max().orElse(0.0);
    }
    private static PickEvaluation illegal(ChampionId id) { return new PickEvaluation(id, Double.NEGATIVE_INFINITY, java.util.Map.of(), false); }
}
