package com.lolfm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.simulator.TeamSide;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** External-client probe for a caller-owned fresh bootRun or packaged-JAR process. */
public final class RealMatchExternalRuntimeProbeV1 {
    private RealMatchExternalRuntimeProbeV1() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 8) {
            throw new IllegalArgumentException(
                    "Expected <baseUrl> <launchMode> <pid> <fixtureId> <blue> <red> <seed> <output>");
        }
        String baseUrl = required(args[0], "baseUrl");
        String launchMode = required(args[1], "launchMode");
        long pid = Long.parseLong(args[2]);
        String fixtureId = required(args[3], "fixtureId");
        Fixture fixture = Fixture.require(fixtureId, args[4], args[5], args[6]);
        Path output = Path.of(args[7]).toAbsolutePath().normalize();

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.INDENT_OUTPUT);
        MatchEngineV1Canonicalizer canonicalizer = new MatchEngineV1Canonicalizer(mapper);
        JvmEvidence jvm = jvmEvidence(pid);
        require(!jvm.tieredStopAtLevel1(), "Target JVM is still restricted to TieredStopAtLevel=1");
        require(jvm.profiledNmethodsHeapAvailable()
                        && jvm.nonProfiledNmethodsHeapAvailable(),
                "Target JVM does not expose C2-capable segmented code heaps");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30)).build();
        HttpResponse<byte[]> options = client.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/v1/real-matches/options"))
                        .timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        require(options.statusCode() == 200, "Options preflight failed");

        byte[] requestBody = mapper.writeValueAsBytes(Map.of(
                "schemaVersion", RealMatchApiV1Dtos.REQUEST_SCHEMA,
                "blueTeamCode", fixture.blueTeamCode(),
                "redTeamCode", fixture.redTeamCode(),
                "seed", fixture.seed()));
        HttpRun first = simulate(client, mapper, canonicalizer, baseUrl, requestBody);
        HttpRun second = simulate(client, mapper, canonicalizer, baseUrl, requestBody);
        validate(fixture, first);
        validate(fixture, second);
        require(first.responseCanonicalHash().equals(second.responseCanonicalHash())
                        && first.outputHash().equals(second.outputHash())
                        && first.replayProvenanceHash().equals(second.replayProvenanceHash())
                        && first.simulatorTimelineHash().equals(second.simulatorTimelineHash())
                        && first.structuredTimelineHash().equals(second.structuredTimelineHash())
                        && first.randomDrawCount() == second.randomDrawCount()
                        && first.randomTraceHash().equals(second.randomTraceHash()),
                "First/warm runtime response identity mismatch");

        Observation observation = new Observation(
                "REAL_MATCH_EXTERNAL_RUNTIME_PROBE_V1", launchMode, fixture,
                pid, jvm, first, second, System.getProperty("java.version"),
                System.getProperty("java.vm.name"), System.getProperty("os.name"),
                System.getProperty("os.version"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory());
        Files.createDirectories(Objects.requireNonNull(output.getParent(), "output parent"));
        Files.writeString(output, canonicalizer.canonicalJson(observation) + '\n',
                StandardCharsets.UTF_8);
        System.out.println("REAL_MATCH_EXTERNAL_RUNTIME_PROBE_CAPTURED " + output);
    }

    private static HttpRun simulate(
            HttpClient client,
            ObjectMapper mapper,
            MatchEngineV1Canonicalizer canonicalizer,
            String baseUrl,
            byte[] requestBody
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/real-matches/simulate"))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
        long start = System.nanoTime();
        HttpResponse<byte[]> http = client.send(request,
                HttpResponse.BodyHandlers.ofByteArray());
        long elapsed = System.nanoTime() - start;
        RealMatchApiV1Dtos.Response response = mapper.readValue(
                http.body(), RealMatchApiV1Dtos.Response.class);
        RealMatchApiV1Dtos.Integrity integrity = response.integrity();
        return new HttpRun(
                elapsed, http.statusCode(), http.body().length,
                http.headers().firstValue("Content-Encoding").orElse("NONE"),
                response.result().winner(), response.result().durationSeconds(),
                response.timeline().events().size(), response.timeline().snapshots().size(),
                integrity.outputHash(), integrity.replayProvenanceHash(),
                integrity.simulatorTimelineHash(), integrity.structuredTimelineHash(),
                integrity.randomFingerprint().randomDrawCount(),
                integrity.randomFingerprint().randomTraceHash(),
                canonicalizer.hash(response));
    }

    private static void validate(Fixture fixture, HttpRun run) {
        require(run.httpStatus() == 200 && run.contentEncoding().equals("NONE"),
                "Runtime HTTP response is invalid");
        require(run.winner() == fixture.expectedWinner()
                        && run.durationSeconds() == fixture.expectedDurationSeconds()
                        && run.eventCount() == fixture.expectedEventCount()
                        && run.snapshotCount() == fixture.expectedSnapshotCount()
                        && run.outputHash().equals(fixture.expectedOutputHash()),
                "Runtime fixture drift: " + fixture.fixtureId());
    }

    private static JvmEvidence jvmEvidence(long pid) throws Exception {
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path executable = javaHome.resolve("bin").resolve(
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                        ? "jcmd.exe" : "jcmd");
        require(Files.isRegularFile(executable), "jcmd is unavailable: " + executable);
        String flags = command(executable.toString(), Long.toString(pid), "VM.flags", "-all");
        String codeCache = command(
                executable.toString(), Long.toString(pid), "Compiler.codecache");
        LinkedHashMap<String, String> selected = new LinkedHashMap<>();
        for (String key : List.of(
                "TieredCompilation", "TieredStopAtLevel", "ReservedCodeCacheSize",
                "ProfiledCodeHeapSize", "NonProfiledCodeHeapSize")) {
            flags.lines().filter(line -> line.matches(".*\\s" + key + "\\s+=.*"))
                    .findFirst()
                    .ifPresent(line -> selected.put(key, line.trim()));
        }
        boolean tieredStopAtLevel1 = selected.getOrDefault("TieredStopAtLevel", "")
                .matches(".*=\\s*1\\s+.*");
        return new JvmEvidence(
                Map.copyOf(selected), codeCache.lines().map(String::trim)
                .filter(line -> !line.isBlank()).toList(),
                tieredStopAtLevel1,
                codeCache.contains("CodeHeap 'profiled nmethods'"),
                codeCache.contains("CodeHeap 'non-profiled nmethods'"));
    }

    private static String command(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int exit = process.waitFor();
        require(exit == 0, "Command failed: " + String.join(" ", command) + "\n" + output);
        return output;
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
            String fixtureId,
            String blueTeamCode,
            String redTeamCode,
            String seed,
            TeamSide expectedWinner,
            int expectedDurationSeconds,
            int expectedEventCount,
            int expectedSnapshotCount,
            String expectedOutputHash
    ) {
        static Fixture require(String id, String blue, String red, String seed) {
            Fixture fixture = switch (id) {
                case "FIXTURE_A_GEN_T1_SEED_73" -> new Fixture(
                        id, "GEN", "T1", "73", TeamSide.BLUE, 3_430, 517, 344,
                        "bdc597af083aa4f081cf4fe7a242d0e36eec7744b186d998d6f83b717648e874");
                case "FIXTURE_B_HLE_DK_SEED_NEGATIVE_73" -> new Fixture(
                        id, "HLE", "DK", "-73", TeamSide.RED, 2_840, 519, 285,
                        "fef2dfd3c522a69f7393bf46196ac9319cb4b6981e9131c694a01239d7aaabb0");
                default -> throw new IllegalArgumentException("Unknown fixture " + id);
            };
            RealMatchExternalRuntimeProbeV1.require(fixture.blueTeamCode().equals(blue)
                            && fixture.redTeamCode().equals(red)
                            && fixture.seed().equals(seed),
                    "Fixture arguments differ from frozen contract");
            return fixture;
        }
    }

    public record JvmEvidence(
            Map<String, String> selectedFlags,
            List<String> codeCacheLines,
            boolean tieredStopAtLevel1,
            boolean profiledNmethodsHeapAvailable,
            boolean nonProfiledNmethodsHeapAvailable
    ) {
    }

    public record HttpRun(
            long elapsedNanos,
            int httpStatus,
            long bodyBytes,
            String contentEncoding,
            TeamSide winner,
            int durationSeconds,
            int eventCount,
            int snapshotCount,
            String outputHash,
            String replayProvenanceHash,
            String simulatorTimelineHash,
            String structuredTimelineHash,
            long randomDrawCount,
            String randomTraceHash,
            String responseCanonicalHash
    ) {
    }

    public record Observation(
            String schemaVersion,
            String launchMode,
            Fixture fixture,
            long targetPid,
            JvmEvidence jvmEvidence,
            HttpRun firstRequest,
            HttpRun secondRequest,
            String probeJavaVersion,
            String probeJavaVm,
            String probeOsName,
            String probeOsVersion,
            String probeOsArch,
            int availableProcessors,
            long probeMaxHeapBytes
    ) {
    }
}
