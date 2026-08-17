package com.lolfm.composition;

import com.lolfm.simulator.TeamSide;

/** Internal match-scoped comparison of one shared-random local decision. */
public record CompositionLocalDecisionComparison(
        long matchSeed,
        GameplayAttemptId attemptId,
        int matchTimeSeconds,
        String applicationKey,
        String decisionType,
        TeamSide perspectiveSide,
        long sharedRandomSampleIdentity,
        double sharedRandomSampleValue,
        double baselinePerspectiveScore,
        double baselineOpponentScore,
        double adjustedPerspectiveScore,
        double adjustedOpponentScore,
        String baselineLocalDecision,
        String candidateLocalDecision,
        boolean localOutcomeChanged,
        String marginBand,
        boolean tieBreakLocalFlip,
        boolean materialLocalFlip,
        boolean highMarginLocalFlip,
        boolean comparisonAvailable,
        String unavailableReason
) {
    public CompositionLocalDecisionComparison {
        if (matchTimeSeconds < 0 || !Double.isFinite(sharedRandomSampleValue)
                || !Double.isFinite(baselinePerspectiveScore) || !Double.isFinite(baselineOpponentScore)
                || !Double.isFinite(adjustedPerspectiveScore) || !Double.isFinite(adjustedOpponentScore)) {
            throw new IllegalArgumentException("Invalid local decision comparison");
        }
        if (applicationKey == null || decisionType == null || perspectiveSide == null
                || baselineLocalDecision == null || candidateLocalDecision == null
                || marginBand == null || unavailableReason == null) {
            throw new NullPointerException("Local comparison fields must not be null");
        }
    }

    /** Compatibility aliases for the Phase 13D-4C diagnostic consumers. */
    public double baselineScore() { return baselinePerspectiveScore; }
    public double candidateScore() { return adjustedPerspectiveScore; }
    public String baselineDecision() { return baselineLocalDecision; }
    public String candidateDecision() { return candidateLocalDecision; }
    public boolean changed() { return localOutcomeChanged; }
    public boolean tieBreakChanged() { return tieBreakLocalFlip; }
    public boolean material() { return materialLocalFlip; }
    public boolean highBandFlip() { return highMarginLocalFlip; }

    /** Backward-compatible constructor for existing focused policy tests. */
    public CompositionLocalDecisionComparison(
            long matchSeed, GameplayAttemptId attemptId, int matchTimeSeconds,
            String applicationKey, String decisionType, TeamSide perspectiveSide,
            long sharedRandomSampleIdentity, double sharedRandomSampleValue,
            double baselineScore, double candidateScore, String baselineDecision,
            String candidateDecision, boolean changed, String marginBand,
            boolean tieBreakChanged, boolean material, boolean highBandFlip,
            boolean comparisonAvailable, String unavailableReason
    ) {
        this(matchSeed, attemptId, matchTimeSeconds, applicationKey, decisionType,
                perspectiveSide, sharedRandomSampleIdentity, sharedRandomSampleValue,
                baselineScore, 0.0, candidateScore, 0.0, baselineDecision,
                candidateDecision, changed, marginBand, tieBreakChanged, material,
                highBandFlip, comparisonAvailable, unavailableReason);
    }
}
