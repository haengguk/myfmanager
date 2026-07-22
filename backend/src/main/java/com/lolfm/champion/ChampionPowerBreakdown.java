package com.lolfm.champion;

import com.lolfm.simulator.ItemProgressStage;
import com.lolfm.simulator.ProgressionCombatContext;

public record ChampionPowerBreakdown(ChampionId championId, String profileVersion, int level,
        ItemProgressStage itemStage, ProgressionCombatContext context, double levelModifier,
        double itemModifier, double contextModifier, double rawPlayerChampionPower,
        double clampedPlayerChampionPower, boolean playerClampApplied) { }
