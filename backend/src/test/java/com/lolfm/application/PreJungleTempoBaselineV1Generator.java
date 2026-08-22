package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.LolfmApplication;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.simulator.JungleClearContribution;
import com.lolfm.simulator.ResolvedSimulationRuntimeProfile;
import com.lolfm.simulator.SimulationGameplayConfiguration;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * One-time pre-production generator for the four-profile V1-A runtime oracle.
 * This artifact must exist before Jungle Tempo production code is introduced.
 */
public final class PreJungleTempoBaselineV1Generator {
    static final String BASELINE_ID = "PRE_JUNGLE_TEMPO_RUNTIME_BASELINE_V1";
    static final String DOCUMENT_SCHEMA = "PRE_JUNGLE_TEMPO_RUNTIME_BASELINE_DOCUMENT_V1";
    static final String PREDECESSOR_BASELINE_ID = "PRE_JUNGLE_RUNTIME_BASELINE_V2";
    static final String PREDECESSOR_ARTIFACT_SHA256 =
            "0bce126117683e47ace908c348dbe2448f21592dc5009bd9f4514bb566fadb8e";
    static final String EXPECTED_PRODUCTION_SOURCE_TREE_HASH =
            "674b3148d782e98753bfd9e79f8b5f78b12cc3ecea8a85bc34ff8fbcaa2def0d";
    static final int EXPECTED_PRODUCTION_SOURCE_FILE_COUNT = 464;
    private static final String SOURCE_TREE_HASH_ALGORITHM =
            "SHA256_UTF8_SORTED_RELATIVE_PATH_PIPE_RAW_FILE_SHA256_LINES_V1";
    private static final List<SimulationRuntimeProfileId> PROFILES = List.of(
            SimulationRuntimeProfileId.BASELINE_V1,
            SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
            SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1,
            SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1);
    private static final int CASES_PER_PROFILE = 3;
    private static final Path SOURCE_OUTPUT =
            Path.of("baseline", "pre-jungle-tempo-runtime-v1");
    private static final Path REPORT_OUTPUT =
            Path.of("build", "reports", "pre-jungle-tempo-runtime-baseline-v1");
    private static final String JSON_FILE = "pre-jungle-tempo-runtime-baseline-v1.json";
    private static final String SUMS_FILE = "SHA256SUMS.txt";

    private PreJungleTempoBaselineV1Generator() {
    }

