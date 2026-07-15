package com.lolfm.simulator;

import com.lolfm.domain.Position;
import java.util.Map;

public record MidGameMacroExecutionStatsSnapshot(
        boolean enabled,
        int evaluationTicks,
        int blueEvaluations,
        int redEvaluations,
        int phaseIneligible,
        int notDue,
        int featureDisabled,
        int duplicateEvaluationRejected,
        int selectionRolls,
        int singleCandidateNoRoll,
        int samePlanRepeatPenaltyApplied,
        int resetSelections,
        int actionAttempts,
        int actionIneligible,
        int pushRolls,
        int pushSuccesses,
        int pushFailures,
        int existingStructureActionBlocked,
        int targetMissingAfterSelection,
        int setupStarts,
        int setupCaptureCancellations,
        int setupExpiryEndings,
        int duplicateStructure,
        int deadAssignmentErrors,
        int combatParticipantAssignmentErrors,
        Map<TeamMacroPlan, Integer> blueSelections,
        Map<TeamMacroPlan, Integer> redSelections,
        Map<TeamMacroPlan, Integer> eligibleCounts,
        Map<TeamMacroPlan, Integer> ineligibleCounts,
        Map<Position, Integer> assignments,
        Map<Position, Integer> farmBlockedTicks
) { }
