package com.lolfm.simulator;

import com.lolfm.domain.Position;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Mutable macro assignment owned by one match and one team side. */
public final class TeamMacroTeamState {
    private TeamMacroPlan currentPlan;
    private Lane targetLane;
    private ObjectiveType targetObjective;
    private int startedAtSeconds = -1;
    private int activeUntilSeconds = -1;
    private int nextEvaluationAtSeconds = -1;
    private int planSequence;
    private TeamMacroPlan previousPlan;
    private TeamMacroPlan lastSelectedPlan;
    private MacroPlanStatus status = MacroPlanStatus.NOT_STARTED;
    private MacroPlanEndReason endReason;
    private int lastEvaluationDueAtSeconds = -1;
    private int lastEvaluationAtSeconds = -1;
    private String lastEvaluationSkippedReason;
    private int lastSelectionRandomConsumptionCount;
    private final EnumSet<Position> assignedPositions = EnumSet.noneOf(Position.class);
    private MacroActionResult lastActionResult = MacroActionResult.NOT_ATTEMPTED;
    private StructureKind lastDestroyedStructure;
    private TowerTier lastDestroyedTowerTier;
    private Lane lastStructureLane;

    public TeamMacroPlan getCurrentPlan() { return currentPlan; }
    public Lane getTargetLane() { return targetLane; }
    public ObjectiveType getTargetObjective() { return targetObjective; }
    public int getStartedAtSeconds() { return startedAtSeconds; }
    public int getActiveUntilSeconds() { return activeUntilSeconds; }
    public int getNextEvaluationAtSeconds() { return nextEvaluationAtSeconds; }
    public int getPlanSequence() { return planSequence; }
    public TeamMacroPlan getPreviousPlan() { return previousPlan; }
    public TeamMacroPlan getLastSelectedPlan() { return lastSelectedPlan; }
    public MacroPlanStatus getStatus() { return status; }
    public MacroPlanEndReason getEndReason() { return endReason; }
    public int getLastEvaluationDueAtSeconds() { return lastEvaluationDueAtSeconds; }
    public int getLastEvaluationAtSeconds() { return lastEvaluationAtSeconds; }
    public String getLastEvaluationSkippedReason() { return lastEvaluationSkippedReason; }
    public int getLastSelectionRandomConsumptionCount() { return lastSelectionRandomConsumptionCount; }
    public Set<Position> getAssignedPositions() {
        if (assignedPositions.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(assignedPositions));
    }
    public MacroActionResult getLastActionResult() { return lastActionResult; }
    public StructureKind getLastDestroyedStructure() { return lastDestroyedStructure; }
    public TowerTier getLastDestroyedTowerTier() { return lastDestroyedTowerTier; }
    public Lane getLastStructureLane() { return lastStructureLane; }

    boolean isActiveAt(int timeSeconds) {
        return currentPlan != null && startedAtSeconds >= 0 && timeSeconds < activeUntilSeconds;
    }

    void scheduleFirstEvaluation(int midGameStartedAtSeconds) {
        if (nextEvaluationAtSeconds >= 0 || status == MacroPlanStatus.MATCH_ENDED) return;
        nextEvaluationAtSeconds = midGameStartedAtSeconds + MidGameMacroRuleConfig.FIRST_EVALUATION_DELAY_SECONDS;
        status = MacroPlanStatus.WAITING_FOR_EVALUATION;
        endReason = null;
    }

    boolean isDueAt(int timeSeconds) {
        return nextEvaluationAtSeconds >= 0 && timeSeconds >= nextEvaluationAtSeconds;
    }

    int advanceScheduleAfterEvaluation(int actualTimeSeconds, int selectionRandomConsumptionCount) {
        if (!isDueAt(actualTimeSeconds)) throw new IllegalStateException("macro evaluation is not due");
        int due = nextEvaluationAtSeconds;
        do {
            nextEvaluationAtSeconds += MidGameMacroRuleConfig.EVALUATION_INTERVAL_SECONDS;
        } while (nextEvaluationAtSeconds <= actualTimeSeconds);
        lastEvaluationDueAtSeconds = due;
        lastEvaluationAtSeconds = actualTimeSeconds;
        lastEvaluationSkippedReason = null;
        lastSelectionRandomConsumptionCount = selectionRandomConsumptionCount;
        return due;
    }

    void expireIfNeeded(int timeSeconds) {
        if (currentPlan != null && activeUntilSeconds >= 0 && timeSeconds >= activeUntilSeconds) {
            previousPlan = currentPlan;
            currentPlan = null;
            targetLane = null;
            targetObjective = null;
            startedAtSeconds = -1;
            activeUntilSeconds = -1;
            assignedPositions.clear();
            lastActionResult = MacroActionResult.NOT_ATTEMPTED;
            status = MacroPlanStatus.EXPIRED;
            endReason = MacroPlanEndReason.EXPIRED;
        }
    }

    void beginPlan(TeamMacroPlan plan, Lane lane, ObjectiveType objective, Set<Position> positions, int timeSeconds) {
        previousPlan = currentPlan == null ? previousPlan : currentPlan;
        currentPlan = plan;
        lastSelectedPlan = plan;
        targetLane = lane;
        targetObjective = objective;
        startedAtSeconds = timeSeconds;
        activeUntilSeconds = timeSeconds + MidGameMacroRuleConfig.PLAN_DURATION_SECONDS;
        planSequence++;
        assignedPositions.clear();
        assignedPositions.addAll(positions);
        lastActionResult = MacroActionResult.NOT_ATTEMPTED;
        lastDestroyedStructure = null;
        lastDestroyedTowerTier = null;
        lastStructureLane = null;
        status = MacroPlanStatus.ACTIVE;
        endReason = null;
    }

    void setLastActionResult(MacroActionResult result) { lastActionResult = result; }
    void recordStructure(StructureOutcome outcome) {
        lastDestroyedStructure = outcome.structureKind();
        lastDestroyedTowerTier = outcome.towerTier();
        lastStructureLane = outcome.lane();
    }

    void cancel(MacroPlanEndReason reason) {
        if (currentPlan != null) previousPlan = currentPlan;
        currentPlan = null;
        targetLane = null;
        targetObjective = null;
        startedAtSeconds = -1;
        activeUntilSeconds = -1;
        assignedPositions.clear();
        lastActionResult = reason == MacroPlanEndReason.FEATURE_DISABLED
                ? MacroActionResult.NOT_ATTEMPTED : MacroActionResult.INELIGIBLE;
        status = reason == MacroPlanEndReason.FEATURE_DISABLED
                ? MacroPlanStatus.DISABLED : MacroPlanStatus.CANCELLED;
        endReason = reason;
    }

    void finishMatch(int timeSeconds) {
        if (isDueAt(timeSeconds)) {
            lastEvaluationDueAtSeconds = nextEvaluationAtSeconds;
            lastEvaluationAtSeconds = timeSeconds;
            lastEvaluationSkippedReason = "GAME_FINISHED";
            lastSelectionRandomConsumptionCount = 0;
        }
        nextEvaluationAtSeconds = -1;
        status = MacroPlanStatus.MATCH_ENDED;
        endReason = MacroPlanEndReason.MATCH_ENDED;
    }
}
