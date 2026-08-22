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

class PreJungleTempoBaselineV1GeneratorTest {
    @TempDir Path tempDir;

    @Test
    void scheduleFreezesTheFourPreTempoProfilesAndTheirContributions() {
        assertThat(PreJungleTempoBaselineV1Generator.fixedProfiles()).containsExactly(
                SimulationRuntimeProfileId.BASELINE_V1,
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
                SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1,
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1);
        assertThat(PreJungleTempoBaselineV1Generator.fixedProfiles())
                .allSatisfy(profileId -> {
                    var profile = SimulationRuntimeProfiles.resolve(profileId);
                    var expected = profileId
                            == SimulationRuntimeProfileId
                            .FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1
                            ? JungleClearContribution.ECONOMY_V1
                            : JungleClearContribution.DISABLED_NOT_INTEGRATED;
                    assertThat(profile.gameplayConfiguration().jungleClearContribution())
                            .isEqualTo(expected);
                    assertThat(PreJungleTempoBaselineV1Generator.profileBaseline(profile))
                            .isNotNull();
                });
    }

    @Test
    void sourceArtifactIsImmutableWhileMismatchRemainsInReport() throws Exception {
        Path source = tempDir.resolve("source");
        Path report = tempDir.resolve("report");
        byte[] original = "pre-tempo-baseline".getBytes(StandardCharsets.UTF_8);

        PreJungleTempoBaselineV1Generator.writeOutputs(original, source, report);
        PreJungleTempoBaselineV1Generator.writeOutputs(original, source, report);

        Path json = source.resolve("pre-jungle-tempo-runtime-baseline-v1.json");
        assertThat(Files.readAllBytes(json)).isEqualTo(original);
        assertThatThrownBy(() -> PreJungleTempoBaselineV1Generator.writeOutputs(
                "different".getBytes(StandardCharsets.UTF_8), source, report))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Immutable Pre-Jungle-Tempo V1 artifact");
        assertThat(Files.readAllBytes(json)).isEqualTo(original);
        assertThat(Files.readString(
                report.resolve("pre-jungle-tempo-runtime-baseline-v1.json")))
                .isEqualTo("different");
    }
}
