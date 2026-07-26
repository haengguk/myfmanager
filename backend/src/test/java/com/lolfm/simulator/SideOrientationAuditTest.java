package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SideOrientationAuditTest {
    @Test void mirrorSwapsTeamSideButPreservesLogicalIdentity() {
        var f = SideOrientationFixtureFactory.neutralFixtures().getFirst();
        var original = f.orient(SideOrientationFixture.Orientation.ORIGINAL);
        var mirrored = f.orient(SideOrientationFixture.Orientation.MIRRORED);
        assertThat(original.blueLogicalTeam()).isEqualTo(SideOrientationFixture.LogicalTeamId.TEAM_A);
        assertThat(mirrored.redLogicalTeam()).isEqualTo(SideOrientationFixture.LogicalTeamId.TEAM_A);
        assertThat(original.blue().getName()).isEqualTo(mirrored.red().getName());
    }

    @Test void doubleMirrorReturnsOriginalFixture() {
        var f = SideOrientationFixtureFactory.neutralFixtures().getFirst();
        var twice = f.mirror().mirror();
        assertThat(twice.teamA().name()).isEqualTo(f.teamA().name());
        assertThat(twice.teamB().name()).isEqualTo(f.teamB().name());
    }

    @Test void mirrorDoesNotShareMutableTeamOrPlayerState() {
        var f = SideOrientationFixtureFactory.neutralFixtures().getFirst();
        var a = f.orient(SideOrientationFixture.Orientation.ORIGINAL);
        var b = f.orient(SideOrientationFixture.Orientation.MIRRORED);
        assertThat(a.blue()).isNotSameAs(b.red());
        assertThat(a.blue().getPlayers().getFirst()).isNotSameAs(b.red().getPlayers().getFirst());
    }

    @Test void neutralChampionOffFixtureHasEqualPlayerPower() {
        var f = SideOrientationFixtureFactory.neutralFixtures().getFirst();
        for (int i = 0; i < 5; i++) {
            assertThat(f.teamA().attributes()[i]).isEqualTo(14);
            assertThat(f.teamB().attributes()[i]).isEqualTo(14);
        }
    }

    @Test void sideOrientationStatsAreMatchScoped() {
        var first = new SideOrientationExecutionStats();
        var second = new SideOrientationExecutionStats();
        first.counters(SideOrientationResolver.LANE_COMBAT, TeamSide.BLUE).attempt(true);
        assertThat(second.snapshot().get(SideOrientationResolver.LANE_COMBAT)
                .get(TeamSide.BLUE).actualAttempts()).isZero();
    }

    @Test void nextMatchStartsWithZeroSideStats() {
        assertThat(new SideOrientationExecutionStats().snapshot().values().stream()
                .flatMap(v -> v.values().stream()).mapToLong(SideOrientationExecutionStats.Snapshot::evaluations).sum()).isZero();
    }

    @Test void evaluationDoesNotCountAsAttempt() {
        var c = counters(); c.evaluation(true);
        assertThat(c.snapshot().actualAttempts()).isZero();
    }

    @Test void failedTriggerDoesNotConsumeMajorCombatSlot() {
        var c = counters(); c.evaluation(true);
        assertThat(c.snapshot().majorCombatSlotConsumed()).isZero();
    }

    @Test void actualAttemptConsumesMajorCombatSlotOnce() {
        var c = counters(); c.attempt(true);
        assertThat(c.snapshot().majorCombatSlotConsumed()).isOne();
    }

    @Test void secondSideIsNotBlockedByEvaluationOnly() {
        var stats = new SideOrientationExecutionStats();
        stats.counters(SideOrientationResolver.JUNGLE_GANK, TeamSide.BLUE).evaluation(false);
        assertThat(stats.snapshot().get(SideOrientationResolver.JUNGLE_GANK)
                .get(TeamSide.RED).blockedByMajorCombatSlot()).isZero();
    }

    @Test void resolverOutcomeIsRecordedOnce() {
        var c = counters(); c.outcome(true);
        assertThat(c.snapshot().actualOutcomes()).isOne();
        assertThat(c.snapshot().successfulOutcomes()).isOne();
    }

    @Test void randomObserverConsumesNoAdditionalDraws() {
        var plain = new java.util.Random(7);
        var observed = observer(7);
        assertThat(observed.nextDouble()).isEqualTo(plain.nextDouble());
        assertThat(observed.nextInt(10)).isEqualTo(plain.nextInt(10));
    }

    @Test void sameSeedRandomTraceIsExact() {
        var a = observer(42); var b = observer(42);
        a.nextDouble(); a.nextInt(9); b.nextDouble(); b.nextInt(9);
        assertThat(a.trace()).isEqualTo(b.trace());
    }

    @Test void resolverFunnelUsesCorrectDenominators() {
        var c = counters(); c.evaluation(true); c.trigger(); c.attempt(true); c.outcome(true);
        assertThat(c.snapshot().triggerSuccesses()).isEqualTo(c.snapshot().eligibleEvaluations());
    }

    @Test void zeroDenominatorIsNotApplicable() {
        var f = new SideOrientationFunnelAccumulator();
        assertThat(f.csv("G", "F", "M", "S", SideOrientationResolver.ROAM, TeamSide.BLUE))
                .contains("NOT_APPLICABLE");
    }

    @Test void mcnemarExactCalculationIsCorrect() {
        assertThat(SideOrientationStatistics.mcnemarExact(0, 4)).isEqualTo(.125);
        assertThat(SideOrientationStatistics.mcnemarExact(2540, 2460))
                .isBetween(.20, .30);
        assertThat(SideOrientationStatistics.mcnemarExact(0, 0)).isOne();
    }

    @Test void holmCorrectionIsDeterministic() {
        assertThat(SideOrientationStatistics.holm(new double[]{.01, .04, .03}))
                .containsExactly(.03, .06, .06);
    }

    @Test void wilsonIntervalContainsExpectedValues() {
        assertThat(SideOrientationStatistics.wilson(50, 100).contains(.5)).isTrue();
    }

    @Test void fixedFivePercentDifferenceWithoutSignificanceIsNotConfirmedBias() {
        assertThat(classify(cell("PRIMARY", "CHAMPION_OFF", .55, .05, .20), false, false))
                .isEqualTo("LIKELY_SAMPLING_NOISE");
    }

    @Test void significantTwoPercentDifferenceWithFunnelEvidenceIsConfirmedBias() {
        assertThat(classify(cell("PRIMARY", "CHAMPION_OFF", .52, .02, .001), true, false))
                .isEqualTo("CONFIRMED_SIDE_BIAS");
    }

    @Test void championPowerAddedBiasRequiresOffOnDelta() {
        var off = cell("SECONDARY", "CHAMPION_OFF", .50, 0, 1);
        var on = cell("SECONDARY", "CHAMPION_ON", .52, .02, .001);
        var evaluator = new SideOrientationVerdictEvaluator();
        var evidence = Map.of(SideOrientationVerdictEvaluator.cellKey(on),
                new SideOrientationVerdictEvaluator.CellEvidence(true, true));
        assertThat(evaluator.classify(List.of(off, on), evidence).get(1).classification())
                .isEqualTo("CHAMPION_POWER_ADDED_SIDE_BIAS");
    }

    @Test void auditVerdictIsComputedNotHardcoded(@TempDir java.nio.file.Path out) throws Exception {
        var result = new SideOrientationAudit().run(new SideOrientationAuditConfig(2, 2, 2, out));
        assertThat(result.verdict()).isEqualTo(new SideOrientationVerdictEvaluator()
                .verdict(result.statistics(), result.integrityErrorCount()));
        assertThat(Files.readString(out.resolve("side-orientation-summary.csv"))).contains(result.verdict());
    }

    private SideOrientationExecutionStats.Counters counters() {
        return new SideOrientationExecutionStats().counters(SideOrientationResolver.LANE_COMBAT, TeamSide.BLUE);
    }

    private SideOrientationRandomTraceObserver observer(long seed) {
        return new SideOrientationRandomTraceObserver(seed, "ORIGINAL", "TEAM_A", "TEAM_B", true);
    }

    private String classify(SideOrientationCellStatistics cell, boolean structural, boolean champion) {
        var evaluator = new SideOrientationVerdictEvaluator();
        var map = Map.of(SideOrientationVerdictEvaluator.cellKey(cell),
                new SideOrientationVerdictEvaluator.CellEvidence(structural, champion));
        return evaluator.classify(List.of(cell), map).getFirst().classification();
    }

    private SideOrientationCellStatistics cell(String group, String mode,
            double blueRate, double difference, double p) {
        return new SideOrientationCellStatistics(group, "F", mode, "S0", 1000,
                (int) (blueRate * 2000), blueRate, blueRate - .019, blueRate + .019,
                .5 + difference / 2, .5 - difference / 2, difference,
                400, 400, 100, 100, 200, .2, p, Double.NaN,
                difference * 100, "UNCLASSIFIED");
    }
}
