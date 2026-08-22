package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.simulator.JungleClearContribution;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreJungleBaselineV2GeneratorTest {
    @TempDir Path tempDir;

    @Test
    void v2ScheduleIsFrozenToTheThreePreJungleProfiles() {
        assertThat(PreJungleBaselineV2Generator.fixedProfiles()).containsExactly(
                SimulationRuntimeProfileId.BASELINE_V1,
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
                SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
        assertThat(PreJungleBaselineV2Generator.fixedProfiles())
                .allSatisfy(profileId -> assertThat(SimulationRuntimeProfiles.resolve(profileId)
                        .gameplayConfiguration().jungleClearContribution())
                        .isEqualTo(JungleClearContribution.DISABLED_NOT_INTEGRATED));
    }

    @Test
    void sourceArtifactCanBeVerifiedButNotOverwrittenWithDifferentBytes() throws Exception {
        Path source = tempDir.resolve("source");
        Path report = tempDir.resolve("report");
        byte[] original = "immutable-baseline".getBytes(StandardCharsets.UTF_8);

        PreJungleBaselineV2Generator.writeOutputs(original, source, report);
        PreJungleBaselineV2Generator.writeOutputs(original, source, report);

        Path json = source.resolve("pre-jungle-runtime-baseline-v2.json");
        assertThat(Files.readAllBytes(json)).isEqualTo(original);
        assertThatThrownBy(() -> PreJungleBaselineV2Generator.writeOutputs(
                "different".getBytes(StandardCharsets.UTF_8), source, report))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Immutable Pre-Jungle V2 artifact");
        assertThat(Files.readAllBytes(json)).isEqualTo(original);
        assertThat(Files.readString(
                report.resolve("pre-jungle-runtime-baseline-v2.json")))
                .isEqualTo("different");
    }
}
