package com.lolfm.simulator;

import java.util.Optional;

/** Match-scoped duplicate clock and latest outcome for one team's jungle economy. */
public final class JungleEconomyState {
    private int lastResolvedAtSeconds = -1;
    private int duplicateResolutionCount;
    private JungleEconomyOutcome latestOutcome;

    public boolean shouldResolveAt(int timeSeconds) {
        if (timeSeconds < lastResolvedAtSeconds) {
            throw new IllegalArgumentException("Jungle economy time cannot move backwards");
        }
        if (timeSeconds == lastResolvedAtSeconds) {
            duplicateResolutionCount++;
            return false;
        }
        return true;
    }

    public void markResolvedAt(int timeSeconds, JungleEconomyOutcome outcome) {
        if (timeSeconds <= lastResolvedAtSeconds) {
            throw new IllegalStateException("Jungle economy time was not advanced");
        }
        lastResolvedAtSeconds = timeSeconds;
        if (outcome != null) latestOutcome = outcome;
    }

    public int getLastResolvedAtSeconds() { return lastResolvedAtSeconds; }
    public int getDuplicateResolutionCount() { return duplicateResolutionCount; }
    public Optional<JungleEconomyOutcome> latestOutcome() {
        return Optional.ofNullable(latestOutcome);
    }
}
