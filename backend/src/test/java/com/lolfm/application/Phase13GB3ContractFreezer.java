package com.lolfm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.AcceptanceContract;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.B2EvidenceBinding;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.CandidateFreeze;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.ExactBehaviorGate;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.FrozenContract;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.FrozenIdentities;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.NumericGate;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.Population;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Freezes B2-derived candidate and gate bytes before any reserved holdout execution. */
public final class Phase13GB3ContractFreezer {
    private static final double CONFIDENCE = 0.99;
    private static final double Z = 2.5758293035489004;
    private static final int SCALE = 12;
    private static final String B2_CONTRACT = "phase13g-b2-calibration-contract.json";
    private static final String B2_REVIEW = "phase13g-b2-review.json";
    private static final String B2_INTEGRITY = "phase13g-b2-integrity.json";
    private static final String B2_MARGINALS = "phase13g-b2-paired-marginals.csv";
    private static final String B2_SHA = "SHA256SUMS.txt";
    private static final String B1_CONTRACT = "phase13g-b1-audit-contract.json";

    private final ObjectMapper mapper;

    public Phase13GB3ContractFreezer(ObjectMapper sourceMapper) {
        mapper = Phase13GB3CheckpointStore.canonicalMapper(sourceMapper);
    }

    public FreezeResult freeze(
            Path backendRoot,
            Path b1Report,
            Path b2Report,
            Path output
    ) throws IOException {
        requireUnusedHoldout(output);
        ManifestVerification manifest = verifyB2Manifest(b2Report);
        JsonNode b2Contract = mapper.readTree(b2Report.resolve(B2_CONTRACT).toFile());
        JsonNode b2Review = mapper.readTree(b2Report.resolve(B2_REVIEW).toFile());
        JsonNode b2Integrity = mapper.readTree(b2Report.resolve(B2_INTEGRITY).toFile());
        JsonNode b1Contract = mapper.readTree(b1Report.resolve(B1_CONTRACT).toFile());
        requireB2Ready(b2Contract, b2Review, b2Integrity, manifest);

        FrozenIdentities identities = identities(backendRoot, b1Contract, b2Contract);
        requireCurrentIdentityBinding(backendRoot, b1Contract, b2Contract, identities);
        List<MarginalSample> samples = readMarginals(b2Report.resolve(B2_MARGINALS));
        CandidateFreeze candidates = new CandidateFreeze(
                "JUNGLE_ECONOMY_CANDIDATE_V1",
                "ECONOMY_MINUS_FULL",
                "JUNGLE_TEMPO_CANDIDATE_V1",
                "TEMPO_MINUS_ECONOMY",
                Phase13GB3FrozenHoldoutContract.PROFILE_ORDER,
                List.of("MATCHUP_MINUS_BASELINE", "FULL_MINUS_MATCHUP",
                        "TEMPO_MINUS_BASELINE"),
                "BOTH_PASS_OR_ECONOMY_PASS_TEMPO_DEFERRED_OR_ECONOMY_FAIL",
                "B2_CALIBRATION_SELECTS_AND_CENTERS_GATES_B3_ONLY_EVALUATES_FROZEN_GATES");
        AcceptanceContract acceptance = acceptance(samples);
        String candidateHash = canonicalHash(candidates);
        String acceptanceHash = canonicalHash(acceptance);

        B2EvidenceBinding b2Binding = new B2EvidenceBinding(
                b2Review.path("status").asText(),
                b2Review.path("calibrationMatchExecutionCount").asInt(),
                b2Review.path("holdoutMatchExecutionCount").asInt(),
                b2Contract.path("runGuardHash").asText(),
                b2Review.path("checkpointPayloadManifestHash").asText(),
                sha(b2Report.resolve(B2_CONTRACT)),
                sha(b2Report.resolve(B2_REVIEW)),
                sha(b2Report.resolve(B2_INTEGRITY)),
                sha(b2Report.resolve(B2_SHA)),
                manifest.entryCount(),
                manifest.exact(),
                b2Contract.path("runGuard"));

        FrozenContract contract = new FrozenContract(
                Phase13GB3FrozenHoldoutContract.SCHEMA,
                Phase13GB3FrozenHoldoutContract.PHASE,
                b2Contract.path("runGuard").path("scheduleVersion").asText(),
                b2Contract.path("runGuard").path("scheduleHash").asText(),
                b2Binding,
                identities,
                candidates,
                candidateHash,
                acceptance,
                acceptanceHash,
                new Population(
                        "HOLDOUT", 100, 90, 10, 8, 5, 40,
                        3_600, 400, 4_000, 100, 4, 0),
                hardIntegrityGates(),
                prohibitions(),
                0,
                false,
                false,
                "NOT_EVALUATED",
                "FINAL_13G_B_SYNTHESIS_AND_PRODUCTION_V1_DECISION");

        byte[] bytes = canonicalBytesWithNewline(contract);
        String contractHash = Phase13GB3CheckpointStore.sha256(bytes);
        Files.createDirectories(output);
        Path contractPath = output.resolve(Phase13GB3CheckpointStore.CONTRACT_FILE);
        Path hashPath = output.resolve(Phase13GB3CheckpointStore.CONTRACT_HASH_FILE);
        writeOrRequireExact(contractPath, bytes);
        writeOrRequireExact(hashPath, (contractHash + "  "
                + Phase13GB3CheckpointStore.CONTRACT_FILE + '\n')
                .getBytes(StandardCharsets.UTF_8));
        writeAuthorizations(output, contractHash);
        return new FreezeResult(
                contractPath,
                contractHash,
                candidateHash,
                acceptanceHash,
                acceptance.numericGates().size(),
                acceptance.exactBehaviorGates().size(),
                0);
    }

