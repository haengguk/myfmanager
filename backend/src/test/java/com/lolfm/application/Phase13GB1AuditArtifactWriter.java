package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.application.Phase13GB1RealMatchHarness.AuditMatchRun;
import com.lolfm.application.Phase13GB1RealMatchHarness.PreparedFixture;
import com.lolfm.simulator.ResolvedSimulationRuntimeProfile;
import com.lolfm.simulator.Phase13GB1SimulationExecutor;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Writes deterministic B1 contract and bounded dry-run evidence; generated files are not baselines. */
public final class Phase13GB1AuditArtifactWriter {
    public static final String REPORT_SCHEMA = "PHASE_13G_B1_AUDIT_CONTRACT_REPORT_V2";
    public static final String SOURCE_TREE_HASH_ALGORITHM =
            "SHA256_UTF8_SORTED_RELATIVE_PATH_PIPE_RAW_FILE_SHA256_LINES_V1";
    public static final String SUMMARY_FILE = "phase13g-b1-audit-contract.json";
    public static final String PROFILE_FILE = "phase13g-b1-profile-contract.json";
    public static final String SCHEDULE_JSON_FILE = "phase13g-b1-schedule.json";
    public static final String SCHEDULE_CSV_FILE = "phase13g-b1-schedule.csv";
    public static final String DRY_RUN_JSON_FILE = "phase13g-b1-dry-run-provenance.json";
    public static final String DRY_RUN_CSV_FILE = "phase13g-b1-dry-run-matches.csv";
    public static final String SHA_FILE = "SHA256SUMS.txt";
    private static final List<String> HASHED_FILES = List.of(
            SUMMARY_FILE,
            PROFILE_FILE,
            SCHEDULE_JSON_FILE,
            SCHEDULE_CSV_FILE,
            DRY_RUN_JSON_FILE,
            DRY_RUN_CSV_FILE);

    private Phase13GB1AuditArtifactWriter() {
    }

    public static ArtifactSet write(
            ObjectMapper sourceMapper,
            Path backendRoot,
            Path output,
            Phase13GB1AuditSchedule.Schedule schedule,
            PreparedFixture prepared,
            List<AuditMatchRun> runs,
            AuditMatchRun deterministicReplay,
            SimulationResourceProvenance resources
    ) throws IOException {
        schedule = Phase13GB1AuditSchedule.requireFrozen(schedule);
        Objects.requireNonNull(prepared, "prepared");
        runs = List.copyOf(runs);
        validateDryRun(prepared, runs, deterministicReplay, resources);
        Files.createDirectories(output);
        ObjectMapper mapper = canonicalMapper(sourceMapper);
        SourceTreeIdentity productionSource = productionSourceTree(backendRoot);
        SourceTreeIdentity auditHarnessSource = phaseTestSourceTree(
                backendRoot, "Phase13GB1");
        List<ProfileContract> profiles = Phase13GB1RealMatchHarness.AUDIT_PROFILES.stream()
                .map(SimulationRuntimeProfiles::resolve)
                .map(Phase13GB1AuditArtifactWriter::profileContract)
                .toList();
        boolean replayExact = exactReplay(runs.getFirst(), deterministicReplay);
        ResourceSnapshot resourceSnapshot = ResourceSnapshot.from(resources);

        writeJson(mapper, output.resolve(SUMMARY_FILE), summary(
                schedule,
                prepared,
                runs,
                replayExact,
                profiles,
                resourceSnapshot,
                productionSource,
                auditHarnessSource));
        writeJson(mapper, output.resolve(PROFILE_FILE), new ProfileContractDocument(
                "PHASE_13G_B_FIVE_PROFILE_CONTRACT_V1",
                profiles.size(),
                profiles));
        writeJson(mapper, output.resolve(SCHEDULE_JSON_FILE), schedule);
        Files.writeString(output.resolve(SCHEDULE_CSV_FILE), scheduleCsv(schedule),
                StandardCharsets.UTF_8);
        writeJson(mapper, output.resolve(DRY_RUN_JSON_FILE), new DryRunDocument(
                "PHASE_13G_B1_DRY_RUN_EVIDENCE_V2",
                prepared.fixture(),
                Phase13GB1AuditSchedule.dryRunSeed(prepared.fixture()),
                prepared.productionOrchestrationCount(),
                prepared.reusePolicy(),
                resourceSnapshot,
                replayExact,
                runs));
        Files.writeString(output.resolve(DRY_RUN_CSV_FILE), dryRunCsv(runs),
                StandardCharsets.UTF_8);
        writeShaManifest(output);
        return new ArtifactSet(
                output,
                productionSource,
                auditHarnessSource,
                replayExact,
                sha256(Files.readAllBytes(output.resolve(SUMMARY_FILE))),
                sha256(Files.readAllBytes(output.resolve(SHA_FILE))));
    }

