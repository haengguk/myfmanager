package com.lolfm.composition;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CompositionInteractionExplanation(
        List<CompositionInteractionRuleEvaluation> ruleEvaluations,
        Map<TeamCompositionContext, CompositionContextInteractionExplanation> contexts
) {
    public CompositionInteractionExplanation {
        Objects.requireNonNull(ruleEvaluations, "ruleEvaluations");
        Objects.requireNonNull(contexts, "contexts");
        ruleEvaluations = List.copyOf(ruleEvaluations);
        EnumMap<TeamCompositionContext, CompositionContextInteractionExplanation> copy = new EnumMap<>(TeamCompositionContext.class);
        copy.putAll(contexts);
        if (copy.size() != TeamCompositionContext.values().length) throw new IllegalArgumentException("Exactly six contexts required");
        for (TeamCompositionContext context : TeamCompositionContext.values()) {
            if (copy.get(context) == null || copy.get(context).context() != context) throw new IllegalArgumentException("Missing context explanation");
        }
        contexts = Map.copyOf(copy);
    }
}
