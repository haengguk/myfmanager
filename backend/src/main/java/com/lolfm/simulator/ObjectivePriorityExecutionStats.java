package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.ObjectivePriorityDecisionData;
import com.lolfm.domain.ObjectiveSelectionWeightBreakdown;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Match-scoped observational counters; no counter participates in gameplay decisions. */
public final class ObjectivePriorityExecutionStats {
    private long decayCalls, decayAppliedSeconds, decayReturnedDragonToZero, decayReturnedBaronToZero;
    private long impactAttempts, impactsApplied, duplicateImpactsRejected, noKillImpactsRejected, disabledImpactsIgnored;
    private long dragonClampCount, baronClampCount, zeroDragonMultiplierCount, zeroBaronMultiplierCount;
    private double dragonAppliedImpactTotal, baronAppliedImpactTotal;
    private long priorityAppliedToPostFightError, priorityAppliedToElderError, sameTickGeneralPostFightDuplicate;
    private long disabledBonusApplication, disabledMultiplierApplication, wrongSideSign, wrongLaneMultiplier, summaryKillDoubleImpact;
    private final EnumMap<CombatSource, Long> impactsBySource = new EnumMap<>(CombatSource.class);
    private final EnumMap<Lane, Long> impactsByLane = new EnumMap<>(Lane.class);
    private final EnumMap<TeamSide, Long> impactsBySide = new EnumMap<>(TeamSide.class);
    private final EnumMap<ObjectiveType, AttemptStats> attempts = new EnumMap<>(ObjectiveType.class);
    private final List<ObjectivePriorityDecisionData> decisions = new ArrayList<>();

    public ObjectivePriorityExecutionStats() {
        for (CombatSource source : CombatSource.values()) impactsBySource.put(source, 0L);
        for (Lane lane : Lane.values()) impactsByLane.put(lane, 0L);
        for (TeamSide side : TeamSide.values()) impactsBySide.put(side, 0L);
        for (ObjectiveType type : ObjectiveType.values()) attempts.put(type, new AttemptStats());
    }

    void recordDecay(int elapsed, double dragonBefore, double dragonAfter, double baronBefore, double baronAfter) {
        decayCalls++; decayAppliedSeconds += elapsed;
        if (dragonBefore != 0 && dragonAfter == 0) decayReturnedDragonToZero++;
        if (baronBefore != 0 && baronAfter == 0) decayReturnedBaronToZero++;
    }
    void recordImpactAttempt() { impactAttempts++; }
    void recordNoKill() { noKillImpactsRejected++; }
    void recordDisabledImpact() { disabledImpactsIgnored++; }
    void recordDuplicateImpact() { duplicateImpactsRejected++; }
    void recordImpact(CombatSource source, Lane lane, TeamSide side, double requestedDragon, double appliedDragon,
                      double requestedBaron, double appliedBaron, boolean dragonClamped, boolean baronClamped) {
        impactsApplied++;
        impactsBySource.merge(source, 1L, Long::sum);
        if (lane != null) impactsByLane.merge(lane, 1L, Long::sum);
        impactsBySide.merge(side, 1L, Long::sum);
        dragonAppliedImpactTotal += appliedDragon;
        baronAppliedImpactTotal += appliedBaron;
        if (requestedDragon == 0) zeroDragonMultiplierCount++;
        if (requestedBaron == 0) zeroBaronMultiplierCount++;
        if (dragonClamped) dragonClampCount++;
        if (baronClamped) baronClampCount++;
    }
    void recordDecision(ObjectivePriorityDecisionData data) {
        decisions.add(data);
        AttemptStats stats = attempts.get(data.objectiveType());
        stats.evaluations++;
        stats.baseChanceTotal += data.existingBaseAttemptChance();
        stats.priorityBonusTotal += data.priorityAttemptBonus();
        stats.finalChanceTotal += data.finalAttemptChance();
        if (!data.blueEligible() && !data.redEligible()) { stats.aliveIneligible++; stats.neitherEligible++; }
        else if (data.blueEligible() && data.redEligible()) stats.bothEligible++;
        else if (data.blueEligible()) stats.blueOnlyEligible++;
        else stats.redOnlyEligible++;
        if (data.attemptRollExecuted()) {
            stats.attemptRolls++;
            if (data.attemptRollSucceeded()) stats.attemptSuccesses++; else stats.attemptFailures++;
        }
        if (data.sideSelectionRollExecuted()) stats.weightedSelectionRolls++;
        if (data.selectedSide() == TeamSide.BLUE) stats.blueSelected++;
        if (data.selectedSide() == TeamSide.RED) stats.redSelected++;
        if (data.attemptRollSucceeded()) {
            addWeights(stats, data.blueExistingWeight(), data.redExistingWeight());
            stats.blueMultiplierTotal += data.bluePriorityMultiplier();
            stats.redMultiplierTotal += data.redPriorityMultiplier();
            stats.finalBlueWeightTotal += data.finalBlueSelectionWeight();
            stats.finalRedWeightTotal += data.finalRedSelectionWeight();
        }
        if (!data.priorityEnabled() && data.priorityAttemptBonus() != 0) disabledBonusApplication++;
        if (!data.priorityEnabled() && (data.bluePriorityMultiplier() != 1 || data.redPriorityMultiplier() != 1)) disabledMultiplierApplication++;
        if (data.postFightLinked() && data.priorityApplied()) priorityAppliedToPostFightError++;
        if (data.objectiveType() == ObjectiveType.ELDER && data.priorityApplied()) priorityAppliedToElderError++;
    }
    private void addWeights(AttemptStats stats, ObjectiveSelectionWeightBreakdown blue, ObjectiveSelectionWeightBreakdown red) {
        stats.existingBlueWeightTotal += blue.totalExistingWeight();
        stats.existingRedWeightTotal += red.totalExistingWeight();
        stats.aliveContributionTotal += blue.aliveContribution() + red.aliveContribution();
        stats.goldContributionTotal += blue.goldContribution() + red.goldContribution();
        stats.killContributionTotal += blue.killContribution() + red.killContribution();
        stats.recentBigWinContributionTotal += blue.recentBigWinContribution() + red.recentBigWinContribution();
        stats.recentAceContributionTotal += blue.recentAceContribution() + red.recentAceContribution();
    }

