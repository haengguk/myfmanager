package com.lolfm.composition;

import java.util.List;
import java.util.Objects;

public record CompositionInteractionRule(
        String ruleId,
        TeamCompositionContext context,
        CompositionSignalRef sourceSignal,
        List<CompositionSignalRef> oppositionSignals,
        OppositionAggregation oppositionAggregation,
        double weight
) {
    public CompositionInteractionRule {
        Objects.requireNonNull(ruleId, "ruleId");
        if (ruleId.isBlank()) throw new IllegalArgumentException("ruleId must not be blank");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(sourceSignal, "sourceSignal");
        Objects.requireNonNull(oppositionSignals, "oppositionSignals");
        oppositionSignals = List.copyOf(oppositionSignals);
        Objects.requireNonNull(oppositionAggregation, "oppositionAggregation");
        if (oppositionSignals.size() != switch (oppositionAggregation) {
            case SINGLE -> 1;
            case COMPLEMENTARY_TWO -> 2;
            case COMPLEMENTARY_THREE -> 3;
        }) throw new IllegalArgumentException("Opposition signal count does not match aggregation");
        if (oppositionSignals.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("oppositionSignals contains null");
        }
        if (!Double.isFinite(weight) || weight < 0.0) throw new IllegalArgumentException("Invalid rule weight");
        weight = weight == 0.0 ? 0.0 : weight;
    }
}
