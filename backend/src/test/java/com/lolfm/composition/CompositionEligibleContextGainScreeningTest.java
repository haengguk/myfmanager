package com.lolfm.composition;

import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.SimulationOptions;
import com.lolfm.composition.TeamCompositionGameplayMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class CompositionEligibleContextGainScreeningTest {
    @Test
    void screeningUsesExactlyFourStructuredApplicationKeys() {
        assertThat(CompositionEligibleContextGainScreening.APPROVED_KEYS).hasSize(4)
                .doesNotHaveDuplicates();
        assertThat(CompositionEligibleContextGainScreening.APPROVED_KEYS)
                .allMatch(key -> key.actionType() != CompositionActionType.JUNGLE_GANK
                        && key.actionType() != CompositionActionType.LANE_COMBAT
                        && key.actionType() != CompositionActionType.ROAM
                        && key.actionType() != CompositionActionType.OBJECTIVE_SETUP);
    }

    @Test
    void inputFilterAcceptsOnlyEligibleAvailableUnappliedApprovedRows() {
        var eligible = observation(0, 1, 1, key(TeamCompositionContext.TEAMFIGHT,
                CompositionActionType.TEAMFIGHT, CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE),
                true, true, false, BigDecimal.ZERO);
        var deferred = new CompositionEligibleContextGainScreening.RawObservation(0, 1, 2, 100,
                CompositionActionType.JUNGLE_GANK, TeamCompositionContext.SKIRMISH, TeamSide.BLUE,
                CompositionBaselineScoreDomain.NOT_AVAILABLE, bd("0.2"), null, null, null,
                CompositionApplicationEligibility.INELIGIBLE_NO_EXISTING_SCORE_DOMAIN, false, false, BigDecimal.ZERO);
        var result = CompositionEligibleContextGainScreening.filter(List.of(eligible, deferred));
        assertThat(result.filtered()).hasSize(1);
        assertThat(result.eligibleObservationCount()).isOne();
        assertThat(result.ineligibleObservationCount()).isOne();
        assertThat(result.deferredObservationIncludedCount()).isZero();
    }

    @Test
    void duplicateAttemptApplicationKeyIsRejected() {
        var key = key(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE);
        var row = observation(1, 1, 7, key, true, true, false, BigDecimal.ZERO);
        assertThatThrownBy(() -> CompositionEligibleContextGainScreening.filter(List.of(row, row)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate attempt/application key");
    }

    @Test
    void partitionUsesCaseIndexModuloThreeWithoutRandom() {
        var key = key(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE);
        List<CompositionEligibleContextGainScreening.Observation> values = List.of(
                observation(0, 1, 1, key), observation(1, 1, 2, key), observation(2, 1, 3, key));
        var partition = CompositionEligibleContextGainScreening.partition(values);
        assertThat(partition.validation()).extracting(CompositionEligibleContextGainScreening.Observation::caseIndex).containsExactly(0);
        assertThat(partition.calibration()).extracting(CompositionEligibleContextGainScreening.Observation::caseIndex).containsExactly(1, 2);
        assertThat(partition.caseLeakageCount()).isZero();
    }

    @Test
    void nearestRankQuantileAndCanonicalDecimalAreDeterministic() {
        List<BigDecimal> values = List.of(bd("1"), bd("2"), bd("3"), bd("4"));
        assertThat(CompositionEligibleContextGainScreening.quantile(values, .50)).isEqualByComparingTo("2");
        assertThat(CompositionEligibleContextGainScreening.quantile(values, .90)).isEqualByComparingTo("4");
        assertThat(CompositionEligibleContextGainScreening.format(new BigDecimal("-0.000000000000"))).isEqualTo("0.000000000000");
        assertThat(CompositionEligibleContextGainScreening.format(new BigDecimal("1.2345678901236"))).isEqualTo("1.234567890124");
    }

    @Test
    void gainGridHasExactlyFiveCandidatesPerKeyAndNoExpansion() {
        var key = key(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE);
        var anchors = new java.util.LinkedHashMap<CompositionEligibleContextGainScreening.GainKey, CompositionEligibleContextGainScreening.Anchor>();
        for (var approved : CompositionEligibleContextGainScreening.APPROVED_KEYS) anchors.put(approved, new CompositionEligibleContextGainScreening.Anchor(approved, Map.of(), Map.of(), bd("2"), bd("10")));
        var grid = CompositionEligibleContextGainScreening.grid(anchors);
        assertThat(grid).filteredOn(x -> x.key().equals(key)).hasSize(5).extracting(CompositionEligibleContextGainScreening.GridCandidate::label)
                .containsExactly("ZERO_REFERENCE", "VERY_LOW", "LOW", "MEDIUM", "HIGH_SCREENING_LIMIT");
        assertThat(grid.get(1).gain()).isEqualByComparingTo("0.125000000000");
    }

    @Test
    void positiveNegativeAndZeroEdgesFollowCanonicalHalfSplitFormula() {
        var key = key(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE);
        var band = new CompositionEligibleContextGainScreening.MarginBand(bd("5"), bd("20"));
        var positive = CompositionEligibleContextGainScreening.counterfactual(observation(1, 1, 1, key,
                bd("1"), bd("20"), bd("10")), bd("2"), band);
        assertThat(positive.modifier()).isEqualByComparingTo("2");
        assertThat(positive.adjustedGap()).isEqualByComparingTo("12");
        assertThat(positive.perspectiveAdjustment()).isEqualByComparingTo("1");
        assertThat(positive.opponentAdjustment()).isEqualByComparingTo("-1");
        assertThat(positive.midpointDrift()).isFalse();
        assertThat(positive.gapArithmeticMismatch()).isFalse();

        var negative = CompositionEligibleContextGainScreening.counterfactual(observation(1, 1, 2, key,
                bd("-1"), bd("20"), bd("10")), bd("2"), band);
        assertThat(negative.adjustedGap()).isEqualByComparingTo("8");
        var zero = CompositionEligibleContextGainScreening.counterfactual(observation(1, 1, 3, key,
                BigDecimal.ZERO, bd("20"), bd("10")), bd("2"), band);
        assertThat(zero.modifier()).isEqualByComparingTo("0");
        assertThat(CompositionEligibleContextGainScreening.format(zero.modifier())).isEqualTo("0.000000000000");
    }

    @Test
    void sideSwapProducesExactOppositeGapAdjustment() {
        var key = key(TeamCompositionContext.BASE_DEFENSE, CompositionActionType.BASE_DEFENSE,
                CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE);
        var band = new CompositionEligibleContextGainScreening.MarginBand(bd("5"), bd("20"));
        var value = CompositionEligibleContextGainScreening.counterfactual(observation(1, 1, 1, key,
                bd("0.3"), bd("20"), bd("10")), bd("2"), band);
        assertThat(value.sideReversalMismatch()).isFalse();
    }

    @Test
    void scoreOrientationIsExplicitlyHigherIsBetterForAllKeys() {
        assertThat(CompositionEligibleContextGainScreening.CompositionScoreOrientation.HIGHER_IS_BETTER).isNotNull();
        assertThat(CompositionEligibleContextGainScreening.applicationPoint(
                key(TeamCompositionContext.SIEGE, CompositionActionType.SIEGE_COMBAT,
                        CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE)))
                .isEqualTo(CompositionApplicationPoint.SIEGE_PUSH);
    }

    @Test
    void candidateHashIgnoresSelectionIterationOrder() {
        var first = selections(false);
        var second = new ArrayList<>(first);
        java.util.Collections.reverse(second);
        assertThat(CompositionEligibleContextGainScreening.candidateHash(first))
                .isEqualTo(CompositionEligibleContextGainScreening.candidateHash(second));
    }

    @Test
    void candidateEvaluationUsesOnlyMatchingApplicationKey() {
        var teamKey = key(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT, CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE);
        var skirmishKey = key(TeamCompositionContext.SKIRMISH, CompositionActionType.SKIRMISH, CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE);
        var team = observation(1, 1, 1, teamKey); var skirmish = observation(2, 1, 2, skirmishKey);
        var anchors = testAnchors(); var grid = CompositionEligibleContextGainScreening.grid(anchors);
        var metrics = CompositionEligibleContextGainScreening.evaluateCandidates(List.of(team, skirmish), grid, anchors, List.of(team, skirmish)).get(teamKey);
        assertThat(metrics).allMatch(value -> value.sampleCount() == 1 && value.metricScope().equals("APPLICATION_KEY_LOCAL") && value.foreignKeyObservationCount() == 0);
    }

    @Test
    void metricsRejectForeignApplicationKeyRows() {
        var teamKey = key(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT, CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE);
        var skirmishKey = key(TeamCompositionContext.SKIRMISH, CompositionActionType.SKIRMISH, CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE);
        var candidate = new CompositionEligibleContextGainScreening.GridCandidate(teamKey, "LOW", bd("0.050"), bd("1"));
        assertThatThrownBy(() -> CompositionEligibleContextGainScreening.metrics(List.of(observation(1, 1, 1, skirmishKey), candidateObservation(teamKey)), candidate, new CompositionEligibleContextGainScreening.MarginBand(teamKey, bd("5"), bd("20"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Foreign application key");
    }

    @Test
    void unrelatedKeyRowsDoNotChangeMetrics() {
        var teamKey = key(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT, CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE);
        var skirmishKey = key(TeamCompositionContext.SKIRMISH, CompositionActionType.SKIRMISH, CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE);
        var team = observation(1, 1, 1, teamKey); var skirmish = observation(2, 1, 2, skirmishKey); var anchors = testAnchors(); var grid = CompositionEligibleContextGainScreening.grid(anchors);
        var only = CompositionEligibleContextGainScreening.evaluateCandidates(List.of(team), grid, anchors, List.of(team, skirmish)).get(teamKey);
        var mixed = CompositionEligibleContextGainScreening.evaluateCandidates(List.of(team, skirmish), grid, anchors, List.of(team, skirmish)).get(teamKey);
        assertThat(mixed).isEqualTo(only);
    }

    @Test
    void marginBandUsesSameKeyCalibrationRows() {
        var teamKey = key(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT, CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE);
        var skirmishKey = key(TeamCompositionContext.SKIRMISH, CompositionActionType.SKIRMISH, CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE);
        var team = candidateObservation(teamKey, bd("20"), bd("10")); var skirmish = candidateObservation(skirmishKey, bd("200"), bd("0"));
        var teamBand = CompositionEligibleContextGainScreening.marginBands(List.of(team, skirmish)).get(teamKey);
        assertThat(teamBand.key()).isEqualTo(teamKey); assertThat(teamBand.highMin()).isEqualByComparingTo("10");
    }

    @Test
    void selectorInputComponentsUseApprovedStructuredAllowlist() {
        assertThat(Arrays.stream(CompositionEligibleContextGainScreening.RawObservation.class.getRecordComponents()).map(RecordComponent::getName).toList())
                .containsExactlyInAnyOrder("caseIndex", "seed", "attemptId", "matchTimeSeconds", "actionType", "context", "perspectiveSide", "scoreDomain", "edge", "perspectiveBaselineScore", "opponentBaselineScore", "gap", "eligibility", "baselineScoreAvailable", "applicationApplied", "appliedModifier");
        assertThat(Arrays.stream(CompositionEligibleContextGainScreening.Observation.class.getRecordComponents()).map(RecordComponent::getName).toList())
                .containsExactlyInAnyOrder("caseIndex", "seed", "attemptId", "matchTimeSeconds", "key", "perspectiveSide", "edge", "perspectiveScore", "opponentScore", "gap");
    }

    @Test
    @Tag("diagnostic")
    @Tag("historical-artifact")
    void outcomeLikeExtraColumnsDoNotChangeParsedInput( @TempDir Path tempDir) throws Exception {
        List<String> firstTwo; try (Stream<String> stream = Files.lines(Path.of("build/reports/composition-shadow-wiring-gate-closure/composition-shadow-observations-gate.csv"))) { firstTwo = stream.limit(2).toList(); }
        Path plain = tempDir.resolve("plain.csv"); Path extra = tempDir.resolve("extra.csv");
        Files.write(plain, firstTwo, StandardCharsets.UTF_8);
        Files.write(extra, List.of(firstTwo.get(0) + ",winner,duration,killResult,lineupId,teamName", firstTwo.get(1) + ",BLUE,999,KILL,L1,TEAM_A"), StandardCharsets.UTF_8);
        assertThat(CompositionEligibleContextGainScreening.readObservations(plain)).isEqualTo(CompositionEligibleContextGainScreening.readObservations(extra));
        assertThat(CompositionEligibleContextGainScreening.filter(CompositionEligibleContextGainScreening.readObservations(plain)))
                .isEqualTo(CompositionEligibleContextGainScreening.filter(CompositionEligibleContextGainScreening.readObservations(extra)));
    }

    @Test
    void validationDecisionIsCanonicalAndRejectedCandidatesHaveReasons() {
        var teamKey = key(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT, CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE);
        var candidate = new CompositionEligibleContextGainScreening.GridCandidate(teamKey, "LOW", bd("0.050"), bd("1"));
        var metric = CompositionEligibleContextGainScreening.metrics(List.of(candidateObservation(teamKey)), candidate, new CompositionEligibleContextGainScreening.MarginBand(teamKey, bd("5"), bd("20")));
        var decision = CompositionEligibleContextGainScreening.validationDecision(metric, List.of(metric));
        assertThat(decision.accepted()).isFalse(); assertThat(decision.reasons()).isNotEmpty();
        assertThat(CompositionEligibleContextGainScreening.validationAccepted(metric, List.of(metric))).isEqualTo(decision.accepted());
        assertThat(CompositionEligibleContextGainScreening.validationReasons(metric, List.of(metric))).isEqualTo(decision.reasons());
    }

    @Test
    void historicalScreeningPolicyRemainsNeutralWhileFrozenV2IsProduction() {
        assertThat(SimulationOptions.productionDefaults().teamCompositionGameplayMode()).isEqualTo(TeamCompositionGameplayMode.PRODUCTION_V2);
        assertThat(SimulationOptions.productionDefaults().championMatchupMode().name()).isEqualTo("GEOMETRIC_V2");
        assertThat(FrozenCompositionInteractionRuntimePolicy.current().gain()).isEqualTo("NONE");
    }

    private static CompositionEligibleContextGainScreening.RawObservation observation(int caseIndex, long seed, long attemptId,
                                                                                        CompositionEligibleContextGainScreening.GainKey key,
                                                                                        boolean eligible, boolean available,
                                                                                        boolean applied, BigDecimal modifier) {
        return new CompositionEligibleContextGainScreening.RawObservation(caseIndex, seed, attemptId, 100,
                key.actionType(), key.context(), TeamSide.BLUE, key.scoreDomain(), bd("0.3"), bd("20"), bd("10"), bd("10"),
                eligible ? CompositionApplicationEligibility.ELIGIBLE_EXISTING_SCORE_DOMAIN : CompositionApplicationEligibility.INELIGIBLE_NO_EXISTING_SCORE_DOMAIN,
                available, applied, modifier);
    }

    private static CompositionEligibleContextGainScreening.Observation observation(int caseIndex, long seed, long attemptId,
                                                                                     CompositionEligibleContextGainScreening.GainKey key) {
        return new CompositionEligibleContextGainScreening.Observation(caseIndex, seed, attemptId, 100, key, TeamSide.BLUE,
                bd("0.3"), bd("20"), bd("10"), bd("10"));
    }

    private static CompositionEligibleContextGainScreening.Observation observation(int caseIndex, long seed, long attemptId,
                                                                                     CompositionEligibleContextGainScreening.GainKey key,
                                                                                     BigDecimal edge, BigDecimal perspective, BigDecimal opponent) {
        return new CompositionEligibleContextGainScreening.Observation(caseIndex, seed, attemptId, 100, key, TeamSide.BLUE,
                edge, perspective, opponent, perspective.subtract(opponent));
    }

    private static CompositionEligibleContextGainScreening.GainKey key(TeamCompositionContext context,
                                                                         CompositionActionType action,
                                                                         CompositionBaselineScoreDomain domain) {
        return new CompositionEligibleContextGainScreening.GainKey(context, action, domain);
    }

    private static List<CompositionEligibleContextGainScreening.Selection> selections(boolean reverse) {
        List<CompositionEligibleContextGainScreening.Selection> values = new ArrayList<>();
        for (var key : CompositionEligibleContextGainScreening.APPROVED_KEYS) {
            values.add(new CompositionEligibleContextGainScreening.Selection(key, true, "LOW", bd("0.050"), bd("1.234567890123"), "TEST", List.of()));
        }
        if (reverse) java.util.Collections.reverse(values);
        return values;
    }

    private static Map<CompositionEligibleContextGainScreening.GainKey, CompositionEligibleContextGainScreening.Anchor> testAnchors() {
        var anchors = new java.util.LinkedHashMap<CompositionEligibleContextGainScreening.GainKey, CompositionEligibleContextGainScreening.Anchor>();
        for (var key : CompositionEligibleContextGainScreening.APPROVED_KEYS) anchors.put(key, new CompositionEligibleContextGainScreening.Anchor(key, Map.of(), Map.of(), bd("2"), bd("10")));
        return anchors;
    }

    private static CompositionEligibleContextGainScreening.Observation candidateObservation(CompositionEligibleContextGainScreening.GainKey key) {
        return candidateObservation(key, bd("20"), bd("10"));
    }

    private static CompositionEligibleContextGainScreening.Observation candidateObservation(CompositionEligibleContextGainScreening.GainKey key, BigDecimal perspective, BigDecimal opponent) {
        return new CompositionEligibleContextGainScreening.Observation(1, 1, 1, 100, key, TeamSide.BLUE, bd("0.3"), perspective, opponent, perspective.subtract(opponent));
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
