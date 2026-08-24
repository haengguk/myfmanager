package com.lolfm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.draft.AutoDraftJfrSamplerV1;
import com.lolfm.draft.AutoDraftObservationHarnessV1;
import com.lolfm.draft.AutoDraftScalabilityScheduleV1;
import com.lolfm.draft.DraftActionType;
import com.lolfm.simulator.TeamSide;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/** Validation, aggregation and byte-level finalization for the runtime/Draft audit. */
public final class RealMatchRuntimeAutoDraftScalabilityV1Artifacts {
    public static final String STATUS =
            "REAL_MATCH_RUNTIME_HARDENED_AND_AUTO_DRAFT_SCALABILITY_AUDIT_CAPTURED";
    public static final String REVIEW_BASELINE_COMMIT =
            "1e21917548407050faf257b8f0c2e14a41ceb567";
    public static final String PERFORMANCE_MANIFEST_SHA256 =
            "c9b4659c4d602fb33c7295885cdc2685a4991469cc4cc0b097ca2d1a20cb26ee";
    public static final String CONTRACT =
            "real-match-runtime-auto-draft-scalability-v1-contract.json";
    public static final String RUNTIME_RUNS = "real-match-runtime-runs.csv";
    public static final String FIXTURE_RUNS = "auto-draft-fixture-runs.csv";
    public static final String TURN_RUNS = "auto-draft-turn-runs.csv";
    public static final String HOTSPOTS = "auto-draft-hotspots.json";
    public static final String SUMMARY =
            "real-match-runtime-auto-draft-scalability-v1-summary.json";
    public static final String ANALYSIS =
            "real-match-runtime-auto-draft-scalability-v1-analysis.md";
    public static final String MANIFEST = "SHA256SUMS.txt";
    public static final List<String> ARTIFACTS = List.of(
            CONTRACT, RUNTIME_RUNS, FIXTURE_RUNS, TURN_RUNS, HOTSPOTS, SUMMARY,
            ANALYSIS);

    private RealMatchRuntimeAutoDraftScalabilityV1Artifacts() { }

    public static Summary validateAndSummarize(List<JsonNode> runtimeObservations,
                                               List<FixtureRun> fixtureRuns,
                                               List<TurnRun> turnRuns,
                                               AutoDraftJfrSamplerV1.Profile profile,
                                               Environment environment,
                                               ManifestEvidence upstream) {
        require(environment.currentHead().matches("[0-9a-f]{40}"),
                "Current HEAD is missing");
        require(environment.reviewBaselineIsAncestor(),
                "Review baseline is not an ancestor");
        require(upstream.rawManifestSha256().equals(PERFORMANCE_MANIFEST_SHA256)
                        && upstream.entryCount() == 4 && upstream.entriesVerified() == 4,
                "Existing performance baseline manifest drifted");
        validateRuntime(runtimeObservations);
        validateDraftRuns(fixtureRuns, turnRuns);
        require(profile.draftExecutionSamples() > 0,
                "Official JFR contains no Draft execution samples");
        require(profile.draftAllocationSamples() > 0,
                "Official JFR contains no Draft allocation samples");

        Distribution draft = distribution(fixtureRuns,
                FixtureRun::fullDraftNanos);
        Distribution preparation = distribution(fixtureRuns,
                FixtureRun::rosterContextHistoryDraftInputNanos);
        Distribution roster = distribution(fixtureRuns,
                FixtureRun::rosterAssemblyNanos);
        Distribution context = distribution(fixtureRuns,
                FixtureRun::draftTeamContextNanos);
        Distribution input = distribution(fixtureRuns,
                FixtureRun::matchEngineInputProjectionNanos);
        Distribution turn = distribution(turnRuns, TurnRun::turnNanos);
        Distribution ban = distribution(turnRuns.stream()
                .filter(value -> value.actionType() == DraftActionType.BAN).toList(),
                TurnRun::turnNanos);
        Distribution pick = distribution(turnRuns.stream()
                .filter(value -> value.actionType() == DraftActionType.PICK).toList(),
                TurnRun::turnNanos);
        double medianDraftShare = medianDouble(fixtureRuns.stream()
                .map(FixtureRun::draftShareOfPreparation).sorted().toList());
        List<Projection> projections = List.of(100, 250, 1_000).stream()
                .map(games -> new Projection(games,
                        multiplyExact(draft.medianNanos(), games),
                        multiplyExact(draft.p90Nanos(), games),
                        "MEASURED_DRAFT_MEDIAN_NANOS_X_GAMES",
                        "MEASURED_DRAFT_P90_NANOS_X_GAMES",
                        "SERIAL_PROJECTION_NOT_A_PARALLEL_THROUGHPUT_GUARANTEE"))
                .toList();
        return new Summary(
                "REAL_MATCH_RUNTIME_AUTO_DRAFT_SCALABILITY_V1_SUMMARY", STATUS,
                runtimeObservations.size(), fixtureRuns.size(), turnRuns.size(),
                AutoDraftScalabilityScheduleV1.SCHEDULE_HASH,
                draft, preparation, roster, context, input, turn, ban, pick,
                medianDraftShare, projections, profile, environment, upstream,
                List.of(
                        "Timing is environment-specific observation, never a correctness threshold.",
                        "JFR values are sampling/profiler evidence, not exact causal counts or absolute ratios.",
                        "Nested phase timings overlap full Draft time and are not additive.",
                        "Batch values are serial projections; contention, GC, memory and scheduling limit parallel scaling.",
                        "No Draft optimization, cache, tuning, gameplay, Random, API or frontend change was made."));
    }

