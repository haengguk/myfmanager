package com.lolfm.composition;

import com.lolfm.simulator.TeamSide;
import java.util.Objects;

/** Match-scoped actual winner-channel observation. */
public record CompositionWinnerChannelObservation(
        long matchSeed,
        int caseIndex,
        GameplayAttemptId attemptId,
        TeamCompositionContext context,
        CompositionActionType actionType,
        CompositionBaselineScoreDomain scoreDomain,
        int timeSeconds,
        TeamSide perspectiveSide,
        TeamSide attackingSide,
        TeamSide defendingSide,
        CompositionCombatRole perspectiveRole,
        double baselineGap,
        double baselineWinnerProbability,
        double rawWinnerEdge,
        String winnerGainStatus,
        double winnerReferenceGain,
        double winnerModifier,
        double winnerDecisionGap,
        double winnerProbability,
        double winnerRandomSample,
        long winnerRandomDrawOrdinal,
        TeamSide winnerResult
) {
    public CompositionWinnerChannelObservation {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(scoreDomain, "scoreDomain");
        Objects.requireNonNull(perspectiveSide, "perspectiveSide");
        Objects.requireNonNull(perspectiveRole, "perspectiveRole");
        Objects.requireNonNull(winnerGainStatus, "winnerGainStatus");
        Objects.requireNonNull(winnerResult, "winnerResult");
    }

    public String applicationKey() {
        return context.name() + "|" + actionType.name() + "|" + scoreDomain.name();
    }
}
