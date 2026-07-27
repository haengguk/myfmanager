package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.domain.Position;

record ChampionMatchupApplicationRow(
        String pairId,
        Position position,
        ProgressionCombatContext context,
        String direction,
        String participantMode,
        ChampionMatchupMode featureMode,
        String sourceChampion,
        String opponentChampion,
        TeamSide sourceSide,
        TeamSide opponentSide,
        boolean sourceAlive,
        boolean opponentAlive,
        boolean sourceParticipant,
        boolean opponentParticipant,
        int eligiblePairCount,
        double expectedEdge,
        double actualEdge,
        int applicationCount,
        String skipReason,
        int directRandomCalls,
        boolean mutationDetected,
        String result
) {
}
