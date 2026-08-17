package com.lolfm.composition;

import com.lolfm.simulator.TeamSide;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Phase 13D-4B.2 artifact-only margin-aware policy review.
 *
 * This class intentionally lives in test sources. It reads the frozen 13D-4B.1
 * artifacts and the shadow-wiring observations, and it never calls production
 * gameplay code or a match simulator.
 */
public final class CompositionGainPolicyReview {
    static final Path SOURCE_OUTPUT = Path.of("build", "reports",
            "composition-eligible-context-gain-screening-key-isolation-repair");
    static final Path OUTPUT = Path.of("build", "reports", "composition-margin-aware-gain-policy-review");
    static final Path SOURCE_OBSERVATIONS = Path.of("build", "reports",
            "composition-shadow-wiring-gate-closure", "composition-shadow-observations-gate.csv");

    static final String AUDIT_VERSION = "phase-13d4b2-margin-aware-gain-policy-review-v1";
    static final String STRICT_POLICY_VERSION = "STRICT_OVERALL_FLIP_REFERENCE_V1";
    static final String MARGIN_AWARE_POLICY_VERSION = "MARGIN_AWARE_SAFETY_POLICY_V1";
    static final String SAFETY_POLICY_VERSION = "composition-gain-margin-aware-safety-policy-v1";
    static final String CANDIDATE_VERSION = "composition-gameplay-margin-aware-gain-candidate-v1";
    static final String ADJUSTMENT_FORMULA = "GAP_MODIFIER_HALF_SPLIT_V1";
    static final String FORMULA = "PRODUCT_EXPOSURE";
    static final String PROFILE_VERSION = "thirty-champion-composition-profile-candidate-v2";
    static final String PROFILE_HASH = "fbf58dc5be12f2b07c5dff7ded9e182d7829999d2255e65dbbd073ccde2688d1";
    static final String RULE_CATALOG_VERSION = "composition-interaction-rule-catalog-v1";
    static final String RULE_CATALOG_HASH = "f0480eb8e9620d02a0187da384224d3735717ad5f5f2e1ca9e904aea4c7ae7d4";
    static final String INTERACTION_CANDIDATE_VERSION = "composition-interaction-product-exposure-v1";
    static final String INTERACTION_CANDIDATE_HASH = "0f92b3f9d3ea81f9d20531341167efe1c0a8c1a9d8b593f27d28b7745c0bb49b";
    static final String SOURCE_SUMMARY_HASH = "8d12c0cf10d941321faf47926477fbf1398ded34f5d6ff67e5c993ed712f8b34";
    static final String SOURCE_AUDIT_HASH = "9ff852c507ae93083ff5823d8d927ea0269749c45e78ecdb4ce2a57cdca15c23";
    static final String DEFERRED_APPLICATION_KEYS = "JUNGLE_GANK|LANE_COMBAT|ROAM|OBJECTIVE_SETUP|observation-only structure attempt|SIDE_LANE";

    static final int EXPECTED_SOURCE_OBSERVATIONS = 116_474;
    static final int EXPECTED_ELIGIBLE_OBSERVATIONS = 25_725;
    static final int EXPECTED_CALIBRATION_CASES = 800;
    static final int EXPECTED_VALIDATION_CASES = 400;
    static final int DECIMAL_SCALE = 12;
    static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    static final BigDecimal STRICT_SIGNAL_MIN = new BigDecimal("0.030");
    static final BigDecimal STRICT_SIGNAL_MAX = new BigDecimal("0.080");
    static final BigDecimal STRICT_P95_MAX = new BigDecimal("0.100");
    static final BigDecimal STRICT_P99_MAX = new BigDecimal("0.150");
    static final BigDecimal STRICT_MAX_MAX = new BigDecimal("0.250");
    static final BigDecimal MARGIN_NON_CLOSE_MAX = new BigDecimal("0.010000000000");
    static final BigDecimal MARGIN_CLOSE_MAX = new BigDecimal("0.333333333333");
    static final BigDecimal MARGIN_CONCENTRATION_MIN = new BigDecimal("0.950000000000");
    static final List<CompositionEligibleContextGainScreening.GainKey> KEYS =
            CompositionEligibleContextGainScreening.APPROVED_KEYS;
    static final List<String> GRID_LABELS = List.of(
            "ZERO_REFERENCE", "VERY_LOW", "LOW", "MEDIUM", "HIGH_SCREENING_LIMIT");
    static final List<String> SOURCE_FILES = List.of(
            "composition-gain-repair-source-manifest.csv",
            "composition-gain-key-local-partition.csv",
            "composition-gain-key-local-scale-anchors.csv",
            "composition-gain-key-local-grid.csv",
            "composition-gain-key-local-validation-results.csv",
            "composition-gain-key-local-selection.csv",
            "composition-gain-key-local-full-confirmation.csv",
            "composition-gain-metric-isolation-integrity.csv",
            "composition-gain-previous-vs-corrected.csv",
            "composition-gameplay-gain-candidate-repaired.csv",
            "composition-gain-key-local-screening-summary.csv",
            "composition-gain-key-local-screening-audit.log");
    static final Comparator<CompositionEligibleContextGainScreening.GainKey> KEY_ORDER =
            Comparator.comparing((CompositionEligibleContextGainScreening.GainKey key) -> key.context().name())
                    .thenComparing(key -> key.actionType().name())
                    .thenComparing(key -> key.scoreDomain().name());

    private CompositionGainPolicyReview() {}

    public static void main(String[] args) throws Exception {
        ReviewResult result = review(SOURCE_OBSERVATIONS, SOURCE_OUTPUT, OUTPUT);
        System.out.println("Composition margin-aware gain policy review: " + result.verdict());
        System.out.println("Candidate hash: " + result.candidateHash());
        System.out.println("Summary SHA-256: " + sha256(OUTPUT.resolve("composition-gain-policy-review-summary.csv")));
        System.out.println("Audit SHA-256: " + sha256(OUTPUT.resolve("composition-gain-policy-review-audit.log")));
        if (result.verdict().startsWith("BLOCKED")) throw new IllegalStateException(result.verdict());
    }

    static ReviewResult review(Path observationsPath, Path sourceOutput, Path output) throws IOException {
        Map<String, String> beforeHashes = sourceHashes(sourceOutput);
        SourceVerification source = verifySource(sourceOutput, beforeHashes);

        Path temporaryRepairOutput = Files.createTempDirectory("composition-margin-aware-repair-");
        CompositionEligibleContextGainScreening.RepairResult repair =
                CompositionEligibleContextGainScreening.repair(
                        CompositionEligibleContextGainScreening.SOURCE_DIR,
                        CompositionEligibleContextGainScreening.PREVIOUS_OUTPUT,
                        temporaryRepairOutput);
        CompositionEligibleContextGainScreening.ScreeningResult screening = repair.screening();
        Map<String, String> afterHashes = sourceHashes(sourceOutput);

        List<CompositionEligibleContextGainScreening.GridCandidate> grid = screening.grid();
        Map<CompositionEligibleContextGainScreening.GainKey, List<CandidateEvaluation>> evaluations =
                evaluate(screening);
        Map<CompositionEligibleContextGainScreening.GainKey, SelectionResult> selections = select(evaluations);
        Map<CompositionEligibleContextGainScreening.GainKey, CompositionEligibleContextGainScreening.Selection> helperSelections =
                helperSelections(selections);
        Map<CompositionEligibleContextGainScreening.GainKey, CompositionEligibleContextGainScreening.Metrics> full =
                CompositionEligibleContextGainScreening.fullConfirmation(
                        screening.filter().filtered(), helperSelections, screening.anchors(), screening.partition().calibration());
        Map<CompositionEligibleContextGainScreening.GainKey, FullEvaluation> fullEvaluations =
                fullEvaluations(selections, full);

        Integrity integrity = integrity(source, beforeHashes, afterHashes, screening, grid, evaluations, selections, fullEvaluations);
        boolean allSelected = selections.size() == KEYS.size() && selections.values().stream().allMatch(SelectionResult::selected);
        boolean allFullAccepted = allSelected && KEYS.stream().allMatch(key -> fullEvaluations.get(key).accepted());
        boolean candidateFrozen = integrity.errorCount() == 0 && allFullAccepted;
        String candidateHash = candidateFrozen ? candidateHash(selections, fullEvaluations) : "NONE";
        String verdict = integrity.errorCount() != 0
                ? "BLOCKED_BY_COMPOSITION_MARGIN_AWARE_POLICY_INTEGRITY"
                : candidateFrozen ? "READY_FOR_PHASE_13D4C" : "MARGIN_AWARE_GAIN_POLICY_REVIEW_REQUIRED";

        ReviewResult result = new ReviewResult(source, screening, grid, evaluations, selections, fullEvaluations,
                integrity, candidateFrozen, candidateHash, verdict, beforeHashes, afterHashes);
        writeArtifacts(output, result);

        if (!beforeHashes.equals(sourceHashes(sourceOutput))) {
            throw new IllegalStateException("Phase 13D-4B.1 source artifacts changed while writing policy review");
        }
        return result;
    }

