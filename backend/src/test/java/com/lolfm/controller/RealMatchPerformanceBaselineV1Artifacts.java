package com.lolfm.controller;

import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.RealMatchPerformanceBaselineV1Harness.PhaseTimings;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.simulator.GameEndReason;
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
import java.util.Objects;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

/** Contract, validation, aggregation and byte-level finalization for the V1 baseline. */
final class RealMatchPerformanceBaselineV1Artifacts {
    static final String STATUS = "REAL_MATCH_PERFORMANCE_BASELINE_CAPTURED";
    static final String REVIEW_BASELINE_COMMIT =
            "de8b418c826ab4fb161935a7cb4b5ec8334246b0";
    static final String UPSTREAM_HANDOFF_MANIFEST_SHA256 =
            "fc4f96158d6c6b1d6e9b30d8441da89a2643f9d25faa8e7218434b49b4909525";
    static final String MATCH_ENGINE_FREEZE_MANIFEST_SHA256 =
            "1f5bc20c347d25d833e822325de1fa294dc61d38c55da121ea30d15ab70a0728";
    static final String CONTRACT = "real-match-performance-baseline-v1-contract.json";
    static final String RUNS = "real-match-performance-baseline-v1-runs.csv";
    static final String SUMMARY = "real-match-performance-baseline-v1-summary.json";
    static final String ANALYSIS = "real-match-performance-baseline-v1-analysis.md";
    static final String MANIFEST = "SHA256SUMS.txt";
    static final List<String> ARTIFACTS = List.of(CONTRACT, RUNS, SUMMARY, ANALYSIS);
    static final List<FixtureSpec> FIXTURES = List.of(
            new FixtureSpec(
                    "FIXTURE_A_GEN_T1_SEED_73", "GEN", "T1", "73",
                    "bdc597af083aa4f081cf4fe7a242d0e36eec7744b186d998d6f83b717648e874",
                    TeamSide.BLUE, GameEndReason.NEXUS_DESTROYED, 3_430, 517, 344),
            new FixtureSpec(
                    "FIXTURE_B_HLE_DK_SEED_NEGATIVE_73", "HLE", "DK", "-73",
                    null, null, null, null, null, null));

    private RealMatchPerformanceBaselineV1Artifacts() {
    }

    static Summary validateAndSummarize(
            List<RunObservation> source,
            Environment environment,
            UpstreamEvidence upstream
    ) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(upstream, "upstream");
        require(environment.currentHead().matches("[0-9a-f]{40}"),
                "Current HEAD is missing");
        require(environment.reviewBaselineIsAncestor(),
                "Review baseline is not an ancestor of current HEAD");
        require(upstream.realMatchHandoffManifestSha256().equals(
                        UPSTREAM_HANDOFF_MANIFEST_SHA256)
                        && upstream.matchEngineFreezeManifestSha256().equals(
                        MATCH_ENGINE_FREEZE_MANIFEST_SHA256)
                        && upstream.realMatchHandoffEntryCount() == 6
                        && upstream.matchEngineFreezeEntryCount() == 7,
                "Upstream handoff or freeze evidence drifted");

