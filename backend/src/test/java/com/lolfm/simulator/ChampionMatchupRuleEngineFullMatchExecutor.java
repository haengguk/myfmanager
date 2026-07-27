package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupCatalog;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.GeneratedChampionMatchupCatalogFactory;
import com.lolfm.domain.PlayerSnapshot;
import java.util.Map;

final class ChampionMatchupRuleEngineFullMatchExecutor {
    private static final ChampionCatalog CHAMPIONS =
            new ChampionCatalog(new ObjectMapper());
    private static final ChampionMatchupCatalog GENERATED =
            GeneratedChampionMatchupCatalogFactory.prototype(CHAMPIONS).catalog();
    private static final ChampionSelectionValidator SELECTOR =
            new ChampionSelectionValidator(CHAMPIONS);
    private static final Map<String, String> PAIRS = Map.of(
            "TOP", "renekton/jax",
            "JUNGLE", "lee-sin/viego",
            "MID", "leblanc/viktor",
            "ADC", "lucian/jinx",
            "SUPPORT", "nautilus/lulu");

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
        ChampionMatchupRuleEngineFullMatchRow offRow =
                run(fixture, direction, skillProfile, seed, ChampionMatchupMode.OFF);
        ChampionMatchupRuleEngineFullMatchRow onRow =
                run(fixture, direction, skillProfile, seed, ChampionMatchupMode.ON);
        ChampionMatchupRuleEngineFullMatchRow offReplay =
                run(fixture, direction, skillProfile, seed, ChampionMatchupMode.OFF);
        ChampionMatchupRuleEngineFullMatchRow onReplay =
                run(fixture, direction, skillProfile, seed, ChampionMatchupMode.ON);
        if (!offRow.equals(offReplay) || !onRow.equals(onReplay)) {
            throw new IllegalStateException("Same-mode same-seed replay mismatch: "
                    + fixture.id() + "/" + skillProfile + "/" + direction + "/" + seed);
        }
        boolean flip = offRow.winnerSide() != onRow.winnerSide();
        String flipDirection = flip
                ? offRow.winnerSide() + "_TO_" + onRow.winnerSide()
                : "UNCHANGED";
        ChampionMatchupRuleEnginePairedRow paired =
                new ChampionMatchupRuleEnginePairedRow(
                        fixture.id(), skillProfile, direction, seed,
                        offRow.winner(), onRow.winner(), flipDirection, flip,
                        onRow.durationSeconds() - offRow.durationSeconds(),
                        kdaScore(onRow.targetFavoredKda()) - kdaScore(offRow.targetFavoredKda()),
                        kdaScore(onRow.targetDisfavoredKda()) - kdaScore(offRow.targetDisfavoredKda()),
                        onRow.targetFavoredGold() - offRow.targetFavoredGold(),
                        onRow.targetDisfavoredGold() - offRow.targetDisfavoredGold(),
                        onRow.targetFavoredLevel() - offRow.targetFavoredLevel(),
                        onRow.targetDisfavoredLevel() - offRow.targetDisfavoredLevel(),
                        onRow.matchupPairApplications(), onRow.finalMatchupEdgeMean(),
                        offRow.randomDrawCount() != onRow.randomDrawCount(),
                        onRow.randomDrawCount() - offRow.randomDrawCount(),
                        offRow.randomDrawCount() != onRow.randomDrawCount()
                                && offRow.directRandomCalls() == 0
                                && onRow.directRandomCalls() == 0,
                        !flip && offRow.randomDrawCount() != onRow.randomDrawCount());
        return new PairResult(offRow, onRow, paired);
    }

    private ChampionMatchupRuleEngineFullMatchRow run(
            SideOrientationFixture fixture,
            SideOrientationFixture.Orientation direction,
            String skillProfile,
            int seed,
            ChampionMatchupMode mode
    ) {
        try {
            SideOrientationFixture.OrientedFixture oriented = fixture.orient(direction);
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
            TeamSide favoredSide =
                    oriented.blueLogicalTeam() == SideOrientationFixture.LogicalTeamId.TEAM_A
                            ? TeamSide.BLUE : TeamSide.RED;
            TeamSide disfavoredSide =
                    favoredSide == TeamSide.BLUE ? TeamSide.RED : TeamSide.BLUE;
            var finalSnapshot = result.timeline().getSnapshots().getLast();
            PlayerSnapshot favored = finalSnapshot.getPlayerSnapshots().stream()
                    .filter(value -> value.getTeamSide() == favoredSide
                            && value.getPosition().name().equals(fixture.id()))
                    .findFirst().orElseThrow();
            PlayerSnapshot disfavored = finalSnapshot.getPlayerSnapshots().stream()
                    .filter(value -> value.getTeamSide() == disfavoredSide
                            && value.getPosition().name().equals(fixture.id()))
                    .findFirst().orElseThrow();
            var stats = result.championMatchupExecutionStats();
            double denominator = Math.max(1, stats.enabledEvaluations());
            double dilutionDenominator = Math.max(1, stats.dilutionSamples());
            boolean diagnosticsMismatch = stats.missingAssignmentErrors() != 0
                    || stats.deadParticipantErrors() != 0
                    || stats.nonParticipantErrors() != 0
                    || stats.sameTeamPairErrors() != 0
                    || stats.crossPositionErrors() != 0
                    || stats.duplicateApplicationErrors() != 0
                    || stats.staleStateErrors() != 0;
            return new ChampionMatchupRuleEngineFullMatchRow(
                    fixture.id(), PAIRS.get(fixture.id()), favored.getPosition(),
                    skillProfile, mode, direction, seed, result.timeline().getWinner(),
                    result.timeline().getDurationSeconds(), favoredSide, disfavoredSide,
                    kda(favored), kda(disfavored), favored.getGold(), disfavored.getGold(),
                    favored.getLevel(), disfavored.getLevel(), stats.evaluations(),
                    stats.totalPairApplications(), stats.nonZeroContributionApplications(),
                    stats.generatedBaseEdgeSum() / denominator,
                    stats.generatedBaseEdgeSum() / denominator,
                    stats.generatedBaseEdgeSum() / denominator,
                    stats.overrideAdjustmentSum() / denominator,
                    stats.finalMatchupEdgeSum() / denominator,
                    stats.eligiblePairCountTotal() / denominator,
                    stats.nonZeroPairCountTotal() / denominator,
                    stats.dilutionRatioSum() / dilutionDenominator,
                    stats.directRandomCalls(), false, diagnosticsMismatch,
                    result.winnerSide(), result.randomDrawCount(),
                    favored.getItemStage().name(), disfavored.getItemStage().name(),
                    result.championPowerExecutionStats().samples().size(),
                    stats.coverageRatioSum() / dilutionDenominator,
                    stats.netDirectionalRetentionSum() / dilutionDenominator,
                    stats.prototypeCoverageDilutionCount(),
                    stats.signCancellationCount(),
                    stats.unexpectedAggregationDilutionCount());
        } catch (Exception error) {
            throw new IllegalStateException("Prototype full-match failed: "
                    + fixture.id() + "/" + skillProfile + "/" + direction + "/" + seed,
                    error);
        }
    }

    private static MatchSimulator simulator(ChampionMatchupMode mode) {
        return new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(),
                new SnapshotFactory(CHAMPIONS), new ObjectiveResolver(),
                new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(),
                SimulationOptions.productionDefaults()
                        .withDiagnosticsEnabled(true)
                        .withChampionMatchupMode(mode),
                GENERATED);
    }

    private static String kda(PlayerSnapshot value) {
        return value.getKills() + "/" + value.getDeaths() + "/" + value.getAssists();
    }

    private static int kdaScore(String value) {
        String[] fields = value.split("/");
        return Integer.parseInt(fields[0]) + Integer.parseInt(fields[2])
                - Integer.parseInt(fields[1]);
    }

    record PairResult(
            ChampionMatchupRuleEngineFullMatchRow off,
            ChampionMatchupRuleEngineFullMatchRow on,
            ChampionMatchupRuleEnginePairedRow paired
    ) {
    }
}
