package com.lolfm.simulator;
import java.util.Map;
public record ObjectiveDecisionExecutionStatsSnapshot(boolean enabled,int evaluations,int duplicateRejected,
 int initiativeRolls,int responderRolls,int resets,int uncontestedCaptures,int contestedFights,
 int tradeAttempts,int tradeSuccesses,int tradeFailures,Map<ObjectiveDecisionAction,Integer> actions,
 int secureRolls,int objectiveSteals) {
 public ObjectiveDecisionExecutionStatsSnapshot(boolean enabled,int evaluations,int duplicateRejected,
  int initiativeRolls,int responderRolls,int resets,int uncontestedCaptures,int contestedFights,
  int tradeAttempts,int tradeSuccesses,int tradeFailures,Map<ObjectiveDecisionAction,Integer> actions) {
  this(enabled,evaluations,duplicateRejected,initiativeRolls,responderRolls,resets,
   uncontestedCaptures,contestedFights,tradeAttempts,tradeSuccesses,tradeFailures,
   actions,0,0);
 }
}
