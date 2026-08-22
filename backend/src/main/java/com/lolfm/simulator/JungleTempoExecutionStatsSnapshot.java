package com.lolfm.simulator;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, canonical diagnostics for Jungle Tempo evaluation and consumption. */
public record JungleTempoExecutionStatsSnapshot(
        int economyUpdates,
        int continuityResets,
        double totalCreditAddedSeconds,
        Map<JungleTempoReadinessStatus, Integer> gankReadinessByStatus,
        Map<JungleTempoReadinessStatus, Integer> counterGankReadinessByStatus,
        Map<JungleTempoActionType, Integer> actualConsumptions,
        Map<TeamSide, JungleTempoState.Readiness> latestGankReadinessBySide,
        Map<TeamSide, JungleTempoState.Readiness> latestCounterGankReadinessBySide,
        Map<TeamSide, JungleTempoStateSnapshot> stateBySide
) {
    public JungleTempoExecutionStatsSnapshot {
        gankReadinessByStatus = immutableEnumMap(
                JungleTempoReadinessStatus.class, gankReadinessByStatus);
        counterGankReadinessByStatus = immutableEnumMap(
                JungleTempoReadinessStatus.class, counterGankReadinessByStatus);
        actualConsumptions = immutableEnumMap(
                JungleTempoActionType.class, actualConsumptions);
        latestGankReadinessBySide = immutableEnumMap(
                TeamSide.class, latestGankReadinessBySide);
        latestCounterGankReadinessBySide = immutableEnumMap(
                TeamSide.class, latestCounterGankReadinessBySide);
        stateBySide = immutableEnumMap(TeamSide.class, stateBySide);
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
