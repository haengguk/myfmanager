package com.lolfm.domain;

import com.lolfm.simulator.Lane;
import com.lolfm.simulator.ObjectiveType;
import com.lolfm.simulator.TeamMacroPlan;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Set;

public record MidGameMacroDecisionData(
        int evaluationTimeSeconds,
        TeamSide teamSide,
        boolean featureEnabled,
        List<MacroPlanWeightBreakdown> candidates,
        TeamMacroPlan selectedPlan,
        Lane targetLane,
        ObjectiveType targetObjective,
        Set<Position> assignedPositions,
        boolean selectionRollExecuted,
        Double selectionRoll,
        int startedAtSeconds,
        int activeUntilSeconds
) {
    public MidGameMacroDecisionData {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        assignedPositions = DeterministicEnumSet.copyOfNullable(
                Position.class, assignedPositions);
    }
}
