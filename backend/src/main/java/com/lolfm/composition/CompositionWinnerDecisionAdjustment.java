package com.lolfm.composition;

/** Pure decision-local winner adjustment; no shared score mutation. */
public record CompositionWinnerDecisionAdjustment(
        String applicationKey,
        String gainStatus,
        double baselineGap,
        double rawEdge,
        double referenceGain,
        double winnerModifier,
        double winnerDecisionGap,
        CompositionCombatRole perspectiveRole
) {
    public CompositionWinnerDecisionAdjustment {
        if (!Double.isFinite(baselineGap) || !Double.isFinite(rawEdge) || !Double.isFinite(referenceGain)
                || !Double.isFinite(winnerModifier) || !Double.isFinite(winnerDecisionGap)) {
            throw new IllegalArgumentException("Winner adjustment values must be finite");
        }
    }
}
