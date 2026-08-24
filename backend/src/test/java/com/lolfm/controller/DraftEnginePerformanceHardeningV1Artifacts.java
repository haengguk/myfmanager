package com.lolfm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.draft.AutoDraftJfrSamplerV1;
import com.lolfm.draft.AutoDraftObservationHarnessV1;
import com.lolfm.draft.DraftDecision;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.application.MatchEngineV1Input;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Canonical artifact contract for Draft Engine performance hardening V1. */
final class DraftEnginePerformanceHardeningV1Artifacts {
    static final String REVIEW_BASELINE_COMMIT =
            "ade0e252437edc2b43f7c8d3b8e7c9f4acb8239d";
    static final String UPSTREAM_MANIFEST_SHA =
            "751cb19ccf55b34cc0bf4a410a292ba66df4e84d566dd1e217b4a68712d3be8b";
    static final String SCHEDULE_HASH =
            "8888526d5085a5bfcc75b1495223e4babeba0c69fa63dd8c9a8adda9e2315b00";
    static final long BEFORE_MEDIAN_NANOS = 11_172_949_850L;
    static final long BEFORE_P90_NANOS = 13_419_973_800L;
    static final long BEFORE_MAX_NANOS = 15_412_444_900L;
    static final String HARDENED = "DRAFT_ENGINE_PERFORMANCE_HARDENED";
    static final String REVIEW_REQUIRED =
            "DRAFT_ENGINE_PERFORMANCE_EVIDENCE_REVIEW_REQUIRED";
    static final String SEMANTIC_REJECTED =
            "DRAFT_ENGINE_PERFORMANCE_HARDENING_REJECTED_SEMANTIC_DRIFT";

    static final String CONTRACT = "draft-engine-performance-hardening-v1-contract.json";
    static final String FIXTURES = "draft-engine-performance-before-after-fixtures.csv";
    static final String TURNS = "draft-engine-performance-before-after-turns.csv";
    static final String CACHE = "draft-engine-cache-statistics.json";
    static final String HOTSPOTS = "draft-engine-hotspots-before-after.json";
    static final String SUMMARY = "draft-engine-performance-hardening-v1-summary.json";
    static final String ANALYSIS = "draft-engine-performance-hardening-v1-analysis.md";
    static final String MANIFEST = "SHA256SUMS.txt";
    static final List<String> ARTIFACTS = List.of(
            CONTRACT, FIXTURES, TURNS, CACHE, HOTSPOTS, SUMMARY, ANALYSIS);

    private DraftEnginePerformanceHardeningV1Artifacts() { }

    static UpstreamEvidence verifyUpstream(Path backendRoot, ObjectMapper mapper)
            throws IOException {
        Path directory = backendRoot.resolve(
                "build/reports/real-match-runtime-auto-draft-scalability-v1");
        Path manifest = directory.resolve(MANIFEST);
        require(Files.isRegularFile(manifest), "Missing upstream manifest");
        require(sha256(Files.readAllBytes(manifest)).equals(UPSTREAM_MANIFEST_SHA),
                "Upstream manifest raw SHA mismatch");
        int verified = verifyManifest(directory, ARTIFACTS_UPSTREAM);
        require(verified == 7, "Upstream manifest must verify 7/7 entries");
        JsonNode summary = mapper.readTree(directory.resolve(
                "real-match-runtime-auto-draft-scalability-v1-summary.json").toFile());
        require(summary.path("fullDraft").path("medianNanos").asLong()
                        == BEFORE_MEDIAN_NANOS,
                "Upstream full Draft median mismatch");
        require(summary.path("fullDraft").path("p90Nanos").asLong()
                        == BEFORE_P90_NANOS,
                "Upstream full Draft p90 mismatch");
        require(summary.path("fullDraft").path("maxNanos").asLong()
                        == BEFORE_MAX_NANOS,
                "Upstream full Draft max mismatch");
        JsonNode contract = mapper.readTree(directory.resolve(
                "real-match-runtime-auto-draft-scalability-v1-contract.json").toFile());
        require(contract.path("scheduleHash").asText().equals(SCHEDULE_HASH),
                "Upstream schedule hash mismatch");
        List<Map<String, String>> fixtures = parseCsv(
                directory.resolve("auto-draft-fixture-runs.csv"));
        List<Map<String, String>> turns = parseCsv(
                directory.resolve("auto-draft-turn-runs.csv"));
        require(fixtures.size() == 24, "Upstream fixture rows must be 24");
        require(turns.size() == 480, "Upstream turn rows must be 480");
        return new UpstreamEvidence(directory, contract, summary,
                mapper.readTree(directory.resolve("auto-draft-hotspots.json").toFile()),
                index(fixtures, row -> fixtureKey(row.get("fixtureId"),
                        Integer.parseInt(row.get("measuredOrdinal")))),
                index(turns, row -> turnKey(row.get("fixtureId"),
                        Integer.parseInt(row.get("measuredOrdinal")),
                        Integer.parseInt(row.get("turn")))), verified);
    }

