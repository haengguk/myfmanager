package com.lolfm.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Artifact-only Final 13G-B synthesis. It reads authenticated B2/B3 evidence and never executes
 * gameplay, calibration, holdout, or tuning.
 */
public final class Phase13GBFinalSynthesis {
    public static final String PHASE = "FINAL_13G_B_SYNTHESIS_AND_PRODUCTION_V1_DECISION";
    public static final String EVIDENCE_BINDING_FILE =
            "final-13g-b-evidence-binding.json";
    public static final String SEGMENTED_SENSITIVITY_FILE =
            "final-13g-b-segmented-sensitivity.csv";
    public static final String FLIPPED_PAIRS_FILE =
            "final-13g-b-flipped-pairs.csv";
    public static final String SYNTHESIS_FILE =
            "final-13g-b-sensitivity-synthesis.json";
    public static final String DECISION_FILE =
            "final-13g-b-production-decision.json";
    public static final String SHA_FILE = "SHA256SUMS.txt";

    private static final String B2_REVIEW_FILE = "phase13g-b2-review.json";
    private static final String B2_PAIRED_FILE = "phase13g-b2-paired-marginals.csv";
    private static final String B2_JUNGLE_FILE = "phase13g-b2-jungle-checkpoints.csv";
    private static final String B3_REVIEW_FILE = "phase13g-b3-final-review.json";
    private static final String B3_GATE_FILE = "phase13g-b3-frozen-gate-evaluation.json";
    private static final String B3_PAIRED_FILE = "phase13g-b3-paired-marginals.csv";
    private static final String B3_JUNGLE_FILE = "phase13g-b3-jungle-observations.csv";
    private static final String B3_DRAFT_FILE = "phase13g-b3-fixed-drafts.csv";
    private static final String ECONOMY = "ECONOMY_MINUS_FULL";
    private static final String TEMPO = "TEMPO_MINUS_ECONOMY";
    private static final String ECONOMY_G1_GATE =
            "ECONOMY_ECONOMY_MINUS_FULL_PRIMARY_LEAGUE_G1_WINNER_FLIP_RATE";
    private static final List<String> OUTPUT_FILES = List.of(
            EVIDENCE_BINDING_FILE,
            SEGMENTED_SENSITIVITY_FILE,
            FLIPPED_PAIRS_FILE,
            SYNTHESIS_FILE,
            DECISION_FILE);