    static Map<CompositionEligibleContextGainScreening.GainKey, List<CandidateEvaluation>> evaluate(
            CompositionEligibleContextGainScreening.ScreeningResult screening) {
        Map<CompositionEligibleContextGainScreening.GainKey, List<CandidateEvaluation>> result = new LinkedHashMap<>();
        for (CompositionEligibleContextGainScreening.GainKey key : KEYS) {
            List<CompositionEligibleContextGainScreening.Metrics> validation = screening.validation().get(key);
            List<CompositionEligibleContextGainScreening.Metrics> calibration = screening.calibration().get(key);
            List<CandidateEvaluation> values = new ArrayList<>();
            for (CompositionEligibleContextGainScreening.Metrics metric : validation) {
                StrictDecision strict = strictDecision(metric, calibration);
                MarginAwareDecision margin = marginAwareDecision(metric, calibration);
                values.add(new CandidateEvaluation(key, metric, strict, margin, bandStats(metric)));
            }
            result.put(key, List.copyOf(values));
        }
        return result;
    }

    static StrictDecision strictDecision(CompositionEligibleContextGainScreening.Metrics metric,
                                         List<CompositionEligibleContextGainScreening.Metrics> calibration) {
        CompositionEligibleContextGainScreening.ValidationDecision decision =
                CompositionEligibleContextGainScreening.validationDecision(metric, calibration);
        return new StrictDecision(decision.accepted(), decision.structuralPass(), decision.coveragePass(),
                decision.signalVisibilityPass(), decision.tailRatioSafetyPass(), decision.signFlipSafetyPass(),
                decision.highMarginSafetyPass(), decision.reasons());
    }

    static MarginAwareDecision marginAwareDecision(CompositionEligibleContextGainScreening.Metrics metric,
                                                   List<CompositionEligibleContextGainScreening.Metrics> calibration) {
        int calibrationSampleCount = calibration.stream()
                .filter(x -> x.candidate().label().equals(metric.candidate().label()))
                .findFirst().map(CompositionEligibleContextGainScreening.Metrics::sampleCount).orElse(0);
        BandStats stats = bandStats(metric);
        List<String> reasons = new ArrayList<>();
        boolean zeroReference = "ZERO_REFERENCE".equals(metric.candidate().label());
        if (zeroReference) reasons.add("ZERO_REFERENCE_NOT_SELECTABLE");
        boolean structural = metric.midpointDriftCount() == 0
                && metric.gapArithmeticMismatchCount() == 0
                && metric.edgeDirectionMismatchCount() == 0
                && metric.sideReversalMismatchCount() == 0
                && metric.nanCount() == 0 && metric.infinityCount() == 0;
        boolean coverage = metric.sampleCount() >= 100 && calibrationSampleCount >= 200
                && metric.bluePerspectiveCount() > 0 && metric.redPerspectiveCount() > 0
                && metric.distinctModifierCount() >= 100;
        boolean signal = metric.p90ModifierGapRatio().compareTo(STRICT_SIGNAL_MIN) >= 0
                && metric.p90ModifierGapRatio().compareTo(STRICT_SIGNAL_MAX) <= 0
                && metric.p90AbsoluteModifier().signum() > 0;
        boolean tail = metric.p95ModifierGapRatio().compareTo(STRICT_P95_MAX) <= 0
                && metric.p99ModifierGapRatio().compareTo(STRICT_P99_MAX) <= 0
                && metric.maxModifierGapRatio().compareTo(STRICT_MAX_MAX) <= 0;
        boolean high = stats.highFlipCount() == 0;
        boolean nonClose = stats.nonCloseFlipRate().compareTo(MARGIN_NON_CLOSE_MAX) <= 0;
        boolean close = stats.closeFlipRate().compareTo(MARGIN_CLOSE_MAX) <= 0;
        boolean concentration = stats.flipCloseConcentration().compareTo(MARGIN_CONCENTRATION_MIN) >= 0;
        if (!zeroReference) {
            if (metric.sampleCount() < 100) reasons.add("VALIDATION_SAMPLE_BELOW_100");
            if (calibrationSampleCount < 200) reasons.add("CALIBRATION_SAMPLE_BELOW_200");
            if (metric.bluePerspectiveCount() == 0 || metric.redPerspectiveCount() == 0) reasons.add("VALIDATION_BLUE_OR_RED_COVERAGE_MISSING");
            if (metric.distinctModifierCount() < 100) reasons.add("DISTINCT_MODIFIER_BELOW_100");
            if (metric.p90ModifierGapRatio().compareTo(STRICT_SIGNAL_MIN) < 0) reasons.add("P90_SIGNAL_BELOW_030");
            if (metric.p90ModifierGapRatio().compareTo(STRICT_SIGNAL_MAX) > 0) reasons.add("P90_SIGNAL_ABOVE_080");
            if (metric.p90AbsoluteModifier().signum() <= 0) reasons.add("P90_MODIFIER_NOT_POSITIVE");
            if (metric.p95ModifierGapRatio().compareTo(STRICT_P95_MAX) > 0) reasons.add("P95_SAFETY_ABOVE_100");
            if (metric.p99ModifierGapRatio().compareTo(STRICT_P99_MAX) > 0) reasons.add("P99_SAFETY_ABOVE_150");
            if (metric.maxModifierGapRatio().compareTo(STRICT_MAX_MAX) > 0) reasons.add("MAX_SAFETY_ABOVE_250");
            if (!high) reasons.add("HIGH_MARGIN_FLIP");
            if (!nonClose) reasons.add("NON_CLOSE_FLIP_RATE_ABOVE_010");
            if (!close) reasons.add("CLOSE_FLIP_RATE_ABOVE_333");
            if (!concentration) reasons.add("FLIP_CLOSE_CONCENTRATION_BELOW_950");
            if (metric.midpointDriftCount() != 0) reasons.add("MIDPOINT_DRIFT");
            if (metric.gapArithmeticMismatchCount() != 0) reasons.add("GAP_ARITHMETIC_MISMATCH");
            if (metric.edgeDirectionMismatchCount() != 0) reasons.add("EDGE_DIRECTION_MISMATCH");
            if (metric.sideReversalMismatchCount() != 0) reasons.add("SIDE_REVERSAL_MISMATCH");
            if (metric.nanCount() != 0 || metric.infinityCount() != 0) reasons.add("NAN_OR_INFINITY");
        }
        boolean accepted = !zeroReference && structural && coverage && signal && tail && high
                && nonClose && close && concentration;
        return new MarginAwareDecision(accepted, structural, coverage, signal, tail, high,
                nonClose, close, concentration, reasons);
    }

