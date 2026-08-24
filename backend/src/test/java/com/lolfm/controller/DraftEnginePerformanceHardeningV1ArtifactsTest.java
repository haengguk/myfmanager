package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DraftEnginePerformanceHardeningV1ArtifactsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void verifiesFrozenUpstreamManifestScheduleAndCompleteSemanticRows()
            throws Exception {
        Path backendRoot = Path.of("").toAbsolutePath().normalize();
        var upstream = DraftEnginePerformanceHardeningV1Artifacts.verifyUpstream(
                backendRoot, mapper);

        assertThat(upstream.entriesVerified()).isEqualTo(7);
        assertThat(upstream.fixtures()).hasSize(24);
        assertThat(upstream.turns()).hasSize(480);
        assertThat(upstream.contract().path("scheduleHash").asText())
                .isEqualTo(DraftEnginePerformanceHardeningV1Artifacts.SCHEDULE_HASH);
        assertThat(upstream.summary().path("fullDraft").path("medianNanos").asLong())
                .isEqualTo(DraftEnginePerformanceHardeningV1Artifacts.BEFORE_MEDIAN_NANOS);
    }

    @Test
    void generatedManifestRejectsOneByteTamper(@TempDir Path temporary)
            throws Exception {
        for (String file : DraftEnginePerformanceHardeningV1Artifacts.ARTIFACTS) {
            Files.writeString(temporary.resolve(file), file + '\n',
                    StandardCharsets.UTF_8);
        }
        DraftEnginePerformanceHardeningV1Artifacts.writeManifest(temporary);
        assertThat(DraftEnginePerformanceHardeningV1Artifacts
                .verifyGeneratedManifest(temporary)).isEqualTo(7);

        Path target = temporary.resolve(
                DraftEnginePerformanceHardeningV1Artifacts.SUMMARY);
        Files.writeString(target, "x", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        assertThatThrownBy(() -> DraftEnginePerformanceHardeningV1Artifacts
                .verifyGeneratedManifest(temporary))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Manifest SHA mismatch");
    }

    @Test
    void performanceDiagnosticsRemainExcludedFromDefaultTestTask() throws Exception {
        String build = Files.readString(Path.of("build.gradle"),
                StandardCharsets.UTF_8);
        assertThat(build).contains("excludeTags 'diagnostic'");
        assertThat(build).contains("runDraftEnginePerformanceCandidateV1",
                "runDraftEnginePerformanceHardeningV1");
    }

    @Test
    void analysisTimingUsesStableRootLocaleDecimalFormatting() {
        assertThat(DraftEnginePerformanceHardeningV1Artifacts
                .seconds(7_128_035_400L)).isEqualTo("7.128");
        assertThat(DraftEnginePerformanceHardeningV1Artifacts
                .millis(123_456_789L)).isEqualTo("123.457");
    }
}
