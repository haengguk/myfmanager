package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.composition.ChampionCompositionProfile;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import com.lolfm.composition.CompositionCapability;
import com.lolfm.composition.DamageChannelProfile;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Draft-only adaptor over the frozen authored composition profiles. */
public final class DraftCompositionEvaluator {
    private final ChampionCatalog champions;
    private final ChampionCompositionProfileCatalog profiles;
    private final RoleAssignmentSolver assignments;

    public DraftCompositionEvaluator(ChampionCatalog champions, ChampionCompositionProfileCatalog profiles,
                                     RoleAssignmentSolver assignments) {
        this.champions = champions; this.profiles = profiles; this.assignments = assignments;
    }

    public double compositionFit(List<ChampionId> ownPicks, ChampionId candidate,
                                 DraftTeamContext team, DraftPlanPortfolio portfolio) {
        List<ChampionId> next = append(ownPicks, candidate);
        return assignments.feasibleAssignments(next).stream().mapToDouble(assignment -> {
            TeamShape shape = shape(assignment);
            double desired = portfolio.preferred().desiredCapabilities().stream()
                    .mapToDouble(capability -> boundedCoverage(shape.capabilities().get(capability))).average().orElse(10.0);
            double fundamentals = (boundedCoverage(shape.capabilities().get(CompositionCapability.ENGAGE))
                    + boundedCoverage(shape.capabilities().get(CompositionCapability.FRONTLINE))
                    + boundedCoverage(shape.capabilities().get(CompositionCapability.WAVE_CLEAR))) / 3.0;
            return desired * 0.58 + fundamentals * 0.22 + damageBalance(shape.damage()) * 0.20;
        }).max().orElse(Double.NEGATIVE_INFINITY);
    }

    public double compositionResponse(List<ChampionId> ownPicks, List<ChampionId> enemyPicks,
                                      ChampionId candidate, DraftTeamContext ownTeam,
                                      DraftTeamContext enemyTeam) {
        if (enemyPicks.isEmpty()) return 10.0;
        TeamShape enemy = threateningPartialShape(enemyPicks);
        double dive = average(enemy, CompositionCapability.BACKLINE_ACCESS, CompositionCapability.ENGAGE,
                CompositionCapability.FOLLOW_UP, CompositionCapability.BURST_DAMAGE);
        double poke = average(enemy, CompositionCapability.POKE, CompositionCapability.SIEGE,
                CompositionCapability.WAVE_CLEAR);
        double frontline = enemy.capabilities().get(CompositionCapability.FRONTLINE);
        double sideLane = enemy.capabilities().get(CompositionCapability.SIDE_LANE_PRESSURE);
        return feasibleCandidatePositions(ownPicks, candidate).stream()
                .mapToDouble(position -> responseScore(enemy, profile(candidate, position))).max().orElse(0.0);
    }

    public double compositionResponseForRole(List<ChampionId> enemyPicks, ChampionRoleKey candidateRole) {
        if (enemyPicks.isEmpty()) return 10.0;
        return responseScore(threateningPartialShape(enemyPicks), profile(candidateRole));
    }

    private static double responseScore(TeamShape enemy, ChampionCompositionProfile profile) {
        double dive = average(enemy, CompositionCapability.BACKLINE_ACCESS, CompositionCapability.ENGAGE,
                CompositionCapability.FOLLOW_UP, CompositionCapability.BURST_DAMAGE);
        double poke = average(enemy, CompositionCapability.POKE, CompositionCapability.SIEGE,
                CompositionCapability.WAVE_CLEAR);
        double frontline = enemy.capabilities().get(CompositionCapability.FRONTLINE);
        double sideLane = enemy.capabilities().get(CompositionCapability.SIDE_LANE_PRESSURE);
        double antiDive = average(profile, CompositionCapability.PEEL, CompositionCapability.DISENGAGE,
                CompositionCapability.FRONTLINE, CompositionCapability.ZONE_CONTROL);
        double antiPoke = average(profile, CompositionCapability.ENGAGE, CompositionCapability.BACKLINE_ACCESS,
                CompositionCapability.WAVE_CLEAR, CompositionCapability.DISENGAGE);
        double antiTank = (profile.capability(CompositionCapability.SUSTAINED_DAMAGE)
                + profile.capability(CompositionCapability.OBJECTIVE_DAMAGE)) / 2.0;
        double mapAnswer = (profile.capability(CompositionCapability.SIDE_LANE_PRESSURE)
                + profile.capability(CompositionCapability.WAVE_CLEAR)) / 2.0;
        return clamp10(10.0 + (dive - 10.0) * (antiDive - 10.0) / 20.0
                + (poke - 10.0) * (antiPoke - 10.0) / 24.0
                + (frontline - 10.0) * (antiTank - 10.0) / 30.0
                + (sideLane - 10.0) * (mapAnswer - 10.0) / 36.0);
    }

    public double repairValue(List<ChampionId> ownPicks, List<ChampionId> enemyPicks,
                              ChampionId candidate, DraftTeamContext team, DraftTeamContext enemy) {
        TeamShape own = ownPicks.isEmpty() ? TeamShape.empty() : bestPartialShape(ownPicks, team);
        double missingEngage = Math.max(0.0, 13.0 - own.capabilities().get(CompositionCapability.ENGAGE));
        java.util.Set<Position> feasiblePositions = feasibleCandidatePositions(ownPicks, candidate);
        double engage = feasiblePositions.stream()
                .mapToInt(position -> profile(candidate, position).capability(CompositionCapability.ENGAGE)).max().orElse(0);
        double imbalanceRepair = damageRepair(own.damage(), candidate, feasiblePositions);
        double response = compositionResponse(ownPicks, enemyPicks, candidate, team, enemy);
        return missingEngage * engage / 10.0 + imbalanceRepair + Math.max(0.0, response - 10.0);
    }