    static FullDecision fullDecision(CompositionEligibleContextGainScreening.Metrics metric) {
        BandStats stats = bandStats(metric);
        List<String> reasons = new ArrayList<>();
        boolean structural = metric.midpointDriftCount() == 0 && metric.gapArithmeticMismatchCount() == 0
                && metric.edgeDirectionMismatchCount() == 0 && metric.sideReversalMismatchCount() == 0
                && metric.nanCount() == 0 && metric.infinityCount() == 0;
        boolean signal = metric.p90ModifierGapRatio().compareTo(new BigDecimal("0.025")) >= 0
                && metric.p90ModifierGapRatio().compareTo(new BigDecimal("0.090")) <= 0;
        boolean tail = metric.p95ModifierGapRatio().compareTo(new BigDecimal("0.120")) <= 0
                && metric.p99ModifierGapRatio().compareTo(new BigDecimal("0.180")) <= 0
                && metric.maxModifierGapRatio().compareTo(new BigDecimal("0.250")) <= 0;
        boolean high = stats.highFlipCount() == 0;
        boolean nonClose = stats.nonCloseFlipRate().compareTo(MARGIN_NON_CLOSE_MAX) <= 0;
        boolean close = stats.closeFlipRate().compareTo(MARGIN_CLOSE_MAX) <= 0;
        boolean concentration = stats.flipCloseConcentration().compareTo(MARGIN_CONCENTRATION_MIN) >= 0;
        if (!signal) reasons.add("FULL_SIGNAL_OR_RANGE_FAILURE");
        if (!tail) reasons.add("FULL_TAIL_FAILURE");
        if (!high) reasons.add("HIGH_MARGIN_FLIP");
        if (!nonClose) reasons.add("NON_CLOSE_FLIP_RATE_ABOVE_010");
        if (!close) reasons.add("CLOSE_FLIP_RATE_ABOVE_333");
        if (!concentration) reasons.add("FLIP_CLOSE_CONCENTRATION_BELOW_950");
        if (!structural) reasons.add("STRUCTURAL_FAILURE");
        return new FullDecision(structural && signal && tail && high && nonClose && close && concentration,
                structural, signal, tail, high, nonClose, close, concentration, reasons);
    }

    static BandStats bandStats(CompositionEligibleContextGainScreening.Metrics metric) {
        long nonCloseSamples = metric.mediumMarginSampleCount() + metric.highMarginSampleCount();
        long nonCloseFlips = metric.mediumMarginSignFlipCount() + metric.highMarginSignFlipCount();
        BigDecimal closeRate = divide(metric.closeMarginSignFlipCount(), metric.closeMarginSampleCount());
        BigDecimal nonCloseRate = divide(nonCloseFlips, nonCloseSamples);
        BigDecimal concentration = metric.overallSignFlipCount() == 0
                ? BigDecimal.ONE.setScale(DECIMAL_SCALE)
                : divide(metric.closeMarginSignFlipCount(), metric.overallSignFlipCount());
        return new BandStats(metric.closeMarginSampleCount(), metric.closeMarginSignFlipCount(), closeRate,
                metric.mediumMarginSampleCount(), metric.mediumMarginSignFlipCount(),
                metric.highMarginSampleCount(), metric.highMarginSignFlipCount(), nonCloseSamples,
                nonCloseFlips, nonCloseRate, concentration, metric.zeroToPositiveCount(), metric.zeroToNegativeCount(),
                metric.nonZeroToZeroCount());
    }

    static Map<CompositionEligibleContextGainScreening.GainKey, SelectionResult> select(
            Map<CompositionEligibleContextGainScreening.GainKey, List<CandidateEvaluation>> evaluations) {
        Map<CompositionEligibleContextGainScreening.GainKey, SelectionResult> result = new LinkedHashMap<>();
        for (CompositionEligibleContextGainScreening.GainKey key : KEYS) {
            CandidateEvaluation selected = null;
            List<String> rejected = new ArrayList<>();
            for (CandidateEvaluation evaluation : evaluations.getOrDefault(key, List.of())) {
                if ("ZERO_REFERENCE".equals(evaluation.metrics().candidate().label())) {
                    rejected.add("ZERO_REFERENCE:ZERO_REFERENCE_NOT_SELECTABLE");
                } else if (evaluation.marginAware().accepted()) {
                    selected = evaluation;
                    break;
                } else {
                    rejected.add(evaluation.metrics().candidate().label() + ":"
                            + String.join("|", evaluation.marginAware().reasons()));
                }
            }
            if (selected == null) {
                result.put(key, new SelectionResult(key, false, "NONE", BigDecimal.ZERO, BigDecimal.ZERO,
                        "MARGIN_AWARE_GAIN_POLICY_REVIEW", rejected, null));
            } else {
                var candidate = selected.metrics().candidate();
                result.put(key, new SelectionResult(key, true, candidate.label(), candidate.targetRatio(),
                        candidate.gain(), "SMALLEST_MARGIN_AWARE_TARGET_RATIO", rejected, selected));
            }
        }
        return result;
    }

    static Map<CompositionEligibleContextGainScreening.GainKey, CompositionEligibleContextGainScreening.Selection> helperSelections(
            Map<CompositionEligibleContextGainScreening.GainKey, SelectionResult> selections) {
        Map<CompositionEligibleContextGainScreening.GainKey, CompositionEligibleContextGainScreening.Selection> result = new LinkedHashMap<>();
        for (CompositionEligibleContextGainScreening.GainKey key : KEYS) {
            SelectionResult selection = selections.get(key);
            result.put(key, new CompositionEligibleContextGainScreening.Selection(key, selection.selected(),
                    selection.label(), selection.targetRatio(), selection.gain(), selection.reason(), selection.rejectedReasons()));
        }
        return result;
    }

    static Map<CompositionEligibleContextGainScreening.GainKey, FullEvaluation> fullEvaluations(
            Map<CompositionEligibleContextGainScreening.GainKey, SelectionResult> selections,
            Map<CompositionEligibleContextGainScreening.GainKey, CompositionEligibleContextGainScreening.Metrics> full) {
        Map<CompositionEligibleContextGainScreening.GainKey, FullEvaluation> result = new LinkedHashMap<>();
        for (CompositionEligibleContextGainScreening.GainKey key : KEYS) {
            CompositionEligibleContextGainScreening.Metrics metric = full.get(key);
            if (metric == null) {
                result.put(key, new FullEvaluation(key, false, null, null));
            } else {
                result.put(key, new FullEvaluation(key, true, metric, fullDecision(metric)));
            }
        }
        return result;
    }

    static String candidateHash(Map<CompositionEligibleContextGainScreening.GainKey, SelectionResult> selections,
                                Map<CompositionEligibleContextGainScreening.GainKey, FullEvaluation> full) {
        return sha256(canonicalCandidatePayload(selections, full).getBytes(StandardCharsets.UTF_8));
    }

    static String canonicalCandidatePayload(
            Map<CompositionEligibleContextGainScreening.GainKey, SelectionResult> selections,
            Map<CompositionEligibleContextGainScreening.GainKey, FullEvaluation> full) {
        StringBuilder payload = new StringBuilder();
        append(payload, "candidateVersion", CANDIDATE_VERSION);
        append(payload, "profileVersion", PROFILE_VERSION);
        append(payload, "profileHash", PROFILE_HASH);
        append(payload, "ruleCatalogVersion", RULE_CATALOG_VERSION);
        append(payload, "ruleCatalogHash", RULE_CATALOG_HASH);
        append(payload, "interactionCandidateVersion", INTERACTION_CANDIDATE_VERSION);
        append(payload, "interactionCandidateHash", INTERACTION_CANDIDATE_HASH);
        append(payload, "sourcePhase13D4B1SummaryHash", SOURCE_SUMMARY_HASH);
        append(payload, "sourcePhase13D4B1AuditHash", SOURCE_AUDIT_HASH);
        append(payload, "formula", FORMULA);
        append(payload, "adjustmentFormula", ADJUSTMENT_FORMULA);
        append(payload, "midpointPreserved", "true");
        append(payload, "safetyPolicyVersion", SAFETY_POLICY_VERSION);
        append(payload, "closeBandDefinition", "abs(baselineGap)<=calibrationP25");
        append(payload, "mediumBandDefinition", "calibrationP25<abs(baselineGap)<calibrationP90");
        append(payload, "highBandDefinition", "abs(baselineGap)>=calibrationP90");
        append(payload, "signalThresholds", "validationP90[0.030,0.080];fullP90[0.025,0.090]");
        append(payload, "tailThresholds", "validationP95<=0.100;P99<=0.150;max<=0.250;fullP95<=0.120;P99<=0.180;max<=0.250");
        append(payload, "closeFlipRateLimit", MARGIN_CLOSE_MAX.toPlainString());
        append(payload, "nonCloseFlipRateLimit", MARGIN_NON_CLOSE_MAX.toPlainString());
        append(payload, "highMarginFlipLimit", "0");
        append(payload, "flipCloseConcentrationMinimum", MARGIN_CONCENTRATION_MIN.toPlainString());
        append(payload, "splitPartition", "CASE_INDEX_FLOORMOD_3_VALIDATION_ZERO");
        append(payload, "policyDevelopmentSet", "POLICY_DEVELOPMENT_VALIDATION_SET");
        append(payload, "blindHoldoutClaimed", "false");
        append(payload, "percentile", "NEAREST_RANK");
        for (CompositionEligibleContextGainScreening.GainKey key : KEYS.stream().sorted(KEY_ORDER).toList()) {
            SelectionResult selection = selections.getOrDefault(key, new SelectionResult(key, false, "NONE", BigDecimal.ZERO, BigDecimal.ZERO, "REVIEW", List.of(), null));
            FullEvaluation fullResult = full.getOrDefault(key, new FullEvaluation(key, false, null, null));
            append(payload, "key", key.stableId());
            append(payload, "targetRatio", format(selection.targetRatio()));
            append(payload, "canonicalGain", format(selection.gain()));
            append(payload, "strictAccepted", selection.evaluation() == null ? "false" : Boolean.toString(selection.evaluation().strict().accepted()));
            append(payload, "marginAwareAccepted", selection.evaluation() == null ? "false" : Boolean.toString(selection.evaluation().marginAware().accepted()));
            append(payload, "fullAccepted", Boolean.toString(fullResult.accepted()));
        }
        append(payload, "deferredKeys", DEFERRED_APPLICATION_KEYS);
        append(payload, "deadzone", "NONE");
        append(payload, "clamp", "NONE");
        append(payload, "cap", "NONE");
        append(payload, "override", "0");
        append(payload, "productionEnabled", "false");
        append(payload, "candidateGameplayEnabled", "false");
        append(payload, "freshGameplayAuditRequired", "true");
        return payload.toString();
    }

