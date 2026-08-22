package com.lolfm.simulator;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record CombatExecutionStatsSnapshot(
        int jungleGankEvaluations,
        int jungleGankAllTriggersFailed,
        int jungleGankNoEligibleSides,
        int jungleGankTriggerRolls,
        int jungleGankTriggerSuccesses,
        int jungleGankUnselectedTriggerSuccesses,
        int jungleGankFallthroughs,
        int jungleGankAttempts,
        int counterGankAttempts,
        Map<JungleGankIneligibility, Integer> jungleGankEligibilityByReason,
        Map<TeamSide, JungleGankIneligibility> latestJungleGankEligibilityBySide,
        Map<CounterGankIneligibility, Integer> counterGankEligibilityByReason,
        Map<TeamSide, CounterGankIneligibility>
                latestCounterGankEligibilityByDefendingSide,
        int laneCombatResolverCalls,
        int laneCombatTriggeredLanes,
        int laneCombatAttempts,
        int laneCombatKills,
        int genericSkirmishCalls,
        int genericSkirmishKills
) {
    public CombatExecutionStatsSnapshot {
        jungleGankEligibilityByReason = immutableEnumMap(
                JungleGankIneligibility.class, jungleGankEligibilityByReason);
        latestJungleGankEligibilityBySide = immutableEnumMap(
                TeamSide.class, latestJungleGankEligibilityBySide);
        counterGankEligibilityByReason = immutableEnumMap(
                CounterGankIneligibility.class, counterGankEligibilityByReason);
        latestCounterGankEligibilityByDefendingSide = immutableEnumMap(
                TeamSide.class, latestCounterGankEligibilityByDefendingSide);
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
