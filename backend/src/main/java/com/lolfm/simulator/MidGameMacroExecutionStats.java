package com.lolfm.simulator;

import com.lolfm.domain.Position;
import java.util.EnumMap;
import java.util.Map;

/** Observational, match-scoped macro counters. It is never read by gameplay decisions. */
public final class MidGameMacroExecutionStats {
    private final boolean enabled;
    private int evaluationTicks;
    private int blueEvaluations;
    private int redEvaluations;
    private int phaseIneligible;
    private int notDue;
    private int featureDisabled;
    private int duplicateEvaluationRejected;
    private int selectionRolls;
    private int singleCandidateNoRoll;
    private int samePlanRepeatPenaltyApplied;
    private int resetSelections;
    private int actionAttempts;
    private int actionIneligible;
    private int pushRolls;
    private int pushSuccesses;
    private int pushFailures;
    private int existingStructureActionBlocked;
    private int targetMissingAfterSelection;
    private int setupStarts;
    private int setupCaptureCancellations;
    private int setupExpiryEndings;
    private int duplicateStructure;
    private int deadAssignmentErrors;
    private int combatParticipantAssignmentErrors;
    private final EnumMap<TeamMacroPlan, Integer> blueSelections = counts();
    private final EnumMap<TeamMacroPlan, Integer> redSelections = counts();
    private final EnumMap<TeamMacroPlan, Integer> eligibleCounts = counts();
    private final EnumMap<TeamMacroPlan, Integer> ineligibleCounts = counts();
    private final EnumMap<Position, Integer> assignments = new EnumMap<>(Position.class);
    private final EnumMap<Position, Integer> farmBlockedTicks = new EnumMap<>(Position.class);

    public MidGameMacroExecutionStats(boolean enabled) {
        this.enabled = enabled;
        for (Position position : Position.values()) {
            assignments.put(position, 0);
            farmBlockedTicks.put(position, 0);
        }
    }

    private EnumMap<TeamMacroPlan, Integer> counts() {
        EnumMap<TeamMacroPlan, Integer> result = new EnumMap<>(TeamMacroPlan.class);
        for (TeamMacroPlan plan : TeamMacroPlan.values()) result.put(plan, 0);
        return result;
    }

    void recordEvaluation(TeamSide side) { evaluationTicks++; if (side == TeamSide.BLUE) blueEvaluations++; else redEvaluations++; }
    void recordPhaseIneligible() { phaseIneligible++; }
    void recordNotDue() { notDue++; }
    void recordFeatureDisabled() { featureDisabled++; }
    void recordDuplicateEvaluation() { duplicateEvaluationRejected++; }
    void recordCandidate(TeamMacroPlan plan, boolean eligible) {
        (eligible ? eligibleCounts : ineligibleCounts).merge(plan, 1, Integer::sum);
    }
    void recordSelection(TeamSide side, TeamMacroPlan plan, boolean roll, boolean repeatPenalty) {
        (side == TeamSide.BLUE ? blueSelections : redSelections).merge(plan, 1, Integer::sum);
        if (roll) selectionRolls++; else singleCandidateNoRoll++;
        if (repeatPenalty) samePlanRepeatPenaltyApplied++;
        if (plan == TeamMacroPlan.RESET_AND_FARM) resetSelections++;
    }
    void recordAssignment(Position position) { assignments.merge(position, 1, Integer::sum); }
    void recordAssignmentValidation(boolean dead, boolean combatParticipant) {
        if (dead) deadAssignmentErrors++;
        if (combatParticipant) combatParticipantAssignmentErrors++;
    }
    void recordActionAttempt() { actionAttempts++; }
    void recordActionIneligible() { actionIneligible++; }
    void recordPushRoll() { pushRolls++; }
    void recordPushSuccess() { pushSuccesses++; }
    void recordPushFailure() { pushFailures++; }
    void recordExistingStructureActionBlocked() { existingStructureActionBlocked++; }
    void recordTargetMissingAfterSelection() { targetMissingAfterSelection++; }
    void recordSetupStart() { setupStarts++; }
    void recordSetupCaptureCancellation() { setupCaptureCancellations++; }
    void recordSetupExpiry() { setupExpiryEndings++; }
    void recordDuplicateStructure() { duplicateStructure++; }
    void recordFarmBlockedTick(Position position) { farmBlockedTicks.merge(position, 1, Integer::sum); }

    public MidGameMacroExecutionStatsSnapshot snapshot() {
        return new MidGameMacroExecutionStatsSnapshot(enabled, evaluationTicks, blueEvaluations, redEvaluations,
                phaseIneligible, notDue, featureDisabled, duplicateEvaluationRejected, selectionRolls,
                singleCandidateNoRoll, samePlanRepeatPenaltyApplied, resetSelections, actionAttempts,
                actionIneligible, pushRolls, pushSuccesses, pushFailures, existingStructureActionBlocked,
                targetMissingAfterSelection, setupStarts, setupCaptureCancellations, setupExpiryEndings,
                duplicateStructure, deadAssignmentErrors, combatParticipantAssignmentErrors, Map.copyOf(blueSelections), Map.copyOf(redSelections),
                Map.copyOf(eligibleCounts), Map.copyOf(ineligibleCounts), Map.copyOf(assignments),
                Map.copyOf(farmBlockedTicks));
    }
}
