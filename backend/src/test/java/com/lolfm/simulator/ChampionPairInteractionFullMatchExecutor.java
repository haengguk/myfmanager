package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupCatalog;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.PairInteractionGeneratedCatalog;
import com.lolfm.champion.ThirtyChampionGeneratedCatalog;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.PlayerSnapshot;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

final class ChampionPairInteractionFullMatchExecutor {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ChampionCatalog CHAMPIONS = new ChampionCatalog(MAPPER);
    private static final ChampionMatchupCatalog LEGACY =
            ThirtyChampionGeneratedCatalog.build(CHAMPIONS).catalog();
    private static final ChampionMatchupCatalog INTERACTION =
            PairInteractionGeneratedCatalog.build(CHAMPIONS).catalog();
    private static final ChampionSelectionValidator SELECTOR =
            new ChampionSelectionValidator(CHAMPIONS);
    private final ThreadLocal<MatchSimulator> off =
            ThreadLocal.withInitial(() -> simulator(FormulaMode.MATCHUP_OFF));
    private final ThreadLocal<MatchSimulator> legacy =
            ThreadLocal.withInitial(() -> simulator(
                    FormulaMode.LEGACY_SEPARABLE_CANDIDATE));
    private final ThreadLocal<MatchSimulator> interaction =
            ThreadLocal.withInitial(() -> simulator(
                    FormulaMode.PAIR_INTERACTION_CANDIDATE));

    TripleResult run(GeneratedMatchupRoundRobinLineupFactory.Lineup lineup,
                     SideOrientationFixture.Orientation orientation, int seed) {
        FullRow offRow = run(lineup, orientation, seed, FormulaMode.MATCHUP_OFF);
        FullRow legacyRow = run(lineup, orientation, seed,
                FormulaMode.LEGACY_SEPARABLE_CANDIDATE);
        FullRow interactionRow = run(lineup, orientation, seed,
                FormulaMode.PAIR_INTERACTION_CANDIDATE);
        return new TripleResult(offRow, legacyRow, interactionRow,
                compare(offRow, legacyRow, ComparisonType.OFF_VS_LEGACY),
                compare(offRow, interactionRow, ComparisonType.OFF_VS_INTERACTION),
                compare(legacyRow, interactionRow,
                        ComparisonType.LEGACY_VS_INTERACTION));
    }

    FullRow replay(GeneratedMatchupRoundRobinLineupFactory.Lineup lineup,
                   SideOrientationFixture.Orientation orientation, int seed,
                   FormulaMode mode) {
        return run(lineup, orientation, seed, mode);
    }

    private FullRow run(GeneratedMatchupRoundRobinLineupFactory.Lineup lineup,
                        SideOrientationFixture.Orientation orientation,
                        int seed, FormulaMode mode) {
        try {
            var oriented = lineup.fixture().orient(orientation);
            var assignments = SELECTOR.resolve(oriented.champions());
            var random = new SideOrientationRandomTraceObserver(seed,
                    orientation.name(), oriented.blueLogicalTeam().name(),
                    oriented.redLogicalTeam().name(), false);
            MatchSimulator selected = switch (mode) {
                case MATCHUP_OFF -> off.get();
                case LEGACY_SEPARABLE_CANDIDATE -> legacy.get();
                case PAIR_INTERACTION_CANDIDATE -> interaction.get();
            };
            MatchSimulator.SimulationResult result = selected
                    .simulateWithSideDiagnostics(oriented.blue(), oriented.red(),
                            assignments, random);
            MatchSnapshot snapshot = result.timeline().getSnapshots().getLast();
            var stats = result.championMatchupExecutionStats();
            List<Double> applications = stats.applicationEdges().stream()
                    .map(Math::abs).toList();
            var quantiles = applications.isEmpty()
                    ? ThirtyChampionStatistics.summarize(List.of(0.0))
                    : ThirtyChampionStatistics.summarize(applications);
            int attempts = result.combatOutcomeExecutionStats().wins().values()
                    .stream().flatMap(value -> value.values().stream())
                    .mapToInt(Integer::intValue).sum();
            boolean diagnosticsMismatch = stats.missingAssignmentErrors() != 0
                    || stats.deadParticipantErrors() != 0
                    || stats.nonParticipantErrors() != 0
                    || stats.sameTeamPairErrors() != 0
                    || stats.crossPositionErrors() != 0
                    || stats.duplicateApplicationErrors() != 0
                    || stats.staleStateErrors() != 0;
            return new FullRow(lineup.lineupId(), lineup.scheduleIndex(),
                    lineup.skillProfile(), mode, orientation, seed,
                    result.winnerSide(), oriented.logicalWinner(result.winnerSide()),
                    result.timeline().getDurationSeconds(),
                    snapshot.getBlueKills(), snapshot.getRedKills(),
                    snapshot.getBlueGold(), snapshot.getRedGold(),
                    meanLevel(snapshot, TeamSide.BLUE),
                    meanLevel(snapshot, TeamSide.RED),
                    snapshot.getBlueDragons(), snapshot.getRedDragons(),
                    snapshot.getBlueTowersDestroyed(),
                    snapshot.getRedTowersDestroyed(),
                    result.endReason().name(), attempts, stats.evaluations(),
                    stats.totalPairApplications(),
                    applications.stream().mapToDouble(Double::doubleValue)
                            .average().orElse(0),
                    quantiles.p50(), quantiles.p90(), quantiles.p95(),
                    quantiles.max(), 0,
                    stats.dilutionSamples() == 0 ? 0
                            : stats.coverageRatioSum() / stats.dilutionSamples(),
                    stats.dilutionSamples() == 0 ? 0
                            : stats.netDirectionalRetentionSum()
                            / stats.dilutionSamples(),
                    stats.signCancellationCount(), 0,
                    result.championPowerExecutionStats().samples().size(),
                    stats.directRandomCalls(), result.randomDrawCount(),
                    hash(result.timeline().getEvents()),
                    hash(result.timeline().getSnapshots()), false,
                    diagnosticsMismatch);
        } catch (Exception error) {
            throw new IllegalStateException("Pair interaction full match failed: "
                    + lineup.lineupId() + "/" + lineup.skillProfile() + "/"
                    + orientation + "/" + seed + "/" + mode, error);
        }
    }