        List<RunObservation> runs = List.copyOf(source);
        require(runs.size() == FIXTURES.size() * 4,
                "Official baseline requires exactly eight run observations");
        int offset = 0;
        LinkedHashMap<String, FixtureSummary> fixtureSummaries = new LinkedHashMap<>();
        ArrayList<Bottleneck> bottlenecks = new ArrayList<>();
        for (FixtureSpec fixture : FIXTURES) {
            List<RunObservation> fixtureRuns = runs.subList(offset, offset + 4);
            validateSchedule(fixture, fixtureRuns);
            RunObservation authoritative = fixtureRuns.get(1);
            validateReference(fixture, authoritative);
            for (RunObservation run : fixtureRuns) {
                validateRun(run);
                require(sameObservationIdentity(authoritative, run),
                        "Repeated result/output/replay/timeline/random mismatch: "
                                + fixture.fixtureId());
            }
            List<RunObservation> measured = fixtureRuns.subList(1, 4);
            FixtureSummary summary = fixtureSummary(fixture, authoritative, measured);
            fixtureSummaries.put(fixture.fixtureId(), summary);
            bottlenecks.add(summary.dominantMeasuredPhase());
            offset += 4;
        }
        return new Summary(
                "REAL_MATCH_PERFORMANCE_BASELINE_V1_SUMMARY",
                STATUS,
                "MEASUREMENT_ONLY_NO_OPTIMIZATION",
                "RAW_RUNS_WARMUP_EXCLUDED",
                1,
                3,
                8,
                Map.copyOf(fixtureSummaries),
                List.copyOf(bottlenecks),
                environment,
                upstream,
                List.of(
                        "No gameplay, tuning, schema, compression, async, streaming or frontend change was made.",
                        "Timing values are environment-specific observations, not acceptance gates.",
                        "Offline gzip is a size observation only; HTTP compression remains inactive."));
    }

    static void writeOfficial(
            Path output,
            List<RunObservation> runs,
            Environment environment,
            UpstreamEvidence upstream,
            MatchEngineV1Canonicalizer canonicalizer
    ) throws IOException {
        Summary summary = validateAndSummarize(runs, environment, upstream);
        Path normalized = output.toAbsolutePath().normalize();
        require(normalized.endsWith(Path.of(
                        "build", "reports", "real-match-performance-baseline-v1")),
                "Unexpected official baseline output directory");
        Files.createDirectories(normalized);
        require(isEmpty(normalized), "Official baseline output directory must be fresh");

        writeJson(normalized.resolve(CONTRACT), contract(environment, upstream), canonicalizer);
        Files.writeString(normalized.resolve(RUNS), runsCsv(runs), StandardCharsets.UTF_8);
        writeJson(normalized.resolve(SUMMARY), summary, canonicalizer);
        Files.writeString(normalized.resolve(ANALYSIS), analysis(summary), StandardCharsets.UTF_8);
        writeManifest(normalized);
        verifyManifest(normalized);
    }

    static Map<String, Object> contract(Environment environment, UpstreamEvidence upstream) {
        LinkedHashMap<String, Object> contract = new LinkedHashMap<>();
        contract.put("schemaVersion", "REAL_MATCH_PERFORMANCE_BASELINE_V1_CONTRACT");
        contract.put("status", STATUS);
        contract.put("scope", "MEASUREMENT_ONLY_NO_OPTIMIZATION");
        contract.put("reviewBaselineCommit", REVIEW_BASELINE_COMMIT);
        contract.put("environment", environment);
        contract.put("upstreamEvidence", upstream);
        contract.put("fixtures", FIXTURES);
        contract.put("runPolicy", Map.of(
                "fixtureOrder", FIXTURES.stream().map(FixtureSpec::fixtureId).toList(),
                "sameFreshJvm", true,
                "parallelExecution", false,
                "warmupRunsPerFixture", 1,
                "measuredRunsPerFixture", 3,
                "aggregation", "MIN_MEDIAN_MAX_FROM_THREE_RAW_MEASURED_RUNS",
                "warmupIncludedInAggregation", false));
        contract.put("timingSemantics", Map.ofEntries(
                Map.entry("clock", "System.nanoTime monotonic elapsed time"),
                Map.entry("requestValidationAndPreflightNanos",
                        "strict JSON/request validation plus Draft preflight; disjoint intervals summed"),
                Map.entry("rosterDraftInputPreparationNanos",
                        "roster assembly, Draft contexts, DraftEngine and MatchEngine input"),
                Map.entry("matchEngineExecutionNanos", "MatchEngineV1.executeDetailed"),
                Map.entry("orchestrationFinalizationNanos",
                        "fresh series commit and commit invariant validation"),
                Map.entry("outputIntegrityValidationNanos",
                        "the production RealMatchApiV1Service output validation boundary"),
                Map.entry("responseMappingNanos", "RealMatchApiV1ResponseMapper.response"),
                Map.entry("jsonSerializationNanos", "ObjectMapper response bytes"),
                Map.entry("applicationBoundaryTotalNanos",
                        "request parser through response mapping in the decomposed production-equivalent path"),
                Map.entry("actualLocalHttpEndToEndNanos",
                        "separate exact replay via live random-port Spring HTTP endpoint, client request to full body"),
                Map.entry("unattributedApplicationOverheadNanos",
                        "application boundary total minus named application phase sum")));
        contract.put("payloadSemantics", Map.of(
                "httpPayloadBytes", "actual uncompressed local HTTP response body bytes",
                "serializedResponseBytes", "in-process ObjectMapper response bytes",
                "offlineGzipBytes", "GZIPOutputStream over actual HTTP response body",
                "compressionActivated", false,
                "sectionBytes", "each top-level value serialized independently; values are not additive"));
        contract.put("integrityRules", List.of(
                "Fixture A must exactly match the frozen Real Match V8 handoff.",
                "Fixture B measured run 1 becomes the authoritative repeated-run identity.",
                "Every fixture run must match result, output, replay, timeline and Random identity.",
                "Timing collection is observational and excluded from all response hashes.",
                "Incomplete, synthetic or tampered observations cannot be finalized."));
        contract.put("nonGoals", List.of(
                "OPTIMIZATION", "GAMEPLAY_TUNING", "SCHEMA_REDESIGN", "ASYNC_JOB",
                "STREAMING", "HTTP_COMPRESSION_ACTIVATION", "FRONTEND_CHANGE",
                "REAL_MATCH_HANDOFF_REGENERATION"));
        return Map.copyOf(contract);
    }

    static void verifyManifest(Path output) throws IOException {
        Path manifest = output.resolve(MANIFEST);
        require(Files.isRegularFile(manifest), "Missing performance baseline manifest");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        require(lines.size() == ARTIFACTS.size(), "Performance manifest count mismatch");
        for (int index = 0; index < ARTIFACTS.size(); index++) {
            String[] fields = lines.get(index).split("  ", 2);
            require(fields.length == 2 && fields[1].equals(ARTIFACTS.get(index)),
                    "Performance manifest ordering mismatch");
            require(sha256(Files.readAllBytes(output.resolve(fields[1]))).equals(fields[0]),
                    "Performance manifest SHA mismatch: " + fields[1]);
        }
    }

    static UpstreamEvidence verifyUpstreamEvidence(Path backendRoot) throws IOException {
        ManifestEvidence handoff = verifyExistingManifest(
                backendRoot.resolve("build/reports/real-match-api-v1"),
                6, UPSTREAM_HANDOFF_MANIFEST_SHA256);
        ManifestEvidence freeze = verifyExistingManifest(
                backendRoot.resolve("build/reports/match-engine-v1-freeze"),
                7, MATCH_ENGINE_FREEZE_MANIFEST_SHA256);
        return new UpstreamEvidence(
                handoff.manifestSha256(), handoff.entryCount(),
                freeze.manifestSha256(), freeze.entryCount(),
                "RAW_SHA256_VERIFIED_NO_REGENERATION");
    }

    static SourceIdentity performanceVerificationSourceIdentity(Path backendRoot)
            throws IOException {
        Path root = backendRoot.toAbsolutePath().normalize();
        Path tests = root.resolve("src/test/java");
        ArrayList<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(tests)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .startsWith("RealMatchPerformanceBaselineV1"))
                    .forEach(files::add);
        }
        files.sort(Comparator.comparing(path -> root.relativize(path).toString()
                .replace('\\', '/')));
        require(!files.isEmpty(), "Performance verification source set is empty");
        StringBuilder canonical = new StringBuilder(
                "sourceTreeIdentitySchema=REAL_MATCH_PERFORMANCE_BASELINE_V1_SOURCE_V1\n");
        for (Path file : files) {
            canonical.append("file=").append(root.relativize(file).toString()
                            .replace('\\', '/')).append('\n')
                    .append("rawSha256=").append(sha256(Files.readAllBytes(file))).append('\n');
        }
        return new SourceIdentity(sha256(canonical.toString().getBytes(StandardCharsets.UTF_8)),
                files.size());
    }

    private static ManifestEvidence verifyExistingManifest(
            Path directory, int expectedEntries, String expectedManifestSha
    ) throws IOException {
        Path manifest = directory.resolve(MANIFEST);
        require(Files.isRegularFile(manifest), "Missing upstream manifest: " + directory);
        require(sha256(Files.readAllBytes(manifest)).equals(expectedManifestSha),
                "Upstream manifest raw SHA drift: " + directory);
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        require(lines.size() == expectedEntries, "Upstream manifest entry count drift");
        for (String line : lines) {
            String[] fields = line.split("  ", 2);
            require(fields.length == 2 && fields[0].matches("[0-9a-f]{64}"),
                    "Malformed upstream manifest line");
            Path file = directory.resolve(fields[1]).normalize();
            require(file.startsWith(directory.normalize()) && Files.isRegularFile(file),
                    "Missing upstream manifest file: " + fields[1]);
            require(sha256(Files.readAllBytes(file)).equals(fields[0]),
                    "Upstream artifact raw SHA mismatch: " + fields[1]);
        }
        return new ManifestEvidence(expectedManifestSha, expectedEntries);
    }

    private static FixtureSummary fixtureSummary(
            FixtureSpec fixture,
            RunObservation authoritative,
            List<RunObservation> measured
    ) {
        LinkedHashMap<String, Distribution> timings = new LinkedHashMap<>();
        add(timings, "requestValidationAndPreflightNanos", measured,
                run -> run.timings().requestValidationAndPreflightNanos());
        add(timings, "rosterDraftInputPreparationNanos", measured,
                run -> run.timings().rosterDraftInputPreparationNanos());
        add(timings, "matchEngineExecutionNanos", measured,
                run -> run.timings().matchEngineExecutionNanos());
        add(timings, "orchestrationFinalizationNanos", measured,
                run -> run.timings().orchestrationFinalizationNanos());
        add(timings, "outputIntegrityValidationNanos", measured,
                run -> run.timings().outputIntegrityValidationNanos());
        add(timings, "responseMappingNanos", measured,
                run -> run.timings().responseMappingNanos());
        add(timings, "jsonSerializationNanos", measured,
                run -> run.timings().jsonSerializationNanos());
        add(timings, "applicationBoundaryTotalNanos", measured,
                run -> run.timings().applicationBoundaryTotalNanos());
        add(timings, "unattributedApplicationOverheadNanos", measured,
                run -> run.timings().unattributedApplicationOverheadNanos());
        add(timings, "actualLocalHttpEndToEndNanos", measured,
                RunObservation::actualLocalHttpEndToEndNanos);

        Map.Entry<String, Distribution> dominant = timings.entrySet().stream()
                .filter(entry -> List.of(
                        "requestValidationAndPreflightNanos",
                        "rosterDraftInputPreparationNanos",
                        "matchEngineExecutionNanos",
                        "orchestrationFinalizationNanos",
                        "outputIntegrityValidationNanos",
                        "responseMappingNanos",
                        "jsonSerializationNanos").contains(entry.getKey()))
                .max(Comparator.comparingLong(entry -> entry.getValue().medianNanos()))
                .orElseThrow();
        double applicationMedian = timings.get("applicationBoundaryTotalNanos").medianNanos();
        Bottleneck bottleneck = new Bottleneck(
                fixture.fixtureId(), dominant.getKey(), dominant.getValue().medianMillis(),
                applicationMedian == 0.0 ? 0.0
                        : dominant.getValue().medianNanos() / applicationMedian);
        return new FixtureSummary(
                fixture, authoritative.resultIdentity(), authoritative.outputHash(),
                authoritative.replayProvenanceHash(),
                authoritative.simulatorTimelineHash(),
                authoritative.structuredTimelineHash(),
                authoritative.randomDrawCount(), authoritative.randomTraceHash(),
                authoritative.httpPayloadBytes(), authoritative.offlineGzipBytes(),
                authoritative.offlineGzipRatio(),
                authoritative.independentlySerializedSectionBytes(),
                Map.copyOf(timings), bottleneck);
    }

    private static void add(
            Map<String, Distribution> target,
            String key,
            List<RunObservation> runs,
            ToLongFunction<RunObservation> extractor
    ) {
        target.put(key, distribution(runs.stream().mapToLong(extractor).toArray()));
    }

    static Distribution distribution(long[] values) {
        require(values.length == 3, "Distribution requires three measured raw values");
        long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return new Distribution(sorted[0], sorted[1], sorted[2],
                nanosToMillis(sorted[0]), nanosToMillis(sorted[1]),
                nanosToMillis(sorted[2]));
    }

    private static void validateSchedule(FixtureSpec fixture, List<RunObservation> runs) {
        require(runs.size() == 4, "Fixture run count mismatch");
        for (int index = 0; index < runs.size(); index++) {
            RunObservation run = runs.get(index);
            require(run.fixtureId().equals(fixture.fixtureId())
                            && run.blueTeamCode().equals(fixture.blueTeamCode())
                            && run.redTeamCode().equals(fixture.redTeamCode())
                            && run.seed().equals(fixture.seed())
                            && run.fixtureSequence() == index,
                    "Fixture schedule identity mismatch: " + fixture.fixtureId());
            require(index == 0 ? run.runKind() == RunKind.WARMUP
                            : run.runKind() == RunKind.MEASURED,
                    "Warmup/measured schedule mismatch: " + fixture.fixtureId());
            require(run.measuredOrdinal() == (index == 0 ? 0 : index),
                    "Measured ordinal mismatch: " + fixture.fixtureId());
        }
    }

    private static void validateReference(FixtureSpec fixture, RunObservation run) {
        if (fixture.expectedOutputHash() == null) return;
        require(run.outputHash().equals(fixture.expectedOutputHash())
                        && run.winner() == fixture.expectedWinner()
                        && run.endReason() == fixture.expectedEndReason()
                        && run.durationSeconds() == fixture.expectedDurationSeconds()
                        && run.eventCount() == fixture.expectedEventCount()
                        && run.snapshotCount() == fixture.expectedSnapshotCount(),
                "Fixture A drifted from frozen Real Match V8 handoff");
    }

    private static void validateRun(RunObservation run) {
        require(run.valid() && run.httpStatus() == 200 && run.httpSemanticExact()
                        && run.httpContentEncoding().equals("NONE"),
                "Invalid or partial HTTP observation");
        require(run.runtimeProfileId().equals("BASELINE_V1")
                        && run.engineImplementationVersion().equals(
                        "MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V8")
                        && run.policyId().equals(MatchEngineV1Policy.POLICY_ID)
                        && run.policyHash().equals(MatchEngineV1Policy.authoritative().policyHash())
                        && run.configurationHash().equals(
                        MatchEngineV1Policy.authoritative().configurationHash())
                        && run.resourceProvenanceHash().equals(
                        MatchEngineV1Policy.APPROVED_RESOURCE_PROVENANCE_SHA256),
                "Historical V8 policy/configuration/resource identity drift");
        require(run.outputHash().matches("[0-9a-f]{64}")
                        && run.replayProvenanceHash().matches("[0-9a-f]{64}")
                        && run.simulatorTimelineHash().matches("[0-9a-f]{64}")
                        && run.structuredTimelineHash().matches("[0-9a-f]{64}")
                        && run.randomTraceHash().matches("[0-9a-f]{64}")
                        && run.responseCanonicalHash().matches("[0-9a-f]{64}")
                        && run.httpResponseCanonicalHash().equals(run.responseCanonicalHash()),
                "Output/result/observation hash is incomplete or tampered");
        require(run.randomDrawCount() > 0L && run.eventCount() > 0
                        && run.snapshotCount() > 0 && run.durationSeconds() > 0,
                "Result observation is incomplete");
        require(run.timings().applicationBoundaryTotalNanos() > 0L
                        && run.timings().matchEngineExecutionNanos() > 0L
                        && run.timings().instrumentedRequestToJsonTotalNanos() > 0L
                        && run.actualLocalHttpEndToEndNanos() > 0L,
                "Timing coverage is incomplete");
        require(run.serializedResponseBytes() > 0L && run.httpPayloadBytes() > 0L
                        && run.offlineGzipBytes() > 0L
                        && run.offlineGzipBytes() < run.httpPayloadBytes()
                        && Double.isFinite(run.offlineGzipRatio())
                        && run.offlineGzipRatio() > 0.0 && run.offlineGzipRatio() < 1.0,
                "Payload or offline gzip observation is invalid");
        require(run.independentlySerializedSectionBytes().keySet().equals(
                        java.util.Set.of("teams", "draft", "result", "timeline", "integrity"))
                        && run.independentlySerializedSectionBytes().values().stream()
                        .allMatch(value -> value > 0L),
                "Independent section-size observation is incomplete");
    }

    private static boolean sameObservationIdentity(RunObservation expected, RunObservation actual) {
        return expected.responseCanonicalHash().equals(actual.responseCanonicalHash())
                && expected.resultCanonicalHash().equals(actual.resultCanonicalHash())
                && expected.timelineCanonicalHash().equals(actual.timelineCanonicalHash())
                && expected.resultIdentity().equals(actual.resultIdentity())
                && expected.outputHash().equals(actual.outputHash())
                && expected.replayProvenanceHash().equals(actual.replayProvenanceHash())
                && expected.simulatorTimelineHash().equals(actual.simulatorTimelineHash())
                && expected.structuredTimelineHash().equals(actual.structuredTimelineHash())
                && expected.randomDrawCount() == actual.randomDrawCount()
                && expected.randomTraceHash().equals(actual.randomTraceHash())
                && expected.policyHash().equals(actual.policyHash())
                && expected.configurationHash().equals(actual.configurationHash())
                && expected.resourceProvenanceHash().equals(actual.resourceProvenanceHash());
    }

    private static String runsCsv(List<RunObservation> runs) {
        StringBuilder csv = new StringBuilder();
        csv.append("fixtureId,fixtureSequence,runKind,measuredOrdinal,blueTeamCode,redTeamCode,seed,")
                .append("winner,endReason,durationSeconds,eventCount,snapshotCount,outputHash,")
                .append("replayProvenanceHash,simulatorTimelineHash,structuredTimelineHash,")
                .append("randomDrawCount,randomTraceHash,responseCanonicalHash,resultCanonicalHash,")
                .append("timelineCanonicalHash,requestValidationAndPreflightNanos,")
                .append("rosterDraftInputPreparationNanos,matchEngineExecutionNanos,")
                .append("orchestrationFinalizationNanos,outputIntegrityValidationNanos,")
                .append("responseMappingNanos,jsonSerializationNanos,applicationBoundaryTotalNanos,")
                .append("applicationPhaseSumNanos,unattributedApplicationOverheadNanos,")
                .append("instrumentedRequestToJsonTotalNanos,actualLocalHttpEndToEndNanos,")
                .append("serializedResponseBytes,httpPayloadBytes,offlineGzipBytes,")
                .append("offlineGzipRatio,teamsBytes,draftBytes,resultBytes,timelineBytes,")
                .append("integrityBytes,httpStatus,httpContentEncoding,httpSemanticExact,valid\n");
        for (RunObservation run : runs) {
            PhaseTimings timing = run.timings();
            csv.append(run.fixtureId()).append(',').append(run.fixtureSequence()).append(',')
                    .append(run.runKind()).append(',').append(run.measuredOrdinal()).append(',')
                    .append(run.blueTeamCode()).append(',').append(run.redTeamCode()).append(',')
                    .append(run.seed()).append(',').append(run.winner()).append(',')
                    .append(run.endReason()).append(',').append(run.durationSeconds()).append(',')
                    .append(run.eventCount()).append(',').append(run.snapshotCount()).append(',')
                    .append(run.outputHash()).append(',').append(run.replayProvenanceHash()).append(',')
                    .append(run.simulatorTimelineHash()).append(',')
                    .append(run.structuredTimelineHash()).append(',')
                    .append(run.randomDrawCount()).append(',').append(run.randomTraceHash()).append(',')
                    .append(run.responseCanonicalHash()).append(',')
                    .append(run.resultCanonicalHash()).append(',')
                    .append(run.timelineCanonicalHash()).append(',')
                    .append(timing.requestValidationAndPreflightNanos()).append(',')
                    .append(timing.rosterDraftInputPreparationNanos()).append(',')
                    .append(timing.matchEngineExecutionNanos()).append(',')
                    .append(timing.orchestrationFinalizationNanos()).append(',')
                    .append(timing.outputIntegrityValidationNanos()).append(',')
                    .append(timing.responseMappingNanos()).append(',')
                    .append(timing.jsonSerializationNanos()).append(',')
                    .append(timing.applicationBoundaryTotalNanos()).append(',')
                    .append(timing.applicationPhaseSumNanos()).append(',')
                    .append(timing.unattributedApplicationOverheadNanos()).append(',')
                    .append(timing.instrumentedRequestToJsonTotalNanos()).append(',')
                    .append(run.actualLocalHttpEndToEndNanos()).append(',')
                    .append(run.serializedResponseBytes()).append(',')
                    .append(run.httpPayloadBytes()).append(',')
                    .append(run.offlineGzipBytes()).append(',')
                    .append(run.offlineGzipRatio()).append(',')
                    .append(run.independentlySerializedSectionBytes().get("teams")).append(',')
                    .append(run.independentlySerializedSectionBytes().get("draft")).append(',')
                    .append(run.independentlySerializedSectionBytes().get("result")).append(',')
                    .append(run.independentlySerializedSectionBytes().get("timeline")).append(',')
                    .append(run.independentlySerializedSectionBytes().get("integrity")).append(',')
                    .append(run.httpStatus()).append(',').append(run.httpContentEncoding()).append(',')
                    .append(run.httpSemanticExact()).append(',').append(run.valid()).append('\n');
        }
        return csv.toString();
    }

    private static String analysis(Summary summary) {
        StringBuilder markdown = new StringBuilder("# Real Match Performance Baseline V1\n\n")
                .append("Status: `").append(summary.status()).append("`\n\n")
                .append("이 문서는 현재 Real Match V1의 재현 가능한 측정 기준선이다. ")
                .append("최적화·게임플레이 튜닝·압축 활성화·비동기화는 수행하지 않았다.\n\n")
                .append("## 측정 결과\n\n")
                .append("| Fixture | 결과 | 앱 경계 median | 엔진 median | 실제 local HTTP median | raw JSON | offline gzip |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (FixtureSummary fixture : summary.fixtures().values().stream()
                .sorted(Comparator.comparing(value -> value.fixture().fixtureId())).toList()) {
            markdown.append('|').append(' ').append(fixture.fixture().fixtureId()).append(" | ")
                    .append(fixture.result().winner()).append(" / ")
                    .append(fixture.result().durationSeconds()).append("s | ")
                    .append(format(fixture.timings().get("applicationBoundaryTotalNanos")
                            .medianMillis())).append(" ms | ")
                    .append(format(fixture.timings().get("matchEngineExecutionNanos")
                            .medianMillis())).append(" ms | ")
                    .append(format(fixture.timings().get("actualLocalHttpEndToEndNanos")
                            .medianMillis())).append(" ms | ")
                    .append(fixture.httpPayloadBytes()).append(" B | ")
                    .append(fixture.offlineGzipBytes()).append(" B (")
                    .append(format(fixture.offlineGzipRatio() * 100.0)).append("%) |\n");
        }
        markdown.append("\n## 병목 해석\n\n");
        for (Bottleneck bottleneck : summary.dominantMeasuredPhases()) {
            markdown.append("- `").append(bottleneck.fixtureId()).append("`: `")
                    .append(bottleneck.phase()).append("` median ")
                    .append(format(bottleneck.medianMillis())).append(" ms, 앱 경계 median의 ")
                    .append(format(bottleneck.shareOfApplicationBoundaryMedian() * 100.0))
                    .append("%.\n");
        }
        return markdown.append("\nJSON 직렬화 및 실제 HTTP 전송은 엔진 실행과 별도로 측정했다. ")
                .append("section byte는 각 top-level 값을 독립 직렬화한 값이므로 합산하지 않는다. ")
                .append("offline gzip은 압축 잠재 크기 관찰일 뿐 서버 압축을 켠 결과가 아니다.\n\n")
                .append("다음 권장 단계는 이 기준선으로 가장 큰 실제 phase를 대상으로 한 별도 최적화 ")
                .append("milestone의 범위와 성능 예산을 먼저 정하는 것이다.\n")
                .toString();
    }

    private static void writeJson(
            Path path, Object value, MatchEngineV1Canonicalizer canonicalizer
    ) throws IOException {
        Files.writeString(path, canonicalizer.canonicalJson(value) + '\n',
                StandardCharsets.UTF_8);
    }

    static void writeManifest(Path output) throws IOException {
        StringBuilder manifest = new StringBuilder();
        for (String artifact : ARTIFACTS) {
            manifest.append(sha256(Files.readAllBytes(output.resolve(artifact))))
                    .append("  ").append(artifact).append('\n');
        }
        Files.writeString(output.resolve(MANIFEST), manifest, StandardCharsets.UTF_8);
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.findAny().isEmpty();
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    enum RunKind { WARMUP, MEASURED }

    record FixtureSpec(
            String fixtureId,
            String blueTeamCode,
            String redTeamCode,
            String seed,
            String expectedOutputHash,
            TeamSide expectedWinner,
            GameEndReason expectedEndReason,
            Integer expectedDurationSeconds,
            Integer expectedEventCount,
            Integer expectedSnapshotCount
    ) {
    }

    record ResultIdentity(
            TeamSide winner,
            GameEndReason endReason,
            int durationSeconds,
            int eventCount,
            int snapshotCount
    ) {
    }

    record RunObservation(
            String fixtureId,
            int fixtureSequence,
            RunKind runKind,
            int measuredOrdinal,
            String blueTeamCode,
            String redTeamCode,
            String seed,
            TeamSide winner,
            GameEndReason endReason,
            int durationSeconds,
            int eventCount,
            int snapshotCount,
            String outputHash,
            String replayProvenanceHash,
            String simulatorTimelineHash,
            String structuredTimelineHash,
            long randomDrawCount,
            String randomTraceHash,
            String responseCanonicalHash,
            String httpResponseCanonicalHash,
            String resultCanonicalHash,
            String timelineCanonicalHash,
            String runtimeProfileId,
            String engineImplementationVersion,
            String activeGameplayRulesVersion,
            String policyId,
            String policyHash,
            String configurationHash,
            String resourceProvenanceHash,
            PhaseTimings timings,
            long actualLocalHttpEndToEndNanos,
            long serializedResponseBytes,
            long httpPayloadBytes,
            long offlineGzipBytes,
            double offlineGzipRatio,
            Map<String, Long> independentlySerializedSectionBytes,
            int httpStatus,
            String httpContentEncoding,
            boolean httpSemanticExact,
            boolean valid
    ) {
        RunObservation {
            independentlySerializedSectionBytes = Map.copyOf(
                    independentlySerializedSectionBytes);
        }

        ResultIdentity resultIdentity() {
            return new ResultIdentity(winner, endReason, durationSeconds, eventCount, snapshotCount);
        }
    }

    record Distribution(
            long minNanos,
            long medianNanos,
            long maxNanos,
            double minMillis,
            double medianMillis,
            double maxMillis
    ) {
    }

    record Bottleneck(
            String fixtureId,
            String phase,
            double medianMillis,
            double shareOfApplicationBoundaryMedian
    ) {
    }

    record FixtureSummary(
            FixtureSpec fixture,
            ResultIdentity result,
            String outputHash,
            String replayProvenanceHash,
            String simulatorTimelineHash,
            String structuredTimelineHash,
            long randomDrawCount,
            String randomTraceHash,
            long httpPayloadBytes,
            long offlineGzipBytes,
            double offlineGzipRatio,
            Map<String, Long> independentlySerializedSectionBytes,
            Map<String, Distribution> timings,
            Bottleneck dominantMeasuredPhase
    ) {
    }

    record Environment(
            String currentHead,
            boolean reviewBaselineIsAncestor,
            String javaVersion,
            String javaVm,
            String osName,
            String osVersion,
            String osArch,
            int availableProcessors,
            long maxHeapBytes,
            String gradleVersion,
            String backendProductionSourceHash,
            int backendProductionSourceFileCount,
            String realMatchVerificationSourceHash,
            int realMatchVerificationSourceFileCount
    ) {
    }

    record UpstreamEvidence(
            String realMatchHandoffManifestSha256,
            int realMatchHandoffEntryCount,
            String matchEngineFreezeManifestSha256,
            int matchEngineFreezeEntryCount,
            String verification
    ) {
    }

    record SourceIdentity(String hash, int fileCount) {
    }

    record Summary(
            String schemaVersion,
            String status,
            String scope,
            String aggregation,
            int warmupRunsPerFixture,
            int measuredRunsPerFixture,
            int totalRunCount,
            Map<String, FixtureSummary> fixtures,
            List<Bottleneck> dominantMeasuredPhases,
            Environment environment,
            UpstreamEvidence upstreamEvidence,
            List<String> limitations
    ) {
    }

    private record ManifestEvidence(String manifestSha256, int entryCount) {
    }
}
