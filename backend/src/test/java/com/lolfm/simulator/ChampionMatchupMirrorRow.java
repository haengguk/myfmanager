package com.lolfm.simulator;

import com.lolfm.domain.Position;

record ChampionMatchupMirrorRow(
        String pairId,
        Position position,
        ProgressionCombatContext context,
        double originalBlueEdge,
        double mirroredBlueEdge,
        double originalLogicalTeamAEdge,
        double mirroredLogicalTeamAEdge,
        double neutralOriginalEdge,
        double neutralMirroredEdge,
        int applicationCount,
        int directRandomCalls,
        boolean logicalIdentityPreserved,
        boolean sideEdgeReversed,
        boolean exactZeroStable,
        String result
) {
}
