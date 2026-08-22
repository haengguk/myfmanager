package com.lolfm.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Frozen fixture and seed contract for Final 13G-B; it does not execute calibration. */
public final class Phase13GB1AuditSchedule {
    public static final String SCHEMA = "PHASE_13G_B_AUDIT_SCHEDULE_V1";
    public static final String SCHEDULE_VERSION = "PHASE_13G_B_REAL_LCK_PAIRED_SCHEDULE_V1";
    public static final String HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_FIELD_LINES_TRAILING_NEWLINE_V1";
    public static final String EXPECTED_SCHEDULE_HASH =
            "3bb5e81241a3be2a1509e67528e577ae8f48fca94dec5fc15f93ec8ac78052ef";
    public static final int CALIBRATION_SEEDS_PER_FIXTURE = 24;
    public static final int HOLDOUT_SEEDS_PER_FIXTURE = 8;
    public static final List<String> TEAM_CODES = List.of(
            "BFX", "BRO", "DK", "DNS", "GEN", "HLE", "KRX", "KT", "NS", "T1");
    private static final Schedule FROZEN_SCHEDULE = buildSchedule();

    private Phase13GB1AuditSchedule() {
    }

    public static Schedule create() {
        return FROZEN_SCHEDULE;
    }

    /** Rejects self-consistent but non-canonical schedules at every audit boundary. */
    public static Schedule requireFrozen(Schedule candidate) {
        Objects.requireNonNull(candidate, "schedule");
        String recalculated = sha256(canonicalSchedule(
                candidate.schemaVersion(),
                candidate.scheduleVersion(),
                candidate.teamCodes(),
                candidate.calibrationSeedsPerFixture(),
                candidate.holdoutSeedsPerFixture(),
                candidate.primaryFixtures(),
                candidate.secondaryHardFearlessFixtures()));
        if (!recalculated.equals(candidate.scheduleHash())) {
            throw new IllegalArgumentException(
                    "13G-B schedule content does not match its declared hash");
        }
        if (!FROZEN_SCHEDULE.equals(candidate)) {
            throw new IllegalArgumentException(
                    "Schedule differs from the frozen 13G-B contract");
        }
        return candidate;
    }

    private static Schedule buildSchedule() {
        List<Fixture> primary = primaryFixtures();
        List<Fixture> fearless = hardFearlessGameTwoFixtures();
        String hash = sha256(canonicalSchedule(
                SCHEMA,
                SCHEDULE_VERSION,
                TEAM_CODES,
                CALIBRATION_SEEDS_PER_FIXTURE,
                HOLDOUT_SEEDS_PER_FIXTURE,
                primary,
                fearless));
        if (!EXPECTED_SCHEDULE_HASH.equals(hash)) {
            throw new IllegalStateException(
                    "13G-B schedule changed without a versioned contract: expected="
                            + EXPECTED_SCHEDULE_HASH + " actual=" + hash);
        }
        return new Schedule(
                SCHEMA,
                SCHEDULE_VERSION,
                HASH_ALGORITHM,
                TEAM_CODES,
                CALIBRATION_SEEDS_PER_FIXTURE,
                HOLDOUT_SEEDS_PER_FIXTURE,
                primary,
                fearless,
                hash);
    }

    public static long dryRunSeed(Fixture fixture) {
        return derivedSeed(Objects.requireNonNull(fixture, "fixture"), SampleLane.DRY_RUN, 0);
    }

    private static List<Fixture> primaryFixtures() {
        ArrayList<Fixture> result = new ArrayList<>();
        for (int first = 0; first < TEAM_CODES.size(); first++) {
            for (int second = first + 1; second < TEAM_CODES.size(); second++) {
                String firstCode = TEAM_CODES.get(first);
                String secondCode = TEAM_CODES.get(second);
                String pairId = "G1_PAIR_" + firstCode + "_" + secondCode;
                result.add(fixture(
                        "G1_" + firstCode + "_BLUE__" + secondCode + "_RED",
                        FixtureLane.PRIMARY_LEAGUE_G1,
                        pairId,
                        firstCode,
                        secondCode,
                        1));
                result.add(fixture(
                        "G1_" + secondCode + "_BLUE__" + firstCode + "_RED",
                        FixtureLane.PRIMARY_LEAGUE_G1,
                        pairId,
                        secondCode,
                        firstCode,
                        1));
            }
        }
        return List.copyOf(result);
    }

