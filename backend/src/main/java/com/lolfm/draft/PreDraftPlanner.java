package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.composition.ChampionCompositionProfile;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import com.lolfm.composition.CompositionCapability;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PreDraftPlanner {
    private final ChampionCatalog champions;
    private final DraftMetaCatalog meta;
    private final ChampionCompositionProfileCatalog composition;
    private final RoleAssignmentSolver assignments;

    public PreDraftPlanner(ChampionCatalog champions, DraftMetaCatalog meta,
                           ChampionCompositionProfileCatalog composition,
                           RoleAssignmentSolver assignments) {
        this.champions = champions; this.meta = meta; this.composition = composition;
        this.assignments = assignments;
    }

    public DraftPlanPortfolio plan(DraftTeamContext team, DraftTeamContext opponent, TeamSide side,
                                   Set<ChampionId> fearlessExclusions) {
        return build(team, opponent, side, fearlessExclusions, List.of(), List.of());
    }

    public DraftPlanPortfolio replan(DraftTeamContext team, DraftTeamContext opponent, TeamSide side,
                                     Set<ChampionId> fearlessExclusions, List<ChampionId> ownPicks,
                                     List<ChampionId> enemyPicks) {
        Set<ChampionId> unavailable = new java.util.HashSet<>(fearlessExclusions);
        unavailable.addAll(ownPicks);
        unavailable.addAll(enemyPicks);
        return build(team, opponent, side, unavailable, ownPicks, enemyPicks);
    }

    public DraftPlanPortfolio replan(DraftTeamContext team, DraftTeamContext opponent,
                                     TeamSide side, DraftState state) {
        return build(team, opponent, side, state.unavailableChampions(),
                state.picks(side), state.picks(side.opposite()));
    }

    private DraftPlanPortfolio build(DraftTeamContext team, DraftTeamContext opponent, TeamSide side,
                                     Set<ChampionId> unavailable, List<ChampionId> ownPicks,
                                     List<ChampionId> enemyPicks) {
        List<ChampionId> available = champions.all().stream().map(value -> value.id())
                .filter(id -> !unavailable.contains(id)).toList();
        Map<ChampionId, Set<Position>> ownCandidateRoles = available.stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        id -> id, id -> assignments.feasibleCandidatePositions(ownPicks, id)));
        Map<ChampionId, Set<Position>> enemyCandidateRoles = available.stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        id -> id, id -> assignments.feasibleCandidatePositions(enemyPicks, id)));
        Map<ChampionId, Set<Position>> enemyPickedRoles = enemyPicks.stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        id -> id, id -> assignments.feasiblePickedPositions(enemyPicks, id)));
        List<RoleAssignmentSolver.RoleAssignment> ownAssignments =
                assignments.feasibleAssignments(ownPicks);
        ArrayList<DraftPlan> plans = new ArrayList<>();
        for (DraftPlanArchetype archetype : DraftPlanArchetype.values()) {
            List<ChampionId> core = available.stream()
                    .filter(id -> !ownCandidateRoles.get(id).isEmpty())
                    .sorted(Comparator.comparingDouble((ChampionId id) -> candidatePlanValue(
                                    id, archetype, team, ownCandidateRoles.get(id))).reversed()
                            .thenComparing(ChampionId::value)).limit(10).toList();
            EnumMap<CompositionCapability, Double> missing = missing(
                    archetype, team, ownAssignments);
            double pool = core.stream().mapToDouble(id -> candidatePlanValue(
                    id, archetype, team, ownCandidateRoles.get(id))).average().orElse(0.0);
            double own = partialPlanValue(archetype, team, pool, ownAssignments);
            double opponentExposure = opponentExposure(
                    archetype, opponent, available, enemyPicks,
                    enemyCandidateRoles, enemyPickedRoles);
            double sideLeverage = side == TeamSide.BLUE ? 0.25 : 0.0;
            plans.add(new DraftPlan(archetype, archetype.desired(), archetype.vulnerabilities(), core,
                    missing, pool * 0.55 + own * 0.45 - opponentExposure + sideLeverage));
        }
        return new DraftPlanPortfolio(plans.stream().sorted(Comparator.comparingDouble(DraftPlan::viability).reversed()
                .thenComparing(plan -> plan.archetype().name())).limit(3).toList());
    }

    private double opponentExposure(DraftPlanArchetype archetype, DraftTeamContext opponent,
                                    List<ChampionId> available, List<ChampionId> enemyPicks,
                                    Map<ChampionId, Set<Position>> enemyCandidateRoles,
                                    Map<ChampionId, Set<Position>> enemyPickedRoles) {
        return java.util.stream.Stream.concat(
                        available.stream().map(id -> opponentThreatValue(
                                id, archetype, opponent, enemyCandidateRoles.get(id))),
                        enemyPicks.stream().map(id -> opponentThreatValue(
                                id, archetype, opponent, enemyPickedRoles.get(id))))
                .sorted(Comparator.reverseOrder()).limit(8).mapToDouble(Double::doubleValue)
                .average().orElse(0.0) * 0.12;
    }

    private double opponentThreatValue(ChampionId id, DraftPlanArchetype archetype,
                                       DraftTeamContext opponent, Set<Position> feasiblePositions) {
        return feasiblePositions.stream().map(position -> new ChampionRoleKey(id, position))
                .mapToDouble(key -> {
                    ChampionCompositionProfile profile = composition.profiles().get(key);
                    double threat = archetype.vulnerabilities().stream().mapToInt(profile::capability).average().orElse(0.0);
                    return threat * 0.55 + meta.priority(key) * 0.25 + opponent.proficiency(key) * 0.20;
                }).max().orElse(0.0);
    }

    private double candidatePlanValue(ChampionId id, DraftPlanArchetype archetype,
                                      DraftTeamContext team, Set<Position> feasiblePositions) {
        double best = 0.0;
        for (Position position : feasiblePositions) {
            ChampionRoleKey key = new ChampionRoleKey(id, position);
            ChampionCompositionProfile profile = composition.profiles().get(key);
            double capability = archetype.desired().stream().mapToInt(profile::capability).average().orElse(0.0);
            best = Math.max(best, capability * 0.55 + meta.priority(key) * 0.30 + team.proficiency(key) * 0.15);
        }
        return best;
    }

    private EnumMap<CompositionCapability, Double> missing(
            DraftPlanArchetype archetype, DraftTeamContext team,
            List<RoleAssignmentSolver.RoleAssignment> feasible) {
        EnumMap<CompositionCapability, Double> result = new EnumMap<>(CompositionCapability.class);
        for (CompositionCapability capability : archetype.desired()) {
            double current = feasible.stream().mapToDouble(assignment -> assignment.positions().entrySet().stream()
                    .mapToDouble(entry -> capabilityValue(entry.getKey(), entry.getValue(), capability, team))
                    .max().orElse(0.0)).max().orElse(0.0);
            result.put(capability, Math.max(0.0, 15.0 - current));
        }
        return result;
    }

    private double partialPlanValue(DraftPlanArchetype archetype, DraftTeamContext team,
                                    double fallback,
                                    List<RoleAssignmentSolver.RoleAssignment> feasible) {
        return feasible.stream().mapToDouble(assignment ->
                assignment.positions().entrySet().stream().mapToDouble(entry -> candidatePlanValue(
                        entry.getKey(), archetype, team, Set.of(entry.getValue())))
                        .average().orElse(fallback)).max().orElse(fallback);
    }

    private double capabilityValue(ChampionId id, Position position,
                                   CompositionCapability capability, DraftTeamContext team) {
        ChampionRoleKey key = new ChampionRoleKey(id, position);
        return composition.profiles().get(key).capability(capability)
                * (0.75 + team.proficiency(key) / 80.0);
    }
}