    static boolean fixtureParity(UpstreamEvidence upstream, FixtureRun run) {
        Map<String, String> before = upstream.fixtures().get(
                fixtureKey(run.fixtureId(), run.measuredOrdinal()));
        if (before == null) return false;
        return equal(before, "blueTeamCode", run.blueTeamCode())
                && equal(before, "redTeamCode", run.redTeamCode())
                && equal(before, "draftIdentity", run.draftIdentity())
                && equal(before, "finalDraftHash", run.finalDraftHash())
                && equal(before, "finalAssignmentHash", run.finalAssignmentHash())
                && equal(before, "inputHash", run.inputHash())
                && equal(before, "blueBans", run.blueBans())
                && equal(before, "redBans", run.redBans())
                && equal(before, "bluePicks", run.bluePicks())
                && equal(before, "redPicks", run.redPicks())
                && equal(before, "blueFinalRoles", run.blueFinalRoles())
                && equal(before, "redFinalRoles", run.redFinalRoles())
                && equal(before, "matchAssignments", run.matchAssignments())
                && equal(before, "turnCount", Integer.toString(run.turnCount()))
                && equal(before, "banCount", Integer.toString(run.banCount()))
                && equal(before, "pickCount", Integer.toString(run.pickCount()))
                && equal(before, "counters", run.semanticCounters());
    }

    static boolean turnParity(UpstreamEvidence upstream, TurnRun run) {
        return turnParityMismatches(upstream, run).isEmpty();
    }

    static List<String> turnParityMismatches(UpstreamEvidence upstream, TurnRun run) {
        Map<String, String> before = upstream.turns().get(
                turnKey(run.fixtureId(), run.measuredOrdinal(), run.turn()));
        if (before == null) return List.of("missing baseline row");
        ArrayList<String> mismatches = new ArrayList<>();
        compare(mismatches, before, "side", run.side());
        compare(mismatches, before, "actionType", run.actionType());
        compare(mismatches, before, "selectedChampionId", run.selectedChampionId());
        compareDouble(mismatches, before, "immediateScore", run.immediateScore());
        compareDouble(mismatches, before, "continuationScore", run.continuationScore());
        compareDouble(mismatches, before, "finalSearchScore", run.finalSearchScore());
        compare(mismatches, before, "preferredPlan", run.preferredPlan());
        compareDouble(mismatches, before, "preferredPlanViability",
                run.preferredPlanViability());
        compare(mismatches, before, "componentBreakdown", run.componentBreakdown());
        compare(mismatches, before, "alternatives", run.alternatives());
        compare(mismatches, before, "counters", run.semanticCounters());
        return List.copyOf(mismatches);
    }

    static boolean diagnosticRootCandidateScoresParity(UpstreamEvidence upstream,
                                                       TurnRun run) {
        Map<String, String> before = upstream.turns().get(
                turnKey(run.fixtureId(), run.measuredOrdinal(), run.turn()));
        return before != null && equal(before, "rootCandidateScores",
                run.rootCandidateScores());
    }

