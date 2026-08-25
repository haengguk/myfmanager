package com.lolfm.application;

import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Frozen, bounded schedule and acceptance policy for the Match Engine V9 audit. */
public final class MatchEngineV9RequalificationContract {
    public static final String CONTRACT_SCHEMA =
            "MATCH_ENGINE_V9_MATCHUP_COMPOSITION_REQUALIFICATION_CONTRACT_V2";
    public static final String SCHEDULE_SCHEMA =
            "MATCH_ENGINE_V9_MATCHUP_COMPOSITION_REQUALIFICATION_SCHEDULE_V2";
    public static final String SCHEDULE_VERSION =
            "MATCH_ENGINE_V9_REAL_LCK_PAIRED_100_FIXTURE_8_4_V2";
    public static final String SEED_NAMESPACE =
            "MATCH_ENGINE_V9_REQUALIFICATION_FRESH_SEEDS_V2";
    public static final String SEED_BINDING_HASH = sha256(
            "schema=MATCH_ENGINE_V9_REQUALIFICATION_SEED_BINDING_V2\n"
                    + "scheduleVersion=" + SCHEDULE_VERSION + "\n"
                    + "calibrationSeedsPerFixture=8\n"
                    + "holdoutSeedsPerFixture=4\n"
                    + "profiles=BASELINE_V1,MATCHUP_ONLY_CANDIDATE_V1,"
                    + "FULL_SYSTEM_CANDIDATE_V1\n");
    public static final int CALIBRATION_SEEDS_PER_FIXTURE = 8;
    public static final int HOLDOUT_SEEDS_PER_FIXTURE = 4;
    public static final int EXPECTED_FIXTURES = 100;
    public static final int EXPECTED_CALIBRATION_ROWS = 2_400;
    public static final int EXPECTED_HOLDOUT_ROWS = 1_200;
    public static final int EXPECTED_OFFICIAL_ROWS = 3_600;
    public static final List<SimulationRuntimeProfileId> PROFILES = List.of(
            SimulationRuntimeProfileId.BASELINE_V1,
            SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
            SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);

    public static final AcceptanceGates GATES = new AcceptanceGates(
            1.5, 7.5, 10.0, 12.0, 60.0, 120.0);

    private static final Schedule FROZEN = build();

    private MatchEngineV9RequalificationContract() {
    }

    public static Schedule schedule() {
        return FROZEN;
    }

    public static Schedule requireFrozen(Schedule candidate) {
        if (!FROZEN.equals(candidate)) {
            throw new IllegalArgumentException("V9 requalification schedule is not frozen");
        }
        String actual = sha256(canonicalSchedule(candidate.fixtures()));
        if (!actual.equals(candidate.scheduleHash())) {
            throw new IllegalArgumentException("V9 schedule raw content/hash mismatch");
        }
        return candidate;
    }

    private static Schedule build() {
        ArrayList<Fixture> fixtures = new ArrayList<>();
        for (var source : Phase13GB1AuditSchedule.create().allFixtures()) {
            fixtures.add(new Fixture(
                    source.fixtureId(), source.fixtureLane(), source.pairId(),
                    source.blueTeamCode(), source.redTeamCode(), source.seriesGameNumber(),
                    seeds(source, SampleLane.CALIBRATION, CALIBRATION_SEEDS_PER_FIXTURE),
                    seeds(source, SampleLane.HOLDOUT, HOLDOUT_SEEDS_PER_FIXTURE)));
        }
        if (fixtures.size() != EXPECTED_FIXTURES) {
            throw new IllegalStateException("V9 schedule fixture count drift");
        }
        String hash = sha256(canonicalSchedule(fixtures));
        Schedule result = new Schedule(
                SCHEDULE_SCHEMA, SCHEDULE_VERSION, SEED_NAMESPACE, SEED_BINDING_HASH,
                CALIBRATION_SEEDS_PER_FIXTURE, HOLDOUT_SEEDS_PER_FIXTURE,
                List.copyOf(fixtures), hash);
        requireNoSeedOverlap(result);
        return result;
    }

    public static SeedOverlapAudit requireNoSeedOverlap(Schedule schedule) {
        Set<Long> historical = new HashSet<>();
        for (var fixture : Phase13GB1AuditSchedule.create().allFixtures()) {
            historical.addAll(fixture.calibrationSeeds());
            historical.addAll(fixture.holdoutSeeds());
        }
        Set<Long> fresh = new HashSet<>();
        int freshCollisionCount = 0;
        int historicalOverlapCount = 0;
        for (Fixture fixture : schedule.fixtures()) {
            for (long seed : concat(fixture.calibrationSeeds(), fixture.holdoutSeeds())) {
                if (!fresh.add(seed)) freshCollisionCount++;
                if (historical.contains(seed)) historicalOverlapCount++;
            }
        }
        SeedOverlapAudit audit = new SeedOverlapAudit(
                SEED_NAMESPACE, SEED_BINDING_HASH, historical.size(), fresh.size(),
                historicalOverlapCount, freshCollisionCount);
        if (!audit.clean() || fresh.size() != EXPECTED_FIXTURES
                * (CALIBRATION_SEEDS_PER_FIXTURE + HOLDOUT_SEEDS_PER_FIXTURE)) {
            throw new IllegalStateException("V9 fresh seed namespace overlaps consumed evidence");
        }
        return audit;
    }

    public static Phase13GB1AuditSchedule.Fixture sourceFixture(Fixture fixture) {
        return Phase13GB1AuditSchedule.create().allFixtures().stream()
                .filter(value -> value.fixtureId().equals(fixture.fixtureId()))
                .findFirst().orElseThrow();
    }

