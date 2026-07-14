package com.lolfm.domain;

import com.lolfm.simulator.Lane;
import com.lolfm.simulator.RoamOutcome;
import com.lolfm.simulator.TeamSide;
import java.util.List;

/** Structured result for one actual roam attempt; it never represents trigger-only evaluation. */
public record RoamData(
        TeamSide roamingSide, String roamerPlayerId, Position roamerPosition, Lane originLane, Lane targetLane,
        RoamOutcome outcome, TeamSide winningSide, String killerPlayerId, String victimPlayerId,
        List<String> assistantPlayerIds, double originPressureBefore, double originPressureAfter,
        double targetPressureBefore, double targetPressureAfter, double originPriority,
        double targetEnemyOverextension, int activityUntilSeconds, int roamFarmBlockedUntilSeconds,
        double attemptChance, double targetWeight, double combatEdge, double decisiveChance, double roamSuccessChance,
        boolean repeatTarget, boolean repeatPenaltyApplied,
        int roamerMechanics, int roamerAggression, int roamerFarming, int roamerTeamfighting,
        double attackerMechanics, double defenderMechanics, double attackerAggression, double defenderAggression,
        double attackerTeamfighting, double defenderTeamfighting, double mechanicsEdge, double aggressionEdge,
        double teamfightingEdge, double goldEdge, double vulnerabilityEdge, double numbersEdge
) {
    public RoamData { assistantPlayerIds = assistantPlayerIds == null ? List.of() : List.copyOf(assistantPlayerIds); }
}
