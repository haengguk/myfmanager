package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.domain.Position;

record ChampionMatchupFullMatchRow(
        String lineupId,
        Position targetPosition,
        String skillProfile,
        ChampionMatchupMode matchupMode,
        SideOrientationFixture.Orientation direction,
        int seed,
        String winner,
        TeamSide winnerSide,
        int duration,
        String timelineHash,
        String snapshotHash,
        long randomDrawCount,
        int matchupApplications,
        int nonZeroMatchupApplications,
        String endReason,
        boolean mismatch
) {
}
