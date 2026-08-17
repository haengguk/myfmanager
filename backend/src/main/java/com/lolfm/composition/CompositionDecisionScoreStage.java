package com.lolfm.composition;

import java.util.Objects;

/** One immutable stage in the exact runtime score pipeline. */
public record CompositionDecisionScoreStage(
        String stageName,
        double scoreBefore,
        double factorInput,
        double factorValue,
        double scoreAfter,
        CompositionFactorAvailability availability
) {
    public CompositionDecisionScoreStage {
        Objects.requireNonNull(stageName, "stageName");
        Objects.requireNonNull(availability, "availability");
        if (!Double.isFinite(scoreBefore) || !Double.isFinite(factorInput)
                || !Double.isFinite(factorValue) || !Double.isFinite(scoreAfter)) {
            throw new IllegalArgumentException("Decision score stages require finite values");
        }
    }
}
