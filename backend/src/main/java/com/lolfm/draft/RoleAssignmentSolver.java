package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RoleAssignmentSolver {
    private final ChampionCatalog catalog;

    public RoleAssignmentSolver(ChampionCatalog catalog) { this.catalog = Objects.requireNonNull(catalog); }

    public List<RoleAssignment> feasibleAssignments(List<ChampionId> champions) {
        return computeFeasibleAssignments(champions);
    }

    List<RoleAssignment> feasibleAssignments(List<ChampionId> champions,
                                             DraftComputationContext context) {
        return context.roleAssignments(champions,
                () -> computeFeasibleAssignments(champions));
    }

    private List<RoleAssignment> computeFeasibleAssignments(List<ChampionId> champions) {
        if (champions.size() > Position.values().length) return List.of();
        if (champions.stream().distinct().count() != champions.size()) return List.of();
        List<ChampionId> stable = champions.stream().sorted(Comparator.comparing(ChampionId::value)).toList();
        List<RoleAssignment> result = new ArrayList<>();
        enumerate(stable, 0, EnumSet.noneOf(Position.class), new LinkedHashMap<>(), result);
        result.sort(Comparator.comparing(RoleAssignment::stableId));
        return List.copyOf(result);
    }

    private void enumerate(List<ChampionId> champions, int index, EnumSet<Position> used,
                           LinkedHashMap<ChampionId, Position> current, List<RoleAssignment> result) {
        if (index == champions.size()) {
            result.add(new RoleAssignment(current));
            return;
        }
        ChampionId champion = champions.get(index);
        catalog.get(champion).supportedPositions().stream().sorted().forEach(position -> {
            if (used.add(position)) {
                current.put(champion, position);
                enumerate(champions, index + 1, used, current, result);
                current.remove(champion);
                used.remove(position);
            }
        });
    }

    public boolean isFeasible(List<ChampionId> champions) { return !feasibleAssignments(champions).isEmpty(); }

    boolean isFeasible(List<ChampionId> champions, DraftComputationContext context) {
        return !feasibleAssignments(champions, context).isEmpty();
    }

    /** Roles the candidate can still occupy in at least one legal assignment of the current partial draft. */
    public Set<Position> feasibleCandidatePositions(List<ChampionId> picks, ChampionId candidate) {
        return computeFeasibleCandidatePositions(picks, candidate, null);
    }

    Set<Position> feasibleCandidatePositions(List<ChampionId> picks, ChampionId candidate,
                                             DraftComputationContext context) {
        return context.candidatePositions(picks, candidate,
                () -> computeFeasibleCandidatePositions(picks, candidate, context));
    }

    private Set<Position> computeFeasibleCandidatePositions(
            List<ChampionId> picks, ChampionId candidate,
            DraftComputationContext context) {
        ArrayList<ChampionId> next = new ArrayList<>(picks);
        next.add(candidate);
        EnumSet<Position> result = EnumSet.noneOf(Position.class);
        assignments(next, context).forEach(value -> result.add(value.positionOf(candidate)));
        return Set.copyOf(result);
    }

    public Set<Position> feasiblePickedPositions(List<ChampionId> picks, ChampionId champion) {
        return computeFeasiblePickedPositions(picks, champion, null);
    }

    Set<Position> feasiblePickedPositions(List<ChampionId> picks, ChampionId champion,
                                          DraftComputationContext context) {
        return context.pickedPositions(picks, champion,
                () -> computeFeasiblePickedPositions(picks, champion, context));
    }

    private Set<Position> computeFeasiblePickedPositions(
            List<ChampionId> picks, ChampionId champion,
            DraftComputationContext context) {
        if (!picks.contains(champion)) return Set.of();
        EnumSet<Position> result = EnumSet.noneOf(Position.class);
        assignments(picks, context).forEach(value -> result.add(value.positionOf(champion)));
        return Set.copyOf(result);
    }

    public RoleAssignment bestAssignment(List<ChampionId> champions, DraftTeamContext team) {
        return bestAssignment(champions, team, null);
    }

    RoleAssignment bestAssignment(List<ChampionId> champions, DraftTeamContext team,
                                  DraftComputationContext context) {
        return assignments(champions, context).stream()
                .max(Comparator.comparingDouble((RoleAssignment value) -> proficiencyScore(value, team))
                        .thenComparing(RoleAssignment::stableId, Comparator.reverseOrder()))
                .orElseThrow(() -> new IllegalArgumentException("No legal role assignment"));
    }

    public double proficiencyScore(RoleAssignment assignment, DraftTeamContext team) {
        return assignment.positions().entrySet().stream()
                .mapToInt(entry -> team.proficiency(new ChampionRoleKey(entry.getKey(), entry.getValue())))
                .average().orElse(0.0);
    }

    public double practicalFlexValue(List<ChampionId> picks, ChampionId candidate, DraftTeamContext team) {
        return practicalFlexValue(picks, candidate, team, null);
    }

    double practicalFlexValue(List<ChampionId> picks, ChampionId candidate,
                              DraftTeamContext team, DraftComputationContext context) {
        ArrayList<ChampionId> next = new ArrayList<>(picks); next.add(candidate);
        List<RoleAssignment> feasible = assignments(next, context);
        if (feasible.isEmpty()) return Double.NEGATIVE_INFINITY;
        EnumMap<Position, Double> roleBest = new EnumMap<>(Position.class);
        feasible.forEach(value -> roleBest.merge(value.positionOf(candidate),
                proficiencyScore(value, team), Math::max));
        double best = roleBest.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        long strong = roleBest.values().stream().filter(value -> value >= best - 2.0).count();
        return Math.min(20.0, best * 0.72 + Math.min(6, strong) * 1.3);
    }

    private List<RoleAssignment> assignments(List<ChampionId> champions,
                                             DraftComputationContext context) {
        return context == null ? feasibleAssignments(champions)
                : feasibleAssignments(champions, context);
    }

    public record RoleAssignment(Map<ChampionId, Position> positions) {
        public RoleAssignment { positions = Map.copyOf(new LinkedHashMap<>(positions)); }
        public Position positionOf(ChampionId champion) { return positions.get(champion); }
        public String stableId() {
            return positions.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ChampionId::value)))
                    .map(entry -> entry.getKey().value() + ":" + entry.getValue()).collect(java.util.stream.Collectors.joining("|"));
        }
    }
}
