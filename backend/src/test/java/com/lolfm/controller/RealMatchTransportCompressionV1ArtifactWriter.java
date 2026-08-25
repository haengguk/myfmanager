package com.lolfm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.application.MatchEngineV1Canonicalizer;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Candidate/official writer for transport compression and live browser evidence. */
public final class RealMatchTransportCompressionV1ArtifactWriter {
    private static final String REVIEW_BASELINE =
            "75d3f5c3c924c366cc07f10681b6f1a01948a17d";
    private static final String PERFORMANCE_MANIFEST =
            "c9b4659c4d602fb33c7295885cdc2685a4991469cc4cc0b097ca2d1a20cb26ee";
    private static final String DRAFT_MANIFEST =
            "ae11f4eb368a8b796a113b32963048a764509b0bb98e27ebce313b7ec645d694";
    private static final String INITIAL_HANDOFF_MANIFEST =
            "0a9da8e91bf5426ef374fd5487dea86b6534b07217a1b96329e23446f37a844d";
    private static final List<String> ARTIFACTS = List.of(
            "real-match-transport-compression-v1-contract.json",
            "real-match-transport-compression-v1-http-runs.csv",
            "real-match-transport-compression-v1-browser-runs.csv",
            "real-match-transport-compression-v1-summary.json",
            "real-match-transport-compression-v1-analysis.md");

