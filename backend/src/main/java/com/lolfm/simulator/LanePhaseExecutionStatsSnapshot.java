package com.lolfm.simulator;

import com.lolfm.domain.OuterTurretSiegeData;
import java.util.List;
import java.util.Map;

public record LanePhaseExecutionStatsSnapshot(
        boolean enabled,
        int evaluationTicks,
        Map<Lane, Integer> laneEvaluations,
        int lanePhaseIneligible,
        int pressureBelowThreshold,
        int attackerDead,
        int attackerActivityIneligible,
        int targetAlreadyDestroyed,
        int featureDisabled,
        int actualSieges,
        int siegeRandomRolls,
        List<OuterTurretSiegeData> sieges,
        Map<TeamSide, Integer> outerDestroyedByOwnerSide,
        Map<Lane, Integer> outerDestroyedByLane,
        Map<StructureActionSource, Integer> outerDestroyedBySource,
        Map<Lane, Integer> laneOpenCounts,
        int timeLimitTransitions,
        int allLanesOpenTransitions,
        int duplicateLaneTransitions,
        int duplicateMatchTransitions,
        int positivePressureDecays,
        int negativePressureDecays,
        int pressureNearNeutral,
        int laneCombatExcluded,
        int jungleGankExcluded,
        int roamOriginExcluded,
        int roamTargetExcluded
) {
    public LanePhaseExecutionStatsSnapshot {
        laneEvaluations = Map.copyOf(laneEvaluations);
        sieges = List.copyOf(sieges);
        outerDestroyedByOwnerSide = Map.copyOf(outerDestroyedByOwnerSide);
        outerDestroyedByLane = Map.copyOf(outerDestroyedByLane);
        outerDestroyedBySource = Map.copyOf(outerDestroyedBySource);
        laneOpenCounts = Map.copyOf(laneOpenCounts);
    }
}