    static void write(Path output, UpstreamEvidence upstream,
                      List<FixtureRun> fixtureRuns, List<TurnRun> turnRuns,
                      AutoDraftJfrSamplerV1.Profile afterProfile,
                      Environment environment, List<ApiParity> apiParity,
                      MatchEngineV1Canonicalizer canonicalizer) throws IOException {
        require(fixtureRuns.size() == 24, "Measured fixture rows must be 24");
        require(turnRuns.size() == 480, "Measured turn rows must be 480");
        require(fixtureRuns.stream().allMatch(FixtureRun::exact),
                "Fixture semantic parity failed");
        require(turnRuns.stream().allMatch(TurnRun::exact),
                "Turn semantic parity failed");
        require(apiParity.size() == 2 && apiParity.stream().allMatch(ApiParity::exact),
                "Real Match API parity failed");
        Files.createDirectories(output);

        Timing full = Timing.of(fixtureRuns.stream().map(FixtureRun::afterDraftNanos)
                .toList());
        Timing allTurn = Timing.of(turnRuns.stream().map(TurnRun::afterTurnNanos).toList());
        Timing ban = Timing.of(turnRuns.stream().filter(value -> value.actionType().equals("BAN"))
                .map(TurnRun::afterTurnNanos).toList());
        Timing pick = Timing.of(turnRuns.stream().filter(value -> value.actionType().equals("PICK"))
                .map(TurnRun::afterTurnNanos).toList());
        boolean performancePassed = full.medianNanos() * 100L
                <= BEFORE_MEDIAN_NANOS * 60L
                && full.p90Nanos() * 100L <= BEFORE_P90_NANOS * 70L;
        String status = performancePassed ? HARDENED : REVIEW_REQUIRED;

        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("schemaVersion", "DRAFT_ENGINE_PERFORMANCE_HARDENING_V1_CONTRACT");
        contract.put("status", status);
        contract.put("reviewBaselineCommit", REVIEW_BASELINE_COMMIT);
        contract.put("upstreamManifestRawSha256", UPSTREAM_MANIFEST_SHA);
        contract.put("upstreamManifestEntriesVerified", upstream.entriesVerified());
        contract.put("scheduleHash", SCHEDULE_HASH);
        contract.put("runPolicy", Map.of(
                "globalWarmupDrafts", 1,
                "fixtures", 12,
                "measuredRunsPerFixture", 2,
                "sequential", true,
                "freshGameOneHistory", true));
        contract.put("parityGate", Map.of(
                "fixtureExact", 24,
                "turnExact", 480,
                "reference", "SAME_JVM_UNCACHED_PRIMITIVE",
                "doubleComparison", "Double.doubleToLongBits",
                "semanticCounterExact", true,
                "realMatchApiFixturesExact", 2));
        contract.put("optimizationScope",
                "ONE_DRAFT_ENGINE_DRAFT_CALL_NO_CROSS_MATCH_OR_STATIC_CACHE");
        contract.put("nonChanges", List.of("SEARCH_DEPTH", "BEAM_WIDTH",
                "CANDIDATE_LIMIT", "SCORING", "SORT_ORDER", "FLOATING_POINT_ORDER",
                "GAMEPLAY_RANDOM", "API_SCHEMA", "FRONTEND", "PRODUCTION_RESOURCES"));
        contract.put("environment", environment);
        writeJson(output.resolve(CONTRACT), contract, canonicalizer);

        writeFixtureCsv(output.resolve(FIXTURES), upstream, fixtureRuns);
        writeTurnCsv(output.resolve(TURNS), upstream, turnRuns);

        List<AutoDraftObservationHarnessV1.ComputationSnapshot> beforeComputation = fixtureRuns
                .stream().map(FixtureRun::referenceComputation).toList();
        List<AutoDraftObservationHarnessV1.ComputationSnapshot> afterComputation = fixtureRuns
                .stream().map(FixtureRun::computation).toList();
        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("schemaVersion", "DRAFT_ENGINE_CACHE_STATISTICS_V1");
        cache.put("status", status);
        cache.put("semanticCountersPerDraft", fixtureRuns.getFirst().semanticCounters());
        cache.put("semanticCountersExactAcross24", fixtureRuns.stream()
                .map(FixtureRun::semanticCounters).distinct().count() == 1);
        cache.put("uncachedReferencePerMeasuredDraft", beforeComputation);
        cache.put("cachedPerMeasuredDraft", afterComputation);
        cache.put("uncachedReferenceAggregate", aggregateComputation(beforeComputation));
        cache.put("cachedAggregate", aggregateComputation(afterComputation));
        cache.put("physicalWorkReduction", physicalWorkReduction(
                beforeComputation, afterComputation));
        writeJson(output.resolve(CACHE), cache, canonicalizer);

        Map<String, Object> hotspots = new LinkedHashMap<>();
        hotspots.put("schemaVersion", "DRAFT_ENGINE_HOTSPOTS_BEFORE_AFTER_V1");
        hotspots.put("evidenceClass", "JFR_SAMPLING_PROFILER_EVIDENCE_ONLY");
        hotspots.put("exactCausalOrAbsoluteRatio", false);
        hotspots.put("before", upstream.hotspots());
        hotspots.put("after", afterProfile);
        writeJson(output.resolve(HOTSPOTS), hotspots, canonicalizer);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", "DRAFT_ENGINE_PERFORMANCE_HARDENING_V1_SUMMARY");
        summary.put("status", status);
        summary.put("performanceTargetPassed", performancePassed);
        summary.put("fullDraftBefore", new Timing(24, 0L, BEFORE_MEDIAN_NANOS,
                BEFORE_P90_NANOS, BEFORE_MAX_NANOS));
        summary.put("fullDraftAfter", full);
        summary.put("medianReduction", 1.0 - full.medianNanos()
                / (double) BEFORE_MEDIAN_NANOS);
        summary.put("p90Reduction", 1.0 - full.p90Nanos()
                / (double) BEFORE_P90_NANOS);
        summary.put("allTurnAfter", allTurn);
        summary.put("banTurnAfter", ban);
        summary.put("pickTurnAfter", pick);
        summary.put("turnNumberAfter", turnNumberTiming(turnRuns));
        summary.put("projectionsBefore", projections(
                BEFORE_MEDIAN_NANOS, BEFORE_P90_NANOS));
        summary.put("projectionsAfter", projections(
                full.medianNanos(), full.p90Nanos()));
        summary.put("fixtureParity", Map.of("exact", 24, "total", 24));
        summary.put("turnParity", Map.of("exact", 480, "total", 480));
        summary.put("upstreamTimingArtifactTurnParity", Map.of(
                "exact", turnRuns.stream().filter(
                        TurnRun::upstreamTimingArtifactExact).count(),
                "total", turnRuns.size(),
                "acceptanceClass",
                "OBSERVATIONAL_PRIOR_JVM_TIMING_ARTIFACT_NOT_SAME_RUN_SEMANTIC_GATE"));
        summary.put("realMatchApiParity", apiParity);
        summary.put("environment", environment);
        summary.put("notes", List.of(
                "Timing is evidence from one sequential run, not a unit-test invariant.",
                "JFR values are sampling evidence, not exact allocation totals or causal ratios.",
                "Batch values are serial projections, not parallel throughput promises."));
        writeJson(output.resolve(SUMMARY), summary, canonicalizer);
        writeAnalysis(output.resolve(ANALYSIS), status, full, ban, pick,
                performancePassed, apiParity, beforeComputation, afterComputation);
        writeManifest(output);
        verifyGeneratedManifest(output);
    }

