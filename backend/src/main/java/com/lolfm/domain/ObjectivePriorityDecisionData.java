package com.lolfm.domain;

import com.lolfm.simulator.ObjectiveType;
import com.lolfm.simulator.TeamSide;

public record ObjectivePriorityDecisionData(
        ObjectiveType objectiveType,
        int evaluationTimeSeconds,
        boolean priorityEnabled,
        boolean generalAttempt,
        boolean postFightLinked,
        boolean priorityApplied,
        double lanePressureScore,
        double recentControl,
        double signedPriority,
        double bluePriority,
        double redPriority,
        double existingBaseAttemptChance,
        double priorityAttemptBonus,
        double finalAttemptChance,
        boolean attemptRollExecuted,
        boolean attemptRollSucceeded,
        boolean blueEligible,
        boolean redEligible,
        ObjectiveSelectionWeightBreakdown blueExistingWeight,
        ObjectiveSelectionWeightBreakdown redExistingWeight,
        double bluePriorityMultiplier,
        double redPriorityMultiplier,
        double finalBlueSelectionWeight,
        double finalRedSelectionWeight,
        boolean sideSelectionRollExecuted,
        TeamSide selectedSide,
        double macroSetupControl
) {
    public ObjectivePriorityDecisionData(ObjectiveType objectiveType, int evaluationTimeSeconds,
                                        boolean priorityEnabled, boolean generalAttempt, boolean postFightLinked,
                                        boolean priorityApplied, double lanePressureScore, double recentControl,
                                        double signedPriority, double bluePriority, double redPriority,
                                        double existingBaseAttemptChance, double priorityAttemptBonus,
                                        double finalAttemptChance, boolean attemptRollExecuted,
                                        boolean attemptRollSucceeded, boolean blueEligible, boolean redEligible,
                                        ObjectiveSelectionWeightBreakdown blueExistingWeight,
                                        ObjectiveSelectionWeightBreakdown redExistingWeight,
                                        double bluePriorityMultiplier, double redPriorityMultiplier,
                                        double finalBlueSelectionWeight, double finalRedSelectionWeight,
                                        boolean sideSelectionRollExecuted, TeamSide selectedSide) {
        this(objectiveType, evaluationTimeSeconds, priorityEnabled, generalAttempt, postFightLinked,
                priorityApplied, lanePressureScore, recentControl, signedPriority, bluePriority, redPriority,
                existingBaseAttemptChance, priorityAttemptBonus, finalAttemptChance, attemptRollExecuted,
                attemptRollSucceeded, blueEligible, redEligible, blueExistingWeight, redExistingWeight,
                bluePriorityMultiplier, redPriorityMultiplier, finalBlueSelectionWeight, finalRedSelectionWeight,
                sideSelectionRollExecuted, selectedSide, 0);
    }
}
