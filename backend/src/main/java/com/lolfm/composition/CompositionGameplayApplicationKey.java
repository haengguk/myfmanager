package com.lolfm.composition;

import java.util.Objects;

/** Structured identity of one frozen candidate score application point. */
public record CompositionGameplayApplicationKey(
        TeamCompositionContext context,
        CompositionActionType actionType,
        CompositionBaselineScoreDomain scoreDomain,
        double selectedGain
) {
    public CompositionGameplayApplicationKey {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(scoreDomain, "scoreDomain");
        if (!Double.isFinite(selectedGain) || selectedGain < 0.0) {
            throw new IllegalArgumentException("selectedGain must be finite and non-negative");
        }
    }

    public String stableId() {
        return context.name() + "|" + actionType.name() + "|" + scoreDomain.name();
    }
}
