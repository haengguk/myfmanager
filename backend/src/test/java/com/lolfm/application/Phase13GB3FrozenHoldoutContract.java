package com.lolfm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.application.Phase13GB1AuditArtifactWriter.SourceTreeIdentity;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable B3 population, candidate, identity, and acceptance contract. */
public final class Phase13GB3FrozenHoldoutContract {
    public static final String PHASE = "PHASE_13G_B3_FROZEN_HOLDOUT";
    public static final String SCHEMA = "PHASE_13G_B3_FROZEN_HOLDOUT_CONTRACT_V1";
    public static final String JOB_SCHEMA = "PHASE_13G_B3_HOLDOUT_JOB_V1";
    public static final String RUN_GUARD_SCHEMA = "PHASE_13G_B3_RUN_GUARD_V1";
    public static final int EXPECTED_PRIMARY_FIXTURES = 90;
    public static final int EXPECTED_SECONDARY_FIXTURES = 10;
    public static final int EXPECTED_FIXTURES = 100;
    public static final int EXPECTED_SEEDS_PER_FIXTURE = 8;
    public static final int EXPECTED_PROFILES_PER_SEED = 5;
    public static final int EXPECTED_ROWS_PER_FIXTURE = 40;
    public static final int EXPECTED_PRIMARY_MATCHES = 3_600;
    public static final int EXPECTED_SECONDARY_MATCHES = 400;
    public static final int EXPECTED_MATCHES = 4_000;
    public static final int EXPECTED_PAIRED_MARGINALS = 4_000;
    public static final List<Integer> FIXED_CHECKPOINT_SECONDS =
            Phase13GB2CalibrationContract.FIXED_CHECKPOINT_SECONDS;
    public static final List<SimulationRuntimeProfileId> PROFILE_ORDER =
            Phase13GB1RealMatchHarness.AUDIT_PROFILES;
    public static final List<Phase13GB2CalibrationContract.MarginalComparison>
            MARGINAL_COMPARISONS = Phase13GB2CalibrationContract.MARGINAL_COMPARISONS;

    private Phase13GB3FrozenHoldoutContract() {
    }

    public static List<HoldoutJob> jobs(Phase13GB1AuditSchedule.Schedule schedule) {
        schedule = Phase13GB1AuditSchedule.requireFrozen(schedule);
        ArrayList<HoldoutJob> result = new ArrayList<>(EXPECTED_MATCHES);
        for (var fixture : schedule.allFixtures()) result.addAll(jobs(fixture));
        long primary = result.stream().filter(job -> job.fixtureLane()
                == Phase13GB1AuditSchedule.FixtureLane.PRIMARY_LEAGUE_G1).count();
        long secondary = result.size() - primary;
        if (schedule.allFixtures().size() != EXPECTED_FIXTURES
                || result.size() != EXPECTED_MATCHES
                || result.stream().map(HoldoutJob::jobId).distinct().count()
                        != EXPECTED_MATCHES
                || primary != EXPECTED_PRIMARY_MATCHES
                || secondary != EXPECTED_SECONDARY_MATCHES) {
            throw new IllegalStateException("Frozen B3 holdout job plan is incomplete");
        }
        return List.copyOf(result);
    }

    public static List<HoldoutJob> jobs(Phase13GB1AuditSchedule.Fixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        ArrayList<HoldoutJob> result = new ArrayList<>(EXPECTED_ROWS_PER_FIXTURE);
        for (int seedIndex = 0; seedIndex < fixture.holdoutSeeds().size(); seedIndex++) {
            long seed = fixture.holdoutSeeds().get(seedIndex);
            if (fixture.calibrationSeeds().contains(seed)) {
                throw new IllegalStateException("B3 holdout overlaps calibration seeds");
            }
            for (int profileIndex = 0; profileIndex < PROFILE_ORDER.size(); profileIndex++) {
                result.add(new HoldoutJob(
                        JOB_SCHEMA,
                        fixture.fixtureId(),
                        fixture.fixtureLane(),
                        fixture.pairId(),
                        fixture.blueTeamCode(),
                        fixture.redTeamCode(),
                        fixture.seriesGameNumber(),
                        Phase13GB1AuditSchedule.SampleLane.HOLDOUT,
                        seedIndex,
                        seed,
                        profileIndex,
                        PROFILE_ORDER.get(profileIndex)));
            }
        }
        if (fixture.holdoutSeeds().size() != EXPECTED_SEEDS_PER_FIXTURE
                || result.size() != EXPECTED_ROWS_PER_FIXTURE) {
            throw new IllegalStateException(
                    "Holdout fixture does not contain exactly 40 jobs: "
                            + fixture.fixtureId());
        }
        return List.copyOf(result);
    }

