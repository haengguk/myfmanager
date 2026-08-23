package com.lolfm.application;

import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Frozen job and comparison contract for calibration only; holdout is never scheduled here. */
public final class Phase13GB2CalibrationContract {
    public static final String SCHEMA = "PHASE_13G_B2_REAL_DATA_CALIBRATION_CONTRACT_V2";
    public static final String JOB_SCHEMA = "PHASE_13G_B2_CALIBRATION_JOB_V1";
    public static final int EXPECTED_PRIMARY_FIXTURES = 90;
    public static final int EXPECTED_SECONDARY_FIXTURES = 10;
    public static final int EXPECTED_FIXTURES = 100;
    public static final int EXPECTED_SEEDS_PER_FIXTURE = 24;
    public static final int EXPECTED_PROFILES_PER_SEED = 5;
    public static final int EXPECTED_ROWS_PER_FIXTURE = 120;
    public static final int EXPECTED_PRIMARY_MATCHES = 10_800;
    public static final int EXPECTED_SECONDARY_MATCHES = 1_200;
    public static final int EXPECTED_MATCHES = 12_000;
    public static final List<Integer> FIXED_CHECKPOINT_SECONDS =
            List.of(600, 900, 1_200, 1_500, 1_800);
    public static final List<MarginalComparison> MARGINAL_COMPARISONS = List.of(
            new MarginalComparison(
                    "MATCHUP_MINUS_BASELINE",
                    SimulationRuntimeProfileId.BASELINE_V1,
                    SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1),
            new MarginalComparison(
                    "FULL_MINUS_MATCHUP",
                    SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
                    SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1),
            new MarginalComparison(
                    "ECONOMY_MINUS_FULL",
                    SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1,
                    SimulationRuntimeProfileId
                            .FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1),
            new MarginalComparison(
                    "TEMPO_MINUS_ECONOMY",
                    SimulationRuntimeProfileId
                            .FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1,
                    SimulationRuntimeProfileId
                            .FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1),
            new MarginalComparison(
                    "TEMPO_MINUS_BASELINE",
                    SimulationRuntimeProfileId.BASELINE_V1,
                    SimulationRuntimeProfileId
                            .FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1));

    private Phase13GB2CalibrationContract() {
    }

    public static List<CalibrationJob> jobs(Phase13GB1AuditSchedule.Schedule schedule) {
        schedule = Phase13GB1AuditSchedule.requireFrozen(schedule);
        ArrayList<CalibrationJob> result = new ArrayList<>(EXPECTED_MATCHES);
        for (Phase13GB1AuditSchedule.Fixture fixture : schedule.allFixtures()) {
            result.addAll(jobs(fixture));
        }
        validateCompletePlan(schedule, result);
        return List.copyOf(result);
    }

    public static List<CalibrationJob> jobs(Phase13GB1AuditSchedule.Fixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        ArrayList<CalibrationJob> result = new ArrayList<>(EXPECTED_ROWS_PER_FIXTURE);
        for (int seedIndex = 0; seedIndex < fixture.calibrationSeeds().size(); seedIndex++) {
            long seed = fixture.calibrationSeeds().get(seedIndex);
            for (int profileIndex = 0;
                    profileIndex < Phase13GB1RealMatchHarness.AUDIT_PROFILES.size();
                    profileIndex++) {
                SimulationRuntimeProfileId profileId =
                        Phase13GB1RealMatchHarness.AUDIT_PROFILES.get(profileIndex);
                result.add(new CalibrationJob(
                        JOB_SCHEMA,
                        fixture.fixtureId(),
                        fixture.fixtureLane(),
                        fixture.pairId(),
                        fixture.blueTeamCode(),
                        fixture.redTeamCode(),
                        fixture.seriesGameNumber(),
                        Phase13GB1AuditSchedule.SampleLane.CALIBRATION,
                        seedIndex,
                        seed,
                        profileIndex,
                        profileId));
            }
        }
        if (result.size() != EXPECTED_ROWS_PER_FIXTURE) {
            throw new IllegalStateException(
                    "Calibration fixture does not contain exactly 120 jobs: "
                            + fixture.fixtureId());
        }
        return List.copyOf(result);
    }

