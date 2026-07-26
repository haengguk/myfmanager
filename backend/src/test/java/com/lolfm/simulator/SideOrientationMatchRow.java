package com.lolfm.simulator;

import java.util.List;
import java.util.Map;

record SideOrientationMatchRow(
        String fixtureId,
        String auditGroup,
        String mode,
        String skillProfile,
        SideOrientationFixture.Orientation orientation,
        int seed,
        boolean logicalTeamABlue,
        TeamSide winnerSide,
        SideOrientationFixture.LogicalTeamId winnerLogicalTeam,
        int durationSeconds,
        int blueKills,
        int redKills,
        int blueGold,
        int redGold,
        int blueObjectives,
        int redObjectives,
        int blueStructures,
        int redStructures,
        int combatOutcomeCount,
        long randomDrawCount,
        int blueChampionPowerApplications,
        int redChampionPowerApplications,
        int replayMismatch,
        int diagnosticsMismatch,
        GameEndReason endReason,
        Map<SideOrientationResolver, Map<TeamSide, SideOrientationExecutionStats.Snapshot>> funnel,
        List<TieRow> ties,
        List<ArbitrationRow> arbitrations,
        List<SideOrientationRandomTraceObserver.Draw> trace
) {
    String csv() {
        return String.join(",",
                fixtureId, auditGroup, mode, skillProfile, orientation.toString(),
                Integer.toString(seed), Boolean.toString(logicalTeamABlue), winnerSide.toString(),
                winnerLogicalTeam.toString(), Integer.toString(durationSeconds),
                Integer.toString(blueKills), Integer.toString(redKills),
                Integer.toString(blueGold), Integer.toString(redGold),
                Integer.toString(blueObjectives), Integer.toString(redObjectives),
                Integer.toString(blueStructures), Integer.toString(redStructures),
                Integer.toString(combatOutcomeCount), Long.toString(randomDrawCount),
                Integer.toString(replayMismatch), Integer.toString(diagnosticsMismatch));
    }

    record ArbitrationRow(
            int tick,
            SideOrientationResolver resolver,
            String fixture,
            int seed,
            boolean bothEvaluated,
            boolean bothEligible,
            boolean bothTriggered,
            boolean blueFirst,
            boolean redFirst,
            boolean blueAttempted,
            boolean redAttempted,
            TeamSide sharedSlotWinner,
            boolean secondSideBlocked,
            String blockReason,
            TeamSide actualOutcomeSide
    ) {
    }

    record TieRow(
            SideOrientationResolver resolver,
            String fixture,
            int seed,
            int tick,
            double blueScore,
            double redScore,
            double difference,
            String tieType,
            String resolutionSource,
            TeamSide winnerSide
    ) {
    }
}