    private static void validateDryRun(
            PreparedFixture prepared,
            List<AuditMatchRun> runs,
            AuditMatchRun replay,
            SimulationResourceProvenance resources
    ) {
        Objects.requireNonNull(replay, "deterministicReplay");
        Objects.requireNonNull(resources, "resources");
        if (runs.size() != Phase13GB1RealMatchHarness.AUDIT_PROFILES.size()) {
            throw new IllegalArgumentException("B1 dry-run requires exactly five paired profile runs");
        }
        Set<SimulationRuntimeProfileId> expected = Set.copyOf(
                Phase13GB1RealMatchHarness.AUDIT_PROFILES);
        Set<SimulationRuntimeProfileId> actual = runs.stream()
                .map(AuditMatchRun::profileId).collect(java.util.stream.Collectors.toSet());
        List<SimulationRuntimeProfileId> actualOrder = runs.stream()
                .map(AuditMatchRun::profileId).toList();
        long dryRunSeed = Phase13GB1AuditSchedule.dryRunSeed(prepared.fixture());
        if (!actual.equals(expected)
                || !actualOrder.equals(Phase13GB1RealMatchHarness.AUDIT_PROFILES)
                || runs.stream().anyMatch(value -> value.sampleLane()
                        != Phase13GB1AuditSchedule.SampleLane.DRY_RUN)
                || runs.stream().anyMatch(value -> value.seed() != dryRunSeed)
                || runs.stream().map(AuditMatchRun::fixtureId).distinct().count() != 1
                || runs.stream().map(AuditMatchRun::draftDecisionHash).distinct().count() != 1
                || runs.stream().map(AuditMatchRun::finalDraftHash).distinct().count() != 1
                || runs.stream().map(AuditMatchRun::finalAssignmentHash).distinct().count() != 1
                || runs.stream().map(AuditMatchRun::resourceProvenanceHash).distinct().count() != 1
                || runs.stream().map(AuditMatchRun::rosterIdentityHash).distinct().count() != 1
                || runs.stream().map(AuditMatchRun::seriesHistoryBeforeHash).distinct().count() != 1
                || runs.stream().map(AuditMatchRun::engineImplementationVersion)
                        .distinct().count() != 1
                || !runs.getFirst().engineImplementationVersion()
                        .equals(SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION)
                || runs.stream().anyMatch(value -> !value.integrityDiagnostics().clean())
                || !runs.getFirst().resourceProvenanceHash()
                        .equals(resources.resourceProvenanceHash())
                || runs.stream().anyMatch(value -> {
                    ResolvedSimulationRuntimeProfile registered =
                            SimulationRuntimeProfiles.resolve(value.profileId());
                    return !registered.configurationHash().equals(value.configurationHash())
                            || !registered.gameplayConfiguration().equals(
                                    value.resolvedGameplayConfiguration())
                            || !registered.activeGameplayRulesVersion().equals(
                                    value.activeGameplayRulesVersion());
                })
                || !runs.getFirst().fixtureId().equals(prepared.fixture().fixtureId())) {
            throw new IllegalArgumentException("B1 dry-run evidence violates paired fixture contract");
        }
        if (!exactReplay(runs.getFirst(), replay)) {
            throw new IllegalArgumentException("B1 dry-run deterministic replay differs");
        }
    }

