package com.lolfm.simulator;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable structured diagnostics for the unified jungle economy path. */
public record JungleEconomyExecutionStatsSnapshot(
        int evaluations,
        int eligibleOutcomes,
        int duplicateCalls,
        Map<JungleEconomySkipReason, Integer> skippedByReason,
        int awardedCs,
        int awardedGold,
        int awardedExperience,
        Map<TeamSide, JungleEconomyOutcome> latestOutcomeBySide
) {
    public JungleEconomyExecutionStatsSnapshot {
        skippedByReason = immutableEnumMap(
                JungleEconomySkipReason.class, skippedByReason);
        latestOutcomeBySide = immutableEnumMap(TeamSide.class, latestOutcomeBySide);
    }

    private static <K extends Enum<K>, V> Map<K, V> immutableEnumMap(
            Class<K> keyType,
            Map<K, V> source
    ) {
        EnumMap<K, V> copy = new EnumMap<>(keyType);
        Objects.requireNonNull(source, "source").forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "map key"),
                Objects.requireNonNull(value, "map value")));
        return Collections.unmodifiableMap(copy);
    }
}