    private FrozenIdentities identities(
            Path backendRoot,
            JsonNode b1Contract,
            JsonNode b2Contract
    ) throws IOException {
        EnumMap<SimulationRuntimeProfileId, String> rules =
                new EnumMap<>(SimulationRuntimeProfileId.class);
        EnumMap<SimulationRuntimeProfileId, String> configurations =
                new EnumMap<>(SimulationRuntimeProfileId.class);
        Phase13GB3FrozenHoldoutContract.PROFILE_ORDER.forEach(profileId -> {
            var profile = SimulationRuntimeProfiles.resolve(profileId);
            rules.put(profileId, profile.activeGameplayRulesVersion());
            configurations.put(profileId, profile.configurationHash());
        });
        JsonNode resources = b1Contract.path("resourceProvenance");
        Map<String, String> resourceShas = new HashMap<>();
        resources.path("resources").forEach(resource -> resourceShas.put(
                resource.path("role").asText(), resource.path("sha256").asText()));
        JsonNode guard = b2Contract.path("runGuard");
        return new FrozenIdentities(
                SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION,
                rules,
                configurations,
                resources.path("resourceProvenanceHash").asText(),
                resources,
                resourceShas.get("PLAYER_IDENTITY"),
                resourceShas.get("PLAYER_RATINGS"),
                resourceShas.get("PLAYER_PROFICIENCY"),
                guard.path("draftRuleSetIdentity").asText(),
                guard.path("draftRuleSetHash").asText(),
                guard.path("draftScoringPolicyHash").asText(),
                Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot),
                Phase13GB1AuditArtifactWriter.phaseTestSourceTree(
                        backendRoot, "Phase13GB1"),
                Phase13GB1AuditArtifactWriter.phaseTestSourceTree(
                        backendRoot, "Phase13GB2"),
                Phase13GB1AuditArtifactWriter.phaseTestSourceTree(
                        backendRoot, "Phase13GB3"));
    }

    private void requireCurrentIdentityBinding(
            Path backendRoot,
            JsonNode b1Contract,
            JsonNode b2Contract,
            FrozenIdentities identities
    ) {
        JsonNode b2Guard = b2Contract.path("runGuard");
        String combinedB1B2 = combinedHarnessHash(backendRoot);
        if (!b1Contract.path("productionSourceTree").path("hash").asText()
                        .equals(identities.productionSourceTree().hash())
                || !b1Contract.path("auditHarnessSourceTree").path("hash").asText()
                        .equals(identities.phase13GB1HarnessSourceTree().hash())
                || !b2Guard.path("productionSourceTree").path("hash").asText()
                        .equals(identities.productionSourceTree().hash())
                || !b2Guard.path("phase13GBHarnessSourceTree").path("hash").asText()
                        .equals(combinedB1B2)
                || !b2Guard.path("engineImplementationVersion").asText()
                        .equals(identities.engineImplementationVersion())
                || !b2Guard.path("resourceProvenanceHash").asText()
                        .equals(identities.resourceProvenanceHash())) {
            throw new IllegalStateException(
                    "B2 evidence is stale against the final B1/B2 executable identity");
        }
        identities.configurationHashes().forEach((profile, hash) -> {
            if (!b2Guard.path("configurationHashes").path(profile.name()).asText()
                    .equals(hash)) {
                throw new IllegalStateException("B2 configuration identity is stale");
            }
        });
    }

    private String combinedHarnessHash(Path backendRoot) {
        // B2's current guard is computed directly over the union, not from child hashes.
        try {
            return Phase13GB1AuditArtifactWriter.phaseTestSourceTree(
                    backendRoot, "Phase13GB").hash();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private AcceptanceContract acceptance(List<MarginalSample> samples) {
        ArrayList<NumericGate> numeric = new ArrayList<>();
        List<LanePopulation> lanes = List.of(
                new LanePopulation("ALL", 2_400, 800),
                new LanePopulation("PRIMARY_LEAGUE_G1", 2_160, 720),
                new LanePopulation("SECONDARY_HARD_FEARLESS_G2", 240, 80));
        List<Metric> metrics = List.of(
                Metric.WINNER_FLIP_RATE,
                Metric.DURATION_SECONDS,
                Metric.BLUE_GOLD_EDGE,
                Metric.BLUE_JUNGLE_CS,
                Metric.RED_JUNGLE_CS,
                Metric.BLUE_JUNGLE_EXPERIENCE,
                Metric.RED_JUNGLE_EXPERIENCE,
                Metric.JUNGLE_GANK_ATTEMPTS,
                Metric.COUNTER_GANK_ATTEMPTS,
                Metric.JUNGLE_CS_SIDE_GAP,
                Metric.JUNGLE_EXPERIENCE_SIDE_GAP);
        for (String comparison : List.of("ECONOMY_MINUS_FULL", "TEMPO_MINUS_ECONOMY")) {
            String candidate = comparison.startsWith("ECONOMY") ? "ECONOMY" : "TEMPO";
            for (LanePopulation lane : lanes) {
                List<MarginalSample> selected = samples.stream()
                        .filter(value -> value.comparisonId().equals(comparison))
                        .filter(value -> lane.lane().equals("ALL")
                                || value.fixtureLane().equals(lane.lane()))
                        .toList();
                if (selected.size() != lane.calibrationCount()) {
                    throw new IllegalStateException("B2 paired sample count differs");
                }
                for (Metric metric : metrics) {
                    numeric.add(gate(candidate, comparison, lane, metric, selected));
                }
            }
        }
        List<MarginalSample> tempoFlips = samples.stream()
                .filter(value -> value.comparisonId().equals("TEMPO_MINUS_ECONOMY"))
                .filter(MarginalSample::winnerFlipped).toList();
        long blueToRed = tempoFlips.stream().filter(value ->
                value.fromWinner().equals("BLUE") && value.toWinner().equals("RED")).count();
        int expectedHoldoutFlips = (int) Math.round(800.0 * tempoFlips.size() / 2_400.0);
        numeric.add(proportionGate(
                "TEMPO_FLIP_DIRECTION_SHARE_ALL",
                "TEMPO",
                "TEMPO_MINUS_ECONOMY",
                "ALL",
                "BLUE_TO_RED_SHARE_OF_FLIPS",
                tempoFlips.size(),
                expectedHoldoutFlips,
                blueToRed / (double) tempoFlips.size()));

        return new AcceptanceContract(
                "PHASE_13G_B3_ACCEPTANCE_GATES_V1",
                "B2_CENTERED_TWO_SAMPLE_NORMAL_PREDICTION_TOLERANCE",
                CONFIDENCE,
                Z,
                "OUTWARD_TO_12_DECIMAL_PLACES",
                "ALL_NUMERIC_BOUNDARIES_INCLUSIVE",
                numeric,
                exactBehaviorGates(),
                "PASS_IFF_ALL_ECONOMY_NUMERIC_AND_EXACT_BEHAVIOR_GATES_PASS_ELSE_FAIL",
                "STRUCTURAL_FAILURE_FAILS;_NUMERIC_INCONSISTENCY_DEFERS_TO_V2;"
                        + "CONSISTENT_HIGH_SENSITIVITY_REMAINS_REVIEW_REQUIRED",
                "B2_33_7916666667_PERCENT_IS_NOT_AUTOMATIC_PASS_OR_FAIL;"
                        + "B3_MUST_FALL_INSIDE_FROZEN_99_PERCENT_TOLERANCE_AND_STILL_REQUIRES_REVIEW");
    }

    private NumericGate gate(
            String candidate,
            String comparison,
            LanePopulation lane,
            Metric metric,
            List<MarginalSample> samples
    ) {
        List<Double> values = samples.stream().map(metric::value).toList();
        double mean = values.stream().mapToDouble(Double::doubleValue)
                .average().orElseThrow();
        double sd = sampleStandardDeviation(values, mean);
        double radius;
        String derivation;
        if (metric == Metric.WINNER_FLIP_RATE) {
            radius = Z * Math.sqrt(mean * (1.0 - mean)
                    * (1.0 / lane.calibrationCount() + 1.0 / lane.holdoutCount()));
            derivation = "P_PLUS_OR_MINUS_Z_SQRT_P1MP_TIMES_1_OVER_NC_PLUS_1_OVER_NH_CLIPPED_0_1";
        } else {
            radius = Z * sd * Math.sqrt(
                    1.0 / lane.calibrationCount() + 1.0 / lane.holdoutCount());
            derivation = "MEAN_PLUS_OR_MINUS_Z_TIMES_SAMPLE_SD_TIMES_"
                    + "SQRT_1_OVER_NC_PLUS_1_OVER_NH";
        }
        double lower = mean - radius;
        double upper = mean + radius;
        if (metric == Metric.WINNER_FLIP_RATE) {
            lower = Math.max(0.0, lower);
            upper = Math.min(1.0, upper);
        }
        return new NumericGate(
                candidate + '_' + comparison + '_' + lane.lane() + '_' + metric,
                candidate,
                comparison,
                lane.lane(),
                metric.name(),
                lane.calibrationCount(),
                lane.holdoutCount(),
                mean,
                sd,
                outwardLower(lower),
                outwardUpper(upper),
                "INCLUSIVE_LOWER_AND_UPPER",
                derivation);
    }

    private NumericGate proportionGate(
            String gateId,
            String candidate,
            String comparison,
            String lane,
            String metric,
            int calibrationCount,
            int holdoutCount,
            double point
    ) {
        double sd = Math.sqrt(point * (1.0 - point));
        double radius = Z * Math.sqrt(point * (1.0 - point)
                * (1.0 / calibrationCount + 1.0 / holdoutCount));
        return new NumericGate(
                gateId, candidate, comparison, lane, metric,
                calibrationCount, holdoutCount, point, sd,
                outwardLower(Math.max(0.0, point - radius)),
                outwardUpper(Math.min(1.0, point + radius)),
                "INCLUSIVE_LOWER_AND_UPPER",
                "P_PLUS_OR_MINUS_Z_SQRT_P1MP_TIMES_1_OVER_NC_PLUS_1_OVER_NH_CLIPPED_0_1;"
                        + "NH_IS_B2_RATE_TIMES_800_ROUNDED_HALF_UP_BEFORE_HOLDOUT");
    }

    private List<ExactBehaviorGate> exactBehaviorGates() {
        return List.of(
                new ExactBehaviorGate("ECONOMY_AWARD_REACHABILITY", "ECONOMY",
                        "TOTAL_AWARDED_CS_GOLD_XP", "EACH_GREATER_THAN", "0",
                        "B2=818783/16375660/50830047"),
                new ExactBehaviorGate("PRE_JUNGLE_CONTRIBUTION_ZERO", "BOTH",
                        "PROFILE_INDEX_0_TO_2_JUNGLE_CONTRIBUTION", "EQUAL", "0",
                        "B2 exact zero"),
                new ExactBehaviorGate("ECONOMY_TEMPO_CONTRIBUTION_ZERO", "ECONOMY",
                        "PROFILE_INDEX_3_TEMPO_CONTRIBUTION", "EQUAL", "0",
                        "B2 exact zero"),
                new ExactBehaviorGate("TEMPO_REACHABILITY", "TEMPO",
                        "GANK_READY_CONSUMED_AND_COUNTER_READY_CONSUMED",
                        "EACH_GREATER_THAN", "0", "B2=47495/5175/4447/660"),
                new ExactBehaviorGate("TEMPO_CONSUMPTION_ATTEMPT_BINDING", "TEMPO",
                        "GANK_AND_COUNTER_CONSUMPTION_MINUS_ATTEMPTS", "EQUAL", "0",
                        "B2 exact equality"),
                new ExactBehaviorGate("SUPPORT_FARM_CS_INVARIANT", "BOTH",
                        "ALL_FINAL_SUPPORT_CS", "EQUAL", "0",
                        "Production invariant; structured final player state added for B3"),
                new ExactBehaviorGate("TIMEOUTS", "BOTH", "ALL_PROFILE_TIMEOUT_COUNT",
                        "EQUAL", "0", "B2 exact zero"));
    }

    private List<String> hardIntegrityGates() {
        return List.of(
                "EXACT_100_FIXTURES_4000_HOLDOUT_ROWS_3600_G1_400_G2",
                "CALIBRATION_MATCH_EXECUTION_COUNT_EXACT_0",
                "EXACT_8_HOLDOUT_SEEDS_PER_FIXTURE_AND_40_ROWS_PER_FIXTURE",
                "CALIBRATION_AND_HOLDOUT_SEEDS_DISJOINT",
                "FIVE_PROFILE_ORDER_EXACT",
                "4000_UNIQUE_JOB_IDS_AND_REPLAY_PROVENANCE_HASHES",
                "ONE_FIXED_DRAFT_AND_FINAL_ASSIGNMENT_PER_FIXTURE",
                "SCHEDULE_CONFIGURATION_RESOURCE_ROSTER_DRAFT_IDENTITIES_EXACT",
                "100_BASELINE_SAME_SEED_REPLAYS_TIMELINE_RANDOM_DIAGNOSTICS_EXACT",
                "CHECKPOINT_ROW_AND_COMBINED_PAYLOAD_DIGESTS_EXACT",
                "FOUR_RECEIPTS_FOUR_DISTINCT_FRESH_JVMS_MODULO_OWNERSHIP_EXACT",
                "CHECKPOINT_RAW_BYTE_SHA_AND_FINAL_SHA_MANIFEST_EXACT",
                "ALL_STRUCTURED_DOMAIN_INTEGRITY_ERRORS_EXACT_0",
                "ONE_MAJOR_COMBAT_REWARD_DEATH_AND_FARM_INTEGRITY_ERRORS_EXACT_0",
                "ALL_PROFILE_TIMEOUTS_EXACT_0",
                "PRE_JUNGLE_CONTRIBUTION_EXACT_0_AND_ECONOMY_TEMPO_EXACT_0",
                "OFFICIAL_WRITER_REQUIRES_VERIFIED_RECEIPT_BOUND_EVIDENCE",
                "SYNTHETIC_STATUS_CANNOT_PRODUCE_OFFICIAL_READY");
    }

    private List<String> prohibitions() {
        return List.of(
                "NO_AUTOMATIC_TUNING",
                "NO_POST_HOLDOUT_GATE_OR_CANDIDATE_CHANGE",
                "NO_CALIBRATION_ROW_RELABELING",
                "NO_B2_CHECKPOINT_REUSE",
                "NO_SYNTHETIC_OFFICIAL_VERDICT",
                "NO_ACCEPTANCE_FAIL_RERUN",
                "NO_PRODUCTION_RUNTIME_DEFAULT_CHANGE",
                "PRODUCTION_DECISION_REMAINS_NOT_EVALUATED");
    }

    private List<MarginalSample> readMarginals(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String[] header = lines.getFirst().split(",", -1);
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.length; index++) columns.put(header[index], index);
        ArrayList<MarginalSample> result = new ArrayList<>(lines.size() - 1);
        for (int line = 1; line < lines.size(); line++) {
            String[] values = lines.get(line).split(",", -1);
            result.add(new MarginalSample(
                    value(values, columns, "comparisonId"),
                    value(values, columns, "fixtureLane"),
                    value(values, columns, "fromWinner"),
                    value(values, columns, "toWinner"),
                    Boolean.parseBoolean(value(values, columns, "winnerFlipped")),
                    integer(values, columns, "durationDelta"),
                    integer(values, columns, "blueGoldEdgeDelta"),
                    integer(values, columns, "blueJungleCsDelta"),
                    integer(values, columns, "redJungleCsDelta"),
                    integer(values, columns, "blueJungleExperienceDelta"),
                    integer(values, columns, "redJungleExperienceDelta"),
                    integer(values, columns, "jungleGankAttemptsDelta"),
                    integer(values, columns, "counterGankAttemptsDelta")));
        }
        if (result.size() != 12_000) {
            throw new IllegalStateException("B2 marginal population is incomplete");
        }
        return List.copyOf(result);
    }

    private String value(String[] values, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null || index >= values.length) {
            throw new IllegalStateException("Missing B2 marginal column " + name);
        }
        return values[index];
    }

    private int integer(String[] values, Map<String, Integer> columns, String name) {
        return Integer.parseInt(value(values, columns, name));
    }

    private ManifestVerification verifyB2Manifest(Path b2Report) throws IOException {
        List<String> lines = Files.readAllLines(b2Report.resolve(B2_SHA),
                StandardCharsets.UTF_8);
        boolean exact = lines.size() == 16;
        for (String line : lines) {
            String[] fields = line.split("  ", 2);
            exact &= fields.length == 2
                    && Files.isRegularFile(b2Report.resolve(fields[1]))
                    && fields[0].equals(sha(b2Report.resolve(fields[1])));
        }
        return new ManifestVerification(lines.size(), exact);
    }

    private void requireB2Ready(
            JsonNode contract,
            JsonNode review,
            JsonNode integrity,
            ManifestVerification manifest
    ) {
        if (!manifest.exact()
                || !"CALIBRATION_EVIDENCE_READY_FOR_REVIEW".equals(
                        review.path("status").asText())
                || !review.path("calibrationExecuted").asBoolean()
                || review.path("holdoutExecuted").asBoolean()
                || review.path("calibrationMatchExecutionCount").asInt() != 12_000
                || review.path("holdoutMatchExecutionCount").asInt() != 0
                || !review.path("determinismReplayExact").asBoolean()
                || !review.path("checkpointPayloadDigestExact").asBoolean()
                || review.path("distinctFreshJvmCount").asInt() != 4
                || integrity.path("totalIntegrityErrorCount").asLong() != 0
                || contract.path("holdoutMatchExecutionCount").asInt() != 0) {
            throw new IllegalStateException("B2 evidence did not pass the frozen B3 review gate");
        }
    }

    private void requireUnusedHoldout(Path output) throws IOException {
        for (String name : List.of(
                Phase13GB3CheckpointStore.CHECKPOINT_DIRECTORY_NAME,
                Phase13GB3CheckpointStore.RECEIPT_DIRECTORY_NAME,
                "phase13g-b3-matches.jsonl",
                "phase13g-b3-final-review.json")) {
            Path path = output.resolve(name);
            if (Files.isRegularFile(path)
                    || (Files.isDirectory(path) && hasAnyFile(path))) {
                throw new IllegalStateException(
                        "B3 contract cannot be frozen after holdout execution began");
            }
        }
        Path authorization = output.resolve(
                Phase13GB3CheckpointStore.AUTHORIZATION_DIRECTORY_NAME);
        if (Files.isDirectory(authorization)) {
            try (var files = Files.list(authorization)) {
                if (files.anyMatch(path -> path.getFileName().toString().endsWith(".started"))) {
                    throw new IllegalStateException("B3 holdout was already opened");
                }
            }
        }
    }

    private boolean hasAnyFile(Path directory) throws IOException {
        try (var files = Files.walk(directory)) {
            return files.anyMatch(Files::isRegularFile);
        }
    }

    private void writeAuthorizations(Path output, String contractHash) throws IOException {
        Path directory = output.resolve(
                Phase13GB3CheckpointStore.AUTHORIZATION_DIRECTORY_NAME);
        Files.createDirectories(directory);
        String content = "schema=PHASE_13G_B3_ONE_TIME_SHARD_AUTHORIZATION_V1\n"
                + "frozenContractHash=" + contractHash + "\n"
                + "calibrationMatchExecutionCount=0\n"
                + "holdoutMatchExecutionCountAtFreeze=0\n";
        for (int shard = 0; shard < 4; shard++) {
            writeOrRequireExact(
                    directory.resolve("shard-" + shard + ".authorized"),
                    content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeOrRequireExact(Path path, byte[] bytes) throws IOException {
        if (Files.isRegularFile(path)) {
            if (!java.util.Arrays.equals(Files.readAllBytes(path), bytes)) {
                throw new IllegalStateException("Frozen B3 bytes already exist and differ: "
                        + path.getFileName());
            }
            return;
        }
        Files.write(path, bytes);
    }

    private byte[] canonicalBytesWithNewline(Object value) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(value);
        byte[] result = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        result[bytes.length] = '\n';
        return result;
    }

    private String canonicalHash(Object value) {
        try {
            return Phase13GB3CheckpointStore.sha256(mapper.writeValueAsBytes(value));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String sha(Path path) throws IOException {
        return Phase13GB3CheckpointStore.sha256(Files.readAllBytes(path));
    }

    private double sampleStandardDeviation(List<Double> values, double mean) {
        if (values.size() < 2) return 0.0;
        double sum = values.stream().mapToDouble(value -> {
            double delta = value - mean;
            return delta * delta;
        }).sum();
        return Math.sqrt(sum / (values.size() - 1));
    }

    private double outwardLower(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.FLOOR).doubleValue();
    }

    private double outwardUpper(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.CEILING).doubleValue();
    }

    private enum Metric {
        WINNER_FLIP_RATE,
        DURATION_SECONDS,
        BLUE_GOLD_EDGE,
        BLUE_JUNGLE_CS,
        RED_JUNGLE_CS,
        BLUE_JUNGLE_EXPERIENCE,
        RED_JUNGLE_EXPERIENCE,
        JUNGLE_GANK_ATTEMPTS,
        COUNTER_GANK_ATTEMPTS,
        JUNGLE_CS_SIDE_GAP,
        JUNGLE_EXPERIENCE_SIDE_GAP;

        double value(MarginalSample value) {
            return switch (this) {
                case WINNER_FLIP_RATE -> value.winnerFlipped() ? 1.0 : 0.0;
                case DURATION_SECONDS -> value.durationDelta();
                case BLUE_GOLD_EDGE -> value.blueGoldEdgeDelta();
                case BLUE_JUNGLE_CS -> value.blueJungleCsDelta();
                case RED_JUNGLE_CS -> value.redJungleCsDelta();
                case BLUE_JUNGLE_EXPERIENCE -> value.blueJungleExperienceDelta();
                case RED_JUNGLE_EXPERIENCE -> value.redJungleExperienceDelta();
                case JUNGLE_GANK_ATTEMPTS -> value.jungleGankAttemptsDelta();
                case COUNTER_GANK_ATTEMPTS -> value.counterGankAttemptsDelta();
                case JUNGLE_CS_SIDE_GAP ->
                        value.blueJungleCsDelta() - value.redJungleCsDelta();
                case JUNGLE_EXPERIENCE_SIDE_GAP ->
                        value.blueJungleExperienceDelta()
                                - value.redJungleExperienceDelta();
            };
        }
    }

    private record MarginalSample(
            String comparisonId,
            String fixtureLane,
            String fromWinner,
            String toWinner,
            boolean winnerFlipped,
            int durationDelta,
            int blueGoldEdgeDelta,
            int blueJungleCsDelta,
            int redJungleCsDelta,
            int blueJungleExperienceDelta,
            int redJungleExperienceDelta,
            int jungleGankAttemptsDelta,
            int counterGankAttemptsDelta
    ) {
    }

    private record LanePopulation(String lane, int calibrationCount, int holdoutCount) {
    }

    private record ManifestVerification(int entryCount, boolean exact) {
    }

    public record FreezeResult(
            Path contractPath,
            String frozenContractHash,
            String candidateFreezeIdentityHash,
            String acceptanceGateIdentityHash,
            int numericGateCount,
            int exactBehaviorGateCount,
            int holdoutExecutionCountAtFreeze
    ) {
    }
}
