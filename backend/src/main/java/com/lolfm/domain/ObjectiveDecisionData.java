package com.lolfm.domain;
import com.lolfm.simulator.*;
import java.util.List;
public record ObjectiveDecisionData(int decisionSequence, int evaluationTimeSeconds, ObjectiveType objectiveType,
 boolean featureEnabled, TeamSide initiativeSide, TeamSide responderSide,
 List<ObjectiveDecisionWeightBreakdown> initiativeCandidates, ObjectiveDecisionAction initiativeAction,
 boolean initiativeSelectionRollExecuted, Double initiativeSelectionRoll,
 List<ObjectiveDecisionWeightBreakdown> responderCandidates, ObjectiveDecisionAction responderAction,
 boolean responderSelectionRollExecuted, Double responderSelectionRoll,
 Lane tradeTargetLane, TowerTier tradeTargetStructure, double tradePushChance,
 boolean tradeRollExecuted, boolean tradeSucceeded, boolean contestedFight,
 TeamSide fightWinner, TeamSide captureSide, ObjectiveDecisionResult result,
 boolean majorCombatConsumed, boolean structureActionConsumed,
 int nextGeneralAttemptAtSeconds, boolean postFightPath, boolean elderPriorityAvailable,
 ObjectiveFightSkillImpactData objectiveFightSkillImpact,
 ObjectiveSecureDecisionData objectiveSecureDecision) {
 public ObjectiveDecisionData {
  initiativeCandidates=initiativeCandidates==null?List.of():List.copyOf(initiativeCandidates);
  responderCandidates=responderCandidates==null?List.of():List.copyOf(responderCandidates);
  if(objectiveFightSkillImpact!=null&&!contestedFight){
   throw new IllegalArgumentException("objective fight skill impact requires a contested fight");
  }
  if(objectiveSecureDecision!=null){
   if(!contestedFight||postFightPath){
    throw new IllegalArgumentException("objective secure decision requires a general contested fight");
   }
   if(fightWinner!=objectiveSecureDecision.fightWinner()){
    throw new IllegalArgumentException("objective secure fight winner mismatch");
   }
   if(objectiveSecureDecision.captureSucceeded()==null){
    throw new IllegalArgumentException("recorded objective secure decision requires a capture result");
   }
   if(objectiveSecureDecision.captureSucceeded()){
    if(result!=com.lolfm.simulator.ObjectiveDecisionResult.CONTEST_FIGHT
     ||captureSide!=objectiveSecureDecision.selectedCaptureSide()){
     throw new IllegalArgumentException("successful objective secure capture mismatch");
    }
   }else if(result!=com.lolfm.simulator.ObjectiveDecisionResult.STALE_OBJECTIVE||captureSide!=null){
    throw new IllegalArgumentException("failed objective secure capture must be stale");
   }
  }
 }

 public ObjectiveDecisionData(int decisionSequence, int evaluationTimeSeconds, ObjectiveType objectiveType,
  boolean featureEnabled, TeamSide initiativeSide, TeamSide responderSide,
  List<ObjectiveDecisionWeightBreakdown> initiativeCandidates, ObjectiveDecisionAction initiativeAction,
  boolean initiativeSelectionRollExecuted, Double initiativeSelectionRoll,
  List<ObjectiveDecisionWeightBreakdown> responderCandidates, ObjectiveDecisionAction responderAction,
  boolean responderSelectionRollExecuted, Double responderSelectionRoll,
  Lane tradeTargetLane, TowerTier tradeTargetStructure, double tradePushChance,
  boolean tradeRollExecuted, boolean tradeSucceeded, boolean contestedFight,
  TeamSide fightWinner, TeamSide captureSide, ObjectiveDecisionResult result,
  boolean majorCombatConsumed, boolean structureActionConsumed,
  int nextGeneralAttemptAtSeconds, boolean postFightPath, boolean elderPriorityAvailable) {
  this(decisionSequence, evaluationTimeSeconds, objectiveType, featureEnabled,
   initiativeSide, responderSide, initiativeCandidates, initiativeAction,
   initiativeSelectionRollExecuted, initiativeSelectionRoll, responderCandidates,
   responderAction, responderSelectionRollExecuted, responderSelectionRoll,
   tradeTargetLane, tradeTargetStructure, tradePushChance, tradeRollExecuted,
   tradeSucceeded, contestedFight, fightWinner, captureSide, result,
   majorCombatConsumed, structureActionConsumed, nextGeneralAttemptAtSeconds,
   postFightPath, elderPriorityAvailable, null, null);
 }
}
