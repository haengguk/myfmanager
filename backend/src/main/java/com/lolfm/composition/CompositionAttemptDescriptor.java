package com.lolfm.composition;

import com.lolfm.simulator.Lane;
import com.lolfm.simulator.ObjectiveType;
import com.lolfm.simulator.StructureKind;
import com.lolfm.simulator.TeamSide;
import java.util.Objects;

/** Immutable structured metadata for one actual gameplay attempt. */
public record CompositionAttemptDescriptor(
        GameplayAttemptId attemptId,
        CompositionActionType actionType,
        TeamSide attemptOwnerSide,
        TeamSide initiatingSide,
        TeamSide defendingSide,
        FightScale fightScale,
        ObjectiveType objectiveType,
        boolean objectiveContested,
        StructureKind structureTargetType,
        Lane lane,
        int matchTimeSeconds,
        CompositionBaselineScoreDomain baselineScoreDomain,
        Double ownerBaselineScore,
        Double opponentBaselineScore
) {
    public CompositionAttemptDescriptor {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(fightScale, "fightScale");
        Objects.requireNonNull(baselineScoreDomain, "baselineScoreDomain");
        if (matchTimeSeconds < 0) throw new IllegalArgumentException("matchTimeSeconds must be non-negative");
        validateScore(ownerBaselineScore, "ownerBaselineScore");
        validateScore(opponentBaselineScore, "opponentBaselineScore");
        if ((ownerBaselineScore == null) != (opponentBaselineScore == null)) {
            throw new IllegalArgumentException("Both baseline scores must be present or absent");
        }
        if ((ownerBaselineScore != null) != (baselineScoreDomain != CompositionBaselineScoreDomain.NOT_AVAILABLE)) {
            throw new IllegalArgumentException("Baseline score domain does not match score availability");
        }
    }

    public CompositionAttemptDescriptor(GameplayAttemptId attemptId, CompositionActionType actionType,
                                        TeamSide owner, int matchTimeSeconds) {
        this(attemptId, actionType, owner, owner, owner == null ? null : owner.opposite(), FightScale.NONE,
                null, false, null, null, matchTimeSeconds,
                CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null);
    }

    private static void validateScore(Double score, String name) {
        if (score != null && (!Double.isFinite(score) || score < 0.0)) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
