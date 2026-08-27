package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stateless bounded matching over unresolved roles and the currently usable champion pool. */
public final class DraftAvailability {
    private final ChampionCatalog champions;
    private final RoleAssignmentSolver assignments;
    private final List<ChampionId> canonicalChampionIds;

    public DraftAvailability(ChampionCatalog champions, RoleAssignmentSolver assignments) {
        this.champions = champions;
        this.assignments = assignments;
        canonicalChampionIds = champions.all().stream().map(value -> value.id())
                .sorted(Comparator.comparing(ChampionId::value)).toList();
    }

    public boolean canComplete(DraftState state, TeamSide side, ChampionId candidate) {
        return computeCanComplete(state, side, candidate, null, null);
    }

    /** Whether the side's current partial roster remains completable after one champion is removed. */
    public boolean canCompleteAfterExcluding(
            DraftState state, TeamSide side, ChampionId excludedChampion
    ) {
        Set<ChampionId> unavailable = new HashSet<>(state.unavailableChampions());
        if (excludedChampion != null) unavailable.add(excludedChampion);
        List<ChampionId> pool = available(unavailable);
        return feasibleAssignments(state.picks(side), null).stream()
                .anyMatch(assignment -> matchRemaining(
                        orderedMissingPositions(assignment), pool, 0, new HashSet<>()));
    }

    boolean canComplete(DraftState state, TeamSide side, ChampionId candidate,
                        DraftComputationContext context) {
        return context.completion(state, side, candidate, null,
                () -> computeCanComplete(state, side, candidate, null, context));
    }

    private boolean computeCanComplete(DraftState state, TeamSide side,
                                       ChampionId candidate, Position targetPosition,
                                       DraftComputationContext context) {
        List<ChampionId> picks = append(state.picks(side), candidate);
        Set<ChampionId> unavailable = new HashSet<>(state.unavailableChampions());
        unavailable.add(candidate);
        List<ChampionId> pool = available(unavailable);
        return feasibleAssignments(picks, context).stream()
                .filter(assignment -> targetPosition == null
                        || targetPosition == assignment.positionOf(candidate))
                .anyMatch(assignment -> matchRemaining(
                        orderedMissingPositions(assignment), pool, 0, new HashSet<>()));
    }

    /**
     * Returns whether the partial roster can be completed while the candidate is fixed at the
     * requested position. This is stricter than champion-level completion for flex champions.
     */
    public boolean canCompleteWithCandidateAtRole(DraftState state, TeamSide side,
                                                   ChampionId candidate, Position targetPosition) {
        return computeCanComplete(state, side, candidate, targetPosition, null);
    }

    boolean canCompleteWithCandidateAtRole(DraftState state, TeamSide side,
                                           ChampionId candidate, Position targetPosition,
                                           DraftComputationContext context) {
        return context.completion(state, side, candidate, targetPosition,
                () -> computeCanComplete(state, side, candidate, targetPosition, context));
    }

    public double poolHealth(DraftState state, TeamSide side, ChampionId candidate) {
        return computePoolHealth(state, side, candidate, null);
    }

    double poolHealth(DraftState state, TeamSide side, ChampionId candidate,
                      DraftComputationContext context) {
        return context.poolHealth(state, side, candidate,
                () -> computePoolHealth(state, side, candidate, context));
    }