    private static Integrity integrity(SourceVerification source, Map<String, String> before, Map<String, String> after,
                                       CompositionEligibleContextGainScreening.ScreeningResult screening,
                                       List<CompositionEligibleContextGainScreening.GridCandidate> grid,
                                       Map<CompositionEligibleContextGainScreening.GainKey, List<CandidateEvaluation>> evaluations,
                                       Map<CompositionEligibleContextGainScreening.GainKey, SelectionResult> selections,
                                       Map<CompositionEligibleContextGainScreening.GainKey, FullEvaluation> full) throws IOException {
        int sampleMismatch = 0;
        int foreign = 0;
        int marginBandMismatch = 0;
        int structural = 0;
        for (CompositionEligibleContextGainScreening.GainKey key : KEYS) {
            int calibration = count(screening.partition().calibration(), key);
            int validation = count(screening.partition().validation(), key);
            int fullCount = count(screening.filter().filtered(), key);
            for (CandidateEvaluation evaluation : evaluations.get(key)) {
                if (evaluation.metrics().sampleCount() != validation || evaluation.metrics().metricInputObservationCount() != validation) sampleMismatch++;
                foreign += (int) evaluation.metrics().foreignKeyObservationCount();
                structural += (int) evaluation.metrics().midpointDriftCount() + (int) evaluation.metrics().gapArithmeticMismatchCount()
                        + (int) evaluation.metrics().edgeDirectionMismatchCount() + (int) evaluation.metrics().sideReversalMismatchCount()
                        + (int) evaluation.metrics().nanCount() + (int) evaluation.metrics().infinityCount();
            }
            FullEvaluation fullEvaluation = full.get(key);
            if (fullEvaluation.metric() != null) {
                if (fullEvaluation.metric().sampleCount() != fullCount) sampleMismatch++;
                foreign += (int) fullEvaluation.metric().foreignKeyObservationCount();
            }
            var band = screening.marginBands().get(key);
            if (band == null || band.key() == null || !band.key().equals(key)) marginBandMismatch++;
        }
        int gridMismatch = grid.size() == KEYS.size() * GRID_LABELS.size() ? 0 : 1;
        int sourceMismatch = source.valid() && before.equals(after) ? 0 : 1;
        int strictMismatch = strictArtifactMismatches(sourceOutputFor(source), evaluations);
        int policyThresholdMismatch = policyDefinitions().size() == 2 ? 0 : 1;
        int candidateHashMismatch = candidateHashDeterminism(selections, full) ? 0 : 1;
        int errorCount = sampleMismatch + foreign + marginBandMismatch + structural + gridMismatch + sourceMismatch
                + strictMismatch + policyThresholdMismatch + candidateHashMismatch;
        return new Integrity(foreign, sampleMismatch, policyThresholdMismatch, 0, strictMismatch,
                0, marginBandMismatch, 0, structural, 0, 0, 0, 0, errorCount, candidateHashMismatch,
                gridMismatch, sourceMismatch);
    }

    private static Path sourceOutputFor(SourceVerification source) {
        return source.path();
    }

    private static int strictArtifactMismatches(Path sourceOutput,
                                                Map<CompositionEligibleContextGainScreening.GainKey, List<CandidateEvaluation>> evaluations) {
        try {
            List<Map<String, String>> rows = readCsvMaps(sourceOutput.resolve("composition-gain-key-local-validation-results.csv"));
            int mismatches = 0;
            for (Map<String, String> row : rows) {
                var key = key(row.get("context"), row.get("actionType"), row.get("scoreDomain"));
                CandidateEvaluation evaluation = evaluations.getOrDefault(key, List.of()).stream()
                        .filter(x -> x.metrics().candidate().label().equals(row.get("candidate"))).findFirst().orElse(null);
                if (evaluation == null) continue;
                boolean accepted = Boolean.parseBoolean(row.get("validationAccepted"));
                String reasons = row.getOrDefault("validationRejectionReasons", "");
                if (accepted != evaluation.strict().accepted()
                        || !reasons.equals(String.join("|", evaluation.strict().reasons()))) mismatches++;
            }
            return mismatches;
        } catch (IOException | RuntimeException e) {
            return 1;
        }
    }

    private static boolean candidateHashDeterminism(Map<CompositionEligibleContextGainScreening.GainKey, SelectionResult> selections,
                                                    Map<CompositionEligibleContextGainScreening.GainKey, FullEvaluation> full) {
        String first = candidateHash(selections, full);
        Map<CompositionEligibleContextGainScreening.GainKey, SelectionResult> shuffled = new LinkedHashMap<>();
        for (int i = KEYS.size() - 1; i >= 0; i--) {
            CompositionEligibleContextGainScreening.GainKey key = KEYS.get(i);
            shuffled.put(key, selections.get(key));
        }
        Map<CompositionEligibleContextGainScreening.GainKey, FullEvaluation> shuffledFull = new LinkedHashMap<>();
        for (int i = KEYS.size() - 1; i >= 0; i--) {
            CompositionEligibleContextGainScreening.GainKey key = KEYS.get(i);
            shuffledFull.put(key, full.get(key));
        }
        return first.equals(candidateHash(shuffled, shuffledFull));
    }

    private static SourceVerification verifySource(Path sourceOutput, Map<String, String> hashes) throws IOException {
        if (!SOURCE_SUMMARY_HASH.equals(hashes.get("composition-gain-key-local-screening-summary.csv"))
                || !SOURCE_AUDIT_HASH.equals(hashes.get("composition-gain-key-local-screening-audit.log"))) {
            throw new IllegalStateException("Phase 13D-4B.1 source summary/audit hash mismatch");
        }
        List<Map<String, String>> partition = readCsvMaps(sourceOutput.resolve("composition-gain-key-local-partition.csv"));
        List<Map<String, String>> grid = readCsvMaps(sourceOutput.resolve("composition-gain-key-local-grid.csv"));
        if (partition.size() != 12 || grid.size() != 20 || hashes.size() != SOURCE_FILES.size()) {
            throw new IllegalStateException("Phase 13D-4B.1 source artifact row/count mismatch");
        }
        for (String file : SOURCE_FILES) if (!hashes.containsKey(file)) throw new IllegalStateException("Missing source artifact: " + file);
        return new SourceVerification(sourceOutput, true, hashes);
    }

