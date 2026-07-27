package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.domain.Position;
import java.security.MessageDigest;
import java.util.HexFormat;

final class ChampionMatchupFullMatchExecutor {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ChampionCatalog CHAMPIONS = new ChampionCatalog(MAPPER);
    private static final ChampionSelectionValidator SELECTOR =
            new ChampionSelectionValidator(CHAMPIONS);
    private final ThreadLocal<MatchSimulator> off =
            ThreadLocal.withInitial(() -> simulator(ChampionMatchupMode.OFF));
    private final ThreadLocal<MatchSimulator> on =
            ThreadLocal.withInitial(() -> simulator(ChampionMatchupMode.ON));

    PairResult runPair(
            SideOrientationFixture fixture,
            SideOrientationFixture.Orientation direction,
            String skillProfile,
            int seed
    ) {
        ChampionMatchupFullMatchRow offRow =
                run(fixture, direction, skillProfile, seed, ChampionMatchupMode.OFF);
        ChampionMatchupFullMatchRow onRow =
                run(fixture, direction, skillProfile, seed, ChampionMatchupMode.ON);
        ChampionMatchupPairedRow paired = new ChampionMatchupPairedRow(
                fixture.id(), skillProfile, direction, seed,
                offRow.winner(), onRow.winner(),
                !offRow.winner().equals(onRow.winner())
                        || offRow.winnerSide() != onRow.winnerSide(),
                offRow.duration() != onRow.duration(),
                !offRow.timelineHash().equals(onRow.timelineHash()),
                !offRow.snapshotHash().equals(onRow.snapshotHash()),
                offRow.randomDrawCount() != onRow.randomDrawCount(),
                offRow.matchupApplications(), onRow.matchupApplications(),
                onRow.nonZeroMatchupApplications());
        boolean integrity = paired.anyMismatch()
                || offRow.matchupApplications() != 0
                || onRow.nonZeroMatchupApplications() != 0;
        return new PairResult(withMismatch(offRow, integrity),
                withMismatch(onRow, integrity), paired);
    }

    private ChampionMatchupFullMatchRow run(
            SideOrientationFixture fixture,
            SideOrientationFixture.Orientation direction,
            String skillProfile,
            int seed,
            ChampionMatchupMode mode
    ) {
        try {
            var oriented = fixture.orient(direction);
            var assignments = SELECTOR.resolve(oriented.champions());
            SideOrientationRandomTraceObserver random =
                    new SideOrientationRandomTraceObserver(
                            seed, direction.toString(),
                            oriented.blueLogicalTeam().toString(),
                            oriented.redLogicalTeam().toString(), false);
            MatchSimulator.SimulationResult result =
                    (mode == ChampionMatchupMode.OFF ? off : on).get()
                            .simulateWithSideDiagnostics(
                                    oriented.blue(), oriented.red(), assignments, random);
            var stats = result.championMatchupExecutionStats();
            return new ChampionMatchupFullMatchRow(
                    fixture.id(), Position.valueOf(fixture.id()), skillProfile,
                    mode, direction, seed, result.timeline().getWinner(),
                    result.winnerSide(), result.timeline().getDurationSeconds(),
                    hash(result.timeline().getEvents()),
                    hash(result.timeline().getSnapshots()), result.randomDrawCount(),
                    stats.totalPairApplications(), stats.nonZeroContributionApplications(),
                    result.endReason().toString(), false);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "Matchup full-match audit failed for " + fixture.id()
                            + "/" + direction + "/" + seed, error);
        }
    }

    private static ChampionMatchupFullMatchRow withMismatch(
            ChampionMatchupFullMatchRow row,
            boolean mismatch
    ) {
        return new ChampionMatchupFullMatchRow(
                row.lineupId(), row.targetPosition(), row.skillProfile(),
                row.matchupMode(), row.direction(), row.seed(), row.winner(),
                row.winnerSide(), row.duration(), row.timelineHash(), row.snapshotHash(),
                row.randomDrawCount(), row.matchupApplications(),
                row.nonZeroMatchupApplications(), row.endReason(), mismatch);
    }

    private static String hash(Object value) throws Exception {
        byte[] bytes = MAPPER.writeValueAsBytes(value);
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static MatchSimulator simulator(ChampionMatchupMode mode) {
        return new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(),
                new SnapshotFactory(CHAMPIONS), new ObjectiveResolver(),
                new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(),
                SimulationOptions.productionDefaults()
                        .withDiagnosticsEnabled(true)
                        .withChampionMatchupMode(mode));
    }

    record PairResult(
            ChampionMatchupFullMatchRow off,
            ChampionMatchupFullMatchRow on,
            ChampionMatchupPairedRow paired
    ) {
    }
}
