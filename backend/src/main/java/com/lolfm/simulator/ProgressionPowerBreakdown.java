package com.lolfm.simulator;
public record ProgressionPowerBreakdown(int level,ItemProgressStage itemStage,double levelPower,double itemPower,double championMultiplier,double championSpikeBonus,double rawTotalPower,double clampedTotalPower,ProgressionCombatContext context){}