    public static void main(String[] args) throws Exception {
        String regressionStatus = requiredProperty("lolfm.baseline.fullRegressionStatus");
        if (!"CLEAN_PASS".equals(regressionStatus)) {
            throw new IllegalStateException(
                    "Pre-Jungle-Tempo baseline requires CLEAN_PASS full regression status");
        }
        String sourceRevision = requiredProperty("lolfm.baseline.sourceRevision");
        SourceTreeIdentity sourceTree = sourceTreeIdentity();
        if (!EXPECTED_PRODUCTION_SOURCE_TREE_HASH.equals(sourceTree.hash())
                || EXPECTED_PRODUCTION_SOURCE_FILE_COUNT != sourceTree.fileCount()) {
            throw new IllegalStateException(
                    "Pre-Jungle-Tempo source guard mismatch: expected="
                            + EXPECTED_PRODUCTION_SOURCE_FILE_COUNT + "/"
                            + EXPECTED_PRODUCTION_SOURCE_TREE_HASH + " actual="
                            + sourceTree.fileCount() + "/" + sourceTree.hash());
        }

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                LolfmApplication.class).web(WebApplicationType.NONE).run()) {
            RealDraftMatchOrchestrator orchestrator =
                    context.getBean(RealDraftMatchOrchestrator.class);
            ObjectMapper mapper = context.getBean(ObjectMapper.class).copy()
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

            List<ProfileBaseline> profiles = PROFILES.stream()
                    .map(SimulationRuntimeProfiles::resolve)
                    .map(PreJungleTempoBaselineV1Generator::profileBaseline)
                    .toList();
            ArrayList<MatchBaseline> matches = new ArrayList<>();
            SimulationResourceProvenance commonResources = null;

            for (SimulationRuntimeProfileId profileId : PROFILES) {
                SeriesDraftHistory genT1Series = new SeriesDraftHistory();
                List<RealDraftMatchResult> results = List.of(
                        orchestrator.orchestrate("GEN", "T1", genT1Series, 73L, profileId),
                        orchestrator.orchestrate("GEN", "T1", genT1Series, 74L, profileId),
                        orchestrator.orchestrate(
                                "T1", "GEN", new SeriesDraftHistory(), 73L, profileId));
                String[] caseIds = {
                        "GEN_T1_SERIES_GAME_1",
                        "GEN_T1_SERIES_GAME_2",
                        "T1_GEN_MIRROR_GAME_1"
                };
                for (int index = 0; index < results.size(); index++) {
                    RealDraftMatchResult result = results.get(index);
                    SimulationExecutionProvenance provenance = result.executionProvenance();
                    if (provenance == null || provenance.runtimeProfileId() != profileId) {
                        throw new IllegalStateException("Baseline execution profile mismatch");
                    }
                    if (provenance.resourceProvenance()
                            .jungleClearGameplayEnabledProfileCount() != 51) {
                        throw new IllegalStateException(
                                "Pre-Jungle-Tempo baseline requires 51 active clear profiles");
                    }
                    if (commonResources == null) commonResources = provenance.resourceProvenance();
                    if (!commonResources.equals(provenance.resourceProvenance())) {
                        throw new IllegalStateException(
                                "Resource provenance changed during generation");
                    }
                    writeTimelineCandidate(mapper, profileId, caseIds[index], result.timeline());
                    matches.add(matchBaseline(caseIds[index], result));
                }
            }

            int expectedMatchCount = PROFILES.size() * CASES_PER_PROFILE;
            if (matches.size() != expectedMatchCount) {
                throw new IllegalStateException(
                        "Pre-Jungle-Tempo schedule mismatch: expected=" + expectedMatchCount
                                + " actual=" + matches.size());
            }

            BaselineDocument document = new BaselineDocument(
                    DOCUMENT_SCHEMA, BASELINE_ID, PREDECESSOR_BASELINE_ID,
                    PREDECESSOR_ARTIFACT_SHA256, sourceRevision,
                    SOURCE_TREE_HASH_ALGORITHM, sourceTree.hash(), sourceTree.fileCount(),
                    "REUSED_CLEAN_V1_A_FULL_REGRESSION_THEN_PRE_TEMPO_BASELINE",
                    regressionStatus, SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION,
                    SimulationInstrumentation.enabled(), profiles, commonResources,
                    List.copyOf(matches));
            byte[] bytes = BaselineArtifactJson.write(mapper, document);
            writeOutputs(bytes, SOURCE_OUTPUT, REPORT_OUTPUT);
        }
    }

    static List<SimulationRuntimeProfileId> fixedProfiles() {
        return PROFILES;
    }

    static ProfileBaseline profileBaseline(ResolvedSimulationRuntimeProfile value) {
        JungleClearContribution expectedContribution =
                value.profileId()
                        == SimulationRuntimeProfileId
                        .FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1
                        ? JungleClearContribution.ECONOMY_V1
                        : JungleClearContribution.DISABLED_NOT_INTEGRATED;
        String expectedRules = expectedContribution == JungleClearContribution.ECONOMY_V1
                ? SimulationRuntimeProfiles.JUNGLE_ECONOMY_ACTIVE_GAMEPLAY_RULES_VERSION
                : SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION;
        if (value.gameplayConfiguration().jungleClearContribution() != expectedContribution
                || !expectedRules.equals(value.activeGameplayRulesVersion())) {
            throw new IllegalStateException(
                    "Unexpected pre-tempo profile semantics: " + value.profileId());
        }
        return new ProfileBaseline(value.profileId(), value.gameplayConfiguration(),
                value.configurationHash(), value.activeGameplayRulesVersion());
    }

    private static MatchBaseline matchBaseline(String caseId, RealDraftMatchResult result) {
        SimulationExecutionProvenance provenance = result.executionProvenance();
        return new MatchBaseline(
                caseId, provenance.runtimeProfileId(), provenance.configurationHash(),
                result.blueTeamCode(), result.redTeamCode(), result.matchSeed(),
                result.seriesGameNumber(), provenance.seriesHistoryBeforeHash(),
                provenance.draftDecisionHash(), provenance.finalDraftHash(),
                provenance.finalAssignmentHash(), provenance.replayProvenanceHash(),
                provenance.timelineHash(), provenance.randomFingerprint(),
                result.timeline().getWinner(), result.timeline().getDurationSeconds(),
                result.timeline().getEvents().size(), result.timeline().getSnapshots().size());
    }

    static void writeOutputs(byte[] bytes, Path sourceOutput, Path reportOutput)
            throws IOException {
        String hash = sha256(bytes);
        String sums = hash + "  " + JSON_FILE + "\n";

        Files.createDirectories(reportOutput);
        Files.write(reportOutput.resolve(JSON_FILE), bytes);
        Files.writeString(reportOutput.resolve(SUMS_FILE), sums, StandardCharsets.UTF_8);

        Files.createDirectories(sourceOutput);
        Path sourceJson = sourceOutput.resolve(JSON_FILE);
        Path sourceSums = sourceOutput.resolve(SUMS_FILE);
        if (Files.exists(sourceJson) || Files.exists(sourceSums)) {
            if (!Files.exists(sourceJson) || !Files.exists(sourceSums)
                    || !Arrays.equals(Files.readAllBytes(sourceJson), bytes)
                    || !Files.readString(sourceSums, StandardCharsets.UTF_8).equals(sums)) {
                throw new IllegalStateException(
                        "Immutable Pre-Jungle-Tempo V1 artifact differs; use a new version");
            }
        } else {
            Files.write(sourceJson, bytes, StandardOpenOption.CREATE_NEW);
            Files.writeString(sourceSums, sums, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        }
        System.out.println("PRE_JUNGLE_TEMPO_BASELINE_JSON=" + sourceJson);
        System.out.println("PRE_JUNGLE_TEMPO_BASELINE_SHA256=" + hash);
        System.out.println("PRE_JUNGLE_TEMPO_BASELINE_MATCH_COUNT="
                + (PROFILES.size() * CASES_PER_PROFILE));
    }

    private static void writeTimelineCandidate(
            ObjectMapper mapper,
            SimulationRuntimeProfileId profileId,
            String caseId,
            MatchTimeline timeline
    ) throws IOException {
        Path output = REPORT_OUTPUT.resolve("timeline-candidates");
        Files.createDirectories(output);
        byte[] bytes = mapper.copy().disable(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsBytes(timeline);
        Files.write(output.resolve(profileId.name() + "--" + caseId + ".json"), bytes);
    }

    private static SourceTreeIdentity sourceTreeIdentity() throws IOException {
        ArrayList<Path> files = new ArrayList<>();
        for (Path root : List.of(Path.of("src", "main", "java"),
                Path.of("src", "main", "resources"))) {
            try (var values = Files.walk(root)) {
                values.filter(Files::isRegularFile).forEach(files::add);
            }
        }
        files.add(Path.of("build.gradle"));
        files.add(Path.of("settings.gradle"));
        files.sort(Comparator.comparing(PreJungleTempoBaselineV1Generator::portablePath));

        StringBuilder canonical = new StringBuilder();
        for (Path file : files) {
            canonical.append(portablePath(file)).append('|')
                    .append(sha256(Files.readAllBytes(file))).append('\n');
        }
        return new SourceTreeIdentity(
                sha256(canonical.toString().getBytes(StandardCharsets.UTF_8)), files.size());
    }

    private static String portablePath(Path value) {
        return value.normalize().toString().replace('\\', '/');
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property " + name);
        }
        return value.trim();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private record SourceTreeIdentity(String hash, int fileCount) {
    }

    record ProfileBaseline(
            SimulationRuntimeProfileId profileId,
            SimulationGameplayConfiguration resolvedGameplayConfiguration,
            String configurationHash,
            String activeGameplayRulesVersion
    ) {
    }

    private record MatchBaseline(
            String caseId,
            SimulationRuntimeProfileId runtimeProfileId,
            String configurationHash,
            String blueTeamCode,
            String redTeamCode,
            long matchSeed,
            int seriesGameNumber,
            String seriesHistoryBeforeHash,
            String draftDecisionHash,
            String finalDraftHash,
            String finalAssignmentHash,
            String replayProvenanceHash,
            String timelineHash,
            SimulationRandomFingerprint randomFingerprint,
            String winner,
            int durationSeconds,
            int eventCount,
            int snapshotCount
    ) {
    }

    private record BaselineDocument(
            String schemaVersion,
            String baselineId,
            String predecessorBaselineId,
            String predecessorArtifactSha256,
            String sourceRevision,
            String productionSourceTreeHashAlgorithm,
            String productionSourceTreeHash,
            int productionSourceFileCount,
            String verificationSequence,
            String fullRegressionStatus,
            String engineImplementationVersion,
            SimulationInstrumentation instrumentation,
            List<ProfileBaseline> profiles,
            SimulationResourceProvenance resourceProvenance,
            List<MatchBaseline> matches
    ) {
    }
}
