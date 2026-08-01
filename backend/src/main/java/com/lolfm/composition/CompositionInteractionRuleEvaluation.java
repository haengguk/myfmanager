package com.lolfm.composition;

import com.lolfm.champion.ChampionRoleKey;
import java.util.List;
import java.util.Objects;

public record CompositionInteractionRuleEvaluation(
        String ruleId,
        TeamCompositionContext context,
        CompositionSignalRef sourceSignal,
        List<CompositionSignalRef> oppositionSignals,
        OppositionAggregation oppositionAggregation,
        double sourceStrength,
        List<Double> oppositionSignalValues,
        double oppositionStrength,
        CompositionInteractionFormula formula,
        double exposure,
        double weight,
        double weightedPressure,
        List<ChampionRoleKey> sourceContributors,
        List<ChampionRoleKey> oppositionContributors
) {
    public CompositionInteractionRuleEvaluation {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(sourceSignal, "sourceSignal");
        oppositionSignals = List.copyOf(oppositionSignals);
        Objects.requireNonNull(oppositionAggregation, "oppositionAggregation");
        oppositionSignalValues = List.copyOf(oppositionSignalValues);
        Objects.requireNonNull(formula, "formula");
        sourceContributors = List.copyOf(sourceContributors);
        oppositionContributors = List.copyOf(oppositionContributors);
        validate01(sourceStrength, "sourceStrength");
        validate01(oppositionStrength, "oppositionStrength");
        validate01(exposure, "exposure");
        if (!Double.isFinite(weight) || weight < 0.0 || !Double.isFinite(weightedPressure) || weightedPressure < 0.0 || weightedPressure > 1.0) {
            throw new IllegalArgumentException("Invalid interaction rule evaluation number");
        }
        for (double value : oppositionSignalValues) validate01(value, "oppositionSignalValue");
        weight = normalizeZero(weight);
        weightedPressure = normalizeZero(weightedPressure);
    }

    private static void validate01(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) throw new IllegalArgumentException("Invalid " + name);
    }

    private static double normalizeZero(double value) { return value == 0.0 ? 0.0 : value; }
}
