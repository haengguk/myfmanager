package com.lolfm.simulator;

public record CombatExecutionStatsSnapshot(
        int jungleGankEvaluations,
        int jungleGankAllTriggersFailed,
        int jungleGankAttempts,
        int counterGankAttempts,
        int laneCombatResolverCalls,
        int laneCombatTriggeredLanes,
        int laneCombatAttempts,
        int laneCombatKills,
        int genericSkirmishCalls,
        int genericSkirmishKills
) { }