    public record HoldoutJob(
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
        public HoldoutJob {
            if (!JOB_SCHEMA.equals(schemaVersion)) {
                throw new IllegalArgumentException("Unsupported B3 holdout job schema");
            }
            Objects.requireNonNull(fixtureId, "fixtureId");
            Objects.requireNonNull(fixtureLane, "fixtureLane");
            Objects.requireNonNull(pairId, "pairId");
            Objects.requireNonNull(blueTeamCode, "blueTeamCode");
            Objects.requireNonNull(redTeamCode, "redTeamCode");
            Objects.requireNonNull(sampleLane, "sampleLane");
            Objects.requireNonNull(profileId, "profileId");
            if (sampleLane != Phase13GB1AuditSchedule.SampleLane.HOLDOUT) {
                throw new IllegalArgumentException("B3 jobs may consume holdout seeds only");
            }
            if (seedIndex < 0 || seedIndex >= EXPECTED_SEEDS_PER_FIXTURE) {
                throw new IllegalArgumentException("Invalid holdout seed index");
            }
            if (profileIndex < 0 || profileIndex >= EXPECTED_PROFILES_PER_SEED
                    || PROFILE_ORDER.get(profileIndex) != profileId) {
                throw new IllegalArgumentException("Invalid holdout profile order");
            }
        }

        public String jobId() {
            return fixtureId + "|HOLDOUT|" + seedIndex + '|' + profileId.name();
        }
    }

    public record FrozenContract(
            String schemaVersion,
            String phase,
            String scheduleVersion,
            String scheduleHash,
            B2EvidenceBinding b2Evidence,
            FrozenIdentities identities,
            CandidateFreeze candidateFreeze,
            String candidateFreezeIdentityHash,
            AcceptanceContract acceptance,
            String acceptanceGateIdentityHash,
            Population population,
            List<String> hardIntegrityGates,
            List<String> prohibitions,
            int holdoutExecutionCountAtFreeze,
            boolean automaticTuningAllowed,
            boolean postHoldoutGateChangesAllowed,
            String productionDecision,
            String nextStep
    ) {
        public FrozenContract {
            if (!SCHEMA.equals(schemaVersion) || !PHASE.equals(phase)) {
                throw new IllegalArgumentException("Unsupported B3 frozen contract");
            }
            Objects.requireNonNull(scheduleVersion, "scheduleVersion");
            requireHash(scheduleHash, "scheduleHash");
            Objects.requireNonNull(b2Evidence, "b2Evidence");
            Objects.requireNonNull(identities, "identities");
            Objects.requireNonNull(candidateFreeze, "candidateFreeze");
            requireHash(candidateFreezeIdentityHash, "candidateFreezeIdentityHash");
            Objects.requireNonNull(acceptance, "acceptance");
            requireHash(acceptanceGateIdentityHash, "acceptanceGateIdentityHash");
            Objects.requireNonNull(population, "population");
            hardIntegrityGates = List.copyOf(hardIntegrityGates);
            prohibitions = List.copyOf(prohibitions);
            if (population.totalMatches() != EXPECTED_MATCHES
                    || holdoutExecutionCountAtFreeze != 0
                    || automaticTuningAllowed
                    || postHoldoutGateChangesAllowed
                    || !"NOT_EVALUATED".equals(productionDecision)) {
                throw new IllegalArgumentException("B3 frozen scope is not immutable");
            }
        }
    }