    private static void validateCompletePlan(
            Phase13GB1AuditSchedule.Schedule schedule,
            List<CalibrationJob> jobs
    ) {
        if (schedule.primaryFixtures().size() != EXPECTED_PRIMARY_FIXTURES
                || schedule.secondaryHardFearlessFixtures().size()
                        != EXPECTED_SECONDARY_FIXTURES
                || schedule.allFixtures().size() != EXPECTED_FIXTURES
                || schedule.calibrationSeedsPerFixture() != EXPECTED_SEEDS_PER_FIXTURE
                || Phase13GB1RealMatchHarness.AUDIT_PROFILES.size()
                        != EXPECTED_PROFILES_PER_SEED
                || jobs.size() != EXPECTED_MATCHES
                || jobs.stream().map(CalibrationJob::jobId).distinct().count()
                        != EXPECTED_MATCHES
                || jobs.stream().anyMatch(job -> job.sampleLane()
                        != Phase13GB1AuditSchedule.SampleLane.CALIBRATION)) {
            throw new IllegalStateException("Frozen B2 calibration job plan is incomplete");
        }
        long primary = jobs.stream().filter(job -> job.fixtureLane()
                == Phase13GB1AuditSchedule.FixtureLane.PRIMARY_LEAGUE_G1).count();
        long secondary = jobs.size() - primary;
        if (primary != EXPECTED_PRIMARY_MATCHES || secondary != EXPECTED_SECONDARY_MATCHES) {
            throw new IllegalStateException("Calibration lane match counts differ from contract");
        }
    }

    public record CalibrationJob(
            String schemaVersion,
            String fixtureId,
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String pairId,
            String blueTeamCode,
            String redTeamCode,
            int seriesGameNumber,
            Phase13GB1AuditSchedule.SampleLane sampleLane,
            int seedIndex,
            long seed,
            int profileIndex,
            SimulationRuntimeProfileId profileId
    ) {
        public CalibrationJob {
            if (!JOB_SCHEMA.equals(schemaVersion)) {
                throw new IllegalArgumentException("Unsupported B2 calibration job schema");
            }
            Objects.requireNonNull(fixtureLane, "fixtureLane");
            Objects.requireNonNull(sampleLane, "sampleLane");
            Objects.requireNonNull(profileId, "profileId");
            if (sampleLane != Phase13GB1AuditSchedule.SampleLane.CALIBRATION) {
                throw new IllegalArgumentException("B2 jobs may consume calibration seeds only");
            }
            if (seedIndex < 0 || seedIndex >= EXPECTED_SEEDS_PER_FIXTURE) {
                throw new IllegalArgumentException("Invalid calibration seed index");
            }
            if (profileIndex < 0 || profileIndex >= EXPECTED_PROFILES_PER_SEED
                    || Phase13GB1RealMatchHarness.AUDIT_PROFILES.get(profileIndex)
                            != profileId) {
                throw new IllegalArgumentException("Invalid calibration profile order");
            }
        }

        public String jobId() {
            return fixtureId + "|CALIBRATION|" + seedIndex + '|' + profileId.name();
        }
    }

    public record MarginalComparison(
            String comparisonId,
            SimulationRuntimeProfileId fromProfile,
            SimulationRuntimeProfileId toProfile
    ) {
        public MarginalComparison {
            Objects.requireNonNull(comparisonId, "comparisonId");
            Objects.requireNonNull(fromProfile, "fromProfile");
            Objects.requireNonNull(toProfile, "toProfile");
            if (fromProfile == toProfile) {
                throw new IllegalArgumentException("A marginal comparison needs two profiles");
            }
        }
    }
}