    private static Map<String, Object> summary(
            Phase13GB1AuditSchedule.Schedule schedule,
            PreparedFixture prepared,
            List<AuditMatchRun> runs,
            boolean replayExact,
            List<ProfileContract> profiles,
            ResourceSnapshot resources,
            SourceTreeIdentity productionSource,
            SourceTreeIdentity auditHarnessSource
    ) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", REPORT_SCHEMA);
        result.put("phase", "PHASE_13G_B1_AUDIT_CONTRACT_AND_REAL_MATCH_HARNESS");
        result.put("status", "HARNESS_HARDENED_READY_FOR_CALIBRATION");
        result.put("scheduleVersion", schedule.scheduleVersion());
        result.put("scheduleHash", schedule.scheduleHash());
        result.put("scheduleHashAlgorithm", schedule.scheduleHashAlgorithm());
        result.put("teamCount", schedule.teamCodes().size());
        result.put("primaryGameOneFixtureCount", schedule.primaryFixtures().size());
        result.put("secondaryHardFearlessGameTwoFixtureCount",
                schedule.secondaryHardFearlessFixtures().size());
        result.put("calibrationSeedsPerFixture", schedule.calibrationSeedsPerFixture());
        result.put("holdoutSeedsPerFixture", schedule.holdoutSeedsPerFixture());
        result.put("calibrationExecuted", false);
        result.put("calibrationMatchExecutionCount", 0);
        result.put("holdoutExecuted", false);
        result.put("holdoutMatchExecutionCount", 0);
        result.put("productionDecision", "NOT_EVALUATED");
        result.put("productionTuningChanged", false);
        result.put("fixturePreparationPolicy", prepared.reusePolicy());
        result.put("fixturePreparationOrchestrationCount",
                prepared.productionOrchestrationCount());
        result.put("fixturePreparationMatchesExcludedFromAuditSample", true);
        result.put("pairedDryRunFixtureId", prepared.fixture().fixtureId());
        result.put("pairedDryRunMatchExecutionCount", runs.size());
        result.put("determinismReplayExecutionCount", 1);
        result.put("sameSeedReplayExact", replayExact);
        result.put("fullStructuredDiagnosticsReplayExact", replayExact);
        result.put("structuredDiagnosticsReplayScope",
                "ALL_SIMULATION_RESULT_DIAGNOSTIC_SNAPSHOTS_AND_HISTORIES_"
                        + "WITH_RANDOM_TRACE_COVERED_BY_FINGERPRINT");
        result.put("structuredDiagnosticsHashAlgorithm",
                Phase13GB1SimulationExecutor.STRUCTURED_DIAGNOSTICS_HASH_ALGORITHM);
        result.put("engineImplementationVersion", runs.getFirst().engineImplementationVersion());
        result.put("hashContracts", new HashContracts(
                SimulationRuntimeProfiles.CONFIGURATION_HASH_ALGORITHM,
                "GAMEPLAY_CONFIGURATION_ONLY_INSTRUMENTATION_EXCLUDED",
                SimulationProvenanceService.ORDERED_LINES_HASH_ALGORITHM,
                "ENGINE_RULES_CONFIGURATION_RESOURCES_ROSTER_SERIES_DRAFT_ASSIGNMENT_AND_SEED",
                SimulationProvenanceService.TIMELINE_HASH_ALGORITHM,
                "COMPLETE_TIMELINE_OUTPUT",
                SimulationRandomFingerprint.TRACE_HASH_ALGORITHM,
                "OBSERVATIONAL_OUTPUT_NOT_REPLAY_INPUT",
                Phase13GB1SimulationExecutor.STRUCTURED_DIAGNOSTICS_HASH_ALGORITHM,
                "ALL_SIMULATION_RESULT_DIAGNOSTIC_SNAPSHOTS_AND_HISTORIES",
                "ALL_SIMULATION_RESULT_DIAGNOSTICS_EXACT_EQUALITY"));
        result.put("fixedDraftDecisionHash", runs.getFirst().draftDecisionHash());
        result.put("fixedFinalDraftHash", runs.getFirst().finalDraftHash());
        result.put("fixedFinalAssignmentHash", runs.getFirst().finalAssignmentHash());
        result.put("profileCount", profiles.size());
        result.put("profiles", profiles);
        result.put("resourceProvenance", resources);
        result.put("productionSourceTree", productionSource);
        result.put("auditHarnessSourceTree", auditHarnessSource);
        result.put("scopeExclusions", List.of(
                "NO_24_SEED_CALIBRATION",
                "NO_HOLDOUT_EXECUTION",
                "NO_BALANCE_TUNING",
                "NO_PRODUCTION_V1_DECISION",
                "NO_GAMEPLAY_OR_API_OR_FRONTEND_CHANGE"));
        result.put("nextStep", "PHASE_13G_B2_CALIBRATION_ON_RESERVED_CALIBRATION_LANE");
        return result;
    }

    private static ProfileContract profileContract(ResolvedSimulationRuntimeProfile profile) {
        return new ProfileContract(
                profile.profileId(),
                profile.gameplayConfiguration(),
                profile.configurationHash(),
                SimulationRuntimeProfiles.CONFIGURATION_HASH_ALGORITHM,
                profile.activeGameplayRulesVersion());
    }

    private static boolean exactReplay(AuditMatchRun first, AuditMatchRun replay) {
        return first.fixtureId().equals(replay.fixtureId())
                && first.profileId() == replay.profileId()
                && first.seed() == replay.seed()
                && first.configurationHash().equals(replay.configurationHash())
                && first.replayProvenanceHash().equals(replay.replayProvenanceHash())
                && first.timelineHash().equals(replay.timelineHash())
                && first.randomFingerprint().equals(replay.randomFingerprint())
                && first.structuredDiagnosticsHash().equals(
                        replay.structuredDiagnosticsHash())
                && first.winnerSide() == replay.winnerSide()
                && first.endReason() == replay.endReason()
                && first.durationSeconds() == replay.durationSeconds()
                && first.structuredDiagnostics().equals(replay.structuredDiagnostics())
                && first.integrityDiagnostics().equals(replay.integrityDiagnostics());
    }

    private static String scheduleCsv(Phase13GB1AuditSchedule.Schedule schedule) {
        StringBuilder result = new StringBuilder(
                "fixtureId,fixtureLane,pairId,blueTeamCode,redTeamCode,seriesGameNumber,sampleLane,replicate,seed,reservedNotExecuted\n");
        for (var fixture : schedule.allFixtures()) {
            appendScheduleSeeds(result, fixture,
                    Phase13GB1AuditSchedule.SampleLane.CALIBRATION,
                    fixture.calibrationSeeds());
            appendScheduleSeeds(result, fixture,
                    Phase13GB1AuditSchedule.SampleLane.HOLDOUT,
                    fixture.holdoutSeeds());
        }
        return result.toString();
    }

    private static void appendScheduleSeeds(
            StringBuilder result,
            Phase13GB1AuditSchedule.Fixture fixture,
            Phase13GB1AuditSchedule.SampleLane sampleLane,
            List<Long> seeds
    ) {
        for (int index = 0; index < seeds.size(); index++) {
            result.append(csv(fixture.fixtureId())).append(',')
                    .append(fixture.fixtureLane()).append(',')
                    .append(csv(fixture.pairId())).append(',')
                    .append(fixture.blueTeamCode()).append(',')
                    .append(fixture.redTeamCode()).append(',')
                    .append(fixture.seriesGameNumber()).append(',')
                    .append(sampleLane).append(',')
                    .append(index).append(',')
                    .append(seeds.get(index)).append(',')
                    .append(true).append('\n');
        }
    }

    private static String dryRunCsv(List<AuditMatchRun> runs) {
        StringBuilder result = new StringBuilder(
                "fixtureId,sampleLane,blueTeamCode,redTeamCode,seriesGameNumber,seed,profileId,configurationHash,activeGameplayRulesVersion,replayProvenanceHash,timelineHash,structuredDiagnosticsHash,randomDrawCount,randomTraceHash,winnerSide,endReason,durationSeconds,blueKills,redKills,blueGold,redGold,blueDragons,redDragons,blueTowers,redTowers,blueJunglePlayerId,blueJungleChampionId,blueJungleCs,blueJungleGold,blueJungleXp,blueJungleLevel,redJunglePlayerId,redJungleChampionId,redJungleCs,redJungleGold,redJungleXp,redJungleLevel,jungleGankEvaluations,jungleGankTriggerSuccesses,jungleGankAttempts,counterGankAttempts,laneCombatAttempts,jungleEconomyEvaluations,jungleEconomyEligibleOutcomes,jungleEconomyAwardedCs,jungleEconomyAwardedGold,jungleEconomyAwardedXp,tempoEconomyUpdates,tempoCreditAddedSeconds,tempoGankConsumptions,tempoCounterGankConsumptions,integrityErrorCount,integrityClean\n");
        for (AuditMatchRun run : runs) {
            result.append(csv(run.fixtureId())).append(',')
                    .append(run.sampleLane()).append(',')
                    .append(run.blueTeamCode()).append(',')
                    .append(run.redTeamCode()).append(',')
                    .append(run.seriesGameNumber()).append(',')
                    .append(run.seed()).append(',')
                    .append(run.profileId()).append(',')
                    .append(run.configurationHash()).append(',')
                    .append(run.activeGameplayRulesVersion()).append(',')
                    .append(run.replayProvenanceHash()).append(',')
                    .append(run.timelineHash()).append(',')
                    .append(run.structuredDiagnosticsHash()).append(',')
                    .append(run.randomFingerprint().randomDrawCount()).append(',')
                    .append(run.randomFingerprint().randomTraceHash()).append(',')
                    .append(run.winnerSide()).append(',')
                    .append(run.endReason()).append(',')
                    .append(run.durationSeconds()).append(',')
                    .append(run.blueKills()).append(',')
                    .append(run.redKills()).append(',')
                    .append(run.blueGold()).append(',')
                    .append(run.redGold()).append(',')
                    .append(run.blueDragons()).append(',')
                    .append(run.redDragons()).append(',')
                    .append(run.blueTowers()).append(',')
                    .append(run.redTowers()).append(',')
                    .append(run.blueJungle().playerId()).append(',')
                    .append(run.blueJungle().championId()).append(',')
                    .append(run.blueJungle().cs()).append(',')
                    .append(run.blueJungle().gold()).append(',')
                    .append(run.blueJungle().totalExperience()).append(',')
                    .append(run.blueJungle().level()).append(',')
                    .append(run.redJungle().playerId()).append(',')
                    .append(run.redJungle().championId()).append(',')
                    .append(run.redJungle().cs()).append(',')
                    .append(run.redJungle().gold()).append(',')
                    .append(run.redJungle().totalExperience()).append(',')
                    .append(run.redJungle().level()).append(',')
                    .append(run.combatDiagnostics().jungleGankEvaluations()).append(',')
                    .append(run.combatDiagnostics().jungleGankTriggerSuccesses()).append(',')
                    .append(run.combatDiagnostics().jungleGankAttempts()).append(',')
                    .append(run.combatDiagnostics().counterGankAttempts()).append(',')
                    .append(run.combatDiagnostics().laneCombatAttempts()).append(',')
                    .append(run.jungleEconomyDiagnostics().evaluations()).append(',')
                    .append(run.jungleEconomyDiagnostics().eligibleOutcomes()).append(',')
                    .append(run.jungleEconomyDiagnostics().awardedCs()).append(',')
                    .append(run.jungleEconomyDiagnostics().awardedGold()).append(',')
                    .append(run.jungleEconomyDiagnostics().awardedExperience()).append(',')
                    .append(run.jungleTempoDiagnostics().economyUpdates()).append(',')
                    .append(Double.toHexString(
                            run.jungleTempoDiagnostics().totalCreditAddedSeconds())).append(',')
                    .append(run.jungleTempoDiagnostics().actualConsumptions().getOrDefault(
                            com.lolfm.simulator.JungleTempoActionType.GANK, 0)).append(',')
                    .append(run.jungleTempoDiagnostics().actualConsumptions().getOrDefault(
                            com.lolfm.simulator.JungleTempoActionType.COUNTER_GANK, 0)).append(',')
                    .append(run.integrityDiagnostics().errorCount()).append(',')
                    .append(run.integrityDiagnostics().clean()).append('\n');
        }
        return result.toString();
    }

    static SourceTreeIdentity productionSourceTree(Path backendRoot) throws IOException {
        return sourceTreeIdentity(
                backendRoot,
                List.of(Path.of("src", "main", "java"),
                        Path.of("src", "main", "resources")),
                List.of(Path.of("settings.gradle")),
                value -> true,
                Map.of("build.gradle", productionBuildContract(backendRoot)));
    }

    static SourceTreeIdentity phaseTestSourceTree(
            Path backendRoot,
            String fileNamePrefix
    ) throws IOException {
        Objects.requireNonNull(fileNamePrefix, "fileNamePrefix");
        List<String> sourcePrefixes;
        List<String> buildPhases;
        if (fileNamePrefix.equals("Phase13GB3")) {
            sourcePrefixes = List.of("Phase13GB3");
            buildPhases = List.of("B3");
        } else if (fileNamePrefix.equals("Phase13GB")) {
            sourcePrefixes = List.of("Phase13GB1", "Phase13GB2");
            buildPhases = List.of("B1", "B2");
        } else if (fileNamePrefix.equals("Phase13GB1")) {
            sourcePrefixes = List.of("Phase13GB1");
            buildPhases = List.of("B1");
        } else if (fileNamePrefix.equals("Phase13GB2")) {
            sourcePrefixes = List.of("Phase13GB2");
            buildPhases = List.of("B2");
        } else {
            sourcePrefixes = List.of(fileNamePrefix);
            buildPhases = List.of();
        }
        LinkedHashMap<String, byte[]> virtualFiles = new LinkedHashMap<>();
        for (String phase : buildPhases) {
            virtualFiles.put("build.gradle#PHASE_13G_" + phase + "_BUILD_CONTRACT",
                    phaseBuildContract(backendRoot, phase));
        }
        return sourceTreeIdentity(
                backendRoot,
                List.of(Path.of("src", "test", "java")),
                List.of(),
                value -> sourcePrefixes.stream().anyMatch(prefix ->
                        value.getFileName().toString().startsWith(prefix)),
                virtualFiles);
    }

    private static SourceTreeIdentity sourceTreeIdentity(
            Path backendRoot,
            List<Path> roots,
            List<Path> explicitFiles,
            java.util.function.Predicate<Path> filter
    ) throws IOException {
        return sourceTreeIdentity(backendRoot, roots, explicitFiles, filter, Map.of());
    }

    private static SourceTreeIdentity sourceTreeIdentity(
            Path backendRoot,
            List<Path> roots,
            List<Path> explicitFiles,
            java.util.function.Predicate<Path> filter,
            Map<String, byte[]> virtualFiles
    ) throws IOException {
        Path normalizedRoot = backendRoot.toAbsolutePath().normalize();
        ArrayList<Path> files = new ArrayList<>();
        for (Path relativeRoot : roots) {
            Path root = normalizedRoot.resolve(relativeRoot);
            if (!Files.exists(root)) continue;
            try (var values = Files.walk(root)) {
                values.filter(Files::isRegularFile)
                        .filter(filter)
                        .forEach(files::add);
            }
        }
        for (Path explicit : explicitFiles) {
            Path path = normalizedRoot.resolve(explicit);
            if (Files.isRegularFile(path)) files.add(path);
        }
        files = new ArrayList<>(files.stream().distinct()
                .sorted(Comparator.comparing(value -> portablePath(normalizedRoot.relativize(value))))
                .toList());
        java.util.TreeMap<String, byte[]> inputs = new java.util.TreeMap<>();
        for (Path file : files) {
            inputs.put(portablePath(normalizedRoot.relativize(file)), Files.readAllBytes(file));
        }
        inputs.putAll(virtualFiles);
        StringBuilder canonical = new StringBuilder();
        inputs.forEach((path, bytes) -> canonical.append(path).append('|')
                .append(sha256(bytes)).append('\n'));
        return new SourceTreeIdentity(
                "SHA256_UTF8_SORTED_LOGICAL_PATH_PIPE_RAW_OR_NORMALIZED_FILE_SHA256_LINES_V2",
                sha256(canonical.toString().getBytes(StandardCharsets.UTF_8)),
                inputs.size());
    }

    private static byte[] productionBuildContract(Path backendRoot) throws IOException {
        String build = Files.readString(backendRoot.resolve("build.gradle"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        for (String phase : List.of("B1", "B2", "B3")) {
            build = removeMarkedSection(build, phase);
        }
        return build.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] phaseBuildContract(Path backendRoot, String phase)
            throws IOException {
        String build = Files.readString(backendRoot.resolve("build.gradle"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        String start = "// PHASE_13G_" + phase + "_BUILD_CONTRACT_START";
        String end = "// PHASE_13G_" + phase + "_BUILD_CONTRACT_END";
        int from = build.indexOf(start);
        int to = build.indexOf(end);
        if (from < 0 || to < from || build.indexOf(start, from + 1) >= 0
                || build.indexOf(end, to + 1) >= 0) {
            throw new IllegalStateException("Missing or duplicate " + phase
                    + " Gradle build contract markers");
        }
        return (build.substring(from, to + end.length()) + '\n')
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String removeMarkedSection(String build, String phase) {
        String start = "// PHASE_13G_" + phase + "_BUILD_CONTRACT_START";
        String end = "// PHASE_13G_" + phase + "_BUILD_CONTRACT_END";
        int from = build.indexOf(start);
        if (from < 0) return build;
        int to = build.indexOf(end, from);
        if (to < 0 || build.indexOf(start, from + 1) >= 0
                || build.indexOf(end, to + 1) >= 0) {
            throw new IllegalStateException("Invalid " + phase
                    + " Gradle build contract markers");
        }
        int after = to + end.length();
        if (after < build.length() && build.charAt(after) == '\n') after++;
        return build.substring(0, from) + build.substring(after);
    }

    private static ObjectMapper canonicalMapper(ObjectMapper source) {
        return Objects.requireNonNull(source, "sourceMapper").copy()
                .findAndRegisterModules()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    private static void writeJson(ObjectMapper mapper, Path output, Object value)
            throws IOException {
        byte[] json = mapper.writeValueAsBytes(value);
        byte[] withNewline = java.util.Arrays.copyOf(json, json.length + 1);
        withNewline[json.length] = '\n';
        Files.write(output, withNewline);
    }

    private static void writeShaManifest(Path output) throws IOException {
        StringBuilder manifest = new StringBuilder();
        for (String file : HASHED_FILES.stream().sorted().toList()) {
            manifest.append(sha256(Files.readAllBytes(output.resolve(file))))
                    .append("  ").append(file).append('\n');
        }
        Files.writeString(output.resolve(SHA_FILE), manifest, StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        String normalized = Objects.toString(value, "");
        if (normalized.indexOf(',') >= 0 || normalized.indexOf('"') >= 0
                || normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            return '"' + normalized.replace("\"", "\"\"") + '"';
        }
        return normalized;
    }

    private static String portablePath(Path value) {
        return value.normalize().toString().replace('\\', '/');
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public record ProfileContract(
            SimulationRuntimeProfileId profileId,
            com.lolfm.simulator.SimulationGameplayConfiguration resolvedGameplayConfiguration,
            String configurationHash,
            String configurationHashAlgorithm,
            String activeGameplayRulesVersion
    ) {
    }

    public record ProfileContractDocument(
            String schemaVersion,
            int profileCount,
            List<ProfileContract> profiles
    ) {
        public ProfileContractDocument {
            profiles = List.copyOf(profiles);
        }
    }

    public record SourceTreeIdentity(
            String hashAlgorithm,
            String hash,
            int fileCount
    ) {
    }

    public record HashContracts(
            String configurationHashAlgorithm,
            String configurationHashScope,
            String replayProvenanceHashAlgorithm,
            String replayProvenanceHashScope,
            String timelineHashAlgorithm,
            String timelineHashScope,
            String randomTraceHashAlgorithm,
            String randomTraceHashScope,
            String structuredDiagnosticsHashAlgorithm,
            String structuredDiagnosticsHashScope,
            String structuredDiagnosticsEqualityScope
    ) {
    }

    public record ResourceEntry(String role, String path, String version, String sha256) {
    }

    public record ResourceSnapshot(
            String schemaVersion,
            String resourceProvenanceHash,
            String compositionProfileHash,
            String draftLegalRoleKeyHash,
            int jungleClearGameplayEnabledProfileCount,
            List<ResourceEntry> resources
    ) {
        public ResourceSnapshot {
            resources = List.copyOf(resources);
        }

        static ResourceSnapshot from(SimulationResourceProvenance value) {
            return new ResourceSnapshot(
                    value.schemaVersion(),
                    value.resourceProvenanceHash(),
                    value.compositionProfileHash(),
                    value.draftLegalRoleKeyHash(),
                    value.jungleClearGameplayEnabledProfileCount(),
                    value.resources().stream().map(resource -> new ResourceEntry(
                            resource.role(), resource.classpathResource(),
                            resource.version(), resource.sha256()))
                            .toList());
        }
    }

    public record DryRunDocument(
            String schemaVersion,
            Phase13GB1AuditSchedule.Fixture fixture,
            long dryRunSeed,
            int fixturePreparationOrchestrationCount,
            String fixtureReusePolicy,
            ResourceSnapshot resourceProvenance,
            boolean sameSeedReplayExact,
            List<AuditMatchRun> runs
    ) {
        public DryRunDocument {
            runs = List.copyOf(runs);
        }
    }

    public record ArtifactSet(
            Path outputDirectory,
            SourceTreeIdentity productionSourceTree,
            SourceTreeIdentity auditHarnessSourceTree,
            boolean sameSeedReplayExact,
            String summarySha256,
            String shaManifestSha256
    ) {
    }
}