    public static void writeOfficial(Path output, List<JsonNode> runtimeObservations,
                                     List<FixtureRun> fixtureRuns,
                                     List<TurnRun> turnRuns,
                                     AutoDraftJfrSamplerV1.Profile profile,
                                     Environment environment,
                                     ManifestEvidence upstream,
                                     MatchEngineV1Canonicalizer canonicalizer)
            throws IOException {
        Summary summary = validateAndSummarize(runtimeObservations, fixtureRuns,
                turnRuns, profile, environment, upstream);
        Path normalized = output.toAbsolutePath().normalize();
        require(normalized.endsWith(Path.of("build", "reports",
                        "real-match-runtime-auto-draft-scalability-v1")),
                "Unexpected official output directory");
        Files.createDirectories(normalized);
        require(isEmpty(normalized), "Official output directory must be fresh");

        writeJson(normalized.resolve(CONTRACT), contract(environment, upstream), canonicalizer);
        Files.writeString(normalized.resolve(RUNTIME_RUNS), runtimeCsv(runtimeObservations),
                StandardCharsets.UTF_8);
        Files.writeString(normalized.resolve(FIXTURE_RUNS), fixtureCsv(fixtureRuns),
                StandardCharsets.UTF_8);
        Files.writeString(normalized.resolve(TURN_RUNS), turnCsv(turnRuns),
                StandardCharsets.UTF_8);
        writeJson(normalized.resolve(HOTSPOTS), hotspots(profile, fixtureRuns), canonicalizer);
        writeJson(normalized.resolve(SUMMARY), summary, canonicalizer);
        Files.writeString(normalized.resolve(ANALYSIS), analysis(summary),
                StandardCharsets.UTF_8);
        writeManifest(normalized);
        verifyManifest(normalized);
    }