    public ObjectivePriorityExecutionStatsSnapshot snapshot() {
        EnumMap<ObjectiveType, ObjectivePriorityExecutionStatsSnapshot.ObjectiveAttemptStatsSnapshot> attemptSnapshots = new EnumMap<>(ObjectiveType.class);
        attempts.forEach((type, s) -> attemptSnapshots.put(type, s.snapshot()));
        return new ObjectivePriorityExecutionStatsSnapshot(decayCalls, decayAppliedSeconds, decayReturnedDragonToZero,
                decayReturnedBaronToZero, impactAttempts, impactsApplied, duplicateImpactsRejected,
                noKillImpactsRejected, disabledImpactsIgnored, dragonClampCount, baronClampCount,
                Map.copyOf(impactsBySource), Map.copyOf(impactsByLane), Map.copyOf(impactsBySide),
                dragonAppliedImpactTotal, baronAppliedImpactTotal, zeroDragonMultiplierCount, zeroBaronMultiplierCount,
                Map.copyOf(attemptSnapshots), priorityAppliedToPostFightError, priorityAppliedToElderError,
                sameTickGeneralPostFightDuplicate, disabledBonusApplication, disabledMultiplierApplication,
                wrongSideSign, wrongLaneMultiplier, summaryKillDoubleImpact, List.copyOf(decisions));
    }

    private static final class AttemptStats {
        long evaluations, stateIneligible, timingIneligible, aliveIneligible, attemptRolls, attemptSuccesses, attemptFailures;
        long blueOnlyEligible, redOnlyEligible, bothEligible, neitherEligible, weightedSelectionRolls, blueSelected, redSelected;
        double baseChanceTotal, priorityBonusTotal, finalChanceTotal, existingBlueWeightTotal, existingRedWeightTotal;
        double aliveContributionTotal, goldContributionTotal, killContributionTotal, recentBigWinContributionTotal, recentAceContributionTotal;
        double blueMultiplierTotal, redMultiplierTotal, finalBlueWeightTotal, finalRedWeightTotal;
        ObjectivePriorityExecutionStatsSnapshot.ObjectiveAttemptStatsSnapshot snapshot() {
            return new ObjectivePriorityExecutionStatsSnapshot.ObjectiveAttemptStatsSnapshot(evaluations, stateIneligible,
                    timingIneligible, aliveIneligible, baseChanceTotal, priorityBonusTotal, finalChanceTotal,
                    attemptRolls, attemptSuccesses, attemptFailures, blueOnlyEligible, redOnlyEligible, bothEligible,
                    neitherEligible, weightedSelectionRolls, blueSelected, redSelected, existingBlueWeightTotal,
                    existingRedWeightTotal, aliveContributionTotal, goldContributionTotal, killContributionTotal,
                    recentBigWinContributionTotal, recentAceContributionTotal, blueMultiplierTotal, redMultiplierTotal,
                    finalBlueWeightTotal, finalRedWeightTotal);
        }
    }
}
