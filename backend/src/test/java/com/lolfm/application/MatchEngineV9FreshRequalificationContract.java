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

/** Frozen schedule and pre-result acceptance policy for the fresh Auto Draft audit. */
public final class MatchEngineV9FreshRequalificationContract {
    public static final String CONTRACT_SCHEMA =
            "MATCH_ENGINE_V9_AUTO_DRAFT_MATCHUP_COMPOSITION_FRESH_REQUALIFICATION_CONTRACT_V2";
    public static final String SCHEDULE_SCHEMA =
            "MATCH_ENGINE_V9_AUTO_DRAFT_MATCHUP_COMPOSITION_FRESH_REQUALIFICATION_SCHEDULE_V2";
    public static final String SCHEDULE_VERSION =
            "MATCH_ENGINE_V9_REAL_LCK_AUTO_DRAFT_PAIRED_100_FIXTURE_4_4_V2";
    public static final String SEED_NAMESPACE =
            "MATCH_ENGINE_V9_AUTO_DRAFT_FRESH_REQUALIFICATION_SEEDS_V2_"
                    + "AFTER_CAUSALITY_EVIDENCE_HARDENING";
    public static final String DRAFT_REUSE_POLICY =
            "ONE_PRODUCTION_AUTO_DRAFT_PER_FIXTURE_AND_SEED_SHARED_BY_ALL_PROFILES";
    public static final int EXPECTED_FIXTURES = 100;
    public static final int EXPECTED_GAME_ONE_FIXTURES = 90;
    public static final int EXPECTED_GAME_TWO_FIXTURES = 10;
    public static final int CALIBRATION_SEEDS_PER_FIXTURE = 4;
    public static final int HOLDOUT_SEEDS_PER_FIXTURE = 4;
    public static final int EXPECTED_DRAFTS = 800;
    public static final int EXPECTED_CALIBRATION_ROWS = 1_200;
    public static final int EXPECTED_HOLDOUT_ROWS = 1_200;
    public static final int EXPECTED_CORE_ROWS = 2_400;
    public static final int EXPECTED_MARGINAL_PAIRS = 1_600;
    public static final int EXPECTED_REPLAY_CHECKS = 300;
    public static final int EXPECTED_INSTRUMENTATION_CHECKS = 300;
    public static final int MAX_OFFICIAL_SIMULATIONS = 3_000;
    public static final List<SimulationRuntimeProfileId> PROFILES = List.of(
            SimulationRuntimeProfileId.BASELINE_V1,
            SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
            SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
    public static final AcceptanceGates GATES = new AcceptanceGates(
            2.0, 2.0, 15.0, 20.0, 15.0, 7.5, 30.0, 120.0, 0);
    public static final String SEED_BINDING_HASH = sha256(
            "seedBindingSchema=MATCH_ENGINE_V9_AUTO_DRAFT_FRESH_SEED_BINDING_V2\n"
                    + "scheduleVersion=" + SCHEDULE_VERSION + "\n"
                    + "calibrationSeedsPerFixture=4\n"
                    + "holdoutSeedsPerFixture=4\n"
                    + "draftReusePolicy=" + DRAFT_REUSE_POLICY + "\n"
                    + "profiles=BASELINE_V1,MATCHUP_ONLY_CANDIDATE_V1,"
                    + "FULL_SYSTEM_CANDIDATE_V1\n");

    private static final Schedule FROZEN = build();

    private MatchEngineV9FreshRequalificationContract() { }

    public static Schedule schedule() {
        return FROZEN;
    }

    public static Schedule requireFrozen(Schedule candidate) {
        if (!FROZEN.equals(candidate)
                || !candidate.scheduleHash().equals(sha256(canonicalSchedule(candidate.fixtures())))) {
            throw new IllegalArgumentException("Fresh requalification schedule is not frozen");
        }
        return candidate;
    }

    public static SeedOverlapAudit requireNoSeedOverlap(
            Schedule schedule, Set<Long> historicalConsumedSeeds
    ) {
        requireFrozen(schedule);
        Set<Long> historical = Set.copyOf(historicalConsumedSeeds);
        Set<Long> official = new HashSet<>();
        Set<Long> calibration = new HashSet<>();
        Set<Long> holdout = new HashSet<>();
        Set<Long> dryRun = new HashSet<>();
        int historicalOverlap = 0;
        int officialCollision = 0;
        int calibrationHoldoutOverlap = 0;
        int dryRunOfficialOverlap = 0;
        for (Fixture fixture : schedule.fixtures()) {
            for (long seed : fixture.calibrationSeeds()) {
                if (!official.add(seed)) officialCollision++;
                calibration.add(seed);
                if (historical.contains(seed)) historicalOverlap++;
            }
            for (long seed : fixture.holdoutSeeds()) {
                if (!official.add(seed)) officialCollision++;
                holdout.add(seed);
                if (historical.contains(seed)) historicalOverlap++;
            }
            long dry = dryRunSeed(fixture);
            if (!dryRun.add(dry)) officialCollision++;
            if (historical.contains(dry)) historicalOverlap++;
        }
        for (long seed : calibration) if (holdout.contains(seed)) calibrationHoldoutOverlap++;
        for (long seed : dryRun) if (official.contains(seed)) dryRunOfficialOverlap++;
        SeedOverlapAudit audit = new SeedOverlapAudit(
                SEED_NAMESPACE, SEED_BINDING_HASH, historical.size(), official.size(),
                dryRun.size(), historicalOverlap, officialCollision,
                calibrationHoldoutOverlap, dryRunOfficialOverlap);
        if (!audit.clean() || official.size() != EXPECTED_DRAFTS
                || dryRun.size() != EXPECTED_FIXTURES) {
            throw new IllegalStateException("Fresh seed namespace overlaps consumed evidence");
        }
        return audit;
    }

    public static Phase13GB1AuditSchedule.Fixture sourceFixture(Fixture fixture) {
        return Phase13GB1AuditSchedule.create().allFixtures().stream()
                .filter(value -> value.fixtureId().equals(fixture.fixtureId()))
                .findFirst().orElseThrow();
    }

    public static long dryRunSeed(Fixture fixture) {
        return seeds(sourceFixture(fixture), SampleLane.DRY_RUN, 1).getFirst();
    }

    /** Stable series-game-one history preparation seed; never an official audit sample. */
    public static long historyPreparationSeed(Fixture fixture) {
        String canonical = "seedSchema=MATCH_ENGINE_V9_AUTO_DRAFT_HISTORY_PREPARATION_SEED_V1\n"
                + "fixtureId=" + fixture.fixtureId() + "\n"
                + "blueTeamCode=" + fixture.blueTeamCode() + "\n"
                + "redTeamCode=" + fixture.redTeamCode() + "\n";
        return ByteBuffer.wrap(digest(canonical.getBytes(StandardCharsets.UTF_8))).getLong();
    }

    private static Schedule build() {
        ArrayList<Fixture> fixtures = new ArrayList<>();
        for (var source : Phase13GB1AuditSchedule.create().allFixtures()) {
            fixtures.add(new Fixture(source.fixtureId(), source.fixtureLane(), source.pairId(),
                    source.blueTeamCode(), source.redTeamCode(), source.seriesGameNumber(),
                    seeds(source, SampleLane.CALIBRATION, CALIBRATION_SEEDS_PER_FIXTURE),
                    seeds(source, SampleLane.HOLDOUT, HOLDOUT_SEEDS_PER_FIXTURE)));
        }
        long gameOne = fixtures.stream().filter(value -> value.seriesGameNumber() == 1).count();
        long gameTwo = fixtures.stream().filter(value -> value.seriesGameNumber() == 2).count();
        if (fixtures.size() != EXPECTED_FIXTURES || gameOne != EXPECTED_GAME_ONE_FIXTURES
                || gameTwo != EXPECTED_GAME_TWO_FIXTURES) {
            throw new IllegalStateException("Fresh requalification fixture coverage drift");
        }
        return new Schedule(SCHEDULE_SCHEMA, SCHEDULE_VERSION, SEED_NAMESPACE,
                SEED_BINDING_HASH, DRAFT_REUSE_POLICY, CALIBRATION_SEEDS_PER_FIXTURE,
                HOLDOUT_SEEDS_PER_FIXTURE, List.copyOf(fixtures),
                sha256(canonicalSchedule(fixtures)));
    }

    private static List<Long> seeds(
            Phase13GB1AuditSchedule.Fixture fixture, SampleLane lane, int count
    ) {
        ArrayList<Long> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String canonical = "seedSchema=MATCH_ENGINE_V9_AUTO_DRAFT_FRESH_SEED_V2\n"
                    + "seedNamespace=" + SEED_NAMESPACE + '\n'
                    + "seedBindingHash=" + SEED_BINDING_HASH + '\n'
                    + "fixtureId=" + fixture.fixtureId() + '\n'
                    + "fixtureLane=" + fixture.fixtureLane() + '\n'
                    + "blueTeamCode=" + fixture.blueTeamCode() + '\n'
                    + "redTeamCode=" + fixture.redTeamCode() + '\n'
                    + "seriesGameNumber=" + fixture.seriesGameNumber() + '\n'
                    + "sampleLane=" + lane + '\n'
                    + "replicate=" + index + '\n';
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
                .append("draftReusePolicy=").append(DRAFT_REUSE_POLICY).append('\n')
                .append("calibrationSeedsPerFixture=4\n")
                .append("holdoutSeedsPerFixture=4\n");
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
            StringBuilder target, String fixtureId, SampleLane lane, List<Long> seeds
    ) {
        for (int index = 0; index < seeds.size(); index++) {
            target.append("seed=").append(fixtureId).append('|').append(lane).append('|')
                    .append(index).append('|').append(seeds.get(index)).append('\n');
        }
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
            if (seriesGameNumber < 1 || seriesGameNumber > 2
                    || calibrationSeeds.size() != CALIBRATION_SEEDS_PER_FIXTURE
                    || holdoutSeeds.size() != HOLDOUT_SEEDS_PER_FIXTURE
                    || calibrationSeeds.stream().anyMatch(holdoutSeeds::contains)) {
                throw new IllegalArgumentException("Invalid fresh fixture allocation");
            }
        }
    }

    public record Schedule(
            String schemaVersion,
            String scheduleVersion,
            String seedNamespace,
            String seedBindingHash,
            String draftReusePolicy,
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
            double directionalWinnerFlipImbalancePercentagePoints,
            double pairedWinnerChangedRatePercent,
            double objectiveChangedRatePercent,
            double actualStructureProgressionChangedRatePercent,
            double nexusOrEndingProgressionChangedRatePercent,
            double absoluteMeanDurationDeltaSeconds,
            double absoluteAggregateP95DurationDeltaSeconds,
            int timeoutIncrease
    ) { }

    public record SeedOverlapAudit(
            String namespace,
            String seedBindingHash,
            int historicalUniqueSeedCount,
            int officialUniqueSeedCount,
            int dryRunUniqueSeedCount,
            int historicalOverlapCount,
            int officialCollisionCount,
            int calibrationHoldoutOverlapCount,
            int dryRunOfficialOverlapCount
    ) {
        public boolean clean() {
            return historicalOverlapCount == 0 && officialCollisionCount == 0
                    && calibrationHoldoutOverlapCount == 0 && dryRunOfficialOverlapCount == 0;
        }
    }
}