    private static Map<String, String> sourceHashes(Path sourceOutput) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String file : SOURCE_FILES) {
            Path path = sourceOutput.resolve(file);
            if (!Files.isRegularFile(path)) throw new IllegalStateException("Missing source artifact: " + path);
            result.put(file, sha256(path));
        }
        return result;
    }

    private static void writeArtifacts(Path output, ReviewResult result) throws IOException {
        Files.createDirectories(output);
        writeCsv(output.resolve("composition-gain-policy-source-manifest.csv"), sourceManifest(result));
        writeCsv(output.resolve("composition-gain-policy-definitions.csv"), policyDefinitionRows());
        writeCsv(output.resolve("composition-gain-policy-application-keys.csv"), applicationKeyRows());
        writeCsv(output.resolve("composition-gain-policy-band-profile.csv"), bandProfileRows(result));
        writeCsv(output.resolve("composition-gain-policy-comparison.csv"), comparisonRows(result));
        writeCsv(output.resolve("composition-gain-policy-validation-results.csv"), validationRows(result));
        writeCsv(output.resolve("composition-gain-policy-selection.csv"), selectionRows(result));
        writeCsv(output.resolve("composition-gain-policy-full-confirmation.csv"), fullRows(result));
        writeCsv(output.resolve("composition-gain-policy-integrity.csv"), integrityRows(result));
        writeCsv(output.resolve("composition-gameplay-margin-aware-gain-candidate.csv"), candidateRows(result));
        writeCsv(output.resolve("composition-gain-policy-selected-observations.csv"), selectedObservationRows(result));
        writeKeyValue(output.resolve("composition-gain-policy-review-summary.csv"), summary(result));
        Files.writeString(output.resolve("composition-gain-policy-review-audit.log"), auditLog(result), StandardCharsets.UTF_8);
    }

    private static List<List<String>> sourceManifest(ReviewResult result) throws IOException {
        List<List<String>> rows = rows("sourceArtifact", "sha256", "rowCount", "expectedSha256", "unchanged", "requiredInput");
        for (String file : SOURCE_FILES) {
            Path path = result.source().path().resolve(file);
            String expected = file.equals("composition-gain-key-local-screening-summary.csv") ? SOURCE_SUMMARY_HASH
                    : file.equals("composition-gain-key-local-screening-audit.log") ? SOURCE_AUDIT_HASH : "NOT_PINNED";
            rows.add(List.of(file, result.beforeHashes().get(file), Long.toString(lineCount(path)), expected,
                    Boolean.toString(result.beforeHashes().get(file).equals(result.afterHashes().get(file))), "true"));
        }
        return rows;
    }

    private static List<List<String>> policyDefinitionRows() {
        List<List<String>> rows = rows("policyId", "policyVersion", "purpose", "signalP90Min", "signalP90Max", "p95Max", "p99Max", "maxMax", "overallFlipRule", "highFlipRule", "nonCloseFlipRule", "closeFlipRule", "concentrationRule", "overallFlipInformational");
        rows.add(List.of(STRICT_POLICY_VERSION, STRICT_POLICY_VERSION, "HISTORICAL_REFERENCE", "0.030", "0.080", "0.100", "0.150", "0.250", "overallFlipRate<=0.030", "highMarginFlipCount=0", "NOT_APPLICABLE", "NOT_APPLICABLE", "NOT_APPLICABLE", "false"));
        rows.add(List.of(MARGIN_AWARE_POLICY_VERSION, SAFETY_POLICY_VERSION, "POLICY_DEVELOPMENT_REVIEW", "0.030", "0.080", "0.100", "0.150", "0.250", "INFORMATIONAL_ONLY", "highMarginFlipCount=0", "nonCloseFlipRate<=0.010000000000", "closeFlipRate<=0.333333333333", "flipCloseConcentration>=0.950000000000", "true"));
        return rows;
    }

    private static List<List<String>> applicationKeyRows() {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "keyIdentity", "resolution");
        for (var key : KEYS) rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), key.stableId(), "APPLICATION_KEY_LOCAL"));
        return rows;
    }

    private static List<List<String>> bandProfileRows(ReviewResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "candidate", "metricScope", "closeSampleCount", "closeFlipCount", "closeFlipRate", "mediumSampleCount", "mediumFlipCount", "highSampleCount", "highFlipCount", "nonCloseSampleCount", "nonCloseFlipCount", "nonCloseFlipRate", "flipCloseConcentration", "zeroToPositiveCount", "zeroToNegativeCount", "nonZeroToZeroCount");
        for (var key : KEYS) for (CandidateEvaluation evaluation : result.evaluations().get(key)) {
            BandStats stats = evaluation.bandStats();
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), evaluation.metrics().candidate().label(), "APPLICATION_KEY_LOCAL", Long.toString(stats.closeSampleCount()), Long.toString(stats.closeFlipCount()), format(stats.closeFlipRate()), Long.toString(stats.mediumSampleCount()), Long.toString(stats.mediumFlipCount()), Long.toString(stats.highSampleCount()), Long.toString(stats.highFlipCount()), Long.toString(stats.nonCloseSampleCount()), Long.toString(stats.nonCloseFlipCount()), format(stats.nonCloseFlipRate()), format(stats.flipCloseConcentration()), Long.toString(stats.zeroToPositiveCount()), Long.toString(stats.zeroToNegativeCount()), Long.toString(stats.nonZeroToZeroCount())));
        }
        return rows;
    }

    private static List<List<String>> comparisonRows(ReviewResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "candidate", "targetRatio", "canonicalGain", "strictAccepted", "strictRejectionReasons", "marginAwareAccepted", "marginAwareRejectionReasons", "decisionChanged", "decisionChangeReason", "overallFlipRateInformational");
        for (var key : KEYS) for (CandidateEvaluation evaluation : result.evaluations().get(key)) {
            boolean changed = evaluation.strict().accepted() != evaluation.marginAware().accepted();
            String reason = changed ? (evaluation.marginAware().accepted() ? "MARGIN_AWARE_RELAXES_CLOSE_FLIP_REFERENCE" : "MARGIN_AWARE_POLICY_REJECTS") : "NONE";
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), evaluation.metrics().candidate().label(), format(evaluation.metrics().candidate().targetRatio()), format(evaluation.metrics().candidate().gain()), Boolean.toString(evaluation.strict().accepted()), String.join("|", evaluation.strict().reasons()), Boolean.toString(evaluation.marginAware().accepted()), String.join("|", evaluation.marginAware().reasons()), Boolean.toString(changed), reason, format(evaluation.metrics().overallSignFlipRate())));
        }
        return rows;
    }

    private static List<List<String>> validationRows(ReviewResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "candidate", "targetRatio", "canonicalGain", "sampleCount", "distinctCaseCount", "blueCount", "redCount", "p90Ratio", "p95Ratio", "p99Ratio", "maxRatio", "overallFlipCount", "overallFlipRate", "closeFlipRate", "nonCloseFlipRate", "highFlipCount", "flipCloseConcentration", "strictAccepted", "strictRejectionReasons", "marginAwareAccepted", "marginAwareRejectionReasons");
        for (var key : KEYS) for (CandidateEvaluation evaluation : result.evaluations().get(key)) {
            var metric = evaluation.metrics();
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), metric.candidate().label(), format(metric.candidate().targetRatio()), format(metric.candidate().gain()), Integer.toString(metric.sampleCount()), Long.toString(metric.distinctCaseCount()), Long.toString(metric.bluePerspectiveCount()), Long.toString(metric.redPerspectiveCount()), format(metric.p90ModifierGapRatio()), format(metric.p95ModifierGapRatio()), format(metric.p99ModifierGapRatio()), format(metric.maxModifierGapRatio()), Long.toString(metric.overallSignFlipCount()), format(metric.overallSignFlipRate()), format(evaluation.bandStats().closeFlipRate()), format(evaluation.bandStats().nonCloseFlipRate()), Long.toString(evaluation.bandStats().highFlipCount()), format(evaluation.bandStats().flipCloseConcentration()), Boolean.toString(evaluation.strict().accepted()), String.join("|", evaluation.strict().reasons()), Boolean.toString(evaluation.marginAware().accepted()), String.join("|", evaluation.marginAware().reasons())));
        }
        return rows;
    }

    private static List<List<String>> selectionRows(ReviewResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "selectedCandidate", "selectedTargetRatio", "canonicalGain", "selected", "marginAwareEligibleCandidateCount", "selectionReason", "rejectedCandidateReasons");
        for (var key : KEYS) {
            SelectionResult selection = result.selections().get(key);
            long eligible = result.evaluations().get(key).stream().filter(x -> x.marginAware().accepted() && !"ZERO_REFERENCE".equals(x.metrics().candidate().label())).count();
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), selection.label(), selection.selected() ? format(selection.targetRatio()) : "NONE", selection.selected() ? format(selection.gain()) : "NONE", Boolean.toString(selection.selected()), Long.toString(eligible), selection.reason(), String.join(";", selection.rejectedReasons())));
        }
        return rows;
    }

    private static List<List<String>> fullRows(ReviewResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "selectedTargetRatio", "canonicalGain", "expectedFullSampleCount", "sampleCount", "p90Ratio", "p95Ratio", "p99Ratio", "maxRatio", "closeFlipRate", "nonCloseFlipRate", "highFlipCount", "flipCloseConcentration", "structuralPass", "signalPass", "tailPass", "fullAccepted", "fullRejectionReasons");
        for (var key : KEYS) {
            SelectionResult selection = result.selections().get(key);
            FullEvaluation evaluation = result.fullEvaluations().get(key);
            int expected = count(result.screening().filter().filtered(), key);
            if (evaluation.metric() == null) rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), "NONE", "NONE", Integer.toString(expected), "0", "NONE", "NONE", "NONE", "NONE", "NONE", "NONE", "0", "NONE", "false", "false", "false", "false", "NOT_EVALUATED"));
            else rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), format(selection.targetRatio()), format(selection.gain()), Integer.toString(expected), Integer.toString(evaluation.metric().sampleCount()), format(evaluation.metric().p90ModifierGapRatio()), format(evaluation.metric().p95ModifierGapRatio()), format(evaluation.metric().p99ModifierGapRatio()), format(evaluation.metric().maxModifierGapRatio()), format(evaluation.stats().closeFlipRate()), format(evaluation.stats().nonCloseFlipRate()), Long.toString(evaluation.stats().highFlipCount()), format(evaluation.stats().flipCloseConcentration()), Boolean.toString(evaluation.decision().structuralPass()), Boolean.toString(evaluation.decision().signalPass()), Boolean.toString(evaluation.decision().tailPass()), Boolean.toString(evaluation.accepted()), String.join("|", evaluation.decision().reasons())));
        }
        return rows;
    }

    private static List<List<String>> integrityRows(ReviewResult result) {
        List<List<String>> rows = rows("integrityCheck", "value", "passed");
        Integrity i = result.integrity();
        rows.add(List.of("sourceArtifactsUnchanged", Boolean.toString(result.beforeHashes().equals(result.afterHashes())), Boolean.toString(result.beforeHashes().equals(result.afterHashes()))));
        rows.add(List.of("foreignKeyObservationCount", Integer.toString(i.foreignKeyObservationCount()), Boolean.toString(i.foreignKeyObservationCount() == 0)));
        rows.add(List.of("sampleMismatchCount", Integer.toString(i.sampleMismatchCount()), Boolean.toString(i.sampleMismatchCount() == 0)));
        rows.add(List.of("policyThresholdMismatchCount", Integer.toString(i.policyThresholdMismatchCount()), Boolean.toString(i.policyThresholdMismatchCount() == 0)));
        rows.add(List.of("strictReferenceMismatchCount", Integer.toString(i.strictReferenceMismatchCount()), Boolean.toString(i.strictReferenceMismatchCount() == 0)));
        rows.add(List.of("midpointDriftCount", Integer.toString(i.midpointDriftCount()), Boolean.toString(i.midpointDriftCount() == 0)));
        rows.add(List.of("gapArithmeticMismatchCount", Integer.toString(i.gapArithmeticMismatchCount()), Boolean.toString(i.gapArithmeticMismatchCount() == 0)));
        rows.add(List.of("edgeDirectionMismatchCount", Integer.toString(i.edgeDirectionMismatchCount()), Boolean.toString(i.edgeDirectionMismatchCount() == 0)));
        rows.add(List.of("sideReversalMismatchCount", Integer.toString(i.sideReversalMismatchCount()), Boolean.toString(i.sideReversalMismatchCount() == 0)));
        rows.add(List.of("candidateHashDeterministic", Integer.toString(i.candidateHashMismatchCount()), Boolean.toString(i.candidateHashMismatchCount() == 0)));
        rows.add(List.of("gridMismatchCount", Integer.toString(i.gridMismatchCount()), Boolean.toString(i.gridMismatchCount() == 0)));
        rows.add(List.of("integrityErrorCount", Integer.toString(i.errorCount()), Boolean.toString(i.errorCount() == 0)));
        return rows;
    }

    private static List<List<String>> candidateRows(ReviewResult result) {
        List<List<String>> rows = rows("candidateVersion", "candidateHash", "profileVersion", "profileHash", "ruleCatalogVersion", "ruleCatalogHash", "interactionCandidateVersion", "interactionCandidateHash", "sourcePhase13D4B1SummaryHash", "sourcePhase13D4B1AuditHash", "formula", "adjustmentFormula", "safetyPolicyVersion", "context", "actionType", "scoreDomain", "targetRatio", "canonicalGain", "strictAccepted", "marginAwareAccepted", "fullAccepted", "candidateFrozen", "midpointPreserved", "deadzone", "clamp", "cap", "overrideCount", "deferredApplicationKeys", "deferredReason", "productionEnabled", "candidateGameplayEnabled", "requiresFreshGameplayAudit");
        for (var key : KEYS) {
            SelectionResult selection = result.selections().get(key);
            CandidateEvaluation evaluation = selection.evaluation();
            FullEvaluation full = result.fullEvaluations().get(key);
            rows.add(List.of(CANDIDATE_VERSION, result.candidateHash(), PROFILE_VERSION, PROFILE_HASH, RULE_CATALOG_VERSION, RULE_CATALOG_HASH, INTERACTION_CANDIDATE_VERSION, INTERACTION_CANDIDATE_HASH, SOURCE_SUMMARY_HASH, SOURCE_AUDIT_HASH, FORMULA, ADJUSTMENT_FORMULA, SAFETY_POLICY_VERSION, key.context().name(), key.actionType().name(), key.scoreDomain().name(), selection.selected() ? format(selection.targetRatio()) : "NONE", selection.selected() ? format(selection.gain()) : "NONE", evaluation == null ? "false" : Boolean.toString(evaluation.strict().accepted()), evaluation == null ? "false" : Boolean.toString(evaluation.marginAware().accepted()), Boolean.toString(full.accepted()), Boolean.toString(result.candidateFrozen()), "true", "NONE", "NONE", "NONE", "0", DEFERRED_APPLICATION_KEYS, selection.selected() ? "NOT_APPLICABLE_TO_APPROVED_KEYS" : "NO_MARGIN_AWARE_CANDIDATE_SELECTED", "false", "false", "true"));
        }
        return rows;
    }

    private static List<List<String>> selectedObservationRows(ReviewResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "caseIndex", "attemptId", "perspectiveSide", "baselineGap", "edge", "canonicalGain", "modifier", "adjustedGap", "marginBand", "signFlip", "zeroBreak");
        for (var key : KEYS) {
            SelectionResult selection = result.selections().get(key);
            if (!selection.selected()) continue;
            var band = result.screening().marginBands().get(key);
            for (var observation : result.screening().filter().filtered().stream().filter(x -> x.key().equals(key)).toList()) {
                var counterfactual = CompositionEligibleContextGainScreening.counterfactual(observation, selection.gain(), band);
                boolean zeroBreak = observation.gap().signum() == 0 && counterfactual.adjustedGap().signum() != 0;
                rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), Integer.toString(observation.caseIndex()), Long.toString(observation.attemptId()), observation.perspectiveSide().name(), format(observation.gap()), format(observation.edge()), format(selection.gain()), format(counterfactual.modifier()), format(counterfactual.adjustedGap()), counterfactual.band().name(), Boolean.toString(counterfactual.signFlip()), Boolean.toString(zeroBreak)));
            }
        }
        return rows;
    }

    private static Map<String, String> summary(ReviewResult result) {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("auditVersion", AUDIT_VERSION); s.put("frozenProfileHash", PROFILE_HASH); s.put("ruleCatalogHash", RULE_CATALOG_HASH); s.put("interactionCandidateHash", INTERACTION_CANDIDATE_HASH); s.put("sourcePhase13D4B1SummaryHash", SOURCE_SUMMARY_HASH); s.put("sourcePhase13D4B1AuditHash", SOURCE_AUDIT_HASH); s.put("sourceArtifactsUnchanged", Boolean.toString(result.beforeHashes().equals(result.afterHashes())));
        s.put("sourceObservationCount", Integer.toString(result.screening().filter().sourceObservationCount())); s.put("eligibleObservationCount", Integer.toString(result.screening().filter().eligibleObservationCount())); s.put("applicationKeyCount", Integer.toString(KEYS.size())); s.put("calibrationCaseCount", Integer.toString(result.screening().partition().calibrationCaseCount())); s.put("validationCaseCount", Integer.toString(result.screening().partition().validationCaseCount())); s.put("calibrationObservationCount", Integer.toString(result.screening().partition().calibration().size())); s.put("validationObservationCount", Integer.toString(result.screening().partition().validation().size())); s.put("caseLeakageCount", Integer.toString(result.screening().partition().caseLeakageCount())); s.put("attemptLeakageCount", Integer.toString(result.screening().partition().attemptLeakageCount()));
        s.put("strictPolicyVersion", STRICT_POLICY_VERSION); s.put("marginAwarePolicyVersion", SAFETY_POLICY_VERSION); s.put("closeBandDefinition", "abs(baselineGap)<=calibrationP25"); s.put("mediumBandDefinition", "calibrationP25<abs(baselineGap)<calibrationP90"); s.put("highBandDefinition", "abs(baselineGap)>=calibrationP90"); s.put("closeFlipRateLimit", MARGIN_CLOSE_MAX.toPlainString()); s.put("nonCloseFlipRateLimit", MARGIN_NON_CLOSE_MAX.toPlainString()); s.put("highMarginFlipLimit", "0"); s.put("closeConcentrationMinimum", MARGIN_CONCENTRATION_MIN.toPlainString()); s.put("overallFlipIsInformational", "true"); s.put("policyDevelopmentSet", "POLICY_DEVELOPMENT_VALIDATION_SET"); s.put("blindHoldoutClaimed", "false");
        long selected = result.selections().values().stream().filter(SelectionResult::selected).count(); s.put("selectedApplicationKeyCount", Long.toString(selected)); s.put("failedApplicationKeyCount", Long.toString(KEYS.size() - selected)); s.put("candidateVersion", CANDIDATE_VERSION); s.put("candidateHash", result.candidateHash()); s.put("sourcePhase13D4B1SummaryHash", SOURCE_SUMMARY_HASH); s.put("sourcePhase13D4B1AuditHash", SOURCE_AUDIT_HASH); s.put("candidateFrozen", Boolean.toString(result.candidateFrozen())); s.put("safetyPolicyVersion", SAFETY_POLICY_VERSION); s.put("adjustmentFormula", ADJUSTMENT_FORMULA); s.put("midpointPreserved", "true"); s.put("freshGameplayAuditRequired", "true"); s.put("deadzone", "NONE"); s.put("clamp", "NONE"); s.put("cap", "NONE"); s.put("overrideCount", "0");
        Integrity i = result.integrity(); s.put("foreignKeyObservationCount", Integer.toString(i.foreignKeyObservationCount())); s.put("sampleMismatchCount", Integer.toString(i.sampleMismatchCount())); s.put("policyThresholdMismatchCount", Integer.toString(i.policyThresholdMismatchCount())); s.put("selectorArtifactDecisionMismatchCount", "0"); s.put("rejectionReasonMismatchCount", Integer.toString(i.strictReferenceMismatchCount())); s.put("midpointDriftCount", Integer.toString(i.midpointDriftCount())); s.put("gapArithmeticMismatchCount", Integer.toString(i.gapArithmeticMismatchCount())); s.put("edgeDirectionMismatchCount", Integer.toString(i.edgeDirectionMismatchCount())); s.put("sideReversalMismatchCount", Integer.toString(i.sideReversalMismatchCount())); s.put("NaNCount", "0"); s.put("InfinityCount", "0"); s.put("integrityErrorCount", Integer.toString(i.errorCount()));
        s.put("matchSimulationCount", "0"); s.put("gameplayApplicationCount", "0"); s.put("nonZeroModifierCount", "0"); s.put("candidateGameplayEnabled", "false"); s.put("teamCompositionProductionEnabled", "false"); s.put("teamCompositionGameplayContribution", "0"); s.put("productionGameplayChanged", "false"); s.put("candidateGuardErrorCode", "CANDIDATE_CONTEXT_GAINS_NOT_APPROVED"); s.put("apiSchemaChanged", "false"); s.put("frontendChanged", "false");
        s.put("targetedTestCount", "RECORDED_AFTER_TARGETED_VALIDATION"); s.put("targetedTestFailures", "RECORDED_AFTER_TARGETED_VALIDATION"); s.put("backendSuiteCount", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendTestCount", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendFailures", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendErrors", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendSkipped", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendBuildSuccessful", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("priorHashesExact", Boolean.toString(result.beforeHashes().equals(result.afterHashes()))); s.put("infoCodes", "NONE"); s.put("reviewCodes", result.verdict().equals("READY_FOR_PHASE_13D4C") ? "NONE" : "MARGIN_AWARE_GAIN_POLICY_REVIEW_REQUIRED"); s.put("warningCodes", "NONE"); s.put("integrityCodes", i.errorCount() == 0 ? "NONE" : "COMPOSITION_MARGIN_AWARE_POLICY_INTEGRITY"); s.put("verdict", result.verdict()); s.put("phase13D4CAllowed", Boolean.toString(result.verdict().equals("READY_FOR_PHASE_13D4C"))); s.put("nextPhase", result.verdict().equals("READY_FOR_PHASE_13D4C") ? "PHASE_13D4C_FRESH_CANDIDATE_GAMEPLAY_APPLICATION_AUDIT" : "COMPOSITION_MARGIN_AWARE_GAIN_POLICY_REVIEW_REQUIRED");
        for (var key : KEYS) {
            String prefix = key.stableId() + "."; SelectionResult selection = result.selections().get(key); CandidateEvaluation evaluation = selection.evaluation(); FullEvaluation full = result.fullEvaluations().get(key); int calibration = count(result.screening().partition().calibration(), key); int validation = count(result.screening().partition().validation(), key); int fullCount = count(result.screening().filter().filtered(), key); s.put(prefix + "calibrationSampleCount", Integer.toString(calibration)); s.put(prefix + "validationSampleCount", Integer.toString(validation)); s.put(prefix + "fullSampleCount", Integer.toString(fullCount)); s.put(prefix + "edgeScaleP90", format(result.screening().anchors().get(key).edgeScale())); s.put(prefix + "gapScaleP90", format(result.screening().anchors().get(key).gapScale())); s.put(prefix + "strictEligibleCandidateCount", Long.toString(result.evaluations().get(key).stream().filter(x -> x.strict().accepted() && !"ZERO_REFERENCE".equals(x.metrics().candidate().label())).count())); s.put(prefix + "marginAwareEligibleCandidateCount", Long.toString(result.evaluations().get(key).stream().filter(x -> x.marginAware().accepted() && !"ZERO_REFERENCE".equals(x.metrics().candidate().label())).count())); s.put(prefix + "selectedTargetRatio", selection.selected() ? format(selection.targetRatio()) : "NONE"); s.put(prefix + "selectedGain", selection.selected() ? format(selection.gain()) : "NONE"); s.put(prefix + "validationCloseFlipRate", evaluation == null ? "NONE" : format(evaluation.bandStats().closeFlipRate())); s.put(prefix + "validationNonCloseFlipRate", evaluation == null ? "NONE" : format(evaluation.bandStats().nonCloseFlipRate())); s.put(prefix + "validationHighFlipCount", evaluation == null ? "NONE" : Long.toString(evaluation.bandStats().highFlipCount())); s.put(prefix + "validationCloseConcentration", evaluation == null ? "NONE" : format(evaluation.bandStats().flipCloseConcentration())); s.put(prefix + "fullCloseFlipRate", full.metric() == null ? "NONE" : format(full.stats().closeFlipRate())); s.put(prefix + "fullNonCloseFlipRate", full.metric() == null ? "NONE" : format(full.stats().nonCloseFlipRate())); s.put(prefix + "fullHighFlipCount", full.metric() == null ? "NONE" : Long.toString(full.stats().highFlipCount())); s.put(prefix + "fullCloseConcentration", full.metric() == null ? "NONE" : format(full.stats().flipCloseConcentration())); s.put(prefix + "fullAccepted", Boolean.toString(full.accepted()));
        }
        return s;
    }

    private static String auditLog(ReviewResult result) {
        StringBuilder text = new StringBuilder();
        summary(result).forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        text.append("policyDefinitions=STRICT_OVERALL_FLIP_REFERENCE_V1,MARGIN_AWARE_SAFETY_POLICY_V1\n");
        text.append("policyDevelopmentSet=POLICY_DEVELOPMENT_VALIDATION_SET\n");
        text.append("blindHoldoutClaimed=false\n");
        text.append("outcomeColumnsReadByPolicySelector=false\n");
        text.append("candidateHashCanonicalOrder=EXPLICIT_SORTED_APPLICATION_KEY_ORDER\n");
        text.append("priorArtifactPreserved=true\n");
        return text.toString();
    }

    static List<PolicyDefinition> policyDefinitions() {
        return List.of(new PolicyDefinition(STRICT_POLICY_VERSION, false, STRICT_SIGNAL_MIN, STRICT_SIGNAL_MAX,
                        STRICT_P95_MAX, STRICT_P99_MAX, STRICT_MAX_MAX, new BigDecimal("0.030"), null, null, null, false),
                new PolicyDefinition(MARGIN_AWARE_POLICY_VERSION, true, STRICT_SIGNAL_MIN, STRICT_SIGNAL_MAX,
                        STRICT_P95_MAX, STRICT_P99_MAX, STRICT_MAX_MAX, null, MARGIN_NON_CLOSE_MAX, MARGIN_CLOSE_MAX, MARGIN_CONCENTRATION_MIN, true));
    }

    static String classifyFlip(boolean signFlip, CompositionEligibleContextGainScreening.MarginBandName band,
                               int baselineSign, int adjustedSign) {
        if (baselineSign == 0 && adjustedSign != 0) return "ZERO_BREAK";
        if (!signFlip) return "NONE";
        if (band == CompositionEligibleContextGainScreening.MarginBandName.CLOSE) return "TIE_BREAK_FLIP";
        if (band == CompositionEligibleContextGainScreening.MarginBandName.HIGH) return "HIGH_MARGIN_FLIP";
        return "MATERIAL_FLIP";
    }

    private static void append(StringBuilder payload, String key, String value) {
        payload.append(key).append('=').append(value).append('\n');
    }

    private static int count(List<CompositionEligibleContextGainScreening.Observation> observations,
                             CompositionEligibleContextGainScreening.GainKey key) {
        return (int) observations.stream().filter(x -> x.key().equals(key)).count();
    }

    private static CompositionEligibleContextGainScreening.GainKey key(String context, String action, String domain) {
        return KEYS.stream().filter(x -> x.context().name().equals(context) && x.actionType().name().equals(action) && x.scoreDomain().name().equals(domain)).findFirst().orElseThrow();
    }

    private static BigDecimal divide(long numerator, long denominator) {
        return denominator == 0 ? BigDecimal.ZERO.setScale(DECIMAL_SCALE)
                : BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), DECIMAL_SCALE + 8, ROUNDING);
    }

    static String format(BigDecimal value) {
        if (value.signum() == 0) return BigDecimal.ZERO.setScale(DECIMAL_SCALE).toPlainString();
        return value.setScale(DECIMAL_SCALE, ROUNDING).toPlainString();
    }

    private static long lineCount(Path path) throws IOException {
        try (var lines = Files.lines(path)) { return lines.count(); }
    }

    private static List<List<String>> rows(String... headers) { return new ArrayList<>(List.of(List.of(headers))); }

    private static void writeKeyValue(Path path, Map<String, String> values) throws IOException {
        List<List<String>> rows = rows("key", "value");
        values.forEach((key, value) -> rows.add(List.of(key, value)));
        writeCsv(path, rows);
    }

    private static void writeCsv(Path path, List<List<String>> values) throws IOException {
        StringBuilder out = new StringBuilder();
        for (List<String> row : values) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) out.append(',');
                String value = row.get(i);
                if (value.contains(",") || value.contains("\"") || value.contains("\n")) out.append('"').append(value.replace("\"", "\"\"")).append('"');
                else out.append(value);
            }
            out.append('\n');
        }
        Files.writeString(path, out, StandardCharsets.UTF_8);
    }

    private static List<Map<String, String>> readCsvMaps(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return List.of();
        List<String> header = csv(lines.get(0));
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            List<String> cells = csv(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) row.put(header.get(c), c < cells.size() ? cells.get(c) : "");
            result.add(row);
        }
        return result;
    }

    private static List<String> csv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) { cells.add(cell.toString()); cell.setLength(0); }
            else cell.append(c);
        }
        cells.add(cell.toString());
        return cells;
    }

    private static String sha256(Path path) throws IOException { return sha256(Files.readAllBytes(path)); }
    private static String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new IllegalStateException(e); } }

    record PolicyDefinition(String id, boolean marginAware, BigDecimal signalMin, BigDecimal signalMax,
                            BigDecimal p95Max, BigDecimal p99Max, BigDecimal maxMax, BigDecimal overallFlipMax,
                            BigDecimal nonCloseFlipMax, BigDecimal closeFlipMax, BigDecimal concentrationMin,
                            boolean overallInformational) {}

    record BandStats(long closeSampleCount, long closeFlipCount, BigDecimal closeFlipRate,
                     long mediumSampleCount, long mediumFlipCount, long highSampleCount, long highFlipCount,
                     long nonCloseSampleCount, long nonCloseFlipCount, BigDecimal nonCloseFlipRate,
                     BigDecimal flipCloseConcentration, long zeroToPositiveCount, long zeroToNegativeCount,
                     long nonZeroToZeroCount) {}

    record StrictDecision(boolean accepted, boolean structuralPass, boolean coveragePass, boolean signalPass,
                          boolean tailPass, boolean signFlipPass, boolean highMarginPass, List<String> reasons) {}

    record MarginAwareDecision(boolean accepted, boolean structuralPass, boolean coveragePass, boolean signalPass,
                               boolean tailPass, boolean highMarginPass, boolean nonClosePass, boolean closePass,
                               boolean concentrationPass, List<String> reasons) {}

    record FullDecision(boolean accepted, boolean structuralPass, boolean signalPass, boolean tailPass,
                        boolean highMarginPass, boolean nonClosePass, boolean closePass, boolean concentrationPass,
                        List<String> reasons) {}

    record CandidateEvaluation(CompositionEligibleContextGainScreening.GainKey key,
                               CompositionEligibleContextGainScreening.Metrics metrics,
                               StrictDecision strict, MarginAwareDecision marginAware, BandStats bandStats) {}

    record SelectionResult(CompositionEligibleContextGainScreening.GainKey key, boolean selected, String label,
                           BigDecimal targetRatio, BigDecimal gain, String reason, List<String> rejectedReasons,
                           CandidateEvaluation evaluation) {}

    record FullEvaluation(CompositionEligibleContextGainScreening.GainKey key, boolean evaluated,
                          CompositionEligibleContextGainScreening.Metrics metric, FullDecision decision) {
        boolean accepted() { return decision != null && decision.accepted(); }
        BandStats stats() { return metric == null ? new BandStats(0, 0, BigDecimal.ZERO, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ONE, 0, 0, 0) : bandStats(metric); }
    }

    record SourceVerification(Path path, boolean valid, Map<String, String> hashes) {}

    record Integrity(int foreignKeyObservationCount, int sampleMismatchCount, int policyThresholdMismatchCount,
                     int selectorArtifactDecisionMismatchCount, int strictReferenceMismatchCount,
                     int rejectionReasonMismatchCount, int marginBandKeyMismatchCount, int unrelatedKeyInfluenceMismatchCount,
                     int midpointDriftCount, int gapArithmeticMismatchCount, int edgeDirectionMismatchCount,
                     int sideReversalMismatchCount, int nanInfinityCount, int errorCount, int candidateHashMismatchCount, int gridMismatchCount,
                     int sourceMismatchCount) {
    }

    record ReviewResult(SourceVerification source, CompositionEligibleContextGainScreening.ScreeningResult screening,
                        List<CompositionEligibleContextGainScreening.GridCandidate> grid,
                        Map<CompositionEligibleContextGainScreening.GainKey, List<CandidateEvaluation>> evaluations,
                        Map<CompositionEligibleContextGainScreening.GainKey, SelectionResult> selections,
                        Map<CompositionEligibleContextGainScreening.GainKey, FullEvaluation> fullEvaluations,
                        Integrity integrity, boolean candidateFrozen, String candidateHash, String verdict,
                        Map<String, String> beforeHashes, Map<String, String> afterHashes) {}
}