    private double computePoolHealth(DraftState state, TeamSide side,
                                     ChampionId candidate,
                                     DraftComputationContext context) {
        List<ChampionId> picks = candidate == null ? state.picks(side) : append(state.picks(side), candidate);
        Set<ChampionId> unavailable = new HashSet<>(state.unavailableChampions());
        if (candidate != null) unavailable.add(candidate);
        List<ChampionId> pool = available(unavailable);
        return feasibleAssignments(picks, context).stream().mapToDouble(assignment -> {
            EnumSet<Position> missing = missingPositions(assignment);
            if (missing.isEmpty()) return 20.0;
            int weakest = Integer.MAX_VALUE;
            double total = 0.0;
            Set<ChampionId> flexible = new HashSet<>();
            for (Position position : missing) {
                int count = 0;
                for (ChampionId id : pool) {
                    if (champions.get(id).supportedPositions().contains(position)) {
                        count++;
                        if (champions.get(id).supportedPositions().stream().filter(missing::contains).count() > 1) flexible.add(id);
                    }
                }
                weakest = Math.min(weakest, count);
                total += count;
            }
            if (weakest == 0 || !matchRemaining(List.copyOf(missing), pool, 0,
                    new HashSet<>())) return 0.0;
            double average = total / missing.size();
            return Math.min(20.0, weakest * 1.6 + average * 0.35 + Math.min(5, flexible.size()) * 0.5);
        }).max().orElse(0.0);
    }

    public double rolePoolCompression(DraftState state, TeamSide targetSide, ChampionId banned) {
        double before = poolHealth(state, targetSide, null);
        return rolePoolCompression(state, targetSide, banned, before);
    }

    double rolePoolCompression(DraftState state, TeamSide targetSide, ChampionId banned,
                               DraftComputationContext context) {
        double before = poolHealth(state, targetSide, null, context);
        return rolePoolCompression(state, targetSide, banned, before, context);
    }

    double rolePoolCompression(DraftState state, TeamSide targetSide, ChampionId banned, double before) {
        return rolePoolCompression(state, targetSide, banned, before, null);
    }

    double rolePoolCompression(DraftState state, TeamSide targetSide, ChampionId banned,
                               double before, DraftComputationContext context) {
        DraftState after = syntheticUnavailable(state, banned);
        double afterHealth = context == null ? poolHealth(after, targetSide, null)
                : poolHealth(after, targetSide, null, context);
        if (before <= 0.0) return 0.0;
        return Math.max(0.0, Math.min(20.0, (before - afterHealth) / before * 20.0));
    }

    private DraftState syntheticUnavailable(DraftState state, ChampionId champion) {
        Set<ChampionId> exclusions = new HashSet<>(state.fearlessExclusions());
        exclusions.add(champion);
        return new DraftState(state.ruleSet(), state.nextTurnIndex(), state.bluePicks(), state.redPicks(),
                state.blueBans(), state.redBans(), exclusions);
    }

    private List<ChampionId> available(Set<ChampionId> unavailable) {
        return canonicalChampionIds.stream().filter(id -> !unavailable.contains(id)).toList();
    }

    private boolean matchRemaining(List<Position> missing, List<ChampionId> pool,
                                   int index, Set<ChampionId> used) {
        if (index == missing.size()) return true;
        Position position = missing.get(index);
        for (ChampionId id : pool) {
            if (!used.contains(id) && champions.get(id).supportedPositions().contains(position)) {
                used.add(id);
                if (matchRemaining(missing, pool, index + 1, used)) return true;
                used.remove(id);
            }
        }
        return false;
    }

    private static EnumSet<Position> missingPositions(RoleAssignmentSolver.RoleAssignment assignment) {
        EnumSet<Position> missing = EnumSet.allOf(Position.class);
        missing.removeAll(assignment.positions().values());
        return missing;
    }
    private static List<Position> orderedMissingPositions(
            RoleAssignmentSolver.RoleAssignment assignment) {
        return List.copyOf(missingPositions(assignment));
    }
    private List<RoleAssignmentSolver.RoleAssignment> feasibleAssignments(
            List<ChampionId> picks, DraftComputationContext context) {
        return context == null ? assignments.feasibleAssignments(picks)
                : assignments.feasibleAssignments(picks, context);
    }
    private static List<ChampionId> append(List<ChampionId> values, ChampionId value) {
        ArrayList<ChampionId> result = new ArrayList<>(values); result.add(value); return result;
    }
}
