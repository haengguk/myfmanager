package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupCatalog;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.ThirtyChampionGeneratedCatalog;
import com.lolfm.domain.PlayerSnapshot;
import java.util.Map;

final class ThirtyChampionFullMatchExecutor {
    private static final ChampionCatalog CHAMPIONS =
            new ChampionCatalog(new ObjectMapper());
    private static final ChampionMatchupCatalog GENERATED =
            ThirtyChampionGeneratedCatalog.build(CHAMPIONS).catalog();
    private static final ChampionSelectionValidator SELECTOR =
            new ChampionSelectionValidator(CHAMPIONS);
    private final ThreadLocal<MatchSimulator> off =
            ThreadLocal.withInitial(() -> simulator(ChampionMatchupMode.OFF));
    private final ThreadLocal<MatchSimulator> on =
            ThreadLocal.withInitial(() -> simulator(ChampionMatchupMode.ON));

    PairResult runPair(GeneratedMatchupRoundRobinLineupFactory.Lineup lineup,
                       SideOrientationFixture.Orientation orientation, int seed) {
        FullRow offRow = run(lineup, orientation, seed, ChampionMatchupMode.OFF);
        FullRow onRow = run(lineup, orientation, seed, ChampionMatchupMode.ON);
        boolean flip = offRow.winnerSide() != onRow.winnerSide();
        PairedRow paired = new PairedRow(lineup.lineupId(), lineup.scheduleIndex(),
                lineup.skillProfile(), orientation, seed,
                offRow.winnerSide(), onRow.winnerSide(),
                flip ? offRow.winnerSide() + "_TO_" + onRow.winnerSide()
                        : "UNCHANGED", flip,
                onRow.durationSeconds() - offRow.durationSeconds(),
                onRow.blueKills() - offRow.blueKills(),
                onRow.redKills() - offRow.redKills(),
                onRow.blueGold() - offRow.blueGold(),
                onRow.redGold() - offRow.redGold(),
                offRow.randomDrawCount() != onRow.randomDrawCount(),
                offRow.randomDrawCount() != onRow.randomDrawCount()
                        && offRow.engineDirectRandomCalls() == 0
                        && onRow.engineDirectRandomCalls() == 0,
                offRow.replayMismatch() || onRow.replayMismatch(),
                offRow.diagnosticsMismatch() || onRow.diagnosticsMismatch());
        return new PairResult(offRow, onRow, paired);
    }

    FullRow replay(GeneratedMatchupRoundRobinLineupFactory.Lineup lineup,
                   SideOrientationFixture.Orientation orientation, int seed,
                   ChampionMatchupMode mode) {
        return run(lineup, orientation, seed, mode);
    }

    private FullRow run(GeneratedMatchupRoundRobinLineupFactory.Lineup lineup,
                        SideOrientationFixture.Orientation orientation, int seed,
                        ChampionMatchupMode mode) {
        try {
            var oriented = lineup.fixture().orient(orientation);
            var assignments = SELECTOR.resolve(oriented.champions());
            SideOrientationRandomTraceObserver random =
                    new SideOrientationRandomTraceObserver(seed, orientation.name(),
                            oriented.blueLogicalTeam().name(),
                            oriented.redLogicalTeam().name(), false);
            MatchSimulator.SimulationResult result =
                    (mode == ChampionMatchupMode.OFF ? off : on).get()
                            .simulateWithSideDiagnostics(
                                    oriented.blue(), oriented.red(), assignments, random);
            var snapshot = result.timeline().getSnapshots().getLast();
            Map<TeamSide, Aggregate> totals = snapshot.getPlayerSnapshots().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            PlayerSnapshot::getTeamSide,
                            java.util.stream.Collectors.collectingAndThen(
                                    java.util.stream.Collectors.toList(),
                                    ThirtyChampionFullMatchExecutor::aggregate)));
            var stats = result.championMatchupExecutionStats();
            double evaluations = Math.max(1, stats.enabledEvaluations());
            double dilution = Math.max(1, stats.dilutionSamples());
            boolean mismatch = stats.missingAssignmentErrors() != 0
                    || stats.deadParticipantErrors() != 0
                    || stats.nonParticipantErrors() != 0
                    || stats.sameTeamPairErrors() != 0
                    || stats.crossPositionErrors() != 0
                    || stats.duplicateApplicationErrors() != 0
                    || stats.staleStateErrors() != 0;
            return new FullRow(lineup.lineupId(), lineup.scheduleIndex(),
                    lineup.skillProfile(), mode, orientation, seed,
                    result.timeline().getWinner(),
                    result.winnerSide(),
                    oriented.logicalWinner(result.winnerSide()),
                    result.timeline().getDurationSeconds(),
                    totals.get(TeamSide.BLUE).kills(),
                    totals.get(TeamSide.RED).kills(),
                    totals.get(TeamSide.BLUE).gold(),
                    totals.get(TeamSide.RED).gold(),
                    stats.evaluations(), stats.totalPairApplications(),
                    stats.nonZeroContributionApplications(),
                    stats.generatedBaseEdgeSum() / evaluations,
                    stats.coverageRatioSum() / dilution,
                    stats.netDirectionalRetentionSum() / dilution,
                    stats.signCancellationCount(),
                    result.championPowerExecutionStats().samples().size(),
                    stats.directRandomCalls(), result.randomDrawCount(),
                    false, mismatch);
        } catch (Exception error) {
            throw new IllegalStateException("Thirty-profile full match failed: "
                    + lineup.lineupId() + "/" + lineup.skillProfile() + "/"
                    + orientation + "/" + seed, error);
        }
    }

    private static Aggregate aggregate(java.util.List<PlayerSnapshot> players) {
        return new Aggregate(players.stream().mapToInt(PlayerSnapshot::getKills).sum(),
                players.stream().mapToInt(PlayerSnapshot::getGold).sum());
    }

    private static MatchSimulator simulator(ChampionMatchupMode mode) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(),
                new SnapshotFactory(CHAMPIONS), new ObjectiveResolver(),
                new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(),
                SimulationOptions.productionDefaults().withDiagnosticsEnabled(true)
                        .withChampionMatchupMode(mode), GENERATED);
    }

    private record Aggregate(int kills, int gold) { }
    record PairResult(FullRow off, FullRow on, PairedRow paired) { }
    record FullRow(String lineupId, int scheduleIndex, String skillProfile,
            ChampionMatchupMode matchupMode,
            SideOrientationFixture.Orientation orientation, int seed,
            String winner, TeamSide winnerSide,
            SideOrientationFixture.LogicalTeamId winnerLogicalTeam,
            int durationSeconds, int blueKills, int redKills,
            int blueGold, int redGold, int matchupEvaluations,
            int eligiblePairApplications, int nonZeroPairApplications,
            double generatedMatchupEdgeMean, double coverageRatioMean,
            double netDirectionalRetentionMean, int signCancellationCount,
            int championPowerApplications, int engineDirectRandomCalls,
            long randomDrawCount, boolean replayMismatch,
            boolean diagnosticsMismatch) { }
    record PairedRow(String lineupId, int scheduleIndex, String skillProfile,
            SideOrientationFixture.Orientation orientation, int seed,
            TeamSide offWinner, TeamSide onWinner, String flipDirection,
            boolean winnerFlip, int durationDelta, int blueKillDelta,
            int redKillDelta, int blueGoldDelta, int redGoldDelta,
            boolean randomDrawDifference,
            boolean downstreamBranchDivergence,
            boolean replayMismatch, boolean diagnosticsMismatch) { }
}
