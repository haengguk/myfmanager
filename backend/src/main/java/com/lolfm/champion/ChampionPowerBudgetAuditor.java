package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.ItemProgressStage;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic review-only audit. Its results never feed gameplay or mutate profile data. */
public final class ChampionPowerBudgetAuditor {
    private static final List<StateWeight> STATES = List.of(
            new StateWeight("S1", 6, ItemProgressStage.COMPONENT, .20),
            new StateWeight("S2", 11, ItemProgressStage.FIRST_CORE, .25),
            new StateWeight("S3", 14, ItemProgressStage.SECOND_CORE, .25),
            new StateWeight("S4", 16, ItemProgressStage.THIRD_CORE, .20),
            new StateWeight("S5", 18, ItemProgressStage.FOURTH_CORE, .10));
    private static final Map<ProgressionCombatContext, Double> CONTEXT_WEIGHTS = createContextWeights();

    private final ChampionCatalog champions;
    private final ChampionPowerProfileCatalog profiles;
    private final ChampionPowerProfileEvaluator evaluator;

    public ChampionPowerBudgetAuditor(ChampionCatalog champions, ChampionPowerProfileCatalog profiles) {
        this.champions = champions;
        this.profiles = profiles;
        this.evaluator = new ChampionPowerProfileEvaluator(profiles);
        requireUnitWeight(STATES.stream().mapToDouble(StateWeight::weight).sum(), "state");
        requireUnitWeight(CONTEXT_WEIGHTS.values().stream().mapToDouble(Double::doubleValue).sum(), "context");
    }

    public Audit audit() {
        List<ChampionBudget> budgets = profiles.all().stream().map(this::audit).toList();
        EnumMap<Position, PositionBudget> positions = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            List<Double> values = budgets.stream().filter(b -> b.position() == position)
                    .map(ChampionBudget::profileBudget).sorted().toList();
            double min = values.getFirst(), max = values.getLast();
            List<String> warnings = max - min > .30 ? List.of("POSITION_RANGE_GT_0_30") : List.of();
            positions.put(position, new PositionBudget(position, average(values), min, max, max - min, warnings));
        }
        List<Double> ordered = budgets.stream().map(ChampionBudget::profileBudget).sorted().toList();
        double mean = average(ordered);
        List<String> globalWarnings = new ArrayList<>();
        if (Math.abs(mean) > .08) globalWarnings.add("GLOBAL_MEAN_ABS_GT_0_08");
        return new Audit(budgets, Map.copyOf(positions), mean, percentile(ordered, .10),
                percentile(ordered, .50), percentile(ordered, .90), ordered.getFirst(), ordered.getLast(),
                profiles.warnings(), List.copyOf(globalWarnings));
    }

    private ChampionBudget audit(ChampionPowerProfile profile) {
        double budget = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        int clamps = 0, samples = 0;
        boolean allNonNegative = true, allNonPositive = true;
        for (StateWeight state : STATES) for (var context : CONTEXT_WEIGHTS.entrySet()) {
            ChampionPowerBreakdown value = evaluator.evaluate(profile.championId(), state.level(), state.itemStage(), context.getKey());
            double raw = value.rawPlayerChampionPower();
            budget += raw * state.weight() * context.getValue();
            min = Math.min(min, raw); max = Math.max(max, raw); samples++;
            if (value.playerClampApplied()) clamps++;
            allNonNegative &= raw >= 0; allNonPositive &= raw <= 0;
        }
        ProgressionCombatContext strongest = profile.contextModifiers().entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        ProgressionCombatContext weakest = profile.contextModifiers().entrySet().stream()
                .min(Map.Entry.comparingByValue()).orElseThrow().getKey();
        List<String> warnings = new ArrayList<>();
        if (Math.abs(budget) > .20) warnings.add("ABS_BUDGET_GT_0_20");
        if (allNonNegative) warnings.add("ALL_STANDARDIZED_NON_NEGATIVE");
        if (allNonPositive) warnings.add("ALL_STANDARDIZED_NON_POSITIVE");
        if (profile.contextModifiers().values().stream().noneMatch(v -> v > 0)) warnings.add("NO_STRENGTH_CONTEXT");
        if (profile.contextModifiers().values().stream().noneMatch(v -> v < 0)) warnings.add("NO_WEAKNESS_CONTEXT");
        if (clamps / (double) samples > .05) warnings.add("PLAYER_CLAMP_GT_5_PERCENT");
        Position position = champions.get(profile.championId()).primaryPosition();
        return new ChampionBudget(profile.championId(), position, profile.levelCurveId(), profile.itemCurveId(),
                budget, min, max, strongest, weakest, clamps, samples, List.copyOf(warnings));
    }

    public List<StateWeight> standardizedStates() { return STATES; }
    public Map<ProgressionCombatContext, Double> contextWeights() { return CONTEXT_WEIGHTS; }

    private static Map<ProgressionCombatContext, Double> createContextWeights() {
        LinkedHashMap<ProgressionCombatContext, Double> values = new LinkedHashMap<>();
        values.put(ProgressionCombatContext.LANE_COMBAT, .15);
        values.put(ProgressionCombatContext.JUNGLE_GANK, .10);
        values.put(ProgressionCombatContext.COUNTER_GANK, .08);
        values.put(ProgressionCombatContext.ROAM, .10);
        values.put(ProgressionCombatContext.GENERIC_SKIRMISH, .10);
        values.put(ProgressionCombatContext.TEAMFIGHT, .18);
        values.put(ProgressionCombatContext.OBJECTIVE_FIGHT, .12);
        values.put(ProgressionCombatContext.LATE_GAME_SIEGE, .10);
        values.put(ProgressionCombatContext.BASE_DEFENSE, .07);
        return Map.copyOf(values);
    }
    private static void requireUnitWeight(double value, String name) {
        if (Math.abs(value - 1) > 1e-12) throw new IllegalStateException(name + " weights must sum to 1.0");
    }
    private static double average(List<Double> values) { return values.stream().mapToDouble(Double::doubleValue).average().orElse(0); }
    private static double percentile(List<Double> values, double q) {
        return values.get((int) Math.min(values.size() - 1, Math.ceil(q * values.size()) - 1));
    }

    public record StateWeight(String id, int level, ItemProgressStage itemStage, double weight) { }
    public record ChampionBudget(ChampionId championId, Position position, String levelCurveId, String itemCurveId,
            double profileBudget, double minimumStandardizedValue, double maximumStandardizedValue,
            ProgressionCombatContext strongestContext, ProgressionCombatContext weakestContext,
            int playerClampSamples, int standardizedSamples, List<String> warnings) {
        public ChampionBudget { warnings = List.copyOf(warnings); }
    }
    public record PositionBudget(Position position, double average, double minimum, double maximum, double range,
            List<String> warnings) { public PositionBudget { warnings = List.copyOf(warnings); } }
    public record Audit(List<ChampionBudget> champions, Map<Position, PositionBudget> positions, double overallAverage,
            double p10, double p50, double p90, double minimum, double maximum,
            List<String> catalogWarnings, List<String> globalWarnings) {
        public Audit { champions = List.copyOf(champions); positions = Map.copyOf(positions);
            catalogWarnings = List.copyOf(catalogWarnings); globalWarnings = List.copyOf(globalWarnings); }
    }
}
