package com.lolfm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.simulator.TeamSide;
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
import java.util.function.ToLongFunction;

/** Canonical artifact contract for Player Draft latency profiling V1. */
public final class PlayerDraftLatencyProfilingV1Artifacts {
    public static final String STATUS =
            "PLAYER_DRAFT_INTERACTIVE_AND_SIMULATION_LATENCY_PROFILE_CAPTURED";
    public static final String REVIEW_BASELINE_COMMIT =
            "b284a12ca82824ee543d12a99785660e87fab1fc";
    public static final String CONTRACT = "profiling-contract.json";
    public static final String ACTIONS = "interactive-action-runs.csv";
    public static final String AI_TURNS = "interactive-ai-turns.csv";
    public static final String SIMULATIONS = "simulation-runs.csv";
    public static final String BROWSER = "browser-runs.csv";
    public static final String SUMMARY = "phase-summary.json";
    public static final String HOTSPOTS = "hotspots.json";
    public static final String RECOMMENDATION = "recommendation.json";
    public static final String ANALYSIS = "analysis.md";
    public static final String MANIFEST = "SHA256SUMS.txt";
    public static final List<String> ARTIFACTS = List.of(
            CONTRACT, ACTIONS, AI_TURNS, SIMULATIONS, BROWSER, SUMMARY,
            HOTSPOTS, RECOMMENDATION, ANALYSIS);

    private PlayerDraftLatencyProfilingV1Artifacts() { }

    public static void writeOfficial(
            Path output,
            List<PlayerDraftLatencyProfilingV1Harness.FlowObservation> flows,
            List<JsonNode> browserRuns,
            PlayerDraftLatencyJfrSamplerV1.Profile jfr,
            Environment environment,
            MatchEngineV1Canonicalizer canonicalizer
    ) throws IOException {
        validate(flows, browserRuns, jfr, environment);
        Path normalized = output.toAbsolutePath().normalize();
        require(normalized.endsWith(Path.of("build", "reports",
                        "player-draft-interactive-simulation-latency-profiling-v1")),
                "Unexpected official output directory");
        Files.createDirectories(normalized);
        try (var files = Files.list(normalized)) {
            require(files.findAny().isEmpty(), "Official output directory must be fresh");
        }

        Summary summary = summarize(flows, browserRuns);
        Recommendation recommendation = recommend(flows, browserRuns);
        writeJson(normalized.resolve(CONTRACT), contract(flows, browserRuns, environment),
                canonicalizer);
        Files.writeString(normalized.resolve(ACTIONS), actionCsv(flows),
                StandardCharsets.UTF_8);
        Files.writeString(normalized.resolve(AI_TURNS), aiCsv(flows),
                StandardCharsets.UTF_8);
        Files.writeString(normalized.resolve(SIMULATIONS), simulationCsv(flows),
                StandardCharsets.UTF_8);
        Files.writeString(normalized.resolve(BROWSER), browserCsv(browserRuns),
                StandardCharsets.UTF_8);
        writeJson(normalized.resolve(SUMMARY), summary, canonicalizer);
        writeJson(normalized.resolve(HOTSPOTS), hotspots(jfr, flows), canonicalizer);
        writeJson(normalized.resolve(RECOMMENDATION), recommendation, canonicalizer);
        Files.writeString(normalized.resolve(ANALYSIS), analysis(summary, recommendation),
                StandardCharsets.UTF_8);
        writeManifest(normalized);
        verifyManifest(normalized);
    }

