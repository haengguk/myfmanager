package com.lolfm.simulator;

record ChampionMatchupPairedRow(
        String lineupId,
        String skillProfile,
        SideOrientationFixture.Orientation direction,
        int seed,
        String offWinner,
        String onWinner,
        boolean winnerMismatch,
        boolean durationMismatch,
        boolean timelineMismatch,
        boolean snapshotMismatch,
        boolean randomDrawMismatch,
        int offApplications,
        int onApplications,
        int onNonZeroApplications
) {
    boolean anyMismatch() {
        return winnerMismatch || durationMismatch || timelineMismatch
                || snapshotMismatch || randomDrawMismatch;
    }
}