    public record B2EvidenceBinding(
            String status,
            int calibrationMatchCount,
            int holdoutMatchCount,
            String runGuardHash,
            String checkpointPayloadManifestHash,
            String contractFileSha256,
            String reviewFileSha256,
            String integrityFileSha256,
            String shaManifestSha256,
            int shaManifestEntryCount,
            boolean shaManifestExact,
            JsonNode calibrationRunGuard
    ) {
        public B2EvidenceBinding {
            if (!"CALIBRATION_EVIDENCE_READY_FOR_REVIEW".equals(status)
                    || calibrationMatchCount != 12_000 || holdoutMatchCount != 0
                    || shaManifestEntryCount != 16 || !shaManifestExact) {
                throw new IllegalArgumentException("B2 evidence is not eligible for B3 freeze");
            }
            requireHash(runGuardHash, "runGuardHash");
            requireHash(checkpointPayloadManifestHash, "checkpointPayloadManifestHash");
            requireHash(contractFileSha256, "contractFileSha256");
            requireHash(reviewFileSha256, "reviewFileSha256");
            requireHash(integrityFileSha256, "integrityFileSha256");
            requireHash(shaManifestSha256, "shaManifestSha256");
            calibrationRunGuard = Objects.requireNonNull(
                    calibrationRunGuard, "calibrationRunGuard").deepCopy();
        }
    }

    public record FrozenIdentities(
            String engineImplementationVersion,
            Map<SimulationRuntimeProfileId, String> activeGameplayRulesVersions,
            Map<SimulationRuntimeProfileId, String> configurationHashes,
            String resourceProvenanceHash,
            JsonNode resourceProvenance,
            String playerIdentitySha256,
            String playerRatingsSha256,
            String playerProficiencySha256,
            String draftRuleSetIdentity,
            String draftRuleSetHash,
            String draftScoringPolicyHash,
            SourceTreeIdentity productionSourceTree,
            SourceTreeIdentity phase13GB1HarnessSourceTree,
            SourceTreeIdentity phase13GB2HarnessSourceTree,
            SourceTreeIdentity phase13GB3HarnessSourceTree
    ) {
        public FrozenIdentities {
            Objects.requireNonNull(engineImplementationVersion,
                    "engineImplementationVersion");
            activeGameplayRulesVersions = immutableEnumMap(activeGameplayRulesVersions);
            configurationHashes = immutableEnumMap(configurationHashes);
            requireHash(resourceProvenanceHash, "resourceProvenanceHash");
            resourceProvenance = Objects.requireNonNull(
                    resourceProvenance, "resourceProvenance").deepCopy();
            requireHash(playerIdentitySha256, "playerIdentitySha256");
            requireHash(playerRatingsSha256, "playerRatingsSha256");
            requireHash(playerProficiencySha256, "playerProficiencySha256");
            Objects.requireNonNull(draftRuleSetIdentity, "draftRuleSetIdentity");
            requireHash(draftRuleSetHash, "draftRuleSetHash");
            requireHash(draftScoringPolicyHash, "draftScoringPolicyHash");
            Objects.requireNonNull(productionSourceTree, "productionSourceTree");
            Objects.requireNonNull(phase13GB1HarnessSourceTree,
                    "phase13GB1HarnessSourceTree");
            Objects.requireNonNull(phase13GB2HarnessSourceTree,
                    "phase13GB2HarnessSourceTree");
            Objects.requireNonNull(phase13GB3HarnessSourceTree,
                    "phase13GB3HarnessSourceTree");
            if (configurationHashes.size() != EXPECTED_PROFILES_PER_SEED
                    || activeGameplayRulesVersions.size()
                            != EXPECTED_PROFILES_PER_SEED) {
                throw new IllegalArgumentException("Five profile identities are required");
            }
        }
    }

    public record CandidateFreeze(
            String economyCandidateId,
            String economyPrimaryComparison,
            String tempoCandidateId,
            String tempoPrimaryComparison,
            List<SimulationRuntimeProfileId> fixedProfileOrder,
            List<String> contextComparisonIds,
            String decisionStructure,
            String calibrationInterpretation
    ) {
        public CandidateFreeze {
            fixedProfileOrder = List.copyOf(fixedProfileOrder);
            contextComparisonIds = List.copyOf(contextComparisonIds);
            if (!fixedProfileOrder.equals(PROFILE_ORDER)
                    || !"ECONOMY_MINUS_FULL".equals(economyPrimaryComparison)
                    || !"TEMPO_MINUS_ECONOMY".equals(tempoPrimaryComparison)) {
                throw new IllegalArgumentException("B3 candidate scope changed");
            }
        }
    }

