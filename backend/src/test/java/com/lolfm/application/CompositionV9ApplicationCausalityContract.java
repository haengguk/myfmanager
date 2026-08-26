package com.lolfm.application;

import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Frozen calibration-only schedule for Composition V9 application/causality diagnosis. */
public final class CompositionV9ApplicationCausalityContract {
    public static final String CONTRACT_SCHEMA = "COMPOSITION_V9_APPLICATION_CAUSALITY_CONTRACT_V3";
    public static final String SCHEDULE_SCHEMA = "COMPOSITION_V9_APPLICATION_CAUSALITY_SCHEDULE_V5";
    public static final String SCHEDULE_VERSION = "COMPOSITION_V9_REAL_LCK_100_FIXTURE_4_FRESH_SEED_V5";
    public static final String SEED_NAMESPACE = "COMPOSITION_APPLICATION_CAUSALITY_DIAGNOSTIC_SEEDS_V5";
    public static final String CONSUMPTION_STATUS = "CONSUMED_AS_DIAGNOSTIC_NOT_HOLDOUT";
    public static final int SEEDS_PER_FIXTURE = 4;
    public static final int EXPECTED_FIXTURES = 100;
    public static final int EXPECTED_PROFILE_ROWS = 800;
    public static final int EXPECTED_PAIRS = 400;
    public static final List<SimulationRuntimeProfileId> PROFILES = List.of(
            SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
            SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
    public static final String SEED_BINDING_HASH = sha256(
            "schema=COMPOSITION_V9_APPLICATION_CAUSALITY_SEED_BINDING_V5\n"
                    + "scheduleVersion=" + SCHEDULE_VERSION + "\n"
                    + "seedsPerFixture=4\n"
                    + "profiles=MATCHUP_ONLY_CANDIDATE_V1,FULL_SYSTEM_CANDIDATE_V1\n"
                    + "consumptionStatus=" + CONSUMPTION_STATUS + "\n");

    private static final Schedule FROZEN = build();

    private CompositionV9ApplicationCausalityContract() { }

    public static Schedule schedule() { return FROZEN; }

    public static Schedule requireFrozen(Schedule candidate) {
        if (!FROZEN.equals(candidate)
                || !sha256(canonicalSchedule(candidate.fixtures())).equals(candidate.scheduleHash())) {
            throw new IllegalArgumentException("Composition causality schedule is not frozen");
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
        Set<Long> matchupAttribution = new HashSet<>();
        MatchupV9StructureAttributionContract.schedule().fixtures()
                .forEach(value -> matchupAttribution.addAll(value.seeds()));
        Set<Long> failedV1 = failedWorkerIsolationV1Seeds();
        Set<Long> blockedV2 = blockedProvenanceGapV2Seeds();
        Set<Long> blockedV3 = blockedProvenanceGapV3Seeds();
        Set<Long> blockedV4 = blockedProvenanceGapV4Seeds();
        Set<Long> fresh = new HashSet<>();
        int duplicate = 0, phase13Overlap = 0, v9CalibrationOverlap = 0;
        int v9HoldoutOverlap = 0, matchupOverlap = 0, failedV1Overlap = 0;
        int blockedV2Overlap = 0, blockedV3Overlap = 0, blockedV4Overlap = 0;
        for (Fixture fixture : schedule.fixtures()) {
            for (long seed : fixture.seeds()) {
                if (!fresh.add(seed)) duplicate++;
                if (phase13.contains(seed)) phase13Overlap++;
                if (v9Calibration.contains(seed)) v9CalibrationOverlap++;
                if (v9Holdout.contains(seed)) v9HoldoutOverlap++;
                if (matchupAttribution.contains(seed)) matchupOverlap++;
                if (failedV1.contains(seed)) failedV1Overlap++;
                if (blockedV2.contains(seed)) blockedV2Overlap++;
                if (blockedV3.contains(seed)) blockedV3Overlap++;
                if (blockedV4.contains(seed)) blockedV4Overlap++;
            }
        }
        SeedOverlapAudit result = new SeedOverlapAudit(SEED_NAMESPACE, SEED_BINDING_HASH,
                fresh.size(), duplicate, phase13.size(), phase13Overlap,
                v9Calibration.size(), v9CalibrationOverlap, v9Holdout.size(), v9HoldoutOverlap,
                matchupAttribution.size(), matchupOverlap, failedV1.size(), failedV1Overlap,
                blockedV2.size(), blockedV2Overlap, blockedV3.size(), blockedV3Overlap,
                blockedV4.size(), blockedV4Overlap,
                0, 0, CONSUMPTION_STATUS);
        if (!result.clean() || fresh.size() != EXPECTED_PAIRS) {
            throw new IllegalStateException("Composition causality seeds overlap consumed evidence");
        }
        return result;
    }

    public static Phase13GB1AuditSchedule.Fixture sourceFixture(Fixture fixture) {
        return Phase13GB1AuditSchedule.create().allFixtures().stream()
                .filter(value -> value.fixtureId().equals(fixture.fixtureId())).findFirst().orElseThrow();
    }

    private static Schedule build() {
        ArrayList<Fixture> fixtures = new ArrayList<>();
        for (var source : Phase13GB1AuditSchedule.create().allFixtures()) {
            fixtures.add(new Fixture(source.fixtureId(), source.fixtureLane(), source.pairId(),
                    source.blueTeamCode(), source.redTeamCode(), source.seriesGameNumber(), seeds(source)));
        }
        if (fixtures.size() != EXPECTED_FIXTURES) throw new IllegalStateException("Fixture count drift");
        Schedule result = new Schedule(SCHEDULE_SCHEMA, SCHEDULE_VERSION, SEED_NAMESPACE,
                SEED_BINDING_HASH, CONSUMPTION_STATUS, SEEDS_PER_FIXTURE, List.copyOf(fixtures),
                sha256(canonicalSchedule(fixtures)));
        requireNoSeedOverlap(result);
        return result;
    }

    private static List<Long> seeds(Phase13GB1AuditSchedule.Fixture fixture) {
        ArrayList<Long> values = new ArrayList<>();
        for (int replicate = 0; replicate < SEEDS_PER_FIXTURE; replicate++) {
            String canonical = "seedSchema=COMPOSITION_V9_APPLICATION_CAUSALITY_SEED_V5\n"
                    + "seedNamespace=" + SEED_NAMESPACE + '\n'
                    + "seedBindingHash=" + SEED_BINDING_HASH + '\n'
                    + "fixtureId=" + fixture.fixtureId() + '\n'
                    + "fixtureLane=" + fixture.fixtureLane() + '\n'
                    + "pairId=" + fixture.pairId() + '\n'
                    + "blueTeamCode=" + fixture.blueTeamCode() + '\n'
                    + "redTeamCode=" + fixture.redTeamCode() + '\n'
                    + "seriesGameNumber=" + fixture.seriesGameNumber() + '\n'
                    + "sampleLane=COMPOSITION_CAUSALITY_DIAGNOSTIC\n"
                    + "replicate=" + replicate + '\n';
            values.add(ByteBuffer.wrap(digest(canonical.getBytes(StandardCharsets.UTF_8))).getLong());
        }
        return List.copyOf(values);
    }

    private static Set<Long> failedWorkerIsolationV1Seeds() {
        return legacySeeds("V1", "COMPOSITION_APPLICATION_CAUSALITY_DIAGNOSTIC_SEEDS_V1",
                "a2ceb25c447b5a86dde03a94ab280578b8b7d6373347b707716d37ebd1f6da32");
    }

    private static Set<Long> blockedProvenanceGapV2Seeds() {
        return legacySeeds("V2", "COMPOSITION_APPLICATION_CAUSALITY_DIAGNOSTIC_SEEDS_V2",
                "ddb5f2de90a4b3d8a97ca1dbf2a631abbdf354fc88fd418e84fc1791b17da06c");
    }

    private static Set<Long> blockedProvenanceGapV3Seeds() {
        return legacySeeds("V3", "COMPOSITION_APPLICATION_CAUSALITY_DIAGNOSTIC_SEEDS_V3",
                "bf08e7a83ec429f6413f3a9049c04d55a639bba35f82b826b60143eab412c620");
    }

    private static Set<Long> blockedProvenanceGapV4Seeds() {
        return legacySeeds("V4", "COMPOSITION_APPLICATION_CAUSALITY_DIAGNOSTIC_SEEDS_V4",
                "07d198d980d498a7258c06557a9095cc7f2684c286a9acbdc72fa613f1327421");
    }

    private static Set<Long> legacySeeds(String version, String namespace, String binding) {
        HashSet<Long> values = new HashSet<>();
        for (var fixture : Phase13GB1AuditSchedule.create().allFixtures()) {
            for (int replicate = 0; replicate < SEEDS_PER_FIXTURE; replicate++) {
                String canonical = "seedSchema=COMPOSITION_V9_APPLICATION_CAUSALITY_SEED_" + version + "\n"
                        + "seedNamespace=" + namespace + '\n'
                        + "seedBindingHash=" + binding + '\n'
                        + "fixtureId=" + fixture.fixtureId() + '\n'
                        + "fixtureLane=" + fixture.fixtureLane() + '\n'
                        + "pairId=" + fixture.pairId() + '\n'
                        + "blueTeamCode=" + fixture.blueTeamCode() + '\n'
                        + "redTeamCode=" + fixture.redTeamCode() + '\n'
                        + "seriesGameNumber=" + fixture.seriesGameNumber() + '\n'
                        + "sampleLane=COMPOSITION_CAUSALITY_DIAGNOSTIC\n"
                        + "replicate=" + replicate + '\n';
                values.add(ByteBuffer.wrap(digest(canonical.getBytes(StandardCharsets.UTF_8))).getLong());
            }
        }
        if (values.size() != EXPECTED_PAIRS) throw new IllegalStateException("Legacy seed identity drift");
        return Set.copyOf(values);
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
                    .append(fixture.blueTeamCode()).append('|').append(fixture.redTeamCode()).append('|')
                    .append(fixture.seriesGameNumber()).append('\n');
            for (int index = 0; index < fixture.seeds().size(); index++) {
                result.append("seed=").append(fixture.fixtureId()).append('|').append(index).append('|')
                        .append(fixture.seeds().get(index)).append('\n');
            }
        }
        return result.toString();
    }

    static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    static String sha256(byte[] value) { return HexFormat.of().formatHex(digest(value)); }

    private static byte[] digest(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    public record Fixture(String fixtureId, Phase13GB1AuditSchedule.FixtureLane fixtureLane,
                          String pairId, String blueTeamCode, String redTeamCode,
                          int seriesGameNumber, List<Long> seeds) {
        public Fixture {
            Objects.requireNonNull(fixtureId); Objects.requireNonNull(fixtureLane);
            Objects.requireNonNull(pairId); Objects.requireNonNull(blueTeamCode);
            Objects.requireNonNull(redTeamCode); seeds = List.copyOf(seeds);
            if (seeds.size() != SEEDS_PER_FIXTURE || new HashSet<>(seeds).size() != seeds.size()) {
                throw new IllegalArgumentException("Invalid seed allocation");
            }
        }
    }

    public record Schedule(String schemaVersion, String scheduleVersion, String seedNamespace,
                           String seedBindingHash, String consumptionStatus, int seedsPerFixture,
                           List<Fixture> fixtures, String scheduleHash) {
        public Schedule { fixtures = List.copyOf(fixtures); }
    }

    public record SeedOverlapAudit(String seedNamespace, String seedBindingHash, int freshSeedCount,
                                   int duplicateCount, int phase13SeedCount, int phase13OverlapCount,
                                   int v9CalibrationSeedCount, int v9CalibrationOverlapCount,
                                   int v9HoldoutSeedCount, int v9HoldoutOverlapCount,
                                   int matchupAttributionSeedCount, int matchupAttributionOverlapCount,
                                   int failedWorkerIsolationV1SeedCount,
                                   int failedWorkerIsolationV1OverlapCount,
                                   int blockedProvenanceGapV2SeedCount,
                                   int blockedProvenanceGapV2OverlapCount,
                                   int blockedProvenanceGapV3SeedCount,
                                   int blockedProvenanceGapV3OverlapCount,
                                   int blockedProvenanceGapV4SeedCount,
                                   int blockedProvenanceGapV4OverlapCount,
                                   int reservedFutureSeedCount, int reservedFutureOverlapCount,
                                   String consumptionStatus) {
        public boolean clean() {
            return duplicateCount + phase13OverlapCount + v9CalibrationOverlapCount
                    + v9HoldoutOverlapCount + matchupAttributionOverlapCount
                    + failedWorkerIsolationV1OverlapCount + blockedProvenanceGapV2OverlapCount
                    + blockedProvenanceGapV3OverlapCount
                    + blockedProvenanceGapV4OverlapCount
                    + reservedFutureOverlapCount == 0;
        }
    }
}
