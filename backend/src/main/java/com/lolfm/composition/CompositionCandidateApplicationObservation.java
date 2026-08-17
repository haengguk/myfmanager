package com.lolfm.composition;

import com.lolfm.simulator.TeamSide;
import java.util.Objects;

/** Immutable internal-only record of one candidate application or deferred key. */
public record CompositionCandidateApplicationObservation(
        long matchSeed,
        GameplayAttemptId attemptId,
        int matchTimeSeconds,
        CompositionActionType actionType,
        TeamCompositionContext context,
        CompositionApplicationPoint applicationPoint,
        CompositionBaselineScoreDomain scoreDomain,
        TeamSide perspectiveSide,
        TeamSide opponentSide,
        double perspectiveRawEdge,
        double selectedGain,
        boolean baselineAvailable,
        Double perspectiveBaselineScore,
        Double opponentBaselineScore,
        Double baselineGap,
        double gapModifier,
        double perspectiveAdjustment,
        double opponentAdjustment,
        Double adjustedPerspectiveScore,
        Double adjustedOpponentScore,
        Double adjustedGap,
        boolean midpointPreserved,
        String baselineGapSign,
        String adjustedGapSign,
        boolean signFlip,
        String flipSubtype,
        boolean applicationApplied,
        String applicationKey,
        String candidateVersion,
        String candidateHash,
        String authorizationPolicyHash,
        String deferralReason
) {
    public CompositionCandidateApplicationObservation {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(applicationPoint, "applicationPoint");
        Objects.requireNonNull(scoreDomain, "scoreDomain");
        Objects.requireNonNull(perspectiveSide, "perspectiveSide");
        Objects.requireNonNull(opponentSide, "opponentSide");
        Objects.requireNonNull(baselineGapSign, "baselineGapSign");
        Objects.requireNonNull(adjustedGapSign, "adjustedGapSign");
        Objects.requireNonNull(flipSubtype, "flipSubtype");
        Objects.requireNonNull(applicationKey, "applicationKey");
        Objects.requireNonNull(candidateVersion, "candidateVersion");
        Objects.requireNonNull(candidateHash, "candidateHash");
        Objects.requireNonNull(authorizationPolicyHash, "authorizationPolicyHash");
        Objects.requireNonNull(deferralReason, "deferralReason");
        if (matchTimeSeconds < 0 || !Double.isFinite(perspectiveRawEdge) || !Double.isFinite(selectedGain)
                || !Double.isFinite(gapModifier) || !Double.isFinite(perspectiveAdjustment)
                || !Double.isFinite(opponentAdjustment)) throw new IllegalArgumentException("Invalid candidate observation");
        if (baselineAvailable != (perspectiveBaselineScore != null && opponentBaselineScore != null)) {
            throw new IllegalArgumentException("Baseline availability does not match scores");
        }
        if (midpointPreserved && Math.abs((perspectiveAdjustment + opponentAdjustment)) > 1.0e-9) {
            throw new IllegalArgumentException("Midpoint adjustment is not preserved");
        }
    }
}
