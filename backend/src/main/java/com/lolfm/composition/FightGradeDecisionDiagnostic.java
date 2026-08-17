package com.lolfm.composition;

import com.lolfm.simulator.FightGrade;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Objects;

/** Match-scoped observation of the actual FightGrade stochastic path. */
public record FightGradeDecisionDiagnostic(
        long matchSeed,
        int caseIndex,
        GameplayAttemptId attemptId,
        TeamCompositionContext context,
        CompositionActionType actionType,
        CompositionBaselineScoreDomain scoreDomain,
        int timeSeconds,
        TeamSide winnerSide,
        TeamSide loserSide,
        TeamSide attackingSide,
        TeamSide defendingSide,
        double baselineGradeGap,
        double baselineWinnerPressure,
        double baselineDominance,
        double winnerModifierApplied,
        double severityModifierApplied,
        double finalSeverityInput,
        double legacyAdjustedGapContributionReference,
        double legacyDominanceContributionReference,
        double legacyTotalGradeCompositionReference,
        List<FightGradeBranchDiagnostic> branches,
        FightGrade selectedFightGrade,
        long firstRandomDrawOrdinal,
        int actualGradeRandomDrawCount,
        int diagnosticAdditionalRandomDrawCount,
        boolean directCompositionWinnerUsed,
        boolean directCompositionSeverityUsed,
        FightGradeCounterfactualCoverageClass counterfactualCoverageClass,
        boolean actualPathReconstructed
) {
    public FightGradeDecisionDiagnostic {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(scoreDomain, "scoreDomain");
        Objects.requireNonNull(winnerSide, "winnerSide");
        Objects.requireNonNull(loserSide, "loserSide");
        Objects.requireNonNull(selectedFightGrade, "selectedFightGrade");
        Objects.requireNonNull(counterfactualCoverageClass, "counterfactualCoverageClass");
        branches = List.copyOf(branches);
        if (branches.size() != 3) throw new IllegalArgumentException("ACE/BIG/NORMAL branch diagnostics required");
        if (diagnosticAdditionalRandomDrawCount != 0) throw new IllegalArgumentException("Diagnostics may not consume Random");
        long drawn = branches.stream().filter(x -> x.drawState() == FightGradeBranchDrawState.DRAWN).count();
        if (drawn != actualGradeRandomDrawCount || actualGradeRandomDrawCount < 1 || actualGradeRandomDrawCount > 3) {
            throw new IllegalArgumentException("Grade branch state/draw count mismatch");
        }
        if (severityModifierApplied != 0.0 || directCompositionSeverityUsed) {
            throw new IllegalArgumentException("Phase 13D-4C.5 severity must be zero reference");
        }
    }

    public String applicationKey() {
        return context.name() + "|" + actionType.name() + "|" + scoreDomain.name();
    }
}
