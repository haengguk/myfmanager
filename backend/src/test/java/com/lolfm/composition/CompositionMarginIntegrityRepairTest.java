package com.lolfm.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositionMarginIntegrityRepairTest {
    private CompositionMarginIntegrityRepair.Result result;

    @BeforeAll
    void analyzeFrozenArtifacts() throws Exception {
        result = CompositionMarginIntegrityRepair.analyze();
    }

    @Test void canonicalClassifierUsesFrozenDevelopmentBounds() {
        assertThat(CanonicalCompositionMarginClassifier.bounds(CompositionMarginIntegrityRepair.KEYS.get(0)))
                .isEqualTo(new CanonicalCompositionMarginClassifier.Bounds(5.104214137066, 109.230158346492));
        assertThat(CanonicalCompositionMarginClassifier.bounds(CompositionMarginIntegrityRepair.KEYS.get(1)))
                .isEqualTo(new CanonicalCompositionMarginClassifier.Bounds(.82, 31.96));
        assertThat(CanonicalCompositionMarginClassifier.bounds(CompositionMarginIntegrityRepair.KEYS.get(2)))
                .isEqualTo(new CanonicalCompositionMarginClassifier.Bounds(.82, 22.82));
        assertThat(CanonicalCompositionMarginClassifier.bounds(CompositionMarginIntegrityRepair.KEYS.get(3)))
                .isEqualTo(new CanonicalCompositionMarginClassifier.Bounds(.82, 27.82));
    }

    @Test void holdoutDoesNotRecalculateMarginThresholds() {
        assertThat(CanonicalCompositionMarginClassifier.SOURCE_DATASET).isEqualTo("POLICY_DEVELOPMENT_SET");
        assertThat(CanonicalCompositionMarginClassifier.SOURCE_ARTIFACT_HASH)
                .isEqualTo("c61ea7c3b9f705a44b7b0b8e080e5a123f8743630a285fe59e081571879a2ef1");
    }

    @Test void inclusiveAndExclusiveBoundariesMatchFrozenDefinition() {
        String key = CompositionMarginIntegrityRepair.KEYS.get(1);
        assertThat(CanonicalCompositionMarginClassifier.classify(key, .82)).isEqualTo(CanonicalCompositionMarginClassifier.Band.CLOSE);
        assertThat(CanonicalCompositionMarginClassifier.classify(key, Math.nextUp(.82))).isEqualTo(CanonicalCompositionMarginClassifier.Band.MEDIUM);
        assertThat(CanonicalCompositionMarginClassifier.classify(key, 31.96)).isEqualTo(CanonicalCompositionMarginClassifier.Band.HIGH);
    }

    @Test void serializedBoundaryRecoveryIsLimitedToPrecisionLoss() {
        String key = CompositionMarginIntegrityRepair.KEYS.get(1);
        assertThat(CanonicalCompositionMarginClassifier.classifySerialized(key, .82, "MEDIUM"))
                .isEqualTo(CanonicalCompositionMarginClassifier.Band.MEDIUM);
        assertThatThrownBy(() -> CanonicalCompositionMarginClassifier.classifySerialized(key, 1.0, "CLOSE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void teamfightHighRowsRespectFrozenHighBoundary() { assertHigh(1); }
    @Test void baseDefenseHighRowsRespectFrozenHighBoundary() { assertHigh(3); }
    @Test void siegeHighRowsRespectFrozenHighBoundary() { assertHigh(2); }
    @Test void skirmishHighRowsRespectFrozenHighBoundary() { assertHigh(0); }

    @Test void marginTransferAndScoreDecisionArtifactsHaveExactBandCounts() {
        Map<String, long[]> transfer = aggregate(CompositionMarginIntegrityRepair.transfer(result), 0, 1, 2, 4);
        Map<String, long[]> matrix = aggregate(CompositionMarginIntegrityRepair.matrix(result), 0, 1, 3, 4);
        assertThat(matrix.keySet()).isEqualTo(transfer.keySet());
        matrix.forEach((key, counts) -> assertThat(counts).containsExactly(transfer.get(key)));
    }

    @Test void keySummaryHighFlipShareMatchesCanonicalCounts() {
        List<List<String>> rows = CompositionMarginIntegrityRepair.keySummary(result);
        for (List<String> row : rows.subList(1, rows.size())) {
            var key = result.keys().stream().filter(k -> k.key().equals(row.get(0))).findFirst().orElseThrow();
            assertThat(row.get(11)).isEqualTo(CompositionMarginIntegrityRepair.f((double) key.highFlips() / key.flips()));
        }
    }

    @Test void deepDiveChangedCohortsUseCanonicalClassifier() {
        assertThat(result.items().stream().filter(CompositionMarginIntegrityRepair.Item::flip)
                .allMatch(i -> i.band() == CanonicalCompositionMarginClassifier.classifySerialized(i.key(), i.gap(), i.band().name())))
                .isTrue();
    }

    @Test void canonicalBandCountsPartitionEveryApplicationExactlyOnce() {
        assertThat(result.keys()).allMatch(k -> k.close() + k.medium() + k.high() == k.total());
        assertThat(result.keys().stream().mapToLong(CompositionMarginIntegrityRepair.Key::total).sum()).isEqualTo(42_929);
    }

    @Test void canonicalFlipCountsPartitionEveryLocalFlipExactlyOnce() {
        assertThat(result.keys()).allMatch(k -> k.closeFlips() + k.mediumFlips() + k.highFlips() == k.flips());
    }

    @Test void repairedFlipBandCountsMatchSourceRowsNotConstantsAlone() {
        assertThat(result.keys().stream().map(k -> new long[]{k.flips(), k.closeFlips(), k.mediumFlips(), k.highFlips()}).toList())
                .usingRecursiveComparison().isEqualTo(List.of(
                        new long[]{54,18,29,7}, new long[]{31,1,30,0},
                        new long[]{8,1,5,2}, new long[]{470,0,347,123}));
    }

    @Test void foreignApplicationKeyCannotInfluenceBandCounts() {
        assertThat(result.foreign()).isZero();
        assertThatThrownBy(() -> CanonicalCompositionMarginClassifier.classify("FOREIGN", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void unknownBandFailsFast() {
        assertThat(result.unknown()).isZero();
        assertThatThrownBy(() -> CanonicalCompositionMarginClassifier.classifySerialized(
                CompositionMarginIntegrityRepair.KEYS.get(0), 1, "OTHER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void mechanismClassificationDoesNotCallOtherGradeOnlyWithoutEvidence() {
        assertThat(result.items().stream().filter(CompositionMarginIntegrityRepair.Item::flip)
                .map(CompositionMarginIntegrityRepair.Item::mechanism))
                .doesNotContain("OTHER_LOCAL_DECISION_MECHANISM", "OTHER");
        assertThat(result.keys()).allMatch(k -> k.unresolved() == 0);
    }

    @Test void partiallyObservedGradeMechanismRequiresStructuredGradeDifference() {
        assertThat(CompositionMarginIntegrityRepair.mechanism(true, false, false, "UNIFORM_ADVANTAGE_SIDE_SELECTION_AND_FIGHT_GRADE"))
                .isEqualTo("FIGHT_GRADE_BRANCH_CHANGE_PARTIALLY_OBSERVED");
        assertThat(result.keys().stream().mapToLong(CompositionMarginIntegrityRepair.Key::partialGrade).sum()).isEqualTo(486);
    }

    @Test void scoreDirectionMechanismHasPriority() {
        assertThat(CompositionMarginIntegrityRepair.mechanism(true, true, true, "UNIFORM_ADVANTAGE_SIDE_SELECTION_AND_FIGHT_GRADE"))
                .isEqualTo("SCORE_DIRECTION_FLIP");
    }

    @Test void sideThresholdCrossIsStructuredSeparately() {
        assertThat(CompositionMarginIntegrityRepair.mechanism(true, false, true, "WEIGHTED_INITIATIVE_SIDE_SELECTION"))
                .isEqualTo("PROBABILITY_THRESHOLD_CROSS_WITHOUT_SCORE_FLIP");
    }

    @Test void sourceArtifactsRemainByteIdentical() { assertThat(result.after()).isEqualTo(result.before()); }

    @Test void candidateHashRemainsExact() {
        assertThat(FrozenCompositionGameplayGainPolicy.CANDIDATE_HASH)
                .isEqualTo("ec99828c0f04a00cc644f4d0446d851543a46a530c9bc561408af9cf704da32d");
    }

    @Test void productionModeRemainsOff() {
        assertThat(FrozenCompositionGameplayGainPolicy.current().productionEnabled()).isFalse();
        assertThat(FrozenCompositionGameplayGainPolicy.current().candidateEnabled()).isFalse();
    }

    @Test void noGameplaySimulationExecutedByRepairTask() {
        List<List<String>> summary = CompositionMarginIntegrityRepair.summary(result);
        assertThat(metric(summary, "matchSimulationCount")).isEqualTo("0");
        assertThat(metric(summary, "gameplayApplicationExecutionCount")).isEqualTo("0");
    }

    @Test void allIntegrityErrorCountersAreZero() {
        assertThat(result.errors()).isZero();
        assertThat(result.classificationMismatch()).isZero();
        assertThat(result.bandCross()).isZero();
        assertThat(result.flipCross()).isZero();
        assertThat(result.duplicates()).isZero();
        assertThat(result.missing()).isZero();
        assertThat(result.nan()).isZero();
        assertThat(result.infinity()).isZero();
    }

    @Test void semanticsReviewVerdictRequiresObservedGradeBranchChanges() {
        assertThat(result.keys().stream().filter(k -> !k.key().startsWith("SKIRMISH|")).anyMatch(k -> k.partialGrade() > 0)).isTrue();
        assertThat(result.verdict()).isEqualTo("READY_FOR_PHASE_13D4C3_APPLICATION_SEMANTICS_REVIEW");
    }

    @Test void recommendationsDoNotTuneFrozenGains() {
        assertThat(result.keys().get(0).rec()).isEqualTo("KEEP_CURRENT_GAIN");
        assertThat(result.keys().subList(1, 4)).allMatch(k -> k.rec().equals("APPLICATION_SEMANTICS_REDESIGN"));
    }

    private void assertHigh(int keyIndex) {
        String key = CompositionMarginIntegrityRepair.KEYS.get(keyIndex);
        double highMin = CanonicalCompositionMarginClassifier.bounds(key).highMin();
        assertThat(result.items().stream().filter(i -> i.key().equals(key) && i.band() == CanonicalCompositionMarginClassifier.Band.HIGH))
                .allMatch(i -> Math.abs(i.gap()) >= highMin);
    }

    private static Map<String, long[]> aggregate(List<List<String>> rows, int key, int band, int samples, int flips) {
        Map<String, long[]> out = new HashMap<>();
        for (List<String> row : rows.subList(1, rows.size())) {
            String id = row.get(key) + "|" + row.get(band);
            long[] counts = out.computeIfAbsent(id, ignored -> new long[2]);
            counts[0] += Long.parseLong(row.get(samples));
            counts[1] += Long.parseLong(row.get(flips));
        }
        return out;
    }

    private static String metric(List<List<String>> rows, String name) {
        return rows.stream().skip(1).filter(r -> r.get(0).equals(name)).findFirst().orElseThrow().get(1);
    }
}
