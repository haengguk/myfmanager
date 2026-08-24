package com.lolfm.simulator;
import java.util.EnumMap;
import java.util.Map;
import com.lolfm.domain.ObjectiveDecisionData;
public final class ObjectiveDecisionExecutionStats {
 private final boolean enabled; private int evaluations,duplicateRejected,initiativeRolls,responderRolls,resets,uncontested,contests,tradeAttempts,tradeSuccesses,tradeFailures,secureRolls,objectiveSteals;
 private final EnumMap<ObjectiveDecisionAction,Integer> actions=new EnumMap<>(ObjectiveDecisionAction.class);
 ObjectiveDecisionExecutionStats(boolean enabled){this.enabled=enabled;for(var a:ObjectiveDecisionAction.values())actions.put(a,0);}
 void duplicate(){if(enabled)duplicateRejected++;}
 void record(ObjectiveDecisionData d){if(!enabled)return;evaluations++;if(d.initiativeAction()!=null)actions.merge(d.initiativeAction(),1,Integer::sum);if(d.responderAction()!=null)actions.merge(d.responderAction(),1,Integer::sum);if(d.initiativeSelectionRollExecuted())initiativeRolls++;if(d.responderSelectionRollExecuted())responderRolls++;if(d.result()==ObjectiveDecisionResult.INITIATOR_RESET)resets++;if(d.result()==ObjectiveDecisionResult.UNCONTESTED_CAPTURE)uncontested++;if(d.result()==ObjectiveDecisionResult.CONTEST_FIGHT)contests++;if(d.result()==ObjectiveDecisionResult.TRADE_SUCCEEDED||d.result()==ObjectiveDecisionResult.TRADE_FAILED){tradeAttempts++;if(d.tradeSucceeded())tradeSuccesses++;else tradeFailures++;}if(d.objectiveSecureDecision()!=null&&d.objectiveSecureDecision().rollExecuted())secureRolls++;if(d.objectiveSecureDecision()!=null&&d.objectiveSecureDecision().actualSteal())objectiveSteals++;}
 public ObjectiveDecisionExecutionStatsSnapshot snapshot(){return new ObjectiveDecisionExecutionStatsSnapshot(enabled,evaluations,duplicateRejected,initiativeRolls,responderRolls,resets,uncontested,contests,tradeAttempts,tradeSuccesses,tradeFailures,Map.copyOf(actions),secureRolls,objectiveSteals);}
}
