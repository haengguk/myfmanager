package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class Phase13GB2CalibrationContractTest {
    @Test
    void schedulesExactlyTwelveThousandCalibrationOnlyProfileJobs() {
        var schedule = Phase13GB1AuditSchedule.create();
        var jobs = Phase13GB2CalibrationContract.jobs(schedule);

        assertThat(jobs).hasSize(12_000);
        assertThat(jobs.stream().map(
                Phase13GB2CalibrationContract.CalibrationJob::jobId).distinct())
                .hasSize(12_000);
        assertThat(jobs).allSatisfy(job -> {
            assertThat(job.sampleLane())
                    .isEqualTo(Phase13GB1AuditSchedule.SampleLane.CALIBRATION);
            var fixture = schedule.allFixtures().stream()
                    .filter(value -> value.fixtureId().equals(job.fixtureId()))
                    .findFirst().orElseThrow();
            assertThat(fixture.calibrationSeeds()).contains(job.seed());
            assertThat(fixture.holdoutSeeds()).doesNotContain(job.seed());
        });
        assertThat(jobs.stream().filter(job -> job.fixtureLane()
                == Phase13GB1AuditSchedule.FixtureLane.PRIMARY_LEAGUE_G1))
                .hasSize(10_800);
        assertThat(jobs.stream().filter(job -> job.fixtureLane()
                == Phase13GB1AuditSchedule.FixtureLane.SECONDARY_HARD_FEARLESS_G2))
                .hasSize(1_200);
    }

    @Test
    void freezesFivePairedMarginalsWithoutOpeningHoldout() {
        assertThat(Phase13GB2CalibrationContract.SCHEMA)
                .endsWith("CONTRACT_V3");
        assertThat(Phase13GB2CalibrationModel.CHECKPOINT_SCHEMA)
                .endsWith("CHECKPOINT_V3");
        assertThat(Phase13GB2CalibrationContract.MARGINAL_COMPARISONS)
                .extracting(Phase13GB2CalibrationContract.MarginalComparison::comparisonId)
                .containsExactly(
                        "MATCHUP_MINUS_BASELINE",
                        "FULL_MINUS_MATCHUP",
                        "ECONOMY_MINUS_FULL",
                        "TEMPO_MINUS_ECONOMY",
                        "TEMPO_MINUS_BASELINE");
        assertThat(Phase13GB2CalibrationContract.MARGINAL_COMPARISONS)
                .allSatisfy(comparison -> {
                    assertThat(comparison.fromProfile()).isNotEqualTo(comparison.toProfile());
                    assertThat(Phase13GB1RealMatchHarness.AUDIT_PROFILES)
                            .contains(comparison.fromProfile(), comparison.toProfile());
                });
        assertThat(Phase13GB2CalibrationContract.FIXED_CHECKPOINT_SECONDS)
                .containsExactly(600, 900, 1_200, 1_500, 1_800);
        assertThat(Phase13GB2CalibrationContract.EXPECTED_ROWS_PER_FIXTURE)
                .isEqualTo(24 * 5);
    }

    @Test
    void everyFixtureJobSetContainsEachProfileForEveryUniqueCalibrationSeed() {
        var schedule = Phase13GB1AuditSchedule.create();
        for (var fixture : schedule.allFixtures()) {
            var jobs = Phase13GB2CalibrationContract.jobs(fixture);
            assertThat(jobs).hasSize(120);
            assertThat(jobs.stream().map(
                    Phase13GB2CalibrationContract.CalibrationJob::seed).distinct())
                    .containsExactlyElementsOf(fixture.calibrationSeeds());
            for (int seedIndex = 0; seedIndex < 24; seedIndex++) {
                int offset = seedIndex * 5;
                assertThat(jobs.subList(offset, offset + 5).stream().map(
                        Phase13GB2CalibrationContract.CalibrationJob::profileId))
                        .containsExactlyElementsOf(Phase13GB1RealMatchHarness.AUDIT_PROFILES);
            }
            HashSet<Long> all = new HashSet<>(fixture.calibrationSeeds());
            all.retainAll(fixture.holdoutSeeds());
            assertThat(all).isEmpty();
        }
        assertThat(Phase13GB1RealMatchHarness.AUDIT_PROFILES)
                .contains(SimulationRuntimeProfileId
                        .FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1);
    }
}
