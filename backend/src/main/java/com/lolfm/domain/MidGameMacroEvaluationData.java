package com.lolfm.domain;

import com.lolfm.simulator.TeamMacroPlan;

/** Immutable structured audit record for one scheduled macro evaluation. */
public record MidGameMacroEvaluationData(
        int dueAtSeconds,
        int actualEvaluationAtSeconds,
        MidGameMacroDecisionData blueDecision,
        MidGameMacroDecisionData redDecision,
        TeamMacroPlan bluePreviousPlan,
        TeamMacroPlan redPreviousPlan,
        com.lolfm.simulator.MacroPlanEndReason bluePreviousPlanEndReason,
        com.lolfm.simulator.MacroPlanEndReason redPreviousPlanEndReason,
        int blueNextEvaluationAtSeconds,
        int redNextEvaluationAtSeconds,
        String evaluationSkippedReason,
        int selectionRandomConsumptionCount
) { }
