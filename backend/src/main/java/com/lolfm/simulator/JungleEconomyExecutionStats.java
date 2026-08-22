package com.lolfm.simulator;

import java.util.EnumMap;

/** Observational counters only; gameplay decisions never read this object. */
public final class JungleEconomyExecutionStats {
    private int evaluations;
    private int eligibleOutcomes;
    private int duplicateCalls;
    private int awardedCs;
    private int awardedGold;
    private int awardedExperience;
    private final EnumMap<JungleEconomySkipReason, Integer> skippedByReason =
            new EnumMap<>(JungleEconomySkipReason.class);
    private final EnumMap<TeamSide, JungleEconomyOutcome> latestOutcomeBySide =
            new EnumMap<>(TeamSide.class);

    public JungleEconomyExecutionStats() {
        for (JungleEconomySkipReason reason : JungleEconomySkipReason.values()) {
            skippedByReason.put(reason, 0);
        }
    }

    void recordEvaluation() { evaluations++; }
    void recordDuplicate() { duplicateCalls++; }
    void recordSkipped(JungleEconomySkipReason reason) {
        skippedByReason.merge(reason, 1, Integer::sum);
    }
    void recordOutcome(JungleEconomyOutcome outcome) {
        eligibleOutcomes++;
        awardedCs += outcome.awardedCs();
        awardedGold += outcome.awardedGold();
        awardedExperience += outcome.awardedExperience();
        latestOutcomeBySide.put(outcome.side(), outcome);
    }

    public JungleEconomyExecutionStatsSnapshot snapshot() {
        return new JungleEconomyExecutionStatsSnapshot(
                evaluations, eligibleOutcomes, duplicateCalls, skippedByReason,
                awardedCs, awardedGold, awardedExperience, latestOutcomeBySide);
    }
}
