package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.ObjectivePriorityDecisionData;
import java.util.List;
import java.util.Map;

public record ObjectivePriorityExecutionStatsSnapshot(
        long decayCalls,
        long decayAppliedSeconds,
        long decayReturnedDragonToZero,
        long decayReturnedBaronToZero,
        long impactAttempts,
        long impactsApplied,
        long duplicateImpactsRejected,
        long noKillImpactsRejected,
        long disabledImpactsIgnored,
        long dragonClampCount,
        long baronClampCount,
        Map<CombatSource, Long> impactsBySource,
        Map<Lane, Long> impactsByLane,
        Map<TeamSide, Long> impactsBySide,
        double dragonAppliedImpactTotal,
        double baronAppliedImpactTotal,
        long zeroDragonMultiplierCount,
        long zeroBaronMultiplierCount,
        Map<ObjectiveType, ObjectiveAttemptStatsSnapshot> attempts,
        long priorityAppliedToPostFightError,
        long priorityAppliedToElderError,
        long sameTickGeneralPostFightDuplicate,
        long disabledBonusApplication,
        long disabledMultiplierApplication,
        long wrongSideSign,
        long wrongLaneMultiplier,
        long summaryKillDoubleImpact,
        List<ObjectivePriorityDecisionData> decisions
) {
    public record ObjectiveAttemptStatsSnapshot(
            long evaluations, long stateIneligible, long timingIneligible, long aliveIneligible,
            double baseChanceTotal, double priorityBonusTotal, double finalChanceTotal,
            long attemptRolls, long attemptSuccesses, long attemptFailures,
            long blueOnlyEligible, long redOnlyEligible, long bothEligible, long neitherEligible,
            long weightedSelectionRolls, long blueSelected, long redSelected,
            double existingBlueWeightTotal, double existingRedWeightTotal,
            double aliveContributionTotal, double goldContributionTotal, double killContributionTotal,
            double recentBigWinContributionTotal, double recentAceContributionTotal,
            double blueMultiplierTotal, double redMultiplierTotal,
            double finalBlueWeightTotal, double finalRedWeightTotal
    ) { }
}