    private static PairedRow compare(FullRow before, FullRow after,
                                     ComparisonType type) {
        boolean flip = before.winnerSide() != after.winnerSide();
        return new PairedRow(before.lineupId(), before.scheduleIndex(),
                before.skillProfile(), before.orientation(), before.seed(), type,
                before.winnerSide(), after.winnerSide(),
                flip ? before.winnerSide() + "_TO_" + after.winnerSide()
                        : "UNCHANGED", flip,
                after.durationSeconds() - before.durationSeconds(),
                after.blueKills() - before.blueKills(),
                after.redKills() - before.redKills(),
                after.blueGold() - before.blueGold(),
                after.redGold() - before.redGold(),
                after.blueObjectives() - before.blueObjectives(),
                after.redObjectives() - before.redObjectives(),
                after.blueStructures() - before.blueStructures(),
                after.redStructures() - before.redStructures(),
                before.randomDrawCount() != after.randomDrawCount(),
                before.randomDrawCount() != after.randomDrawCount()
                        && before.engineDirectRandomCalls() == 0
                        && after.engineDirectRandomCalls() == 0,
                before.replayMismatch() || after.replayMismatch(),
                before.diagnosticsMismatch() || after.diagnosticsMismatch());
    }

    private static MatchSimulator simulator(FormulaMode mode) {
        ChampionMatchupCatalog catalog = mode
                == FormulaMode.PAIR_INTERACTION_CANDIDATE
                ? INTERACTION : LEGACY;
        ChampionMatchupMode enabled = mode == FormulaMode.MATCHUP_OFF
                ? ChampionMatchupMode.OFF : ChampionMatchupMode.ON;
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(),
                new SnapshotFactory(CHAMPIONS), new ObjectiveResolver(),
                new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(),
                SimulationOptions.productionDefaults().withDiagnosticsEnabled(true)
                        .withChampionMatchupMode(enabled), catalog);
    }

    private static double meanLevel(MatchSnapshot snapshot, TeamSide side) {
        return snapshot.getPlayerSnapshots().stream().filter(player ->
                player.getTeamSide() == side).mapToInt(PlayerSnapshot::getLevel)
                .average().orElse(0);
    }
    private static String hash(Object value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(MAPPER.writeValueAsBytes(value)));
    }

    enum FormulaMode {
        MATCHUP_OFF, LEGACY_SEPARABLE_CANDIDATE, PAIR_INTERACTION_CANDIDATE
    }
    enum ComparisonType {
        OFF_VS_LEGACY, OFF_VS_INTERACTION, LEGACY_VS_INTERACTION
    }
    record TripleResult(FullRow off, FullRow legacy, FullRow interaction,
            PairedRow offVsLegacy, PairedRow offVsInteraction,
            PairedRow legacyVsInteraction) { }
    record FullRow(String lineupId, int scheduleIndex, String skillProfile,
            FormulaMode formulaMode,
            SideOrientationFixture.Orientation orientation, int seed,
            TeamSide winnerSide,
            SideOrientationFixture.LogicalTeamId winnerLogicalTeam,
            int durationSeconds, int blueKills, int redKills,
            int blueGold, int redGold, double blueMeanLevel,
            double redMeanLevel, int blueObjectives, int redObjectives,
            int blueStructures, int redStructures, String endReason,
            int actualCombatAttempts, int matchupEvaluations,
            int matchupApplications, double generatedEdgeMean,
            double actualGeneratedEdgeP50, double actualGeneratedEdgeP90,
            double actualGeneratedEdgeP95, double generatedEdgeMax,
            int counterfactualOutcomeFlipCount, double coverageRatioMean,
            double netDirectionalRetentionMean,
            int expectedSignCancellationCount,
            int meaningfulSignCancellationCount,
            int championPowerApplications, int engineDirectRandomCalls,
            long randomDrawCount, String timelineHash, String snapshotHash,
            boolean replayMismatch, boolean diagnosticsMismatch) { }
    record PairedRow(String lineupId, int scheduleIndex, String skillProfile,
            SideOrientationFixture.Orientation orientation, int seed,
            ComparisonType comparisonType, TeamSide beforeWinner,
            TeamSide afterWinner, String flipDirection, boolean winnerFlip,
            int durationDelta, int blueKillDelta, int redKillDelta,
            int blueGoldDelta, int redGoldDelta, int blueObjectiveDelta,
            int redObjectiveDelta, int blueStructureDelta,
            int redStructureDelta, boolean randomDrawDifference,
            boolean downstreamBranchDivergence, boolean replayMismatch,
            boolean diagnosticsMismatch) { }
}
