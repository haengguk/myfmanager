package com.lolfm.controller;

import com.lolfm.LolfmApplication;
import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.RealMatchApiV1Service;
import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Position;
import com.lolfm.draft.DraftActionType;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.PlayerActivityType;
import com.lolfm.simulator.StructureActionSource;
import com.lolfm.simulator.StructureKind;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.TowerTier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
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

/** Generates frontend handoff evidence only after a clean complete backend regression. */
public final class RealMatchApiV1ArtifactWriter {
    static final String CONTRACT = "real-match-api-v1-contract.json";
    static final String OPTIONS = "real-match-api-v1-options-example.json";
    static final String REQUEST = "real-match-api-v1-fixed-request.json";
    static final String RESPONSE = "real-match-api-v1-fixed-response.json";
    static final String ERRORS = "real-match-api-v1-error-contract.json";
    static final String HANDOFF = "real-match-api-v1-handoff.json";
    static final String MANIFEST = "SHA256SUMS.txt";
    static final List<String> ARTIFACTS = List.of(
            CONTRACT, OPTIONS, REQUEST, RESPONSE, ERRORS, HANDOFF);
    static final String MATCH_ENGINE_FREEZE_MANIFEST_SHA256 =
            "1f5bc20c347d25d833e822325de1fa294dc61d38c55da121ea30d15ab70a0728";
    static final String BASE_HEAD =
            "3d9570372c83de3a7325ad0d5186def3537bd4d7";
    static final String FIXED_BLUE = "GEN";
    static final String FIXED_RED = "T1";
    static final String FIXED_SEED = "73";

