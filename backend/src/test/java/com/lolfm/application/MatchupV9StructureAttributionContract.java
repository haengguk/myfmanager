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

/** Frozen attribution-only schedule. These seeds are calibration evidence, never a holdout. */
public final class MatchupV9StructureAttributionContract {
    public static final String CONTRACT_SCHEMA =
            "MATCHUP_V9_STRUCTURE_EFFECT_ATTRIBUTION_CONTRACT_V1";
    public static final String SCHEDULE_SCHEMA =
            "MATCHUP_V9_STRUCTURE_EFFECT_ATTRIBUTION_SCHEDULE_V1";
    public static final String SCHEDULE_VERSION =
            "MATCHUP_V9_STRUCTURE_ATTRIBUTION_REAL_LCK_100_FIXTURE_4_SEED_V1";
    public static final String SEED_NAMESPACE =
            "MATCHUP_V9_STRUCTURE_ATTRIBUTION_DIAGNOSTIC_SEEDS_V1";
    public static final String CONSUMPTION_STATUS =
            "CONSUMED_AS_DIAGNOSTIC_NOT_HOLDOUT";
    public static final int SEEDS_PER_FIXTURE = 4;
    public static final int EXPECTED_FIXTURES = 100;
    public static final int EXPECTED_PROFILE_ROWS = 800;
    public static final int EXPECTED_PAIRS = 400;
    public static final List<SimulationRuntimeProfileId> PROFILES = List.of(
            SimulationRuntimeProfileId.BASELINE_V1,
            SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
    public static final String SEED_BINDING_HASH = sha256(
            "schema=MATCHUP_V9_STRUCTURE_ATTRIBUTION_SEED_BINDING_V1\n"
                    + "scheduleVersion=" + SCHEDULE_VERSION + "\n"
                    + "seedsPerFixture=4\n"
                    + "profiles=BASELINE_V1,MATCHUP_ONLY_CANDIDATE_V1\n"
                    + "consumptionStatus=" + CONSUMPTION_STATUS + "\n");

    private static final Schedule FROZEN = build();

    private MatchupV9StructureAttributionContract() {
    }

    public static Schedule schedule() {
        return FROZEN;
    }

    public static Schedule requireFrozen(Schedule candidate) {
        if (!FROZEN.equals(candidate)) {
            throw new IllegalArgumentException("Attribution schedule is not frozen");
        }
        if (!sha256(canonicalSchedule(candidate.fixtures())).equals(candidate.scheduleHash())) {
            throw new IllegalArgumentException("Attribution schedule content/hash mismatch");
        }
        return candidate;
    }

    public static SeedOverlapAudit requireNoSeedOverlap(Schedule schedule) {
        Set<Long> phase13 = new HashSet<>();
        for (var fixture : Phase13GB1AuditSchedule.create().allFixtures()) {
            phase13.addAll(fixture.calibrationSeeds());
            phase13.addAll(fixture.holdoutSeeds());
        }
        Set<Long> v9Calibration = new HashSet<>();
        Set<Long> v9Holdout = new HashSet<>();
        for (var fixture : MatchEngineV9RequalificationContract.schedule().fixtures()) {
            v9Calibration.addAll(fixture.calibrationSeeds());
            v9Holdout.addAll(fixture.holdoutSeeds());
        }
        Set<Long> fresh = new HashSet<>();
        int duplicate = 0;
        int phase13Overlap = 0;
        int v9CalibrationOverlap = 0;
        int v9HoldoutOverlap = 0;
        for (Fixture fixture : schedule.fixtures()) {
            for (long seed : fixture.seeds()) {
                if (!fresh.add(seed)) duplicate++;
                if (phase13.contains(seed)) phase13Overlap++;
                if (v9Calibration.contains(seed)) v9CalibrationOverlap++;
                if (v9Holdout.contains(seed)) v9HoldoutOverlap++;
            }
        }
        SeedOverlapAudit result = new SeedOverlapAudit(
                SEED_NAMESPACE, SEED_BINDING_HASH, fresh.size(), duplicate,
                phase13.size(), phase13Overlap, v9Calibration.size(),
                v9CalibrationOverlap, v9Holdout.size(), v9HoldoutOverlap,
                0, 0, CONSUMPTION_STATUS);
        if (!result.clean() || fresh.size() != EXPECTED_PAIRS) {
            throw new IllegalStateException("Attribution seeds overlap consumed evidence");
        }
        return result;
    }

    public static Phase13GB1AuditSchedule.Fixture sourceFixture(Fixture fixture) {
        return Phase13GB1AuditSchedule.create().allFixtures().stream()
                .filter(value -> value.fixtureId().equals(fixture.fixtureId()))
                .findFirst().orElseThrow();
    }

    private static Schedule build() {
        ArrayList<Fixture> fixtures = new ArrayList<>();
        for (var source : Phase13GB1AuditSchedule.create().allFixtures()) {
            fixtures.add(new Fixture(
                    source.fixtureId(), source.fixtureLane(), source.pairId(),
                    source.blueTeamCode(), source.redTeamCode(),
                    source.seriesGameNumber(), seeds(source)));
        }
        if (fixtures.size() != EXPECTED_FIXTURES) {
            throw new IllegalStateException("Attribution fixture count drift");
        }
        Schedule result = new Schedule(
                SCHEDULE_SCHEMA, SCHEDULE_VERSION, SEED_NAMESPACE, SEED_BINDING_HASH,
                CONSUMPTION_STATUS, SEEDS_PER_FIXTURE, List.copyOf(fixtures),
                sha256(canonicalSchedule(fixtures)));
        requireNoSeedOverlap(result);
        return result;
    }

    private static List<Long> seeds(Phase13GB1AuditSchedule.Fixture fixture) {
        ArrayList<Long> result = new ArrayList<>(SEEDS_PER_FIXTURE);
        for (int replicate = 0; replicate < SEEDS_PER_FIXTURE; replicate++) {
            String canonical = "seedSchema=MATCHUP_V9_STRUCTURE_ATTRIBUTION_SEED_V1\n"
                    + "seedNamespace=" + SEED_NAMESPACE + '\n'
                    + "seedBindingHash=" + SEED_BINDING_HASH + '\n'
                    + "fixtureId=" + fixture.fixtureId() + '\n'
                    + "fixtureLane=" + fixture.fixtureLane() + '\n'
                    + "pairId=" + fixture.pairId() + '\n'
                    + "blueTeamCode=" + fixture.blueTeamCode() + '\n'
                    + "redTeamCode=" + fixture.redTeamCode() + '\n'
                    + "seriesGameNumber=" + fixture.seriesGameNumber() + '\n'
                    + "sampleLane=ATTRIBUTION_DIAGNOSTIC\n"
                    + "replicate=" + replicate + '\n';
            result.add(ByteBuffer.wrap(digest(canonical.getBytes(StandardCharsets.UTF_8))).getLong());
        }
        return List.copyOf(result);
    }

    private static String canonicalSchedule(List<Fixture> fixtures) {
        StringBuilder result = new StringBuilder()
                .append("schemaVersion=").append(SCHEDULE_SCHEMA).append('\n')
                .append("scheduleVersion=").append(SCHEDULE_VERSION).append('\n')
                .append("seedNamespace=").append(SEED_NAMESPACE).append('\n')
                .append("seedBindingHash=").append(SEED_BINDING_HASH).append('\n')
                .append("consumptionStatus=").append(CONSUMPTION_STATUS).append('\n')
                .append("seedsPerFixture=").append(SEEDS_PER_FIXTURE).append('\n');
        for (Fixture fixture : fixtures) {
            result.append("fixture=").append(fixture.fixtureId()).append('|')
                    .append(fixture.fixtureLane()).append('|').append(fixture.pairId()).append('|')
                    .append(fixture.blueTeamCode()).append('|').append(fixture.redTeamCode())
                    .append('|').append(fixture.seriesGameNumber()).append('\n');
            for (int index = 0; index < fixture.seeds().size(); index++) {
                result.append("seed=").append(fixture.fixtureId()).append('|')
                        .append(index).append('|').append(fixture.seeds().get(index)).append('\n');
            }
        }
        return result.toString();
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

    public record Fixture(
            String fixtureId,
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String pairId,
            String blueTeamCode,
            String redTeamCode,
            int seriesGameNumber,
            List<Long> seeds
    ) {
        public Fixture {
            Objects.requireNonNull(fixtureId);
            Objects.requireNonNull(fixtureLane);
            Objects.requireNonNull(pairId);
            Objects.requireNonNull(blueTeamCode);
            Objects.requireNonNull(redTeamCode);
            seeds = List.copyOf(seeds);
            if (seeds.size() != SEEDS_PER_FIXTURE || new HashSet<>(seeds).size() != seeds.size()) {
                throw new IllegalArgumentException("Invalid attribution seed allocation");
            }
        }
    }

    public record Schedule(
            String schemaVersion,
            String scheduleVersion,
            String seedNamespace,
            String seedBindingHash,
            String consumptionStatus,
            int seedsPerFixture,
            List<Fixture> fixtures,
            String scheduleHash
    ) {
        public Schedule {
            fixtures = List.copyOf(fixtures);
        }
    }

    public record SeedOverlapAudit(
            String seedNamespace,
            String seedBindingHash,
            int attributionSeedCount,
            int attributionDuplicateCount,
            int phase13GBSeedCount,
            int phase13GBOverlapCount,
            int v9RequalificationCalibrationSeedCount,
            int v9RequalificationCalibrationOverlapCount,
            int v9RequalificationConsumedHoldoutSeedCount,
            int v9RequalificationConsumedHoldoutOverlapCount,
            int reservedFutureSeedCount,
            int reservedFutureOverlapCount,
            String consumptionStatus
    ) {
        public boolean clean() {
            return attributionDuplicateCount == 0
                    && phase13GBOverlapCount == 0
                    && v9RequalificationCalibrationOverlapCount == 0
                    && v9RequalificationConsumedHoldoutOverlapCount == 0
                    && reservedFutureOverlapCount == 0;
        }
    }
}