    public static Map<String, Object> contract(Environment environment,
                                                ManifestEvidence upstream) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion",
                "REAL_MATCH_RUNTIME_AUTO_DRAFT_SCALABILITY_V1_CONTRACT");
        result.put("status", STATUS);
        result.put("reviewBaselineCommit", REVIEW_BASELINE_COMMIT);
        result.put("existingPerformanceBaseline", upstream);
        result.put("environment", environment);
        result.put("scheduleHashAlgorithm",
                AutoDraftScalabilityScheduleV1.HASH_ALGORITHM);
        result.put("scheduleHash", AutoDraftScalabilityScheduleV1.SCHEDULE_HASH);
        result.put("schedule", AutoDraftScalabilityScheduleV1.FIXTURES);
        result.put("runPolicy", Map.of(
                "globalWarmupDrafts", 1,
                "measuredRunsPerFixture", 2,
                "freshGameOneHistory", true,
                "sequential", true,
                "performanceCoverageNotBalanceSample", true));
        result.put("timingSemantics", Map.ofEntries(
                Map.entry("clock", "System.nanoTime monotonic elapsed time"),
                Map.entry("deepSearchTiming", "NONE_DETERMINISTIC_COUNTERS_AND_JFR_ONLY"),
                Map.entry("fullDraftNanos", "production-equivalent DraftEngine.draft decomposition"),
                Map.entry("turnNanos", "coarse boundary around one complete choose operation"),
                Map.entry("nestedTimingAdditive", false),
                Map.entry("latencyAcceptanceThreshold", "NONE")));
        result.put("jfrSemantics", Map.of(
                "executionSamplePeriodMillis",
                AutoDraftJfrSamplerV1.EXECUTION_SAMPLE_PERIOD.toMillis(),
                "evidenceClass", "SAMPLING_PROFILER_EVIDENCE_ONLY",
                "exactOperationCounts", false,
                "profilerOverheadPresent", true));
        result.put("integrityRules", List.of(
                "Hardened bootRun and packaged JAR must expose tiered C2-capable code heaps.",
                "Both frozen HTTP fixtures must preserve result/output/replay/timeline/Random identity.",
                "Every observed Draft must exactly equal DraftEngine.draft before it is recorded.",
                "Every observed Match Engine input must preserve final Draft, assignment and input identity.",
                "Existing artifacts are verified but never regenerated or overwritten."));
        result.put("nonGoals", List.of(
                "DRAFT_OPTIMIZATION", "CACHE", "SEARCH_POLICY_CHANGE",
                "SCORING_OR_TUNING_CHANGE", "GAMEPLAY_OR_RANDOM_CHANGE",
                "API_OR_FRONTEND_CHANGE", "COMPRESSION", "ASYNC_OR_STREAMING"));
        return Map.copyOf(result);
    }

    public static ManifestEvidence verifyPerformanceBaselineManifest(Path backendRoot)
            throws IOException {
        Path directory = backendRoot.resolve(
                "build/reports/real-match-performance-baseline-v1");
        Path manifest = directory.resolve(MANIFEST);
        require(Files.isRegularFile(manifest), "Missing performance baseline manifest");
        String raw = sha256(Files.readAllBytes(manifest));
        require(raw.equals(PERFORMANCE_MANIFEST_SHA256),
                "Performance baseline manifest raw SHA drift");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        require(lines.size() == 4, "Performance baseline manifest count mismatch");
        int verified = 0;
        for (String line : lines) {
            String[] fields = line.split("  ", 2);
            require(fields.length == 2 && fields[0].matches("[0-9a-f]{64}"),
                    "Malformed performance baseline manifest");
            Path file = directory.resolve(fields[1]).normalize();
            require(file.startsWith(directory.normalize()) && Files.isRegularFile(file),
                    "Missing performance baseline artifact");
            require(sha256(Files.readAllBytes(file)).equals(fields[0]),
                    "Performance baseline artifact SHA drift: " + fields[1]);
            verified++;
        }
        return new ManifestEvidence(raw, lines.size(), verified,
                "RAW_SHA256_AND_ALL_ENTRIES_VERIFIED_NO_REGENERATION");
    }

    public static void writeManifest(Path output) throws IOException {
        StringBuilder value = new StringBuilder();
        for (String artifact : ARTIFACTS) {
            value.append(sha256(Files.readAllBytes(output.resolve(artifact))))
                    .append("  ").append(artifact).append('\n');
        }
        Files.writeString(output.resolve(MANIFEST), value, StandardCharsets.UTF_8);
    }

    public static void verifyManifest(Path output) throws IOException {
        List<String> lines = Files.readAllLines(output.resolve(MANIFEST),
                StandardCharsets.UTF_8);
        require(lines.size() == ARTIFACTS.size(), "Artifact manifest count mismatch");
        for (int index = 0; index < ARTIFACTS.size(); index++) {
            String[] fields = lines.get(index).split("  ", 2);
            require(fields.length == 2 && fields[1].equals(ARTIFACTS.get(index)),
                    "Artifact manifest ordering mismatch");
            require(sha256(Files.readAllBytes(output.resolve(fields[1]))).equals(fields[0]),
                    "Artifact manifest SHA mismatch: " + fields[1]);
        }
    }

    private static void validateRuntime(List<JsonNode> observations) {
        require(observations.size() == 4,
                "Official runtime evidence requires four fresh-JVM observations");
        Map<String, String> expected = Map.of(
                "FIXTURE_A_GEN_T1_SEED_73",
                "bdc597af083aa4f081cf4fe7a242d0e36eec7744b186d998d6f83b717648e874",
                "FIXTURE_B_HLE_DK_SEED_NEGATIVE_73",
                "fef2dfd3c522a69f7393bf46196ac9319cb4b6981e9131c694a01239d7aaabb0");
        Map<String, Integer> coverage = new LinkedHashMap<>();
        for (JsonNode observation : observations) {
            require(observation.path("schemaVersion").asText().equals(
                            "REAL_MATCH_EXTERNAL_RUNTIME_PROBE_V1"),
                    "Runtime observation schema mismatch");
            String mode = observation.path("launchMode").asText();
            require(mode.equals("HARDENED_BOOT_RUN") || mode.equals("PACKAGED_JAR"),
                    "Unexpected runtime launch mode");
            String fixture = observation.path("fixture").path("fixtureId").asText();
            require(expected.containsKey(fixture), "Unexpected runtime fixture");
            coverage.merge(mode + ":" + fixture, 1, Integer::sum);
            JsonNode jvm = observation.path("jvmEvidence");
            require(!jvm.path("tieredStopAtLevel1").asBoolean()
                            && jvm.path("profiledNmethodsHeapAvailable").asBoolean()
                            && jvm.path("nonProfiledNmethodsHeapAvailable").asBoolean(),
                    "Runtime JVM is not C2-capable");
            JsonNode first = observation.path("firstRequest");
            JsonNode second = observation.path("secondRequest");
            for (JsonNode run : List.of(first, second)) {
                require(run.path("httpStatus").asInt() == 200
                                && run.path("contentEncoding").asText().equals("NONE")
                                && run.path("outputHash").asText().equals(expected.get(fixture)),
                        "Runtime fixture result/output drift");
            }
            for (String identity : List.of("outputHash", "replayProvenanceHash",
                    "simulatorTimelineHash", "structuredTimelineHash", "randomDrawCount",
                    "randomTraceHash", "responseCanonicalHash")) {
                require(first.path(identity).asText().equals(second.path(identity).asText()),
                        "Runtime first/warm identity drift: " + identity);
            }
        }
        require(coverage.size() == 4 && coverage.values().stream().allMatch(value -> value == 1),
                "Runtime mode/fixture coverage mismatch");
    }

    private static void validateDraftRuns(List<FixtureRun> fixtures,
                                          List<TurnRun> turns) {
        require(fixtures.size() == 24, "Official Draft audit requires 24 measured runs");
        require(turns.size() == 480, "Official Draft audit requires 480 turn rows");
        int runIndex = 0;
        for (AutoDraftScalabilityScheduleV1.Fixture fixture
                : AutoDraftScalabilityScheduleV1.FIXTURES) {
            for (int ordinal = 1; ordinal <= 2; ordinal++) {
                int measuredOrdinal = ordinal;
                FixtureRun run = fixtures.get(runIndex++);
                require(run.fixtureIndex() == fixture.index()
                                && run.fixtureId().equals(fixture.id())
                                && run.measuredOrdinal() == ordinal
                                && run.blueTeamCode().equals(fixture.blueTeamCode())
                                && run.redTeamCode().equals(fixture.redTeamCode())
                                && run.decompositionParity()
                                && run.turnCount() == 20
                                && run.banCount() == 10 && run.pickCount() == 10,
                        "Draft fixture schedule/parity mismatch: " + fixture.id());
                if (ordinal == 2) {
                    FixtureRun first = fixtures.get(runIndex - 2);
                    require(run.draftIdentity().equals(first.draftIdentity())
                                    && run.finalDraftHash().equals(first.finalDraftHash())
                                    && run.finalAssignmentHash().equals(first.finalAssignmentHash())
                                    && run.inputHash().equals(first.inputHash())
                                    && run.counters().equals(first.counters())
                                    && run.blueBans().equals(first.blueBans())
                                    && run.redBans().equals(first.redBans())
                                    && run.bluePicks().equals(first.bluePicks())
                                    && run.redPicks().equals(first.redPicks())
                                    && run.blueFinalRoles().equals(first.blueFinalRoles())
                                    && run.redFinalRoles().equals(first.redFinalRoles())
                                    && run.matchAssignments().equals(first.matchAssignments()),
                            "Same-JVM Draft/counter/input replay drift: " + fixture.id());
                }
                List<TurnRun> runTurns = turns.stream()
                        .filter(value -> value.fixtureId().equals(run.fixtureId())
                                && value.measuredOrdinal() == measuredOrdinal).toList();
                require(runTurns.size() == 20, "Draft turn coverage mismatch");
                for (int index = 0; index < 20; index++) {
                    require(runTurns.get(index).turn() == index + 1,
                            "Draft turn order mismatch");
                }
                require(runTurns.stream().filter(
                                value -> value.actionType() == DraftActionType.BAN).count() == 10
                                && runTurns.stream().filter(
                                value -> value.actionType() == DraftActionType.PICK).count() == 10,
                        "BAN/PICK turn coverage mismatch");
            }
        }
        require(AutoDraftScalabilityScheduleV1.teams(TeamSide.BLUE).size() == 10
                        && AutoDraftScalabilityScheduleV1.teams(TeamSide.RED).size() == 10,
                "Team-side coverage mismatch");
    }

    static String runtimeCsv(List<JsonNode> observations) {
        StringBuilder value = new StringBuilder(
                "launchMode,fixtureId,requestKind,elapsedNanos,httpStatus,bodyBytes,contentEncoding,outputHash,replayProvenanceHash,simulatorTimelineHash,structuredTimelineHash,randomDrawCount,randomTraceHash,responseCanonicalHash\n");
        for (JsonNode observation : observations) {
            for (String requestKind : List.of("FIRST", "WARM_SECOND")) {
                JsonNode run = observation.path(requestKind.equals("FIRST")
                        ? "firstRequest" : "secondRequest");
                appendCsv(value, observation.path("launchMode").asText(),
                        observation.path("fixture").path("fixtureId").asText(), requestKind,
                        run.path("elapsedNanos").asText(), run.path("httpStatus").asText(),
                        run.path("bodyBytes").asText(), run.path("contentEncoding").asText(),
                        run.path("outputHash").asText(), run.path("replayProvenanceHash").asText(),
                        run.path("simulatorTimelineHash").asText(),
                        run.path("structuredTimelineHash").asText(),
                        run.path("randomDrawCount").asText(), run.path("randomTraceHash").asText(),
                        run.path("responseCanonicalHash").asText());
            }
        }
        return value.toString();
    }

    static String fixtureCsv(List<FixtureRun> runs) {
        StringBuilder value = new StringBuilder(
                "fixtureIndex,fixtureId,measuredOrdinal,blueTeamCode,redTeamCode,rosterAssemblyNanos,draftTeamContextNanos,freshHistoryNanos,fullDraftNanos,initialPlanNanos,finalRoleResolutionNanos,finalPlanNanos,matchAssignmentProjectionNanos,matchEngineInputProjectionNanos,rosterContextHistoryDraftInputNanos,draftShareOfPreparation,draftIdentity,finalDraftHash,finalAssignmentHash,inputHash,blueBans,redBans,bluePicks,redPicks,blueFinalRoles,redFinalRoles,matchAssignments,turnCount,banCount,pickCount,decompositionParity,counters\n");
        for (FixtureRun run : runs) {
            appendCsv(value, run.fixtureIndex(), run.fixtureId(), run.measuredOrdinal(),
                    run.blueTeamCode(), run.redTeamCode(), run.rosterAssemblyNanos(),
                    run.draftTeamContextNanos(), run.freshHistoryNanos(),
                    run.fullDraftNanos(), run.initialPlanNanos(),
                    run.finalRoleResolutionNanos(), run.finalPlanNanos(),
                    run.matchAssignmentProjectionNanos(),
                    run.matchEngineInputProjectionNanos(),
                    run.rosterContextHistoryDraftInputNanos(), run.draftShareOfPreparation(),
                    run.draftIdentity(), run.finalDraftHash(), run.finalAssignmentHash(),
                    run.inputHash(), run.blueBans(), run.redBans(), run.bluePicks(),
                    run.redPicks(), run.blueFinalRoles(), run.redFinalRoles(),
                    run.matchAssignments(), run.turnCount(), run.banCount(), run.pickCount(),
                    run.decompositionParity(), run.counters());
        }
        return value.toString();
    }

    static String turnCsv(List<TurnRun> runs) {
        StringBuilder value = new StringBuilder(
                "fixtureId,measuredOrdinal,turn,side,actionType,selectedChampionId,turnNanos,immediateScore,continuationScore,finalSearchScore,preferredPlan,preferredPlanViability,componentBreakdown,alternatives,rootCandidateScores,counters\n");
        for (TurnRun run : runs) {
            appendCsv(value, run.fixtureId(), run.measuredOrdinal(), run.turn(), run.side(),
                    run.actionType(), run.selectedChampionId(), run.turnNanos(),
                    run.immediateScore(), run.continuationScore(), run.finalSearchScore(),
                    run.preferredPlan(), run.preferredPlanViability(),
                    run.componentBreakdown(), run.alternatives(), run.rootCandidateScores(),
                    run.counters());
        }
        return value.toString();
    }

    private static Map<String, Object> hotspots(AutoDraftJfrSamplerV1.Profile profile,
                                                 List<FixtureRun> fixtures) {
        return Map.of(
                "schemaVersion", "AUTO_DRAFT_HOTSPOTS_V1",
                "evidenceClass", "JFR_SAMPLING_PROFILER_EVIDENCE_ONLY",
                "profilerOverheadPresent", true,
                "exactCausalOrAbsoluteRatio", false,
                "jfr", profile,
                "deterministicCountersPerMeasuredDraft",
                fixtures.stream().map(FixtureRun::counters).toList(),
                "interpretation", List.of(
                        "Execution samples locate recurring on-CPU Draft stacks.",
                        "Allocation sampled bytes are weighted samples, not a heap census.",
                        "Deterministic counters are exact for this fixed implementation and schedule."));
    }

    private static String analysis(Summary summary) {
        return """
                # Real Match runtime hardening and Auto Draft scalability audit V1

                Status: `%s`

                `bootRun`의 C1-only optimized launch를 제거한 뒤, hardened bootRun과 packaged JAR 모두
                두 frozen fixture의 output/replay/timeline/Random identity를 유지했다. 이 변경은 개발 실행
                JVM의 컴파일 의미만 정상화하며 gameplay 개선이 아니다.

                정상 JVM의 자동 Draft 중앙값은 %.3f ms, p90은 %.3f ms, 최댓값은 %.3f ms였다.
                Roster + Context + fresh history + Draft + Match Engine input 준비 중 Draft 비중의 run별 중앙값은
                %.4f%%다. BAN turn 중앙값은 %.3f ms, PICK turn 중앙값은 %.3f ms다.

                JFR은 %dms execution sampling과 allocation sampling을 사용했다. 따라서 hotspot 순위는
                profiler 표본 증거이며 exact 인과관계나 절대 CPU/allocation 비율이 아니다. 검색 내부의 exact
                작업량은 별도의 deterministic counter로 기록했다.

                100/250/1,000게임 값은 측정 중앙값과 p90을 직렬 곱한 projection이다. worker 수로 단순히
                나눈 병렬 확정치가 아니며 CPU contention, GC, 메모리, scheduling overhead를 포함하지 않는다.

                이번 artifact는 최적화 전 기준선이다. search depth/beam/candidate/scoring/order, cache,
                gameplay, Random, API, frontend는 변경하지 않았다. 다음 단계는 JFR과 exact counter가 함께
                가리키는 Draft 호출 경로를 대상으로 별도 `DRAFT_ENGINE_PERFORMANCE_HARDENING_V1`을 수행하는 것이다.
                """.formatted(summary.status(), nanosToMillis(summary.fullDraft().medianNanos()),
                nanosToMillis(summary.fullDraft().p90Nanos()),
                nanosToMillis(summary.fullDraft().maxNanos()),
                summary.medianDraftShareOfPreparation() * 100.0,
                nanosToMillis(summary.banTurn().medianNanos()),
                nanosToMillis(summary.pickTurn().medianNanos()),
                summary.jfrProfile().executionSamplePeriodMillis());
    }

    private static <T> Distribution distribution(List<T> values,
                                                  ToLongFunction<T> extractor) {
        List<Long> sorted = values.stream().mapToLong(extractor).sorted().boxed().toList();
        require(!sorted.isEmpty(), "Cannot summarize empty observations");
        int median = sorted.size() / 2;
        long medianValue = sorted.size() % 2 == 1 ? sorted.get(median)
                : Math.addExact(sorted.get(median - 1), sorted.get(median)) / 2L;
        int p90Index = Math.max(0, (int) Math.ceil(sorted.size() * 0.90) - 1);
        return new Distribution(sorted.getFirst(), medianValue, sorted.get(p90Index),
                sorted.getLast(), sorted.size(),
                "NEAREST_RANK_P90_AND_MIDDLE_MEDIAN");
    }

    private static double medianDouble(List<Double> sorted) {
        require(!sorted.isEmpty(), "Cannot summarize empty double observations");
        int median = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(median)
                : (sorted.get(median - 1) + sorted.get(median)) / 2.0;
    }

    private static long multiplyExact(long nanos, int games) {
        return Math.multiplyExact(nanos, games);
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.findAny().isEmpty();
        }
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
            target.append(csv(String.valueOf(values[index])));
        }
        target.append('\n');
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public record FixtureRun(
            int fixtureIndex, String fixtureId, int measuredOrdinal,
            String blueTeamCode, String redTeamCode,
            long rosterAssemblyNanos, long draftTeamContextNanos,
            long freshHistoryNanos, long fullDraftNanos, long initialPlanNanos,
            long finalRoleResolutionNanos, long finalPlanNanos,
            long matchAssignmentProjectionNanos, long matchEngineInputProjectionNanos,
            long rosterContextHistoryDraftInputNanos, double draftShareOfPreparation,
            String draftIdentity, String finalDraftHash, String finalAssignmentHash,
            String inputHash, String blueBans, String redBans, String bluePicks,
            String redPicks, String blueFinalRoles, String redFinalRoles,
            String matchAssignments, int turnCount, int banCount, int pickCount,
            boolean decompositionParity,
            AutoDraftObservationHarnessV1.CounterSnapshot counters) { }

    public record TurnRun(
            String fixtureId, int measuredOrdinal, int turn, TeamSide side,
            DraftActionType actionType, String selectedChampionId, long turnNanos,
            double immediateScore, double continuationScore, double finalSearchScore,
            String preferredPlan, double preferredPlanViability,
            String componentBreakdown, String alternatives, String rootCandidateScores,
            AutoDraftObservationHarnessV1.CounterSnapshot counters) { }

    public record Environment(
            String currentHead, boolean reviewBaselineIsAncestor,
            String javaVersion, String javaVmName, String osName, String osVersion,
            String osArch, int availableProcessors, long maxHeapBytes,
            String gradleVersion, String springBootVersion,
            String productionSourceTreeHash, int productionSourceFileCount,
            String verificationSourceTreeHash, int verificationSourceFileCount,
            String bootRunSemantics, String packagedJarSemantics) { }

    public record ManifestEvidence(String rawManifestSha256, int entryCount,
                                   int entriesVerified, String verification) { }

    public record Distribution(long minNanos, long medianNanos, long p90Nanos,
                               long maxNanos, int sampleCount, String aggregation) { }

    public record Projection(int gameCount, long medianProjectedNanos,
                             long conservativeP90ProjectedNanos,
                             String medianFormula, String conservativeFormula,
                             String limitation) { }

    public record Summary(
            String schemaVersion, String status, int runtimeObservationCount,
            int measuredDraftCount, int measuredTurnCount, String scheduleHash,
            Distribution fullDraft, Distribution totalPreparation,
            Distribution rosterAssembly, Distribution draftTeamContext,
            Distribution matchEngineInputProjection, Distribution allTurn,
            Distribution banTurn, Distribution pickTurn,
            double medianDraftShareOfPreparation, List<Projection> projections,
            AutoDraftJfrSamplerV1.Profile jfrProfile, Environment environment,
            ManifestEvidence existingPerformanceBaseline, List<String> notes) {
        public Summary {
            projections = List.copyOf(projections);
            notes = List.copyOf(notes);
        }
    }
}