    private RealMatchApiV1ArtifactWriter() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected <backend-root> <output-directory>");
        }
        Path backendRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        FullRegressionResult full = fullRegression(
                backendRoot.resolve("build/test-results/test"));
        FreezeBinding freeze = verifyMatchEngineFreeze(
                backendRoot.resolve("build/reports/match-engine-v1-freeze"));
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                LolfmApplication.class).web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "logging.level.root=ERROR").run()) {
            write(context, output, full, freeze);
        }
    }

    private static void write(
            ConfigurableApplicationContext context,
            Path output,
            FullRegressionResult full,
            FreezeBinding freeze
    ) throws IOException {
        Files.createDirectories(output);
        RealMatchApiV1Service service = context.getBean(RealMatchApiV1Service.class);
        MatchEngineV1Canonicalizer canonicalizer = context.getBean(
                MatchEngineV1Canonicalizer.class);
        RealMatchApiV1Dtos.OptionsResponse options = service.options();
        RealMatchApiV1Dtos.SimulateRequest request = new RealMatchApiV1Dtos.SimulateRequest(
                RealMatchApiV1Dtos.REQUEST_SCHEMA, FIXED_BLUE, FIXED_RED, FIXED_SEED);
        RealMatchApiV1Dtos.Response response = service.simulate(request);
        require(response.integrity().outputHash() != null,
                "Fixed response output hash is missing");
        require(response.draft().seriesGameNumber() == 1,
                "Fixed response is not isolated Game 1");
        require(response.draft().hardFearlessExclusionsBeforeDraft().isEmpty(),
                "Fixed response unexpectedly inherited series exclusions");

        writeJson(canonicalizer, output.resolve(CONTRACT), contract(options, freeze));
        writeJson(canonicalizer, output.resolve(OPTIONS), options);
        writeJson(canonicalizer, output.resolve(REQUEST), request);
        writeJson(canonicalizer, output.resolve(RESPONSE), response);
        writeJson(canonicalizer, output.resolve(ERRORS), errorContract());
        writeJson(canonicalizer, output.resolve(HANDOFF), handoff(response, full, freeze));
        writeManifest(output);
        verifyGeneratedManifest(output);
    }

    private static Map<String, Object> contract(
            RealMatchApiV1Dtos.OptionsResponse options, FreezeBinding freeze
    ) {
        return map(
                "schemaVersion", "REAL_MATCH_API_V1_CONTRACT_DOCUMENT_V1",
                "status", "BACKEND_PRODUCTION_HTTP_BOUNDARY_READY",
                "endpoints", List.of(
                        map("method", "GET", "path", "/api/v1/real-matches/options",
                                "responseSchema", RealMatchApiV1Dtos.OPTIONS_SCHEMA),
                        map("method", "POST", "path", "/api/v1/real-matches/simulate",
                                "requestSchema", RealMatchApiV1Dtos.REQUEST_SCHEMA,
                                "responseSchema", RealMatchApiV1Dtos.RESPONSE_SCHEMA,
                                "errorSchema", RealMatchApiV1Dtos.ERROR_SCHEMA)),
                "schemas", Map.of(
                        "options", RealMatchApiV1Dtos.OPTIONS_SCHEMA,
                        "request", RealMatchApiV1Dtos.REQUEST_SCHEMA,
                        "response", RealMatchApiV1Dtos.RESPONSE_SCHEMA,
                        "draft", RealMatchApiV1Dtos.DRAFT_SCHEMA,
                        "error", RealMatchApiV1Dtos.ERROR_SCHEMA),
                "request", map(
                        "fieldInventory", recordFields(RealMatchApiV1Dtos.SimulateRequest.class),
                        "requiredFields", List.of(
                                "schemaVersion", "blueTeamCode", "redTeamCode", "seed"),
                        "unknownFields", "REJECTED_AT_REAL_MATCH_V1_BOUNDARY",
                        "teamCodeInput", "TRIMMED_THEN_UPPERCASE_CANONICALIZED",
                        "sameTeamAllowed", false,
                        "runtimeProfileOrGameplayFlagsAccepted", false,
                        "clientAuthoredDraftOrSeriesHistoryAccepted", false),
                "seed", Map.of(
                        "required", true,
                        "jsonType", "string",
                        "encoding", RealMatchApiV1Dtos.SEED_ENCODING,
                        "range", "JAVA_SIGNED_LONG",
                        "implicitOrServerGeneratedSeed", false),
                "response", map(
                        "topLevelFieldInventory",
                        recordFields(RealMatchApiV1Dtos.Response.class),
                        "structuredFieldInventory", responseFieldInventory(),
                        "enumValues", enumValues(),
                        "nullableFields", nullableFields(),
                        "displayMessagesGameplayAuthoritative", false,
                        "rawDomainObjectsExposed", false),
                "roster", Map.of(
                        "source", "LckTeamAssembler_AND_AUTHORITATIVE_PLAYER_RESOURCES",
                        "teamCount", options.teams().size(),
                        "playersPerTeam", 5,
                        "uniquePlayerCount", 50,
                        "teamOrder", "CANONICAL_TEAM_CODE",
                        "lineupOrder", "POSITION_ENUM"),
                "execution", Map.of(
                        "draftOwnership", "RealDraftMatchOrchestrator.orchestrateV1",
                        "matchEngineContract", MatchEngineV1Policy.CONTRACT_SCHEMA,
                        "scope", "FRESH_SINGLE_GAME_1_PER_HTTP_REQUEST",
                        "sharedSeriesState", false,
                        "outputIntegrityRequiredBeforeResponse", true),
                "productionPolicy", options.productionPolicy(),
                "matchEngineFreeze", freeze,
                "compatibility", Map.of(
                        "legacyPostApiMatchesSimulatePreserved", true,
                        "championApiPreserved", true,
                        "existingFrontendContractPreserved", true,
                        "frontendUsesRealMatchApiV1AtThisMilestone", false,
                        "matchEngineV1ContractChanged", false));
    }

    private static Map<String, Object> responseFieldInventory() {
        return map(
                "teamPresentation", recordFields(RealMatchApiV1Dtos.TeamPresentation.class),
                "playerPresentation", recordFields(RealMatchApiV1Dtos.PlayerPresentation.class),
                "championPresentation",
                recordFields(RealMatchApiV1Dtos.ChampionPresentation.class),
                "draft", recordFields(RealMatchApiV1Dtos.Draft.class),
                "draftDecision", recordFields(RealMatchApiV1Dtos.DraftDecision.class),
                "finalAssignment", recordFields(RealMatchApiV1Dtos.FinalAssignment.class),
                "result", recordFields(RealMatchApiV1Dtos.Result.class),
                "teamResult", recordFields(RealMatchApiV1Dtos.TeamResult.class),
                "playerResult", recordFields(RealMatchApiV1Dtos.PlayerResult.class),
                "timeline", recordFields(RealMatchApiV1Dtos.Timeline.class),
                "event", recordFields(RealMatchApiV1Dtos.Event.class),
                "snapshot", recordFields(RealMatchApiV1Dtos.Snapshot.class),
                "teamState", recordFields(RealMatchApiV1Dtos.TeamState.class),
                "playerState", recordFields(RealMatchApiV1Dtos.PlayerState.class),
                "integrity", recordFields(RealMatchApiV1Dtos.Integrity.class),
                "randomFingerprint",
                recordFields(RealMatchApiV1Dtos.RandomFingerprint.class));
    }

    private static Map<String, List<String>> enumValues() {
        LinkedHashMap<String, List<String>> values = new LinkedHashMap<>();
        values.put("TeamSide", names(TeamSide.values()));
        values.put("Position", names(Position.values()));
        values.put("GameEndReason", names(GameEndReason.values()));
        values.put("MatchEventType", names(MatchEventType.values()));
        values.put("DraftActionType", names(DraftActionType.values()));
        values.put("Lane", names(Lane.values()));
        values.put("CombatSource", names(CombatSource.values()));
        values.put("StructureActionSource", names(StructureActionSource.values()));
        values.put("StructureKind", names(StructureKind.values()));
        values.put("TowerTier", names(TowerTier.values()));
        values.put("PlayerActivityType", names(PlayerActivityType.values()));
        return Map.copyOf(values);
    }

    private static List<String> nullableFields() {
        return List.of(
                "result.winner",
                "timeline.winner",
                "timeline.events[].actorSide",
                "timeline.events[].actorPosition",
                "timeline.events[].lane",
                "timeline.events[].killerPlayerId",
                "timeline.events[].victimPlayerId",
                "timeline.events[].killerChampionId",
                "timeline.events[].victimChampionId",
                "timeline.events[].combatSource",
                "timeline.events[].structureActionSource",
                "timeline.events[].structureKind",
                "timeline.events[].structureTowerTier",
                "timeline.events[].structureAttackingSide",
                "timeline.events[].structureDefendingSide",
                "timeline.events[].displayMessage",
                "timeline.snapshots[].players[].activityType",
                "timeline.snapshots[].players[].activityOriginLane",
                "timeline.snapshots[].players[].activityTargetLane",
                "error.field");
    }

    private static Map<String, Object> errorContract() {
        return map(
                "schemaVersion", "REAL_MATCH_API_V1_ERROR_CONTRACT_DOCUMENT_V1",
                "responseSchema", RealMatchApiV1Dtos.ERROR_SCHEMA,
                "fieldInventory", recordFields(RealMatchApiV1Dtos.ErrorResponse.class),
                "stackTraceExposed", false,
                "internalResourcePathExposed", false,
                "legacyGlobalExceptionSemanticsChanged", false,
                "errors", List.of(
                        error("MALFORMED_JSON_OR_BODY", 400, "MALFORMED_REQUEST", null,
                                "요청 본문은 유효한 JSON 객체여야 합니다."),
                        error("MISSING_SCHEMA", 400, "INVALID_REQUEST_SCHEMA", "schemaVersion",
                                "schemaVersion이 필요합니다."),
                        error("UNSUPPORTED_SCHEMA", 400, "INVALID_REQUEST_SCHEMA", "schemaVersion",
                                "지원하지 않는 Real Match 요청 schema입니다."),
                        error("MISSING_OR_BLANK_BLUE_TEAM", 400,
                                "BLUE_TEAM_REQUIRED", "blueTeamCode",
                                "blueTeamCode가 필요합니다."),
                        error("MISSING_OR_BLANK_RED_TEAM", 400,
                                "RED_TEAM_REQUIRED", "redTeamCode",
                                "redTeamCode가 필요합니다."),
                        error("UNKNOWN_BLUE_TEAM", 400, "UNKNOWN_TEAM", "blueTeamCode",
                                "지원하지 않는 BLUE 팀 코드입니다."),
                        error("UNKNOWN_RED_TEAM", 400, "UNKNOWN_TEAM", "redTeamCode",
                                "지원하지 않는 RED 팀 코드입니다."),
                        error("SAME_CANONICAL_TEAM", 400,
                                "SAME_TEAM_NOT_ALLOWED", "redTeamCode",
                                "BLUE 팀과 RED 팀은 서로 달라야 합니다."),
                        error("MISSING_OR_NON_STRING_SEED", 400, "INVALID_SEED", "seed",
                                "seed는 signed 64-bit decimal string이어야 합니다."),
                        error("NON_CANONICAL_SEED", 400, "INVALID_SEED", "seed",
                                "seed는 canonical signed 64-bit decimal string이어야 합니다."),
                        error("OUT_OF_RANGE_SEED", 400, "INVALID_SEED", "seed",
                                "seed가 signed 64-bit 범위를 벗어났습니다."),
                        error("UNKNOWN_FIELD", 400,
                                "UNSUPPORTED_REQUEST_FIELD", "<unknown-field>",
                                "Real Match API V1에서 지원하지 않는 요청 필드입니다."),
                        error("KNOWN_ROSTER_OR_DRAFT_PREFLIGHT", 422,
                                "REAL_MATCH_PREFLIGHT_FAILED", null,
                                "실제 roster 또는 Draft 사전 검증을 통과하지 못했습니다."),
                        error("OUTPUT_POLICY_PROVENANCE_OR_HASH", 500,
                                "ENGINE_OUTPUT_INTEGRITY_FAILED", null,
                                "경기 결과 무결성을 확인할 수 없습니다."),
                        error("UNEXPECTED_INTERNAL_FAILURE", 500,
                                "REAL_MATCH_INTERNAL_ERROR", null,
                                "실제 매치 요청을 처리하지 못했습니다.")));
    }

    private static Map<String, Object> handoff(
            RealMatchApiV1Dtos.Response response,
            FullRegressionResult full,
            FreezeBinding freeze
    ) {
        return map(
                "schemaVersion", "REAL_MATCH_API_V1_FRONTEND_HANDOFF_V1",
                "backendBaseHead", BASE_HEAD,
                "suggestedCommitMessage", "feat: [BE] 실제 LCK 매치 API V1 추가",
                "status", "BACKEND_READY_FRONTEND_NOT_YET_CONNECTED",
                "endpoints", List.of(
                        "GET /api/v1/real-matches/options",
                        "POST /api/v1/real-matches/simulate"),
                "requestExample", Map.of(
                        "schemaVersion", RealMatchApiV1Dtos.REQUEST_SCHEMA,
                        "blueTeamCode", FIXED_BLUE,
                        "redTeamCode", FIXED_RED,
                        "seed", FIXED_SEED),
                "responseExamplePath", RESPONSE,
                "errorContractPath", ERRORS,
                "seed", Map.of(
                        "jsonType", "string",
                        "encoding", RealMatchApiV1Dtos.SEED_ENCODING,
                        "canonicalExamples", List.of("0", "73", "-73"),
                        "rejectedExamples", List.of("+73", "073", "-0", " 73 ")),
                "enumValues", enumValues(),
                "nullableFields", nullableFields(),
                "errorCodes", List.of(
                        "MALFORMED_REQUEST", "INVALID_REQUEST_SCHEMA",
                        "BLUE_TEAM_REQUIRED", "RED_TEAM_REQUIRED", "UNKNOWN_TEAM",
                        "SAME_TEAM_NOT_ALLOWED", "INVALID_SEED",
                        "UNSUPPORTED_REQUEST_FIELD", "REAL_MATCH_PREFLIGHT_FAILED",
                        "ENGINE_OUTPUT_INTEGRITY_FAILED", "REAL_MATCH_INTERNAL_ERROR"),
                "frontendAuthoritativeStructuredFields", List.of(
                        "teams[].teamSide/teamCode/lineup[].playerId/position/championId",
                        "draft.decisions/finalAssignments/finalDraftHash/finalAssignmentHash",
                        "result.winner/endReason/teams/players",
                        "timeline.events[].eventType/stable participant IDs/combat and structure fields",
                        "timeline.snapshots[].team state/player state/structured progression",
                        "integrity.policy/input/resource/replay/timeline/output hashes/randomFingerprint"),
                "displayOnlyFields", List.of(
                        "teams[].displayName", "teams[].lineup[].nickname",
                        "teams[].lineup[].champion display metadata",
                        "timeline.events[].displayMessage"),
                "fixedFixture", Map.of(
                        "blueTeamCode", FIXED_BLUE,
                        "redTeamCode", FIXED_RED,
                        "seed", FIXED_SEED,
                        "seriesGameNumber", response.draft().seriesGameNumber(),
                        "hardFearlessExclusionsBeforeDraft",
                        response.draft().hardFearlessExclusionsBeforeDraft(),
                        "outputHash", response.integrity().outputHash()),
                "productionPolicy", Map.of(
                        "contract", response.integrity().matchEngineContract(),
                        "policyId", response.integrity().policyId(),
                        "policyHash", response.integrity().policyHash(),
                        "runtimeProfileId", response.integrity().runtimeProfileId(),
                        "configurationHash", response.integrity().configurationHash(),
                        "engineImplementationVersion",
                        response.integrity().engineImplementationVersion(),
                        "activeGameplayRulesVersion",
                        response.integrity().activeGameplayRulesVersion()),
                "matchEngineFreeze", freeze,
                "backendRun", "cd backend && gradlew.bat bootRun --console=plain",
                "curlExamples", List.of(
                        "curl http://localhost:8080/api/v1/real-matches/options",
                        "curl -X POST http://localhost:8080/api/v1/real-matches/simulate "
                                + "-H \"Content-Type: application/json\" "
                                + "-d '{\"schemaVersion\":\"REAL_MATCH_SIMULATE_REQUEST_V1\","
                                + "\"blueTeamCode\":\"GEN\",\"redTeamCode\":\"T1\","
                                + "\"seed\":\"73\"}'"),
                "compatibility", Map.of(
                        "legacyEndpointPreserved", true,
                        "championApiPreserved", true,
                        "frontendFilesChanged", false),
                "knownLimitations", List.of(
                        "SINGLE_GAME_GAME_1_ONLY",
                        "NO_SHARED_HARD_FEARLESS_SERIES_HISTORY",
                        "FRONTEND_NOT_YET_CONNECTED",
                        "NO_BO3_OR_BO5",
                        "NO_SAVE_LOAD",
                        "NO_CAREER_OR_SEASON",
                        "ECONOMY_AND_TEMPO_CANDIDATES_INACTIVE"),
                "verification", full,
                "nextMilestones", List.of(
                        "REAL_MATCH_FRONTEND_V1", "SERIES_LIFECYCLE_V1"));
    }

    private static Map<String, Object> error(
            String variant, int status, String code, String field, String message
    ) {
        return map("variant", variant, "httpStatus", status,
                "code", code, "field", field, "message", message);
    }

    private static FreezeBinding verifyMatchEngineFreeze(Path directory) throws IOException {
        Path manifest = directory.resolve(MANIFEST);
        require(Files.isRegularFile(manifest), "Missing Match Engine V1 freeze manifest");
        byte[] bytes = Files.readAllBytes(manifest);
        require(sha256(bytes).equals(MATCH_ENGINE_FREEZE_MANIFEST_SHA256),
                "Match Engine V1 freeze manifest SHA differs");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        require(lines.size() == 7, "Match Engine V1 freeze manifest must have seven entries");
        for (String line : lines) {
            String[] fields = line.split("  ", 2);
            require(fields.length == 2 && fields[0].matches("[0-9a-f]{64}"),
                    "Invalid Match Engine V1 freeze manifest line");
            Path artifact = directory.resolve(fields[1]).normalize();
            require(artifact.startsWith(directory) && Files.isRegularFile(artifact),
                    "Invalid Match Engine V1 freeze artifact path");
            require(sha256(Files.readAllBytes(artifact)).equals(fields[0]),
                    "Match Engine V1 freeze raw SHA mismatch: " + fields[1]);
        }
        return new FreezeBinding(
                "MATCH_ENGINE_V1_FROZEN", MATCH_ENGINE_FREEZE_MANIFEST_SHA256,
                lines.size(), "RAW_SHA256_VERIFIED_NO_REGENERATION");
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
        require(suites >= 175 && tests >= 1_993,
                "Test XML does not represent a complete backend regression");
        require(failures == 0 && errors == 0, "Full backend regression is not clean");
        return new FullRegressionResult(
                "CLEAN_PASS", suites, tests, failures, errors, skipped, 1,
                "gradlew.bat test --console=plain --no-daemon");
    }

    private static void writeJson(
            MatchEngineV1Canonicalizer canonicalizer, Path path, Object value
    ) throws IOException {
        Files.writeString(path, canonicalizer.canonicalJson(value) + '\n',
                StandardCharsets.UTF_8);
    }

    private static void writeManifest(Path output) throws IOException {
        StringBuilder manifest = new StringBuilder();
        for (String file : ARTIFACTS) {
            manifest.append(sha256(Files.readAllBytes(output.resolve(file))))
                    .append("  ").append(file).append('\n');
        }
        Files.writeString(output.resolve(MANIFEST), manifest, StandardCharsets.UTF_8);
    }

    private static void verifyGeneratedManifest(Path output) throws IOException {
        List<String> lines = Files.readAllLines(output.resolve(MANIFEST),
                StandardCharsets.UTF_8);
        require(lines.size() == ARTIFACTS.size(),
                "Real Match API V1 manifest entry count differs");
        for (int index = 0; index < ARTIFACTS.size(); index++) {
            String[] fields = lines.get(index).split("  ", 2);
            require(fields.length == 2 && fields[1].equals(ARTIFACTS.get(index)),
                    "Real Match API V1 manifest ordering differs");
            require(sha256(Files.readAllBytes(output.resolve(fields[1]))).equals(fields[0]),
                    "Real Match API V1 raw SHA mismatch: " + fields[1]);
        }
    }

    private static List<String> recordFields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(component ->
                component.getName() + ":" + component.getGenericType().getTypeName()).toList();
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static Map<String, Object> map(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Map entries must be key/value pairs");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            String key = Objects.toString(entries[index]);
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate map key " + key);
            }
            result.put(key, entries[index + 1]);
        }
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    record FreezeBinding(
            String status,
            String manifestSha256,
            int manifestEntryCount,
            String verification
    ) {
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
