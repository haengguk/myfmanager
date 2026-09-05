package com.lolfm.composition;

import com.lolfm.simulator.TeamSide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class CompositionGainPolicyReviewTest {
    private static final CompositionEligibleContextGainScreening.GainKey KEY =
            CompositionGainPolicyReview.KEYS.get(0);

    @Test
    void strictPolicyPreservesOriginalOverallFlipRule() {
        var metric = metric("LOW", "0.050", 400, 400, 0, 508, 1740, 488, 4283, 20, 621, 0);
        var strict = CompositionGainPolicyReview.strictDecision(metric, List.of(calibration(metric)));
        assertThat(strict.accepted()).isFalse();
        assertThat(strict.reasons()).contains("SIGN_FLIP_ABOVE_030");
    }

    @Test
    void marginAwarePolicyClassifiesCloseFlipAsTieBreak() {
        assertThat(CompositionGainPolicyReview.classifyFlip(true, CompositionEligibleContextGainScreening.MarginBandName.CLOSE, 1, -1))
                .isEqualTo("TIE_BREAK_FLIP");
    }

    @Test
    void mediumFlipIsMaterialFlip() {
        assertThat(CompositionGainPolicyReview.classifyFlip(true, CompositionEligibleContextGainScreening.MarginBandName.MEDIUM, 1, -1))
                .isEqualTo("MATERIAL_FLIP");
    }

    @Test
    void highFlipIsMaterialAndHighMarginFlip() {
        assertThat(CompositionGainPolicyReview.classifyFlip(true, CompositionEligibleContextGainScreening.MarginBandName.HIGH, 1, -1))
                .isEqualTo("HIGH_MARGIN_FLIP");
    }

    @Test
    void zeroBreakIsNotSignFlip() {
        assertThat(CompositionGainPolicyReview.classifyFlip(false, CompositionEligibleContextGainScreening.MarginBandName.CLOSE, 0, 1))
                .isEqualTo("ZERO_BREAK");
    }

    @Test
    void overallFlipRateIsInformationalForMarginAwarePolicy() {
        var metric = metric("LOW", "0.050", 400, 400, 0, 100, 400, 100, 0, 0, 0, 0);
        var decision = CompositionGainPolicyReview.marginAwareDecision(metric, List.of(calibration(metric)));
        assertThat(decision.accepted()).isTrue();
        assertThat(decision.reasons()).doesNotContain("SIGN_FLIP_ABOVE_030");
    }

    @Test
    void highMarginFlipAlwaysBlocksCandidate() {
        var metric = metric("LOW", "0.050", 400, 400, 0, 1, 300, 0, 100, 0, 0, 1);
        var decision = CompositionGainPolicyReview.marginAwareDecision(metric, List.of(calibration(metric)));
        assertThat(decision.accepted()).isFalse();
        assertThat(decision.reasons()).contains("HIGH_MARGIN_FLIP");
    }

    @Test
    void nonCloseFlipRateAboveOnePercentBlocksCandidate() {
        var metric = metric("LOW", "0.050", 400, 400, 0, 2, 300, 0, 100, 2, 0, 0);
        var decision = CompositionGainPolicyReview.marginAwareDecision(metric, List.of(calibration(metric)));
        assertThat(decision.accepted()).isFalse();
        assertThat(decision.reasons()).contains("NON_CLOSE_FLIP_RATE_ABOVE_010");
    }

    @Test
    void closeFlipRateAboveOneThirdBlocksCandidate() {
        var metric = metric("LOW", "0.050", 400, 400, 0, 2, 300, 101, 100, 0, 0, 0);
        var decision = CompositionGainPolicyReview.marginAwareDecision(metric, List.of(calibration(metric)));
        assertThat(decision.accepted()).isFalse();
        assertThat(decision.reasons()).contains("CLOSE_FLIP_RATE_ABOVE_333");
    }

    @Test
    void closeConcentrationBelowNinetyFivePercentBlocksCandidate() {
        var metric = metric("LOW", "0.050", 400, 400, 0, 2, 300, 1, 100, 1, 0, 0);
        var decision = CompositionGainPolicyReview.marginAwareDecision(metric, List.of(calibration(metric)));
        assertThat(decision.accepted()).isFalse();
        assertThat(decision.reasons()).contains("FLIP_CLOSE_CONCENTRATION_BELOW_950");
    }

    @Test
    void zeroOverallFlipsProduceFullCloseConcentration() {
        var metric = metric("LOW", "0.050", 400, 400, 0, 0, 300, 0, 100, 0, 0, 0);
        assertThat(CompositionGainPolicyReview.bandStats(metric).flipCloseConcentration()).isEqualByComparingTo("1.000000000000");
    }

    @Test
    void sameMarginAwareThresholdsApplyToAllKeys() {
        assertThat(CompositionGainPolicyReview.policyDefinitions()).hasSize(2)
                .extracting(CompositionGainPolicyReview.PolicyDefinition::closeFlipMax)
                .containsExactly(null, new BigDecimal("0.333333333333"));
        assertThat(CompositionGainPolicyReview.policyDefinitions().get(1).nonCloseFlipMax()).isEqualByComparingTo("0.010000000000");
    }

    @Test
    void policyHasNoKeySpecificThreshold() {
        assertThat(CompositionGainPolicyReview.policyDefinitions())
                .allMatch(policy -> policy.id().equals(CompositionGainPolicyReview.STRICT_POLICY_VERSION)
                        || policy.id().equals(CompositionGainPolicyReview.MARGIN_AWARE_POLICY_VERSION));
    }

    @Test
    void policyHasNoChampionSpecificThreshold() {
        assertThat(CompositionGainPolicyReview.canonicalCandidatePayload(Map.of(), Map.of()))
                .doesNotContain("championSpecific", "lineupSpecific", "teamSpecific", "sideSpecific");
    }

    @Test
    void applicationKeyStillIncludesContextActionAndScoreDomain() {
        assertThat(KEY.stableId()).isEqualTo("SKIRMISH|SKIRMISH|SKIRMISH_COMBAT_SCORE");
        assertThat(CompositionGainPolicyReview.KEYS).allMatch(key -> key.context() != null && key.actionType() != null && key.scoreDomain() != null);
    }

    @Test
    void policyReviewReusesExactlyTwentyGridRows() {
        var anchors = new LinkedHashMap<CompositionEligibleContextGainScreening.GainKey, CompositionEligibleContextGainScreening.Anchor>();
        for (var key : CompositionGainPolicyReview.KEYS) anchors.put(key,
                new CompositionEligibleContextGainScreening.Anchor(key, Map.of(), Map.of(), bd("2"), bd("10")));
        assertThat(CompositionEligibleContextGainScreening.grid(anchors)).hasSize(20);
    }

    @Test
    void gainGridIsUnchangedFromPhase13D4B1() {
        var anchors = new LinkedHashMap<CompositionEligibleContextGainScreening.GainKey, CompositionEligibleContextGainScreening.Anchor>();
        for (var key : CompositionGainPolicyReview.KEYS) anchors.put(key,
                new CompositionEligibleContextGainScreening.Anchor(key, Map.of(), Map.of(), bd("2"), bd("10")));
        assertThat(CompositionEligibleContextGainScreening.grid(anchors)).extracting(x -> x.label())
                .containsExactly("ZERO_REFERENCE", "VERY_LOW", "LOW", "MEDIUM", "HIGH_SCREENING_LIMIT",
                        "ZERO_REFERENCE", "VERY_LOW", "LOW", "MEDIUM", "HIGH_SCREENING_LIMIT",
                        "ZERO_REFERENCE", "VERY_LOW", "LOW", "MEDIUM", "HIGH_SCREENING_LIMIT",
                        "ZERO_REFERENCE", "VERY_LOW", "LOW", "MEDIUM", "HIGH_SCREENING_LIMIT");
    }

    @Test
    void noIntermediateGainIsGenerated() {
        assertThat(CompositionEligibleContextGainScreening.TARGET_RATIOS)
                .containsExactly(bd("0.000"), bd("0.025"), bd("0.050"), bd("0.075"), bd("0.100"));
    }

    @Test
    void zeroReferenceCannotBeSelected() {
        var zero = metric("ZERO_REFERENCE", "0.000", 400, 400, 0, 0, 300, 0, 100, 0, 0, 0);
        var low = metric("LOW", "0.050", 400, 400, 0, 0, 300, 0, 100, 0, 0, 0);
        var selections = CompositionGainPolicyReview.select(Map.of(KEY, List.of(
                evaluation(zero), evaluation(low))));
        assertThat(selections.get(KEY).selected()).isTrue();
        assertThat(selections.get(KEY).label()).isEqualTo("LOW");
    }

    @Test
    void smallestMarginAwareAcceptedCandidateIsSelected() {
        var veryLow = metric("VERY_LOW", "0.025", 400, 400, 0, 0, 300, 0, 100, 0, 0, 0);
        var low = metric("LOW", "0.050", 400, 400, 0, 0, 300, 0, 100, 0, 0, 0);
        var selections = CompositionGainPolicyReview.select(Map.of(KEY, List.of(evaluation(veryLow), evaluation(low))));
        assertThat(selections.get(KEY).label()).isEqualTo("LOW");
    }

    @Test
    void strictAndMarginAwareDecisionsAreRecordedSeparately() {
        var metric = metric("LOW", "0.050", 400, 400, 0, 508, 1740, 488, 4283, 20, 621, 0);
        var evaluation = evaluation(metric);
        assertThat(evaluation.strict().accepted()).isFalse();
        assertThat(evaluation.marginAware().accepted()).isTrue();
    }

    @Test
    void validationDecisionUsesCanonicalMarginAwarePredicate() {
        var metric = metric("LOW", "0.050", 400, 400, 0, 0, 300, 0, 100, 0, 0, 0);
        assertThat(CompositionGainPolicyReview.marginAwareDecision(metric, List.of(calibration(metric))).accepted()).isTrue();
    }

    @Test
    void fullHighMarginFlipBlocksCandidate() {
        var metric = metric("LOW", "0.050", 1200, 1200, 0, 1, 900, 0, 300, 0, 0, 1);
        assertThat(CompositionGainPolicyReview.fullDecision(metric).reasons()).contains("HIGH_MARGIN_FLIP");
    }

    @Test
    void fullNonCloseFlipLimitIsEnforced() {
        var metric = metric("LOW", "0.050", 1200, 1200, 0, 2, 900, 0, 300, 2, 0, 0);
        assertThat(CompositionGainPolicyReview.fullDecision(metric).accepted()).isFalse();
    }

    @Test
    void fullCloseFlipLimitIsEnforced() {
        var metric = metric("LOW", "0.050", 1200, 1200, 0, 2, 900, 301, 300, 0, 0, 0);
        assertThat(CompositionGainPolicyReview.fullDecision(metric).accepted()).isFalse();
    }

    @Test
    void fullCloseConcentrationIsEnforced() {
        var metric = metric("LOW", "0.050", 1200, 1200, 0, 2, 900, 1, 300, 1, 0, 0);
        assertThat(CompositionGainPolicyReview.fullDecision(metric).accepted()).isFalse();
    }

    @Test
    void candidateHashIncludesPolicyThresholds() {
        String payload = CompositionGainPolicyReview.canonicalCandidatePayload(Map.of(), Map.of());
        assertThat(payload).contains("closeFlipRateLimit=0.333333333333", "nonCloseFlipRateLimit=0.010000000000", "flipCloseConcentrationMinimum=0.950000000000");
    }

    @Test
    void candidateHashIsDeterministic() {
        var selections = new LinkedHashMap<CompositionEligibleContextGainScreening.GainKey, CompositionGainPolicyReview.SelectionResult>();
        var full = new LinkedHashMap<CompositionEligibleContextGainScreening.GainKey, CompositionGainPolicyReview.FullEvaluation>();
        assertThat(CompositionGainPolicyReview.candidateHash(selections, full)).isEqualTo(CompositionGainPolicyReview.candidateHash(selections, full));
    }

    @Test
    void candidateHashIgnoresMapIterationOrder() {
        var first = new LinkedHashMap<CompositionEligibleContextGainScreening.GainKey, CompositionGainPolicyReview.SelectionResult>();
        var second = new LinkedHashMap<CompositionEligibleContextGainScreening.GainKey, CompositionGainPolicyReview.SelectionResult>();
        for (var key : CompositionGainPolicyReview.KEYS) first.put(key, new CompositionGainPolicyReview.SelectionResult(key, false, "NONE", BigDecimal.ZERO, BigDecimal.ZERO, "REVIEW", List.of(), null));
        for (int i = CompositionGainPolicyReview.KEYS.size() - 1; i >= 0; i--) { var key = CompositionGainPolicyReview.KEYS.get(i); second.put(key, first.get(key)); }
        assertThat(CompositionGainPolicyReview.canonicalCandidatePayload(first, Map.of())).isEqualTo(CompositionGainPolicyReview.canonicalCandidatePayload(second, Map.of()));
    }

    @Test
    void candidateHasNoGlobalFallbackGain() {
        assertThat(CompositionGainPolicyReview.canonicalCandidatePayload(Map.of(), Map.of())).doesNotContain("fallbackGain", "globalFallback");
    }

    @Test
    void candidateHasNoDeadzoneClampCapOrOverride() {
        assertThat(CompositionGainPolicyReview.canonicalCandidatePayload(Map.of(), Map.of())).contains("deadzone=NONE", "clamp=NONE", "cap=NONE", "override=0");
    }

    @Test
    void currentValidationIsMarkedPolicyDevelopmentSet() {
        assertThat(CompositionGainPolicyReview.canonicalCandidatePayload(Map.of(), Map.of())).contains("policyDevelopmentSet=POLICY_DEVELOPMENT_VALIDATION_SET", "blindHoldoutClaimed=false");
    }

    @Test
    void candidateRequiresFreshGameplayAudit() {
        assertThat(CompositionGainPolicyReview.canonicalCandidatePayload(Map.of(), Map.of())).contains("freshGameplayAuditRequired=true");
    }

    @Test
    void productionDefaultAndCandidateGuardRemainDisabled() {
        assertThat(CompositionGainPolicyReview.OUTPUT.toString()).contains("composition-margin-aware-gain-policy-review");
        assertThat(CompositionEligibleContextGainScreening.PREVIOUS_OUTPUT).isNotEqualTo(CompositionGainPolicyReview.OUTPUT);
    }

    @Test
    void policyReviewRunsWithoutAProductionSimulationEntryPoint() {
        assertThat(CompositionGainPolicyReview.class.getName()).doesNotContain("simulator.MatchSimulator");
    }

    @Test
    void applicationKeyIsStructuredAndNotDisplayText() {
        assertThat(KEY.stableId()).doesNotContain(" ", ":", "message", "description");
    }

    @Test
    void sourceSummaryAndAuditHashesAreFrozen() {
        assertThat(CompositionGainPolicyReview.SOURCE_SUMMARY_HASH).isEqualTo("8d12c0cf10d941321faf47926477fbf1398ded34f5d6ff67e5c993ed712f8b34");
        assertThat(CompositionGainPolicyReview.SOURCE_AUDIT_HASH).isEqualTo("9ff852c507ae93083ff5823d8d927ea0269749c45e78ecdb4ce2a57cdca15c23");
    }

    @Test
    void fullFailureDoesNotAutoSelectAnotherGain() {
        var low = metric("LOW", "0.050", 400, 400, 0, 0, 300, 0, 100, 0, 0, 0);
        var medium = metric("MEDIUM", "0.075", 400, 400, 0, 0, 300, 0, 100, 0, 0, 0);
        var selection = CompositionGainPolicyReview.select(Map.of(KEY, List.of(evaluation(low), evaluation(medium)))).get(KEY);
        assertThat(selection.label()).isEqualTo("LOW");
    }

    @Test
    void laterGameplayAuditCannotChangePolicyIdentity() {
        assertThat(CompositionGainPolicyReview.SAFETY_POLICY_VERSION).isEqualTo("composition-gain-margin-aware-safety-policy-v1");
        assertThat(CompositionGainPolicyReview.ADJUSTMENT_FORMULA).isEqualTo("GAP_MODIFIER_HALF_SPLIT_V1");
    }

    @Test
    @Tag("diagnostic")
    @Tag("historical-artifact")
    void reviewReadsSourceAndProducesFrozenCandidateWithoutRuntimeActivation(@TempDir Path tempDir) throws Exception {
        var result = CompositionGainPolicyReview.review(CompositionGainPolicyReview.SOURCE_OBSERVATIONS,
                CompositionGainPolicyReview.SOURCE_OUTPUT, tempDir.resolve("review"));
        assertThat(result.integrity().errorCount()).isZero();
        assertThat(result.candidateFrozen()).isTrue();
        assertThat(result.candidateHash()).isNotEqualTo("NONE");
        assertThat(result.selections().values()).allMatch(CompositionGainPolicyReview.SelectionResult::selected);
        assertThat(result.fullEvaluations().values()).allMatch(CompositionGainPolicyReview.FullEvaluation::accepted);
        assertThat(result.verdict()).isEqualTo("READY_FOR_PHASE_13D4C");
    }

    private static CompositionGainPolicyReview.CandidateEvaluation evaluation(CompositionEligibleContextGainScreening.Metrics metric) {
        return new CompositionGainPolicyReview.CandidateEvaluation(KEY, metric,
                CompositionGainPolicyReview.strictDecision(metric, List.of(calibration(metric))),
                CompositionGainPolicyReview.marginAwareDecision(metric, List.of(calibration(metric))),
                CompositionGainPolicyReview.bandStats(metric));
    }

    private static CompositionEligibleContextGainScreening.Metrics calibration(CompositionEligibleContextGainScreening.Metrics metric) {
        return metric;
    }

    private static CompositionEligibleContextGainScreening.Metrics metric(String label, String ratio, int sample, int distinct,
                                                                           long zeroPositive, long overallFlips, long closeSample,
                                                                           long closeFlips, long mediumSample, long mediumFlips,
                                                                           long highSample, long highFlips) {
        var candidate = new CompositionEligibleContextGainScreening.GridCandidate(KEY, label, bd(ratio), bd("1"));
        BigDecimal rate = sample == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(overallFlips).divide(BigDecimal.valueOf(sample), 20, java.math.RoundingMode.HALF_EVEN);
        return new CompositionEligibleContextGainScreening.Metrics(candidate, sample, distinct, 200, 200, 200, 200, 0,
                bd("1"), bd("0.5"), bd("0.8"), bd("1"), bd("1"), bd("1"), bd("1"), bd(ratio), bd("0.050"), bd("0.050"), bd("0.050"), overallFlips, rate,
                closeSample, mediumSample, highSample, closeFlips, mediumFlips, highFlips, zeroPositive, 0, 0, bd("0"), 200, 0, 0, 0, 0, 0, 0,
                "APPLICATION_KEY_LOCAL", KEY.context(), KEY.actionType(), KEY.scoreDomain(), sample, 0);
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
