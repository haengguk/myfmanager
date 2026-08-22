package com.lolfm.simulator;

import java.util.Objects;

/** Match-scoped deterministic credit state for one side's jungle actions. */
public final class JungleTempoState {
    private static final double EPSILON = 1.0e-9;

    private double creditSeconds;
    private int lastEconomyOutcomeAtSeconds = -1;
    private int lastActualActionAtSeconds = -1;
    private int actualActionCount;
    private int continuityResetCount;

    public CreditUpdate recordEconomyOutcome(JungleEconomyOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        int timeSeconds = outcome.timeSeconds();
        if (timeSeconds <= lastEconomyOutcomeAtSeconds) {
            throw new IllegalArgumentException(
                    "Jungle tempo economy time must advance exactly once per outcome");
        }

        double before = creditSeconds;
        boolean continuityReset = lastEconomyOutcomeAtSeconds >= 0
                && timeSeconds - lastEconomyOutcomeAtSeconds
                > JungleTempoRuleConfig.CONTINUITY_GRACE_SECONDS;
        if (continuityReset) {
            creditSeconds = 0.0;
            continuityResetCount++;
        }
        double boundedEfficiency = clamp(
                outcome.combinedEfficiency(),
                JungleTempoRuleConfig.MIN_CREDIT_EFFICIENCY,
                JungleTempoRuleConfig.MAX_CREDIT_EFFICIENCY);
        double added = outcome.elapsedSeconds() * boundedEfficiency;
        creditSeconds = Math.min(
                JungleTempoRuleConfig.MAX_BANKED_CREDIT_SECONDS,
                creditSeconds + added);
        lastEconomyOutcomeAtSeconds = timeSeconds;
        return new CreditUpdate(
                timeSeconds, before, boundedEfficiency, added, creditSeconds,
                continuityReset);
    }

    public Readiness readinessAt(int timeSeconds) {
        if (timeSeconds < 0) {
            throw new IllegalArgumentException("Jungle tempo time must be non-negative");
        }
        if (lastEconomyOutcomeAtSeconds >= 0 && timeSeconds < lastEconomyOutcomeAtSeconds) {
            throw new IllegalArgumentException("Jungle tempo time cannot move backwards");
        }
        double required = actualActionCount == 0
                ? JungleTempoRuleConfig.FIRST_ACTION_READINESS_SECONDS
                : JungleTempoRuleConfig.REPEAT_ACTION_READINESS_SECONDS;
        JungleTempoReadinessStatus status;
        if (lastEconomyOutcomeAtSeconds != timeSeconds) {
            status = JungleTempoReadinessStatus.NO_CURRENT_ECONOMY_OUTCOME;
        } else if (creditSeconds + EPSILON < required) {
            status = JungleTempoReadinessStatus.INSUFFICIENT_CREDIT;
        } else {
            status = JungleTempoReadinessStatus.READY;
        }
        return new Readiness(timeSeconds, creditSeconds, required, status);
    }

    public Consumption consumeActualActionAt(int timeSeconds) {
        if (timeSeconds <= lastActualActionAtSeconds) {
            throw new IllegalStateException(
                    "Jungle tempo action time must advance exactly once per actual action");
        }
        Readiness readiness = readinessAt(timeSeconds);
        if (!readiness.ready()) {
            throw new IllegalStateException(
                    "Cannot consume Jungle Tempo while not ready: " + readiness.status());
        }
        double before = creditSeconds;
        creditSeconds = Math.max(
                0.0, creditSeconds - JungleTempoRuleConfig.ACTION_COST_SECONDS);
        lastActualActionAtSeconds = timeSeconds;
        actualActionCount++;
        return new Consumption(timeSeconds, before, creditSeconds, actualActionCount);
    }

    public JungleTempoStateSnapshot snapshot() {
        return new JungleTempoStateSnapshot(
                creditSeconds, lastEconomyOutcomeAtSeconds, lastActualActionAtSeconds,
                actualActionCount, continuityResetCount);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Readiness(
            int timeSeconds,
            double creditSeconds,
            double requiredCreditSeconds,
            JungleTempoReadinessStatus status
    ) {
        public Readiness {
            Objects.requireNonNull(status, "status");
        }

        public boolean ready() {
            return status == JungleTempoReadinessStatus.READY;
        }
    }

    public record CreditUpdate(
            int timeSeconds,
            double creditBeforeSeconds,
            double boundedEfficiency,
            double addedCreditSeconds,
            double creditAfterSeconds,
            boolean continuityReset
    ) {
    }

    public record Consumption(
            int timeSeconds,
            double creditBeforeSeconds,
            double creditAfterSeconds,
            int actualActionCount
    ) {
    }
}
