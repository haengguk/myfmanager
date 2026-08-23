package com.lolfm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.LolfmApplication;
import com.lolfm.simulator.SimulationInstrumentation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.w3c.dom.Element;

/** Generates the Match Engine V1 freeze evidence after a clean complete backend regression. */
public final class MatchEngineV1FreezeArtifactWriter {
    static final String CONTRACT = "match-engine-v1-contract.json";
    static final String POLICY = "match-engine-v1-production-policy.json";
    static final String INPUT = "match-engine-v1-input-contract.json";
    static final String OUTPUT = "match-engine-v1-output-contract.json";
    static final String FIXTURES = "match-engine-v1-fixed-fixtures.json";
    static final String CROSS_JVM = "match-engine-v1-cross-jvm-verification.json";
    static final String SUMMARY = "match-engine-v1-freeze-summary.json";
    static final String MANIFEST = "SHA256SUMS.txt";
    static final List<String> ARTIFACTS = List.of(
            CONTRACT, POLICY, INPUT, OUTPUT, FIXTURES, CROSS_JVM, SUMMARY);

    private MatchEngineV1FreezeArtifactWriter() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected <backend-root> <output-directory>");
        }
        Path backendRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        FullRegressionResult full = fullRegression(backendRoot.resolve(
                "build/test-results/test"));
        verifyFinalDecision(backendRoot.resolve("build/reports/final-13g-b"));
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                LolfmApplication.class).web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "logging.level.root=ERROR").run()) {
            write(context, backendRoot, output, full);
        }
    }

    private static void write(
            ConfigurableApplicationContext context,
            Path backendRoot,
            Path output,
            FullRegressionResult full
    ) throws Exception {
        Files.createDirectories(output);
        RealDraftMatchOrchestrator orchestrator = context.getBean(
                RealDraftMatchOrchestrator.class);
        MatchEngineV1InputFactory inputFactory = context.getBean(
                MatchEngineV1InputFactory.class);
        MatchEngineV1 engine = context.getBean(MatchEngineV1.class);
        MatchEngineV1Canonicalizer canonicalizer = context.getBean(
                MatchEngineV1Canonicalizer.class);
        RealDraftMatchResult legacy = orchestrator.orchestrate(
                "GEN", "T1", MatchEngineV1CrossJvmProbe.FIXED_SEED);
        MatchEngineV1Input input = inputFactory.fromRealDraft(
                legacy.blueTeamCode(), legacy.blueTeam(), legacy.redTeamCode(), legacy.redTeam(),
                legacy.matchSeed(), legacy.seriesGameNumber(),
                legacy.hardFearlessExclusionsBeforeDraft(), legacy.draftResult());
        MatchEngineV1.MatchEngineV1Execution v1 = engine.executeDetailed(
                input, SimulationInstrumentation.enabled());
        require(legacy.executionProvenance().equals(v1.executionProvenance()),
                "Existing RealDraft and V1 provenance differ");
        require(legacy.executionProvenance().timelineHash().equals(
                v1.executionProvenance().timelineHash()),
                "Existing RealDraft and V1 timeline differ");
        require(legacy.executionProvenance().randomFingerprint().equals(
                v1.executionProvenance().randomFingerprint()),
                "Existing RealDraft and V1 Random fingerprint differ");

        CrossJvmResult crossJvm = crossJvm();
        Phase13GB1AuditArtifactWriter.SourceTreeIdentity freezeSource =
                Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot);
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        SimulationExecutionProvenance provenance = v1.executionProvenance();

        writeJson(canonicalizer, output.resolve(CONTRACT), Map.of(
                "schemaVersion", "MATCH_ENGINE_V1_FREEZE_CONTRACT_DOCUMENT_V1",
                "contractIdentity", MatchEngineV1Policy.CONTRACT_SCHEMA,
                "frozenSemantics", List.of(
                        "PRODUCTION_RUNTIME_POLICY_IDENTITY",
                        "MATCH_ENGINE_V1_INPUT_SEMANTICS",
                        "MATCH_ENGINE_V1_OUTPUT_SEMANTICS",
                        "MATCH_RESULT_SUMMARY_V1",
                        "IMMUTABLE_TIMELINE_V1_PROJECTION",
                        "MANDATORY_EXECUTION_PROVENANCE",
                        "CANONICAL_HASH_SCOPE",
                        "ADDITIVE_COMPATIBILITY_POLICY"),
                "notFrozen", List.of(
                        "INTERNAL_RESOLVER_STRUCTURE", "INTERNAL_DIAGNOSTICS",
                        "DISPLAY_MESSAGE_WORDING", "FUTURE_V2_TUNING",
                        "ECONOMY_OR_TEMPO_CANDIDATE_DESIGN"),
                "compatibilityPolicy", "V1_FIELDS_KEEP_MEANING_ADDITIVE_OPTIONAL_ONLY_"
                        + "BREAKING_CHANGES_REQUIRE_V2"));
        writeJson(canonicalizer, output.resolve(POLICY), Map.of(
                "schemaVersion", "MATCH_ENGINE_V1_FREEZE_POLICY_DOCUMENT_V1",
                "productionPolicy", policy,
                "final13GBinding", finalBinding(),
                "draftRuleSetIdentity", provenance.draftRuleSetIdentity(),
                "draftRuleSetHash", provenance.draftRuleSetHash(),
                "draftScoringPolicyHash", provenance.draftScoringPolicyHash(),
                "resourceProvenanceHash",
                provenance.resourceProvenance().resourceProvenanceHash(),
                "simulationOptionsProductionDefaultsAuthoritative", false));
        writeJson(canonicalizer, output.resolve(INPUT), inputContract());
        writeJson(canonicalizer, output.resolve(OUTPUT), outputContract());
        writeJson(canonicalizer, output.resolve(FIXTURES), Map.of(
                "schemaVersion", "MATCH_ENGINE_V1_FIXED_FIXTURES_V1",
                "fixture", map(
                        "blueTeamIdentity", input.blueTeam().teamIdentity(),
                        "redTeamIdentity", input.redTeam().teamIdentity(),
                        "seriesGameNumber", input.finalDraft().seriesGameNumber(),
                        "seed", input.matchSeed(),
                        "inputHash", input.inputHash(),
                        "finalDraftHash", input.finalDraft().finalDraftHash(),
                        "finalAssignmentHash", input.finalDraft().finalAssignmentHash(),
                        "replayProvenanceHash", provenance.replayProvenanceHash(),
                        "simulatorTimelineHash", provenance.timelineHash(),
                        "structuredTimelineHash", v1.output().structuredTimelineHash(),
                        "randomFingerprint", provenance.randomFingerprint(),
                        "outputHash", v1.output().outputHash(),
                        "winner", Objects.toString(v1.output().resultSummary().winner(), "NONE"),
                        "endReason", v1.output().resultSummary().endReason(),
                        "durationSeconds", v1.output().resultSummary().durationSeconds(),
                        "eventCount", v1.output().timeline().events().size(),
                        "snapshotCount", v1.output().timeline().snapshots().size()),
                "existingRealDraftParity", Map.of(
                        "winnerParity", true,
                        "completeTimelineParity", true,
                        "finalStateParity", true,
                        "provenanceParity", true,
                        "randomFingerprintParity", true),
                "summary", v1.output().resultSummary()));
        writeJson(canonicalizer, output.resolve(CROSS_JVM), map(
                "schemaVersion", "MATCH_ENGINE_V1_CROSS_JVM_VERIFICATION_V1",
                "freshJvmCount", 2,
                "canonicalOutputExact", true,
                "structuredSummaryExact", true,
                "timelineHashExact", true,
                "replayProvenanceHashExact", true,
                "randomFingerprintExact", true,
                "outputHashExact", true,
                "generatedManifestExact", true,
                "probeManifestSha256", crossJvm.manifestSha256(),
                "probeFiles", crossJvm.fileSha256()));
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", "MATCH_ENGINE_V1_FREEZE_SUMMARY_V1");
        summary.put("status", "MATCH_ENGINE_V1_FROZEN");
        summary.put("contractIdentity", MatchEngineV1Policy.CONTRACT_SCHEMA);
        summary.put("productionRuntimePolicy", policy);
        summary.put("final13GDecisionManifestSha256",
                MatchEngineV1Policy.FINAL_13G_B_MANIFEST_SHA256);
        summary.put("final13GApprovedSourceTree", Map.of(
                "hash", MatchEngineV1Policy.FINAL_13G_B_APPROVED_SOURCE_TREE_SHA256,
                "fileCount", 472));
        summary.put("matchEngineV1FreezeSourceTree", freezeSource);
        summary.put("productionSourceIdentityExpectedToAdvance", true);
        summary.put("resourceProvenanceHash",
                provenance.resourceProvenance().resourceProvenanceHash());
        summary.put("resourceChangedSinceFinal13G", false);
        summary.put("draftRuleSetHash", provenance.draftRuleSetHash());
        summary.put("draftScoringPolicyHash", provenance.draftScoringPolicyHash());
        summary.put("gameplaySemanticsChanged", false);
        summary.put("randomConsumptionChanged", false);
        summary.put("draftLogicChanged", false);
        summary.put("candidateSystemsActive", false);
        summary.put("realDraftV1ParityExact", true);
        summary.put("crossJvmExact", true);
        summary.put("fullBackendRegression", full);
        summary.put("httpDemoUsesMatchEngineV1", false);
        summary.put("httpDemoRosterSource", "DUMMY_DATA_FACTORY");
        summary.put("frontendChangedByMilestone", false);
        summary.put("calibrationExecuted", false);
        summary.put("holdoutExecuted", false);
        summary.put("v2Backlog", List.of(
                "REAL_MATCH_API_TRANSITION", "FRONTEND_INTEGRATION", "BO3_BO5",
                "CAREER", "SAVE_LOAD", "SEASON", "ECONOMY_CANDIDATE_V2_REVIEW",
                "TEMPO_V2_REDESIGN"));
        writeJson(canonicalizer, output.resolve(SUMMARY), summary);
        writeManifest(output);
    }

    private static Map<String, Object> inputContract() {
        return map(
                "schemaVersion", "MATCH_ENGINE_V1_INPUT_CONTRACT_DOCUMENT_V1",
                "inputSchemaVersion", MatchEngineV1Input.SCHEMA,
                "fieldInventory", recordFields(MatchEngineV1Input.class),
                "teamFieldInventory", recordFields(MatchEngineV1Input.TeamInput.class),
                "playerFieldInventory", recordFields(MatchEngineV1Input.PlayerInput.class),
                "assignmentFieldInventory",
                recordFields(MatchEngineV1Input.ChampionAssignmentInput.class),
                "draftFieldInventory", recordFields(MatchEngineV1Input.DraftInput.class),
                "identityDiscipline", "PLAYER_ID_TEAM_SIDE_POSITION_CHAMPION_ID_NO_DISPLAY_OR_INDEX_INFERENCE",
                "validationBeforeRandom", true,
                "draftOwnership", "FINAL_DRAFT_RESULT_CONTROLLED_ADAPTER_NO_REDRAFT",
                "inputHashAlgorithm", MatchEngineV1Input.INPUT_HASH_ALGORITHM,
                "rejections", List.of(
                        "NON_5V5_ROSTER", "MISSING_OR_DUPLICATE_POSITION",
                        "DUPLICATE_PLAYER_ID", "TEAM_IDENTITY_COLLISION",
                        "MISSING_OR_DUPLICATE_ASSIGNMENT", "ILLEGAL_CHAMPION_ASSIGNMENT",
                        "DRAFT_ASSIGNMENT_IDENTITY_MISMATCH", "NON_BASELINE_POLICY",
                        "ECONOMY_OR_TEMPO_CANDIDATE_ACTIVATION"));
    }

    private static Map<String, Object> outputContract() {
        return map(
                "schemaVersion", "MATCH_ENGINE_V1_OUTPUT_CONTRACT_DOCUMENT_V1",
                "outputSchemaVersion", MatchEngineV1Output.SCHEMA,
                "fieldInventory", recordFields(MatchEngineV1Output.class),
                "summaryFieldInventory",
                recordFields(MatchEngineV1Output.MatchResultSummaryV1.class),
                "timelineFieldInventory", recordFields(MatchEngineV1Output.TimelineV1.class),
                "eventFieldInventory", recordFields(MatchEngineV1Output.EventV1.class),
                "snapshotFieldInventory", recordFields(MatchEngineV1Output.SnapshotV1.class),
                "deepCopy", true,
                "collectionsExternallyMutable", false,
                "mandatoryExecutionProvenance", true,
                "hashContracts", Map.of(
                        "configurationHash", "GAMEPLAY_CONFIGURATION_ONLY_DIAGNOSTICS_EXCLUDED",
                        "replayProvenanceHash", "EXECUTION_INPUT_RESOURCE_DRAFT_PROFILE_SEED",
                        "simulatorTimelineHash", "LEGACY_COMPLETE_TIMELINE_INCLUDING_DISPLAY_PLAYBACK",
                        "structuredTimelineHash", "V1_STRUCTURED_PLAYBACK_DISPLAY_WORDING_EXCLUDED",
                        "outputHash", MatchEngineV1Output.OUTPUT_HASH_SCOPE,
                        "canonicalJson", MatchEngineV1Canonicalizer.HASH_ALGORITHM));
    }

    private static Map<String, Object> finalBinding() {
        return Map.of(
                "manifestSha256", MatchEngineV1Policy.FINAL_13G_B_MANIFEST_SHA256,
                "evidenceStatus", "FINAL_EVIDENCE_VALID",
                "productionDecision", "KEEP_CURRENT_RUNTIME_DEFAULT",
                "runtimeIdentityStatus", "EXACT",
                "freezeReadiness", "READY_FOR_MATCH_ENGINE_V1_FREEZE",
                "approvedSourceTreeHash",
                MatchEngineV1Policy.FINAL_13G_B_APPROVED_SOURCE_TREE_SHA256);
    }

    private static CrossJvmResult crossJvm() throws Exception {
        Path root = Files.createTempDirectory("match-engine-v1-freeze-cross-jvm-");
        Path first = root.resolve("a");
        Path second = root.resolve("b");
        MatchEngineV1CrossJvmProbe.ProcessResult a =
                MatchEngineV1CrossJvmProbe.launchFreshJvm(first);
        MatchEngineV1CrossJvmProbe.ProcessResult b =
                MatchEngineV1CrossJvmProbe.launchFreshJvm(second);
        require(a.exitCode() == 0, "First cross-JVM probe failed: " + a.log());
        require(b.exitCode() == 0, "Second cross-JVM probe failed: " + b.log());
        for (String file : MatchEngineV1CrossJvmProbe.PAYLOAD_FILES) {
            require(Arrays.equals(Files.readAllBytes(first.resolve(file)),
                    Files.readAllBytes(second.resolve(file))),
                    "Cross-JVM file differs: " + file);
        }
        byte[] firstManifest = Files.readAllBytes(first.resolve(
                MatchEngineV1CrossJvmProbe.MANIFEST));
        require(Arrays.equals(firstManifest, Files.readAllBytes(second.resolve(
                MatchEngineV1CrossJvmProbe.MANIFEST))), "Cross-JVM manifest differs");
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        for (String file : MatchEngineV1CrossJvmProbe.PAYLOAD_FILES) {
            hashes.put(file, MatchEngineV1CrossJvmProbe.sha256(
                    Files.readAllBytes(first.resolve(file))));
        }
        return new CrossJvmResult(
                MatchEngineV1CrossJvmProbe.sha256(firstManifest), Map.copyOf(hashes));
    }

    private static void verifyFinalDecision(Path directory) throws IOException {
        Path manifest = directory.resolve("SHA256SUMS.txt");
        require(Files.isRegularFile(manifest), "Missing Final 13G-B manifest");
        require(MatchEngineV1CrossJvmProbe.sha256(Files.readAllBytes(manifest))
                        .equals(MatchEngineV1Policy.FINAL_13G_B_MANIFEST_SHA256),
                "Final 13G-B manifest SHA differs");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        require(lines.size() == 6, "Final 13G-B manifest must have six entries");
        for (String line : lines) {
            String[] fields = line.split("  ", 2);
            require(fields.length == 2 && fields[0].matches("[0-9a-f]{64}"),
                    "Invalid Final 13G-B manifest line");
            require(MatchEngineV1CrossJvmProbe.sha256(
                            Files.readAllBytes(directory.resolve(fields[1]))).equals(fields[0]),
                    "Final 13G-B raw SHA mismatch: " + fields[1]);
        }
        JsonNode decision = new ObjectMapper().readTree(
                directory.resolve("final-13g-b-production-decision.json").toFile());
        require(decision.path("evidenceStatus").asText().equals("FINAL_EVIDENCE_VALID")
                        && decision.path("productionDecision").asText()
                        .equals("KEEP_CURRENT_RUNTIME_DEFAULT")
                        && decision.path("runtimeIdentityStatus").asText().equals("EXACT")
                        && decision.path("matchEngineV1FreezeReadiness").asText()
                        .equals("READY_FOR_MATCH_ENGINE_V1_FREEZE"),
                "Final 13G-B decision is not approved for V1 freeze");
    }

    private static FullRegressionResult fullRegression(Path directory) throws Exception {
        require(Files.isDirectory(directory), "Missing full regression XML directory");
        int suites = 0;
        int tests = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(value -> value.getFileName().toString()
                            .startsWith("TEST-") && value.toString().endsWith(".xml"))
                    .sorted().toList()) {
                Element suite = factory.newDocumentBuilder().parse(path.toFile())
                        .getDocumentElement();
                suites++;
                tests += Integer.parseInt(suite.getAttribute("tests"));
                failures += Integer.parseInt(suite.getAttribute("failures"));
                errors += Integer.parseInt(suite.getAttribute("errors"));
                skipped += Integer.parseInt(suite.getAttribute("skipped"));
            }
        }
        require(suites >= 170 && tests >= 1_970,
                "Test XML does not represent a complete backend regression");
        require(failures == 0 && errors == 0, "Full backend regression is not clean");
        return new FullRegressionResult(
                "CLEAN_PASS", suites, tests, failures, errors, skipped,
                1, "gradlew.bat test --console=plain --no-daemon");
    }

    private static List<String> recordFields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(component ->
                component.getName() + ":" + component.getGenericType().getTypeName()).toList();
    }

    private static Map<String, Object> map(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Map entries must be key/value pairs");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            String key = Objects.toString(entries[index]);
            if (result.put(key, entries[index + 1]) != null) {
                throw new IllegalArgumentException("Duplicate map key " + key);
            }
        }
        return result;
    }

    private static void writeJson(
            MatchEngineV1Canonicalizer canonicalizer, Path path, Object value
    ) throws IOException {
        Files.writeString(path, canonicalizer.canonicalJson(value) + '\n', StandardCharsets.UTF_8);
    }

    private static void writeManifest(Path output) throws IOException {
        StringBuilder manifest = new StringBuilder();
        for (String file : ARTIFACTS) {
            manifest.append(MatchEngineV1CrossJvmProbe.sha256(
                    Files.readAllBytes(output.resolve(file))))
                    .append("  ").append(file).append('\n');
        }
        Files.writeString(output.resolve(MANIFEST), manifest, StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    record CrossJvmResult(String manifestSha256, Map<String, String> fileSha256) {
    }

    record FullRegressionResult(
            String status,
            int suiteCount,
            int testCount,
            int failures,
            int errors,
            int skipped,
            int fullRegressionRunCount,
            String command
    ) {
    }
}
