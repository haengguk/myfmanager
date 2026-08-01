package com.lolfm.composition;

import com.lolfm.champion.ChampionRoleKey;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stateless evaluator for the frozen composition interaction candidate. */
public final class CompositionInteractionEvaluator {
    public CompositionInteractionAnalysis evaluate(CompositionInteractionInput teamA,
                                                    CompositionInteractionInput teamB,
                                                    CompositionInteractionFormula formula) {
        Objects.requireNonNull(teamA, "teamA");
        Objects.requireNonNull(teamB, "teamB");
        Objects.requireNonNull(formula, "formula");
        EnumMap<TeamCompositionContext, CompositionContextInteraction> contexts = new EnumMap<>(TeamCompositionContext.class);
        List<CompositionInteractionRuleEvaluation> explanationRules = new ArrayList<>();
        EnumMap<TeamCompositionContext, CompositionContextInteractionExplanation> contextExplanations = new EnumMap<>(TeamCompositionContext.class);
        for (TeamCompositionContext context : TeamCompositionContext.values()) {
            DirectedCompositionPressure aToB = directed(context, teamA, teamB, formula);
            DirectedCompositionPressure bToA = directed(context, teamB, teamA, formula);
            double edge = normalizeZero(aToB.pressure() - bToA.pressure());
            CompositionContextInteraction interaction = new CompositionContextInteraction(context, aToB, bToA, edge);
            contexts.put(context, interaction);
            explanationRules.addAll(aToB.rules());
            explanationRules.addAll(bToA.rules());
            contextExplanations.put(context, new CompositionContextInteractionExplanation(context, aToB.pressure(), bToA.pressure(), edge));
        }
        return new CompositionInteractionAnalysis(teamA.lineup(), teamB.lineup(), formula, contexts,
                new CompositionInteractionExplanation(explanationRules, contextExplanations));
    }

    public DirectedCompositionPressure directed(TeamCompositionContext context,
                                                CompositionInteractionInput source,
                                                CompositionInteractionInput opponent,
                                                CompositionInteractionFormula formula) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(opponent, "opponent");
        Objects.requireNonNull(formula, "formula");
        List<CompositionInteractionRuleEvaluation> evaluations = new ArrayList<>();
        double weightedTotal = 0.0;
        double weightTotal = 0.0;
        for (CompositionInteractionRule rule : CompositionInteractionRuleCatalog.rules(context)) {
            double sourceStrength = rule.sourceSignal().value(source);
            List<Double> oppositionValues = rule.oppositionSignals().stream().map(signal -> signal.value(opponent)).toList();
            double oppositionStrength = OppositionAggregationPolicy.aggregate(oppositionValues, rule.oppositionAggregation());
            double exposure = formula.exposure(sourceStrength, oppositionStrength);
            double weightedPressure = normalizeZero(exposure * rule.weight());
            evaluations.add(new CompositionInteractionRuleEvaluation(rule.ruleId(), rule.context(), rule.sourceSignal(),
                    rule.oppositionSignals(), rule.oppositionAggregation(), sourceStrength, oppositionValues,
                    oppositionStrength, formula, exposure, rule.weight(), weightedPressure,
                    source.contributors(rule.sourceSignal()), contributors(opponent, rule.oppositionSignals())));
            weightedTotal += weightedPressure;
            weightTotal += rule.weight();
        }
        if (!Double.isFinite(weightedTotal) || !Double.isFinite(weightTotal) || weightTotal <= 0.0) throw new IllegalStateException("Invalid rule weight total");
        return new DirectedCompositionPressure(context, source.lineup(), opponent.lineup(), normalizeZero(weightedTotal / weightTotal), evaluations);
    }

    private static List<ChampionRoleKey> contributors(CompositionInteractionInput input, List<CompositionSignalRef> signals) {
        List<ChampionRoleKey> result = new ArrayList<>();
        for (CompositionSignalRef signal : signals) result.addAll(input.contributors(signal));
        return List.copyOf(result);
    }

    private static double normalizeZero(double value) { return value == 0.0 ? 0.0 : value; }
}
