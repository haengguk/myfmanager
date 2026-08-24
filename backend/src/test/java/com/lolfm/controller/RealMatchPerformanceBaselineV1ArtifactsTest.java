package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.RealMatchPerformanceBaselineV1Harness.PhaseTimings;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.TeamSide;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RealMatchPerformanceBaselineV1ArtifactsTest {
    @TempDir Path temporary;

    @Test
    void completeScheduleExcludesWarmupAndUsesRawMeasuredMinMedianMax() {
        List<RealMatchPerformanceBaselineV1Artifacts.RunObservation> runs = validRuns();
        RealMatchPerformanceBaselineV1Artifacts.Summary summary =
                RealMatchPerformanceBaselineV1Artifacts.validateAndSummarize(
                        runs, environment(), upstream());

        var distribution = summary.fixtures().get(
                        "FIXTURE_A_GEN_T1_SEED_73").timings()
                .get("actualLocalHttpEndToEndNanos");
        assertThat(distribution.minNanos()).isEqualTo(10L);
        assertThat(distribution.medianNanos()).isEqualTo(20L);
        assertThat(distribution.maxNanos()).isEqualTo(30L);
        assertThat(summary.status()).isEqualTo(
                "REAL_MATCH_PERFORMANCE_BASELINE_CAPTURED");
        assertThat(summary.aggregation()).isEqualTo("RAW_RUNS_WARMUP_EXCLUDED");
    }

    @Test
    void incompleteScheduleAndFixtureAHandoffDriftAreRejected() {
        List<RealMatchPerformanceBaselineV1Artifacts.RunObservation> complete = validRuns();
        List<RealMatchPerformanceBaselineV1Artifacts.RunObservation> incomplete =
                complete.subList(0, complete.size() - 1);
        assertThatThrownBy(() ->
                RealMatchPerformanceBaselineV1Artifacts.validateAndSummarize(
                        incomplete, environment(), upstream()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly eight");

        List<RealMatchPerformanceBaselineV1Artifacts.RunObservation> drifted =
                new ArrayList<>(validRuns());
        drifted.set(1, copy(drifted.get(1), hash('f'), drifted.get(1).winner(),
                drifted.get(1).randomTraceHash(), true, true));
        assertThatThrownBy(() ->
                RealMatchPerformanceBaselineV1Artifacts.validateAndSummarize(
                        drifted, environment(), upstream()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fixture A drifted");
    }

    @Test
    void resultRandomAndHttpObservationTamperingAreRejected() {
        List<RealMatchPerformanceBaselineV1Artifacts.RunObservation> resultTamper =
                new ArrayList<>(validRuns());
        var original = resultTamper.get(2);
        resultTamper.set(2, copy(original, original.outputHash(), TeamSide.RED,
                original.randomTraceHash(), true, true));
        assertThatThrownBy(() ->
                RealMatchPerformanceBaselineV1Artifacts.validateAndSummarize(
                        resultTamper, environment(), upstream()))
                .isInstanceOf(IllegalStateException.class);

        List<RealMatchPerformanceBaselineV1Artifacts.RunObservation> randomTamper =
                new ArrayList<>(validRuns());
        original = randomTamper.get(3);
        randomTamper.set(3, copy(original, original.outputHash(), original.winner(),
                hash('f'), true, true));
        assertThatThrownBy(() ->
                RealMatchPerformanceBaselineV1Artifacts.validateAndSummarize(
                        randomTamper, environment(), upstream()))
                .isInstanceOf(IllegalStateException.class);

        List<RealMatchPerformanceBaselineV1Artifacts.RunObservation> httpTamper =
                new ArrayList<>(validRuns());
        original = httpTamper.get(4);
        httpTamper.set(4, copy(original, original.outputHash(), original.winner(),
                original.randomTraceHash(), false, true));
        assertThatThrownBy(() ->
                RealMatchPerformanceBaselineV1Artifacts.validateAndSummarize(
                        httpTamper, environment(), upstream()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP observation");
    }

    @Test
    void offlineGzipContractAndManifestAreByteVerified() throws Exception {
        byte[] repetitive = "timeline".repeat(1_000).getBytes(StandardCharsets.UTF_8);
        assertThat(com.lolfm.application.RealMatchPerformanceBaselineV1Harness.gzip(repetitive))
                .hasSizeLessThan(repetitive.length);

        for (String artifact : RealMatchPerformanceBaselineV1Artifacts.ARTIFACTS) {
            Files.writeString(temporary.resolve(artifact), artifact + "\n",
                    StandardCharsets.UTF_8);
        }
        RealMatchPerformanceBaselineV1Artifacts.writeManifest(temporary);
        RealMatchPerformanceBaselineV1Artifacts.verifyManifest(temporary);
        Files.writeString(temporary.resolve(RealMatchPerformanceBaselineV1Artifacts.SUMMARY),
                "tampered\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() ->
                RealMatchPerformanceBaselineV1Artifacts.verifyManifest(temporary))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHA mismatch");
    }

    @Test
    void defaultTestExcludesOfficialDiagnosticAndDedicatedTaskIsSequential() throws Exception {
        String build = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);
        assertThat(build).contains("excludeTags 'diagnostic'")
                .contains("runRealMatchPerformanceBaselineV1")
                .contains("includeTags \"real-match-performance-baseline-v1\"")
                .contains("maxParallelForks = 1")
                .contains("outputs.upToDateWhen { false }");
    }

    @Test
    void httpSemanticComparisonUsesTheTypedResponseContractAfterJsonRoundTrip()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        MatchEngineV1Canonicalizer canonicalizer = new MatchEngineV1Canonicalizer(mapper);
        RealMatchApiV1Dtos.SimulateRequest expected = new RealMatchApiV1Dtos.SimulateRequest(
                RealMatchApiV1Dtos.REQUEST_SCHEMA, "GEN", "T1", "73");
        byte[] httpJson = mapper.writeValueAsBytes(expected);
        RealMatchApiV1Dtos.SimulateRequest typed = mapper.readValue(
                httpJson, RealMatchApiV1Dtos.SimulateRequest.class);

        assertThat(canonicalizer.hash(typed)).isEqualTo(canonicalizer.hash(expected));
        assertThat(typed).isEqualTo(expected);
    }

    private static List<RealMatchPerformanceBaselineV1Artifacts.RunObservation> validRuns() {
        ArrayList<RealMatchPerformanceBaselineV1Artifacts.RunObservation> runs =
                new ArrayList<>();
        long[] measuredHttp = {999L, 30L, 10L, 20L};
        for (var fixture : RealMatchPerformanceBaselineV1Artifacts.FIXTURES) {
            for (int sequence = 0; sequence < 4; sequence++) {
                runs.add(run(fixture, sequence, measuredHttp[sequence]));
            }
        }
        return runs;
    }

    private static RealMatchPerformanceBaselineV1Artifacts.RunObservation run(
            RealMatchPerformanceBaselineV1Artifacts.FixtureSpec fixture,
            int sequence,
            long httpNanos
    ) {
        boolean first = fixture.fixtureId().startsWith("FIXTURE_A");
        String output = first ? fixture.expectedOutputHash() : hash('d');
        TeamSide winner = first ? TeamSide.BLUE : TeamSide.RED;
        int duration = first ? 3_430 : 2_500;
        int events = first ? 517 : 400;
        int snapshots = first ? 344 : 251;
        String responseHash = first ? hash('a') : hash('e');
        String resultHash = first ? hash('b') : hash('a');
        String timelineHash = first ? hash('c') : hash('b');
        PhaseTimings timings = new PhaseTimings(
                10L, 20L, 100L, 2L, 5L, 8L, 10L,
                150L, 145L, 5L, 165L);
        return new RealMatchPerformanceBaselineV1Artifacts.RunObservation(
                fixture.fixtureId(), sequence,
                sequence == 0
                        ? RealMatchPerformanceBaselineV1Artifacts.RunKind.WARMUP
                        : RealMatchPerformanceBaselineV1Artifacts.RunKind.MEASURED,
                sequence == 0 ? 0 : sequence,
                fixture.blueTeamCode(), fixture.redTeamCode(), fixture.seed(),
                winner, GameEndReason.NEXUS_DESTROYED, duration, events, snapshots,
                output, hash('b'), hash('c'), hash('d'), 7_000L, hash('e'),
                responseHash, responseHash, resultHash, timelineHash,
                "BASELINE_V1", "MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V8",
                "MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2", MatchEngineV1Policy.POLICY_ID,
                MatchEngineV1Policy.authoritative().policyHash(),
                MatchEngineV1Policy.authoritative().configurationHash(),
                MatchEngineV1Policy.APPROVED_RESOURCE_PROVENANCE_SHA256,
                timings, httpNanos, 1_000L, 1_000L, 500L, 0.5,
                Map.of("teams", 100L, "draft", 100L, "result", 100L,
                        "timeline", 600L, "integrity", 100L),
                200, "NONE", true, true);
    }

    private static RealMatchPerformanceBaselineV1Artifacts.RunObservation copy(
            RealMatchPerformanceBaselineV1Artifacts.RunObservation source,
            String outputHash,
            TeamSide winner,
            String randomTraceHash,
            boolean httpSemanticExact,
            boolean valid
    ) {
        return new RealMatchPerformanceBaselineV1Artifacts.RunObservation(
                source.fixtureId(), source.fixtureSequence(), source.runKind(),
                source.measuredOrdinal(), source.blueTeamCode(), source.redTeamCode(),
                source.seed(), winner, source.endReason(), source.durationSeconds(),
                source.eventCount(), source.snapshotCount(), outputHash,
                source.replayProvenanceHash(), source.simulatorTimelineHash(),
                source.structuredTimelineHash(), source.randomDrawCount(), randomTraceHash,
                source.responseCanonicalHash(), source.httpResponseCanonicalHash(),
                source.resultCanonicalHash(), source.timelineCanonicalHash(),
                source.runtimeProfileId(), source.engineImplementationVersion(),
                source.activeGameplayRulesVersion(), source.policyId(), source.policyHash(),
                source.configurationHash(), source.resourceProvenanceHash(), source.timings(),
                source.actualLocalHttpEndToEndNanos(), source.serializedResponseBytes(),
                source.httpPayloadBytes(), source.offlineGzipBytes(),
                source.offlineGzipRatio(), source.independentlySerializedSectionBytes(),
                source.httpStatus(), source.httpContentEncoding(), httpSemanticExact, valid);
    }

    private static RealMatchPerformanceBaselineV1Artifacts.Environment environment() {
        return new RealMatchPerformanceBaselineV1Artifacts.Environment(
                hash40('a'), true, "21", "test-vm", "test-os", "1", "x86_64",
                4, 1_000_000L, "test-gradle", hash('a'), 1, hash('b'), 3);
    }

    private static RealMatchPerformanceBaselineV1Artifacts.UpstreamEvidence upstream() {
        return new RealMatchPerformanceBaselineV1Artifacts.UpstreamEvidence(
                RealMatchPerformanceBaselineV1Artifacts.UPSTREAM_HANDOFF_MANIFEST_SHA256, 6,
                RealMatchPerformanceBaselineV1Artifacts.MATCH_ENGINE_FREEZE_MANIFEST_SHA256, 7,
                "RAW_SHA256_VERIFIED_NO_REGENERATION");
    }

    private static String hash(char value) {
        return String.valueOf(value).repeat(64);
    }

    private static String hash40(char value) {
        return String.valueOf(value).repeat(40);
    }
}
