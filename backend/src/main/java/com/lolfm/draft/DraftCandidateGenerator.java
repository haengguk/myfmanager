package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class DraftCandidateGenerator {
    private final ChampionCatalog champions;
    private final DraftMetaCatalog meta;
    private final RoleAssignmentSolver assignments;
    private final DraftCompositionEvaluator composition;
    private final DraftAvailability availability;
    private final DraftScoringPolicy policy;

    public DraftCandidateGenerator(ChampionCatalog champions, DraftMetaCatalog meta,
                                   RoleAssignmentSolver assignments,
                                   DraftCompositionEvaluator composition, DraftAvailability availability,
                                   DraftScoringPolicy policy) {
        this.champions = champions; this.meta = meta; this.assignments = assignments;
        this.composition = composition; this.availability = availability; this.policy = policy;
    }

    public List<ChampionId> generate(DraftState state, DraftTeamContext own, DraftTeamContext enemy,
                                     DraftPlanPortfolio ownPortfolio, DraftPlanPortfolio enemyPortfolio) {
        return generate(state, own, enemy, ownPortfolio, enemyPortfolio,
                DraftComputationContext.uncached());
    }

    List<ChampionId> generate(DraftState state, DraftTeamContext own,
                              DraftTeamContext enemy,
                              DraftPlanPortfolio ownPortfolio,
                              DraftPlanPortfolio enemyPortfolio,
                              DraftComputationContext context) {
        TeamSide side = state.currentTurn().side();
        List<ChampionId> legal = champions.all().stream().map(value -> value.id())
                .filter(id -> !state.unavailableChampions().contains(id))
                .filter(id -> state.currentTurn().actionType() == DraftActionType.BAN
                        || feasiblePick(state, side, id, context))
                .toList();
        double beforeEnemyHealth = state.currentTurn().actionType() == DraftActionType.BAN
                ? availability.poolHealth(state, side.opposite(), null, context) : 0.0;
        Map<ChampionId, Double> coarseValues = new HashMap<>();
        Map<ChampionId, Double> repairValues = new HashMap<>();
        Map<ChampionId, Double> searchPriorities = new HashMap<>();
        for (ChampionId id : legal) {
            double coarseValue = state.currentTurn().actionType() == DraftActionType.PICK
                    ? pickCoarseValue(state, side, id, own, ownPortfolio, context)
                    : banCoarseValue(state, side, id, own, enemy, ownPortfolio, enemyPortfolio,
                            beforeEnemyHealth, context);
            coarseValues.put(id, coarseValue);
            if (state.currentTurn().actionType() == DraftActionType.PICK) {
                double repairValue = composition.repairValue(
                        state.picks(side), state.picks(side.opposite()), id, own, enemy,
                        context);
                repairValues.put(id, repairValue);
                searchPriorities.put(id, coarseValue + repairValue * 0.24
                        + assignments.feasibleCandidatePositions(
                                state.picks(side), id, context).size() * 0.12);
            } else {
                searchPriorities.put(id, coarseValue);
            }
        }
        Comparator<ChampionId> coarse = Comparator.comparingDouble((ChampionId id) -> coarseValues.get(id))
                .reversed().thenComparing(ChampionId::value);
        Comparator<ChampionId> repair = Comparator.comparingDouble((ChampionId id) -> repairValues.get(id))
                .reversed().thenComparing(ChampionId::value);
        LinkedHashSet<ChampionId> reserved = new LinkedHashSet<>();
        if (state.currentTurn().actionType() == DraftActionType.PICK) {
            legal.stream().sorted(repair).limit(policy.structuralRepairSlots()).forEach(reserved::add);
        }
        LinkedHashSet<ChampionId> selected = new LinkedHashSet<>(reserved);
        legal.stream().sorted(coarse).filter(id -> !selected.contains(id))
                .limit(policy.candidateLimit() - selected.size()).forEach(selected::add);
        Comparator<ChampionId> searchOrder = Comparator.comparingDouble((ChampionId id) -> searchPriorities.get(id))
                .reversed().thenComparing(ChampionId::value);
        return selected.stream().sorted(searchOrder).toList();
    }

    private boolean feasiblePick(DraftState state, TeamSide side, ChampionId candidate,
                                 DraftComputationContext context) {
        ArrayList<ChampionId> values = new ArrayList<>(state.picks(side)); values.add(candidate);
        return assignments.isFeasible(values, context)
                && availability.canComplete(state, side, candidate, context);
    }
    private double pickCoarseValue(DraftState state, TeamSide side, ChampionId id,
                                   DraftTeamContext team, DraftPlanPortfolio portfolio,
                                   DraftComputationContext context) {
        double best = assignments.feasibleCandidatePositions(
                state.picks(side), id, context).stream()
                .map(position -> new ChampionRoleKey(id, position))
                .mapToDouble(key -> meta.priority(key) * 0.62 + team.proficiency(key) * 0.38).max().orElse(0.0);
        double relevance = portfolio.plans().stream().filter(plan -> plan.coreCandidates().contains(id))
                .mapToDouble(DraftPlan::viability).max().orElse(0.0);
        return best + relevance * 0.18;
    }

    private double banCoarseValue(DraftState state, TeamSide side, ChampionId id,
                                  DraftTeamContext own, DraftTeamContext enemy,
                                  DraftPlanPortfolio ownPortfolio, DraftPlanPortfolio enemyPortfolio) {
        return banCoarseValue(state, side, id, own, enemy, ownPortfolio, enemyPortfolio,
                availability.poolHealth(state, side.opposite(), null),
                DraftComputationContext.uncached());
    }

    private double banCoarseValue(DraftState state, TeamSide side, ChampionId id,
                                  DraftTeamContext own, DraftTeamContext enemy,
                                  DraftPlanPortfolio ownPortfolio, DraftPlanPortfolio enemyPortfolio,
                                  double beforeEnemyHealth,
                                  DraftComputationContext context) {
        if (!availability.canComplete(state, side.opposite(), id, context)) return 0.0;
        java.util.Set<com.lolfm.domain.Position> enemyPositions =
                assignments.feasibleCandidatePositions(
                        state.picks(side.opposite()), id, context);
        double enemyValue = enemyPositions.stream()
                .map(position -> new ChampionRoleKey(id, position))
                .mapToDouble(key -> meta.priority(key) * 0.48 + enemy.proficiency(key) * 0.34).max().orElse(0.0);
        double flex = assignments.practicalFlexValue(
                state.picks(side.opposite()), id, enemy, context);
        if (!Double.isFinite(flex)) flex = 0.0;
        double enemyPlan = enemyPortfolio.plans().stream().filter(plan -> plan.coreCandidates().contains(id))
                .mapToDouble(DraftPlan::viability).max().orElse(0.0);
        double threat = ownPortfolio.plans().stream().mapToDouble(plan -> enemyPositions.stream()
                .map(position -> new ChampionRoleKey(id, position))
                .mapToDouble(key -> plan.structuralVulnerabilities().stream()
                        .mapToInt(capability -> composition.profile(key).capability(capability)).average().orElse(0.0))
                .max().orElse(0.0)).max().orElse(0.0);
        double compression = availability.rolePoolCompression(
                state, side.opposite(), id, beforeEnemyHealth, context);
        return enemyValue + flex * 0.16 + enemyPlan * 0.16 + threat * 0.22 + compression * 0.35;
    }

    double coarseValue(DraftState state, TeamSide side, ChampionId id,
                       DraftTeamContext own, DraftTeamContext enemy,
                       DraftPlanPortfolio ownPortfolio, DraftPlanPortfolio enemyPortfolio) {
        return state.currentTurn().actionType() == DraftActionType.PICK
                ? pickCoarseValue(state, side, id, own, ownPortfolio,
                        DraftComputationContext.uncached())
                : banCoarseValue(state, side, id, own, enemy, ownPortfolio, enemyPortfolio);
    }

    double searchPriority(DraftState state, TeamSide side, ChampionId id,
                          DraftTeamContext own, DraftTeamContext enemy,
                          DraftPlanPortfolio ownPortfolio, DraftPlanPortfolio enemyPortfolio) {
        double coarse = coarseValue(state, side, id, own, enemy, ownPortfolio, enemyPortfolio);
        if (state.currentTurn().actionType() == DraftActionType.BAN) return coarse;
        double repair = composition.repairValue(state.picks(side), state.picks(side.opposite()), id, own, enemy);
        double currentRoleOptions = assignments.feasibleCandidatePositions(state.picks(side), id).size();
        return coarse + repair * 0.24 + currentRoleOptions * 0.12;
    }
}