    static int verifyGeneratedManifest(Path output) throws IOException {
        int verified = verifyManifest(output, ARTIFACTS);
        require(verified == ARTIFACTS.size(), "Generated manifest entry mismatch");
        return verified;
    }

    private static Map<String, Object> aggregateComputation(
            List<AutoDraftObservationHarnessV1.ComputationSnapshot> values) {
        return Map.ofEntries(
                Map.entry("measuredDrafts", values.size()),
                Map.entry("roleAssignmentRequests", sum(values,
                        AutoDraftObservationHarnessV1.ComputationSnapshot::roleAssignmentRequests)),
                Map.entry("roleAssignmentHits", sum(values,
                        AutoDraftObservationHarnessV1.ComputationSnapshot::roleAssignmentHits)),
                Map.entry("roleAssignmentPhysicalComputations", sum(values,
                        AutoDraftObservationHarnessV1.ComputationSnapshot::roleAssignmentPhysicalComputations)),
                Map.entry("completionRequests", sum(values,
                        AutoDraftObservationHarnessV1.ComputationSnapshot::completionRequests)),
                Map.entry("completionHits", sum(values,
                        AutoDraftObservationHarnessV1.ComputationSnapshot::completionHits)),
                Map.entry("completionPhysicalComputations", sum(values,
                        AutoDraftObservationHarnessV1.ComputationSnapshot::completionPhysicalComputations)),
                Map.entry("poolHealthRequests", sum(values,
                        AutoDraftObservationHarnessV1.ComputationSnapshot::poolHealthRequests)),
                Map.entry("poolHealthHits", sum(values,
                        AutoDraftObservationHarnessV1.ComputationSnapshot::poolHealthHits)),
                Map.entry("poolHealthPhysicalComputations", sum(values,
                        AutoDraftObservationHarnessV1.ComputationSnapshot::poolHealthPhysicalComputations)),
                Map.entry("plannerCandidatePhysicalComputations", sum(values,
                        AutoDraftObservationHarnessV1.ComputationSnapshot::plannerCandidatePhysicalComputations)),
                Map.entry("plannerCandidateLocalReuses", sum(values,
                        AutoDraftObservationHarnessV1.ComputationSnapshot::plannerCandidateLocalReuses)),
                Map.entry("peakEntriesMax", values.stream().mapToInt(
                        AutoDraftObservationHarnessV1.ComputationSnapshot::peakEntries).max().orElse(0)));
    }

