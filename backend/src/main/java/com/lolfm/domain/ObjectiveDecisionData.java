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
 int nextGeneralAttemptAtSeconds, boolean postFightPath, boolean elderPriorityAvailable) {
 public ObjectiveDecisionData {
  initiativeCandidates=initiativeCandidates==null?List.of():List.copyOf(initiativeCandidates);
  responderCandidates=responderCandidates==null?List.of():List.copyOf(responderCandidates);
 }
}
