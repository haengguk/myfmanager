package com.lolfm.composition;

import com.lolfm.simulator.TeamSide;
import java.util.Objects;

/** Result of routing one structured actual attempt to at most one context. */
public record CompositionContextRouting(
        boolean mapped,
        TeamCompositionContext context,
        TeamSide perspectiveSide,
        String mappingReason,
        CompositionApplicationPoint applicationPoint,
        CompositionBaselineScoreDomain scoreDomain,
        boolean baselineScoreAvailable,
        Double perspectiveBaselineScore,
        Double opponentBaselineScore,
        CompositionApplicationEligibility applicationEligibility,
        String eligibilityReason,
        String scoreCapturePoint,
        String scoreCaptureEvidence
) {
    public CompositionContextRouting {
        Objects.requireNonNull(mappingReason, "mappingReason");
        Objects.requireNonNull(applicationPoint, "applicationPoint");
        Objects.requireNonNull(scoreDomain, "scoreDomain");
        Objects.requireNonNull(applicationEligibility, "applicationEligibility");
        Objects.requireNonNull(eligibilityReason, "eligibilityReason");
        Objects.requireNonNull(scoreCapturePoint, "scoreCapturePoint");
        Objects.requireNonNull(scoreCaptureEvidence, "scoreCaptureEvidence");
        if (!mapped && (context != null || perspectiveSide != null)) {
            throw new IllegalArgumentException("Unmapped routing cannot have context or perspective");
        }
        if (mapped && (context == null || perspectiveSide == null)) {
            throw new IllegalArgumentException("Mapped routing requires context and perspective");
        }
        if (baselineScoreAvailable != (perspectiveBaselineScore != null && opponentBaselineScore != null)) {
            throw new IllegalArgumentException("Baseline availability does not match scores");
        }
        if (applicationEligibility.eligible() && !baselineScoreAvailable) {
            throw new IllegalArgumentException("Application eligibility requires available baseline scores");
        }
    }

    public static CompositionContextRouting unmapped(String reason, CompositionBaselineScoreDomain domain) {
        return new CompositionContextRouting(false, null, null, reason,
                CompositionApplicationPoint.NOT_AVAILABLE, domain, false, null, null,
                CompositionApplicationEligibility.INELIGIBLE_AMBIGUOUS_APPLICATION_POINT,
                reason, "NOT_AVAILABLE", "NO_MAPPED_APPLICATION_POINT");
    }
}