    public TeamShape bestPartialShape(List<ChampionId> picks, DraftTeamContext team) {
        return assignments.feasibleAssignments(picks).stream()
                .max(java.util.Comparator.comparingDouble(value -> assignments.proficiencyScore(value, team)))
                .map(this::shape).orElse(TeamShape.empty());
    }

    public TeamShape threateningPartialShape(List<ChampionId> picks) {
        return assignments.feasibleAssignments(picks).stream().map(this::shape)
                .max(java.util.Comparator.comparingDouble(value -> average(value,
                        CompositionCapability.ENGAGE, CompositionCapability.BACKLINE_ACCESS,
                        CompositionCapability.POKE, CompositionCapability.BURST_DAMAGE)))
                .orElse(TeamShape.empty());
    }

    public java.util.Set<Position> feasibleCandidatePositions(List<ChampionId> ownPicks, ChampionId candidate) {
        List<ChampionId> next = append(ownPicks, candidate);
        java.util.EnumSet<Position> result = java.util.EnumSet.noneOf(Position.class);
        assignments.feasibleAssignments(next).forEach(value -> result.add(value.positionOf(candidate)));
        return java.util.Set.copyOf(result);
    }

    public double assignmentQuality(RoleAssignmentSolver.RoleAssignment assignment) {
        TeamShape value = shape(assignment);
        double fundamentals = average(value, CompositionCapability.ENGAGE, CompositionCapability.FRONTLINE,
                CompositionCapability.PEEL, CompositionCapability.WAVE_CLEAR,
                CompositionCapability.SUSTAINED_DAMAGE);
        return fundamentals * 0.75 + damageBalance(value.damage()) * 0.25;
    }

    private TeamShape shape(RoleAssignmentSolver.RoleAssignment assignment) {
        EnumMap<CompositionCapability, Double> caps = new EnumMap<>(CompositionCapability.class);
        for (CompositionCapability capability : CompositionCapability.values()) caps.put(capability, 0.0);
        int physical = 0, magic = 0, trueDamage = 0;
        for (Map.Entry<ChampionId, Position> entry : assignment.positions().entrySet()) {
            ChampionCompositionProfile profile = profile(entry.getKey(), entry.getValue());
            for (CompositionCapability capability : CompositionCapability.values()) {
                caps.put(capability, caps.get(capability) + profile.capability(capability));
            }
            physical += profile.damageProfile().physicalThreat(); magic += profile.damageProfile().magicThreat();
            trueDamage += profile.damageProfile().trueDamageThreat();
        }
        int count = Math.max(1, assignment.positions().size());
        caps.replaceAll((capability, value) -> value / count);
        return new TeamShape(caps, new DamageChannelProfile(Math.min(20, physical / count),
                Math.min(20, magic / count), Math.min(20, trueDamage / count)));
    }

    public ChampionCompositionProfile profile(ChampionRoleKey key) { return profiles.profiles().get(key); }
    private ChampionCompositionProfile profile(ChampionId id, Position position) {
        return profiles.profiles().get(new ChampionRoleKey(id, position));
    }
    private static double average(ChampionCompositionProfile profile, CompositionCapability... capabilities) {
        return java.util.Arrays.stream(capabilities).mapToInt(profile::capability).average().orElse(0.0);
    }
    private static double average(TeamShape shape, CompositionCapability... capabilities) {
        return java.util.Arrays.stream(capabilities).mapToDouble(value -> shape.capabilities().get(value)).average().orElse(0.0);
    }
    private static double boundedCoverage(double value) { return Math.min(20.0, value * 1.18); }
    private static double clamp10(double value) { return Math.max(0.0, Math.min(20.0, value)); }
    private static List<ChampionId> append(List<ChampionId> values, ChampionId value) {
        ArrayList<ChampionId> result = new ArrayList<>(values); result.add(value); return result;
    }
    private double damageRepair(DamageChannelProfile own, ChampionId candidate, java.util.Set<Position> feasiblePositions) {
        int skew = own.physicalThreat() - own.magicThreat();
        if (Math.abs(skew) < 4) return 0.0;
        return feasiblePositions.stream().map(position -> profile(candidate, position).damageProfile())
                .mapToDouble(damage -> skew > 0 ? Math.max(0, damage.magicThreat() - damage.physicalThreat())
                        : Math.max(0, damage.physicalThreat() - damage.magicThreat())).max().orElse(0.0) * 0.35;
    }
    private static double damageBalance(DamageChannelProfile damage) {
        return Math.max(4.0, 20.0 - Math.abs(damage.physicalThreat() - damage.magicThreat()) * 0.7);
    }
    public record TeamShape(Map<CompositionCapability, Double> capabilities, DamageChannelProfile damage) {
        public TeamShape { capabilities = Map.copyOf(capabilities); }
        static TeamShape empty() {
            EnumMap<CompositionCapability, Double> values = new EnumMap<>(CompositionCapability.class);
            for (CompositionCapability capability : CompositionCapability.values()) values.put(capability, 0.0);
            return new TeamShape(values, new DamageChannelProfile(0, 0, 0));
        }
    }
}
