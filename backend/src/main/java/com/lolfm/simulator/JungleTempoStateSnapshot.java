package com.lolfm.simulator;

/** Immutable end-of-match view of one side's Jungle Tempo state. */
public record JungleTempoStateSnapshot(
        double creditSeconds,
        int lastEconomyOutcomeAtSeconds,
        int lastActualActionAtSeconds,
        int actualActionCount,
        int continuityResetCount
) {
}
