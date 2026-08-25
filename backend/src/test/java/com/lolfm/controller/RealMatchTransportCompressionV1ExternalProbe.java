package com.lolfm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.simulator.TeamSide;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/** External transport probe for a caller-owned bootRun or packaged JAR. */
public final class RealMatchTransportCompressionV1ExternalProbe {
    private RealMatchTransportCompressionV1ExternalProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 8) {
            throw new IllegalArgumentException(
                    "Expected <baseUrl> <launchMode> <pid> <fixtureId> <blue> <red> <seed> <output>");
        }
        String baseUrl = required(args[0], "baseUrl");
        String launchMode = required(args[1], "launchMode");
        long pid = Long.parseLong(args[2]);
        Fixture fixture = Fixture.require(args[3], args[4], args[5], args[6]);
        Path output = Path.of(args[7]).toAbsolutePath().normalize();

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.INDENT_OUTPUT);
        MatchEngineV1Canonicalizer canonicalizer = new MatchEngineV1Canonicalizer(mapper);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30)).build();
        byte[] requestBody = mapper.writeValueAsBytes(Map.of(
                "schemaVersion", RealMatchApiV1Dtos.REQUEST_SCHEMA,
                "blueTeamCode", fixture.blueTeamCode(),
                "redTeamCode", fixture.redTeamCode(),
                "seed", fixture.seed()));

        Run gzipFirst = simulate(client, mapper, canonicalizer, baseUrl, requestBody, "gzip");
        Run gzipWarm = simulate(client, mapper, canonicalizer, baseUrl, requestBody, "gzip");
        Run identity = simulate(client, mapper, canonicalizer, baseUrl, requestBody, "identity");
        Run unspecified = simulate(client, mapper, canonicalizer, baseUrl, requestBody, null);
        for (Run run : List.of(gzipFirst, gzipWarm, identity, unspecified)) {
            validateIdentity(fixture, run);
        }
        validateGzip(gzipFirst);
        validateGzip(gzipWarm);
        require(identity.contentEncoding().equals("NONE")
                        && unspecified.contentEncoding().equals("NONE"),
                "Identity or unspecified request was forcibly compressed");
        require(List.of(gzipFirst, gzipWarm, identity, unspecified).stream()
                        .map(Run::responseCanonicalHash).distinct().count() == 1L,
                "Transport negotiation changed response semantics");

        Observation observation = new Observation(
                "REAL_MATCH_TRANSPORT_COMPRESSION_V1_EXTERNAL_PROBE",
                launchMode, pid, fixture, gzipFirst, gzipWarm, identity, unspecified,
                System.getProperty("java.version"), System.getProperty("java.vm.name"),
                System.getProperty("os.name"), System.getProperty("os.version"),
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory(),
                "DECOMPRESSED_JSON_EXACT_ACROSS_GZIP_IDENTITY_UNSPECIFIED");
        Files.createDirectories(Objects.requireNonNull(output.getParent(), "output parent"));
        Files.writeString(output, canonicalizer.canonicalJson(observation) + '\n',
                StandardCharsets.UTF_8);
        System.out.println("REAL_MATCH_TRANSPORT_COMPRESSION_V1_EXTERNAL_PROBE_CAPTURED "
                + output);
    }

    private static Run simulate(
            HttpClient client,
            ObjectMapper mapper,
            MatchEngineV1Canonicalizer canonicalizer,
            String baseUrl,
            byte[] requestBody,
            String acceptEncoding
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/real-matches/simulate"))
                .timeout(Duration.ofMinutes(5))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        if (acceptEncoding != null) request.header("Accept-Encoding", acceptEncoding);
        long start = System.nanoTime();
        HttpResponse<byte[]> http = client.send(
                request.POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        long elapsed = System.nanoTime() - start;
        String contentEncoding = http.headers().firstValue("Content-Encoding")
                .orElse("NONE");
        byte[] decoded = contentEncoding.equalsIgnoreCase("gzip")
                ? gunzip(http.body()) : http.body();
        RealMatchApiV1Dtos.Response response = mapper.readValue(
                decoded, RealMatchApiV1Dtos.Response.class);
        RealMatchApiV1Dtos.Integrity integrity = response.integrity();
        return new Run(
                acceptEncoding == null ? "UNSPECIFIED" : acceptEncoding.toUpperCase(Locale.ROOT),
                elapsed, http.statusCode(), http.body().length, decoded.length,
                http.body().length / (double) decoded.length, contentEncoding,
                String.join(",", http.headers().allValues("Vary")),
                http.headers().firstValue("Content-Type").orElse(""),
                isGzip(http.body()), isGzip(decoded),
                response.result().winner(), response.result().durationSeconds(),
                response.timeline().events().size(), response.timeline().snapshots().size(),
                integrity.outputHash(), integrity.replayProvenanceHash(),
                integrity.simulatorTimelineHash(), integrity.structuredTimelineHash(),
                integrity.randomFingerprint().randomDrawCount(),
                integrity.randomFingerprint().randomTraceHash(),
                canonicalizer.hash(response));
    }

    private static void validateGzip(Run run) {
        require(run.httpStatus() == 200 && run.contentEncoding().equalsIgnoreCase("gzip"),
                "gzip negotiation failed");
        require(run.rawBodyIsGzip() && !run.decodedBodyIsGzip(),
                "gzip stream is invalid or double compressed");
        require(run.vary().toLowerCase(Locale.ROOT).contains("accept-encoding"),
                "Vary: Accept-Encoding is missing");
        require(run.compressionRatio() <= 0.15d,
                "gzip ratio exceeds acceptance threshold: " + run.compressionRatio());
    }

    private static void validateIdentity(Fixture fixture, Run run) {
        require(run.httpStatus() == 200 && run.contentType().startsWith("application/json"),
                "HTTP response contract failed");
        require(run.winner() == fixture.expectedWinner()
                        && run.durationSeconds() == fixture.expectedDurationSeconds()
                        && run.eventCount() == fixture.expectedEventCount()
                        && run.snapshotCount() == fixture.expectedSnapshotCount()
                        && run.outputHash().equals(fixture.expectedOutputHash())
                        && run.replayProvenanceHash().equals(fixture.expectedReplayProvenanceHash())
                        && run.simulatorTimelineHash().equals(fixture.expectedSimulatorTimelineHash())
                        && run.structuredTimelineHash().equals(fixture.expectedStructuredTimelineHash())
                        && run.randomDrawCount() == fixture.expectedRandomDrawCount()
                        && run.randomTraceHash().equals(fixture.expectedRandomTraceHash())
                        && run.responseCanonicalHash().equals(fixture.expectedResponseCanonicalHash()),
                "Fixture semantic identity drift: " + fixture.fixtureId());
    }

    private static byte[] gunzip(byte[] body) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return gzip.readAllBytes();
        }
    }

    private static boolean isGzip(byte[] body) {
        return body.length >= 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b;
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public record Fixture(
            String fixtureId, String blueTeamCode, String redTeamCode, String seed,
            TeamSide expectedWinner, int expectedDurationSeconds,
            int expectedEventCount, int expectedSnapshotCount,
            String expectedOutputHash, String expectedReplayProvenanceHash,
            String expectedSimulatorTimelineHash, String expectedStructuredTimelineHash,
            long expectedRandomDrawCount, String expectedRandomTraceHash,
            String expectedResponseCanonicalHash
    ) {
        static Fixture require(String id, String blue, String red, String seed) {
            Fixture fixture = switch (id) {
                case "FIXTURE_A_GEN_T1_SEED_73" -> new Fixture(
                        id, "GEN", "T1", "73", TeamSide.BLUE, 3_430, 517, 344,
                        "bdc597af083aa4f081cf4fe7a242d0e36eec7744b186d998d6f83b717648e874",
                        "7da759c4eccac82a44690edb7aade4853f446165dd99097188294e0fa81fb9d5",
                        "85cdd8894ebd1196f60c629b577c7ceeb8190b7ce5e402458e69919d04037aca",
                        "2ce88664791f95f28b19bc285b37db34fce2361c27145d0a831fb6e97cec65f3",
                        7_749L,
                        "46fb1c06b6b7d8f5b7e1e5894146b5c725815f6122e3f12f90c9eb7745df53f2",
                        "1148043bc8a47eb1c458444f66c6302c95a1a64b4e99104bb7c241fcf157297d");
                case "FIXTURE_B_HLE_DK_SEED_NEGATIVE_73" -> new Fixture(
                        id, "HLE", "DK", "-73", TeamSide.RED, 2_840, 519, 285,
                        "fef2dfd3c522a69f7393bf46196ac9319cb4b6981e9131c694a01239d7aaabb0",
                        "5cb28b9a822535b838519fad45c5e2df53e599c8a4d358855eb1eee842937069",
                        "ad15527e4a558b3b1a17023ee428aabd6b5c9407a252ab63e5041cb286fed78a",
                        "53901672aa6bde3a4c903540d4e58d127bef1227ef909d53b94cb893fa84be5b",
                        6_467L,
                        "05b4668ea4bff74ea161f150295481e34aa0194b086ef2448cd22d3a25362962",
                        "9af1af859be7a5515e05b572d65c748cf88f7ebcfca6c0c99740a9e2a337f651");
                default -> throw new IllegalArgumentException("Unknown fixture " + id);
            };
            RealMatchTransportCompressionV1ExternalProbe.require(
                    fixture.blueTeamCode().equals(blue)
                            && fixture.redTeamCode().equals(red)
                            && fixture.seed().equals(seed),
                    "Fixture arguments differ from frozen contract");
            return fixture;
        }
    }

    public record Run(
            String requestEncoding, long elapsedNanos, int httpStatus,
            long wireBodyBytes, long decodedBodyBytes, double compressionRatio,
            String contentEncoding, String vary, String contentType,
            boolean rawBodyIsGzip, boolean decodedBodyIsGzip,
            TeamSide winner, int durationSeconds, int eventCount, int snapshotCount,
            String outputHash, String replayProvenanceHash,
            String simulatorTimelineHash, String structuredTimelineHash,
            long randomDrawCount, String randomTraceHash, String responseCanonicalHash
    ) {
    }

    public record Observation(
            String schemaVersion, String launchMode, long targetPid, Fixture fixture,
            Run gzipFirst, Run gzipWarm, Run identity, Run unspecified,
            String probeJavaVersion, String probeJavaVm, String probeOsName,
            String probeOsVersion, int availableProcessors, long maxHeapBytes,
            String semanticEquality
    ) {
    }
}
