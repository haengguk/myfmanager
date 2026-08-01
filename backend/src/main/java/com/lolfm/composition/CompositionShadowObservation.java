package com.lolfm.composition;

import com.lolfm.simulator.TeamSide;
import java.util.Objects;

/** Immutable internal-only observation. It is intentionally absent from timeline/API models. */
public record CompositionShadowObservation(
        long matchSeed,
        GameplayAttemptId attemptId,
        int matchTimeSeconds,
        CompositionActionType actionType,
        TeamCompositionContext context,
        TeamSide attemptOwnerSide,
        TeamSide perspectiveSide,
        TeamSide opponentSide,
        double blueRawSignedEdge,
        double redRawSignedEdge,
        double perspectiveRawEdge,
        String candidateVersion,
        String candidateHash,
        CompositionInteractionFormula formula,
        CompositionApplicationPoint applicationPoint,
        CompositionBaselineScoreDomain baselineScoreDomain,
        boolean baselineScoreAvailable,
        Double perspectiveBaselineScore,
        Double opponentBaselineScore,
        Double baselineScoreGap,
        CompositionApplicationEligibility applicationEligibility,
        boolean applicationEligible,
        String eligibilityReason,
        String scoreCapturePoint,
        String scoreCaptureEvidence,
        boolean applicationApplied,
        double appliedModifier,
        String routingReason
) {
    public CompositionShadowObservation {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(perspectiveSide, "perspectiveSide");
        Objects.requireNonNull(opponentSide, "opponentSide");
        Objects.requireNonNull(candidateVersion, "candidateVersion");
        Objects.requireNonNull(candidateHash, "candidateHash");
        Objects.requireNonNull(formula, "formula");
        Objects.requireNonNull(applicationPoint, "applicationPoint");
        Objects.requireNonNull(baselineScoreDomain, "baselineScoreDomain");
        Objects.requireNonNull(applicationEligibility, "applicationEligibility");
        Objects.requireNonNull(eligibilityReason, "eligibilityReason");
        Objects.requireNonNull(scoreCapturePoint, "scoreCapturePoint");
        Objects.requireNonNull(scoreCaptureEvidence, "scoreCaptureEvidence");
        Objects.requireNonNull(routingReason, "routingReason");
        if (matchTimeSeconds < 0) throw new IllegalArgumentException("matchTimeSeconds must be non-negative");
        if (!Double.isFinite(blueRawSignedEdge) || !Double.isFinite(redRawSignedEdge)
                || !Double.isFinite(perspectiveRawEdge)) throw new IllegalArgumentException("Non-finite raw edge");
        if (Math.copySign(1.0, blueRawSignedEdge) == -1.0 && blueRawSignedEdge == 0.0) blueRawSignedEdge = 0.0;
        if (Math.copySign(1.0, redRawSignedEdge) == -1.0 && redRawSignedEdge == 0.0) redRawSignedEdge = 0.0;
        if (Math.copySign(1.0, perspectiveRawEdge) == -1.0 && perspectiveRawEdge == 0.0) perspectiveRawEdge = 0.0;
        if (baselineScoreAvailable != (perspectiveBaselineScore != null && opponentBaselineScore != null)) {
            throw new IllegalArgumentException("Baseline availability does not match scores");
        }
        if (baselineScoreGap != null && !Double.isFinite(baselineScoreGap)) {
            throw new IllegalArgumentException("Non-finite baseline gap");
        }
        if (baselineScoreGap != null && baselineScoreGap == 0.0) baselineScoreGap = 0.0;
        if (applicationEligible != applicationEligibility.eligible()) {
            throw new IllegalArgumentException("Eligibility boolean does not match structured eligibility");
        }
        if (applicationEligible && !baselineScoreAvailable) {
            throw new IllegalArgumentException("Eligible observation requires an available baseline score");
        }
        if (applicationApplied) throw new IllegalArgumentException("Shadow observations cannot apply gameplay");
        if (appliedModifier != 0.0) throw new IllegalArgumentException("Shadow modifier must be zero");
        appliedModifier = 0.0;
    }
}