    private static List<Fixture> hardFearlessGameTwoFixtures() {
        List<String> permutation = TEAM_CODES.stream()
                .sorted(Comparator.comparing(Phase13GB1AuditSchedule::fearlessPairingKey)
                        .thenComparing(value -> value))
                .toList();
        ArrayList<Fixture> result = new ArrayList<>();
        for (int index = 0; index < permutation.size(); index += 2) {
            String firstCode = permutation.get(index);
            String secondCode = permutation.get(index + 1);
            String low = firstCode.compareTo(secondCode) < 0 ? firstCode : secondCode;
            String high = firstCode.compareTo(secondCode) < 0 ? secondCode : firstCode;
            String pairId = "G2_PAIR_" + low + "_" + high;
            result.add(fixture(
                    "G2_" + firstCode + "_BLUE__" + secondCode + "_RED",
                    FixtureLane.SECONDARY_HARD_FEARLESS_G2,
                    pairId,
                    firstCode,
                    secondCode,
                    2));
            result.add(fixture(
                    "G2_" + secondCode + "_BLUE__" + firstCode + "_RED",
                    FixtureLane.SECONDARY_HARD_FEARLESS_G2,
                    pairId,
                    secondCode,
                    firstCode,
                    2));
        }
        return List.copyOf(result);
    }

    private static Fixture fixture(
            String fixtureId,
            FixtureLane lane,
            String pairId,
            String blueTeamCode,
            String redTeamCode,
            int seriesGameNumber
    ) {
        Fixture identityOnly = new Fixture(
                fixtureId,
                lane,
                pairId,
                blueTeamCode,
                redTeamCode,
                seriesGameNumber,
                List.of(),
                List.of());
        List<Long> calibration = seeds(identityOnly, SampleLane.CALIBRATION,
                CALIBRATION_SEEDS_PER_FIXTURE);
        List<Long> holdout = seeds(identityOnly, SampleLane.HOLDOUT,
                HOLDOUT_SEEDS_PER_FIXTURE);
        return new Fixture(
                fixtureId,
                lane,
                pairId,
                blueTeamCode,
                redTeamCode,
                seriesGameNumber,
                calibration,
                holdout);
    }

    private static List<Long> seeds(Fixture fixture, SampleLane lane, int count) {
        ArrayList<Long> result = new ArrayList<>(count);
        Set<Long> unique = new HashSet<>();
        for (int replicate = 0; replicate < count; replicate++) {
            long seed = derivedSeed(fixture, lane, replicate);
            if (!unique.add(seed)) {
                throw new IllegalStateException("Derived seed collision for " + fixture.fixtureId());
            }
            result.add(seed);
        }
        return List.copyOf(result);
    }

    private static long derivedSeed(Fixture fixture, SampleLane lane, int replicate) {
        String canonical = "seedSchema=PHASE_13G_B_DERIVED_MATCH_SEED_V1\n"
                + "scheduleVersion=" + SCHEDULE_VERSION + '\n'
                + "fixtureId=" + fixture.fixtureId() + '\n'
                + "fixtureLane=" + fixture.fixtureLane().name() + '\n'
                + "blueTeamCode=" + fixture.blueTeamCode() + '\n'
                + "redTeamCode=" + fixture.redTeamCode() + '\n'
                + "seriesGameNumber=" + fixture.seriesGameNumber() + '\n'
                + "sampleLane=" + lane.name() + '\n'
                + "replicate=" + replicate + '\n';
        return ByteBuffer.wrap(digest(canonical.getBytes(StandardCharsets.UTF_8))).getLong();
    }

    private static String canonicalSchedule(
            String schema,
            String version,
            List<String> teamCodes,
            int calibrationSeedsPerFixture,
            int holdoutSeedsPerFixture,
            List<Fixture> primary,
            List<Fixture> fearless
    ) {
        StringBuilder canonical = new StringBuilder("scheduleSchema=")
                .append(schema).append('\n')
                .append("scheduleVersion=").append(version).append('\n')
                .append("calibrationSeedsPerFixture=")
                .append(calibrationSeedsPerFixture).append('\n')
                .append("holdoutSeedsPerFixture=")
                .append(holdoutSeedsPerFixture).append('\n');
        teamCodes.forEach(team -> canonical.append("team=").append(team).append('\n'));
        for (Fixture fixture : concat(primary, fearless)) {
            canonical.append("fixture=").append(fixture.fixtureId()).append('|')
                    .append(fixture.fixtureLane()).append('|')
                    .append(fixture.pairId()).append('|')
                    .append(fixture.blueTeamCode()).append('|')
                    .append(fixture.redTeamCode()).append('|')
                    .append(fixture.seriesGameNumber()).append('\n');
            appendSeeds(canonical, fixture, SampleLane.CALIBRATION, fixture.calibrationSeeds());
            appendSeeds(canonical, fixture, SampleLane.HOLDOUT, fixture.holdoutSeeds());
        }
        return canonical.toString();
    }