    public static long dryRunSeed(Fixture fixture) {
        var source = sourceFixture(fixture);
        return seeds(source, SampleLane.DRY_RUN, 1).getFirst();
    }

    private static List<Long> seeds(
            Phase13GB1AuditSchedule.Fixture fixture, SampleLane lane, int count) {
        ArrayList<Long> result = new ArrayList<>(count);
        for (int replicate = 0; replicate < count; replicate++) {
            String canonical = "seedSchema=MATCH_ENGINE_V9_REQUALIFICATION_SEED_V2\n"
                    + "seedNamespace=" + SEED_NAMESPACE + '\n'
                    + "seedBindingHash=" + SEED_BINDING_HASH + '\n'
                    + "fixtureId=" + fixture.fixtureId() + '\n'
                    + "fixtureLane=" + fixture.fixtureLane() + '\n'
                    + "blueTeamCode=" + fixture.blueTeamCode() + '\n'
                    + "redTeamCode=" + fixture.redTeamCode() + '\n'
                    + "seriesGameNumber=" + fixture.seriesGameNumber() + '\n'
                    + "sampleLane=" + lane + '\n'
                    + "replicate=" + replicate + '\n';
            result.add(ByteBuffer.wrap(digest(canonical.getBytes(StandardCharsets.UTF_8))).getLong());
        }
        return List.copyOf(result);
    }

    private static String canonicalSchedule(List<Fixture> fixtures) {
        StringBuilder value = new StringBuilder()
                .append("scheduleSchema=").append(SCHEDULE_SCHEMA).append('\n')
                .append("scheduleVersion=").append(SCHEDULE_VERSION).append('\n')
                .append("seedNamespace=").append(SEED_NAMESPACE).append('\n')
                .append("seedBindingHash=").append(SEED_BINDING_HASH).append('\n')
                .append("calibrationSeedsPerFixture=").append(CALIBRATION_SEEDS_PER_FIXTURE).append('\n')
                .append("holdoutSeedsPerFixture=").append(HOLDOUT_SEEDS_PER_FIXTURE).append('\n');
        for (Fixture fixture : fixtures) {
            value.append("fixture=").append(fixture.fixtureId()).append('|')
                    .append(fixture.fixtureLane()).append('|').append(fixture.pairId()).append('|')
                    .append(fixture.blueTeamCode()).append('|').append(fixture.redTeamCode())
                    .append('|').append(fixture.seriesGameNumber()).append('\n');
            appendSeeds(value, fixture.fixtureId(), SampleLane.CALIBRATION,
                    fixture.calibrationSeeds());
            appendSeeds(value, fixture.fixtureId(), SampleLane.HOLDOUT,
                    fixture.holdoutSeeds());
        }
        return value.toString();
    }

    private static void appendSeeds(
            StringBuilder target, String fixtureId, SampleLane lane, List<Long> seeds) {
        for (int index = 0; index < seeds.size(); index++) {
            target.append("seed=").append(fixtureId).append('|').append(lane).append('|')
                    .append(index).append('|').append(seeds.get(index)).append('\n');
        }
    }

    private static List<Long> concat(List<Long> first, List<Long> second) {
        ArrayList<Long> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    public static String sha256(String value) {
        return HexFormat.of().formatHex(digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256(byte[] value) {
        return HexFormat.of().formatHex(digest(value));
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public enum SampleLane { DRY_RUN, CALIBRATION, HOLDOUT }

    public record Fixture(
            String fixtureId,
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String pairId,
            String blueTeamCode,
            String redTeamCode,
            int seriesGameNumber,
            List<Long> calibrationSeeds,
            List<Long> holdoutSeeds
    ) {
        public Fixture {
            Objects.requireNonNull(fixtureId);
            Objects.requireNonNull(fixtureLane);
            Objects.requireNonNull(pairId);
            Objects.requireNonNull(blueTeamCode);
            Objects.requireNonNull(redTeamCode);
            calibrationSeeds = List.copyOf(calibrationSeeds);
            holdoutSeeds = List.copyOf(holdoutSeeds);
            if (calibrationSeeds.size() != CALIBRATION_SEEDS_PER_FIXTURE
                    || holdoutSeeds.size() != HOLDOUT_SEEDS_PER_FIXTURE
                    || calibrationSeeds.stream().anyMatch(holdoutSeeds::contains)) {
                throw new IllegalArgumentException("Invalid V9 fixture seed allocation");
            }
        }
    }

    public record Schedule(
            String schemaVersion,
            String scheduleVersion,
            String seedNamespace,
            String seedBindingHash,
            int calibrationSeedsPerFixture,
            int holdoutSeedsPerFixture,
            List<Fixture> fixtures,
            String scheduleHash
    ) {
        public Schedule {
            fixtures = List.copyOf(fixtures);
        }
    }

    public record AcceptanceGates(
            double absoluteBlueWinRateDeltaPercentagePoints,
            double pairedWinnerChangedRatePercent,
            double objectiveChangedRatePercent,
            double structureChangedRatePercent,
            double absoluteMeanDurationDeltaSeconds,
            double absoluteP95DurationDeltaSeconds
    ) { }

    public record SeedOverlapAudit(
            String seedNamespace,
            String seedBindingHash,
            int historicalSeedCount,
            int freshSeedCount,
            int historicalOverlapCount,
            int freshCollisionCount
    ) {
        public boolean clean() {
            return historicalOverlapCount == 0 && freshCollisionCount == 0;
        }
    }
}
