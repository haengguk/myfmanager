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

    public DraftAvailability(ChampionCatalog champions, RoleAssignmentSolver assignments) {
        this.champions = champions;
        this.assignments = assignments;
    }

    public boolean canComplete(DraftState state, TeamSide side, ChampionId candidate) {
        List<ChampionId> picks = append(state.picks(side), candidate);
        Set<ChampionId> unavailable = new HashSet<>(state.unavailableChampions());
        unavailable.add(candidate);
        return assignments.feasibleAssignments(picks).stream()
                .anyMatch(assignment -> matchRemaining(missingPositions(assignment), available(unavailable), 0, new HashSet<>()));
    }

    public double poolHealth(DraftState state, TeamSide side, ChampionId candidate) {
        List<ChampionId> picks = candidate == null ? state.picks(side) : append(state.picks(side), candidate);
        Set<ChampionId> unavailable = new HashSet<>(state.unavailableChampions());
        if (candidate != null) unavailable.add(candidate);
        List<ChampionId> pool = available(unavailable);
        return assignments.feasibleAssignments(picks).stream().mapToDouble(assignment -> {
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
            if (weakest == 0 || !matchRemaining(missing, pool, 0, new HashSet<>())) return 0.0;
            double average = total / missing.size();
            return Math.min(20.0, weakest * 1.6 + average * 0.35 + Math.min(5, flexible.size()) * 0.5);
        }).max().orElse(0.0);
    }

    public double rolePoolCompression(DraftState state, TeamSide targetSide, ChampionId banned) {
        double before = poolHealth(state, targetSide, null);
        return rolePoolCompression(state, targetSide, banned, before);
    }

    double rolePoolCompression(DraftState state, TeamSide targetSide, ChampionId banned, double before) {
        DraftState after = syntheticUnavailable(state, banned);
        double afterHealth = poolHealth(after, targetSide, null);
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
        return champions.all().stream().map(value -> value.id()).filter(id -> !unavailable.contains(id))
                .sorted(Comparator.comparing(ChampionId::value)).toList();
    }

    private boolean matchRemaining(Set<Position> missing, List<ChampionId> pool, int index, Set<ChampionId> used) {
        if (index == missing.size()) return true;
        Position position = missing.stream().sorted().toList().get(index);
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
    private static List<ChampionId> append(List<ChampionId> values, ChampionId value) {
        ArrayList<ChampionId> result = new ArrayList<>(values); result.add(value); return result;
    }
}
