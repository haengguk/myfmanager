package com.lolfm.controller;

import com.lolfm.LolfmApplication;
import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.RealMatchApiV1Service;
import com.lolfm.application.SimulationProvenanceService;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.draft.DraftActionType;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
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
    static final int MIN_FULL_REGRESSION_SUITES = 180;
    static final int MIN_FULL_REGRESSION_TESTS = 2_016;
    static final String SOURCE_TREE_HASH_ALGORITHM =
            "SHA256_UTF8_SORTED_RELATIVE_PATH_AND_RAW_FILE_SHA256_LINES_V1";
    static final String SOURCE_BINDING_SUITE =
            "com.lolfm.controller.RealMatchApiV1VerificationBindingTest";
    static final Map<String, Integer> REQUIRED_FULL_REGRESSION_SUITES = Map.ofEntries(
            Map.entry("com.lolfm.controller.RealMatchApiV1RequestParserTest", 4),
            Map.entry("com.lolfm.application.RealMatchApiV1ServiceTest", 7),
            Map.entry("com.lolfm.controller.RealMatchApiV1ControllerTest", 7),
            Map.entry("com.lolfm.controller.RealMatchApiV1ErrorBoundaryTest", 2),
            Map.entry(SOURCE_BINDING_SUITE, 1),
            Map.entry("com.lolfm.controller.ChampionApiTest", 4),
            Map.entry("com.lolfm.application.MatchEngineV1ContractTest", 11),
            Map.entry("com.lolfm.application.RealDraftMatchOrchestratorTest", 14));
    static final String FIXED_BLUE = "GEN";
    static final String FIXED_RED = "T1";
    static final String FIXED_SEED = "73";
    static final List<String> EXPECTED_TEAM_CODES = List.of(
            "BFX", "BRO", "DK", "DNS", "GEN", "HLE", "KRX", "KT", "NS", "T1");

    private RealMatchApiV1ArtifactWriter() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected <backend-root> <output-directory>");
        }
        Path backendRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        SourceTreeIdentity productionSource = productionSourceTree(backendRoot);
        SourceTreeIdentity verificationSource = verificationSourceTree(backendRoot);
        FullRegressionResult full = fullRegression(
                backendRoot.resolve("build/test-results/test"),
                productionSource, verificationSource);
        FreezeBinding freeze = verifyMatchEngineFreeze(
                backendRoot.resolve("build/reports/match-engine-v1-freeze"));
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                LolfmApplication.class).web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "logging.level.root=ERROR").run()) {
            write(context, output, full, freeze, productionSource, verificationSource);
        }
    }

    private static void write(
            ConfigurableApplicationContext context,
            Path output,
            FullRegressionResult full,
            FreezeBinding freeze,
            SourceTreeIdentity productionSource,
            SourceTreeIdentity verificationSource
    ) throws IOException {
        Files.createDirectories(output);
        RealMatchApiV1Service service = context.getBean(RealMatchApiV1Service.class);
        LckTeamAssembler teams = context.getBean(LckTeamAssembler.class);
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
        validateCurrentRuntimeHandoff(options, request, response, teams);
        RealMatchApiV1Dtos.Response replay = service.simulate(request);
        require(canonicalizer.canonicalJson(response).equals(canonicalizer.canonicalJson(replay)),
                "Fixed response differs on same-request same-seed replay");

        writeJson(canonicalizer, output.resolve(CONTRACT), contract(
                options, freeze, productionSource, verificationSource));
        writeJson(canonicalizer, output.resolve(OPTIONS), options);
        writeJson(canonicalizer, output.resolve(REQUEST), request);
        writeJson(canonicalizer, output.resolve(RESPONSE), response);
        writeJson(canonicalizer, output.resolve(ERRORS), errorContract());
        writeJson(canonicalizer, output.resolve(HANDOFF), handoff(
                response, full, freeze, productionSource, verificationSource));
        writeManifest(output);
        verifyGeneratedManifest(output);
    }

    private static Map<String, Object> contract(
            RealMatchApiV1Dtos.OptionsResponse options,
            FreezeBinding freeze,
            SourceTreeIdentity productionSource,
            SourceTreeIdentity verificationSource
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
                        "requestOutputProvenanceBindingRequired", true,
                        "typedPreflightFailureBoundary", true,
                        "outputIntegrityRequiredBeforeResponse", true),
                "productionPolicy", options.productionPolicy(),
                "matchEngineFreeze", freeze,
                "sourceEvidence", sourceEvidence(productionSource, verificationSource),
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
                "playerAbilityProfile",
                recordFields(RealMatchApiV1Dtos.PlayerAbilityProfile.class),
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
            FreezeBinding freeze,
            SourceTreeIdentity productionSource,
            SourceTreeIdentity verificationSource
    ) {
        return map(
                "schemaVersion", "REAL_MATCH_API_V1_FRONTEND_HANDOFF_V1",
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
                        "result.winner/endReason/teams/players/players[].abilityProfile",
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
                        "winner", response.result().winner(),
                        "durationSeconds", response.result().durationSeconds(),
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
                "sourceEvidence", sourceEvidence(productionSource, verificationSource),
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
                "semanticAudit", "CURRENT_RUNTIME_V8_EXACT_PASS",
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

    private static void validateCurrentRuntimeHandoff(
            RealMatchApiV1Dtos.OptionsResponse options,
            RealMatchApiV1Dtos.SimulateRequest request,
            RealMatchApiV1Dtos.Response response,
            LckTeamAssembler teams
    ) {
        require(RealMatchApiV1Dtos.OPTIONS_SCHEMA.equals(options.schemaVersion()),
                "Options schema differs");
        require(options.teams().stream().map(RealMatchApiV1Dtos.OptionTeam::teamCode).toList()
                        .equals(EXPECTED_TEAM_CODES),
                "Options team set or canonical ordering differs");
        HashSet<String> allOptionPlayerIds = new HashSet<>();
        for (RealMatchApiV1Dtos.OptionTeam team : options.teams()) {
            require(team.lineup().size() == Position.values().length,
                    "Options lineup cardinality differs: " + team.teamCode());
            require(team.lineup().stream().map(RealMatchApiV1Dtos.OptionPlayer::position)
                            .collect(java.util.stream.Collectors.toSet())
                            .equals(EnumSet.allOf(Position.class)),
                    "Options position coverage differs: " + team.teamCode());
            require(team.lineup().stream().map(RealMatchApiV1Dtos.OptionPlayer::playerId)
                            .allMatch(allOptionPlayerIds::add),
                    "Options player identity is not unique: " + team.teamCode());
        }
        require(allOptionPlayerIds.size() == 50, "Options unique player count differs");

        require(RealMatchApiV1Dtos.REQUEST_SCHEMA.equals(request.schemaVersion())
                        && FIXED_BLUE.equals(request.blueTeamCode())
                        && FIXED_RED.equals(request.redTeamCode())
                        && FIXED_SEED.equals(request.seed()),
                "Fixed request identity differs");
        require(RealMatchApiV1Dtos.RESPONSE_SCHEMA.equals(response.schemaVersion())
                        && FIXED_SEED.equals(response.seed()),
                "Fixed response schema or seed differs");

        RealMatchApiV1Dtos.ProductionPolicy policy = options.productionPolicy();
        RealMatchApiV1Dtos.Integrity integrity = response.integrity();
        require("BASELINE_V1".equals(policy.runtimeProfileId())
                        && !policy.economyCandidateActivation()
                        && !policy.tempoCandidateActivation()
                        && SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION.equals(
                        policy.engineImplementationVersion())
                        && "MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V8".equals(
                        integrity.engineImplementationVersion()),
                "Current V8 production policy differs");
        require(policy.policyId().equals(integrity.policyId())
                        && policy.policyHash().equals(integrity.policyHash())
                        && policy.runtimeProfileId().equals(integrity.runtimeProfileId())
                        && policy.configurationHash().equals(integrity.configurationHash())
                        && policy.activeGameplayRulesVersion().equals(
                        integrity.activeGameplayRulesVersion())
                        && policy.engineImplementationVersion().equals(
                        integrity.engineImplementationVersion())
                        && policy.diagnosticsExcludedFromGameplayIdentity()
                        == integrity.diagnosticsExcludedFromGameplayIdentity(),
                "Options and fixed-response policy identity differ");
        require(options.resourceVersions().resourceProvenanceHash().equals(
                        integrity.resourceProvenanceHash())
                        && response.result().resourceProvenanceHash().equals(
                        integrity.resourceProvenanceHash())
                        && response.result().replayProvenanceHash().equals(
                        integrity.replayProvenanceHash()),
                "Resource or replay provenance differs");

        require(response.teams().size() == TeamSide.values().length,
                "Fixed response team presentation cardinality differs");
        Map<TeamSide, String> expectedTeamCodes = Map.of(
                TeamSide.BLUE, FIXED_BLUE, TeamSide.RED, FIXED_RED);
        Map<String, RealMatchApiV1Dtos.OptionPlayer> fixedRoster = new HashMap<>();
        Map<String, Player> authoritativePlayers = new HashMap<>();
        for (RealMatchApiV1Dtos.TeamPresentation team : response.teams()) {
            require(expectedTeamCodes.get(team.teamSide()).equals(team.teamCode()),
                    "Fixed response side/team identity differs");
            RealMatchApiV1Dtos.OptionTeam optionTeam = options.teams().stream()
                    .filter(value -> value.teamCode().equals(team.teamCode()))
                    .findFirst().orElseThrow();
            optionTeam.lineup().forEach(player -> fixedRoster.put(player.playerId(), player));
            teams.assemble(team.teamCode()).getPlayers().forEach(player ->
                    authoritativePlayers.put(player.requirePlayerId().value(), player));
            require(team.lineup().size() == Position.values().length,
                    "Fixed response lineup cardinality differs");
            for (RealMatchApiV1Dtos.PlayerPresentation player : team.lineup()) {
                RealMatchApiV1Dtos.OptionPlayer option = fixedRoster.get(player.playerId());
                require(option != null && option.position() == player.position(),
                        "Presentation player identity differs from authoritative options roster");
                require(player.championId().equals(player.champion().championId()),
                        "Presentation champion identity differs");
            }
        }
        require(fixedRoster.size() == 10, "Fixed response roster cardinality differs");

        RealMatchApiV1Dtos.Draft draft = response.draft();
        require(RealMatchApiV1Dtos.DRAFT_SCHEMA.equals(draft.schemaVersion())
                        && draft.seriesGameNumber() == 1
                        && draft.hardFearlessExclusionsBeforeDraft().isEmpty()
                        && draft.decisions().size() == 20
                        && draft.finalAssignments().size() == 10,
                "Fixed response Draft structure differs");
        for (int index = 0; index < draft.decisions().size(); index++) {
            require(draft.decisions().get(index).turn() == index + 1,
                    "Draft decisions are not in turn order");
        }

        Map<String, RealMatchApiV1Dtos.FinalAssignment> assignments = new HashMap<>();
        for (RealMatchApiV1Dtos.FinalAssignment assignment : draft.finalAssignments()) {
            require(assignments.put(assignment.playerId(), assignment) == null
                            && fixedRoster.containsKey(assignment.playerId()),
                    "Draft assignment player identity differs");
        }
        Map<String, RealMatchApiV1Dtos.PlayerPresentation> presentation = new HashMap<>();
        response.teams().forEach(team -> team.lineup().forEach(player ->
                presentation.put(player.playerId(), player)));
        Map<String, RealMatchApiV1Dtos.PlayerResult> results = new HashMap<>();
        for (RealMatchApiV1Dtos.PlayerResult player : response.result().players()) {
            RealMatchApiV1Dtos.FinalAssignment assignment = assignments.get(player.playerId());
            RealMatchApiV1Dtos.PlayerPresentation shown = presentation.get(player.playerId());
            require(results.put(player.playerId(), player) == null
                            && assignment != null && shown != null
                            && assignment.teamSide() == player.teamSide()
                            && assignment.position() == player.position()
                            && assignment.championId().equals(player.championId())
                            && shown.position() == player.position()
                            && shown.championId().equals(player.championId()),
                    "Draft, presentation and result player identity differ");
            require(player.abilityProfile() != null
                            && RealMatchApiV1Dtos.PlayerAbilityProfile.SCHEMA.equals(
                            player.abilityProfile().schemaVersion()),
                    "Current V8 player ability profile is missing");
            Player authoritative = authoritativePlayers.get(player.playerId());
            TreeMap<String, Integer> expectedBaseRatings = new TreeMap<>();
            authoritative.getRatings().asMap().forEach((skill, value) ->
                    expectedBaseRatings.put(skill.name(), value));
            int expectedProficiency = authoritative.getChampionProficiencies().get(
                    new ChampionRoleKey(
                            new ChampionId(player.championId()), player.position()));
            require(player.abilityProfile().baseRatings().equals(expectedBaseRatings)
                            && player.abilityProfile().selectedChampionProficiency()
                            == expectedProficiency,
                    "Current V8 player ability profile differs from authoritative resources");
        }
        require(results.size() == 10, "Fixed result player cardinality differs");

        require(response.result().durationSeconds() == response.timeline().durationSeconds()
                        && response.result().winner() == response.timeline().winner()
                        && response.result().endReason() == response.timeline().endReason()
                        && response.result().finalDraftHash().equals(draft.finalDraftHash())
                        && response.result().finalAssignmentHash().equals(
                        draft.finalAssignmentHash()),
                "Draft, result and timeline identity differ");
        require(!response.timeline().snapshots().isEmpty(),
                "Fixed timeline has no snapshots");
        RealMatchApiV1Dtos.Snapshot finalSnapshot = response.timeline().snapshots().getLast();
        require(finalSnapshot.timeSeconds() == response.result().durationSeconds(),
                "Final snapshot duration differs");
        Map<TeamSide, RealMatchApiV1Dtos.TeamState> finalTeams = Map.of(
                TeamSide.BLUE, finalSnapshot.blueTeam(),
                TeamSide.RED, finalSnapshot.redTeam());
        for (RealMatchApiV1Dtos.TeamResult team : response.result().teams()) {
            RealMatchApiV1Dtos.TeamState state = finalTeams.get(team.teamSide());
            require(state != null && state.teamIdentity().equals(team.teamIdentity())
                            && state.kills() == team.kills()
                            && state.gold() == team.totalGold()
                            && state.dragons() == team.dragons()
                            && state.hasDragonSoul() == team.hasDragonSoul()
                            && state.hasBaronBuff() == team.hasBaronBuff()
                            && state.hasElderBuff() == team.hasElderBuff()
                            && state.towersDestroyed() == team.towersDestroyed()
                            && state.inhibitorsRemaining() == team.inhibitorsRemaining()
                            && state.nexusTurretsRemaining() == team.nexusTurretsRemaining()
                            && state.nexusAlive() == team.nexusAlive()
                            && state.alivePlayers() == team.alivePlayers(),
                    "Final team result differs from final snapshot");
        }
        if (response.result().endReason() == GameEndReason.NEXUS_DESTROYED) {
            require(response.result().winner() != null
                            && response.result().teams().stream()
                            .filter(team -> team.teamSide() != response.result().winner())
                            .noneMatch(RealMatchApiV1Dtos.TeamResult::nexusAlive),
                    "Nexus winner/final team state differs");
        } else {
            require(response.result().winner() == null,
                    "Timeout result unexpectedly has a winner");
        }

        Map<String, RealMatchApiV1Dtos.PlayerState> finalPlayers = new HashMap<>();
        finalSnapshot.players().forEach(player -> finalPlayers.put(player.playerId(), player));
        require(finalPlayers.size() == 10, "Final snapshot player cardinality differs");
        for (RealMatchApiV1Dtos.PlayerResult result : response.result().players()) {
            RealMatchApiV1Dtos.PlayerState state = finalPlayers.get(result.playerId());
            require(state != null && state.teamSide() == result.teamSide()
                            && state.position() == result.position()
                            && state.championId().equals(result.championId())
                            && state.kills() == result.kills()
                            && state.deaths() == result.deaths()
                            && state.assists() == result.assists()
                            && state.cs() == result.cs()
                            && state.gold() == result.gold()
                            && state.totalExperience() == result.totalExperience()
                            && state.level() == result.level(),
                    "Final player result differs from final snapshot");
        }

        Set<String> rosterIds = Set.copyOf(results.keySet());
        for (RealMatchApiV1Dtos.Event event : response.timeline().events()) {
            require(isRosterParticipant(event.actorPlayerId(), rosterIds)
                            && isRosterParticipant(event.killerPlayerId(), rosterIds)
                            && isRosterParticipant(event.victimPlayerId(), rosterIds)
                            && rosterIds.containsAll(event.assistantPlayerIds()),
                    "Timeline event contains a participant outside the fixed roster");
        }
        requireHashes(draft.draftRuleSetHash(), draft.draftScoringPolicyHash(),
                draft.finalDraftHash(), draft.finalAssignmentHash(), policy.policyHash(),
                policy.configurationHash(), options.resourceVersions().resourceProvenanceHash(),
                integrity.inputHash(), integrity.resourceProvenanceHash(),
                integrity.replayProvenanceHash(), integrity.simulatorTimelineHash(),
                integrity.structuredTimelineHash(), integrity.outputHash(),
                integrity.randomFingerprint().randomTraceHash());
        require(integrity.randomFingerprint().randomDrawCount() >= 0,
                "Random fingerprint draw count is negative");
    }

    private static boolean isRosterParticipant(String playerId, Set<String> rosterIds) {
        return playerId == null || rosterIds.contains(playerId);
    }

    private static void requireHashes(String... values) {
        for (String value : values) {
            require(value != null && value.matches("[0-9a-f]{64}"),
                    "Current runtime evidence contains an invalid SHA-256 value");
        }
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

    private static FullRegressionResult fullRegression(
            Path directory,
            SourceTreeIdentity productionSource,
            SourceTreeIdentity verificationSource
    ) throws Exception {
        require(Files.isDirectory(directory), "Missing full regression XML directory");
        int suites = 0;
        int tests = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        LinkedHashMap<String, Integer> requiredSuiteTests = new LinkedHashMap<>();
        String expectedBinding = sourceBindingTestName(productionSource, verificationSource);
        boolean currentSourceBindingFound = false;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(value -> value.getFileName().toString()
                            .startsWith("TEST-") && value.toString().endsWith(".xml"))
                    .sorted().toList()) {
                Element suite = factory.newDocumentBuilder().parse(path.toFile())
                        .getDocumentElement();
                String suiteName = suite.getAttribute("name");
                int suiteTests = Integer.parseInt(suite.getAttribute("tests"));
                suites++;
                tests += suiteTests;
                failures += Integer.parseInt(suite.getAttribute("failures"));
                errors += Integer.parseInt(suite.getAttribute("errors"));
                skipped += Integer.parseInt(suite.getAttribute("skipped"));
                if (REQUIRED_FULL_REGRESSION_SUITES.containsKey(suiteName)) {
                    requiredSuiteTests.put(suiteName, suiteTests);
                }
                if (SOURCE_BINDING_SUITE.equals(suiteName)) {
                    org.w3c.dom.NodeList testCases = suite.getElementsByTagName("testcase");
                    for (int index = 0; index < testCases.getLength(); index++) {
                        Element testCase = (Element) testCases.item(index);
                        if (testCase.getAttribute("name").contains(expectedBinding)) {
                            currentSourceBindingFound = true;
                        }
                    }
                }
            }
        }
        require(suites >= MIN_FULL_REGRESSION_SUITES && tests >= MIN_FULL_REGRESSION_TESTS,
                "Test XML does not represent a complete backend regression");
        for (Map.Entry<String, Integer> required : REQUIRED_FULL_REGRESSION_SUITES.entrySet()) {
            Integer actual = requiredSuiteTests.get(required.getKey());
            require(actual != null && actual >= required.getValue(),
                    "Missing or stale required full-regression suite: " + required.getKey());
        }
        require(currentSourceBindingFound,
                "Full regression XML is not bound to the current production/API source trees");
        require(failures == 0 && errors == 0 && skipped == 0,
                "Full backend regression is not a clean unskipped pass");
        return new FullRegressionResult(
                "CLEAN_PASS_BOUND_TO_CURRENT_SOURCE", suites, tests, failures, errors, skipped,
                Map.copyOf(requiredSuiteTests),
                "REQUIRED_SUITES_AND_DYNAMIC_SOURCE_HASH_TESTCASE_VERIFIED",
                "gradlew.bat test --console=plain --no-daemon");
    }

    static SourceTreeIdentity productionSourceTree(Path backendRoot) throws IOException {
        return sourceTreeIdentity(
                backendRoot,
                List.of(Path.of("src", "main", "java"),
                        Path.of("src", "main", "resources")),
                List.of(Path.of("build.gradle"), Path.of("settings.gradle")),
                value -> true);
    }

    static SourceTreeIdentity verificationSourceTree(Path backendRoot) throws IOException {
        return sourceTreeIdentity(
                backendRoot,
                List.of(Path.of("src", "test", "java")),
                List.of(),
                value -> {
                    String name = value.getFileName().toString();
                    return name.startsWith("RealMatchApiV1")
                            || name.equals("RealDraftMatchOrchestratorTest.java")
                            || name.equals("MatchEngineV1ContractTest.java")
                            || name.equals("ChampionApiTest.java");
                });
    }

    static String sourceBindingTestName(
            SourceTreeIdentity productionSource,
            SourceTreeIdentity verificationSource
    ) {
        return "productionSourceTreeHash=" + productionSource.hash()
                + ";verificationSourceTreeHash=" + verificationSource.hash();
    }

    private static SourceTreeIdentity sourceTreeIdentity(
            Path backendRoot,
            List<Path> sourceRoots,
            List<Path> standaloneFiles,
            Predicate<Path> include
    ) throws IOException {
        Path normalizedRoot = backendRoot.toAbsolutePath().normalize();
        ArrayList<Path> files = new ArrayList<>();
        for (Path sourceRoot : sourceRoots) {
            Path directory = normalizedRoot.resolve(sourceRoot).normalize();
            require(directory.startsWith(normalizedRoot) && Files.isDirectory(directory),
                    "Missing source tree directory: " + sourceRoot);
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.filter(Files::isRegularFile).filter(include).forEach(files::add);
            }
        }
        for (Path standalone : standaloneFiles) {
            Path file = normalizedRoot.resolve(standalone).normalize();
            require(file.startsWith(normalizedRoot) && Files.isRegularFile(file),
                    "Missing source identity file: " + standalone);
            if (include.test(file)) files.add(file);
        }
        files.sort(Comparator.comparing(path -> relativePath(normalizedRoot, path)));
        StringBuilder canonical = new StringBuilder(
                "sourceTreeIdentitySchema=REAL_MATCH_API_V1_SOURCE_TREE_IDENTITY_V1\n");
        for (Path file : files) {
            canonical.append("file=").append(relativePath(normalizedRoot, file)).append('\n')
                    .append("rawSha256=").append(sha256(Files.readAllBytes(file))).append('\n');
        }
        return new SourceTreeIdentity(
                SOURCE_TREE_HASH_ALGORITHM,
                sha256(canonical.toString().getBytes(StandardCharsets.UTF_8)),
                files.size());
    }

    private static String relativePath(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString()
                .replace('\\', '/');
    }

    private static Map<String, Object> sourceEvidence(
            SourceTreeIdentity productionSource,
            SourceTreeIdentity verificationSource
    ) {
        return map(
                "productionSourceTree", productionSource,
                "verificationSourceTree", verificationSource,
                "binding", "FULL_REGRESSION_DYNAMIC_TESTCASE_EXACT_HASH");
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
            Map<String, Integer> requiredSuiteTestCounts,
            String sourceBinding,
            String command
    ) {
    }

    record SourceTreeIdentity(
            String hashAlgorithm,
            String hash,
            int fileCount
    ) {
    }
}
