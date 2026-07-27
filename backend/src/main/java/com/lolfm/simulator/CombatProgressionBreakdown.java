package com.lolfm.simulator;

import com.lolfm.champion.ChampionCombatPowerBreakdown;
import com.lolfm.champion.ChampionMatchupResult;

public record CombatProgressionBreakdown(
        ProgressionCombatContext context,
        int ownParticipantCount,
        int enemyParticipantCount,
        double ownAveragePower,
        double enemyAveragePower,
        double progressionEdge,
        double contextMultiplier,
        double commonProgressionContribution,
        double championContribution,
        double championMatchupContribution,
        double scoreBeforeMatchup,
        double finalContribution,
        ChampionCombatPowerBreakdown championBreakdown,
        ChampionMatchupResult championMatchupBreakdown
) {
}
