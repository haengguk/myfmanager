package com.lolfm.domain;

import com.lolfm.simulator.Lane;
import com.lolfm.simulator.MacroActionResult;
import com.lolfm.simulator.MacroPlanEndReason;
import com.lolfm.simulator.MacroPlanStatus;
import com.lolfm.simulator.ObjectiveType;
import com.lolfm.simulator.StructureKind;
import com.lolfm.simulator.TeamMacroPlan;
import com.lolfm.simulator.TowerTier;
import java.util.Set;

public record TeamMacroSnapshot(
        TeamMacroPlan currentPlan,
        Lane targetLane,
        ObjectiveType targetObjective,
        int startedAtSeconds,
        int activeUntilSeconds,
        int nextEvaluationAtSeconds,
        Set<Position> assignedPositions,
        MacroActionResult lastActionResult,
        StructureKind lastDestroyedStructure,
        TowerTier lastDestroyedTowerTier,
        Lane lastStructureLane,
        TeamMacroPlan lastSelectedPlan,
        MacroPlanStatus status,
        MacroPlanEndReason endReason,
        int lastEvaluationDueAtSeconds,
        int lastEvaluationAtSeconds,
        String lastEvaluationSkippedReason,
        int lastSelectionRandomConsumptionCount
) {
    public TeamMacroSnapshot {
        assignedPositions = DeterministicEnumSet.copyOfNullable(
                Position.class, assignedPositions);
    }

    public TeamMacroSnapshot(
            TeamMacroPlan currentPlan, Lane targetLane, ObjectiveType targetObjective,
            int startedAtSeconds, int activeUntilSeconds, int nextEvaluationAtSeconds,
            Set<Position> assignedPositions, MacroActionResult lastActionResult,
            StructureKind lastDestroyedStructure, TowerTier lastDestroyedTowerTier,
            Lane lastStructureLane
    ) {
        this(currentPlan, targetLane, targetObjective, startedAtSeconds, activeUntilSeconds,
                nextEvaluationAtSeconds, assignedPositions, lastActionResult,
                lastDestroyedStructure, lastDestroyedTowerTier, lastStructureLane,
                currentPlan, MacroPlanStatus.NOT_STARTED, null, -1, -1, null, 0);
    }
}
