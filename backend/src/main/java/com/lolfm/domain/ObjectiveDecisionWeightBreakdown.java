package com.lolfm.domain;
import com.lolfm.simulator.*;
public record ObjectiveDecisionWeightBreakdown(ObjectiveDecisionAction action, ObjectiveDecisionRole role,
 boolean eligible, ObjectiveDecisionIneligibleReason reason, double baseWeight,
 double priorityEdge, double priorityContribution, double aliveEdge, double aliveContribution,
 double goldEdge, double goldContribution, double teamfightEdge, double teamfightContribution,
 double farmingEdge, double farmingContribution, double urgencyContribution,
 double missingPlayerContribution, double tradeAvailabilityContribution, double finalWeight,
 double objectiveDecisionScore, double objectiveFavorability,
 double objectiveDecisionContribution) {
 public ObjectiveDecisionWeightBreakdown(ObjectiveDecisionAction action, ObjectiveDecisionRole role,
  boolean eligible, ObjectiveDecisionIneligibleReason reason, double baseWeight,
  double priorityEdge, double priorityContribution, double aliveEdge, double aliveContribution,
  double goldEdge, double goldContribution, double teamfightEdge, double teamfightContribution,
  double farmingEdge, double farmingContribution, double urgencyContribution,
  double missingPlayerContribution, double tradeAvailabilityContribution, double finalWeight) {
  this(action, role, eligible, reason, baseWeight, priorityEdge, priorityContribution,
   aliveEdge, aliveContribution, goldEdge, goldContribution, teamfightEdge,
   teamfightContribution, farmingEdge, farmingContribution, urgencyContribution,
   missingPlayerContribution, tradeAvailabilityContribution, finalWeight,
   com.lolfm.simulator.PlayerImpactRuleConfig.BASELINE_ATTRIBUTE, 0, 0);
 }
}
