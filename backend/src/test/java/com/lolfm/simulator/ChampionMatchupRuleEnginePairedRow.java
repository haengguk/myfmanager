package com.lolfm.simulator;

record ChampionMatchupRuleEnginePairedRow(
        String lineupId,
        String skillProfile,
        SideOrientationFixture.Orientation direction,
        int seed,
        String offWinner,
        String onWinner,
        String winnerFlipDirection,
        boolean winnerFlip,
        int durationDeltaSeconds,
        int favoredKdaDelta,
        int disfavoredKdaDelta,
        int favoredGoldDelta,
        int disfavoredGoldDelta,
        int favoredLevelDelta,
        int disfavoredLevelDelta,
        int matchupApplicationCount,
        double finalMatchupEdgeMean,
        boolean randomDrawMismatch,
        long pairedRandomDrawCountDifference,
        boolean downstreamBranchDivergence,
        boolean sameWinnerTimelineDivergence
) {
}