    private RealMatchTransportCompressionV1ArtifactWriter() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected <backendRoot> <output> <CANDIDATE|OFFICIAL>");
        }
        Path backendRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        Mode mode = Mode.valueOf(args[2]);
        require(Files.isDirectory(backendRoot.resolve("src/main/java")),
                "Invalid backend root");
        require(output.startsWith(backendRoot.resolve("build/reports")),
                "Output must remain under backend/build/reports");
        String expectedName = mode == Mode.OFFICIAL
                ? "real-match-transport-compression-v1"
                : "real-match-transport-compression-v1-candidate";
        require(output.getFileName().toString().equals(expectedName),
                "Unexpected transport artifact output");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.INDENT_OUTPUT);
        MatchEngineV1Canonicalizer canonicalizer = new MatchEngineV1Canonicalizer(mapper);
        Path inputs = backendRoot.resolve(
                "build/reports/real-match-transport-compression-v1-inputs");
        Csv preHttp = Csv.read(inputs.resolve("pre-compression-http-runs.csv"));
        Csv postHttp = Csv.read(inputs.resolve("post-compression-http-runs.csv"));
        Csv preBrowser = Csv.read(inputs.resolve("pre-compression-browser-runs.csv"));
        Csv postBrowser = Csv.read(inputs.resolve("post-compression-browser-runs.csv"));
        Csv phases = Csv.read(inputs.resolve("post-compression-phase-runs.csv"));
        require(preHttp.rows().size() == 4 && postHttp.rows().size() == 4,
                "HTTP run cardinality mismatch");
        require(preBrowser.rows().size() == 4 && postBrowser.rows().size() == 4,
                "Browser run cardinality mismatch");
        require(phases.rows().size() == 4, "Phase run cardinality mismatch");

        List<JsonNode> probes = readProbes(mapper, inputs, mode);
        verifyHttp(postHttp);
        verifyBrowser(postBrowser);
        verifyPhases(phases);
        verifyProbes(probes);
        JsonNode frontend = mapper.readTree(
                inputs.resolve("frontend-verification.json").toFile());
        require(frontend.path("typescript").asBoolean()
                        && frontend.path("productionBuild").asBoolean()
                        && frontend.path("referenceIntegrity").asBoolean()
                        && frontend.path("liveContract").asBoolean()
                        && frontend.path("bundleIntegrity").asBoolean(),
                "Frontend verification is incomplete");

        ManifestEvidence performance = verifyManifest(backendRoot.resolve(
                "build/reports/real-match-performance-baseline-v1"),
                PERFORMANCE_MANIFEST);
        ManifestEvidence draft = verifyManifest(backendRoot.resolve(
                "build/reports/draft-engine-performance-hardening-v1"), DRAFT_MANIFEST);
        ManifestEvidence handoff = verifyManifest(backendRoot.resolve(
                "build/reports/real-match-api-v1"), null);
        if (mode == Mode.CANDIDATE) {
            require(handoff.rawManifestSha256().equals(INITIAL_HANDOFF_MANIFEST),
                    "Candidate must remain bound to the initial handoff before final refresh");
        }

        RealMatchApiV1ArtifactWriter.SourceTreeIdentity production =
                RealMatchApiV1ArtifactWriter.productionSourceTree(backendRoot);
        TreeIdentity resources = treeIdentity(backendRoot, Path.of("src/main/resources"));
        TreeIdentity runtime = filesIdentity(backendRoot,
                List.of(Path.of("build.gradle"),
                        Path.of("src/main/resources/application.properties")));
        JunitSummary junit = junitSummary(backendRoot.resolve("build/test-results/test"));
        if (mode == Mode.OFFICIAL) {
            require(junit.suiteCount() >= 200 && junit.testCount() >= 2_100
                            && junit.failures() == 0 && junit.errors() == 0
                            && junit.skipped() == 0
                            && junit.transportCompressionTestCount() >= 1,
                    "Official artifact requires a clean current full regression");
            require(!handoff.rawManifestSha256().equals(INITIAL_HANDOFF_MANIFEST),
                    "Official artifact requires refreshed API handoff source binding");
        }

        LinkedHashMap<String, Object> fixtureSummaries = fixtureSummaries(
                preHttp, postHttp, preBrowser, postBrowser, phases);
        String status = mode == Mode.OFFICIAL
                ? "REAL_MATCH_TRANSPORT_COMPRESSION_AND_LIVE_E2E_ACCEPTED"
                : "REAL_MATCH_TRANSPORT_COMPRESSION_V1_CANDIDATE_READY_FOR_FULL_REGRESSION";
        Map<String, Object> contract = contract(mode, status, production, resources, runtime,
                performance, draft, handoff, probes);
        Map<String, Object> summary = summary(mode, status, production, resources, runtime,
                performance, draft, handoff, probes, frontend, junit, fixtureSummaries);

        recreate(output);
        writeJson(output.resolve(ARTIFACTS.get(0)), contract, canonicalizer);
        Files.writeString(output.resolve(ARTIFACTS.get(1)),
                combine("measurementStage", "PRE_COMPRESSION", preHttp,
                        "POST_COMPRESSION", postHttp), StandardCharsets.UTF_8);
        Files.writeString(output.resolve(ARTIFACTS.get(2)),
                combine("measurementStage", "PRE_COMPRESSION", preBrowser,
                        "POST_COMPRESSION", postBrowser), StandardCharsets.UTF_8);
        writeJson(output.resolve(ARTIFACTS.get(3)), summary, canonicalizer);
        Files.writeString(output.resolve(ARTIFACTS.get(4)),
                analysis(status, fixtureSummaries, junit, handoff), StandardCharsets.UTF_8);
        writeManifest(output);
        verifyManifest(output, null);
        System.out.println(status + " output=" + output);
    }

    private static Map<String, Object> contract(
            Mode mode, String status,
            RealMatchApiV1ArtifactWriter.SourceTreeIdentity production,
            TreeIdentity resources, TreeIdentity runtime,
            ManifestEvidence performance, ManifestEvidence draft,
            ManifestEvidence handoff, List<JsonNode> probes
    ) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", "REAL_MATCH_TRANSPORT_COMPRESSION_V1_CONTRACT");
        value.put("artifactMode", mode.name());
        value.put("status", status);
        value.put("reviewBaselineCommit", REVIEW_BASELINE);
        value.put("transport", Map.of(
                "implementation", "SPRING_BOOT_STANDARD_SERVER_COMPRESSION",
                "enabled", true,
                "mimeTypes", List.of("application/json"),
                "minResponseSize", "8KB",
                "contentNegotiation", List.of("gzip", "identity", "unspecified"),
                "gameplayIdentityIncluded", false));
        value.put("acceptance", Map.of(
                "maximumCompressionRatio", 0.15d,
                "minimumWireReduction", 0.85d,
                "decompressedSemantics", "EXACT",
                "browserFlow", "SETUP_DRAFT_PLAYBACK_RESULT",
                "consoleErrors", 0));
        value.put("preservedSemantics", List.of(
                "ROSTER_AND_PLAYER_ID", "DRAFT_20_DECISIONS", "FINAL_ROLE_ASSIGNMENT",
                "WINNER_DURATION_EVENTS_SNAPSHOTS", "OUTPUT_HASH", "REPLAY_PROVENANCE",
                "SIMULATOR_TIMELINE_FINGERPRINT", "STRUCTURED_TIMELINE_FINGERPRINT",
                "RANDOM_CONSUMPTION_FINGERPRINT", "REAL_MATCH_RESPONSE_V1_JSON"));
        value.put("sourceIdentity", Map.of(
                "production", production, "resources", resources, "runtimeConfiguration", runtime));
        value.put("upstream", Map.of(
                "performanceBaseline", performance,
                "draftHardening", draft,
                "currentApiHandoff", handoff));
        value.put("externalProbeCount", probes.size());
        value.put("limitations", List.of(
                "Decoded JSON remains approximately 20-34MB.",
                "Compression reduces wire transfer only; JSON parse, validation and heap costs remain.",
                "Wall time is environment-specific and is not a brittle correctness threshold.",
                "Tomcat may compress negotiated small MVC responses when Content-Length is unknown; identity and unspecified clients remain uncompressed."));
        return Map.copyOf(value);
    }

    private static Map<String, Object> summary(
            Mode mode, String status,
            RealMatchApiV1ArtifactWriter.SourceTreeIdentity production,
            TreeIdentity resources, TreeIdentity runtime,
            ManifestEvidence performance, ManifestEvidence draft,
            ManifestEvidence handoff, List<JsonNode> probes,
            JsonNode frontend, JunitSummary junit,
            Map<String, Object> fixtureSummaries
    ) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", "REAL_MATCH_TRANSPORT_COMPRESSION_V1_SUMMARY");
        value.put("artifactMode", mode.name());
        value.put("status", status);
        value.put("reviewBaselineCommit", REVIEW_BASELINE);
        value.put("currentHead", command(Path.of("").toAbsolutePath().getParent(),
                "git", "rev-parse", "HEAD"));
        value.put("fixtures", fixtureSummaries);
        value.put("semanticEquality", "EXACT_ACROSS_PRE_IDENTITY_POST_IDENTITY_POST_GZIP");
        value.put("externalRuntime", Map.of(
                "probeCount", probes.size(),
                "bootRun", "CLEAN", "packagedJar", "CLEAN",
                "gzipIdentityUnspecifiedExact", true));
        value.put("frontend", frontend);
        value.put("backendVerification", junit);
        value.put("sourceIdentity", Map.of(
                "production", production, "resources", resources,
                "runtimeConfiguration", runtime));
        value.put("upstream", Map.of(
                "performanceBaseline", performance,
                "draftHardening", draft,
                "apiHandoff", handoff,
                "apiHandoffRefreshed", !handoff.rawManifestSha256()
                        .equals(INITIAL_HANDOFF_MANIFEST)));
        value.put("compressionSolved", List.of(
                "HTTP_WIRE_TRANSFER_SIZE", "STANDARD_ACCEPT_ENCODING_NEGOTIATION"));
        value.put("compressionDidNotSolve", List.of(
                "DECODED_JSON_SIZE", "JSON_PARSE", "RUNTIME_VALIDATION", "BROWSER_HEAP"));
        return Map.copyOf(value);
    }

    private static LinkedHashMap<String, Object> fixtureSummaries(
            Csv preHttp, Csv postHttp, Csv preBrowser, Csv postBrowser, Csv phases
    ) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String fixture : List.of(
                "FIXTURE_A_GEN_T1_SEED_73", "FIXTURE_B_HLE_DK_SEED_NEGATIVE_73")) {
            Map<String, String> preFirst = row(preHttp, fixture, "FIRST");
            Map<String, String> preWarm = row(preHttp, fixture, "WARM");
            Map<String, String> postFirst = row(postHttp, fixture, "FIRST");
            Map<String, String> postWarm = row(postHttp, fixture, "WARM");
            Map<String, String> browserPreFirst = row(preBrowser, fixture, "FIRST");
            Map<String, String> browserPreWarm = row(preBrowser, fixture, "WARM");
            Map<String, String> browserPostFirst = row(postBrowser, fixture, "FIRST");
            Map<String, String> browserPostWarm = row(postBrowser, fixture, "WARM");
            long decoded = longValue(postFirst, "decodedBodyBytes");
            long wire = longValue(postFirst, "wireBodyBytes");
            double ratio = wire / (double) decoded;
            LinkedHashMap<String, Object> fixtureValue = new LinkedHashMap<>();
            fixtureValue.put("decodedBodyBytes", decoded);
            fixtureValue.put("compressedWireBodyBytes", wire);
            fixtureValue.put("compressionRatio", ratio);
            fixtureValue.put("wireReduction", 1.0d - ratio);
            fixtureValue.put("httpSeconds", Map.of(
                    "preFirst", doubleValue(preFirst, "totalSeconds"),
                    "preWarm", doubleValue(preWarm, "totalSeconds"),
                    "postFirst", doubleValue(postFirst, "totalSeconds"),
                    "postWarm", doubleValue(postWarm, "totalSeconds")));
            fixtureValue.put("browser", Map.of(
                    "preFirst", browserSummary(browserPreFirst),
                    "preWarm", browserSummary(browserPreWarm),
                    "postFirst", browserSummary(browserPostFirst),
                    "postWarm", browserSummary(browserPostWarm)));
            List<Map<String, String>> fixturePhases = phases.rows().stream()
                    .filter(value -> value.get("fixtureId").equals(fixture)).toList();
            fixtureValue.put("backendPhaseMedianMillis", Map.of(
                    "rosterAssembly", medianMillis(fixturePhases, "rosterAssemblyNanos"),
                    "draft", medianMillis(fixturePhases, "fullDraftNanos"),
                    "rosterDraftInputPreparation", medianMillis(
                            fixturePhases, "rosterDraftInputPreparationNanos"),
                    "matchEngine", medianMillis(fixturePhases, "matchEngineExecutionNanos"),
                    "outputIntegrity", medianMillis(
                            fixturePhases, "outputIntegrityValidationNanos"),
                    "responseMapping", medianMillis(fixturePhases, "responseMappingNanos"),
                    "jsonSerialization", medianMillis(fixturePhases, "jsonSerializationNanos"),
                    "applicationBoundary", medianMillis(
                            fixturePhases, "applicationBoundaryTotalNanos")));
            result.put(fixture, Map.copyOf(fixtureValue));
        }
        return result;
    }

    private static Map<String, Object> browserSummary(Map<String, String> row) {
        return Map.of(
                "encodedDataLength", longValue(row, "cdpEncodedDataLength"),
                "decodedPayloadBytes", longValue(row, "decodedPayloadBytes"),
                "requestAndDownloadMs", doubleValue(row, "requestAndDownloadMs"),
                "jsonParseMs", doubleValue(row, "jsonParseMs"),
                "runtimeValidationMs", doubleValue(row, "runtimeValidationMs"),
                "normalizationMs", doubleValue(row, "normalizationMs"),
                "requestToDraftScreenMs", longValue(row, "requestToDraftScreenMs"),
                "firstPlaybackPaintMs", doubleValue(row, "firstPlaybackPaintMs"));
    }

    private static String analysis(
            String status, Map<String, Object> fixtures, JunitSummary junit,
            ManifestEvidence handoff
    ) {
        StringBuilder value = new StringBuilder()
                .append("# Real Match Transport Compression V1\n\n")
                .append("- 상태: `").append(status).append("`\n")
                .append("- 구현: Spring Boot 표준 server compression, JSON, 8KB\n")
                .append("- 의미 동일성: gzip 해제/identity/무헤더 exact\n")
                .append("- Chromium: setup → Draft → playback → result, console/page error 0\n\n")
                .append("## Fixture 결과\n\n");
        fixtures.forEach((id, summary) -> value.append("- `").append(id)
                .append("`: `").append(summary).append("`\n"));
        value.append("\n## 검증\n\n- Backend: `").append(junit).append("`\n")
                .append("- API handoff manifest: `")
                .append(handoff.rawManifestSha256()).append("`\n\n")
                .append("## 한계\n\n")
                .append("decoded JSON은 여전히 20~34MB이며 parse/validation/heap 비용은 남는다. ")
                .append("gzip은 wire transfer만 약 2~3MB로 줄인다. localhost wall time은 ")
                .append("CPU/JIT/환경 영향으로 항상 단축된다고 보장하지 않는다.\n");
        return value.toString();
    }

    private static void verifyHttp(Csv csv) {
        for (Map<String, String> row : csv.rows()) {
            require(row.get("httpStatus").equals("200")
                            && row.get("acceptEncoding").equals("gzip")
                            && row.get("contentEncoding").equalsIgnoreCase("gzip"),
                    "Post-compression HTTP negotiation failed");
            double ratio = longValue(row, "wireBodyBytes")
                    / (double) longValue(row, "decodedBodyBytes");
            require(ratio <= 0.15d && 1.0d - ratio >= 0.85d,
                    "HTTP compression acceptance failed");
        }
    }

    private static void verifyBrowser(Csv csv) {
        for (Map<String, String> row : csv.rows()) {
            double ratio = longValue(row, "cdpEncodedDataLength")
                    / (double) longValue(row, "decodedPayloadBytes");
            require(row.get("httpStatus").equals("200")
                            && row.get("contentEncoding").equalsIgnoreCase("gzip")
                            && ratio <= 0.15d
                            && row.get("consoleErrors").equals("0")
                            && row.get("pageErrors").equals("0")
                            && row.get("resultScreenVisible").equals("true"),
                    "Browser compression/E2E acceptance failed");
        }
    }

    private static void verifyPhases(Csv csv) {
        require(csv.rows().stream().allMatch(row -> row.get("semanticExact").equals("true")),
                "Phase diagnostic semantic identity failed");
        require(csv.rows().stream().map(row -> row.get("outputHash")).distinct().count() == 2,
                "Phase fixture output identity mismatch");
    }

    private static void verifyProbes(List<JsonNode> probes) {
        require(probes.size() == 4, "Expected bootRun/JAR probes for both fixtures");
        require(probes.stream().allMatch(probe ->
                        probe.path("schemaVersion").asText().equals(
                                "REAL_MATCH_TRANSPORT_COMPRESSION_V1_EXTERNAL_PROBE")
                                && probe.path("semanticEquality").asText().equals(
                                "DECOMPRESSED_JSON_EXACT_ACROSS_GZIP_IDENTITY_UNSPECIFIED")
                                && probe.path("gzipFirst").path("compressionRatio").asDouble() <= 0.15d
                                && probe.path("gzipWarm").path("contentEncoding").asText()
                                .equalsIgnoreCase("gzip")
                                && probe.path("identity").path("contentEncoding").asText()
                                .equals("NONE")
                                && probe.path("unspecified").path("contentEncoding").asText()
                                .equals("NONE")),
                "External probe acceptance failed");
        require(probes.stream().map(probe -> probe.path("launchMode").asText())
                        .anyMatch(value -> value.contains("BOOT_RUN"))
                        && probes.stream().map(probe -> probe.path("launchMode").asText())
                        .anyMatch(value -> value.contains("PACKAGED_JAR")),
                "Missing bootRun or packaged JAR evidence");
    }

    private static List<JsonNode> readProbes(
            ObjectMapper mapper, Path inputs, Mode mode
    ) throws IOException {
        String prefix = mode == Mode.OFFICIAL ? "official" : "candidate";
        ArrayList<JsonNode> result = new ArrayList<>();
        for (String name : List.of(
                prefix + "-bootrun-gen-t1.json", prefix + "-bootrun-hle-dk.json",
                prefix + "-jar-gen-t1.json", prefix + "-jar-hle-dk.json")) {
            Path file = inputs.resolve(name);
            require(Files.isRegularFile(file), "Missing transport probe " + name);
            result.add(mapper.readTree(file.toFile()));
        }
        return List.copyOf(result);
    }

    private static String combine(
            String prefixHeader, String firstLabel, Csv first,
            String secondLabel, Csv second
    ) {
        require(first.header().equals(second.header()), "Combined CSV headers differ");
        StringBuilder value = new StringBuilder(prefixHeader).append(',')
                .append(String.join(",", first.header())).append('\n');
        for (List<String> row : first.rawRows()) {
            value.append(firstLabel).append(',').append(String.join(",", row)).append('\n');
        }
        for (List<String> row : second.rawRows()) {
            value.append(secondLabel).append(',').append(String.join(",", row)).append('\n');
        }
        return value.toString();
    }

    private static Map<String, String> row(Csv csv, String fixture, String kind) {
        return csv.rows().stream().filter(value -> value.get("fixtureId").equals(fixture)
                        && value.get("requestKind").equals(kind)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing CSV row "
                        + fixture + "/" + kind));
    }

    private static double medianMillis(List<Map<String, String>> rows, String field) {
        List<Long> values = rows.stream().map(row -> longValue(row, field)).sorted().toList();
        require(values.size() == 2, "Expected two phase observations");
        return (values.get(0) + values.get(1)) / 2.0d / 1_000_000.0d;
    }

    private static long longValue(Map<String, String> row, String field) {
        return Long.parseLong(Objects.requireNonNull(row.get(field), field));
    }

    private static double doubleValue(Map<String, String> row, String field) {
        return Double.parseDouble(Objects.requireNonNull(row.get(field), field));
    }

    private static ManifestEvidence verifyManifest(Path directory, String expectedRaw)
            throws IOException {
        Path manifest = directory.resolve("SHA256SUMS.txt");
        require(Files.isRegularFile(manifest), "Missing manifest " + directory);
        String raw = sha256(Files.readAllBytes(manifest));
        if (expectedRaw != null) require(raw.equals(expectedRaw),
                "Manifest raw SHA drift " + directory);
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        for (String line : lines) {
            String[] fields = line.split("  ", 2);
            require(fields.length == 2 && fields[0].matches("[0-9a-f]{64}"),
                    "Malformed manifest line");
            Path file = directory.resolve(fields[1]).normalize();
            require(file.startsWith(directory.normalize()) && Files.isRegularFile(file)
                            && sha256(Files.readAllBytes(file)).equals(fields[0]),
                    "Manifest entry drift " + fields[1]);
        }
        return new ManifestEvidence(raw, lines.size());
    }

    private static TreeIdentity treeIdentity(Path root, Path relative) throws IOException {
        Path directory = root.resolve(relative);
        ArrayList<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile).forEach(files::add);
        }
        return filesIdentity(root, files.stream().map(root::relativize).toList());
    }

    private static TreeIdentity filesIdentity(Path root, List<Path> relatives)
            throws IOException {
        List<Path> sorted = relatives.stream().sorted(Comparator.comparing(
                path -> path.toString().replace('\\', '/'))).toList();
        StringBuilder canonical = new StringBuilder(
                "identitySchema=SHA256_SORTED_RELATIVE_PATH_AND_RAW_SHA_V1\n");
        for (Path relative : sorted) {
            Path file = root.resolve(relative);
            require(Files.isRegularFile(file), "Missing identity file " + relative);
            canonical.append("file=").append(relative.toString().replace('\\', '/'))
                    .append('\n').append("rawSha256=")
                    .append(sha256(Files.readAllBytes(file))).append('\n');
        }
        return new TreeIdentity(sha256(canonical.toString().getBytes(StandardCharsets.UTF_8)),
                sorted.size(), "SHA256_SORTED_RELATIVE_PATH_AND_RAW_SHA_V1");
    }

    private static JunitSummary junitSummary(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) return new JunitSummary(
                "NOT_AVAILABLE", 0, 0, 0, 0, 0, 0);
        int suites = 0;
        int tests = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        int transport = 0;
        var factory = DocumentBuilderFactory.newInstance();
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path file : paths.filter(path -> path.getFileName().toString()
                            .startsWith("TEST-") && path.toString().endsWith(".xml"))
                    .sorted().toList()) {
                Element root = factory.newDocumentBuilder().parse(file.toFile())
                        .getDocumentElement();
                suites++;
                int suiteTests = integer(root, "tests");
                tests += suiteTests;
                failures += integer(root, "failures");
                errors += integer(root, "errors");
                skipped += integer(root, "skipped");
                if (root.getAttribute("name").equals(
                        "com.lolfm.controller.RealMatchTransportCompressionV1IntegrationTest")) {
                    transport += suiteTests;
                }
            }
        }
        String status = failures == 0 && errors == 0 && skipped == 0
                ? "CLEAN_PASS" : "FAILED";
        return new JunitSummary(status, suites, tests, failures, errors, skipped, transport);
    }

    private static int integer(Element element, String name) {
        String value = element.getAttribute(name);
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private static void recreate(Path output) throws IOException {
        if (Files.exists(output)) {
            try (Stream<Path> paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(output);
    }

    private static void writeJson(
            Path output, Object value, MatchEngineV1Canonicalizer canonicalizer
    ) throws IOException {
        Files.writeString(output, canonicalizer.canonicalJson(value) + '\n',
                StandardCharsets.UTF_8);
    }

    private static void writeManifest(Path output) throws IOException {
        StringBuilder manifest = new StringBuilder();
        for (String name : ARTIFACTS) {
            manifest.append(sha256(Files.readAllBytes(output.resolve(name))))
                    .append("  ").append(name).append('\n');
        }
        Files.writeString(output.resolve("SHA256SUMS.txt"), manifest.toString(),
                StandardCharsets.UTF_8);
    }

    private static String command(Path directory, String... command) {
        try {
            Process process = new ProcessBuilder(command).directory(directory.toFile())
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            require(process.waitFor() == 0, "Command failed: " + output);
            return output;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
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

    private enum Mode { CANDIDATE, OFFICIAL }

    private record ManifestEvidence(String rawManifestSha256, int entryCount) {
    }

    private record TreeIdentity(String hash, int fileCount, String algorithm) {
    }

    private record JunitSummary(
            String status, int suiteCount, int testCount, int failures,
            int errors, int skipped, int transportCompressionTestCount
    ) {
    }

    private record Csv(
            List<String> header,
            List<List<String>> rawRows,
            List<Map<String, String>> rows
    ) {
        static Csv read(Path file) throws IOException {
            require(Files.isRegularFile(file), "Missing CSV " + file);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            require(lines.size() >= 2, "Empty CSV " + file);
            List<String> header = List.of(lines.getFirst().split(",", -1));
            ArrayList<List<String>> rawRows = new ArrayList<>();
            ArrayList<Map<String, String>> rows = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) continue;
                List<String> fields = List.of(line.split(",", -1));
                require(fields.size() == header.size(), "CSV column mismatch " + file);
                LinkedHashMap<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < header.size(); index++) {
                    row.put(header.get(index), fields.get(index));
                }
                rawRows.add(fields);
                rows.add(Map.copyOf(row));
            }
            return new Csv(List.copyOf(header), List.copyOf(rawRows), List.copyOf(rows));
        }
    }
}
