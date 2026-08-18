package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

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
        TeamSide side = state.currentTurn().side();
        List<ChampionId> legal = champions.all().stream().map(value -> value.id())
                .filter(id -> !state.unavailableChampions().contains(id))
                .filter(id -> state.currentTurn().actionType() == DraftActionType.BAN || feasiblePick(state, side, id))
                .toList();
        Comparator<ChampionId> coarse = Comparator.comparingDouble((ChampionId id) ->
                        state.currentTurn().actionType() == DraftActionType.PICK
                                ? pickCoarseValue(id, own, ownPortfolio)
                                : banCoarseValue(state, side, id, own, enemy, ownPortfolio, enemyPortfolio))
                .reversed().thenComparing(ChampionId::value);
        Comparator<ChampionId> repair = Comparator.comparingDouble((ChampionId id) -> composition.repairValue(
                state.picks(side), state.picks(side.opposite()), id, own, enemy)).reversed().thenComparing(ChampionId::value);
        LinkedHashSet<ChampionId> reserved = new LinkedHashSet<>();
        if (state.currentTurn().actionType() == DraftActionType.PICK) {
            legal.stream().sorted(repair).limit(policy.structuralRepairSlots()).forEach(reserved::add);
        }
        LinkedHashSet<ChampionId> selected = new LinkedHashSet<>(reserved);
        legal.stream().sorted(coarse).filter(id -> !selected.contains(id))
                .limit(policy.candidateLimit() - selected.size()).forEach(selected::add);
        return List.copyOf(selected);
    }

    private boolean feasiblePick(DraftState state, TeamSide side, ChampionId candidate) {
        ArrayList<ChampionId> values = new ArrayList<>(state.picks(side)); values.add(candidate);
        return assignments.isFeasible(values) && availability.canComplete(state, side, candidate);
    }
    private double pickCoarseValue(ChampionId id, DraftTeamContext team, DraftPlanPortfolio portfolio) {
        double best = champions.get(id).supportedPositions().stream().map(position -> new ChampionRoleKey(id, position))
                .mapToDouble(key -> meta.priority(key) * 0.62 + team.proficiency(key) * 0.38).max().orElse(0.0);
        double relevance = portfolio.plans().stream().filter(plan -> plan.coreCandidates().contains(id))
                .mapToDouble(DraftPlan::viability).max().orElse(0.0);
        return best + relevance * 0.18;
    }

    private double banCoarseValue(DraftState state, TeamSide side, ChampionId id,
                                  DraftTeamContext own, DraftTeamContext enemy,
                                  DraftPlanPortfolio ownPortfolio, DraftPlanPortfolio enemyPortfolio) {
        double enemyValue = champions.get(id).supportedPositions().stream()
                .map(position -> new ChampionRoleKey(id, position))
                .mapToDouble(key -> meta.priority(key) * 0.48 + enemy.proficiency(key) * 0.34).max().orElse(0.0);
        double flex = assignments.practicalFlexValue(state.picks(side.opposite()), id, enemy);
        if (!Double.isFinite(flex)) flex = 0.0;
        double enemyPlan = enemyPortfolio.plans().stream().filter(plan -> plan.coreCandidates().contains(id))
                .mapToDouble(DraftPlan::viability).max().orElse(0.0);
        double threat = ownPortfolio.plans().stream().mapToDouble(plan -> champions.get(id).supportedPositions().stream()
                .map(position -> new ChampionRoleKey(id, position))
                .mapToDouble(key -> plan.structuralVulnerabilities().stream()
                        .mapToInt(capability -> composition.profile(key).capability(capability)).average().orElse(0.0))
                .max().orElse(0.0)).max().orElse(0.0);
        double compression = availability.rolePoolCompression(state, side.opposite(), id);
        return enemyValue + flex * 0.16 + enemyPlan * 0.16 + threat * 0.22 + compression * 0.35;
    }
}
