package com.lolfm.simulator;

import java.util.EnumMap;
import java.util.Map;

/** Observational counters only; Jungle Tempo gameplay never reads this object. */
public final class JungleTempoExecutionStats {
    private int economyUpdates;
    private int continuityResets;
    private double totalCreditAddedSeconds;
    private final EnumMap<JungleTempoReadinessStatus, Integer> gankReadinessByStatus =
            zeroed(JungleTempoReadinessStatus.class);
    private final EnumMap<JungleTempoReadinessStatus, Integer> counterGankReadinessByStatus =
            zeroed(JungleTempoReadinessStatus.class);
    private final EnumMap<JungleTempoActionType, Integer> actualConsumptions =
            zeroed(JungleTempoActionType.class);
    private final EnumMap<TeamSide, JungleTempoState.Readiness> latestGankReadinessBySide =
            new EnumMap<>(TeamSide.class);
    private final EnumMap<TeamSide, JungleTempoState.Readiness>
            latestCounterGankReadinessBySide = new EnumMap<>(TeamSide.class);

    void recordEconomyUpdate(JungleTempoState.CreditUpdate update) {
        economyUpdates++;
        totalCreditAddedSeconds += update.addedCreditSeconds();
        if (update.continuityReset()) continuityResets++;
    }

    void recordGankReadiness(TeamSide side, JungleTempoState.Readiness readiness) {
        gankReadinessByStatus.merge(readiness.status(), 1, Integer::sum);
        latestGankReadinessBySide.put(side, readiness);
    }

    void recordCounterGankReadiness(TeamSide side, JungleTempoState.Readiness readiness) {
        counterGankReadinessByStatus.merge(readiness.status(), 1, Integer::sum);
        latestCounterGankReadinessBySide.put(side, readiness);
    }

    void recordActualConsumption(JungleTempoActionType actionType) {
        actualConsumptions.merge(actionType, 1, Integer::sum);
    }

    public JungleTempoExecutionStatsSnapshot snapshot(
            Map<TeamSide, JungleTempoState> states
    ) {
        EnumMap<TeamSide, JungleTempoStateSnapshot> stateSnapshots =
                new EnumMap<>(TeamSide.class);
        states.forEach((side, state) -> stateSnapshots.put(side, state.snapshot()));
        return new JungleTempoExecutionStatsSnapshot(
                economyUpdates, continuityResets, totalCreditAddedSeconds,
                gankReadinessByStatus, counterGankReadinessByStatus,
                actualConsumptions, latestGankReadinessBySide,
                latestCounterGankReadinessBySide, stateSnapshots);
    }

    private static <E extends Enum<E>> EnumMap<E, Integer> zeroed(Class<E> type) {
        EnumMap<E, Integer> result = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) result.put(value, 0);
        return result;
    }
}
