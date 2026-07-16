package com.lolfm.domain;
public record ObjectiveDecisionSnapshot(boolean enabled, ObjectiveDecisionData latestOverall,
 ObjectiveDecisionData latestDragon, ObjectiveDecisionData latestBaron, ObjectiveDecisionData latestElder,
 ObjectiveDecisionData latestBlue, ObjectiveDecisionData latestRed) {
 public static ObjectiveDecisionSnapshot disabled(){return new ObjectiveDecisionSnapshot(false,null,null,null,null,null,null);}
}
