package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1;
import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.application.MatchEngineV1InputFactory;
import com.lolfm.application.RealDraftMatchOrchestrator;
import com.lolfm.application.RealDraftMatchPreflightValidator;
import com.lolfm.application.RealMatchApiV1ResponseMapper;
import com.lolfm.application.RealMatchApiV1Service;
import com.lolfm.application.RealMatchPerformanceBaselineV1Harness;
import com.lolfm.application.RealMatchPerformanceBaselineV1Harness.Execution;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Official one-warmup/three-measured, two-fixture baseline in one fresh JVM. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("diagnostic")
@Tag("real-match-performance-baseline-v1")
class RealMatchPerformanceBaselineV1DiagnosticTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired RealMatchApiV1RequestParser requests;
    @Autowired RealMatchApiV1Service service;
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired LckTeamAssembler teams;
    @Autowired RealDraftMatchPreflightValidator preflight;
    @Autowired MatchEngineV1InputFactory inputs;
    @Autowired MatchEngineV1 engine;
    @Autowired RealMatchApiV1ResponseMapper responses;
    @Autowired MatchEngineV1Canonicalizer canonicalizer;

    @Test
    void captureOfficialBaseline() throws Exception {
        Path backendRoot = Path.of("").toAbsolutePath().normalize();
        Path output = backendRoot.resolve(
                "build/reports/real-match-performance-baseline-v1");
        recreateEmptyOutput(output);
        RealMatchPerformanceBaselineV1Artifacts.UpstreamEvidence upstream =
                RealMatchPerformanceBaselineV1Artifacts.verifyUpstreamEvidence(backendRoot);
        RealMatchPerformanceBaselineV1Artifacts.Environment environment =
                environment(backendRoot);
        RealMatchPerformanceBaselineV1Harness harness =
                new RealMatchPerformanceBaselineV1Harness(
                        mapper, requests, service, orchestrator, teams, preflight,
                        inputs, engine, responses, canonicalizer);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30)).build();
        ArrayList<RealMatchPerformanceBaselineV1Artifacts.RunObservation> runs =
                new ArrayList<>();

        for (RealMatchPerformanceBaselineV1Artifacts.FixtureSpec fixture
                : RealMatchPerformanceBaselineV1Artifacts.FIXTURES) {
            for (int sequence = 0; sequence < 4; sequence++) {
                JsonNode body = request(fixture);
                Execution execution = harness.simulate(body, true);
                HttpObservation http = post(client, body, execution.responseCanonicalHash());
                runs.add(observation(fixture, sequence, execution, http));
            }
        }

        RealMatchPerformanceBaselineV1Artifacts.writeOfficial(
                output, runs, environment, upstream, canonicalizer);
        RealMatchPerformanceBaselineV1Artifacts.verifyManifest(output);
        try (var files = Files.list(output)) {
            assertThat(files.map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "SHA256SUMS.txt",
                            "real-match-performance-baseline-v1-analysis.md",
                            "real-match-performance-baseline-v1-contract.json",
                            "real-match-performance-baseline-v1-runs.csv",
                            "real-match-performance-baseline-v1-summary.json");
        }
        System.out.println("REAL_MATCH_PERFORMANCE_BASELINE_CAPTURED output=" + output);
    }

    private JsonNode request(RealMatchPerformanceBaselineV1Artifacts.FixtureSpec fixture) {
        return mapper.createObjectNode()
                .put("schemaVersion", RealMatchApiV1Dtos.REQUEST_SCHEMA)
                .put("blueTeamCode", fixture.blueTeamCode())
                .put("redTeamCode", fixture.redTeamCode())
                .put("seed", fixture.seed());
    }

    private HttpObservation post(
            HttpClient client, JsonNode body, String expectedCanonicalHash
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port
                        + "/api/v1/real-matches/simulate"))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)))
                .build();
        long start = System.nanoTime();
        HttpResponse<byte[]> response = client.send(
                request, HttpResponse.BodyHandlers.ofByteArray());
        long elapsed = System.nanoTime() - start;
        RealMatchApiV1Dtos.Response responseBody = mapper.readValue(
                response.body(), RealMatchApiV1Dtos.Response.class);
        String canonicalHash = canonicalizer.hash(responseBody);
        String encoding = response.headers().firstValue("Content-Encoding").orElse("NONE");
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        byte[] gzip = RealMatchPerformanceBaselineV1Harness.gzip(response.body());
        return new HttpObservation(
                response.statusCode(), encoding, contentType, elapsed,
                response.body().length, gzip.length,
                gzip.length / (double) response.body().length,
                canonicalHash, canonicalHash.equals(expectedCanonicalHash));
    }

    private static RealMatchPerformanceBaselineV1Artifacts.RunObservation observation(
            RealMatchPerformanceBaselineV1Artifacts.FixtureSpec fixture,
            int sequence,
            Execution execution,
            HttpObservation http
    ) {
        RealMatchApiV1Dtos.Response response = execution.response();
        RealMatchApiV1Dtos.Integrity integrity = response.integrity();
        RealMatchApiV1Dtos.RandomFingerprint random = integrity.randomFingerprint();
        return new RealMatchPerformanceBaselineV1Artifacts.RunObservation(
                fixture.fixtureId(), sequence,
                sequence == 0
                        ? RealMatchPerformanceBaselineV1Artifacts.RunKind.WARMUP
                        : RealMatchPerformanceBaselineV1Artifacts.RunKind.MEASURED,
                sequence == 0 ? 0 : sequence,
                fixture.blueTeamCode(), fixture.redTeamCode(), fixture.seed(),
                response.result().winner(), response.result().endReason(),
                response.result().durationSeconds(), response.timeline().events().size(),
                response.timeline().snapshots().size(), integrity.outputHash(),
                integrity.replayProvenanceHash(), integrity.simulatorTimelineHash(),
                integrity.structuredTimelineHash(), random.randomDrawCount(),
                random.randomTraceHash(), execution.responseCanonicalHash(),
                http.responseCanonicalHash(), execution.resultCanonicalHash(),
                execution.timelineCanonicalHash(), integrity.runtimeProfileId(),
                integrity.engineImplementationVersion(), integrity.activeGameplayRulesVersion(),
                integrity.policyId(), integrity.policyHash(), integrity.configurationHash(),
                integrity.resourceProvenanceHash(), execution.timings(),
                http.elapsedNanos(), execution.serializedResponse().length,
                http.bodyBytes(), http.offlineGzipBytes(), http.offlineGzipRatio(),
                execution.independentlySerializedSectionBytes(), http.status(),
                http.contentEncoding(),
                http.semanticExact(),
                http.status() == 200 && http.contentType().startsWith("application/json")
                        && http.semanticExact());
    }

    private static RealMatchPerformanceBaselineV1Artifacts.Environment environment(
            Path backendRoot
    ) throws Exception {
        Path repositoryRoot = backendRoot.getParent();
        String currentHead = command(repositoryRoot, "git", "rev-parse", "HEAD");
        boolean ancestor = commandExit(repositoryRoot, "git", "merge-base", "--is-ancestor",
                RealMatchPerformanceBaselineV1Artifacts.REVIEW_BASELINE_COMMIT, currentHead) == 0;
        RealMatchApiV1ArtifactWriter.SourceTreeIdentity production =
                RealMatchApiV1ArtifactWriter.productionSourceTree(backendRoot);
        RealMatchPerformanceBaselineV1Artifacts.SourceIdentity verification =
                RealMatchPerformanceBaselineV1Artifacts.performanceVerificationSourceIdentity(
                        backendRoot);
        Runtime runtime = Runtime.getRuntime();
        return new RealMatchPerformanceBaselineV1Artifacts.Environment(
                currentHead, ancestor,
                System.getProperty("java.version"), System.getProperty("java.vm.name"),
                System.getProperty("os.name"), System.getProperty("os.version"),
                System.getProperty("os.arch"), runtime.availableProcessors(),
                runtime.maxMemory(), System.getProperty("lolfm.gradleVersion", "UNKNOWN"),
                production.hash(), production.fileCount(),
                verification.hash(), verification.fileCount());
    }

    private static String command(Path directory, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).trim();
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException("Command failed: " + output);
        return output;
    }

    private static int commandExit(Path directory, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }

    private static void recreateEmptyOutput(Path output) throws IOException {
        Path normalized = output.toAbsolutePath().normalize();
        if (!normalized.endsWith(Path.of(
                "build", "reports", "real-match-performance-baseline-v1"))) {
            throw new IllegalArgumentException("Unexpected performance output directory");
        }
        if (Files.exists(normalized)) {
            try (var paths = Files.walk(normalized)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(normalized);
    }

    private record HttpObservation(
            int status,
            String contentEncoding,
            String contentType,
            long elapsedNanos,
            long bodyBytes,
            long offlineGzipBytes,
            double offlineGzipRatio,
            String responseCanonicalHash,
            boolean semanticExact
    ) {
    }
}
