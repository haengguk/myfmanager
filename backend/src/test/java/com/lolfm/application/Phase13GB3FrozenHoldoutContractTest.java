package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Phase13GB3FrozenHoldoutContractTest {
    @Test
    void schedulesExactlyFourThousandReservedHoldoutJobs() {
        var schedule = Phase13GB1AuditSchedule.create();
        var jobs = Phase13GB3FrozenHoldoutContract.jobs(schedule);

        assertThat(jobs).hasSize(4_000);
        assertThat(jobs.stream().map(
                Phase13GB3FrozenHoldoutContract.HoldoutJob::jobId).distinct())
                .hasSize(4_000);
        assertThat(jobs).allSatisfy(job -> {
            assertThat(job.sampleLane())
                    .isEqualTo(Phase13GB1AuditSchedule.SampleLane.HOLDOUT);
            var fixture = schedule.allFixtures().stream()
                    .filter(value -> value.fixtureId().equals(job.fixtureId()))
                    .findFirst().orElseThrow();
            assertThat(fixture.holdoutSeeds()).contains(job.seed());
            assertThat(fixture.calibrationSeeds()).doesNotContain(job.seed());
        });
        assertThat(jobs.stream().filter(job -> job.fixtureLane()
                == Phase13GB1AuditSchedule.FixtureLane.PRIMARY_LEAGUE_G1))
                .hasSize(3_600);
        assertThat(jobs.stream().filter(job -> job.fixtureLane()
                == Phase13GB1AuditSchedule.FixtureLane.SECONDARY_HARD_FEARLESS_G2))
                .hasSize(400);
    }

    @Test
    void everyFixtureHasEightSeedsAndExactFiveProfileOrder() {
        var schedule = Phase13GB1AuditSchedule.create();
        for (var fixture : schedule.allFixtures()) {
            var jobs = Phase13GB3FrozenHoldoutContract.jobs(fixture);
            assertThat(jobs).hasSize(40);
            assertThat(jobs.stream().map(
                    Phase13GB3FrozenHoldoutContract.HoldoutJob::seed).distinct())
                    .containsExactlyElementsOf(fixture.holdoutSeeds());
            for (int seed = 0; seed < 8; seed++) {
                assertThat(jobs.subList(seed * 5, seed * 5 + 5).stream().map(
                        Phase13GB3FrozenHoldoutContract.HoldoutJob::profileId))
                        .containsExactlyElementsOf(
                                Phase13GB3FrozenHoldoutContract.PROFILE_ORDER);
            }
        }
    }

    @Test
    void refusesCalibrationLaneAndChangedProfileOrder() {
        var fixture = Phase13GB1AuditSchedule.create().allFixtures().getFirst();
        assertThatThrownBy(() -> new Phase13GB3FrozenHoldoutContract.HoldoutJob(
                Phase13GB3FrozenHoldoutContract.JOB_SCHEMA,
                fixture.fixtureId(), fixture.fixtureLane(), fixture.pairId(),
                fixture.blueTeamCode(), fixture.redTeamCode(), fixture.seriesGameNumber(),
                Phase13GB1AuditSchedule.SampleLane.CALIBRATION,
                0, fixture.calibrationSeeds().getFirst(), 0,
                SimulationRuntimeProfileId.BASELINE_V1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holdout seeds only");
        assertThatThrownBy(() -> new Phase13GB3FrozenHoldoutContract.HoldoutJob(
                Phase13GB3FrozenHoldoutContract.JOB_SCHEMA,
                fixture.fixtureId(), fixture.fixtureLane(), fixture.pairId(),
                fixture.blueTeamCode(), fixture.redTeamCode(), fixture.seriesGameNumber(),
                Phase13GB1AuditSchedule.SampleLane.HOLDOUT,
                0, fixture.holdoutSeeds().getFirst(), 0,
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profile order");
    }

    @Test
    void phaseSourceAndBuildContractsHaveExplicitNonOverlappingScopes() throws Exception {
        Path root = Path.of(".");
        long b1Files;
        long b2Files;
        long b3Files;
        try (var files = Files.walk(root.resolve("src/test/java"))) {
            var names = files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString()).toList();
            b1Files = names.stream().filter(name -> name.startsWith("Phase13GB1")).count();
            b2Files = names.stream().filter(name -> name.startsWith("Phase13GB2")).count();
            b3Files = names.stream().filter(name -> name.startsWith("Phase13GB3")).count();
        }
        var b1 = Phase13GB1AuditArtifactWriter.phaseTestSourceTree(root, "Phase13GB1");
        var b2 = Phase13GB1AuditArtifactWriter.phaseTestSourceTree(root, "Phase13GB");
        var b3 = Phase13GB1AuditArtifactWriter.phaseTestSourceTree(root, "Phase13GB3");
        assertThat(b1.fileCount()).isEqualTo(b1Files + 1);
        assertThat(b2.fileCount()).isEqualTo(b1Files + b2Files + 2);
        assertThat(b3.fileCount()).isEqualTo(b3Files + 1);
        assertThat(b1.hash()).isNotEqualTo(b2.hash()).isNotEqualTo(b3.hash());
    }

    @Test
    void refusesFrozenContractWhoseRawBytesDoNotMatchHash(@TempDir Path output)
            throws Exception {
        Files.writeString(output.resolve(Phase13GB3CheckpointStore.CONTRACT_FILE), "{}\n");
        Files.writeString(output.resolve(Phase13GB3CheckpointStore.CONTRACT_HASH_FILE),
                "0".repeat(64) + "  " + Phase13GB3CheckpointStore.CONTRACT_FILE + "\n");

        assertThatThrownBy(() -> new Phase13GB3CheckpointStore(
                new com.fasterxml.jackson.databind.ObjectMapper())
                .readFrozenContract(output))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("modified");
    }
}
