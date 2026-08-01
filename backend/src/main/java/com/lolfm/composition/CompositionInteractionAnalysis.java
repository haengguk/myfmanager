package com.lolfm.composition;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record CompositionInteractionAnalysis(
        TeamCompositionLineup teamA,
        TeamCompositionLineup teamB,
        CompositionInteractionFormula formula,
        Map<TeamCompositionContext, CompositionContextInteraction> contexts,
        CompositionInteractionExplanation explanation
) {
    public CompositionInteractionAnalysis {
        Objects.requireNonNull(teamA, "teamA");
        Objects.requireNonNull(teamB, "teamB");
        Objects.requireNonNull(formula, "formula");
        Objects.requireNonNull(contexts, "contexts");
        EnumMap<TeamCompositionContext, CompositionContextInteraction> copy = new EnumMap<>(TeamCompositionContext.class);
        copy.putAll(contexts);
        if (copy.size() != TeamCompositionContext.values().length) throw new IllegalArgumentException("Exactly six contexts required");
        contexts = Map.copyOf(copy);
        Objects.requireNonNull(explanation, "explanation");
    }
}