    public record AcceptanceContract(
            String schemaVersion,
            String statisticalMethod,
            double confidenceLevel,
            double zScore,
            String roundingPolicy,
            String boundaryPolicy,
            List<NumericGate> numericGates,
            List<ExactBehaviorGate> exactBehaviorGates,
            String economyVerdictPolicy,
            String tempoVerdictPolicy,
            String highTempoSensitivityPolicy
    ) {
        public AcceptanceContract {
            numericGates = List.copyOf(numericGates);
            exactBehaviorGates = List.copyOf(exactBehaviorGates);
            if (confidenceLevel != 0.99 || numericGates.isEmpty()
                    || exactBehaviorGates.isEmpty()) {
                throw new IllegalArgumentException("B3 acceptance gates are incomplete");
            }
        }
    }

    public record NumericGate(
            String gateId,
            String candidate,
            String comparisonId,
            String fixtureLane,
            String metric,
            int calibrationCount,
            int holdoutCount,
            double calibrationPointEstimate,
            double calibrationSampleStandardDeviation,
            double lowerInclusive,
            double upperInclusive,
            String comparator,
            String derivation
    ) {
        public NumericGate {
            Objects.requireNonNull(gateId, "gateId");
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(comparisonId, "comparisonId");
            Objects.requireNonNull(fixtureLane, "fixtureLane");
            Objects.requireNonNull(metric, "metric");
            Objects.requireNonNull(comparator, "comparator");
            Objects.requireNonNull(derivation, "derivation");
            if (calibrationCount <= 0 || holdoutCount <= 0
                    || lowerInclusive > upperInclusive
                    || !"INCLUSIVE_LOWER_AND_UPPER".equals(comparator)) {
                throw new IllegalArgumentException("Invalid numeric acceptance gate");
            }
        }
    }

    public record ExactBehaviorGate(
            String gateId,
            String candidate,
            String metric,
            String comparator,
            String expected,
            String calibrationEvidence
    ) {
    }

    public record Population(
            String sampleLane,
            int fixtureCount,
            int primaryFixtureCount,
            int secondaryFixtureCount,
            int seedsPerFixture,
            int profilesPerSeed,
            int rowsPerFixture,
            int primaryMatches,
            int secondaryMatches,
            int totalMatches,
            int determinismReplayCount,
            int workerShardCount,
            int calibrationMatchExecutionCount
    ) {
    }

    public record RunGuard(
            String schemaVersion,
            String frozenContractHash,
            String scheduleVersion,
            String scheduleHash,
            String engineImplementationVersion,
            Map<SimulationRuntimeProfileId, String> configurationHashes,
            String resourceProvenanceHash,
            String draftRuleSetIdentity,
            String draftRuleSetHash,
            String draftScoringPolicyHash,
            SourceTreeIdentity productionSourceTree,
            SourceTreeIdentity phase13GB1HarnessSourceTree,
            SourceTreeIdentity phase13GB2HarnessSourceTree,
            SourceTreeIdentity phase13GB3HarnessSourceTree,
            int expectedFixtureCount,
            int expectedHoldoutMatchCount,
            int calibrationMatchExecutionCount
    ) {
        public RunGuard {
            if (!RUN_GUARD_SCHEMA.equals(schemaVersion)) {
                throw new IllegalArgumentException("Unsupported B3 run guard");
            }
            requireHash(frozenContractHash, "frozenContractHash");
            requireHash(scheduleHash, "scheduleHash");
            configurationHashes = immutableEnumMap(configurationHashes);
            requireHash(resourceProvenanceHash, "resourceProvenanceHash");
            requireHash(draftRuleSetHash, "draftRuleSetHash");
            requireHash(draftScoringPolicyHash, "draftScoringPolicyHash");
            if (configurationHashes.size() != EXPECTED_PROFILES_PER_SEED
                    || expectedFixtureCount != EXPECTED_FIXTURES
                    || expectedHoldoutMatchCount != EXPECTED_MATCHES
                    || calibrationMatchExecutionCount != 0) {
                throw new IllegalArgumentException("B3 run guard violates frozen scope");
            }
        }
    }

    private static Map<SimulationRuntimeProfileId, String> immutableEnumMap(
            Map<SimulationRuntimeProfileId, String> source
    ) {
        EnumMap<SimulationRuntimeProfileId, String> copy =
                new EnumMap<>(SimulationRuntimeProfileId.class);
        copy.putAll(Objects.requireNonNull(source, "source"));
        return Collections.unmodifiableMap(copy);
    }

    static String requireHash(String value, String field) {
        String hash = Objects.requireNonNull(value, field);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return hash;
    }
}
