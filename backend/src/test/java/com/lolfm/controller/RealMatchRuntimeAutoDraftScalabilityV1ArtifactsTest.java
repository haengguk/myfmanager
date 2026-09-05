package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1Canonicalizer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

class RealMatchRuntimeAutoDraftScalabilityV1ArtifactsTest {
    @TempDir Path temporary;

    @Test
    @Tag("diagnostic")
    @Tag("historical-artifact")
    void existingPerformanceBaselineManifestIsIndependentlyVerifiedFourOfFour()
            throws Exception {
        var evidence = RealMatchRuntimeAutoDraftScalabilityV1Artifacts
                .verifyPerformanceBaselineManifest(Path.of("").toAbsolutePath());

        assertThat(evidence.rawManifestSha256()).isEqualTo(
                RealMatchRuntimeAutoDraftScalabilityV1Artifacts
                        .PERFORMANCE_MANIFEST_SHA256);
        assertThat(evidence.entryCount()).isEqualTo(4);
        assertThat(evidence.entriesVerified()).isEqualTo(4);
    }

    @Test
    void contractJsonAndCsvSchemasAreCanonicalAndExplicit() throws Exception {
        MatchEngineV1Canonicalizer canonicalizer = new MatchEngineV1Canonicalizer(
                new ObjectMapper().findAndRegisterModules());
        var environment = new RealMatchRuntimeAutoDraftScalabilityV1Artifacts.Environment(
                "a".repeat(40), true, "21", "vm", "os", "version", "arch",
                12, 1_000_000L, "gradle", "spring", "b".repeat(64), 1,
                "c".repeat(64), 1,
                "OPTIMIZED_LAUNCH_FALSE_NORMAL_TIERED_C2_CAPABLE",
                "UNCHANGED_NORMAL_TIERED_C2_CAPABLE");
        var upstream = new RealMatchRuntimeAutoDraftScalabilityV1Artifacts.ManifestEvidence(
                RealMatchRuntimeAutoDraftScalabilityV1Artifacts
                        .PERFORMANCE_MANIFEST_SHA256,
                4, 4, "RAW_SHA256_AND_ALL_ENTRIES_VERIFIED_NO_REGENERATION");
        String first = canonicalizer.canonicalJson(
                RealMatchRuntimeAutoDraftScalabilityV1Artifacts.contract(
                        environment, upstream));
        String replay = canonicalizer.canonicalJson(
                RealMatchRuntimeAutoDraftScalabilityV1Artifacts.contract(
                        environment, upstream));

        assertThat(first).isEqualTo(replay);
        assertThat(new ObjectMapper().readTree(first).path("scheduleHash").asText())
                .hasSize(64);
        assertThat(RealMatchRuntimeAutoDraftScalabilityV1Artifacts.runtimeCsv(List.of()))
                .startsWith("launchMode,fixtureId,requestKind").endsWith("\n");
        assertThat(RealMatchRuntimeAutoDraftScalabilityV1Artifacts.fixtureCsv(List.of()))
                .startsWith("fixtureIndex,fixtureId,measuredOrdinal").endsWith("\n");
        assertThat(RealMatchRuntimeAutoDraftScalabilityV1Artifacts.turnCsv(List.of()))
                .startsWith("fixtureId,measuredOrdinal,turn").endsWith("\n");
    }

    @Test
    void manifestRejectsOneByteTamper() throws Exception {
        for (String artifact : RealMatchRuntimeAutoDraftScalabilityV1Artifacts.ARTIFACTS) {
            Files.writeString(temporary.resolve(artifact), artifact + "\n",
                    StandardCharsets.UTF_8);
        }
        RealMatchRuntimeAutoDraftScalabilityV1Artifacts.writeManifest(temporary);
        RealMatchRuntimeAutoDraftScalabilityV1Artifacts.verifyManifest(temporary);

        Files.writeString(temporary.resolve(
                        RealMatchRuntimeAutoDraftScalabilityV1Artifacts.SUMMARY),
                "tampered\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> RealMatchRuntimeAutoDraftScalabilityV1Artifacts
                .verifyManifest(temporary))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHA mismatch");
    }

    @Test
    void expensiveAuditAndCrossJvmProbeStayOutsideDefaultTest() throws Exception {
        String build = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);
        assertThat(build).contains("excludeTags 'diagnostic'")
                .contains("runRealMatchRuntimeAutoDraftScalabilityAuditV1")
                .contains("includeTags \"real-match-runtime-auto-draft-scalability-v1\"")
                .contains("runRealMatchAutoDraftCrossJvmProbeA")
                .contains("runRealMatchAutoDraftCrossJvmProbeB")
                .contains("maxParallelForks = 1")
                .contains("outputs.upToDateWhen { false }");
    }
}
