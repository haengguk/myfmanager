package com.lolfm.simulator;

/** Per-game structured execution counters for diagnostics; no combat probability depends on this state. */
public final class CombatExecutionStats {
    private static final int CHECKPOINT_SECONDS = 840;
    private int jungleGankEvaluations;
    private int jungleGankAllTriggersFailed;
    private int jungleGankNoEligibleSides;
    private int jungleGankTriggerRolls;
    private int jungleGankTriggerSuccesses;
    private int jungleGankUnselectedTriggerSuccesses;
    private int jungleGankFallthroughs;
    private int jungleGankAttempts;
    private int counterGankAttempts;
    private int laneCombatResolverCalls;
    private int laneCombatTriggeredLanes;
    private int laneCombatAttempts;
    private int laneCombatKills;
    private int genericSkirmishCalls;
    private int genericSkirmishKills;

    public void recordJungleGankEvaluation() { jungleGankEvaluations++; }
    public void recordJungleGankAllTriggersFailed() { jungleGankAllTriggersFailed++; }
    public void recordJungleGankNoEligibleSides() { jungleGankNoEligibleSides++; }
    public void recordJungleGankTriggerRoll() { jungleGankTriggerRolls++; }
    public void recordJungleGankTriggerSuccess() { jungleGankTriggerSuccesses++; }
    public void recordJungleGankUnselectedTriggerSuccesses(int count) {
        jungleGankUnselectedTriggerSuccesses += count;
    }
    public void recordJungleGankFallthrough() { jungleGankFallthroughs++; }
    public void recordJungleGankAttempt() { jungleGankAttempts++; }
    public void recordCounterGankAttempt() { counterGankAttempts++; }
    public void recordLaneCombatResolverCall(int timeSeconds) { if (timeSeconds <= CHECKPOINT_SECONDS) laneCombatResolverCalls++; }
    public void recordLaneCombatTriggeredLanes(int count) { laneCombatTriggeredLanes += count; }
    public void recordLaneCombatAttempt() { laneCombatAttempts++; }
    public void recordLaneCombatKill() { laneCombatKills++; }
    public void recordGenericSkirmishCall(int timeSeconds) { if (timeSeconds <= CHECKPOINT_SECONDS) genericSkirmishCalls++; }
    public void recordGenericSkirmishKill(int timeSeconds) { if (timeSeconds <= CHECKPOINT_SECONDS) genericSkirmishKills++; }

    public void recordRoamEvaluation() { }
    public void recordRoamCandidateEvaluation(com.lolfm.domain.Position position, TeamSide side) { }
    public void recordRoamTriggerRoll() { }
    public void recordRoamMultipleTriggers() { }
    public void recordRoamUnselectedTriggers(int count) { }
    public void recordRoamAttempt(com.lolfm.domain.Position position, TeamSide side) { }
    public void recordRoamKill(TeamSide side) { }

    public CombatExecutionStatsSnapshot snapshot() {
        return new CombatExecutionStatsSnapshot(
                jungleGankEvaluations, jungleGankAllTriggersFailed,
                jungleGankNoEligibleSides, jungleGankTriggerRolls,
                jungleGankTriggerSuccesses, jungleGankUnselectedTriggerSuccesses,
                jungleGankFallthroughs, jungleGankAttempts, counterGankAttempts,
                laneCombatResolverCalls, laneCombatTriggeredLanes, laneCombatAttempts,
                laneCombatKills, genericSkirmishCalls, genericSkirmishKills);
    }
}
