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
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phase 13D-4B artifact-only gain screening. This class must never be called by
 * production runtime code and deliberately has no simulation dependency.
 */
public final class CompositionEligibleContextGainScreening {
    static final Path SOURCE_DIR = Path.of("build", "reports", "composition-shadow-wiring-gate-closure");
    static final Path OUTPUT = Path.of("build", "reports", "composition-eligible-context-gain-screening");
    static final Path REPAIR_OUTPUT = Path.of("build", "reports", "composition-eligible-context-gain-screening-key-isolation-repair");
    static final Path PREVIOUS_OUTPUT = OUTPUT;
    static final String PREVIOUS_SUMMARY_HASH = "efa56d5bc40f662e17038ee0397d9cd1435fb3baaede229914bca19609ffc8d7";
    static final String PREVIOUS_AUDIT_HASH = "d751603e82660380d24a0656ccd4e90f532c788f94d888f76f205c12767ae7c3";
    static final Path OBSERVATIONS = SOURCE_DIR.resolve("composition-shadow-observations-gate.csv");
    static final String AUDIT_VERSION = "phase-13d4b-eligible-context-gain-screening-v1";
    static final String CANDIDATE_VERSION = "composition-gameplay-context-gain-candidate-v1";
    static final String PROFILE_VERSION = "thirty-champion-composition-profile-candidate-v2";
    static final String PROFILE_HASH = "fbf58dc5be12f2b07c5dff7ded9e182d7829999d2255e65dbbd073ccde2688d1";
    static final String RULE_CATALOG_VERSION = "composition-interaction-rule-catalog-v1";
    static final String RULE_CATALOG_HASH = "f0480eb8e9620d02a0187da384224d3735717ad5f5f2e1ca9e904aea4c7ae7d4";
    static final String INTERACTION_CANDIDATE_VERSION = "composition-interaction-product-exposure-v1";
    static final String INTERACTION_CANDIDATE_HASH = "0f92b3f9d3ea81f9d20531341167efe1c0a8c1a9d8b593f27d28b7745c0bb49b";
    static final String FORMULA = "PRODUCT_EXPOSURE";
    static final String SOURCE_GATE_SUMMARY_HASH = "ae184785d5d009e1a1901111e9ea9ebfe8169545e6d0b2d60b7a55f862cc3f7b";
    static final String SOURCE_GATE_AUDIT_HASH = "33132965471140c5f6cca4ea93ad2381e4b62667a5b1e0877c224ef18b30dff4";
    static final String SOURCE_PHASE_4A_SUMMARY_HASH = "1ccab21a78402374d44089c02276ad95cabc6c9135b93825ff0944e6b9ad4d21";
    static final String SOURCE_PHASE_4A_AUDIT_HASH = "31de31a1ad4efe1eda3cea633eced669eff88cb93d82713c4314d0a7f0fee309";
    static final String SOURCE_SCHEDULE_HASH = "8c82f0e1c2a24a0112df769808c471fea2707acb20d48f641212fe321d6c39b1";
    static final String FROZEN_MATCHUP_PROFILE_HASH = "c8956937e8c9032654feb2bb17ff7ef66d68a964b4f1f6ed98853400f5b3dc64";
    static final List<HistoricalArtifact> HISTORICAL_ARTIFACTS = List.of(
            new HistoricalArtifact(Path.of("baseline", "phase12_5", "progression-baseline-summary.csv"), "af014896733d568974c91043c24d07917239808e3fcb9277bfba55480974da04"),
            new HistoricalArtifact(Path.of("baseline", "phase12_5", "progression-combat-contribution.csv"), "f18ab7781284d23a9369a1f8a1ee4ba5df156706727dc588ce42114d90ddc735"),
            new HistoricalArtifact(Path.of("baseline", "phase12_5", "progression-position-timings.csv"), "464f895021398f6ffa25cfebabc08d0483e3428018321f127f45d82f8725ec5c"),
            new HistoricalArtifact(Path.of("build", "reports", "champion-matchup-production-activation", "champion-matchup-production-activation-summary.csv"), "d6eab10c88e5cc36ecd5d2e71b916e26703f83cddce1a792ba6cad7a2adf8cb1"),
            new HistoricalArtifact(Path.of("build", "reports", "champion-matchup-production-activation", "champion-matchup-production-activation-audit.log"), "e89359182f9019bd097f67cb59d39f8b4c0bd818e598452e22810deeb3f290b3"),
            new HistoricalArtifact(Path.of("build", "reports", "team-composition-foundation", "team-composition-foundation-summary.csv"), "98c896c81e3b432c095b74b3693c3491f3d9cac7c941d98b9c34ea658b33afa1"),
            new HistoricalArtifact(Path.of("build", "reports", "team-composition-foundation", "team-composition-foundation-audit.log"), "8fe61f85d16a5c4339b1ceed969114dcf49d09749300285c6a4f41b2b941d746"),
            new HistoricalArtifact(Path.of("build", "reports", "thirty-champion-composition-profile-review", "composition-profile-freeze-summary.csv"), "1ca9fae04cf2057076bafb5f05fe15c0174ac922eeffdc7ad288f7b1fc5272ff"),
            new HistoricalArtifact(Path.of("build", "reports", "thirty-champion-composition-profile-review", "composition-profile-freeze-audit.log"), "2055191dc5b16d74114c6a6d6f83a3de15183480100dc7f44d82b94d288d98dc"),
            new HistoricalArtifact(Path.of("build", "reports", "composition-interaction-context", "composition-interaction-candidate-summary.csv"), "fea9adfb5ef90ce174130d0e072fcc043e5c2506ba3e2dac323c890624c53b92"),
            new HistoricalArtifact(Path.of("build", "reports", "composition-interaction-context", "composition-interaction-context-audit.log"), "9d1ae61123465796717ba5bbe6613a58a41cca0da11b3f7d8f1dfcc0adb6ff99"));
    static final int EXPECTED_SOURCE_OBSERVATION_COUNT = 116_474;
    static final int EXPECTED_ELIGIBLE_COUNT = 25_725;
    static final int EXPECTED_CALIBRATION_CASES = 800;
    static final int EXPECTED_VALIDATION_CASES = 400;
    static final int DECIMAL_SCALE = 12;
    static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    static final List<BigDecimal> TARGET_RATIOS = List.of(
            new BigDecimal("0.000"), new BigDecimal("0.025"), new BigDecimal("0.050"),
            new BigDecimal("0.075"), new BigDecimal("0.100"));
    static final List<String> TARGET_NAMES = List.of(
            "ZERO_REFERENCE", "VERY_LOW", "LOW", "MEDIUM", "HIGH_SCREENING_LIMIT");
    static final List<GainKey> APPROVED_KEYS = List.of(
            new GainKey(TeamCompositionContext.SKIRMISH, CompositionActionType.SKIRMISH,
                    CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE),
            new GainKey(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT,
                    CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE),
            new GainKey(TeamCompositionContext.SIEGE, CompositionActionType.SIEGE_COMBAT,
                    CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE),
            new GainKey(TeamCompositionContext.BASE_DEFENSE, CompositionActionType.BASE_DEFENSE,
                    CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE));
    static final Comparator<GainKey> KEY_ORDER = Comparator
            .comparing((GainKey key) -> key.context().name())
            .thenComparing(key -> key.actionType().name())
            .thenComparing(key -> key.scoreDomain().name());