    private Phase13GBFinalSynthesis() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: Phase13GBFinalSynthesis <b2-report> <b3-report> <output>");
        }
        ArtifactSet result = write(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        System.out.println("phase=" + PHASE);
        System.out.println("evidenceStatus=" + result.evidenceStatus());
        System.out.println("productionDecision=" + result.productionDecision());
        System.out.println("economyDisposition=" + result.economyDisposition());
        System.out.println("tempoDisposition=" + result.tempoDisposition());
        System.out.println("inputPairedRows=" + result.inputPairedRows());
        System.out.println("newSimulationExecutions=0");
        System.out.println("manifestSha256=" + result.manifestSha256());
    }

    public static ArtifactSet write(Path b2Directory, Path b3Directory, Path output)
            throws IOException {
        Objects.requireNonNull(b2Directory, "b2Directory");
        Objects.requireNonNull(b3Directory, "b3Directory");
        Objects.requireNonNull(output, "output");
        InputBinding inputs = verifyInputs(b2Directory, b3Directory);
        List<PairedEvidence> evidence = new ArrayList<>(6_400);
        evidence.addAll(loadPairedEvidence(b2Directory, SourcePopulation.CALIBRATION_B2));
        evidence.addAll(loadPairedEvidence(b3Directory, SourcePopulation.HOLDOUT_B3));
        requirePopulation(evidence, SourcePopulation.CALIBRATION_B2, ECONOMY, 2_400);
        requirePopulation(evidence, SourcePopulation.CALIBRATION_B2, TEMPO, 2_400);
        requirePopulation(evidence, SourcePopulation.HOLDOUT_B3, ECONOMY, 800);
        requirePopulation(evidence, SourcePopulation.HOLDOUT_B3, TEMPO, 800);

        List<SegmentRow> segments = segment(evidence);
        GateBoundary economyBoundary = readEconomyBoundary(
                b3Directory.resolve(B3_GATE_FILE));
        DecisionPolicy policy = decisionPolicy(
                inputs.economyFrozenVerdict(),
                inputs.tempoFrozenVerdict(),
                inputs.exact(),
                economyBoundary.actual(),
                economyBoundary.upperInclusive());

        Files.createDirectories(output);
        Map<String, Object> binding = bindingReport(inputs, evidence);
        writeJson(output.resolve(EVIDENCE_BINDING_FILE), binding);
        writeUtf8(output.resolve(SEGMENTED_SENSITIVITY_FILE), segmentsCsv(segments));
        writeUtf8(output.resolve(FLIPPED_PAIRS_FILE), flippedPairsCsv(evidence));
        Map<String, Object> synthesis = synthesisReport(
                inputs, evidence, segments, economyBoundary);
        writeJson(output.resolve(SYNTHESIS_FILE), synthesis);

        Map<String, String> decisionInputs = new TreeMap<>();
        for (String file : List.of(
                EVIDENCE_BINDING_FILE,
                SEGMENTED_SENSITIVITY_FILE,
                FLIPPED_PAIRS_FILE,
                SYNTHESIS_FILE)) {
            decisionInputs.put(file, sha256(output.resolve(file)));
        }
        writeJson(output.resolve(DECISION_FILE), decisionReport(
                inputs, policy, economyBoundary, evidence, segments, decisionInputs));
        writeManifest(output);
        return new ArtifactSet(
                output,
                "FINAL_EVIDENCE_VALID",
                policy.productionDecision(),
                policy.economyDisposition(),
                policy.tempoDisposition(),
                evidence.size(),
                sha256(output.resolve(SHA_FILE)));
    }

    static InputBinding verifyInputs(Path b2Directory, Path b3Directory) throws IOException {
        ManifestEvidence b2 = verifyManifest(b2Directory, 16);
        ManifestEvidence b3 = verifyManifest(b3Directory, 18);
        requireManifestEntry(b2, B2_REVIEW_FILE);
        requireManifestEntry(b2, B2_PAIRED_FILE);
        requireManifestEntry(b2, B2_JUNGLE_FILE);
        requireManifestEntry(b3, B3_REVIEW_FILE);
        requireManifestEntry(b3, B3_GATE_FILE);
        requireManifestEntry(b3, B3_PAIRED_FILE);
        requireManifestEntry(b3, B3_JUNGLE_FILE);
        requireManifestEntry(b3, B3_DRAFT_FILE);

        String b2Review = Files.readString(b2Directory.resolve(B2_REVIEW_FILE),
                StandardCharsets.UTF_8);
        String b3Review = Files.readString(b3Directory.resolve(B3_REVIEW_FILE),
                StandardCharsets.UTF_8);
        String b2Status = stringField(b2Review, "status");
        String b3Status = stringField(b3Review, "evidenceStatus");
        String economyVerdict = stringField(b3Review, "economyCandidateVerdict");
        String tempoVerdict = stringField(b3Review, "tempoCandidateVerdict");
        String productionDecision = stringField(b3Review, "productionDecision");
        long calibrationMatches = longField(b2Review, "calibrationMatchExecutionCount");
        // B3 embeds the B2 run guard (holdout=0) before its own top-level execution count.
        long holdoutMatches = lastLongField(b3Review, "holdoutMatchExecutionCount");
        String frozenContractHash = stringField(b3Review, "frozenContractHash");
        String candidateHash = stringField(b3Review, "candidateFreezeIdentityHash");
        String gateHash = stringField(b3Review, "acceptanceGateIdentityHash");

        List<String> bindingMismatches = new ArrayList<>();
        requireBinding(bindingMismatches, "b2Status",
                "CALIBRATION_EVIDENCE_READY_FOR_REVIEW", b2Status);
        requireBinding(bindingMismatches, "b3Status",
                "HOLDOUT_EVIDENCE_READY_FOR_FINAL_REVIEW", b3Status);
        requireBinding(bindingMismatches, "economyVerdict", "FAIL", economyVerdict);
        requireBinding(bindingMismatches, "tempoVerdict", "REVIEW_REQUIRED", tempoVerdict);
        requireBinding(bindingMismatches, "productionDecision", "NOT_EVALUATED",
                productionDecision);
        requireBinding(bindingMismatches, "calibrationMatches", 12_000L, calibrationMatches);
        requireBinding(bindingMismatches, "holdoutMatches", 4_000L, holdoutMatches);
        requireBinding(bindingMismatches, "b2ReviewSha256",
                b2.entries().get(B2_REVIEW_FILE),
                stringField(b3Review, "reviewFileSha256"));
        requireBinding(bindingMismatches, "b2ManifestSha256", b2.manifestSha256(),
                stringField(b3Review, "shaManifestSha256"));
        boolean exact = bindingMismatches.isEmpty();
        if (!exact) {
            throw new IllegalStateException("B2/B3 final evidence binding is not exact: "
                    + String.join("; ", bindingMismatches));
        }
        return new InputBinding(
                true,
                b2Status,
                b3Status,
                economyVerdict,
                tempoVerdict,
                productionDecision,
                calibrationMatches,
                holdoutMatches,
                frozenContractHash,
                candidateHash,
                gateHash,
                b2.manifestSha256(),
                b3.manifestSha256(),
                b2.entries().get(B2_REVIEW_FILE),
                b3.entries().get(B3_REVIEW_FILE),
                b2.entryCount(),
                b3.entryCount());
    }

    private static void requireBinding(
            List<String> mismatches,
            String field,
            Object expected,
            Object actual
    ) {
        if (!Objects.equals(expected, actual)) {
            mismatches.add(field + " expected=" + expected + " actual=" + actual);
        }
    }

    static ManifestEvidence verifyManifest(Path directory, int expectedEntries)
            throws IOException {
        Path manifest = directory.resolve(SHA_FILE);
        if (!Files.isRegularFile(manifest)) {
            throw new IllegalStateException("Missing SHA manifest " + manifest);
        }
        TreeMap<String, String> entries = new TreeMap<>();
        for (String rawLine : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            String line = rawLine.strip();
            if (line.isEmpty()) continue;
            if (line.length() < 67 || !line.substring(64, 66).equals("  ")) {
                throw new IllegalStateException("Malformed SHA manifest line");
            }
            String expected = line.substring(0, 64);
            String fileName = line.substring(66);
            if (entries.put(fileName, expected) != null) {
                throw new IllegalStateException("Duplicate SHA manifest entry " + fileName);
            }
            Path file = directory.resolve(fileName).normalize();
            if (!file.startsWith(directory.normalize()) || !Files.isRegularFile(file)) {
                throw new IllegalStateException("Invalid SHA manifest target " + fileName);
            }
            String actual = sha256(file);
            if (!actual.equals(expected)) {
                throw new IllegalStateException("SHA mismatch for " + fileName);
            }
        }
        if (entries.size() != expectedEntries) {
            throw new IllegalStateException("Expected " + expectedEntries
                    + " SHA entries but found " + entries.size());
        }
        return new ManifestEvidence(
                true, expectedEntries, sha256(manifest), Map.copyOf(entries));
    }

    private static void requireManifestEntry(ManifestEvidence manifest, String file) {
        if (!manifest.entries().containsKey(file)) {
            throw new IllegalStateException("SHA manifest does not bind " + file);
        }
    }

    private static List<PairedEvidence> loadPairedEvidence(
            Path directory,
            SourcePopulation population
    ) throws IOException {
        Map<String, Participant> participants = loadParticipants(directory, population);
        Map<String, TeamPair> teams = population == SourcePopulation.HOLDOUT_B3
                ? loadB3Teams(directory.resolve(B3_DRAFT_FILE)) : Map.of();
        ArrayList<PairedEvidence> result = new ArrayList<>();
        Path paired = directory.resolve(population == SourcePopulation.CALIBRATION_B2
                ? B2_PAIRED_FILE : B3_PAIRED_FILE);
        readCsv(paired, row -> {
            String comparison = row.get("comparisonId");
            if (!comparison.equals(ECONOMY) && !comparison.equals(TEMPO)) return;
            String fixtureId = required(row, "fixtureId");
            int seedIndex = integer(row, "seedIndex");
            String fromProfile = required(row, "fromProfile");
            String lane = population == SourcePopulation.CALIBRATION_B2
                    ? "CALIBRATION" : "HOLDOUT";
            String jobId = fixtureId + '|' + lane + '|' + seedIndex + '|' + fromProfile;
            Participant blue = participants.get(jobId + "|BLUE");
            Participant red = participants.get(jobId + "|RED");
            if (blue == null || red == null) {
                throw new IllegalStateException("Missing final jungler identity for " + jobId);
            }
            TeamPair teamPair = population == SourcePopulation.CALIBRATION_B2
                    ? new TeamPair(required(row, "blueTeamCode"),
                            required(row, "redTeamCode"))
                    : Objects.requireNonNull(teams.get(fixtureId),
                            "Missing B3 fixture team identity " + fixtureId);
            result.add(new PairedEvidence(
                    population,
                    comparison,
                    fixtureId,
                    required(row, "fixtureLane"),
                    required(row, "pairId"),
                    teamPair.blueTeamCode(),
                    teamPair.redTeamCode(),
                    seedIndex,
                    Long.parseLong(required(row, "seed")),
                    fromProfile,
                    required(row, "toProfile"),
                    required(row, "fromWinner"),
                    required(row, "toWinner"),
                    bool(row, "winnerFlipped"),
                    number(row, "durationDelta"),
                    number(row, "blueGoldEdgeDelta"),
                    number(row, "blueJungleCsDelta"),
                    number(row, "redJungleCsDelta"),
                    number(row, "blueJungleExperienceDelta"),
                    number(row, "redJungleExperienceDelta"),
                    number(row, "jungleGankAttemptsDelta"),
                    number(row, "counterGankAttemptsDelta"),
                    blue,
                    red));
        });
        return List.copyOf(result);
    }

    private static Map<String, Participant> loadParticipants(
            Path directory,
            SourcePopulation population
    ) throws IOException {
        Path file = directory.resolve(population == SourcePopulation.CALIBRATION_B2
                ? B2_JUNGLE_FILE : B3_JUNGLE_FILE);
        LinkedHashMap<String, Participant> result = new LinkedHashMap<>();
        readCsv(file, row -> {
            if (!required(row, "checkpointKind").equals("FINAL")) return;
            String jobId = required(row, "jobId");
            String side = required(row, "side");
            Participant participant = new Participant(
                    side, required(row, "playerId"), required(row, "championId"));
            if (result.put(jobId + '|' + side, participant) != null) {
                throw new IllegalStateException("Duplicate final jungler identity " + jobId);
            }
        });
        return Map.copyOf(result);
    }

    private static Map<String, TeamPair> loadB3Teams(Path fixedDrafts) throws IOException {
        LinkedHashMap<String, TeamPair> result = new LinkedHashMap<>();
        readCsv(fixedDrafts, row -> {
            String fixture = required(row, "fixtureId");
            TeamPair pair = new TeamPair(
                    required(row, "blueTeamCode"), required(row, "redTeamCode"));
            if (result.put(fixture, pair) != null) {
                throw new IllegalStateException("Duplicate fixed draft fixture " + fixture);
            }
        });
        if (result.size() != 100) {
            throw new IllegalStateException("Expected 100 B3 fixed drafts");
        }
        return Map.copyOf(result);
    }

    private static void requirePopulation(
            List<PairedEvidence> evidence,
            SourcePopulation population,
            String comparison,
            int expected
    ) {
        long actual = evidence.stream().filter(row -> row.population() == population)
                .filter(row -> row.comparisonId().equals(comparison)).count();
        if (actual != expected) {
            throw new IllegalStateException(population + " " + comparison
                    + " expected " + expected + " paired rows but found " + actual);
        }
    }

    static List<SegmentRow> segment(List<PairedEvidence> evidence) {
        ArrayList<SegmentRow> result = new ArrayList<>();
        for (SourcePopulation population : SourcePopulation.values()) {
            List<PairedEvidence> selected = population == SourcePopulation.COMBINED
                    ? evidence : evidence.stream()
                    .filter(row -> row.population() == population).toList();
            for (String comparison : List.of(ECONOMY, TEMPO)) {
                List<PairedEvidence> comparisonRows = selected.stream()
                        .filter(row -> row.comparisonId().equals(comparison)).toList();
                for (Dimension dimension : Dimension.values()) {
                    TreeMap<String, MutableSegment> values = new TreeMap<>();
                    for (PairedEvidence row : comparisonRows) {
                        for (String key : dimension.keys(row)) {
                            values.computeIfAbsent(key, ignored -> new MutableSegment())
                                    .record(row);
                        }
                    }
                    values.forEach((key, value) -> result.add(value.freeze(
                            population, comparison, dimension, key)));
                }
            }
        }
        result.sort(Comparator
                .comparing((SegmentRow row) -> row.population().ordinal())
                .thenComparingInt(row -> comparisonOrder(row.comparisonId()))
                .thenComparing(row -> row.dimension().ordinal())
                .thenComparing(SegmentRow::key));
        return List.copyOf(result);
    }

    static DecisionPolicy decisionPolicy(
            String economyFrozenVerdict,
            String tempoFrozenVerdict,
            boolean evidenceExact,
            double economyActual,
            double economyUpper
    ) {
        if (!evidenceExact) {
            return new DecisionPolicy(
                    "FINAL_EVIDENCE_INVALID",
                    "NO_PRODUCTION_DECISION",
                    "NO_PRODUCTION_DECISION",
                    "BLOCK_MATCH_ENGINE_V1_FREEZE");
        }
        if (!economyFrozenVerdict.equals("FAIL")
                || !tempoFrozenVerdict.equals("REVIEW_REQUIRED")
                || !(economyActual > economyUpper)) {
            throw new IllegalStateException("Unexpected frozen B3 decision inputs");
        }
        return new DecisionPolicy(
                "KEEP_CURRENT_RUNTIME_DEFAULT",
                "NOT_APPROVED_FOR_PRODUCTION_V1_BORDERLINE_FROZEN_GATE_FAILURE",
                "DEFER_TO_V2_PRODUCT_TOLERANCE_AND_TRAJECTORY_ISOLATION_REVIEW",
                "READY_TO_FREEZE_WITHOUT_JUNGLE_ECONOMY_OR_TEMPO_CANDIDATES");
    }

    private static Map<String, Object> bindingReport(
            InputBinding inputs,
            List<PairedEvidence> evidence
    ) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("allInputManifestEntriesExact", inputs.exact());
        report.put("b2", Map.of(
                "calibrationMatchExecutionCount", inputs.calibrationMatches(),
                "manifestEntryCount", inputs.b2ManifestEntries(),
                "manifestSha256", inputs.b2ManifestSha256(),
                "reviewSha256", inputs.b2ReviewSha256(),
                "status", inputs.b2Status()));
        report.put("b3", Map.of(
                "frozenContractHash", inputs.frozenContractHash(),
                "holdoutMatchExecutionCount", inputs.holdoutMatches(),
                "manifestEntryCount", inputs.b3ManifestEntries(),
                "manifestSha256", inputs.b3ManifestSha256(),
                "reviewSha256", inputs.b3ReviewSha256(),
                "status", inputs.b3Status()));
        report.put("candidateFreezeIdentityHash", inputs.candidateHash());
        report.put("gateIdentityHash", inputs.gateHash());
        report.put("newCalibrationMatchExecutionCount", 0);
        report.put("newHoldoutMatchExecutionCount", 0);
        report.put("newSimulationExecutionCount", 0);
        report.put("pairedEvidenceRowsRead", evidence.size());
        report.put("phase", PHASE);
        report.put("schemaVersion", "FINAL_13G_B_EVIDENCE_BINDING_V1");
        return report;
    }

    private static Map<String, Object> synthesisReport(
            InputBinding inputs,
            List<PairedEvidence> evidence,
            List<SegmentRow> segments,
            GateBoundary economyBoundary
    ) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("aggregateComparisons", comparisonReports(evidence));
        report.put("economy", Map.of(
                "frozenVerdict", inputs.economyFrozenVerdict(),
                "g1HoldoutActual", economyBoundary.actual(),
                "g1HoldoutUpperInclusive", economyBoundary.upperInclusive(),
                "percentagePointExcess",
                (economyBoundary.actual() - economyBoundary.upperInclusive()) * 100.0,
                "interpretation",
                "ONE_DISCRETE_FLIP_ABOVE_FROZEN_G1_BOUNDARY_NOT_A_STRUCTURAL_FAILURE",
                "rerunRequired", false));
        report.put("newSimulationExecutionCount", 0);
        report.put("phase", PHASE);
        report.put("schemaVersion", "FINAL_13G_B_SENSITIVITY_SYNTHESIS_V1");
        report.put("stratifiedCalibrationHoldoutCorrelation",
                correlationReport(segments));
        report.put("tempo", Map.of(
                "championDependenceInterpretation",
                "SYSTEMATIC_CHAMPION_DEPENDENCE_REPRODUCED_ACROSS_CALIBRATION_AND_HOLDOUT",
                "frozenVerdict", inputs.tempoFrozenVerdict(),
                "interpretation",
                "WINNER_FLIP_IS_TRAJECTORY_SENSITIVITY_NOT_DIRECT_WIN_RATE_EFFECT",
                "productToleranceDefined", false));
        report.put("topHoldoutSegments", topHoldoutSegments(segments));
        return report;
    }

    private static Map<String, Object> decisionReport(
            InputBinding inputs,
            DecisionPolicy policy,
            GateBoundary economyBoundary,
            List<PairedEvidence> evidence,
            List<SegmentRow> segments,
            Map<String, String> decisionInputs
    ) {
        double tempoChampionCorrelation = correlation(
                segmentRates(segments, SourcePopulation.CALIBRATION_B2, TEMPO,
                        Dimension.CHAMPION),
                segmentRates(segments, SourcePopulation.HOLDOUT_B3, TEMPO,
                        Dimension.CHAMPION));
        ComparisonStats tempoB2 = ComparisonStats.from(evidence.stream()
                .filter(row -> row.population() == SourcePopulation.CALIBRATION_B2)
                .filter(row -> row.comparisonId().equals(TEMPO)).toList());
        ComparisonStats tempoB3 = ComparisonStats.from(evidence.stream()
                .filter(row -> row.population() == SourcePopulation.HOLDOUT_B3)
                .filter(row -> row.comparisonId().equals(TEMPO)).toList());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("automaticTuningPerformed", false);
        report.put("decisionInputArtifactSha256", decisionInputs);
        report.put("economy", Map.of(
                "actualG1WinnerFlipRate", economyBoundary.actual(),
                "disposition", policy.economyDisposition(),
                "frozenVerdict", inputs.economyFrozenVerdict(),
                "gateRelabeledAfterHoldout", false,
                "interpretation", "BORDERLINE_ONE_DISCRETE_FLIP_GATE_FAILURE",
                "rerunHoldout", false,
                "upperInclusive", economyBoundary.upperInclusive()));
        report.put("evidenceStatus", "FINAL_EVIDENCE_VALID");
        report.put("matchEngineV1FreezeReadiness", policy.freezeReadiness());
        report.put("nextStep", "MATCH_ENGINE_V1_FREEZE");
        report.put("phase", PHASE);
        report.put("productionCandidateActivation", Map.of(
                "FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1", false,
                "FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1", false));
        report.put("productionDecision", policy.productionDecision());
        report.put("productionGameplayChanged", false);
        report.put("schemaVersion", "FINAL_13G_B_PRODUCTION_DECISION_V1");
        report.put("tempo", Map.of(
                "calibrationHoldoutChampionRateCorrelation", tempoChampionCorrelation,
                "calibrationWinnerFlipRate", tempoB2.winnerFlipRate(),
                "disposition", policy.tempoDisposition(),
                "frozenVerdict", inputs.tempoFrozenVerdict(),
                "holdoutWinnerFlipRate", tempoB3.winnerFlipRate(),
                "interpretation",
                "CHAMPION_DEPENDENT_REPRODUCIBLE_BUT_NO_PRODUCT_TOLERANCE",
                "rerunHoldout", false));
        report.put("tradeoffs", List.of(
                "Frozen Economy FAIL is preserved even though the miss is one discrete flip",
                "Tempo champion dependence is real but aggregate winner flip is not causal win rate",
                "Keeping the current runtime avoids an evidence override and consumes no new seeds",
                "Any V2 candidate requires a new contract, calibration plan, and fresh holdout"));
        return report;
    }

    private static Map<String, Object> comparisonReports(List<PairedEvidence> evidence) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String comparison : List.of(ECONOMY, TEMPO)) {
            Map<String, Object> populations = new LinkedHashMap<>();
            for (SourcePopulation population : SourcePopulation.values()) {
                List<PairedEvidence> rows = evidence.stream()
                        .filter(row -> population == SourcePopulation.COMBINED
                                || row.population() == population)
                        .filter(row -> row.comparisonId().equals(comparison)).toList();
                populations.put(population.name(), ComparisonStats.from(rows).asMap());
            }
            result.put(comparison, populations);
        }
        return result;
    }

    private static Map<String, Object> correlationReport(List<SegmentRow> segments) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String comparison : List.of(ECONOMY, TEMPO)) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Dimension dimension : List.of(
                    Dimension.FIXTURE,
                    Dimension.TEAM,
                    Dimension.PLAYER,
                    Dimension.CHAMPION,
                    Dimension.PLAYER_CHAMPION)) {
                Map<String, Double> b2 = segmentRates(
                        segments, SourcePopulation.CALIBRATION_B2, comparison, dimension);
                Map<String, Double> b3 = segmentRates(
                        segments, SourcePopulation.HOLDOUT_B3, comparison, dimension);
                values.put(dimension.name(), Map.of(
                        "commonKeyCount", b2.keySet().stream()
                                .filter(b3::containsKey).count(),
                        "pearsonRateCorrelation", correlation(b2, b3)));
            }
            result.put(comparison, values);
        }
        return result;
    }

    private static Map<String, Object> topHoldoutSegments(List<SegmentRow> segments) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String comparison : List.of(ECONOMY, TEMPO)) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Dimension dimension : List.of(
                    Dimension.FIXTURE,
                    Dimension.TEAM,
                    Dimension.PLAYER,
                    Dimension.CHAMPION,
                    Dimension.PLAYER_CHAMPION,
                    Dimension.CHAMPION_MATCHUP)) {
                List<Map<String, Object>> top = segments.stream()
                        .filter(row -> row.population() == SourcePopulation.HOLDOUT_B3)
                        .filter(row -> row.comparisonId().equals(comparison))
                        .filter(row -> row.dimension() == dimension)
                        .sorted(Comparator.comparingDouble(SegmentRow::winnerFlipRate)
                                .reversed().thenComparing(SegmentRow::key))
                        .limit(5)
                        .map(row -> Map.<String, Object>of(
                                "exposureCount", row.exposureCount(),
                                "key", row.key(),
                                "winnerFlipCount", row.winnerFlipCount(),
                                "winnerFlipRate", row.winnerFlipRate()))
                        .toList();
                values.put(dimension.name(), top);
            }
            result.put(comparison, values);
        }
        return result;
    }

    private static Map<String, Double> segmentRates(
            List<SegmentRow> segments,
            SourcePopulation population,
            String comparison,
            Dimension dimension
    ) {
        TreeMap<String, Double> result = new TreeMap<>();
        segments.stream().filter(row -> row.population() == population)
                .filter(row -> row.comparisonId().equals(comparison))
                .filter(row -> row.dimension() == dimension)
                .forEach(row -> result.put(row.key(), row.winnerFlipRate()));
        return result;
    }

    static double correlation(Map<String, Double> left, Map<String, Double> right) {
        List<String> keys = left.keySet().stream().filter(right::containsKey).sorted().toList();
        if (keys.size() < 2) return 0.0;
        double leftMean = keys.stream().mapToDouble(left::get).average().orElseThrow();
        double rightMean = keys.stream().mapToDouble(right::get).average().orElseThrow();
        double numerator = 0.0;
        double leftSquares = 0.0;
        double rightSquares = 0.0;
        for (String key : keys) {
            double a = left.get(key) - leftMean;
            double b = right.get(key) - rightMean;
            numerator += a * b;
            leftSquares += a * a;
            rightSquares += b * b;
        }
        if (leftSquares == 0.0 || rightSquares == 0.0) return 0.0;
        return numerator / Math.sqrt(leftSquares * rightSquares);
    }

    private static GateBoundary readEconomyBoundary(Path gateFile) throws IOException {
        String json = Files.readString(gateFile, StandardCharsets.UTF_8);
        int marker = json.indexOf("\"gateId\" : \"" + ECONOMY_G1_GATE + "\"");
        if (marker < 0) throw new IllegalStateException("Missing frozen Economy G1 gate");
        int from = json.lastIndexOf('{', marker);
        int to = json.indexOf('}', marker);
        if (from < 0 || to < 0) throw new IllegalStateException("Malformed frozen gate object");
        String object = json.substring(from, to + 1);
        boolean passed = booleanField(object, "passed");
        GateBoundary boundary = new GateBoundary(
                doubleField(object, "actual"),
                doubleField(object, "lowerInclusive"),
                doubleField(object, "upperInclusive"),
                passed);
        if (boundary.passed() || !(boundary.actual() > boundary.upperInclusive())) {
            throw new IllegalStateException("Economy frozen gate is not the expected failure");
        }
        return boundary;
    }

    private static String segmentsCsv(List<SegmentRow> rows) {
        StringBuilder out = new StringBuilder();
        csv(out, "sourcePopulation", "comparisonId", "dimension", "key",
                "exposureCount", "winnerFlipCount", "winnerFlipRate",
                "blueToRedFlipCount", "redToBlueFlipCount");
        rows.forEach(row -> csv(out,
                row.population(), row.comparisonId(), row.dimension(), row.key(),
                row.exposureCount(), row.winnerFlipCount(), row.winnerFlipRate(),
                row.blueToRedFlipCount(), row.redToBlueFlipCount()));
        return out.toString();
    }

    private static String flippedPairsCsv(List<PairedEvidence> evidence) {
        StringBuilder out = new StringBuilder();
        csv(out, "sourcePopulation", "comparisonId", "fixtureId", "fixtureLane",
                "blueTeamCode", "redTeamCode", "seedIndex", "seed", "fromWinner",
                "toWinner", "bluePlayerId", "blueChampionId", "redPlayerId",
                "redChampionId", "durationDelta", "blueGoldEdgeDelta",
                "blueJungleCsDelta", "redJungleCsDelta",
                "blueJungleExperienceDelta", "redJungleExperienceDelta",
                "jungleGankAttemptsDelta", "counterGankAttemptsDelta");
        evidence.stream().filter(PairedEvidence::winnerFlipped)
                .sorted(Comparator
                        .comparing((PairedEvidence row) -> row.population().ordinal())
                        .thenComparingInt(row -> comparisonOrder(row.comparisonId()))
                        .thenComparing(PairedEvidence::fixtureId)
                        .thenComparingInt(PairedEvidence::seedIndex))
                .forEach(row -> csv(out,
                        row.population(), row.comparisonId(), row.fixtureId(), row.fixtureLane(),
                        row.blueTeamCode(), row.redTeamCode(), row.seedIndex(), row.seed(),
                        row.fromWinner(), row.toWinner(), row.blueJungler().playerId(),
                        row.blueJungler().championId(), row.redJungler().playerId(),
                        row.redJungler().championId(), row.durationDelta(),
                        row.blueGoldEdgeDelta(), row.blueJungleCsDelta(),
                        row.redJungleCsDelta(), row.blueJungleExperienceDelta(),
                        row.redJungleExperienceDelta(), row.jungleGankAttemptsDelta(),
                        row.counterGankAttemptsDelta()));
        return out.toString();
    }

    private static int comparisonOrder(String comparison) {
        return comparison.equals(ECONOMY) ? 0 : 1;
    }

    private static void writeManifest(Path output) throws IOException {
        StringBuilder manifest = new StringBuilder();
        for (String file : OUTPUT_FILES) {
            manifest.append(sha256(output.resolve(file))).append("  ").append(file).append('\n');
        }
        writeUtf8(output.resolve(SHA_FILE), manifest.toString());
    }

    private static void readCsv(Path file, Consumer<Map<String, String>> consumer)
            throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new IllegalStateException("Empty CSV " + file);
            List<String> header = parseCsvLine(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                List<String> values = parseCsvLine(line);
                if (values.size() != header.size()) {
                    throw new IllegalStateException("CSV width mismatch in " + file);
                }
                LinkedHashMap<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < header.size(); i++) row.put(header.get(i), values.get(i));
                consumer.accept(row);
            }
        }
    }

    static List<String> parseCsvLine(String line) {
        ArrayList<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unclosed CSV quote");
        values.add(value.toString());
        return List.copyOf(values);
    }

    private static String required(Map<String, String> row, String field) {
        String value = row.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing CSV field " + field);
        }
        return value;
    }

    private static int integer(Map<String, String> row, String field) {
        return Integer.parseInt(required(row, field));
    }

    private static double number(Map<String, String> row, String field) {
        return Double.parseDouble(required(row, field));
    }

    private static boolean bool(Map<String, String> row, String field) {
        return Boolean.parseBoolean(required(row, field));
    }

    private static String stringField(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field)
                + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        if (!matcher.find()) throw new IllegalStateException("Missing JSON field " + field);
        return matcher.group(1);
    }

    private static long longField(String json, String field) {
        Matcher matcher = numberMatcher(json, field);
        return Long.parseLong(matcher.group(1));
    }

    private static long lastLongField(String json, String field) {
        Matcher matcher = numberPattern(field).matcher(json);
        String value = null;
        while (matcher.find()) value = matcher.group(1);
        if (value == null) throw new IllegalStateException("Missing JSON number " + field);
        return Long.parseLong(value);
    }

    private static double doubleField(String json, String field) {
        Matcher matcher = numberMatcher(json, field);
        return Double.parseDouble(matcher.group(1));
    }

    private static Matcher numberMatcher(String json, String field) {
        Matcher matcher = numberPattern(field).matcher(json);
        if (!matcher.find()) throw new IllegalStateException("Missing JSON number " + field);
        return matcher;
    }

    private static Pattern numberPattern(String field) {
        return Pattern.compile("\\\"" + Pattern.quote(field)
                + "\\\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[Ee][+-]?[0-9]+)?)");
    }

    private static boolean booleanField(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field)
                + "\\\"\\s*:\\s*(true|false)").matcher(json);
        if (!matcher.find()) throw new IllegalStateException("Missing JSON boolean " + field);
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static void writeJson(Path output, Object value) throws IOException {
        writeUtf8(output, canonicalJson(value) + '\n');
    }

    static String canonicalJson(Object value) {
        StringBuilder out = new StringBuilder();
        appendJson(out, value);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJson(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            out.append('"').append(jsonEscape(string)).append('"');
        } else if (value instanceof Boolean || value instanceof Integer
                || value instanceof Long) {
            out.append(value);
        } else if (value instanceof Number number) {
            double decimal = number.doubleValue();
            if (!Double.isFinite(decimal)) throw new IllegalArgumentException("Non-finite JSON");
            out.append(BigDecimal.valueOf(decimal).stripTrailingZeros().toPlainString());
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, entry) -> sorted.put(Objects.toString(key), entry));
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) out.append(',');
                first = false;
                appendJson(out, entry.getKey());
                out.append(':');
                appendJson(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object entry : iterable) {
                if (!first) out.append(',');
                first = false;
                appendJson(out, entry);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported canonical JSON type "
                    + value.getClass());
        }
    }

    private static String jsonEscape(String value) {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (current < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) current));
                    } else {
                        out.append(current);
                    }
                }
            }
        }
        return out.toString();
    }

    private static void csv(StringBuilder out, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) out.append(',');
            String value = Objects.toString(values[index], "");
            if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                    || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                out.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else {
                out.append(value);
            }
        }
        out.append('\n');
    }

    private static void writeUtf8(Path output, String content) throws IOException {
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), output.getFileName().toString(),
                ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    enum SourcePopulation {
        CALIBRATION_B2,
        HOLDOUT_B3,
        COMBINED
    }

    enum Dimension {
        ALL {
            @Override
            List<String> keys(PairedEvidence row) {
                return List.of("ALL");
            }
        },
        FIXTURE {
            @Override
            List<String> keys(PairedEvidence row) {
                return List.of(row.fixtureId());
            }
        },
        TEAM {
            @Override
            List<String> keys(PairedEvidence row) {
                return List.of(row.blueTeamCode(), row.redTeamCode());
            }
        },
        TEAM_SIDE {
            @Override
            List<String> keys(PairedEvidence row) {
                return List.of("BLUE|" + row.blueTeamCode(), "RED|" + row.redTeamCode());
            }
        },
        PLAYER {
            @Override
            List<String> keys(PairedEvidence row) {
                return List.of(row.blueJungler().playerId(), row.redJungler().playerId());
            }
        },
        PLAYER_SIDE {
            @Override
            List<String> keys(PairedEvidence row) {
                return List.of("BLUE|" + row.blueJungler().playerId(),
                        "RED|" + row.redJungler().playerId());
            }
        },
        CHAMPION {
            @Override
            List<String> keys(PairedEvidence row) {
                return List.of(row.blueJungler().championId(), row.redJungler().championId());
            }
        },
        CHAMPION_SIDE {
            @Override
            List<String> keys(PairedEvidence row) {
                return List.of("BLUE|" + row.blueJungler().championId(),
                        "RED|" + row.redJungler().championId());
            }
        },
        PLAYER_CHAMPION {
            @Override
            List<String> keys(PairedEvidence row) {
                return List.of(
                        row.blueJungler().playerId() + '@' + row.blueJungler().championId(),
                        row.redJungler().playerId() + '@' + row.redJungler().championId());
            }
        },
        PLAYER_CHAMPION_SIDE {
            @Override
            List<String> keys(PairedEvidence row) {
                return List.of("BLUE|" + row.blueJungler().playerId() + '@'
                                + row.blueJungler().championId(),
                        "RED|" + row.redJungler().playerId() + '@'
                                + row.redJungler().championId());
            }
        },
        CHAMPION_MATCHUP {
            @Override
            List<String> keys(PairedEvidence row) {
                String blue = row.blueJungler().championId();
                String red = row.redJungler().championId();
                return List.of(blue.compareTo(red) <= 0
                        ? blue + '|' + red : red + '|' + blue);
            }
        };

        abstract List<String> keys(PairedEvidence row);
    }

    record Participant(String side, String playerId, String championId) {
    }

    record TeamPair(String blueTeamCode, String redTeamCode) {
    }

    record PairedEvidence(
            SourcePopulation population,
            String comparisonId,
            String fixtureId,
            String fixtureLane,
            String pairId,
            String blueTeamCode,
            String redTeamCode,
            int seedIndex,
            long seed,
            String fromProfile,
            String toProfile,
            String fromWinner,
            String toWinner,
            boolean winnerFlipped,
            double durationDelta,
            double blueGoldEdgeDelta,
            double blueJungleCsDelta,
            double redJungleCsDelta,
            double blueJungleExperienceDelta,
            double redJungleExperienceDelta,
            double jungleGankAttemptsDelta,
            double counterGankAttemptsDelta,
            Participant blueJungler,
            Participant redJungler
    ) {
        PairedEvidence {
            Objects.requireNonNull(population, "population");
            Objects.requireNonNull(blueJungler, "blueJungler");
            Objects.requireNonNull(redJungler, "redJungler");
            if (population == SourcePopulation.COMBINED) {
                throw new IllegalArgumentException("Raw evidence cannot be COMBINED");
            }
            if (winnerFlipped == fromWinner.equals(toWinner)) {
                throw new IllegalArgumentException("Winner flip flag and winners differ");
            }
        }
    }

    private static final class MutableSegment {
        private int exposureCount;
        private int winnerFlipCount;
        private int blueToRedFlipCount;
        private int redToBlueFlipCount;

        void record(PairedEvidence row) {
            exposureCount++;
            if (!row.winnerFlipped()) return;
            winnerFlipCount++;
            if (row.fromWinner().equals("BLUE") && row.toWinner().equals("RED")) {
                blueToRedFlipCount++;
            } else if (row.fromWinner().equals("RED") && row.toWinner().equals("BLUE")) {
                redToBlueFlipCount++;
            } else {
                throw new IllegalStateException("Unknown winner flip direction");
            }
        }

        SegmentRow freeze(
                SourcePopulation population,
                String comparison,
                Dimension dimension,
                String key
        ) {
            return new SegmentRow(
                    population,
                    comparison,
                    dimension,
                    key,
                    exposureCount,
                    winnerFlipCount,
                    winnerFlipCount / (double) exposureCount,
                    blueToRedFlipCount,
                    redToBlueFlipCount);
        }
    }

    record SegmentRow(
            SourcePopulation population,
            String comparisonId,
            Dimension dimension,
            String key,
            int exposureCount,
            int winnerFlipCount,
            double winnerFlipRate,
            int blueToRedFlipCount,
            int redToBlueFlipCount
    ) {
    }

    record ComparisonStats(
            int count,
            int winnerFlipCount,
            double winnerFlipRate,
            int blueToRedFlipCount,
            int redToBlueFlipCount,
            double meanDurationDelta,
            double meanBlueGoldEdgeDelta,
            double meanBlueJungleCsDelta,
            double meanRedJungleCsDelta,
            double meanBlueJungleExperienceDelta,
            double meanRedJungleExperienceDelta,
            double meanJungleGankAttemptsDelta,
            double meanCounterGankAttemptsDelta
    ) {
        static ComparisonStats from(List<PairedEvidence> rows) {
            if (rows.isEmpty()) throw new IllegalArgumentException("Comparison rows are empty");
            int flips = (int) rows.stream().filter(PairedEvidence::winnerFlipped).count();
            int blueToRed = (int) rows.stream().filter(PairedEvidence::winnerFlipped)
                    .filter(row -> row.fromWinner().equals("BLUE")).count();
            int redToBlue = flips - blueToRed;
            return new ComparisonStats(
                    rows.size(),
                    flips,
                    flips / (double) rows.size(),
                    blueToRed,
                    redToBlue,
                    mean(rows, PairedEvidence::durationDelta),
                    mean(rows, PairedEvidence::blueGoldEdgeDelta),
                    mean(rows, PairedEvidence::blueJungleCsDelta),
                    mean(rows, PairedEvidence::redJungleCsDelta),
                    mean(rows, PairedEvidence::blueJungleExperienceDelta),
                    mean(rows, PairedEvidence::redJungleExperienceDelta),
                    mean(rows, PairedEvidence::jungleGankAttemptsDelta),
                    mean(rows, PairedEvidence::counterGankAttemptsDelta));
        }

        private static double mean(
                List<PairedEvidence> rows,
                java.util.function.ToDoubleFunction<PairedEvidence> metric
        ) {
            return rows.stream().mapToDouble(metric).average().orElseThrow();
        }

        Map<String, Object> asMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("blueToRedFlipCount", blueToRedFlipCount);
            result.put("count", count);
            result.put("meanBlueGoldEdgeDelta", meanBlueGoldEdgeDelta);
            result.put("meanBlueJungleCsDelta", meanBlueJungleCsDelta);
            result.put("meanBlueJungleExperienceDelta", meanBlueJungleExperienceDelta);
            result.put("meanCounterGankAttemptsDelta", meanCounterGankAttemptsDelta);
            result.put("meanDurationDelta", meanDurationDelta);
            result.put("meanJungleGankAttemptsDelta", meanJungleGankAttemptsDelta);
            result.put("meanRedJungleCsDelta", meanRedJungleCsDelta);
            result.put("meanRedJungleExperienceDelta", meanRedJungleExperienceDelta);
            result.put("redToBlueFlipCount", redToBlueFlipCount);
            result.put("winnerFlipCount", winnerFlipCount);
            result.put("winnerFlipRate", winnerFlipRate);
            return result;
        }
    }

    record GateBoundary(
            double actual,
            double lowerInclusive,
            double upperInclusive,
            boolean passed
    ) {
    }

    record DecisionPolicy(
            String productionDecision,
            String economyDisposition,
            String tempoDisposition,
            String freezeReadiness
    ) {
    }

    record ManifestEvidence(
            boolean exact,
            int entryCount,
            String manifestSha256,
            Map<String, String> entries
    ) {
    }

    record InputBinding(
            boolean exact,
            String b2Status,
            String b3Status,
            String economyFrozenVerdict,
            String tempoFrozenVerdict,
            String priorProductionDecision,
            long calibrationMatches,
            long holdoutMatches,
            String frozenContractHash,
            String candidateHash,
            String gateHash,
            String b2ManifestSha256,
            String b3ManifestSha256,
            String b2ReviewSha256,
            String b3ReviewSha256,
            int b2ManifestEntries,
            int b3ManifestEntries
    ) {
    }

    public record ArtifactSet(
            Path output,
            String evidenceStatus,
            String productionDecision,
            String economyDisposition,
            String tempoDisposition,
            int inputPairedRows,
            String manifestSha256
    ) {
    }
}
