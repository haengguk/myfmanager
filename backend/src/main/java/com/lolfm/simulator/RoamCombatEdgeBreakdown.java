package com.lolfm.simulator;

/** Structured, immutable decomposition of one roam combat edge calculation. */
public record RoamCombatEdgeBreakdown(
        double attackerMechanics,
        double defenderMechanics,
        double attackerAggression,
        double defenderAggression,
        double attackerTeamfighting,
        double defenderTeamfighting,
        double mechanicsEdge,
        double aggressionEdge,
        double teamfightingEdge,
        double goldEdge,
        double vulnerabilityEdge,
        double numbersEdge,
        double combatEdge
) { }
