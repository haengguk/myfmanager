package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Map;

public record DraftDecision(
        int turn,
        TeamSide side,
        DraftActionType actionType,
        ChampionId selectedChampionId,
        double immediateScore,
        double continuationScore,
        double finalSearchScore,
        Map<String, Double> componentBreakdown,
        DraftPlanArchetype preferredPlan,
        double preferredPlanViability,
        List<DraftAlternative> topAlternatives
) {
    public DraftDecision {
        componentBreakdown = Map.copyOf(componentBreakdown);
        topAlternatives = List.copyOf(topAlternatives);
    }
}
