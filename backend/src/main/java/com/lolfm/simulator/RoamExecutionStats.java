package com.lolfm.simulator;

import com.lolfm.domain.Position;

/** Match-scoped, observational-only roam execution counters. */
public final class RoamExecutionStats {
    private final boolean enabled;
    private int roamResolverEvaluations, midCandidateEvaluationsBlue, midCandidateEvaluationsRed;
    private int supportCandidateEvaluationsBlue, supportCandidateEvaluationsRed;
    private int roamIneligibleDead, roamIneligibleCooldown, roamIneligibleActivity, roamIneligibleNoTarget;
    private int roamSkippedByHigherPriorityActualCombat, roamTriggerRolls, roamTriggersBlue, roamTriggersRed;
    private int multipleRoamTriggers, actualRoamAttempts, actualMidRoams, actualSupportRoams;
    private int unselectedTriggeredCandidates, midToTopAttempts, midToBotAttempts, supportToMidAttempts;
    private int roamNoKill, roamingSideKills, defendingSideKills, roamBlockedLaneCombat;
    private int roamEvaluationFallthroughToLaneCombat, roamBlockedGeneric;
    private int activityCreated, activityReturned, activityClearedByDeath;

    public RoamExecutionStats(boolean enabled) { this.enabled = enabled; }

    public void recordEvaluation() { if (enabled) roamResolverEvaluations++; }
    public void recordCandidateEvaluation(Position position, TeamSide side) {
        if (!enabled) return;
        if (position == Position.MID) { if (side == TeamSide.BLUE) midCandidateEvaluationsBlue++; else midCandidateEvaluationsRed++; }
        else { if (side == TeamSide.BLUE) supportCandidateEvaluationsBlue++; else supportCandidateEvaluationsRed++; }
    }
    public void recordIneligible(RoamIneligibility reason) {
        if (!enabled) return;
        switch (reason) {
            case DEAD -> roamIneligibleDead++;
            case COOLDOWN -> roamIneligibleCooldown++;
            case ACTIVITY -> roamIneligibleActivity++;
            case NO_TARGET -> roamIneligibleNoTarget++;
            case NONE -> { }
        }
    }
    public void recordSkippedByHigherPriority() { if (enabled) roamSkippedByHigherPriorityActualCombat++; }
    public void recordTriggerRoll() { if (enabled) roamTriggerRolls++; }
    public void recordTrigger(TeamSide side) { if (enabled) { if (side == TeamSide.BLUE) roamTriggersBlue++; else roamTriggersRed++; } }
    public void recordMultipleTriggers() { if (enabled) multipleRoamTriggers++; }
    public void recordUnselectedTriggers(int count) { if (enabled) unselectedTriggeredCandidates += count; }
    public void recordAttempt(Position position, Lane target) {
        if (!enabled) return;
        actualRoamAttempts++;
        if (position == Position.MID) { actualMidRoams++; if (target == Lane.TOP) midToTopAttempts++; else midToBotAttempts++; }
        else { actualSupportRoams++; supportToMidAttempts++; }
    }
    public void recordOutcome(RoamOutcome outcome) {
        if (!enabled) return;
        switch (outcome) { case NO_KILL -> roamNoKill++; case ROAMING_SIDE_KILL -> roamingSideKills++; case DEFENDING_SIDE_KILL -> defendingSideKills++; }
    }
    public void recordBlockedLaneCombat() { if (enabled) roamBlockedLaneCombat++; }
    public void recordFallthroughToLaneCombat() { if (enabled) roamEvaluationFallthroughToLaneCombat++; }
    public void recordBlockedGeneric() { if (enabled) roamBlockedGeneric++; }
    public void recordActivityCreated() { if (enabled) activityCreated++; }
    public void recordActivityReturned() { if (enabled) activityReturned++; }
    public void recordActivityClearedByDeath() { if (enabled) activityClearedByDeath++; }

    public RoamExecutionStatsSnapshot snapshot() {
        return new RoamExecutionStatsSnapshot(roamResolverEvaluations, midCandidateEvaluationsBlue,
                midCandidateEvaluationsRed, supportCandidateEvaluationsBlue, supportCandidateEvaluationsRed,
                roamIneligibleDead, roamIneligibleCooldown, roamIneligibleActivity, roamIneligibleNoTarget,
                roamSkippedByHigherPriorityActualCombat, roamTriggerRolls, roamTriggersBlue, roamTriggersRed,
                multipleRoamTriggers, actualRoamAttempts, actualMidRoams, actualSupportRoams,
                unselectedTriggeredCandidates, midToTopAttempts, midToBotAttempts, supportToMidAttempts,
                roamNoKill, roamingSideKills, defendingSideKills, roamBlockedLaneCombat,
                roamEvaluationFallthroughToLaneCombat, roamBlockedGeneric, activityCreated, activityReturned,
                activityClearedByDeath);
    }
}
