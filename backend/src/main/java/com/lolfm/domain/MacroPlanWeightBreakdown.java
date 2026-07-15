package com.lolfm.domain;

import com.lolfm.simulator.TeamMacroPlan;

public record MacroPlanWeightBreakdown(
        TeamMacroPlan plan,
        boolean eligible,
        double baseWeight,
        double goldEdge,
        double goldContribution,
        double attributeEdge,
        double attributeContribution,
        double objectivePriorityEdge,
        double objectiveContribution,
        double soulPointBonus,
        double resetBehindContribution,
        double resetMissingPlayerContribution,
        double repeatMultiplier,
        double finalWeight,
        String ineligibleReason
) { }