    private static List<Fixture> concat(List<Fixture> primary, List<Fixture> fearless) {
        ArrayList<Fixture> result = new ArrayList<>(primary.size() + fearless.size());
        result.addAll(primary);
        result.addAll(fearless);
        return List.copyOf(result);
    }

    private static void appendSeeds(
            StringBuilder canonical,
            Fixture fixture,
            SampleLane lane,
            List<Long> seeds
    ) {
        for (int index = 0; index < seeds.size(); index++) {
            canonical.append("seed=").append(fixture.fixtureId()).append('|')
                    .append(lane.name()).append('|').append(index).append('|')
                    .append(seeds.get(index)).append('\n');
        }
    }

    private static String fearlessPairingKey(String teamCode) {
        String canonical = "pairingSchema=PHASE_13G_B_HARD_FEARLESS_PAIRING_V1\n"
                + "scheduleVersion=" + SCHEDULE_VERSION + '\n'
                + "teamCode=" + teamCode + '\n';
        return HexFormat.of().formatHex(digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sha256(String canonical) {
        if (!canonical.endsWith("\n")) {
            throw new IllegalArgumentException("Canonical schedule requires trailing newline");
        }
        return HexFormat.of().formatHex(digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public enum FixtureLane {
        PRIMARY_LEAGUE_G1,
        SECONDARY_HARD_FEARLESS_G2
    }

    public enum SampleLane {
        DRY_RUN,
        CALIBRATION,
        HOLDOUT
    }

    public record Fixture(
            String fixtureId,
            FixtureLane fixtureLane,
            String pairId,
            String blueTeamCode,
            String redTeamCode,
            int seriesGameNumber,
            List<Long> calibrationSeeds,
            List<Long> holdoutSeeds
    ) {
        public Fixture {
            fixtureId = required(fixtureId, "fixtureId");
            Objects.requireNonNull(fixtureLane, "fixtureLane");
            pairId = required(pairId, "pairId");
            blueTeamCode = required(blueTeamCode, "blueTeamCode");
            redTeamCode = required(redTeamCode, "redTeamCode");
            if (blueTeamCode.equals(redTeamCode)) {
                throw new IllegalArgumentException("A fixture requires two distinct teams");
            }
            if (seriesGameNumber < 1 || seriesGameNumber > 2) {
                throw new IllegalArgumentException("B1 supports G1 and G2 fixtures only");
            }
            calibrationSeeds = List.copyOf(calibrationSeeds);
            holdoutSeeds = List.copyOf(holdoutSeeds);
            if (calibrationSeeds.stream().anyMatch(holdoutSeeds::contains)) {
                throw new IllegalArgumentException(
                        "Calibration and holdout seeds must be disjoint");
            }
        }

        private static String required(String value, String field) {
            String normalized = Objects.requireNonNull(value, field).trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
            return normalized;
        }
    }

    public record Schedule(
            String schemaVersion,
            String scheduleVersion,
            String scheduleHashAlgorithm,
            List<String> teamCodes,
            int calibrationSeedsPerFixture,
            int holdoutSeedsPerFixture,
            List<Fixture> primaryFixtures,
            List<Fixture> secondaryHardFearlessFixtures,
            String scheduleHash
    ) {
        public Schedule {
            schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
            scheduleVersion = Objects.requireNonNull(scheduleVersion, "scheduleVersion");
            scheduleHashAlgorithm = Objects.requireNonNull(
                    scheduleHashAlgorithm, "scheduleHashAlgorithm");
            teamCodes = List.copyOf(teamCodes);
            primaryFixtures = List.copyOf(primaryFixtures);
            secondaryHardFearlessFixtures = List.copyOf(secondaryHardFearlessFixtures);
            scheduleHash = Objects.requireNonNull(scheduleHash, "scheduleHash");
        }

        public List<Fixture> allFixtures() {
            return concat(primaryFixtures, secondaryHardFearlessFixtures);
        }
    }
}