    private static long sum(List<AutoDraftObservationHarnessV1.ComputationSnapshot> values,
                            java.util.function.ToLongFunction<
                                    AutoDraftObservationHarnessV1.ComputationSnapshot> function) {
        return values.stream().mapToLong(function).sum();
    }

    private static Map<String, Object> physicalWorkReduction(
            List<AutoDraftObservationHarnessV1.ComputationSnapshot> before,
            List<AutoDraftObservationHarnessV1.ComputationSnapshot> after) {
        long beforeRole = sum(before,
                AutoDraftObservationHarnessV1.ComputationSnapshot::roleAssignmentPhysicalComputations);
        long afterRole = sum(after,
                AutoDraftObservationHarnessV1.ComputationSnapshot::roleAssignmentPhysicalComputations);
        long beforeCompletion = sum(before,
                AutoDraftObservationHarnessV1.ComputationSnapshot::completionPhysicalComputations);
        long afterCompletion = sum(after,
                AutoDraftObservationHarnessV1.ComputationSnapshot::completionPhysicalComputations);
        long beforePool = sum(before,
                AutoDraftObservationHarnessV1.ComputationSnapshot::poolHealthPhysicalComputations);
        long afterPool = sum(after,
                AutoDraftObservationHarnessV1.ComputationSnapshot::poolHealthPhysicalComputations);
        long beforePlanner = sum(before,
                AutoDraftObservationHarnessV1.ComputationSnapshot::plannerCandidatePhysicalComputations);
        long afterPlanner = sum(after,
                AutoDraftObservationHarnessV1.ComputationSnapshot::plannerCandidatePhysicalComputations);
        require(afterRole < beforeRole, "Role assignment physical work was not reduced");
        require(afterCompletion < beforeCompletion, "Completion physical work was not reduced");
        require(afterPool < beforePool, "Pool-health physical work was not reduced");
        require(afterPlanner < beforePlanner, "Planner candidate physical work was not reduced");
        return Map.of(
                "roleAssignment", reduction(beforeRole, afterRole),
                "completion", reduction(beforeCompletion, afterCompletion),
                "poolHealth", reduction(beforePool, afterPool),
                "plannerCandidate", reduction(beforePlanner, afterPlanner));
    }

    private static Map<String, Object> reduction(long before, long after) {
        return Map.of("before", before, "after", after,
                "reduction", 1.0 - after / (double) before);
    }