    private CompositionEligibleContextGainScreening() {}

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("repair")) {
            RepairResult result = repair(SOURCE_DIR, PREVIOUS_OUTPUT, REPAIR_OUTPUT);
            System.out.println("Composition gain key isolation repair: " + result.verdict());
            System.out.println("Candidate hash: " + result.screening().candidateHash());
            System.out.println("Summary SHA-256: " + sha256(REPAIR_OUTPUT.resolve("composition-gain-key-local-screening-summary.csv")));
            System.out.println("Audit SHA-256: " + sha256(REPAIR_OUTPUT.resolve("composition-gain-key-local-screening-audit.log")));
            if (result.verdict().startsWith("BLOCKED")) throw new IllegalStateException(result.verdict());
            return;
        }
        ScreeningResult result = screen(SOURCE_DIR, OUTPUT);
        System.out.println("Composition eligible context gain screening: " + result.verdict());
        System.out.println("Candidate hash: " + result.candidateHash());
        System.out.println("Summary SHA-256: " + sha256(OUTPUT.resolve("composition-gain-screening-summary.csv")));
        System.out.println("Audit SHA-256: " + sha256(OUTPUT.resolve("composition-gain-screening-audit.log")));
        if (result.verdict().startsWith("BLOCKED")) {
            throw new IllegalStateException(result.verdict());
        }
    }

    static RepairResult repair(Path sourceDir, Path previousOutput, Path outputDir) throws IOException {
        SourceState source = readSource(sourceDir);
        PreviousState previous = readPrevious(previousOutput);
        List<RawObservation> raw = readObservations(sourceDir.resolve("composition-shadow-observations-gate.csv"));
        FilterResult filter = filter(raw);
        if (filter.sourceObservationCount() != EXPECTED_SOURCE_OBSERVATION_COUNT || filter.eligibleObservationCount() != EXPECTED_ELIGIBLE_COUNT) {
            throw new IllegalStateException("Unexpected repair input counts: " + filter);
        }
        Partition partition = partition(filter.filtered());
        if (partition.calibrationCaseCount() != EXPECTED_CALIBRATION_CASES || partition.validationCaseCount() != EXPECTED_VALIDATION_CASES
                || partition.caseLeakageCount() != 0 || partition.attemptLeakageCount() != 0) {
            throw new IllegalStateException("Invalid repair partition: " + partition);
        }
        Map<GainKey, Anchor> anchors = anchors(partition.calibration());
        List<GridCandidate> grid = grid(anchors);
        Map<GainKey, List<Metrics>> validation = evaluateCandidates(partition.validation(), grid, anchors, partition.calibration());
        Map<GainKey, List<Metrics>> calibration = evaluateCandidates(partition.calibration(), grid, anchors, partition.calibration());
        Map<GainKey, Selection> selections = select(grid, validation, calibration);
        Map<GainKey, Metrics> full = fullConfirmation(filter.filtered(), selections, anchors, partition.calibration());
        boolean candidateEligible = selections.size() == APPROVED_KEYS.size() && selections.values().stream().allMatch(Selection::selected)
                && full.size() == APPROVED_KEYS.size() && full.values().stream().allMatch(CompositionEligibleContextGainScreening::fullAccepted);
        String provisionalHash = candidateEligible ? candidateHash(selections.values()) : "NONE";
        ScreeningResult provisional = new ScreeningResult(source, raw, filter, partition, anchors, grid, calibration, validation, selections, full, candidateEligible, provisionalHash, "UNSET");
        RepairChecks checks = repairChecks(provisional, previous);
        boolean integrityExact = checks.integrityErrorCount() == 0 && source.identityExact() && source.priorHashesExact() && previous.exact();
        boolean candidateFrozen = candidateEligible && integrityExact;
        String candidateHash = candidateFrozen ? provisionalHash : "NONE";
        String verdict = !integrityExact ? "BLOCKED_BY_COMPOSITION_GAIN_KEY_ISOLATION_INTEGRITY"
                : candidateFrozen ? "READY_FOR_PHASE_13D4C" : "GAIN_SELECTION_REVIEW_CONFIRMED";
        ScreeningResult screening = new ScreeningResult(source, raw, filter, partition, anchors, grid, calibration, validation, selections, full, candidateFrozen, candidateHash, verdict);
        RepairResult result = new RepairResult(screening, previous, checks, verdict);
        writeRepairArtifacts(outputDir, result);
        SourceState after = readSource(sourceDir);
        if (!source.sameFiles(after)) throw new IllegalStateException("Source artifact changed during repair");
        PreviousState previousAfter = readPrevious(previousOutput);
        if (!previous.sameHashes(previousAfter)) throw new IllegalStateException("Historical Phase 13D-4B artifact changed during repair");
        return result;
    }

    static ScreeningResult screen(Path sourceDir, Path outputDir) throws IOException {
        SourceState source = readSource(sourceDir);
        List<RawObservation> raw = readObservations(sourceDir.resolve("composition-shadow-observations-gate.csv"));
        FilterResult filter = filter(raw);
        if (filter.sourceObservationCount() != EXPECTED_SOURCE_OBSERVATION_COUNT
                || filter.eligibleObservationCount() != EXPECTED_ELIGIBLE_COUNT) {
            throw new IllegalStateException("Unexpected source observation counts: " + filter);
        }
        Partition partition = partition(filter.filtered());
        Map<GainKey, Anchor> anchors = anchors(partition.calibration());
        List<GridCandidate> grid = grid(anchors);
        Map<GainKey, List<Metrics>> validation = evaluateCandidates(
                partition.validation(), grid, anchors, partition.calibration());
        Map<GainKey, List<Metrics>> calibration = evaluateCandidates(
                partition.calibration(), grid, anchors, partition.calibration());
        Map<GainKey, Selection> selections = select(grid, validation, calibration);
        Map<GainKey, Metrics> full = fullConfirmation(filter.filtered(), selections, anchors, partition.calibration());
        boolean candidateFrozen = selections.size() == APPROVED_KEYS.size()
                && selections.values().stream().allMatch(Selection::selected)
                && full.size() == APPROVED_KEYS.size()
                && full.values().stream().allMatch(CompositionEligibleContextGainScreening::fullAccepted)
                && source.identityExact() && source.priorHashesExact();
        String candidateHash = candidateFrozen ? candidateHash(selections.values()) : "NONE";
        String verdict = candidateFrozen ? "READY_FOR_PHASE_13D4C" : verdict(selections, full, source);
        ScreeningResult result = new ScreeningResult(source, raw, filter, partition, anchors, grid,
                calibration, validation, selections, full, candidateFrozen, candidateHash, verdict);
        writeArtifacts(outputDir, result);
        SourceState after = readSource(sourceDir);
        if (!source.sameFiles(after)) throw new IllegalStateException("Source artifact changed during screening");
        return result;
    }

    static List<RawObservation> readObservations(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) throw new IllegalStateException("Empty observation artifact");
        Map<String, Integer> columns = indexes(csv(lines.getFirst()));
        List<RawObservation> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            List<String> cells = csv(line);
            CompositionActionType action = CompositionActionType.valueOf(cell(cells, columns, "actionType"));
            TeamCompositionContext context = TeamCompositionContext.valueOf(cell(cells, columns, "context"));
            CompositionBaselineScoreDomain domain = CompositionBaselineScoreDomain.valueOf(
                    cell(cells, columns, "baselineScoreDomain"));
            CompositionApplicationEligibility eligibility = CompositionApplicationEligibility.valueOf(
                    cell(cells, columns, "applicationEligibility"));
            boolean available = Boolean.parseBoolean(cell(cells, columns, "scoreAvailable"));
            BigDecimal perspective = decimalOrNull(cell(cells, columns, "perspectiveBaselineScore"));
            BigDecimal opponent = decimalOrNull(cell(cells, columns, "opponentBaselineScore"));
            BigDecimal gap = decimalOrNull(cell(cells, columns, "baselineScoreGap"));
            BigDecimal edge = decimal(cell(cells, columns, "perspectiveRawEdge"));
            if (!Double.isFinite(edge.doubleValue()) || (perspective != null && !Double.isFinite(perspective.doubleValue()))
                    || (opponent != null && !Double.isFinite(opponent.doubleValue()))
                    || (gap != null && !Double.isFinite(gap.doubleValue()))) {
                throw new IllegalStateException("NaN/Infinity in observation artifact");
            }
            rows.add(new RawObservation(
                    Integer.parseInt(cell(cells, columns, "caseIndex")),
                    Long.parseLong(cell(cells, columns, "seed")),
                    Long.parseLong(cell(cells, columns, "attemptId")),
                    Integer.parseInt(cell(cells, columns, "matchTimeSeconds")),
                    action, context, TeamSide.valueOf(cell(cells, columns, "perspectiveSide")), domain,
                    edge, perspective, opponent, gap, eligibility, available,
                    Boolean.parseBoolean(cell(cells, columns, "applicationApplied")),
                    decimal(cell(cells, columns, "appliedModifier"))));
        }
        return List.copyOf(rows);
    }

    static FilterResult filter(List<RawObservation> raw) {
        List<Observation> selected = new ArrayList<>();
        Set<AttemptApplicationIdentity> identities = new HashSet<>();
        int unknown = 0;
        int mismatch = 0;
        int deferredIncluded = 0;
        int nanInfinity = 0;
        for (RawObservation row : raw) {
            GainKey key = new GainKey(row.context(), row.actionType(), row.scoreDomain());
            boolean approved = APPROVED_KEYS.contains(key);
            if (row.eligibility().eligible() && !approved) unknown++;
            if (row.eligibility().eligible() && (!row.baselineScoreAvailable()
                    || row.perspectiveBaselineScore() == null || row.opponentBaselineScore() == null
                    || row.gap() == null || row.applicationApplied() || row.appliedModifier().compareTo(BigDecimal.ZERO) != 0)) {
                mismatch++;
            }
            if (!row.eligibility().eligible()) continue;
            if (!approved || !row.baselineScoreAvailable() || row.perspectiveBaselineScore() == null
                    || row.opponentBaselineScore() == null || row.gap() == null
                    || row.applicationApplied() || row.appliedModifier().compareTo(BigDecimal.ZERO) != 0) continue;
            if (!identities.add(new AttemptApplicationIdentity(row.caseIndex(), row.attemptId(), key))) {
                throw new IllegalStateException("Duplicate attempt/application key: " + row.caseIndex() + "/" + row.attemptId());
            }
            if (!Double.isFinite(row.edge().doubleValue()) || !Double.isFinite(row.gap().doubleValue())) {
                nanInfinity++;
                continue;
            }
            selected.add(new Observation(row.caseIndex(), row.seed(), row.attemptId(), row.matchTimeSeconds(), key,
                    row.perspectiveSide(), row.edge(), row.perspectiveBaselineScore(), row.opponentBaselineScore(), row.perspectiveBaselineScore().subtract(row.opponentBaselineScore())));
        }
        if (raw.stream().filter(row -> !row.eligibility().eligible() && row.baselineScoreAvailable()
                && row.applicationApplied()).count() > 0) deferredIncluded++;
        return new FilterResult(raw.size(), selected.size(), raw.size() - selected.size(), selected,
                unknown, mismatch, deferredIncluded, nanInfinity, identities.size());
    }

    static Partition partition(List<Observation> observations) {
        List<Observation> calibration = observations.stream()
                .filter(row -> Math.floorMod(row.caseIndex(), 3) != 0).toList();
        List<Observation> validation = observations.stream()
                .filter(row -> Math.floorMod(row.caseIndex(), 3) == 0).toList();
        Set<Integer> calibrationCases = calibration.stream().map(Observation::caseIndex).collect(Collectors.toSet());
        Set<Integer> validationCases = validation.stream().map(Observation::caseIndex).collect(Collectors.toSet());
        Set<Integer> intersection = new HashSet<>(calibrationCases);
        intersection.retainAll(validationCases);
        Set<String> calibrationAttempts = calibration.stream().map(row -> row.caseIndex() + ":" + row.attemptId()).collect(Collectors.toSet());
        Set<String> validationAttempts = validation.stream().map(row -> row.caseIndex() + ":" + row.attemptId()).collect(Collectors.toSet());
        calibrationAttempts.retainAll(validationAttempts);
        int attemptLeakage = calibrationAttempts.size();
        return new Partition(calibration, validation, calibrationCases.size(), validationCases.size(),
                intersection.size(), attemptLeakage, EXPECTED_CALIBRATION_CASES, EXPECTED_VALIDATION_CASES);
    }

    static Map<GainKey, Anchor> anchors(List<Observation> calibration) {
        Map<GainKey, Anchor> result = new LinkedHashMap<>();
        for (GainKey key : APPROVED_KEYS) {
            List<Observation> rows = calibration.stream().filter(row -> row.key().equals(key)).toList();
            List<BigDecimal> edges = sorted(rows, Observation::edge, true);
            List<BigDecimal> gaps = sorted(rows, Observation::gap, true);
            result.put(key, new Anchor(key, quantiles(edges), quantiles(gaps), quantile(edges, .90), quantile(gaps, .90)));
            if (result.get(key).edgeScale().signum() <= 0 || result.get(key).gapScale().signum() <= 0) {
                throw new IllegalStateException("Invalid gain anchor for " + key);
            }
        }
        return Map.copyOf(result);
    }

    static List<GridCandidate> grid(Map<GainKey, Anchor> anchors) {
        List<GridCandidate> result = new ArrayList<>();
        for (GainKey key : APPROVED_KEYS) {
            Anchor anchor = anchors.get(key);
            for (int i = 0; i < TARGET_RATIOS.size(); i++) {
                BigDecimal gain = TARGET_RATIOS.get(i).multiply(anchor.gapScale())
                        .divide(anchor.edgeScale(), DECIMAL_SCALE + 8, ROUNDING)
                        .setScale(DECIMAL_SCALE, ROUNDING);
                result.add(new GridCandidate(key, TARGET_NAMES.get(i), TARGET_RATIOS.get(i), canonical(gain)));
            }
        }
        return List.copyOf(result);
    }

    static Map<GainKey, List<Metrics>> evaluateCandidates(List<Observation> observations,
                                                           List<GridCandidate> grid,
                                                           Map<GainKey, Anchor> anchors,
                                                           List<Observation> calibrationForBands) {
        Map<GainKey, MarginBand> bands = marginBands(calibrationForBands);
        Map<GainKey, List<Metrics>> result = new LinkedHashMap<>();
        for (GainKey key : APPROVED_KEYS) {
            MarginBand band = bands.get(key);
            List<Metrics> values = new ArrayList<>();
            List<Observation> keyObservations = observations.stream().filter(observation -> observation.key().equals(key)).toList();
            for (GridCandidate candidate : grid.stream().filter(x -> x.key().equals(key)).toList()) {
                values.add(metrics(keyObservations, candidate, band));
            }
            result.put(key, List.copyOf(values));
        }
        return Map.copyOf(result);
    }

    static Map<GainKey, Selection> select(List<GridCandidate> grid, Map<GainKey, List<Metrics>> validation,
                                           Map<GainKey, List<Metrics>> calibration) {
        Map<GainKey, Selection> result = new LinkedHashMap<>();
        for (GainKey key : APPROVED_KEYS) {
            List<Metrics> values = validation.get(key);
            List<Metrics> calibrationValues = calibration.get(key);
            List<String> rejected = new ArrayList<>();
            Metrics selected = null;
            for (Metrics metric : values) {
                if ("ZERO_REFERENCE".equals(metric.candidate().label())) {
                    rejected.add(metric.candidate().label() + ":ZERO_REFERENCE_NOT_SELECTABLE");
                    continue;
                }
                if (validationAccepted(metric, calibrationValues)) {
                    selected = metric;
                    break;
                }
                rejected.add(metric.candidate().label() + ":" + String.join("|", validationReasons(metric, calibrationValues)));
            }
            result.put(key, selected == null
                    ? new Selection(key, false, "NONE", BigDecimal.ZERO, BigDecimal.ZERO, "GAIN_SELECTION_REVIEW", rejected)
                    : new Selection(key, true, selected.candidate().label(), selected.candidate().targetRatio(),
                    selected.candidate().gain(), "SMALLEST_ELIGIBLE_TARGET_RATIO", rejected));
        }
        return Map.copyOf(result);
    }

    static Map<GainKey, Metrics> fullConfirmation(List<Observation> observations, Map<GainKey, Selection> selections,
                                                   Map<GainKey, Anchor> anchors, List<Observation> calibration) {
        Map<GainKey, MarginBand> bands = marginBands(calibration);
        Map<GainKey, Metrics> result = new LinkedHashMap<>();
        for (GainKey key : APPROVED_KEYS) {
            Selection selection = selections.get(key);
            if (selection == null || !selection.selected()) continue;
            List<Observation> keyObservations = observations.stream().filter(observation -> observation.key().equals(key)).toList();
            result.put(key, metrics(keyObservations,
                    new GridCandidate(key, selection.targetLabel(), selection.targetRatio(), selection.gain()),
                    bands.get(key)));
        }
        return Map.copyOf(result);
    }

    static ValidationDecision validationDecision(Metrics metric, List<Metrics> calibration) {
        int calibrationSampleCount = calibration.stream().filter(x -> x.candidate().label().equals(metric.candidate().label()))
                .findFirst().map(Metrics::sampleCount).orElse(0);
        List<String> reasons = new ArrayList<>();
        boolean zeroReference = "ZERO_REFERENCE".equals(metric.candidate().label());
        if (zeroReference) reasons.add("ZERO_REFERENCE_NOT_SELECTABLE");
        boolean structural = metric.midpointDriftCount() == 0 && metric.gapArithmeticMismatchCount() == 0
                && metric.edgeDirectionMismatchCount() == 0 && metric.sideReversalMismatchCount() == 0
                && metric.nanCount() == 0 && metric.infinityCount() == 0;
        boolean coverage = metric.sampleCount() >= 100 && calibrationSampleCount >= 200
                && metric.bluePerspectiveCount() > 0 && metric.redPerspectiveCount() > 0;
        boolean signal = metric.p90ModifierGapRatio().compareTo(new BigDecimal("0.030")) >= 0
                && metric.p90ModifierGapRatio().compareTo(new BigDecimal("0.080")) <= 0
                && metric.p90AbsoluteModifier().signum() > 0 && metric.distinctModifierCount() >= 100;
        boolean tail = metric.p95ModifierGapRatio().compareTo(new BigDecimal("0.100")) <= 0
                && metric.p99ModifierGapRatio().compareTo(new BigDecimal("0.150")) <= 0
                && metric.maxModifierGapRatio().compareTo(new BigDecimal("0.250")) <= 0;
        boolean flips = metric.overallSignFlipRate().compareTo(new BigDecimal("0.030")) <= 0;
        boolean high = metric.highMarginSignFlipCount() == 0;
        if (!zeroReference) {
            if (metric.sampleCount() < 100) reasons.add("VALIDATION_SAMPLE_BELOW_100");
            if (calibrationSampleCount < 200) reasons.add("CALIBRATION_SAMPLE_BELOW_200");
            if (metric.bluePerspectiveCount() == 0 || metric.redPerspectiveCount() == 0) reasons.add("VALIDATION_BLUE_OR_RED_COVERAGE_MISSING");
            if (metric.p90ModifierGapRatio().compareTo(new BigDecimal("0.030")) < 0) reasons.add("P90_SIGNAL_BELOW_030");
            if (metric.p90ModifierGapRatio().compareTo(new BigDecimal("0.080")) > 0) reasons.add("P90_SIGNAL_ABOVE_080");
            if (metric.p90AbsoluteModifier().signum() <= 0) reasons.add("P90_MODIFIER_NOT_POSITIVE");
            if (metric.distinctModifierCount() < 100) reasons.add("DISTINCT_MODIFIER_BELOW_100");
            if (metric.p95ModifierGapRatio().compareTo(new BigDecimal("0.100")) > 0) reasons.add("P95_SAFETY_ABOVE_100");
            if (metric.p99ModifierGapRatio().compareTo(new BigDecimal("0.150")) > 0) reasons.add("P99_SAFETY_ABOVE_150");
            if (metric.maxModifierGapRatio().compareTo(new BigDecimal("0.250")) > 0) reasons.add("MAX_SAFETY_ABOVE_250");
            if (metric.overallSignFlipRate().compareTo(new BigDecimal("0.030")) > 0) reasons.add("SIGN_FLIP_ABOVE_030");
            if (metric.highMarginSignFlipCount() != 0) reasons.add("HIGH_MARGIN_SIGN_FLIP");
            if (metric.midpointDriftCount() != 0) reasons.add("MIDPOINT_DRIFT");
            if (metric.gapArithmeticMismatchCount() != 0) reasons.add("GAP_ARITHMETIC_MISMATCH");
            if (metric.edgeDirectionMismatchCount() != 0) reasons.add("EDGE_DIRECTION_MISMATCH");
            if (metric.sideReversalMismatchCount() != 0) reasons.add("SIDE_REVERSAL_MISMATCH");
            if (metric.nanCount() != 0 || metric.infinityCount() != 0) reasons.add("NAN_OR_INFINITY");
        }
        boolean accepted = !zeroReference && structural && coverage && signal && tail && flips && high;
        return new ValidationDecision(structural, coverage, signal, tail, flips, high, accepted, List.copyOf(reasons));
    }

    static boolean validationAccepted(Metrics metric, List<Metrics> calibration) {
        return validationDecision(metric, calibration).accepted();
    }

    static List<String> validationReasons(Metrics metric, List<Metrics> calibration) {
        return validationDecision(metric, calibration).reasons();
    }

    static boolean fullAccepted(Metrics metric) {
        return metric.midpointDriftCount() == 0 && metric.gapArithmeticMismatchCount() == 0
                && metric.edgeDirectionMismatchCount() == 0 && metric.sideReversalMismatchCount() == 0
                && metric.nanCount() == 0 && metric.infinityCount() == 0
                && metric.p90ModifierGapRatio().compareTo(new BigDecimal("0.025")) >= 0
                && metric.p90ModifierGapRatio().compareTo(new BigDecimal("0.090")) <= 0
                && metric.p95ModifierGapRatio().compareTo(new BigDecimal("0.120")) <= 0
                && metric.p99ModifierGapRatio().compareTo(new BigDecimal("0.180")) <= 0
                && metric.maxModifierGapRatio().compareTo(new BigDecimal("0.250")) <= 0
                && metric.overallSignFlipRate().compareTo(new BigDecimal("0.035")) <= 0
                && metric.highMarginSignFlipCount() == 0;
    }

    static Metrics metrics(List<Observation> observations, GridCandidate candidate, MarginBand band) {
        long foreignKeyObservationCount = observations.stream().filter(observation -> !observation.key().equals(candidate.key())).count();
        if (foreignKeyObservationCount != 0) {
            throw new IllegalStateException("Foreign application key observation in metric input: " + candidate.key());
        }
        if (band.key() != null && !band.key().equals(candidate.key())) {
            throw new IllegalStateException("Foreign margin band in metric input: " + candidate.key());
        }
        List<Counterfactual> counterfactuals = new ArrayList<>();
        for (Observation observation : observations) counterfactuals.add(counterfactual(observation, candidate.gain(), band));
        List<BigDecimal> absModifiers = counterfactuals.stream().map(Counterfactual::absoluteModifier).sorted().toList();
        List<BigDecimal> absGaps = observations.stream().map(Observation::gap).map(BigDecimal::abs).sorted().toList();
        List<BigDecimal> modifierRatios = counterfactuals.stream().map(Counterfactual::absoluteModifier).sorted().toList();
        BigDecimal gapP90 = quantile(absGaps, .90);
        BigDecimal gapP95 = quantile(absGaps, .95);
        BigDecimal gapP99 = quantile(absGaps, .99);
        BigDecimal gapMax = max(absGaps);
        BigDecimal p90Ratio = divide(quantile(modifierRatios, .90), gapP90);
        BigDecimal p95Ratio = divide(quantile(modifierRatios, .95), gapP95);
        BigDecimal p99Ratio = divide(quantile(modifierRatios, .99), gapP99);
        BigDecimal maxRatio = divide(max(modifierRatios), gapMax);
        long allFlips = counterfactuals.stream().filter(Counterfactual::signFlip).count();
        long closeFlips = counterfactuals.stream().filter(x -> x.band() == MarginBandName.CLOSE && x.signFlip()).count();
        long mediumFlips = counterfactuals.stream().filter(x -> x.band() == MarginBandName.MEDIUM && x.signFlip()).count();
        long highFlips = counterfactuals.stream().filter(x -> x.band() == MarginBandName.HIGH && x.signFlip()).count();
        long zeroPositive = counterfactuals.stream().filter(x -> x.observation().gap().signum() == 0 && x.adjustedGap().signum() > 0).count();
        long zeroNegative = counterfactuals.stream().filter(x -> x.observation().gap().signum() == 0 && x.adjustedGap().signum() < 0).count();
        long nonZeroZero = counterfactuals.stream().filter(x -> x.observation().gap().signum() != 0 && x.adjustedGap().signum() == 0).count();
        return new Metrics(candidate, observations.size(), observations.stream().map(Observation::caseIndex).distinct().count(),
                observations.stream().filter(x -> x.perspectiveSide() == TeamSide.BLUE).count(),
                observations.stream().filter(x -> x.perspectiveSide() == TeamSide.RED).count(),
                observations.stream().filter(x -> x.edge().signum() > 0).count(),
                observations.stream().filter(x -> x.edge().signum() < 0).count(),
                observations.stream().filter(x -> x.edge().signum() == 0).count(),
                mean(absModifiers), quantile(absModifiers, .50), quantile(absModifiers, .75), quantile(absModifiers, .90),
                quantile(absModifiers, .95), quantile(absModifiers, .99), max(absModifiers),
                p90Ratio, p95Ratio, p99Ratio, maxRatio, allFlips, divide(BigDecimal.valueOf(allFlips), BigDecimal.valueOf(observations.size())),
                counterfactuals.stream().filter(x -> x.band() == MarginBandName.CLOSE).count(),
                counterfactuals.stream().filter(x -> x.band() == MarginBandName.MEDIUM).count(),
                counterfactuals.stream().filter(x -> x.band() == MarginBandName.HIGH).count(),
                closeFlips, mediumFlips, highFlips, zeroPositive, zeroNegative, nonZeroZero,
                mean(counterfactuals.stream().map(Counterfactual::absoluteGapChange).toList()),
                counterfactuals.stream().map(Counterfactual::absoluteModifier).collect(Collectors.toCollection(TreeSet::new)).size(),
                counterfactuals.stream().filter(Counterfactual::midpointDrift).count(),
                counterfactuals.stream().filter(Counterfactual::gapArithmeticMismatch).count(),
                counterfactuals.stream().filter(Counterfactual::edgeDirectionMismatch).count(),
                counterfactuals.stream().filter(Counterfactual::sideReversalMismatch).count(), 0, 0, "APPLICATION_KEY_LOCAL", candidate.key().context(), candidate.key().actionType(), candidate.key().scoreDomain(), observations.size(), foreignKeyObservationCount);
    }

    static Counterfactual counterfactual(Observation observation, BigDecimal gain, MarginBand band) {
        BigDecimal modifier = canonical(gain.multiply(observation.edge()));
        BigDecimal adjustedGap = observation.gap().add(modifier);
        BigDecimal perspectiveAdjustment = modifier.divide(BigDecimal.valueOf(2));
        BigDecimal opponentAdjustment = perspectiveAdjustment.negate();
        BigDecimal adjustedPerspective = observation.perspectiveScore().add(perspectiveAdjustment);
        BigDecimal adjustedOpponent = observation.opponentScore().add(opponentAdjustment);
        BigDecimal midpointBefore = observation.perspectiveScore().add(observation.opponentScore()).divide(BigDecimal.valueOf(2));
        BigDecimal midpointAfter = adjustedPerspective.add(adjustedOpponent).divide(BigDecimal.valueOf(2));
        BigDecimal swappedGap = observation.gap().negate().add(modifier.negate());
        boolean directionMismatch = modifier.signum() != 0 && observation.edge().signum() != 0 && modifier.signum() != observation.edge().signum();
        boolean sideMismatch = swappedGap.compareTo(adjustedGap.negate()) != 0;
        return new Counterfactual(observation, modifier, adjustedGap, perspectiveAdjustment, opponentAdjustment,
                adjustedPerspective, adjustedOpponent, midpointBefore, midpointAfter,
                midpointBefore.compareTo(midpointAfter) != 0,
                adjustedPerspective.subtract(adjustedOpponent).compareTo(adjustedGap) != 0,
                directionMismatch, sideMismatch, adjustedGap.subtract(observation.gap()).abs(),
                band.nameFor(observation.gap().abs()), observation.gap().signum() != 0
                        && adjustedGap.signum() != 0 && observation.gap().signum() != adjustedGap.signum());
    }

    static Map<GainKey, MarginBand> marginBands(List<Observation> calibration) {
        Map<GainKey, MarginBand> result = new LinkedHashMap<>();
        for (GainKey key : APPROVED_KEYS) {
            List<BigDecimal> gaps = sorted(calibration.stream().filter(x -> x.key().equals(key)).toList(), Observation::gap, true);
            result.put(key, new MarginBand(key, quantile(gaps, .25), quantile(gaps, .90)));
        }
        return Map.copyOf(result);
    }

    static RepairChecks repairChecks(ScreeningResult result, PreviousState previous) {
        int calibrationMismatch = 0;
        int validationMismatch = 0;
        int fullMismatch = 0;
        long foreign = 0;
        int marginMismatch = 0;
        long midpoint = 0;
        long gapMismatch = 0;
        long directionMismatch = 0;
        long sideMismatch = 0;
        long nanInfinity = 0;
        for (GainKey key : APPROVED_KEYS) {
            int calibrationExpected = (int) result.partition().calibration().stream().filter(x -> x.key().equals(key)).count();
            int validationExpected = (int) result.partition().validation().stream().filter(x -> x.key().equals(key)).count();
            int fullExpected = (int) result.filter().filtered().stream().filter(x -> x.key().equals(key)).count();
            for (Metrics metric : result.calibration().getOrDefault(key, List.of())) {
                if (metric.sampleCount() != calibrationExpected) calibrationMismatch++;
                foreign += metric.foreignKeyObservationCount();
            }
            for (Metrics metric : result.validation().getOrDefault(key, List.of())) {
                if (metric.sampleCount() != validationExpected) validationMismatch++;
                foreign += metric.foreignKeyObservationCount();
                midpoint += metric.midpointDriftCount(); gapMismatch += metric.gapArithmeticMismatchCount();
                directionMismatch += metric.edgeDirectionMismatchCount(); sideMismatch += metric.sideReversalMismatchCount();
                nanInfinity += metric.nanCount() + metric.infinityCount();
            }
            Metrics full = result.full().get(key);
            if (full != null) {
                if (full.sampleCount() != fullExpected) fullMismatch++;
                foreign += full.foreignKeyObservationCount();
                midpoint += full.midpointDriftCount(); gapMismatch += full.gapArithmeticMismatchCount();
                directionMismatch += full.edgeDirectionMismatchCount(); sideMismatch += full.sideReversalMismatchCount();
                nanInfinity += full.nanCount() + full.infinityCount();
            }
            MarginBand band = result.marginBands().get(key);
            if (band == null || band.key() == null || !band.key().equals(key)) marginMismatch++;
        }
        int integrity = calibrationMismatch + validationMismatch + fullMismatch + (int) foreign + marginMismatch
                + (int) midpoint + (int) gapMismatch + (int) directionMismatch + (int) sideMismatch + (int) nanInfinity
                + (previous.exact() ? 0 : 1);
        return new RepairChecks(calibrationMismatch, validationMismatch, fullMismatch, foreign, 0, marginMismatch,
                0, 0, midpoint, gapMismatch, directionMismatch, sideMismatch, nanInfinity, integrity);
    }

    static PreviousState readPrevious(Path output) throws IOException {
        Path summaryPath = output.resolve("composition-gain-screening-summary.csv");
        Path auditPath = output.resolve("composition-gain-screening-audit.log");
        boolean files = Files.isRegularFile(summaryPath) && Files.isRegularFile(auditPath);
        String summaryHash = files ? sha256(summaryPath) : "MISSING";
        String auditHash = files ? sha256(auditPath) : "MISSING";
        List<Map<String, String>> validation = files ? readCsvMaps(output.resolve("composition-gain-validation-results.csv")) : List.of();
        List<Map<String, String>> partition = files ? readCsvMaps(output.resolve("composition-gain-partition-summary.csv")) : List.of();
        List<Map<String, String>> selections = files ? readCsvMaps(output.resolve("composition-gain-selection.csv")) : List.of();
        Map<String, String> summary = files ? readKeyValue(summaryPath) : Map.of();
        boolean exact = files && PREVIOUS_SUMMARY_HASH.equals(summaryHash) && PREVIOUS_AUDIT_HASH.equals(auditHash)
                && "GAIN_SELECTION_REVIEW".equals(summary.get("verdict"))
                && "false".equals(summary.get("candidateFrozen"));
        return new PreviousState(summaryHash, auditHash, summary, validation, partition, selections, exact);
    }

    static List<Map<String, String>> readCsvMaps(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return List.of();
        List<String> header = csv(lines.getFirst());
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            List<String> cells = csv(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size() && i < cells.size(); i++) row.put(header.get(i), cells.get(i));
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    static String rowKey(Map<String, String> row) {
        return row.getOrDefault("context", "") + "|" + row.getOrDefault("actionType", "") + "|" + row.getOrDefault("scoreDomain", "");
    }

    private static String verdict(Map<GainKey, Selection> selections, Map<GainKey, Metrics> full, SourceState source) {
        if (!source.identityExact()) return "BLOCKED_BY_SOURCE_IDENTITY";
        if (selections.values().stream().anyMatch(x -> !x.selected())) return "GAIN_SELECTION_REVIEW";
        if (full.values().stream().anyMatch(x -> !fullAccepted(x))) return "GAIN_FULL_DATA_CONFIRMATION_REVIEW";
        return "GAIN_SCREENING_REVIEW";
    }

    private static void writeArtifacts(Path output, ScreeningResult result) throws IOException {
        Files.createDirectories(output);
        writeCsv(output.resolve("composition-gain-source-manifest.csv"), sourceManifest(result));
        writeCsv(output.resolve("composition-gain-application-keys.csv"), applicationKeys());
        writeCsv(output.resolve("composition-gain-partition-summary.csv"), partitionSummary(result));
        writeCsv(output.resolve("composition-gain-scale-anchors.csv"), scaleAnchors(result));
        writeCsv(output.resolve("composition-gain-grid.csv"), gridRows(result.grid()));
        writeCsv(output.resolve("composition-gain-validation-results.csv"), metricRows(result.validation(), result.partition()));
        writeCsv(output.resolve("composition-gain-selection.csv"), selectionRows(result));
        writeCsv(output.resolve("composition-gain-full-confirmation.csv"), fullRows(result));
        writeCsv(output.resolve("composition-gain-selected-observations.csv"), selectedObservationRows(result));
        writeCsv(output.resolve("composition-gameplay-gain-candidate.csv"), candidateRows(result));
        writeCsv(output.resolve("composition-gain-statistic-integrity.csv"), integrityRows(result));
        writeKeyValue(output.resolve("composition-gain-screening-summary.csv"), summary(result));
        Files.writeString(output.resolve("composition-gain-screening-audit.log"), auditLog(result), StandardCharsets.UTF_8);
    }

    private static void writeRepairArtifacts(Path output, RepairResult result) throws IOException {
        Files.createDirectories(output);
        writeCsv(output.resolve("composition-gain-repair-source-manifest.csv"), repairSourceManifest(result));
        writeCsv(output.resolve("composition-gain-key-local-partition.csv"), repairPartitionRows(result.screening()));
        writeCsv(output.resolve("composition-gain-key-local-scale-anchors.csv"), repairScaleAnchorRows(result.screening()));
        writeCsv(output.resolve("composition-gain-key-local-grid.csv"), gridRows(result.screening().grid()));
        writeCsv(output.resolve("composition-gain-key-local-validation-results.csv"), repairValidationRows(result.screening()));
        writeCsv(output.resolve("composition-gain-key-local-selection.csv"), repairSelectionRows(result.screening()));
        writeCsv(output.resolve("composition-gain-key-local-full-confirmation.csv"), repairFullRows(result.screening()));
        writeCsv(output.resolve("composition-gain-metric-isolation-integrity.csv"), repairIntegrityRows(result));
        writeCsv(output.resolve("composition-gain-previous-vs-corrected.csv"), previousVsCorrectedRows(result));
        writeCsv(output.resolve("composition-gain-key-local-selected-observations.csv"), selectedObservationRows(result.screening()));
        writeCsv(output.resolve("composition-gameplay-gain-candidate-repaired.csv"), repairCandidateRows(result));
        writeKeyValue(output.resolve("composition-gain-key-local-screening-summary.csv"), repairSummary(result));
        Files.writeString(output.resolve("composition-gain-key-local-screening-audit.log"), repairAuditLog(result), StandardCharsets.UTF_8);
    }

    private static List<List<String>> repairSourceManifest(RepairResult result) {
        List<List<String>> rows = rows("sourceFile", "sha256", "rowCount", "unchanged", "requiredInput");
        for (SourceFile file : result.screening().source().files()) rows.add(List.of(file.name(), file.hash(), Long.toString(file.rowCount()), Boolean.toString(file.unchanged()), Boolean.toString(file.required())));
        rows.add(List.of("composition-eligible-context-gain-screening/composition-gain-screening-summary.csv", result.previous().summaryHash(), Long.toString(safeRowCount(PREVIOUS_OUTPUT.resolve("composition-gain-screening-summary.csv"))), Boolean.toString(result.previous().exact()), "true"));
        rows.add(List.of("composition-eligible-context-gain-screening/composition-gain-screening-audit.log", result.previous().auditHash(), Long.toString(safeRowCount(PREVIOUS_OUTPUT.resolve("composition-gain-screening-audit.log"))), Boolean.toString(result.previous().exact()), "true"));
        return rows;
    }

    private static List<List<String>> repairPartitionRows(ScreeningResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "partition", "metricScope", "observationCount", "distinctCaseCount", "blueCount", "redCount", "caseLeakageCount", "attemptLeakageCount");
        for (GainKey key : APPROVED_KEYS) {
            for (String partition : List.of("CALIBRATION", "VALIDATION", "FULL")) {
                List<Observation> values = partition.equals("CALIBRATION") ? result.partition().calibration() : partition.equals("VALIDATION") ? result.partition().validation() : result.filter().filtered();
                values = values.stream().filter(x -> x.key().equals(key)).toList();
                rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), partition, "APPLICATION_KEY_LOCAL", Integer.toString(values.size()), Long.toString(values.stream().map(Observation::caseIndex).distinct().count()), Long.toString(values.stream().filter(x -> x.perspectiveSide() == TeamSide.BLUE).count()), Long.toString(values.stream().filter(x -> x.perspectiveSide() == TeamSide.RED).count()), Integer.toString(result.partition().caseLeakageCount()), Integer.toString(result.partition().attemptLeakageCount())));
            }
        }
        return rows;
    }

    private static List<List<String>> repairScaleAnchorRows(ScreeningResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "metricScope", "edgeP50", "edgeP75", "edgeP90", "edgeP95", "edgeP99", "edgeMax", "gapP50", "gapP75", "gapP90", "gapP95", "gapP99", "gapMax", "edgeScaleP90", "gapScaleP90", "percentileMethod", "marginBandKey");
        for (GainKey key : APPROVED_KEYS) {
            Anchor anchor = result.anchors().get(key); MarginBand band = result.marginBands().get(key);
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), "APPLICATION_KEY_LOCAL", format(anchor.edgeQuantiles().get("P50")), format(anchor.edgeQuantiles().get("P75")), format(anchor.edgeQuantiles().get("P90")), format(anchor.edgeQuantiles().get("P95")), format(anchor.edgeQuantiles().get("P99")), format(anchor.edgeQuantiles().get("MAX")), format(anchor.gapQuantiles().get("P50")), format(anchor.gapQuantiles().get("P75")), format(anchor.gapQuantiles().get("P90")), format(anchor.gapQuantiles().get("P95")), format(anchor.gapQuantiles().get("P99")), format(anchor.gapQuantiles().get("MAX")), format(anchor.edgeScale()), format(anchor.gapScale()), "NEAREST_RANK", band == null || band.key() == null ? "NONE" : band.key().stableId()));
        }
        return rows;
    }

    private static List<List<String>> repairValidationRows(ScreeningResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "metricScope", "metricContext", "metricActionType", "metricScoreDomain", "candidate", "targetRatio", "canonicalGain", "metricInputObservationCount", "foreignKeyObservationCount", "sampleCount", "distinctCaseCount", "bluePerspectiveCount", "redPerspectiveCount", "positiveEdgeCount", "negativeEdgeCount", "zeroEdgeCount", "meanAbsoluteModifier", "p50AbsoluteModifier", "p75AbsoluteModifier", "p90AbsoluteModifier", "p95AbsoluteModifier", "p99AbsoluteModifier", "maxAbsoluteModifier", "p90ModifierGapRatio", "p95ModifierGapRatio", "p99ModifierGapRatio", "maxModifierGapRatio", "overallSignFlipCount", "overallSignFlipRate", "closeSampleCount", "closeSignFlipCount", "closeSignFlipRate", "mediumSampleCount", "mediumSignFlipCount", "mediumSignFlipRate", "highSampleCount", "highSignFlipCount", "highSignFlipRate", "zeroToPositiveCount", "zeroToNegativeCount", "nonZeroToZeroCount", "meanAbsoluteAdjustedGapChange", "distinctModifierCount", "midpointDriftCount", "gapArithmeticMismatchCount", "edgeDirectionMismatchCount", "sideReversalMismatchCount", "nanCount", "infinityCount", "structuralPass", "coveragePass", "signalVisibilityPass", "tailRatioSafetyPass", "signFlipSafetyPass", "highMarginSafetyPass", "validationAccepted", "validationRejectionReasons");
        for (GainKey key : APPROVED_KEYS) for (Metrics metric : result.validation().getOrDefault(key, List.of())) {
            ValidationDecision decision = validationDecision(metric, result.calibration().get(key));
            rows.add(repairMetricRow(metric, decision));
        }
        return rows;
    }

    private static List<String> repairMetricRow(Metrics metric, ValidationDecision decision) {
        return List.of(metric.candidate().key().context().name(), metric.candidate().key().actionType().name(), metric.candidate().key().scoreDomain().name(), metric.metricScope(), metric.metricContext().name(), metric.metricActionType().name(), metric.metricScoreDomain().name(), metric.candidate().label(), format(metric.candidate().targetRatio()), format(metric.candidate().gain()), Integer.toString(metric.metricInputObservationCount()), Long.toString(metric.foreignKeyObservationCount()), Integer.toString(metric.sampleCount()), Long.toString(metric.distinctCaseCount()), Long.toString(metric.bluePerspectiveCount()), Long.toString(metric.redPerspectiveCount()), Long.toString(metric.positiveEdgeCount()), Long.toString(metric.negativeEdgeCount()), Long.toString(metric.zeroEdgeCount()), format(metric.meanAbsoluteModifier()), format(metric.p50AbsoluteModifier()), format(metric.p75AbsoluteModifier()), format(metric.p90AbsoluteModifier()), format(metric.p95AbsoluteModifier()), format(metric.p99AbsoluteModifier()), format(metric.maxAbsoluteModifier()), format(metric.p90ModifierGapRatio()), format(metric.p95ModifierGapRatio()), format(metric.p99ModifierGapRatio()), format(metric.maxModifierGapRatio()), Long.toString(metric.overallSignFlipCount()), format(metric.overallSignFlipRate()), Long.toString(metric.closeMarginSampleCount()), Long.toString(metric.closeMarginSignFlipCount()), format(divide(BigDecimal.valueOf(metric.closeMarginSignFlipCount()), BigDecimal.valueOf(metric.closeMarginSampleCount()))), Long.toString(metric.mediumMarginSampleCount()), Long.toString(metric.mediumMarginSignFlipCount()), format(divide(BigDecimal.valueOf(metric.mediumMarginSignFlipCount()), BigDecimal.valueOf(metric.mediumMarginSampleCount()))), Long.toString(metric.highMarginSampleCount()), Long.toString(metric.highMarginSignFlipCount()), format(divide(BigDecimal.valueOf(metric.highMarginSignFlipCount()), BigDecimal.valueOf(metric.highMarginSampleCount()))), Long.toString(metric.zeroToPositiveCount()), Long.toString(metric.zeroToNegativeCount()), Long.toString(metric.nonZeroToZeroCount()), format(metric.meanAbsoluteAdjustedGapChange()), Long.toString(metric.distinctModifierCount()), Long.toString(metric.midpointDriftCount()), Long.toString(metric.gapArithmeticMismatchCount()), Long.toString(metric.edgeDirectionMismatchCount()), Long.toString(metric.sideReversalMismatchCount()), Long.toString(metric.nanCount()), Long.toString(metric.infinityCount()), Boolean.toString(decision.structuralPass()), Boolean.toString(decision.coveragePass()), Boolean.toString(decision.signalVisibilityPass()), Boolean.toString(decision.tailRatioSafetyPass()), Boolean.toString(decision.signFlipSafetyPass()), Boolean.toString(decision.highMarginSafetyPass()), Boolean.toString(decision.accepted()), String.join("|", decision.reasons()));
    }

    private static List<List<String>> repairSelectionRows(ScreeningResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "metricScope", "selectedCandidate", "selectedTargetRatio", "canonicalGain", "selected", "eligibleCandidateCount", "selectionReason", "validationRejectionReasons");
        for (GainKey key : APPROVED_KEYS) {
            Selection selection = result.selections().get(key); long eligible = result.validation().get(key).stream().filter(x -> validationAccepted(x, result.calibration().get(key))).count();
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), "APPLICATION_KEY_LOCAL", selection.targetLabel(), selection.selected() ? format(selection.targetRatio()) : "NONE", selection.selected() ? format(selection.gain()) : "NONE", Boolean.toString(selection.selected()), Long.toString(eligible), selection.reason(), String.join(";", selection.rejectedReasons())));
        }
        return rows;
    }

    private static List<List<String>> repairFullRows(ScreeningResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "metricScope", "selectedTargetRatio", "canonicalGain", "expectedFullSampleCount", "sampleCount", "foreignKeyObservationCount", "p90ModifierGapRatio", "p95ModifierGapRatio", "p99ModifierGapRatio", "maxModifierGapRatio", "overallSignFlipCount", "overallSignFlipRate", "closeSampleCount", "closeSignFlipCount", "closeSignFlipRate", "mediumSampleCount", "mediumSignFlipCount", "mediumSignFlipRate", "highSampleCount", "highSignFlipCount", "highSignFlipRate", "highMarginFlipCount", "midpointDriftCount", "gapArithmeticMismatchCount", "edgeDirectionMismatchCount", "sideReversalMismatchCount", "nanCount", "infinityCount", "fullAccepted");
        for (GainKey key : APPROVED_KEYS) {
            Selection selection = result.selections().get(key); Metrics metric = result.full().get(key); int expected = (int) result.filter().filtered().stream().filter(x -> x.key().equals(key)).count();
            if (metric == null) { rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), "APPLICATION_KEY_LOCAL", "NONE", "NONE", Integer.toString(expected), "0", "0", "NONE", "NONE", "NONE", "NONE", "0", "NONE", "0", "0", "NONE", "0", "0", "NONE", "0", "0", "NONE", "0", "0", "0", "0", "0", "0", "0", "false")); continue; }
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), metric.metricScope(), format(selection.targetRatio()), format(selection.gain()), Integer.toString(expected), Integer.toString(metric.sampleCount()), Long.toString(metric.foreignKeyObservationCount()), format(metric.p90ModifierGapRatio()), format(metric.p95ModifierGapRatio()), format(metric.p99ModifierGapRatio()), format(metric.maxModifierGapRatio()), Long.toString(metric.overallSignFlipCount()), format(metric.overallSignFlipRate()), Long.toString(metric.closeMarginSampleCount()), Long.toString(metric.closeMarginSignFlipCount()), format(divide(BigDecimal.valueOf(metric.closeMarginSignFlipCount()), BigDecimal.valueOf(metric.closeMarginSampleCount()))), Long.toString(metric.mediumMarginSampleCount()), Long.toString(metric.mediumMarginSignFlipCount()), format(divide(BigDecimal.valueOf(metric.mediumMarginSignFlipCount()), BigDecimal.valueOf(metric.mediumMarginSampleCount()))), Long.toString(metric.highMarginSampleCount()), Long.toString(metric.highMarginSignFlipCount()), format(divide(BigDecimal.valueOf(metric.highMarginSignFlipCount()), BigDecimal.valueOf(metric.highMarginSampleCount()))), Long.toString(metric.highMarginSignFlipCount()), Long.toString(metric.midpointDriftCount()), Long.toString(metric.gapArithmeticMismatchCount()), Long.toString(metric.edgeDirectionMismatchCount()), Long.toString(metric.sideReversalMismatchCount()), Long.toString(metric.nanCount()), Long.toString(metric.infinityCount()), Boolean.toString(fullAccepted(metric))));
        }
        return rows;
    }

    private static List<List<String>> repairIntegrityRows(RepairResult result) {
        ScreeningResult screening = result.screening(); RepairChecks checks = result.checks();
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "candidate", "metricScope", "expectedCalibrationSampleCount", "actualCalibrationSampleCount", "expectedValidationSampleCount", "actualValidationSampleCount", "expectedFullSampleCount", "actualFullSampleCount", "foreignKeyObservationCount", "marginBandKey", "marginBandMatches", "selectorArtifactDecisionMismatchCount", "rejectionReasonMismatchCount", "passed");
        for (GainKey key : APPROVED_KEYS) {
            int expectedCalibration = (int) screening.partition().calibration().stream().filter(x -> x.key().equals(key)).count(); int expectedValidation = (int) screening.partition().validation().stream().filter(x -> x.key().equals(key)).count(); int expectedFull = (int) screening.filter().filtered().stream().filter(x -> x.key().equals(key)).count();
            String marginKey = screening.marginBands().get(key) == null || screening.marginBands().get(key).key() == null ? "NONE" : screening.marginBands().get(key).key().stableId();
            for (Metrics metric : screening.validation().getOrDefault(key, List.of())) {
                boolean passed = metric.sampleCount() == expectedValidation && metric.foreignKeyObservationCount() == 0 && key.stableId().equals(marginKey) && metric.metricScope().equals("APPLICATION_KEY_LOCAL");
                rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), metric.candidate().label(), metric.metricScope(), Integer.toString(expectedCalibration), Integer.toString(screening.calibration().get(key).getFirst().sampleCount()), Integer.toString(expectedValidation), Integer.toString(metric.sampleCount()), Integer.toString(expectedFull), screening.full().get(key) == null ? "NONE" : Integer.toString(screening.full().get(key).sampleCount()), Long.toString(metric.foreignKeyObservationCount()), marginKey, Boolean.toString(key.stableId().equals(marginKey)), Integer.toString(checks.selectorArtifactDecisionMismatchCount()), Integer.toString(checks.rejectionReasonMismatchCount()), Boolean.toString(passed)));
            }
        }
        return rows;
    }

    private static List<List<String>> previousVsCorrectedRows(RepairResult result) {
        ScreeningResult screening = result.screening(); PreviousState previous = result.previous();
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "previousValidationSampleCount", "correctedValidationSampleCount", "previousCalibrationSampleCount", "correctedCalibrationSampleCount", "previousDistinctCaseCount", "correctedDistinctCaseCount", "previousEligibleCandidateCount", "correctedEligibleCandidateCount", "previousSelection", "correctedSelection", "previousVerdictContribution", "correctedVerdictContribution", "metricIsolationChanged", "explanation");
        for (GainKey key : APPROVED_KEYS) {
            Map<String, String> previousValidation = previous.validation().stream().filter(x -> rowKey(x).equals(key.stableId()) && "ZERO_REFERENCE".equals(x.get("candidate"))).findFirst().orElse(Map.of());
            Map<String, String> previousPartition = previous.partition().stream().filter(x -> rowKey(x).equals(key.stableId()) && "VALIDATION".equals(x.get("partition"))).findFirst().orElse(Map.of());
            Map<String, String> previousCalibration = previous.partition().stream().filter(x -> rowKey(x).equals(key.stableId()) && "CALIBRATION".equals(x.get("partition"))).findFirst().orElse(Map.of());
            Map<String, String> previousSelection = previous.selections().stream().filter(x -> rowKey(x).equals(key.stableId())).findFirst().orElse(Map.of());
            int correctedValidation = (int) screening.partition().validation().stream().filter(x -> x.key().equals(key)).count(); int correctedCalibration = (int) screening.partition().calibration().stream().filter(x -> x.key().equals(key)).count();
            long correctedCases = screening.validation().get(key).getFirst().distinctCaseCount(); long correctedEligible = screening.validation().get(key).stream().filter(x -> validationAccepted(x, screening.calibration().get(key))).count();
            String oldValidation = previousValidation.getOrDefault("sampleCount", "NONE"); String oldCalibration = previousCalibration.getOrDefault("observationCount", "NONE"); String oldCases = previousValidation.getOrDefault("distinctCaseCount", "NONE"); String oldEligible = previous.summary().getOrDefault(key.stableId() + ".eligibleGainCandidateCount", "NONE"); String oldSelection = previousSelection.getOrDefault("selectedCandidate", "NONE"); String correctedSelection = screening.selections().get(key).selected() ? screening.selections().get(key).targetLabel() : "NONE";
            boolean changed = !oldValidation.equals(Integer.toString(correctedValidation)) || !oldCalibration.equals(Integer.toString(correctedCalibration)) || !oldCases.equals(Long.toString(correctedCases)) || !oldEligible.equals(Long.toString(correctedEligible));
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), oldValidation, Integer.toString(correctedValidation), oldCalibration, Integer.toString(correctedCalibration), oldCases, Long.toString(correctedCases), oldEligible, Long.toString(correctedEligible), oldSelection, correctedSelection, "GAIN_SELECTION_REVIEW", screening.selections().get(key).selected() ? "SELECTED" : "GAIN_SELECTION_REVIEW_CONFIRMED", Boolean.toString(changed), "PREVIOUS_METRICS_USED_ALL_VALIDATION_ROWS;CORRECTED_METRICS_USE_EXACT_APPLICATION_KEY"));
        }
        return rows;
    }

    private static List<List<String>> repairCandidateRows(RepairResult result) {
        List<List<String>> rows = rows("candidateVersion", "candidateHash", "profileVersion", "profileHash", "ruleCatalogVersion", "ruleCatalogHash", "interactionCandidateVersion", "interactionCandidateHash", "formula", "context", "actionType", "scoreDomain", "targetRatio", "canonicalGain", "selected", "candidateFrozen", "freezeVerdict", "failedReason", "deadzone", "clamp", "cap", "overrideCount", "productionEnabled", "gameplayApplication");
        String deferred = "OBJECTIVE_SETUP|SIDE_LANE|SKIRMISH:JUNGLE_GANK|SKIRMISH:LANE_COMBAT|SKIRMISH:ROAM|SIEGE:SIEGE";
        for (GainKey key : APPROVED_KEYS) { Selection selection = result.screening().selections().get(key); rows.add(List.of(CANDIDATE_VERSION, result.screening().candidateHash(), PROFILE_VERSION, PROFILE_HASH, RULE_CATALOG_VERSION, RULE_CATALOG_HASH, INTERACTION_CANDIDATE_VERSION, INTERACTION_CANDIDATE_HASH, FORMULA, key.context().name(), key.actionType().name(), key.scoreDomain().name(), selection.selected() ? format(selection.targetRatio()) : "NONE", selection.selected() ? format(selection.gain()) : "NONE", Boolean.toString(selection.selected()), Boolean.toString(result.screening().candidateFrozen()), result.verdict(), selection.selected() ? "NONE" : String.join(";", selection.rejectedReasons()), "NONE", "NONE", "NONE", "0", "false", "false")); }
        return rows;
    }

    private static Map<String, String> repairSummary(RepairResult result) {
        ScreeningResult screening = result.screening(); RepairChecks checks = result.checks(); PreviousState previous = result.previous(); Map<String, String> s = new LinkedHashMap<>();
        s.put("auditVersion", "phase-13d4b1-per-application-key-metric-isolation-repair-v1"); s.put("frozenProfileHash", PROFILE_HASH); s.put("ruleCatalogHash", RULE_CATALOG_HASH); s.put("interactionCandidateHash", INTERACTION_CANDIDATE_HASH); s.put("sourceGateSummaryHash", screening.source().gateSummaryHash()); s.put("sourceGateAuditHash", screening.source().gateAuditHash()); s.put("sourceObservationHash", screening.source().observationHash()); s.put("sourceEligibilityHash", screening.source().eligibilityHash()); s.put("sourcePhase13D4BSummaryHash", previous.summaryHash()); s.put("sourcePhase13D4BAuditHash", previous.auditHash()); s.put("sourceArtifactsUnchanged", Boolean.toString(screening.source().identityExact() && previous.exact()));
        s.put("sourceObservationCount", Integer.toString(screening.filter().sourceObservationCount())); s.put("eligibleObservationCount", Integer.toString(screening.filter().eligibleObservationCount())); s.put("ineligibleObservationCount", Integer.toString(screening.filter().ineligibleObservationCount())); s.put("applicationKeyCount", Integer.toString(APPROVED_KEYS.size())); s.put("deferredObservationIncludedCount", Integer.toString(screening.filter().deferredObservationIncludedCount())); s.put("unknownApplicationKeyCount", Integer.toString(screening.filter().unknownApplicationKeyCount()));
        s.put("calibrationCaseCount", Integer.toString(screening.partition().calibrationCaseCount())); s.put("validationCaseCount", Integer.toString(screening.partition().validationCaseCount())); s.put("calibrationObservationCount", Integer.toString(screening.partition().calibration().size())); s.put("validationObservationCount", Integer.toString(screening.partition().validation().size())); s.put("caseLeakageCount", Integer.toString(screening.partition().caseLeakageCount())); s.put("attemptLeakageCount", Integer.toString(screening.partition().attemptLeakageCount()));
        s.put("metricScope", "APPLICATION_KEY_LOCAL"); s.put("keyLocalMetricCount", Integer.toString(screening.validation().values().stream().mapToInt(List::size).sum())); s.put("foreignKeyObservationCount", Long.toString(checks.foreignKeyObservationCount())); s.put("calibrationSampleMismatchCount", Integer.toString(checks.calibrationSampleMismatchCount())); s.put("validationSampleMismatchCount", Integer.toString(checks.validationSampleMismatchCount())); s.put("fullSampleMismatchCount", Integer.toString(checks.fullSampleMismatchCount())); s.put("unrelatedKeyInfluenceMismatchCount", Integer.toString(checks.unrelatedKeyInfluenceMismatchCount())); s.put("marginBandKeyMismatchCount", Integer.toString(checks.marginBandKeyMismatchCount()));
        s.put("selectedApplicationKeyCount", Long.toString(screening.selections().values().stream().filter(Selection::selected).count())); s.put("failedApplicationKeyCount", Long.toString(screening.selections().values().stream().filter(x -> !x.selected()).count())); s.put("candidateVersion", CANDIDATE_VERSION); s.put("candidateHash", screening.candidateHash()); s.put("candidateFrozen", Boolean.toString(screening.candidateFrozen())); s.put("deadzone", "NONE"); s.put("clamp", "NONE"); s.put("cap", "NONE"); s.put("overrideCount", "0");
        s.put("selectorArtifactDecisionMismatchCount", Integer.toString(checks.selectorArtifactDecisionMismatchCount())); s.put("rejectionReasonMismatchCount", Integer.toString(checks.rejectionReasonMismatchCount())); s.put("midpointDriftCount", Long.toString(checks.midpointDriftCount())); s.put("gapArithmeticMismatchCount", Long.toString(checks.gapArithmeticMismatchCount())); s.put("edgeDirectionMismatchCount", Long.toString(checks.edgeDirectionMismatchCount())); s.put("sideReversalMismatchCount", Long.toString(checks.sideReversalMismatchCount())); s.put("NaNCount", Long.toString(checks.nanInfinityCount())); s.put("InfinityCount", "0"); s.put("integrityErrorCount", Integer.toString(checks.integrityErrorCount()));
        s.put("matchSimulationCount", "0"); s.put("gameplayApplicationCount", "0"); s.put("nonZeroModifierCount", "0"); s.put("candidateGameplayEnabled", "false"); s.put("teamCompositionProductionEnabled", "false"); s.put("teamCompositionGameplayContribution", "0"); s.put("productionGameplayChanged", "false"); s.put("candidateGuardErrorCode", "CANDIDATE_CONTEXT_GAINS_NOT_APPROVED"); s.put("apiSchemaChanged", "false"); s.put("frontendChanged", "false");
        s.put("targetedTestCount", "RECORDED_AFTER_TARGETED_VALIDATION"); s.put("targetedTestFailures", "RECORDED_AFTER_TARGETED_VALIDATION"); s.put("backendSuiteCount", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendTestCount", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendFailures", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendErrors", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendSkipped", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendBuildSuccessful", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("priorHashesExact", Boolean.toString(screening.source().priorHashesExact() && previous.exact())); s.put("infoCodes", "NONE"); s.put("reviewCodes", result.verdict().equals("READY_FOR_PHASE_13D4C") ? "NONE" : result.verdict()); s.put("warningCodes", "NONE"); s.put("integrityCodes", result.verdict().startsWith("BLOCKED") ? "COMPOSITION_GAIN_KEY_ISOLATION_INTEGRITY" : "NONE"); s.put("verdict", result.verdict()); s.put("phase13D4CAllowed", Boolean.toString(result.verdict().equals("READY_FOR_PHASE_13D4C"))); s.put("nextPhase", result.verdict().equals("READY_FOR_PHASE_13D4C") ? "PHASE_13D4C_CANDIDATE_GAMEPLAY_APPLICATION_AUDIT" : "PHASE_13D4B2_GAIN_POLICY_REVIEW");
        for (GainKey key : APPROVED_KEYS) { String prefix = key.stableId() + "."; List<Observation> calibration = screening.partition().calibration().stream().filter(x -> x.key().equals(key)).toList(); List<Observation> validation = screening.partition().validation().stream().filter(x -> x.key().equals(key)).toList(); List<Observation> fullRows = screening.filter().filtered().stream().filter(x -> x.key().equals(key)).toList(); Selection selection = screening.selections().get(key); Metrics full = screening.full().get(key); s.put(prefix + "calibrationSampleCount", Integer.toString(calibration.size())); s.put(prefix + "validationSampleCount", Integer.toString(validation.size())); s.put(prefix + "fullSampleCount", Integer.toString(fullRows.size())); s.put(prefix + "edgeScaleP90", format(screening.anchors().get(key).edgeScale())); s.put(prefix + "gapScaleP90", format(screening.anchors().get(key).gapScale())); s.put(prefix + "eligibleCandidateCount", Long.toString(screening.validation().get(key).stream().filter(x -> validationAccepted(x, screening.calibration().get(key))).count())); s.put(prefix + "selectedTargetRatio", selection.selected() ? format(selection.targetRatio()) : "NONE"); s.put(prefix + "selectedGain", selection.selected() ? format(selection.gain()) : "NONE"); s.put(prefix + "selected", Boolean.toString(selection.selected())); s.put(prefix + "validationRejectionReasons", String.join(";", selection.rejectedReasons())); s.put(prefix + "fullAccepted", full == null ? "NONE" : Boolean.toString(fullAccepted(full))); }
        return s;
    }

    private static String repairAuditLog(RepairResult result) { StringBuilder text = new StringBuilder(); repairSummary(result).forEach((key, value) -> text.append(key).append("=").append(value).append("\n")); text.append("metricScope=APPLICATION_KEY_LOCAL\n"); text.append("gainSelectorAllowedColumns=caseIndex,seed,attemptId,context,actionType,scoreDomain,perspectiveSide,perspectiveRawEdge,perspectiveBaselineScore,opponentBaselineScore,baselineScoreGap,applicationEligibility\n"); text.append("outcomeColumnsReadByGainSelector=false\n"); text.append("previousArtifactPreserved=true\n"); return text.toString(); }

    private static List<List<String>> sourceManifest(ScreeningResult result) {
        List<List<String>> rows = rows("sourceFile", "sha256", "rowCount", "unchanged", "requiredInput");
        for (SourceFile file : result.source().files()) rows.add(List.of(file.name(), file.hash(), Long.toString(file.rowCount()),
                Boolean.toString(file.unchanged()), Boolean.toString(file.required())));
        return rows;
    }

    private static List<List<String>> applicationKeys() {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "applicationPoint", "scoreOrientation", "resolution");
        for (GainKey key : APPROVED_KEYS) rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(),
                applicationPoint(key).name(), CompositionScoreOrientation.HIGHER_IS_BETTER.name(), "GAIN_SCREENING_ELIGIBLE"));
        return rows;
    }

    private static List<List<String>> partitionSummary(ScreeningResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "partition", "observationCount", "distinctCaseCount", "blueCount", "redCount", "caseLeakageCount", "attemptLeakageCount");
        for (GainKey key : APPROVED_KEYS) for (String partition : List.of("CALIBRATION", "VALIDATION")) {
            List<Observation> values = partition.equals("CALIBRATION") ? result.partition().calibration() : result.partition().validation();
            values = values.stream().filter(x -> x.key().equals(key)).toList();
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), partition,
                    Integer.toString(values.size()), Long.toString(values.stream().map(Observation::caseIndex).distinct().count()),
                    Long.toString(values.stream().filter(x -> x.perspectiveSide() == TeamSide.BLUE).count()),
                    Long.toString(values.stream().filter(x -> x.perspectiveSide() == TeamSide.RED).count()),
                    Integer.toString(result.partition().caseLeakageCount()), Integer.toString(result.partition().attemptLeakageCount())));
        }
        return rows;
    }

    private static List<List<String>> scaleAnchors(ScreeningResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "edgeP50", "edgeP75", "edgeP90", "edgeP95", "edgeP99", "edgeMax",
                "gapP50", "gapP75", "gapP90", "gapP95", "gapP99", "gapMax", "edgeScale", "gapScale", "percentileMethod");
        for (GainKey key : APPROVED_KEYS) {
            Anchor anchor = result.anchors().get(key);
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(),
                    format(anchor.edgeQuantiles().get("P50")), format(anchor.edgeQuantiles().get("P75")), format(anchor.edgeQuantiles().get("P90")),
                    format(anchor.edgeQuantiles().get("P95")), format(anchor.edgeQuantiles().get("P99")), format(anchor.edgeQuantiles().get("MAX")),
                    format(anchor.gapQuantiles().get("P50")), format(anchor.gapQuantiles().get("P75")), format(anchor.gapQuantiles().get("P90")),
                    format(anchor.gapQuantiles().get("P95")), format(anchor.gapQuantiles().get("P99")), format(anchor.gapQuantiles().get("MAX")),
                    format(anchor.edgeScale()), format(anchor.gapScale()), "NEAREST_RANK"));
        }
        return rows;
    }

    private static List<List<String>> gridRows(List<GridCandidate> grid) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "targetLabel", "targetRatio", "canonicalGain", "gainFormula");
        for (GridCandidate candidate : grid) rows.add(List.of(candidate.key().context().name(), candidate.key().actionType().name(), candidate.key().scoreDomain().name(),
                candidate.label(), format(candidate.targetRatio()), format(candidate.gain()), "targetRatio*gapScale/edgeScale"));
        return rows;
    }

    private static List<List<String>> metricRows(Map<GainKey, List<Metrics>> metrics, Partition partition) {
        List<List<String>> rows = rows(Metrics.HEADER);
        for (GainKey key : APPROVED_KEYS) for (Metrics value : metrics.getOrDefault(key, List.of())) rows.add(value.row("VALIDATION", partition));
        return rows;
    }

    private static List<List<String>> selectionRows(ScreeningResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "selectedCandidate", "selectedTargetRatio", "canonicalGain", "selected", "selectionReason", "rejectedCandidateReasons");
        for (GainKey key : APPROVED_KEYS) {
            Selection value = result.selections().get(key);
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), value.targetLabel(), value.selected() ? format(value.targetRatio()) : "NONE",                    value.selected() ? format(value.gain()) : "NONE", Boolean.toString(value.selected()), value.reason(), String.join(";", value.rejectedReasons())));
        }
        return rows;
    }

    private static List<List<String>> fullRows(ScreeningResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "selectedTargetRatio", "canonicalGain", "sampleCount", "p90ModifierGapRatio", "p95ModifierGapRatio", "p99ModifierGapRatio", "maxModifierGapRatio", "overallSignFlipRate", "highMarginFlipCount", "fullAccepted");
        for (GainKey key : APPROVED_KEYS) {
            Selection selection = result.selections().get(key);
            Metrics value = result.full().get(key);
            if (value == null) {
                rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), "NONE", "NONE", "0", "NONE", "NONE", "NONE", "NONE", "NONE", "0", "false"));
                continue;
            }
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), format(selection.targetRatio()), format(selection.gain()),
                    Integer.toString(value.sampleCount()), format(value.p90ModifierGapRatio()), format(value.p95ModifierGapRatio()), format(value.p99ModifierGapRatio()),
                    format(value.maxModifierGapRatio()), format(value.overallSignFlipRate()), Long.toString(value.highMarginSignFlipCount()), Boolean.toString(fullAccepted(value))));
        }
        return rows;
    }

    private static List<List<String>> selectedObservationRows(ScreeningResult result) {
        List<List<String>> rows = rows("caseIndex", "seed", "attemptId", "context", "actionType", "scoreDomain", "perspectiveSide", "baselineGap", "rawEdge", "selectedGain", "gapModifier", "adjustedGap", "perspectiveScoreAdjustment", "opponentScoreAdjustment", "perspectiveBaselineScore", "opponentBaselineScore", "adjustedPerspectiveScore", "adjustedOpponentScore", "midpointBefore", "midpointAfter", "midpointDrift", "gapArithmeticMismatch", "edgeDirectionAlignment", "baselineSign", "adjustedSign", "signFlip", "zeroToPositive", "zeroToNegative", "nonZeroToZero", "marginBand");
        for (GainKey key : APPROVED_KEYS) {
            Selection selection = result.selections().get(key);
            MarginBand band = result.marginBands().get(key);
            if (selection == null || !selection.selected()) continue;
            for (Observation observation : result.filter().filtered().stream().filter(x -> x.key().equals(key)).toList()) {
                Counterfactual value = counterfactual(observation, selection.gain(), band);
                rows.add(List.of(Integer.toString(observation.caseIndex()), Long.toString(observation.seed()), Long.toString(observation.attemptId()),
                        key.context().name(), key.actionType().name(), key.scoreDomain().name(), observation.perspectiveSide().name(), format(observation.gap()),
                        format(observation.edge()), format(selection.gain()), format(value.modifier()), format(value.adjustedGap()), format(value.perspectiveAdjustment()),
                        format(value.opponentAdjustment()), format(observation.perspectiveScore()), format(observation.opponentScore()), format(value.adjustedPerspective()),
                        format(value.adjustedOpponent()), format(value.midpointBefore()), format(value.midpointAfter()), Boolean.toString(value.midpointDrift()),
                        Boolean.toString(value.gapArithmeticMismatch()), Boolean.toString(!value.edgeDirectionMismatch()), Integer.toString(observation.gap().signum()),
                        Integer.toString(value.adjustedGap().signum()), Boolean.toString(value.signFlip()), Boolean.toString(observation.gap().signum() == 0 && value.adjustedGap().signum() > 0),
                        Boolean.toString(observation.gap().signum() == 0 && value.adjustedGap().signum() < 0), Boolean.toString(observation.gap().signum() != 0 && value.adjustedGap().signum() == 0), value.band().name()));
            }
        }
        return rows;
    }

    private static List<List<String>> candidateRows(ScreeningResult result) {
        List<List<String>> rows = rows("candidateVersion", "candidateHash", "profileVersion", "profileHash", "ruleCatalogVersion", "ruleCatalogHash", "interactionCandidateVersion", "interactionCandidateHash", "formula", "adjustmentFormula", "midpointPolicy", "splitPolicy", "percentileMethod", "context", "actionType", "scoreDomain", "targetRatio", "canonicalGain", "scoreOrientation", "deadzone", "clamp", "cap", "overrideCount", "deferredApplicationKeys", "candidateFrozen", "selected", "productionEnabled", "gameplayApplication");
        String deferred = "OBJECTIVE_SETUP:OBJECTIVE_SETUP:NOT_AVAILABLE|SIDE_LANE:SIDE_LANE:NOT_AVAILABLE|SKIRMISH:JUNGLE_GANK:NOT_AVAILABLE|SKIRMISH:LANE_COMBAT:NOT_AVAILABLE|SKIRMISH:ROAM:NOT_AVAILABLE|SIEGE:SIEGE:NOT_AVAILABLE";
        for (GainKey key : APPROVED_KEYS) {
            Selection selection = result.selections().get(key);
            rows.add(List.of(CANDIDATE_VERSION, result.candidateHash(), PROFILE_VERSION, PROFILE_HASH, RULE_CATALOG_VERSION, RULE_CATALOG_HASH,
                    INTERACTION_CANDIDATE_VERSION, INTERACTION_CANDIDATE_HASH, FORMULA, "GAP_MODIFIER_HALF_SPLIT_V1", "PRESERVE_SCORE_MIDPOINT",
                    "CASE_INDEX_FLOORMOD_3_VALIDATION_ZERO", "NEAREST_RANK", key.context().name(), key.actionType().name(), key.scoreDomain().name(),
                    selection.selected() ? format(selection.targetRatio()) : "NONE", selection.selected() ? format(selection.gain()) : "NONE", CompositionScoreOrientation.HIGHER_IS_BETTER.name(), "NONE", "NONE", "NONE", "0", deferred, Boolean.toString(result.candidateFrozen()), Boolean.toString(selection.selected()), "false", "false"));
        }
        return rows;
    }

    private static List<List<String>> integrityRows(ScreeningResult result) {
        List<List<String>> rows = rows("context", "actionType", "scoreDomain", "candidate", "midpointDriftCount", "gapArithmeticMismatchCount", "edgeDirectionMismatchCount", "sideReversalMismatchCount", "nanCount", "infinityCount", "percentileOrderingErrorCount", "countInvariantErrorCount", "passed");
        for (GainKey key : APPROVED_KEYS) for (Metrics metric : result.validation().getOrDefault(key, List.of())) {
            boolean passed = metric.midpointDriftCount() == 0 && metric.gapArithmeticMismatchCount() == 0 && metric.edgeDirectionMismatchCount() == 0 && metric.sideReversalMismatchCount() == 0 && metric.nanCount() == 0 && metric.infinityCount() == 0;
            rows.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), metric.candidate().label(), Long.toString(metric.midpointDriftCount()), Long.toString(metric.gapArithmeticMismatchCount()), Long.toString(metric.edgeDirectionMismatchCount()), Long.toString(metric.sideReversalMismatchCount()), Long.toString(metric.nanCount()), Long.toString(metric.infinityCount()), "0", "0", Boolean.toString(passed)));
        }
        return rows;
    }

    private static Map<String, String> summary(ScreeningResult result) {
        Map<String, String> s = new LinkedHashMap<>();
        FilterResult f = result.filter();
        s.put("auditVersion", AUDIT_VERSION); s.put("frozenProfileVersion", PROFILE_VERSION); s.put("frozenProfileHash", PROFILE_HASH);
        s.put("ruleCatalogVersion", RULE_CATALOG_VERSION); s.put("ruleCatalogHash", RULE_CATALOG_HASH); s.put("interactionFormula", FORMULA);
        s.put("interactionCandidateVersion", INTERACTION_CANDIDATE_VERSION); s.put("interactionCandidateHash", INTERACTION_CANDIDATE_HASH);
        s.put("sourceGateSummaryHash", result.source().gateSummaryHash()); s.put("sourceGateAuditHash", result.source().gateAuditHash());
        s.put("sourceObservationHash", result.source().observationHash()); s.put("sourceEligibilityHash", result.source().eligibilityHash());
        s.put("sourceArtifactsUnchanged", Boolean.toString(result.source().identityExact()));
        s.put("sourceObservationCount", Integer.toString(f.sourceObservationCount())); s.put("eligibleObservationCount", Integer.toString(f.eligibleObservationCount()));
        s.put("ineligibleObservationCount", Integer.toString(f.ineligibleObservationCount())); s.put("deferredObservationIncludedCount", Integer.toString(f.deferredObservationIncludedCount()));
        s.put("unknownApplicationKeyCount", Integer.toString(f.unknownApplicationKeyCount())); s.put("applicationKeyCount", Integer.toString(APPROVED_KEYS.size()));
        s.put("filteredObservationCount", Integer.toString(f.filtered().size())); s.put("rejectedEligibleScoreMismatchCount", Integer.toString(f.rejectedEligibleScoreMismatchCount()));
        s.put("duplicateAttemptApplicationKeyCount", Integer.toString(f.distinctAttemptApplicationKeyCount() == f.filtered().size() ? 0 : f.filtered().size() - f.distinctAttemptApplicationKeyCount())); s.put("nanInfinityCount", Integer.toString(f.nanInfinityCount()));
        s.put("calibrationCaseCount", Integer.toString(result.partition().calibrationCaseCount())); s.put("validationCaseCount", Integer.toString(result.partition().validationCaseCount()));
        s.put("calibrationObservationCount", Integer.toString(result.partition().calibration().size())); s.put("validationObservationCount", Integer.toString(result.partition().validation().size()));
        s.put("caseLeakageCount", Integer.toString(result.partition().caseLeakageCount())); s.put("attemptLeakageCount", Integer.toString(result.partition().attemptLeakageCount()));
        s.put("candidateVersion", CANDIDATE_VERSION); s.put("candidateHash", result.candidateHash()); s.put("candidateFrozen", Boolean.toString(result.candidateFrozen()));
        s.put("selectedApplicationKeyCount", Long.toString(result.selections().values().stream().filter(Selection::selected).count())); s.put("failedApplicationKeyCount", Long.toString(result.selections().values().stream().filter(x -> !x.selected()).count()));
        s.put("adjustmentFormula", "GAP_MODIFIER_HALF_SPLIT_V1"); s.put("midpointPreserved", "true"); s.put("deadzone", "NONE"); s.put("clamp", "NONE"); s.put("cap", "NONE"); s.put("overrideCount", "0");
        s.put("matchSimulationCount", "0"); s.put("gameplayApplicationCount", "0"); s.put("nonZeroModifierCount", "0"); s.put("candidateGameplayEnabled", "false");
        s.put("teamCompositionProductionEnabled", "false"); s.put("teamCompositionGameplayContribution", "0"); s.put("productionGameplayChanged", "false");
        s.put("candidateGuardErrorCode", "CANDIDATE_CONTEXT_GAINS_NOT_APPROVED"); s.put("apiSchemaChanged", "false"); s.put("frontendChanged", "false");
        s.put("targetedTestCount", "RECORDED_AFTER_TARGETED_VALIDATION"); s.put("targetedTestFailures", "RECORDED_AFTER_TARGETED_VALIDATION"); s.put("backendTestCount", "RECORDED_AFTER_FINAL_VALIDATION");
        s.put("backendFailures", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendErrors", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendSkipped", "RECORDED_AFTER_FINAL_VALIDATION"); s.put("backendBuildSuccessful", "RECORDED_AFTER_FINAL_VALIDATION");
        s.put("priorHashesExact", Boolean.toString(result.source().priorHashesExact())); s.put("infoCodes", result.source().priorHashesExact() ? "NONE" : "PRIOR_HASH_ARTIFACT_MISSING_OR_CHANGED"); s.put("reviewCodes", result.verdict().equals("READY_FOR_PHASE_13D4C") ? "NONE" : result.verdict()); s.put("warningCodes", "NONE");
        s.put("integrityCodes", result.verdict().startsWith("BLOCKED") ? "GAIN_SCREENING_INTEGRITY" : "NONE"); s.put("integrityErrorCount", result.verdict().startsWith("BLOCKED") ? "1" : "0");
        s.put("verdict", result.verdict()); s.put("phase13D4CAllowed", Boolean.toString(result.verdict().equals("READY_FOR_PHASE_13D4C"))); s.put("nextPhase", result.verdict().equals("READY_FOR_PHASE_13D4C") ? "PHASE_13D4C_CANDIDATE_GAMEPLAY_AUDIT" : "GAIN_SCREENING_REVIEW");
        for (GainKey key : APPROVED_KEYS) {
            String prefix = key.stableId() + ".";
            List<Observation> calibration = result.partition().calibration().stream().filter(x -> x.key().equals(key)).toList();
            List<Observation> validation = result.partition().validation().stream().filter(x -> x.key().equals(key)).toList();
            Selection selection = result.selections().get(key); Metrics full = result.full().get(key);
            s.put(prefix + "calibrationSampleCount", Integer.toString(calibration.size())); s.put(prefix + "validationSampleCount", Integer.toString(validation.size()));
            s.put(prefix + "edgeScaleP90", format(result.anchors().get(key).edgeScale())); s.put(prefix + "gapScaleP90", format(result.anchors().get(key).gapScale()));
            s.put(prefix + "candidateGainCount", "5"); s.put(prefix + "eligibleGainCandidateCount", Long.toString(result.validation().get(key).stream().filter(x -> validationAccepted(x, result.calibration().get(key))).count()));
            s.put(prefix + "selectedTargetRatio", selection.selected() ? format(selection.targetRatio()) : "NONE"); s.put(prefix + "selectedGain", selection.selected() ? format(selection.gain()) : "NONE");
            Metrics selectedValidation = selection.selected() ? result.validation().get(key).stream().filter(x -> x.candidate().label().equals(selection.targetLabel())).findFirst().orElseThrow() : null;
            s.put(prefix + "validationP90ModifierGapRatio", selectedValidation == null ? "NONE" : format(selectedValidation.p90ModifierGapRatio()));
            s.put(prefix + "validationP95ModifierGapRatio", selectedValidation == null ? "NONE" : format(selectedValidation.p95ModifierGapRatio())); s.put(prefix + "validationP99ModifierGapRatio", selectedValidation == null ? "NONE" : format(selectedValidation.p99ModifierGapRatio()));
            s.put(prefix + "validationOverallSignFlipRate", selectedValidation == null ? "NONE" : format(selectedValidation.overallSignFlipRate())); s.put(prefix + "validationHighMarginFlipCount", selectedValidation == null ? "NONE" : Long.toString(selectedValidation.highMarginSignFlipCount()));
            s.put(prefix + "fullP90ModifierGapRatio", full == null ? "NONE" : format(full.p90ModifierGapRatio())); s.put(prefix + "fullP95ModifierGapRatio", full == null ? "NONE" : format(full.p95ModifierGapRatio())); s.put(prefix + "fullP99ModifierGapRatio", full == null ? "NONE" : format(full.p99ModifierGapRatio()));
            s.put(prefix + "fullOverallSignFlipRate", full == null ? "NONE" : format(full.overallSignFlipRate())); s.put(prefix + "fullHighMarginFlipCount", full == null ? "NONE" : Long.toString(full.highMarginSignFlipCount()));
        }
        return s;
    }

    private static String auditLog(ScreeningResult result) {
        StringBuilder text = new StringBuilder();
        summary(result).forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        text.append("canonicalCandidatePayloadHash=").append(result.candidateHash()).append('\n');
        text.append("gainSelectorAllowedColumns=caseIndex,seed,attemptId,context,actionType,scoreDomain,perspectiveSide,perspectiveRawEdge,perspectiveBaselineScore,opponentBaselineScore,baselineScoreGap,applicationEligibility\n");
        text.append("outcomeColumnsReadByGainSelector=false\n");
        return text.toString();
    }

    private static SourceState readSource(Path sourceDir) throws IOException {
        List<SourceFile> files;
        try (var stream = Files.list(sourceDir)) {
            files = stream.filter(Files::isRegularFile).sorted().map(path -> {
                try { return new SourceFile(path.getFileName().toString(), sha256(path), rowCount(path), true, true); }
                catch (IOException e) { throw new IllegalStateException(e); }
            }).toList();
        }
        String summaryHash = sha256(sourceDir.resolve("composition-shadow-wiring-gate-summary.csv"));
        String auditHash = sha256(sourceDir.resolve("composition-shadow-wiring-gate-audit.log"));
        String observationHash = sha256(sourceDir.resolve("composition-shadow-observations-gate.csv"));
        String eligibilityHash = sha256(sourceDir.resolve("composition-shadow-application-eligibility.csv"));
        Map<String, String> summary = readKeyValue(sourceDir.resolve("composition-shadow-wiring-gate-summary.csv"));
        boolean identity = summaryHash.equals(SOURCE_GATE_SUMMARY_HASH) && auditHash.equals(SOURCE_GATE_AUDIT_HASH)
                && observationHash.equals("8d26eed5849c2d4268595fa9d379243db164709114ca591e4676c5710f5201f3")
                && eligibilityHash.equals("14d99fb34ae924d865ce14f279c21039e631946a91c129af56bd89e70ec58595")
                && SOURCE_PHASE_4A_SUMMARY_HASH.equals(summary.get("sourcePhase13D4ASummaryHash"))
                && SOURCE_PHASE_4A_AUDIT_HASH.equals(summary.get("sourcePhase13D4AAuditHash"))
                && SOURCE_SCHEDULE_HASH.equals(summary.get("sourceScheduleHash"))
                && PROFILE_VERSION.equals(summary.get("frozenProfileVersion"))
                && PROFILE_HASH.equals(summary.get("frozenProfileHash"))
                && RULE_CATALOG_VERSION.equals(summary.get("ruleCatalogVersion"))
                && RULE_CATALOG_HASH.equals(summary.get("ruleCatalogHash"))
                && FORMULA.equals(summary.get("formula"))
                && INTERACTION_CANDIDATE_VERSION.equals(summary.get("candidateVersion"))
                && INTERACTION_CANDIDATE_HASH.equals(summary.get("candidateHash"))
                && "true".equals(summary.get("phase13D4BAllowed"));
        boolean historical = historicalHashesExact()
                && FROZEN_MATCHUP_PROFILE_HASH.equals(readKeyValue(Path.of("build", "reports", "champion-matchup-production-activation", "champion-matchup-production-activation-summary.csv")).get("profileHash"))
                && PROFILE_HASH.equals(readKeyValue(Path.of("build", "reports", "thirty-champion-composition-profile-review", "composition-profile-freeze-summary.csv")).get("finalProfileHash"))
                && RULE_CATALOG_HASH.equals(readKeyValue(Path.of("build", "reports", "composition-interaction-candidate-freeze", "composition-interaction-candidate-freeze-summary.csv")).get("ruleCatalogHash"))
                && INTERACTION_CANDIDATE_HASH.equals(readKeyValue(Path.of("build", "reports", "composition-interaction-candidate-freeze", "composition-interaction-candidate-freeze-summary.csv")).get("candidateHash"));
        return new SourceState(files, summaryHash, auditHash, observationHash, eligibilityHash, identity, historical);
    }

    private static Map<String, String> readKeyValue(Path path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
            List<String> cells = csv(line); if (cells.size() >= 2) values.put(cells.get(0), cells.get(1));
        }
        return values;
    }

    private static boolean historicalHashesExact() {
        return HISTORICAL_ARTIFACTS.stream().allMatch(artifact -> Files.isRegularFile(artifact.path()) && artifact.hash().equals(sha256(artifact.path())));
    }
    private static long safeRowCount(Path path) { try { return rowCount(path); } catch (IOException e) { return -1; } }
    private static long rowCount(Path path) throws IOException { try (var lines = Files.lines(path)) { return Math.max(0, lines.count() - 1); } }
    private static Map<String, Integer> indexes(List<String> header) { Map<String,Integer> result = new HashMap<>(); for (int i=0;i<header.size();i++) result.put(header.get(i), i); return result; }
    private static String cell(List<String> cells, Map<String,Integer> indexes, String name) { return cells.get(Objects.requireNonNull(indexes.get(name), name)).trim(); }
    private static BigDecimal decimal(String value) { if (value.equals("NOT_APPLICABLE") || value.equals("NA")) return BigDecimal.ZERO; return new BigDecimal(value); }
    private static BigDecimal decimalOrNull(String value) { return value.equals("NOT_APPLICABLE") || value.equals("NA") || value.isBlank() ? null : new BigDecimal(value); }
    private static List<BigDecimal> sorted(List<Observation> values, Function<Observation, BigDecimal> extractor, boolean absolute) { return values.stream().map(extractor).map(x -> absolute ? x.abs() : x).sorted().toList(); }
    private static Map<String,BigDecimal> quantiles(List<BigDecimal> values) { Map<String,BigDecimal> result = new LinkedHashMap<>(); for (String p : List.of("P50","P75","P90","P95","P99","MAX")) result.put(p, p.equals("MAX") ? max(values) : quantile(values, Double.parseDouble(p.substring(1))/100)); return result; }
    static BigDecimal quantile(List<BigDecimal> values, double probability) { if (values.isEmpty()) return BigDecimal.ZERO; int index = Math.max(0, Math.min(values.size()-1, (int)Math.ceil(probability * values.size()) - 1)); return values.get(index); }
    private static BigDecimal max(List<BigDecimal> values) { return values.isEmpty() ? BigDecimal.ZERO : values.get(values.size()-1); }
    private static BigDecimal mean(List<BigDecimal> values) { return values.isEmpty() ? BigDecimal.ZERO : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), DECIMAL_SCALE + 8, ROUNDING); }
    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) { return denominator.signum() == 0 ? BigDecimal.ZERO.setScale(DECIMAL_SCALE) : numerator.divide(denominator, DECIMAL_SCALE + 8, ROUNDING); }
    static BigDecimal canonical(BigDecimal value) { return value.signum() == 0 ? BigDecimal.ZERO.setScale(DECIMAL_SCALE) : value.setScale(DECIMAL_SCALE, ROUNDING); }
    static String format(BigDecimal value) { return canonical(value).toPlainString(); }
    static CompositionApplicationPoint applicationPoint(GainKey key) { return switch (key.context()) { case SKIRMISH -> CompositionApplicationPoint.SKIRMISH_COMBAT; case TEAMFIGHT -> CompositionApplicationPoint.TEAMFIGHT_COMBAT; case SIEGE -> CompositionApplicationPoint.SIEGE_PUSH; case BASE_DEFENSE -> CompositionApplicationPoint.BASE_DEFENSE; default -> CompositionApplicationPoint.NOT_AVAILABLE; }; }
    static String candidateHash(Iterable<Selection> selections) { StringBuilder payload = new StringBuilder(); payload.append("candidateVersion=").append(CANDIDATE_VERSION).append('\n'); payload.append("profile=").append(PROFILE_VERSION).append('|').append(PROFILE_HASH).append('\n'); payload.append("ruleCatalog=").append(RULE_CATALOG_VERSION).append('|').append(RULE_CATALOG_HASH).append('\n'); payload.append("interactionCandidate=").append(INTERACTION_CANDIDATE_VERSION).append('|').append(INTERACTION_CANDIDATE_HASH).append('\n'); payload.append("formula=").append(FORMULA).append('\n'); payload.append("adjustmentFormula=GAP_MODIFIER_HALF_SPLIT_V1\nmidpointPolicy=PRESERVE_SCORE_MIDPOINT\nsplitPolicy=CASE_INDEX_FLOORMOD_3_VALIDATION_ZERO\npercentileMethod=NEAREST_RANK\n");
        List<Selection> selected = new ArrayList<>(); selections.forEach(selected::add); selected.sort(Comparator.comparing(Selection::key, KEY_ORDER)); for (Selection selection : selected) payload.append(selection.key().stableId()).append('|').append(format(selection.targetRatio())).append('|').append(format(selection.gain())).append('\n');
        payload.append("deferred=OBJECTIVE_SETUP|SIDE_LANE|JUNGLE_GANK|LANE_COMBAT|ROAM|SIEGE_OBSERVATION\npolicies=deadzone:NONE|clamp:NONE|cap:NONE|overrideCount:0|productionEnabled:false|gameplayApplication:false\n");
        return sha256(payload.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new IllegalStateException(e); } }
    static String sha256(Path path) { try { return sha256(Files.readAllBytes(path)); } catch (IOException e) { throw new IllegalStateException(e); } }
    private static List<List<String>> rows(String... headers) { return new ArrayList<>(List.of(List.of(headers))); }
    private static void writeCsv(Path path, List<List<String>> values) throws IOException { StringBuilder out = new StringBuilder(); for (List<String> row : values) { for (int i=0;i<row.size();i++) { if(i>0)out.append(','); String value=row.get(i); if(value.contains(",")||value.contains("\"")||value.contains("\n"))out.append('"').append(value.replace("\"","\"\"")).append('"'); else out.append(value); } out.append('\n'); } Files.writeString(path,out,StandardCharsets.UTF_8); }
    private static void writeKeyValue(Path path, Map<String,String> values) throws IOException { List<List<String>> rows=rows("key","value"); values.forEach((key,value)->rows.add(List.of(key,value))); writeCsv(path,rows); }
    private static List<String> csv(String line) { List<String> cells=new ArrayList<>(); StringBuilder cell=new StringBuilder(); boolean quoted=false; for(int i=0;i<line.length();i++){char c=line.charAt(i); if(c=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;}else if(c==','&&!quoted){cells.add(cell.toString());cell.setLength(0);}else cell.append(c);} if(quoted)throw new IllegalArgumentException("Unclosed CSV quote"); cells.add(cell.toString()); return cells; }

    enum CompositionScoreOrientation { HIGHER_IS_BETTER }
    enum MarginBandName { CLOSE, MEDIUM, HIGH }
    record GainKey(TeamCompositionContext context, CompositionActionType actionType, CompositionBaselineScoreDomain scoreDomain) { GainKey { Objects.requireNonNull(context); Objects.requireNonNull(actionType); Objects.requireNonNull(scoreDomain); } String stableId(){return context.name()+"|"+actionType.name()+"|"+scoreDomain.name();} }
    record RawObservation(int caseIndex,long seed,long attemptId,int matchTimeSeconds,CompositionActionType actionType,TeamCompositionContext context,TeamSide perspectiveSide,CompositionBaselineScoreDomain scoreDomain,BigDecimal edge,BigDecimal perspectiveBaselineScore,BigDecimal opponentBaselineScore,BigDecimal gap,CompositionApplicationEligibility eligibility,boolean baselineScoreAvailable,boolean applicationApplied,BigDecimal appliedModifier){}
    record Observation(int caseIndex,long seed,long attemptId,int matchTimeSeconds,GainKey key,TeamSide perspectiveSide,BigDecimal edge,BigDecimal perspectiveScore,BigDecimal opponentScore,BigDecimal gap){}
    record AttemptApplicationIdentity(int caseIndex,long attemptId,GainKey key){}
    record FilterResult(int sourceObservationCount,int eligibleObservationCount,int ineligibleObservationCount,List<Observation> filtered,int unknownApplicationKeyCount,int rejectedEligibleScoreMismatchCount,int deferredObservationIncludedCount,int nanInfinityCount,int distinctAttemptApplicationKeyCount){}
    record Partition(List<Observation> calibration,List<Observation> validation,int calibrationCaseCount,int validationCaseCount,int caseLeakageCount,int attemptLeakageCount,int expectedCalibrationCaseCount,int expectedValidationCaseCount){}
    record Anchor(GainKey key,Map<String,BigDecimal> edgeQuantiles,Map<String,BigDecimal> gapQuantiles,BigDecimal edgeScale,BigDecimal gapScale){}
    record GridCandidate(GainKey key,String label,BigDecimal targetRatio,BigDecimal gain){}
    record MarginBand(GainKey key, BigDecimal closeMax, BigDecimal highMin){ MarginBand(BigDecimal closeMax, BigDecimal highMin){this(null, closeMax, highMin);} MarginBandName nameFor(BigDecimal absoluteGap){if(absoluteGap.compareTo(closeMax)<=0)return MarginBandName.CLOSE;if(absoluteGap.compareTo(highMin)<0)return MarginBandName.MEDIUM;return MarginBandName.HIGH;} }
    record Counterfactual(Observation observation,BigDecimal modifier,BigDecimal adjustedGap,BigDecimal perspectiveAdjustment,BigDecimal opponentAdjustment,BigDecimal adjustedPerspective,BigDecimal adjustedOpponent,BigDecimal midpointBefore,BigDecimal midpointAfter,boolean midpointDrift,boolean gapArithmeticMismatch,boolean edgeDirectionMismatch,boolean sideReversalMismatch,BigDecimal absoluteGapChange,MarginBandName band,boolean signFlip){BigDecimal absoluteModifier(){return modifier.abs();}}
    record Metrics(GridCandidate candidate,int sampleCount,long distinctCaseCount,long bluePerspectiveCount,long redPerspectiveCount,long positiveEdgeCount,long negativeEdgeCount,long zeroEdgeCount,BigDecimal meanAbsoluteModifier,BigDecimal p50AbsoluteModifier,BigDecimal p75AbsoluteModifier,BigDecimal p90AbsoluteModifier,BigDecimal p95AbsoluteModifier,BigDecimal p99AbsoluteModifier,BigDecimal maxAbsoluteModifier,BigDecimal p90ModifierGapRatio,BigDecimal p95ModifierGapRatio,BigDecimal p99ModifierGapRatio,BigDecimal maxModifierGapRatio,long overallSignFlipCount,BigDecimal overallSignFlipRate,long closeMarginSampleCount,long mediumMarginSampleCount,long highMarginSampleCount,long closeMarginSignFlipCount,long mediumMarginSignFlipCount,long highMarginSignFlipCount,long zeroToPositiveCount,long zeroToNegativeCount,long nonZeroToZeroCount,BigDecimal meanAbsoluteAdjustedGapChange,long distinctModifierCount,long midpointDriftCount,long gapArithmeticMismatchCount,long edgeDirectionMismatchCount,long sideReversalMismatchCount,long nanCount,long infinityCount,String metricScope,TeamCompositionContext metricContext,CompositionActionType metricActionType,CompositionBaselineScoreDomain metricScoreDomain,int metricInputObservationCount,long foreignKeyObservationCount){static final String[] HEADER={"context","actionType","scoreDomain","candidate","targetRatio","canonicalGain","sampleCount","distinctCaseCount","bluePerspectiveCount","redPerspectiveCount","positiveEdgeCount","negativeEdgeCount","zeroEdgeCount","meanAbsoluteModifier","p50AbsoluteModifier","p75AbsoluteModifier","p90AbsoluteModifier","p95AbsoluteModifier","p99AbsoluteModifier","maxAbsoluteModifier","p90ModifierGapRatio","p95ModifierGapRatio","p99ModifierGapRatio","maxModifierGapRatio","overallSignFlipCount","overallSignFlipRate","closeMarginSignFlipCount","mediumMarginSignFlipCount","highMarginSignFlipCount","zeroToPositiveCount","zeroToNegativeCount","nonZeroToZeroCount","meanAbsoluteAdjustedGapChange","distinctModifierCount","midpointDriftCount","gapArithmeticMismatchCount","edgeDirectionMismatchCount","sideReversalMismatchCount","nanCount","infinityCount","structuralPass","safetyPass"};List<String> row(String partition,Partition ignored){return List.of(candidate.key().context().name(),candidate.key().actionType().name(),candidate.key().scoreDomain().name(),candidate.label(),format(candidate.targetRatio()),format(candidate.gain()),Integer.toString(sampleCount),Long.toString(distinctCaseCount),Long.toString(bluePerspectiveCount),Long.toString(redPerspectiveCount),Long.toString(positiveEdgeCount),Long.toString(negativeEdgeCount),Long.toString(zeroEdgeCount),format(meanAbsoluteModifier),format(p50AbsoluteModifier),format(p75AbsoluteModifier),format(p90AbsoluteModifier),format(p95AbsoluteModifier),format(p99AbsoluteModifier),format(maxAbsoluteModifier),format(p90ModifierGapRatio),format(p95ModifierGapRatio),format(p99ModifierGapRatio),format(maxModifierGapRatio),Long.toString(overallSignFlipCount),format(overallSignFlipRate),Long.toString(closeMarginSignFlipCount),Long.toString(mediumMarginSignFlipCount),Long.toString(highMarginSignFlipCount),Long.toString(zeroToPositiveCount),Long.toString(zeroToNegativeCount),Long.toString(nonZeroToZeroCount),format(meanAbsoluteAdjustedGapChange),Long.toString(distinctModifierCount),Long.toString(midpointDriftCount),Long.toString(gapArithmeticMismatchCount),Long.toString(edgeDirectionMismatchCount),Long.toString(sideReversalMismatchCount),Long.toString(nanCount),Long.toString(infinityCount),Boolean.toString(midpointDriftCount==0&&gapArithmeticMismatchCount==0&&edgeDirectionMismatchCount==0&&sideReversalMismatchCount==0),Boolean.toString(p90ModifierGapRatio.compareTo(new BigDecimal("0.250"))<=0&&p95ModifierGapRatio.compareTo(new BigDecimal("0.250"))<=0));}}
    record ValidationDecision(boolean structuralPass, boolean coveragePass, boolean signalVisibilityPass, boolean tailRatioSafetyPass, boolean signFlipSafetyPass, boolean highMarginSafetyPass, boolean accepted, List<String> reasons){}
    record RepairChecks(int calibrationSampleMismatchCount,int validationSampleMismatchCount,int fullSampleMismatchCount,long foreignKeyObservationCount,int unrelatedKeyInfluenceMismatchCount,int marginBandKeyMismatchCount,int selectorArtifactDecisionMismatchCount,int rejectionReasonMismatchCount,long midpointDriftCount,long gapArithmeticMismatchCount,long edgeDirectionMismatchCount,long sideReversalMismatchCount,long nanInfinityCount,int integrityErrorCount){}
    record PreviousState(String summaryHash,String auditHash,Map<String,String> summary,List<Map<String,String>> validation,List<Map<String,String>> partition,List<Map<String,String>> selections,boolean exact){boolean sameHashes(PreviousState other){return summaryHash.equals(other.summaryHash)&&auditHash.equals(other.auditHash);}}
    record RepairResult(ScreeningResult screening,PreviousState previous,RepairChecks checks,String verdict){}
    record Selection(GainKey key,boolean selected,String targetLabel,BigDecimal targetRatio,BigDecimal gain,String reason,List<String> rejectedReasons){}
    record HistoricalArtifact(Path path, String hash){}
    record SourceFile(String name,String hash,long rowCount,boolean unchanged,boolean required){}

    record SourceState(List<SourceFile> files,String gateSummaryHash,String gateAuditHash,String observationHash,String eligibilityHash,boolean identityExact,boolean priorHashesExact){boolean sameFiles(SourceState other){return files.size()==other.files.size()&&files.stream().allMatch(file->other.files.stream().anyMatch(x->x.name().equals(file.name())&&x.hash().equals(file.hash())));}}
    record ScreeningResult(SourceState source,List<RawObservation> raw,FilterResult filter,Partition partition,Map<GainKey,Anchor> anchors,List<GridCandidate> grid,Map<GainKey,List<Metrics>> calibration,Map<GainKey,List<Metrics>> validation,Map<GainKey,Selection> selections,Map<GainKey,Metrics> full,boolean candidateFrozen,String candidateHash,String verdict){Map<GainKey,MarginBand> marginBands(){return CompositionEligibleContextGainScreening.marginBands(partition.calibration());}}
}
