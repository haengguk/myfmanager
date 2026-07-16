package com.lolfm.simulator;
import java.util.Map;
public record ObjectiveDecisionExecutionStatsSnapshot(boolean enabled,int evaluations,int duplicateRejected,
 int initiativeRolls,int responderRolls,int resets,int uncontestedCaptures,int contestedFights,
 int tradeAttempts,int tradeSuccesses,int tradeFailures,Map<ObjectiveDecisionAction,Integer> actions) { }