    private static List<Map<String, Object>> turnNumberTiming(List<TurnRun> turns) {
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        for (int turn = 1; turn <= 20; turn++) {
            int value = turn;
            Timing timing = Timing.of(turns.stream().filter(row -> row.turn() == value)
                    .map(TurnRun::afterTurnNanos).toList());
            result.add(Map.of("turn", turn, "timing", timing));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> projections(long median, long p90) {
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        for (int games : new int[] {100, 250, 1_000}) {
            LinkedHashMap<String, Object> projection = new LinkedHashMap<>();
            projection.put("gameCount", games);
            projection.put("medianProjectedNanos", median * games);
            projection.put("conservativeP90ProjectedNanos", p90 * games);
            projection.put("limitation",
                    "SERIAL_PROJECTION_NOT_A_PARALLEL_THROUGHPUT_GUARANTEE");
            result.add(Map.copyOf(projection));
        }
        return List.copyOf(result);
    }

    private static void writeFixtureCsv(Path path, UpstreamEvidence upstream,
                                        List<FixtureRun> runs) throws IOException {
        StringBuilder csv = new StringBuilder("fixtureId,measuredOrdinal,blueTeamCode,redTeamCode,beforeDraftNanos,afterDraftNanos,draftIdentity,finalDraftHash,finalAssignmentHash,inputHash,semanticExact,semanticCounters,uncachedReferenceComputation,cachedComputation\n");
        for (FixtureRun run : runs) {
            Map<String, String> before = upstream.fixtures().get(
                    fixtureKey(run.fixtureId(), run.measuredOrdinal()));
            row(csv, run.fixtureId(), run.measuredOrdinal(), run.blueTeamCode(),
                    run.redTeamCode(), before.get("fullDraftNanos"),
                    run.afterDraftNanos(), run.draftIdentity(), run.finalDraftHash(),
                    run.finalAssignmentHash(), run.inputHash(), run.exact(),
                    run.semanticCounters(), run.referenceComputation(),
                    run.computation());
        }
        Files.writeString(path, csv.toString(), StandardCharsets.UTF_8);
    }

    private static void writeTurnCsv(Path path, UpstreamEvidence upstream,
                                     List<TurnRun> runs) throws IOException {
        StringBuilder csv = new StringBuilder("fixtureId,measuredOrdinal,turn,side,actionType,selectedChampionId,beforeTurnNanos,afterTurnNanos,immediateScore,continuationScore,finalSearchScore,preferredPlan,preferredPlanViability,componentBreakdown,alternatives,rootCandidateScores,semanticExact,upstreamTimingArtifactExact,counters\n");
        for (TurnRun run : runs) {
            Map<String, String> before = upstream.turns().get(
                    turnKey(run.fixtureId(), run.measuredOrdinal(), run.turn()));
            row(csv, run.fixtureId(), run.measuredOrdinal(), run.turn(), run.side(),
                    run.actionType(), run.selectedChampionId(), before.get("turnNanos"),
                    run.afterTurnNanos(), run.immediateScore(), run.continuationScore(),
                    run.finalSearchScore(), run.preferredPlan(),
                    run.preferredPlanViability(), run.componentBreakdown(),
                    run.alternatives(), run.rootCandidateScores(), run.exact(),
                    run.upstreamTimingArtifactExact(),
                    run.semanticCounters());
        }
        Files.writeString(path, csv.toString(), StandardCharsets.UTF_8);
    }

    private static void writeAnalysis(Path path, String status, Timing full, Timing ban,
                                      Timing pick, boolean performancePassed,
                                      List<ApiParity> apiParity,
                                      List<AutoDraftObservationHarnessV1.ComputationSnapshot> beforeStats,
                                      List<AutoDraftObservationHarnessV1.ComputationSnapshot> afterStats)
            throws IOException {
        String text = "# Draft Engine performance hardening V1\n\n"
                + "- Status: `" + status + "`\n"
                + "- Semantic parity: 12 fixtures / 24 Draft / 480 turns exact\n"
                + "- Real Match API parity: " + apiParity.size() + "/2 exact\n"
                + "- Full Draft before median/p90/max: 11.173 / 13.420 / 15.412 s\n"
                + "- Full Draft after median/p90/max: " + seconds(full.medianNanos())
                + " / " + seconds(full.p90Nanos()) + " / " + seconds(full.maxNanos()) + " s\n"
                + "- BAN after median/p90: " + millis(ban.medianNanos()) + " / "
                + millis(ban.p90Nanos()) + " ms\n"
                + "- PICK after median/p90: " + millis(pick.medianNanos()) + " / "
                + millis(pick.p90Nanos()) + " ms\n"
                + "- Performance target passed: " + performancePassed + "\n"
                + "- Cache scope: one DraftEngine.draft call; no static, global, resolver-owned, ThreadLocal, or cross-match cache.\n"
                + "- Uncached/cached role-assignment physical computations: "
                + sum(beforeStats, AutoDraftObservationHarnessV1.ComputationSnapshot::roleAssignmentPhysicalComputations)
                + " / " + sum(afterStats, AutoDraftObservationHarnessV1.ComputationSnapshot::roleAssignmentPhysicalComputations) + "\n"
                + "- Uncached/cached completion physical computations: "
                + sum(beforeStats, AutoDraftObservationHarnessV1.ComputationSnapshot::completionPhysicalComputations)
                + " / " + sum(afterStats, AutoDraftObservationHarnessV1.ComputationSnapshot::completionPhysicalComputations) + "\n"
                + "- Uncached/cached pool-health physical computations: "
                + sum(beforeStats, AutoDraftObservationHarnessV1.ComputationSnapshot::poolHealthPhysicalComputations)
                + " / " + sum(afterStats, AutoDraftObservationHarnessV1.ComputationSnapshot::poolHealthPhysicalComputations) + "\n"
                + "- Uncached/cached planner-candidate physical computations: "
                + sum(beforeStats, AutoDraftObservationHarnessV1.ComputationSnapshot::plannerCandidatePhysicalComputations)
                + " / " + sum(afterStats, AutoDraftObservationHarnessV1.ComputationSnapshot::plannerCandidatePhysicalComputations) + "\n"
                + "- Peak entries observed: " + afterStats.stream().mapToInt(
                AutoDraftObservationHarnessV1.ComputationSnapshot::peakEntries).max().orElse(0) + "\n"
                + "- JFR values are sampling evidence, not exact CPU ratios or allocation totals.\n"
                + "- Serial projections are not parallel throughput guarantees.\n";
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    static String seconds(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f",
                nanos / 1_000_000_000.0);
    }

    static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static void writeJson(Path path, Object value,
                                  MatchEngineV1Canonicalizer canonicalizer)
            throws IOException {
        Files.writeString(path, canonicalizer.canonicalJson(value) + '\n',
                StandardCharsets.UTF_8);
    }

    static void writeManifest(Path output) throws IOException {
        StringBuilder value = new StringBuilder();
        for (String file : ARTIFACTS) {
            value.append(sha256(Files.readAllBytes(output.resolve(file))))
                    .append("  ").append(file).append('\n');
        }
        Files.writeString(output.resolve(MANIFEST), value.toString(), StandardCharsets.UTF_8);
    }

    private static int verifyManifest(Path directory, List<String> expected)
            throws IOException {
        List<String> lines = Files.readAllLines(directory.resolve(MANIFEST),
                StandardCharsets.UTF_8).stream().filter(value -> !value.isBlank()).toList();
        require(lines.size() == expected.size(), "Manifest entry count mismatch");
        int verified = 0;
        for (String line : lines) {
            int split = line.indexOf("  ");
            require(split == 64, "Malformed manifest line");
            String hash = line.substring(0, split);
            String file = line.substring(split + 2);
            require(expected.contains(file), "Unexpected manifest entry " + file);
            require(sha256(Files.readAllBytes(directory.resolve(file))).equals(hash),
                    "Manifest SHA mismatch for " + file);
            verified++;
        }
        return verified;
    }

    private static List<Map<String, String>> parseCsv(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        ArrayList<List<String>> records = new ArrayList<>();
        ArrayList<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (quoted) {
                if (value == '"' && index + 1 < text.length()
                        && text.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else if (value == '"') {
                    quoted = false;
                } else {
                    field.append(value);
                }
            } else if (value == '"') {
                quoted = true;
            } else if (value == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (value == '\n') {
                row.add(field.toString());
                field.setLength(0);
                records.add(List.copyOf(row));
                row.clear();
            } else if (value != '\r') {
                field.append(value);
            }
        }
        require(!quoted && row.isEmpty() && field.isEmpty(), "Incomplete CSV record");
        List<String> header = records.removeFirst();
        ArrayList<Map<String, String>> result = new ArrayList<>();
        for (List<String> values : records) {
            require(values.size() == header.size(), "CSV field count mismatch");
            LinkedHashMap<String, String> mapped = new LinkedHashMap<>();
            for (int index = 0; index < header.size(); index++) {
                mapped.put(header.get(index), values.get(index));
            }
            result.add(Map.copyOf(mapped));
        }
        return List.copyOf(result);
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> key) {
        return values.stream().collect(Collectors.toUnmodifiableMap(key,
                Function.identity()));
    }

    private static void row(StringBuilder csv, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) csv.append(',');
            String value = Objects.toString(values[index], "");
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append('\n');
    }

    private static boolean equal(Map<String, String> row, String key, String value) {
        return Objects.equals(row.get(key), value);
    }

    private static boolean sameDouble(String expected, double actual) {
        return expected != null && Double.doubleToLongBits(Double.parseDouble(expected))
                == Double.doubleToLongBits(actual);
    }

    private static void compare(List<String> mismatches, Map<String, String> before,
                                String key, String actual) {
        if (!equal(before, key, actual)) {
            mismatches.add(key + " expected=" + before.get(key) + " actual=" + actual);
        }
    }

    private static void compareDouble(List<String> mismatches,
                                      Map<String, String> before,
                                      String key, double actual) {
        if (!sameDouble(before.get(key), actual)) {
            mismatches.add(key + " expected=" + before.get(key) + " actual=" + actual);
        }
    }

    private static String fixtureKey(String fixtureId, int ordinal) {
        return fixtureId + "|" + ordinal;
    }

    private static String turnKey(String fixtureId, int ordinal, int turn) {
        return fixtureKey(fixtureId, ordinal) + "|" + turn;
    }

    static String sha256(byte[] value) {
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

    private static final List<String> ARTIFACTS_UPSTREAM = List.of(
            "real-match-runtime-auto-draft-scalability-v1-contract.json",
            "real-match-runtime-runs.csv", "auto-draft-fixture-runs.csv",
            "auto-draft-turn-runs.csv", "auto-draft-hotspots.json",
            "real-match-runtime-auto-draft-scalability-v1-summary.json",
            "real-match-runtime-auto-draft-scalability-v1-analysis.md");

    record UpstreamEvidence(Path directory, JsonNode contract, JsonNode summary,
                            JsonNode hotspots, Map<String, Map<String, String>> fixtures,
                            Map<String, Map<String, String>> turns,
                            int entriesVerified) { }

    record FixtureRun(String fixtureId, int measuredOrdinal, String blueTeamCode,
                      String redTeamCode, long afterDraftNanos, String draftIdentity,
                      String finalDraftHash, String finalAssignmentHash, String inputHash,
                      String blueBans, String redBans, String bluePicks, String redPicks,
                      String blueFinalRoles, String redFinalRoles, String matchAssignments,
                      int turnCount, int banCount, int pickCount, String semanticCounters,
                      AutoDraftObservationHarnessV1.ComputationSnapshot referenceComputation,
                      AutoDraftObservationHarnessV1.ComputationSnapshot computation,
                      boolean exact) { }

    record TurnRun(String fixtureId, int measuredOrdinal, int turn, String side,
                   String actionType, String selectedChampionId, long afterTurnNanos,
                   double immediateScore, double continuationScore, double finalSearchScore,
                   String preferredPlan, double preferredPlanViability,
                   String componentBreakdown, String alternatives,
                   String rootCandidateScores, String semanticCounters,
                   boolean exact, boolean upstreamTimingArtifactExact) { }

    record ApiParity(String fixtureId, String blueTeamCode, String redTeamCode,
                     String seed, String winner, int durationSeconds, int eventCount,
                     int snapshotCount, String outputHash, String replayProvenanceHash,
                     String simulatorTimelineHash, String structuredTimelineHash,
                     long randomDrawCount, String randomTraceHash,
                     String responseCanonicalHash, String resultCanonicalHash,
                     String timelineCanonicalHash, String policyId, String policyHash,
                     String runtimeProfileId, String configurationHash,
                     String engineImplementationVersion,
                     String activeGameplayRulesVersion,
                     String resourceProvenanceHash, boolean exact) { }

    record Environment(String currentHead, boolean reviewBaselineIsAncestor,
                       String javaVersion, String javaVmName, String osName,
                       String osVersion, String osArch, int availableProcessors,
                       long maxHeapBytes, String gradleVersion,
                       String productionSourceBeforeHash,
                       int productionSourceBeforeFileCount,
                       String productionSourceAfterHash,
                       int productionSourceAfterFileCount,
                       String productionResourcesBeforeHash,
                       String productionResourcesAfterHash,
                       boolean productionResourcesUnchanged,
                       String draftRuleIdentity, String draftMetaIdentity,
                       String draftScoringIdentity,
                       String matchEnginePolicyId, String matchEnginePolicyHash,
                       String runtimeProfileId, String configurationHash,
                       String engineImplementationVersion,
                       String activeGameplayRulesVersion,
                       String resourceProvenanceHash,
                       boolean apiSchemaUnchanged, boolean frontendUnchanged) { }

    record Timing(int sampleCount, long minNanos, long medianNanos,
                  long p90Nanos, long maxNanos) {
        static Timing of(List<Long> values) {
            require(!values.isEmpty(), "Timing sample must not be empty");
            List<Long> sorted = values.stream().sorted().toList();
            int size = sorted.size();
            long median = size % 2 == 0
                    ? (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2L
                    : sorted.get(size / 2);
            int p90 = Math.max(0, (int) Math.ceil(size * 0.90) - 1);
            return new Timing(size, sorted.getFirst(), median, sorted.get(p90),
                    sorted.getLast());
        }
    }
}
