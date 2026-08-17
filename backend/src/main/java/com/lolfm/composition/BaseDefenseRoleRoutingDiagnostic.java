package com.lolfm.composition;

import com.lolfm.simulator.TeamSide;
import java.util.Objects;

/** Structured BASE_DEFENSE role routing; candidate application remains match-scoped and audit-only. */
public record BaseDefenseRoleRoutingDiagnostic(
        long matchSeed,
        int caseIndex,
        GameplayAttemptId attemptId,
        int timeSeconds,
        TeamSide attackingSide,
        TeamSide defendingSide,
        double attackerPerspectiveSignal,
        double defenderPerspectiveSignal,
        double canonicalAttackerAdvantageSignal,
        double mirroredRoleSignal,
        String numericTransformStatus,
        double appliedWinnerModifier,
        boolean roleSelectedFromWinnerResult,
        boolean keySpecificCandidateApplied
) {
    public BaseDefenseRoleRoutingDiagnostic(long matchSeed, int caseIndex, GameplayAttemptId attemptId,
                                            int timeSeconds, TeamSide attackingSide, TeamSide defendingSide,
                                            double attackerPerspectiveSignal, double defenderPerspectiveSignal,
                                            double canonicalAttackerAdvantageSignal, double mirroredRoleSignal,
                                            String numericTransformStatus, double appliedWinnerModifier,
                                            boolean roleSelectedFromWinnerResult) {
        this(matchSeed, caseIndex, attemptId, timeSeconds, attackingSide, defendingSide,
                attackerPerspectiveSignal, defenderPerspectiveSignal, canonicalAttackerAdvantageSignal,
                mirroredRoleSignal, numericTransformStatus, appliedWinnerModifier,
                roleSelectedFromWinnerResult, false);
    }

    public BaseDefenseRoleRoutingDiagnostic {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(attackingSide, "attackingSide");
        Objects.requireNonNull(defendingSide, "defendingSide");
        Objects.requireNonNull(numericTransformStatus, "numericTransformStatus");
        if (attackingSide == defendingSide) throw new IllegalArgumentException("Attacker and defender must differ");
        if (!keySpecificCandidateApplied && appliedWinnerModifier != 0.0) {
            throw new IllegalArgumentException("BASE_DEFENSE winner modifier requires key-specific candidate authorization");
        }
        if (Double.compare(canonicalAttackerAdvantageSignal, -mirroredRoleSignal) != 0) {
            throw new IllegalArgumentException("BASE_DEFENSE mirrored role signal must be exact sign reverse");
        }
        if (roleSelectedFromWinnerResult) throw new IllegalArgumentException("Combat role may not be inferred from winner result");
    }
}