    static Map<String, Object> contract(
            List<PlayerDraftLatencyProfilingV1Harness.FlowObservation> flows,
            List<JsonNode> browserRuns,
            Environment environment
    ) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", "PLAYER_DRAFT_LATENCY_PROFILING_CONTRACT_V1");
        value.put("status", STATUS);
        value.put("reviewBaselineCommit", REVIEW_BASELINE_COMMIT);
        value.put("source", environment);
        value.put("schedule", Map.of(
                "fixture", "GEN_VS_T1_SEED_73",
                "controlledSides", List.of("BLUE", "RED"),
                "freshServerBrowserRunsPerSide", 1,
                "warmedCompleteHarnessRunsPerSide", 2,
                "profilingOnOffExactParityPairs", 1,
                "largePopulationBalanceAudit", false));
        value.put("flowIdentities", flows.stream().map(flow -> Map.of(
                "runId", flow.runId(),
                "runKind", flow.runKind().name(),
                "controlledSide", flow.controlledSide().name(),
                "actionScript", flow.actionScript(),
                "actionScriptHash", actionScriptHash(flow.actionScript()),
                "draftIdentity", flow.finalProgress().result().draftIdentity(),
                "outputHash", flow.simulation().outputHash(),
                "randomTraceHash", flow.simulation().randomTraceHash())).toList());
        value.put("browserIdentities", browserRuns.stream().map(run -> Map.of(
                "runId", text(run, "runId"),
                "controlledSide", text(run, "controlledSide"),
                "actionScriptHash", text(run, "actionScriptHash"),
                "browserCache", text(run, "browserCache"),
                "backendMode", text(run, "backendMode"),
                "frontendMode", text(run, "frontendMode"))).toList());
        value.put("interactiveBackendBoundaries", List.of(
                "PRODUCTION_EQUIVALENT_PLAYER_LEGALITY_VIEW",
                "PLAYER_APPLY_AND_EVIDENCE",
                "AI_FOLLOW_UP_TOTAL_AND_PER_DECISION",
                "EXACT_REPLAY_REPOSITORY_LOOKUP_LOCK_IDEMPOTENCY_CLOSEST_BOUNDARY",
                "SERVICE_TOTAL", "RESPONSE_PROJECTION", "JACKSON_SERIALIZATION",
                "OFFLINE_GZIP_CLOSEST_BOUNDARY"));
        value.put("simulationBackendBoundaries", List.of(
                "SESSION_LOOKUP_AND_COMPLETED_VALIDATION",
                "AUTHORITATIVE_ROSTER_META_LEGAL_ROLE_CONTROL_AUTHORITY_INPUT_VALIDATION",
                "PRODUCTION_V9_MATCH_ENGINE", "OUTPUT_INTEGRITY_PROVENANCE_VALIDATION",
                "COMPACT_RECEIPT", "RESPONSE_PROJECTION", "JACKSON_SERIALIZATION",
                "OFFLINE_GZIP_CLOSEST_BOUNDARY", "SERVICE_FIRST", "EXACT_RETRY"));
        value.put("browserBoundaries", List.of(
                "CONFIRM_OR_SIMULATE_CLICK", "FETCH_START", "CDP_RESPONSE_HEADERS_TTFB",
                "BODY_COMPLETE", "JSON_PARSE", "STRICT_RUNTIME_VALIDATION",
                "NORMALIZATION_WHEN_APPLICABLE", "REACT_STATE", "STRUCTURED_DOM_STABLE"));
        value.put("timingSemantics", Map.ofEntries(
                Map.entry("clock", "System.nanoTime/backend and performance.now/browser"),
                Map.entry("requestUploadComplete", "CDP requestWillBeSent closest observable boundary"),
                Map.entry("httpWriteComplete", "CDP loadingFinished closest observable boundary"),
                Map.entry("backendDecomposition", "separate exact semantic replay, not nested production timers"),
                Map.entry("repositoryLock", "exact idempotent replay is the closest uncontended lookup/lock boundary"),
                Map.entry("percentiles", "nearest-rank p90; small-sample descriptive only"),
                Map.entry("acceptanceThreshold", "NONE_ENVIRONMENT_SPECIFIC_OBSERVATION")));
        value.put("profilingIsolation", List.of(
                "No timing field in public request or response DTO",
                "No gameplay or Draft Random consumed by diagnostics",
                "Frontend hook is synchronous no-op without Playwright observer",
                "Timing values are excluded from gameplay, replay and output hashes"));
        value.put("nonGoals", List.of(
                "OPTIMIZATION", "CACHE_CHANGE", "SEARCH_OR_TUNING_CHANGE",
                "GAMEPLAY_OR_RANDOM_CHANGE", "API_SCHEMA_CHANGE", "PAYLOAD_COMPACTION",
                "ASYNC_JOB", "UI_OR_ACCESSIBILITY_CHANGE"));
        return Map.copyOf(value);
    }

    static Summary summarize(
            List<PlayerDraftLatencyProfilingV1Harness.FlowObservation> flows,
            List<JsonNode> browserRuns
    ) {
        List<PlayerDraftLatencyProfilingV1Harness.ActionObservation> actions = flows.stream()
                .flatMap(flow -> flow.actions().stream()).toList();
        List<PlayerDraftLatencyProfilingV1Harness.AiTurnObservation> ai = flows.stream()
                .flatMap(flow -> flow.aiTurns().stream()).toList();
        List<PlayerDraftLatencyProfilingV1Harness.SimulationObservation> simulations = flows
                .stream().map(PlayerDraftLatencyProfilingV1Harness.FlowObservation::simulation)
                .toList();
        return new Summary(
                "PLAYER_DRAFT_LATENCY_PHASE_SUMMARY_V1", STATUS,
                flows.size(), actions.size(), ai.size(), simulations.size(),
                browserRuns.size(), browserRows(browserRuns).size(),
                distribution(actions, value -> value.backendServiceTotalNanos()),
                distribution(actions, value -> value.aiFollowUpTotalNanos()),
                distribution(actions, value -> value.responseProjectionNanos()),
                distribution(actions, value -> value.jsonSerializationNanos()),
                groupedActions(actions, "AI_DECISION_COUNT"),
                groupedActions(actions, "ACTION_TYPE"),
                groupedActions(actions, "DRAFT_STAGE"),
                distribution(ai, value -> value.elapsedNanos()),
                distribution(simulations, value -> value.serviceTotalNanos()),
                distribution(simulations, value -> value.matchEngineNanos()),
                distribution(simulations, value -> value.responseProjectionNanos()),
                distribution(simulations, value -> value.jsonSerializationNanos()),
                distribution(simulations, value -> value.exactRetryNanos()),
                browserDistribution(browserRuns, "ACTION"),
                browserDistribution(browserRuns, "SIMULATION"),
                "Nearest-rank p90 and median are descriptive only because the deterministic schedule is intentionally small.");
    }

    static Recommendation recommend(
            List<PlayerDraftLatencyProfilingV1Harness.FlowObservation> flows,
            List<JsonNode> browserRuns
    ) {
        List<PlayerDraftLatencyProfilingV1Harness.ActionObservation> actions = flows.stream()
                .flatMap(flow -> flow.actions().stream()).toList();
        List<PlayerDraftLatencyProfilingV1Harness.SimulationObservation> simulations = flows
                .stream().map(PlayerDraftLatencyProfilingV1Harness.FlowObservation::simulation)
                .toList();
        Map<String, Long> actionPhases = Map.of(
                "AI_FOLLOW_UP", median(actions, value -> value.aiFollowUpTotalNanos()),
                "RESPONSE_PROJECTION", median(actions, value -> value.responseProjectionNanos()),
                "JACKSON_SERIALIZATION", median(actions, value -> value.jsonSerializationNanos()),
                "REPOSITORY_LOOKUP_LOCK_IDEMPOTENCY_CLOSEST",
                median(actions, value -> value.exactReplayRepositoryBoundaryNanos()));
        Map<String, Long> simulationPhases = Map.of(
                "MATCH_ENGINE_V9", median(simulations, value -> value.matchEngineNanos()),
                "INPUT_VALIDATION", median(simulations, value -> value.inputValidationNanos()),
                "OUTPUT_INTEGRITY", median(simulations, value -> value.outputIntegrityNanos()),
                "RESPONSE_PROJECTION", median(simulations, value -> value.responseProjectionNanos()),
                "JACKSON_SERIALIZATION", median(simulations, value -> value.jsonSerializationNanos()));
        String actionPrimary = largest(actionPhases);
        String simulationPrimary = largest(simulationPhases);
        String interactiveNext = actionPrimary.equals("AI_FOLLOW_UP")
                ? "PLAYER_DRAFT_AI_TURN_PERFORMANCE_HARDENING_V1"
                : "PLAYER_DRAFT_SESSION_PROJECTION_PERFORMANCE_HARDENING_V1";
        String simulationNext = "PLAYER_DRAFT_SIMULATION_INPUT_VALIDATION_PERFORMANCE_HARDENING_V1";
        if (simulationPrimary.equals("MATCH_ENGINE_V9")) {
            simulationNext = "MATCH_ENGINE_V9_EXACT_OUTPUT_HOT_PATH_HARDENING";
        } else if (simulationPrimary.equals("RESPONSE_PROJECTION")
                || simulationPrimary.equals("JACKSON_SERIALIZATION")) {
            simulationNext = "PLAYER_DRAFT_SIMULATION_PROJECTION_SERIALIZATION_HARDENING_V1";
        }
        String next = simulationPhases.get(simulationPrimary) >= actionPhases.get(actionPrimary)
                ? simulationNext + "+" + interactiveNext
                : interactiveNext + "+" + simulationNext;
        return new Recommendation(
                "PLAYER_DRAFT_LATENCY_RECOMMENDATION_V1", STATUS,
                actionPrimary, actionPhases, simulationPrimary, simulationPhases,
                browserDistribution(browserRuns, "ACTION"),
                browserDistribution(browserRuns, "SIMULATION"), next,
                "MEASURED_PHASE_TIMING_PLUS_EXACT_COUNTERS_PLUS_JFR_SAMPLING",
                List.of(
                        "This milestone applies no optimization and promises no unmeasured speedup.",
                        "Engine compute reduction changes actual latency; async progress changes perceived latency and adds lifecycle/cancel/recovery complexity.",
                        "Payload compaction can reduce transfer/parse cost but must define which timeline details are delayed or omitted."));
    }

    static String actionCsv(List<PlayerDraftLatencyProfilingV1Harness.FlowObservation> flows) {
        StringBuilder csv = new StringBuilder("runId,runKind,controlledSide,playerActionIndex,playerTurn,draftStage,actionType,championId,decisionCountBefore,decisionCountAfter,aiDecisionCount,playerLegalityViewNanos,playerApplyEvidenceNanos,aiFollowUpTotalNanos,completionNanos,backendServiceTotalNanos,exactReplayRepositoryBoundaryNanos,responseProjectionNanos,jsonSerializationNanos,offlineGzipNanos,decodedJsonBytes,offlineGzipBytes,resultingStatus,stateHash,completedDraftIdentity,actionScriptHash\n");
        for (var flow : flows) for (var value : flow.actions()) {
            appendCsv(csv, value.runId(), value.runKind(), value.controlledSide(),
                    value.playerActionIndex(), value.playerTurn(), stage(value.playerTurn()),
                    value.actionType(), value.championId(), value.decisionCountBefore(),
                    value.decisionCountAfter(), value.aiDecisionCount(),
                    value.playerLegalityViewNanos(), value.playerApplyEvidenceNanos(),
                    value.aiFollowUpTotalNanos(), value.completionNanos(),
                    value.backendServiceTotalNanos(),
                    value.exactReplayRepositoryBoundaryNanos(),
                    value.responseProjectionNanos(), value.jsonSerializationNanos(),
                    value.offlineGzipNanos(), value.decodedJsonBytes(),
                    value.offlineGzipBytes(), value.resultingStatus(), value.stateHash(),
                    value.completedDraftIdentity(), actionScriptHash(flow.actionScript()));
        }
        return csv.toString();
    }

    static String aiCsv(List<PlayerDraftLatencyProfilingV1Harness.FlowObservation> flows) {
        StringBuilder csv = new StringBuilder("runId,runKind,controlledSide,playerActionIndex,aiDecisionIndex,aiTurn,draftStage,aiSide,actionType,championId,candidateEvaluationCount,elapsedNanos,plannerCandidatePhysicalComputations,roleAssignmentPhysicalComputations,completionPhysicalComputations,poolHealthPhysicalComputations,rolePositionPhysicalComputations,peakCacheEntries,actionScriptHash\n");
        for (var flow : flows) for (var value : flow.aiTurns()) {
            appendCsv(csv, value.runId(), value.runKind(), value.controlledSide(),
                    value.playerActionIndex(), value.aiDecisionIndex(), value.aiTurn(),
                    stage(value.aiTurn()), value.aiSide(), value.actionType(),
                    value.championId(), value.candidateEvaluationCount(),
                    value.elapsedNanos(), value.plannerCandidatePhysicalComputations(),
                    value.roleAssignmentPhysicalComputations(),
                    value.completionPhysicalComputations(),
                    value.poolHealthPhysicalComputations(),
                    value.rolePositionPhysicalComputations(), value.peakCacheEntries(),
                    actionScriptHash(flow.actionScript()));
        }
        return csv.toString();
    }

    static String simulationCsv(List<PlayerDraftLatencyProfilingV1Harness.FlowObservation> flows) {
        StringBuilder csv = new StringBuilder("runId,runKind,controlledSide,requestKind,lookupAndCompletedValidationNanos,inputValidationNanos,matchEngineNanos,outputIntegrityNanos,receiptNanos,responseProjectionNanos,jsonSerializationNanos,offlineGzipNanos,totalNanos,decodedJsonBytes,offlineGzipBytes,eventCount,snapshotCount,receiptBytes,winner,durationSeconds,inputHash,replayProvenanceHash,simulatorTimelineHash,structuredTimelineHash,outputHash,randomDrawCount,randomTraceHash,responseCanonicalHash,actionScriptHash,retrySemantics\n");
        for (var flow : flows) {
            var value = flow.simulation();
            appendSimulation(csv, flow, value, "FIRST", value.serviceTotalNanos(),
                    "FRESH_PRODUCTION_V9_EXECUTION_AND_COMPACT_RECEIPT_STORE");
            appendSimulation(csv, flow, value, "EXACT_RETRY", value.exactRetryNanos(),
                    "FRESH_PRODUCTION_V9_REEXECUTION_THEN_COMPACT_RECEIPT_EQUALITY_CHECK");
        }
        return csv.toString();
    }

    private static void appendSimulation(StringBuilder csv,
            PlayerDraftLatencyProfilingV1Harness.FlowObservation flow,
            PlayerDraftLatencyProfilingV1Harness.SimulationObservation value,
            String requestKind, long total, String retrySemantics) {
        boolean first = requestKind.equals("FIRST");
        appendCsv(csv, value.runId(), value.runKind(), value.controlledSide(), requestKind,
                first ? value.lookupAndCompletedValidationNanos() : "NOT_SEPARATELY_PROBED",
                first ? value.inputValidationNanos() : "NOT_SEPARATELY_PROBED",
                first ? value.matchEngineNanos() : "NOT_SEPARATELY_PROBED",
                first ? value.outputIntegrityNanos() : "NOT_SEPARATELY_PROBED",
                first ? value.receiptNanos() : "NOT_SEPARATELY_PROBED",
                first ? value.responseProjectionNanos() : "NOT_SEPARATELY_PROBED",
                first ? value.jsonSerializationNanos() : "NOT_SEPARATELY_PROBED",
                first ? value.offlineGzipNanos() : "NOT_SEPARATELY_PROBED", total,
                value.decodedJsonBytes(), value.offlineGzipBytes(), value.eventCount(),
                value.snapshotCount(), value.receiptBytes(), value.winner(),
                value.durationSeconds(), value.inputHash(), value.replayProvenanceHash(),
                value.simulatorTimelineHash(), value.structuredTimelineHash(),
                value.outputHash(), value.randomDrawCount(), value.randomTraceHash(),
                value.responseCanonicalHash(), actionScriptHash(flow.actionScript()),
                retrySemantics);
    }

    static String browserCsv(List<JsonNode> runs) {
        StringBuilder csv = new StringBuilder("runId,runKind,controlledSide,operation,correlationId,actionIndex,playerTurn,actionType,aiDecisionCount,confirmOrClickToFetchMs,fetchToHeadersMs,headersToBodyCompleteMs,cdpRequestToHeadersMs,cdpHeadersToLoadingFinishedMs,jsonParseMs,runtimeValidationMs,commonSemanticValidationMs,envelopeAndRemainingValidationMs,normalizationMs,stateUpdateMs,stateToDomMs,totalToDomMs,httpStatus,contentEncoding,encodedBodyBytes,decodedJsonBytes,eventCount,snapshotCount,browserCache,backendMode,frontendMode,consoleErrorCount,pageErrorCount,referenceFallbackCount,actionScriptHash\n");
        for (JsonNode run : runs) for (JsonNode row : run.path("rows")) {
            appendCsv(csv, text(run, "runId"), text(run, "runKind"),
                    text(run, "controlledSide"), text(row, "operation"),
                    text(row, "correlationId"), numberOrBlank(row, "actionIndex"),
                    numberOrBlank(row, "playerTurn"), text(row, "actionType"),
                    numberOrBlank(row, "aiDecisionCount"), numberOrBlank(row, "confirmOrClickToFetchMs"),
                    numberOrBlank(row, "fetchToHeadersMs"), numberOrBlank(row, "headersToBodyCompleteMs"),
                    numberOrBlank(row, "cdpRequestToHeadersMs"), numberOrBlank(row, "cdpHeadersToLoadingFinishedMs"),
                    numberOrBlank(row, "jsonParseMs"), numberOrBlank(row, "runtimeValidationMs"),
                    numberOrBlank(row, "commonSemanticValidationMs"), numberOrBlank(row, "envelopeAndRemainingValidationMs"),
                    numberOrBlank(row, "normalizationMs"), numberOrBlank(row, "stateUpdateMs"),
                    numberOrBlank(row, "stateToDomMs"), numberOrBlank(row, "totalToDomMs"),
                    numberOrBlank(row, "httpStatus"), text(row, "contentEncoding"),
                    numberOrBlank(row, "encodedBodyBytes"), numberOrBlank(row, "decodedJsonBytes"),
                    numberOrBlank(row, "eventCount"), numberOrBlank(row, "snapshotCount"),
                    text(run, "browserCache"), text(run, "backendMode"),
                    text(run, "frontendMode"), run.path("consoleErrors").size(),
                    run.path("pageErrors").size(), run.path("referenceFallbackCount").asInt(),
                    text(run, "actionScriptHash"));
        }
        return csv.toString();
    }

    public static void writeManifest(Path output) throws IOException {
        StringBuilder manifest = new StringBuilder();
        for (String artifact : ARTIFACTS) {
            manifest.append(sha256(Files.readAllBytes(output.resolve(artifact))))
                    .append("  ").append(artifact).append('\n');
        }
        Files.writeString(output.resolve(MANIFEST), manifest, StandardCharsets.UTF_8);
    }

    public static void verifyManifest(Path output) throws IOException {
        List<String> lines = Files.readAllLines(output.resolve(MANIFEST), StandardCharsets.UTF_8);
        require(lines.size() == ARTIFACTS.size(), "Manifest entry count mismatch");
        for (int index = 0; index < ARTIFACTS.size(); index++) {
            String artifact = ARTIFACTS.get(index);
            String expected = sha256(Files.readAllBytes(output.resolve(artifact)))
                    + "  " + artifact;
            require(lines.get(index).equals(expected), "Manifest drift: " + artifact);
        }
    }

    public static String actionScriptHash(List<String> script) {
        return sha256(String.join("\n", script).getBytes(StandardCharsets.UTF_8));
    }

    private static void validate(
            List<PlayerDraftLatencyProfilingV1Harness.FlowObservation> flows,
            List<JsonNode> browserRuns,
            PlayerDraftLatencyJfrSamplerV1.Profile jfr,
            Environment environment
    ) {
        require(environment.currentHead().matches("[0-9a-f]{40}"), "Missing current HEAD");
        require(environment.reviewBaselineCommit().equals(REVIEW_BASELINE_COMMIT),
                "Review baseline mismatch");
        require(flows.stream().filter(value -> value.runKind()
                == PlayerDraftLatencyProfilingV1Harness.RunKind.WARM
                && value.controlledSide() == TeamSide.BLUE).count() >= 2,
                "Missing two warm BLUE flows");
        require(flows.stream().filter(value -> value.runKind()
                == PlayerDraftLatencyProfilingV1Harness.RunKind.WARM
                && value.controlledSide() == TeamSide.RED).count() >= 2,
                "Missing two warm RED flows");
        require(flows.stream().anyMatch(value -> value.runKind()
                == PlayerDraftLatencyProfilingV1Harness.RunKind.COLD),
                "Missing direct cold flow");
        for (var flow : flows) {
            require(flow.actionScript().size() == 10 && flow.actions().size() == 10,
                    "Complete Player Draft action cardinality mismatch");
            long initialAiDecisions = flow.actions().getFirst().decisionCountBefore();
            require(initialAiDecisions + flow.aiTurns().size() == 10,
                    "Initial plus post-action AI turn cardinality mismatch");
            require(flow.actions().stream().noneMatch(value -> value.backendServiceTotalNanos() < 0
                    || value.aiFollowUpTotalNanos() < 0
                    || value.responseProjectionNanos() < 0), "Negative action phase");
            require(flow.simulation().serviceTotalNanos() >= 0
                    && flow.simulation().exactRetryNanos() >= 0, "Negative simulation phase");
        }
        require(browserRuns.size() >= 2, "Missing browser BLUE/RED flows");
        for (TeamSide side : TeamSide.values()) {
            require(browserRuns.stream().anyMatch(run -> text(run, "controlledSide")
                    .equals(side.name()) && text(run, "runKind").equals("COLD")),
                    "Missing cold browser side " + side);
        }
        for (JsonNode run : browserRuns) {
            require(run.path("rows").size() == 11, "Browser flow must contain 10 actions + simulate");
            require(run.path("consoleErrors").isEmpty() && run.path("pageErrors").isEmpty()
                    && run.path("referenceFallbackCount").asInt(-1) == 0,
                    "Browser runtime errors or fallback detected");
            require(text(run, "browserCache").equals("DISABLED"), "Browser cache not disabled");
            require(text(run, "actionScriptHash").equals(actionScriptHash(
                    strings(run.path("actionScript")))), "Browser action script hash mismatch");
            for (JsonNode row : run.path("rows")) {
                require(row.path("totalToDomMs").asDouble(-1) >= 0,
                        "Missing browser DOM timing");
                require(row.path("httpStatus").asInt() == 200,
                        "Browser request failed");
            }
        }
        require(jfr.relevantExecutionSamples() > 0, "JFR has no relevant CPU samples");
        require(jfr.relevantAllocationSamples() > 0, "JFR has no relevant allocation samples");
    }

    private static Map<String, Distribution> groupedActions(
            List<PlayerDraftLatencyProfilingV1Harness.ActionObservation> actions,
            String grouping
    ) {
        LinkedHashMap<String, Distribution> result = new LinkedHashMap<>();
        List<String> keys = switch (grouping) {
            case "AI_DECISION_COUNT" -> actions.stream().map(value ->
                    Integer.toString(value.aiDecisionCount())).distinct().sorted().toList();
            case "ACTION_TYPE" -> List.of("BAN", "PICK");
            case "DRAFT_STAGE" -> List.of("EARLY_1_7", "MID_8_14", "LATE_15_20");
            default -> throw new IllegalArgumentException(grouping);
        };
        for (String key : keys) {
            List<PlayerDraftLatencyProfilingV1Harness.ActionObservation> selected = actions
                    .stream().filter(value -> switch (grouping) {
                        case "AI_DECISION_COUNT" -> Integer.toString(value.aiDecisionCount()).equals(key);
                        case "ACTION_TYPE" -> value.actionType().equals(key);
                        case "DRAFT_STAGE" -> stage(value.playerTurn()).equals(key);
                        default -> false;
                    }).toList();
            if (!selected.isEmpty()) result.put(key,
                    distribution(selected, value -> value.backendServiceTotalNanos()));
        }
        return Map.copyOf(result);
    }

    private static Distribution browserDistribution(List<JsonNode> runs, String operation) {
        List<Long> values = browserRows(runs).stream()
                .filter(row -> text(row, "operation").equals(operation))
                .map(row -> Math.round(row.path("totalToDomMs").asDouble() * 1_000_000.0))
                .sorted().toList();
        return distributionLong(values);
    }

    private static List<JsonNode> browserRows(List<JsonNode> runs) {
        ArrayList<JsonNode> rows = new ArrayList<>();
        for (JsonNode run : runs) run.path("rows").forEach(rows::add);
        return List.copyOf(rows);
    }

    private static <T> Distribution distribution(List<T> values, ToLongFunction<T> extractor) {
        return distributionLong(values.stream().mapToLong(extractor).sorted().boxed().toList());
    }

    private static <T> long median(List<T> values, ToLongFunction<T> extractor) {
        return distribution(values, extractor).medianNanos();
    }

    private static Distribution distributionLong(List<Long> sorted) {
        require(!sorted.isEmpty(), "Cannot summarize empty timing list");
        int middle = sorted.size() / 2;
        long median = sorted.size() % 2 == 1 ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2L;
        int p90 = Math.max(0, (int) Math.ceil(sorted.size() * 0.90) - 1);
        return new Distribution(sorted.getFirst(), median, sorted.get(p90),
                sorted.getLast(), sorted.size(),
                "NANOSECONDS_NEAREST_RANK_P90_MIDDLE_MEDIAN_SMALL_SAMPLE_DESCRIPTIVE_ONLY");
    }

    private static String largest(Map<String, Long> phases) {
        return phases.entrySet().stream().max(Map.Entry.<String, Long>comparingByValue()
                .thenComparing(Map.Entry::getKey)).orElseThrow().getKey();
    }

    private static Map<String, Object> hotspots(
            PlayerDraftLatencyJfrSamplerV1.Profile jfr,
            List<PlayerDraftLatencyProfilingV1Harness.FlowObservation> flows
    ) {
        return Map.of(
                "schemaVersion", "PLAYER_DRAFT_LATENCY_HOTSPOTS_V1",
                "evidenceClass", "JFR_SAMPLING_PLUS_EXACT_DRAFT_COUNTERS",
                "profilerOverheadPresent", true,
                "exactCpuPercentageOrAllocationTotal", false,
                "jfr", jfr,
                "exactAiDecisionCounters", flows.stream().flatMap(flow -> flow.aiTurns().stream())
                        .map(value -> Map.of(
                                "runId", value.runId(), "aiTurn", value.aiTurn(),
                                "candidateEvaluations", value.candidateEvaluationCount(),
                                "plannerComputations", value.plannerCandidatePhysicalComputations(),
                                "roleAssignmentComputations", value.roleAssignmentPhysicalComputations(),
                                "completionComputations", value.completionPhysicalComputations(),
                                "poolHealthComputations", value.poolHealthPhysicalComputations(),
                                "rolePositionComputations", value.rolePositionPhysicalComputations(),
                                "peakCacheEntries", value.peakCacheEntries())).toList());
    }

    private static String analysis(Summary summary, Recommendation recommendation) {
        return """
                # Player Draft interactive and simulation latency profiling V1

                Status: `%s`

                이 결과는 최적화가 아니라 current-source 원인 분해다. 기존 LIVE의 2~3초 action과
                약 10초 simulate 관측은 stopwatch 기준 시작 가설이었고, 이번에는 마지막 Draft action과
                명시적 `/simulate`를 서로 다른 요청으로 측정했다.

                Warm backend action %d건의 service 중앙값은 %.3fms, AI follow-up 중앙값은 %.3fms,
                response projection 중앙값은 %.3fms, Jackson 중앙값은 %.3fms였다. action의 가장 큰
                관측 phase는 `%s`다. AI 0/1/2 decision, BAN/PICK, early/mid/late 분포는
                `phase-summary.json`과 raw CSV에 분리되어 있다.

                Completed Draft simulation %d건의 first service 중앙값은 %.3fms, Production V9 자체
                중앙값은 %.3fms, response projection 중앙값은 %.3fms, Jackson 중앙값은 %.3fms였다.
                exact retry는 compact receipt를 즉시 반환하는 cache가 아니라 Production V9을 fresh
                재실행하고 compact receipt equality를 확인한다. simulation의 가장 큰 phase는 `%s`다.

                Actual Chromium cold BLUE/RED는 각각 10 action + 1 simulate를 LIVE endpoint로 수행했다.
                browser cache는 disabled였고, application performance mark와 CDP network 경계를 구분했다.
                action DOM 중앙값은 %.3fms, simulate click-to-playback DOM 중앙값은 %.3fms다. encoded
                wire bytes와 decoded JSON bytes는 별도 raw 열이며 gzip 감소를 parse 개선으로 해석하지 않는다.

                JFR은 10ms CPU sampling과 allocation sampling의 hotspot 후보만 제공한다. sample weight는
                CPU 비율이나 정확한 allocation 총량이 아니다. Draft 계산의 물리 counter는 별도의 exact
                structured counter이고, wall-clock phase와 함께 해석했다.

                profiling ON/OFF는 Draft decision/authority/final assignment/evidence, Match Engine input,
                replay/timeline/Random/output identity를 exact 비교했다. public API timing field, tuning,
                cache, gameplay, Random, UI 동작은 바꾸지 않았다.

                다음 권장 milestone은 `%s`다. 계산 hot path를 줄이는 것은 actual latency 개선이고,
                async progress는 perceived latency를 바꾸지만 lifecycle/cancel/recovery 복잡성을 추가한다.
                compact payload는 transfer/parse를 줄일 수 있지만 지연하거나 생략할 timeline 정보 계약이
                먼저 필요하다. 이번 결과만으로 개선율을 약속하지 않는다.
                """.formatted(summary.status(), summary.actionObservationCount(),
                ms(summary.actionService().medianNanos()), ms(summary.actionAiFollowUp().medianNanos()),
                ms(summary.actionResponseProjection().medianNanos()),
                ms(summary.actionJsonSerialization().medianNanos()),
                recommendation.interactivePrimaryPhase(), summary.simulationObservationCount(),
                ms(summary.simulationServiceFirst().medianNanos()),
                ms(summary.simulationMatchEngine().medianNanos()),
                ms(summary.simulationResponseProjection().medianNanos()),
                ms(summary.simulationJsonSerialization().medianNanos()),
                recommendation.simulationPrimaryPhase(),
                ms(summary.browserActionTotalToDom().medianNanos()),
                ms(summary.browserSimulationTotalToDom().medianNanos()),
                recommendation.nextMilestone());
    }

    private static String stage(int turn) {
        if (turn <= 7) return "EARLY_1_7";
        if (turn <= 14) return "MID_8_14";
        return "LATE_15_20";
    }

    private static List<String> strings(JsonNode node) {
        ArrayList<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private static String numberOrBlank(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asText() : "";
    }

    private static void writeJson(Path file, Object value,
                                  MatchEngineV1Canonicalizer canonicalizer)
            throws IOException {
        Files.writeString(file, canonicalizer.canonicalJson(value) + '\n',
                StandardCharsets.UTF_8);
    }

    private static void appendCsv(StringBuilder target, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) target.append(',');
            target.append('"').append(String.valueOf(values[index])
                    .replace("\"", "\"\"")).append('"');
        }
        target.append('\n');
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public record Environment(
            String currentHead,
            String reviewBaselineCommit,
            String sourceTreeIdentity,
            String sourceIdentitySemantics,
            String javaVersion,
            String javaVendor,
            String javaVmName,
            String tieredCompilation,
            String osName,
            String osVersion,
            String osArch,
            int logicalProcessors,
            long maxHeapBytes,
            String backendHarnessMode,
            String browserBackendMode,
            String frontendMode,
            String cpuContention,
            Map<String, String> verifiedHistoricalManifestRawSha256
    ) { }

    public record Distribution(long minNanos, long medianNanos, long p90Nanos,
                               long maxNanos, int count, String semantics) { }

    public record Summary(
            String schemaVersion,
            String status,
            int flowCount,
            int actionObservationCount,
            int aiTurnObservationCount,
            int simulationObservationCount,
            int browserFlowCount,
            int browserRowCount,
            Distribution actionService,
            Distribution actionAiFollowUp,
            Distribution actionResponseProjection,
            Distribution actionJsonSerialization,
            Map<String, Distribution> actionServiceByAiDecisionCount,
            Map<String, Distribution> actionServiceByActionType,
            Map<String, Distribution> actionServiceByDraftStage,
            Distribution aiDecision,
            Distribution simulationServiceFirst,
            Distribution simulationMatchEngine,
            Distribution simulationResponseProjection,
            Distribution simulationJsonSerialization,
            Distribution simulationExactRetry,
            Distribution browserActionTotalToDom,
            Distribution browserSimulationTotalToDom,
            String percentileLimitation
    ) { }

    public record Recommendation(
            String schemaVersion,
            String status,
            String interactivePrimaryPhase,
            Map<String, Long> interactiveMedianNanosByPhase,
            String simulationPrimaryPhase,
            Map<String, Long> simulationMedianNanosByPhase,
            Distribution browserActionTotalToDom,
            Distribution browserSimulationTotalToDom,
            String nextMilestone,
            String evidenceLevel,
            List<String> limitations
    ) {
        public Recommendation {
            limitations = List.copyOf(limitations);
        }
    }
}
