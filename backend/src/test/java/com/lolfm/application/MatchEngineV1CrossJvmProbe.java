package com.lolfm.application;

import com.lolfm.LolfmApplication;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Fresh-JVM deterministic probe used by focused verification and the freeze writer. */
public final class MatchEngineV1CrossJvmProbe {
    static final long FIXED_SEED = 73L;
    static final List<String> PAYLOAD_FILES = List.of(
            "match-engine-v1-output.json",
            "match-engine-v1-summary.json",
            "match-engine-v1-verification.json");
    static final String MANIFEST = "SHA256SUMS.txt";

    private MatchEngineV1CrossJvmProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected output directory");
        }
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                LolfmApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "logging.level.root=ERROR")
                .run()) {
            write(context, Path.of(args[0]));
        }
    }

    static void write(ConfigurableApplicationContext context, Path output) throws IOException {
        Files.createDirectories(output);
        RealDraftMatchOrchestrator orchestrator = context.getBean(
                RealDraftMatchOrchestrator.class);
        MatchEngineV1Canonicalizer canonicalizer = context.getBean(
                MatchEngineV1Canonicalizer.class);
        MatchEngineV1Output result = orchestrator.orchestrateV1("GEN", "T1", FIXED_SEED);
        writeCanonical(output.resolve(PAYLOAD_FILES.get(0)),
                canonicalizer.canonicalJson(result));
        writeCanonical(output.resolve(PAYLOAD_FILES.get(1)),
                canonicalizer.canonicalJson(result.resultSummary()));
        LinkedHashMap<String, Object> verification = new LinkedHashMap<>();
        verification.put("schemaVersion", "MATCH_ENGINE_V1_CROSS_JVM_PROBE_V1");
        verification.put("matchIdentity", result.matchIdentity());
        verification.put("runtimeProfileId",
                result.productionPolicy().retainedRuntimeProfileId());
        verification.put("configurationHash", result.configurationHash());
        verification.put("inputHash", result.inputHash());
        verification.put("replayProvenanceHash",
                result.executionProvenance().replayProvenanceHash());
        verification.put("replayProvenanceHashAlgorithm",
                result.executionProvenance().replayProvenanceHashAlgorithm());
        verification.put("simulatorTimelineHash", result.simulatorTimelineHash());
        verification.put("structuredTimelineHash", result.structuredTimelineHash());
        verification.put("randomFingerprint",
                result.executionProvenance().randomFingerprint());
        verification.put("outputHash", result.outputHash());
        verification.put("winner", result.resultSummary().winner());
        verification.put("endReason", result.resultSummary().endReason());
        verification.put("durationSeconds", result.resultSummary().durationSeconds());
        writeCanonical(output.resolve(PAYLOAD_FILES.get(2)),
                canonicalizer.canonicalJson(verification));
        StringBuilder manifest = new StringBuilder();
        for (String file : PAYLOAD_FILES) {
            manifest.append(sha256(Files.readAllBytes(output.resolve(file))))
                    .append("  ").append(file).append('\n');
        }
        Files.writeString(output.resolve(MANIFEST), manifest, StandardCharsets.UTF_8);
    }

    static ProcessResult launchFreshJvm(Path output) throws IOException, InterruptedException {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                        ? "java.exe" : "java").toString();
        Process process = new ProcessBuilder(
                javaExecutable, "-Xms64m", "-Xmx512m", "-XX:MaxMetaspaceSize=192m",
                "-XX:+UseSerialGC", "-cp", System.getProperty("java.class.path"),
                MatchEngineV1CrossJvmProbe.class.getName(), output.toString())
                .redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        return new ProcessResult(exit, log);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void writeCanonical(Path path, String json) throws IOException {
        Files.writeString(path, json + '\n', StandardCharsets.UTF_8);
    }

    record ProcessResult(int exitCode, String log) {
    }
}
