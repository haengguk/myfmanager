package com.lolfm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.Phase13GB2CalibrationModel.FixtureCheckpoint;
import com.lolfm.application.Phase13GB2CalibrationModel.MatchRow;
import com.lolfm.application.Phase13GB2CalibrationModel.RunGuard;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Sharded calibration runner with authenticated fixture resume and no holdout path. */
public final class Phase13GB2CalibrationRunner {
    private static final Path B1_REPORT = Path.of("build", "reports", "phase13g-b1");
    private static final Path CROSS_JVM_REPORT =
            Path.of("build", "reports", "phase13g-b1-cross-jvm",
                    "cross-jvm-determinism.txt");

    private final RealDraftMatchOrchestrator orchestrator;
    private final ConfiguredMatchSimulatorFactory simulators;
    private final ObjectMapper mapper;
    private final ChampionCatalog champions;
    private final PlayerIdentityCatalog identities;
    private final PlayerRatingCatalog ratings;
    private final ChampionProficiencyCatalog proficiencies;

    public Phase13GB2CalibrationRunner(
            RealDraftMatchOrchestrator orchestrator,
            ConfiguredMatchSimulatorFactory simulators,
            ObjectMapper mapper,
            ChampionCatalog champions,
            PlayerIdentityCatalog identities,
            PlayerRatingCatalog ratings,
            ChampionProficiencyCatalog proficiencies
    ) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.simulators = Objects.requireNonNull(simulators, "simulators");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.champions = Objects.requireNonNull(champions, "champions");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.ratings = Objects.requireNonNull(ratings, "ratings");
        this.proficiencies = Objects.requireNonNull(proficiencies, "proficiencies");
    }

    public ShardResult runShard(
            Path backendRoot,
            Path output,
            int shardIndex,
            int shardCount
    ) throws IOException {
        if (shardCount != Phase13GB2CheckpointStore.OFFICIAL_SHARD_COUNT
                || shardIndex < 0 || shardIndex >= shardCount) {
            throw new IllegalArgumentException("Invalid B2 shard identity");
        }
        var schedule = Phase13GB1AuditSchedule.requireFrozen(
                Phase13GB1AuditSchedule.create());
        var harness = harness();
        RunGuard guard = guard(backendRoot, schedule, harness);
        requireB1Gate(backendRoot, guard);
        Phase13GB2CheckpointStore store = new Phase13GB2CheckpointStore(mapper);
        String guardHash = store.guardHash(guard);
        Path checkpointDirectory = store.checkpointDirectory(output);
        List<Phase13GB1AuditSchedule.Fixture> fixtures = schedule.allFixtures();
        ArrayList<Phase13GB2CalibrationModel.CheckpointPayloadReceipt> payloadReceipts =
                new ArrayList<>();
        int completedFixtures = 0;
        int completedMatches = 0;
        for (int fixtureIndex = 0; fixtureIndex < fixtures.size(); fixtureIndex++) {
            if (fixtureIndex % shardCount != shardIndex) continue;
            var fixture = fixtures.get(fixtureIndex);
            Path checkpointPath = store.checkpointPath(
                    checkpointDirectory, fixtureIndex, fixture);
            FixtureCheckpoint checkpoint;
            if (Files.isRegularFile(checkpointPath)) {
                checkpoint = store.readAndValidate(checkpointPath, guard, fixture);
                payloadReceipts.add(store.readPayloadReceipt(
                        checkpointPath, fixtureIndex, fixture, checkpoint));
                System.out.printf(
                        java.util.Locale.ROOT,
                        "B2 shard %d/%d resume fixture %d/%d %s rows=%d%n",
                        shardIndex + 1,
                        shardCount,
                        fixtureIndex + 1,
                        fixtures.size(),
                        fixture.fixtureId(),
                        checkpoint.rows().size());
            } else {
                checkpoint = executeFixture(
                        store, harness, fixture, guardHash, guard,
                        fixtureIndex, fixtures.size());
                payloadReceipts.add(store.writeAtomic(
                        checkpointPath,
                        fixtureIndex,
                        checkpoint,
                        guard,
                        fixture));
                System.out.printf(
                        java.util.Locale.ROOT,
                        "B2 shard %d/%d checkpoint fixture %d/%d %s rows=%d%n",
                        shardIndex + 1,
                        shardCount,
                        fixtureIndex + 1,
                        fixtures.size(),
                        fixture.fixtureId(),
                        checkpoint.rows().size());
            }
            completedFixtures++;
            completedMatches += checkpoint.rows().size();
        }
        int expectedFixtures = (Phase13GB2CalibrationContract.EXPECTED_FIXTURES
                + shardCount - 1 - shardIndex) / shardCount;
        if (completedFixtures != expectedFixtures
                || completedMatches != expectedFixtures
                        * Phase13GB2CalibrationContract.EXPECTED_ROWS_PER_FIXTURE) {
            throw new IllegalStateException("B2 shard coverage differs from contract");
        }
        store.writeWorkerReceipt(
                output, guardHash, shardIndex, shardCount, payloadReceipts);
        return new ShardResult(
                shardIndex,
                shardCount,
                completedFixtures,
                completedMatches,
                0,
                guardHash);
    }

    public RunResult finalizeOfficial(Path backendRoot, Path output) throws IOException {
        var schedule = Phase13GB1AuditSchedule.requireFrozen(
                Phase13GB1AuditSchedule.create());
        var harness = harness();
        RunGuard guard = guard(backendRoot, schedule, harness);
        requireB1Gate(backendRoot, guard);
        Phase13GB2CheckpointStore store = new Phase13GB2CheckpointStore(mapper);
        var verifiedEvidence = store.readOfficialEvidence(output, guard, schedule);
        var checkpoints = verifiedEvidence.checkpoints();
        var artifacts = Phase13GB2CalibrationArtifactWriter.writeOfficial(
                mapper, output, schedule, guard, verifiedEvidence);
        if (!"CALIBRATION_EVIDENCE_READY_FOR_REVIEW".equals(artifacts.status())) {
            throw new IllegalStateException(
                    "B2 calibration was blocked by structural integrity");
        }
        return new RunResult(
                artifacts,
                checkpoints.size(),
                checkpoints.stream().mapToInt(value -> value.rows().size()).sum(),
                0,
                store.guardHash(guard));
    }

    public SmokeResult runSmoke(Path backendRoot, Path output) throws IOException {
        var schedule = Phase13GB1AuditSchedule.requireFrozen(
                Phase13GB1AuditSchedule.create());
        var fixture = schedule.primaryFixtures().stream()
                .filter(value -> value.fixtureId().equals("G1_BFX_BLUE__DK_RED"))
                .findFirst().orElseThrow();
        var harness = harness();
        RunGuard guard = guard(backendRoot, schedule, harness);
        Phase13GB2CheckpointStore store = new Phase13GB2CheckpointStore(mapper);
        var prepared = harness.prepareFixture(fixture);
        long seed = fixture.calibrationSeeds().getFirst();
        var runs = harness.executeAllProfiles(
                prepared, Phase13GB1AuditSchedule.SampleLane.CALIBRATION, seed);
        List<Phase13GB2CalibrationContract.CalibrationJob> jobs =
                Phase13GB2CalibrationContract.jobs(fixture).subList(
                        0, Phase13GB2CalibrationContract.EXPECTED_PROFILES_PER_SEED);
        ArrayList<MatchRow> rows = new ArrayList<>();
        for (int index = 0; index < runs.size(); index++) {
            MatchRow row = MatchRow.from(jobs.get(index), runs.get(index));
            validateRun(row);
            store.validateReplayProvenance(row, guard);
            store.validateRowEvidence(row, store.rowEvidence(row));
            rows.add(row);
        }
        assertFixedDraftAcrossRows(rows);
        var replay = harness.execute(
                prepared,
                Phase13GB1AuditSchedule.SampleLane.CALIBRATION,
                seed,
                SimulationRuntimeProfileId.BASELINE_V1);
        var replayEvidence = Phase13GB2CalibrationModel.DeterminismReplayEvidence.from(
                fixture.fixtureId(), 0, seed, runs.getFirst(), replay);
        if (!replayEvidence.exact()) {
            throw new IllegalStateException("B2 smoke deterministic replay differs");
        }
        var fixedDraft = Phase13GB2CalibrationModel.FixedDraftRow.from(
                prepared, runs.getFirst());
        var smokeArtifacts = Phase13GB2CalibrationArtifactWriter.writeSmoke(
                mapper,
                output,
                guard,
                fixedDraft,
                rows);
        return new SmokeResult(
                guard,
                fixture.fixtureId(),
                seed,
                prepared.productionOrchestrationCount(),
                List.copyOf(rows),
                fixedDraft,
                replayEvidence,
                smokeArtifacts);
    }

    private FixtureCheckpoint executeFixture(
            Phase13GB2CheckpointStore store,
            Phase13GB1RealMatchHarness harness,
            Phase13GB1AuditSchedule.Fixture fixture,
            String guardHash,
            RunGuard guard,
            int fixtureIndex,
            int fixtureCount
    ) {
        var prepared = harness.prepareFixture(fixture);
        List<Phase13GB2CalibrationContract.CalibrationJob> jobs =
                Phase13GB2CalibrationContract.jobs(fixture);
        ArrayList<MatchRow> rows = new ArrayList<>(jobs.size());
        Phase13GB1RealMatchHarness.AuditMatchRun firstRun = null;
        Phase13GB2CalibrationModel.DeterminismReplayEvidence replayEvidence = null;
        for (int seedIndex = 0;
                seedIndex < Phase13GB2CalibrationContract.EXPECTED_SEEDS_PER_FIXTURE;
                seedIndex++) {
            long seed = fixture.calibrationSeeds().get(seedIndex);
            var runs = harness.executeAllProfiles(
                    prepared,
                    Phase13GB1AuditSchedule.SampleLane.CALIBRATION,
                    seed);
            if (firstRun == null) firstRun = runs.getFirst();
            for (int profileIndex = 0; profileIndex < runs.size(); profileIndex++) {
                int jobIndex = seedIndex
                        * Phase13GB2CalibrationContract.EXPECTED_PROFILES_PER_SEED
                        + profileIndex;
                MatchRow row = MatchRow.from(jobs.get(jobIndex), runs.get(profileIndex));
                validateRun(row);
                rows.add(row);
            }
            if (seedIndex == 0) {
                var replay = harness.execute(
                        prepared,
                        Phase13GB1AuditSchedule.SampleLane.CALIBRATION,
                        seed,
                        SimulationRuntimeProfileId.BASELINE_V1);
                replayEvidence = Phase13GB2CalibrationModel.DeterminismReplayEvidence.from(
                        fixture.fixtureId(), seedIndex, seed, runs.getFirst(), replay);
                if (!replayEvidence.exact()) {
                    throw new IllegalStateException(
                            "B2 deterministic replay differs for " + fixture.fixtureId());
                }
            }
            System.out.printf(
                    java.util.Locale.ROOT,
                    "B2 fixture %d/%d %s seed %d/%d%n",
                    fixtureIndex + 1,
                    fixtureCount,
                    fixture.fixtureId(),
                    seedIndex + 1,
                    Phase13GB2CalibrationContract.EXPECTED_SEEDS_PER_FIXTURE);
        }
        assertFixedDraftAcrossRows(rows);
        if (firstRun == null || replayEvidence == null) {
            throw new IllegalStateException("B2 fixture produced no runs or replay evidence");
        }
        return store.createCheckpoint(
                guardHash,
                guard,
                Phase13GB2CalibrationModel.FixedDraftRow.from(prepared, firstRun),
                replayEvidence,
                rows);
    }

    private static void validateRun(MatchRow row) {
        if (!row.integrityClean()
                || row.integrityErrorCount() != 0
                || row.randomDrawCount() <= 0) {
            throw new IllegalStateException(
                    "B2 structural integrity blocker in " + row.jobId()
                            + " integrityErrors=" + row.integrityErrorCount()
                            + " randomDrawCount=" + row.randomDrawCount());
        }
        int profileIndex = row.profileIndex();
        if (profileIndex < 3) {
            if (row.jungleEconomyEvaluations() != 0
                    || row.tempoEconomyUpdates() != 0
                    || row.tempoGankConsumptions() != 0
                    || row.tempoCounterGankConsumptions() != 0) {
                throw new IllegalStateException("Jungle OFF profile mutated in " + row.jobId());
            }
        } else if (profileIndex == 3) {
            if (row.jungleEconomyEvaluations() <= 0
                    || row.tempoEconomyUpdates() != 0
                    || row.tempoGankConsumptions() != 0
                    || row.tempoCounterGankConsumptions() != 0) {
                throw new IllegalStateException(
                        "Economy-only profile contract failed in " + row.jobId());
            }
        } else if (row.jungleEconomyEvaluations() <= 0
                || row.tempoEconomyUpdates() <= 0
                || row.tempoGankConsumptions() != row.jungleGankAttempts()
                || row.tempoCounterGankConsumptions() != row.counterGankAttempts()) {
            throw new IllegalStateException(
                    "Tempo profile contract failed in " + row.jobId()
                            + " gankConsumption=" + row.tempoGankConsumptions()
                            + " gankAttempts=" + row.jungleGankAttempts()
                            + " counterConsumption=" + row.tempoCounterGankConsumptions()
                            + " counterAttempts=" + row.counterGankAttempts());
        }
    }

    private static void assertFixedDraftAcrossRows(List<MatchRow> rows) {
        if (rows.stream().map(MatchRow::draftDecisionHash).distinct().count() != 1
                || rows.stream().map(MatchRow::finalDraftHash).distinct().count() != 1
                || rows.stream().map(MatchRow::finalAssignmentHash).distinct().count() != 1
                || rows.stream().map(MatchRow::rosterIdentityHash).distinct().count() != 1
                || rows.stream().map(MatchRow::seriesHistoryBeforeHash).distinct().count() != 1
                || rows.stream().map(MatchRow::resourceProvenanceHash).distinct().count() != 1
                || rows.stream().map(MatchRow::engineImplementationVersion)
                        .distinct().count() != 1) {
            throw new IllegalStateException("B2 fixture did not preserve one fixed Draft input");
        }
    }

    private RunGuard guard(
            Path backendRoot,
            Phase13GB1AuditSchedule.Schedule schedule,
            Phase13GB1RealMatchHarness harness
    ) throws IOException {
        EnumMap<SimulationRuntimeProfileId, String> hashes =
                new EnumMap<>(SimulationRuntimeProfileId.class);
        Phase13GB1RealMatchHarness.AUDIT_PROFILES.forEach(profileId -> hashes.put(
                profileId,
                SimulationRuntimeProfiles.resolve(profileId).configurationHash()));
        return new RunGuard(
                Phase13GB2CalibrationContract.SCHEMA,
                schedule.scheduleVersion(),
                schedule.scheduleHash(),
                SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION,
                hashes,
                harness.resourceProvenance().resourceProvenanceHash(),
                harness.draftRuleSetIdentity(),
                harness.draftRuleSetHash(),
                harness.draftScoringPolicyHash(),
                Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot),
                Phase13GB1AuditArtifactWriter.phaseTestSourceTree(
                        backendRoot, "Phase13GB"),
                Phase13GB2CalibrationContract.EXPECTED_FIXTURES,
                Phase13GB2CalibrationContract.EXPECTED_MATCHES,
                0);
    }

    private Phase13GB1RealMatchHarness harness() {
        return new Phase13GB1RealMatchHarness(
                orchestrator,
                simulators,
                mapper,
                champions,
                identities,
                ratings,
                proficiencies);
    }

    private void requireB1Gate(Path backendRoot, RunGuard guard) throws IOException {
        Path crossJvm = backendRoot.resolve(CROSS_JVM_REPORT);
        Path b1Directory = backendRoot.resolve(B1_REPORT);
        Path b1Manifest = b1Directory.resolve(Phase13GB1AuditArtifactWriter.SHA_FILE);
        Path b1Summary = b1Directory.resolve(Phase13GB1AuditArtifactWriter.SUMMARY_FILE);
        if (!Files.isRegularFile(crossJvm)
                || !Files.isRegularFile(b1Manifest)
                || !Files.isRegularFile(b1Summary)) {
            throw new IllegalStateException(
                    "B2 requires a fresh clean B1 cross-JVM gate and artifact first");
        }
        Map<String, String> crossJvmEvidence = Files.readAllLines(
                        crossJvm, StandardCharsets.UTF_8).stream()
                .map(line -> line.split("=", 2))
                .filter(fields -> fields.length == 2)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        fields -> fields[0], fields -> fields[1]));
        String currentB1ManifestHash = Phase13GB2CheckpointStore.sha256(
                Files.readAllBytes(b1Manifest));
        if (!"PASS".equals(crossJvmEvidence.get("status"))
                || !"2".equals(crossJvmEvidence.get("freshJvmCount"))
                || !"7".equals(crossJvmEvidence.get("artifactFileCount"))
                || !currentB1ManifestHash.equals(
                        crossJvmEvidence.get("b1ManifestSha256"))) {
            throw new IllegalStateException(
                    "B1 cross-JVM gate is stale or differs from the current B1 artifact");
        }
        List<String> manifestLines = Files.readAllLines(
                b1Manifest, StandardCharsets.UTF_8);
        if (manifestLines.size() != 6) {
            throw new IllegalStateException("B1 SHA manifest file count differs");
        }
        for (String line : manifestLines) {
            String[] fields = line.split("  ", 2);
            if (fields.length != 2
                    || !Files.isRegularFile(b1Directory.resolve(fields[1]))
                    || !Phase13GB2CheckpointStore.sha256(
                            Files.readAllBytes(b1Directory.resolve(fields[1])))
                            .equals(fields[0])) {
                throw new IllegalStateException("B1 SHA manifest is not clean");
            }
        }
        JsonNode summary = mapper.readTree(b1Summary.toFile());
        if (!summary.path("status").asText()
                        .equals("HARNESS_HARDENED_READY_FOR_CALIBRATION")
                || !summary.path("engineImplementationVersion").asText()
                        .equals(guard.engineImplementationVersion())
                || !summary.path("scheduleHash").asText().equals(guard.scheduleHash())
                || !summary.path("productionSourceTree").path("hash").asText()
                        .equals(guard.productionSourceTree().hash())
                || !summary.path("resourceProvenance")
                        .path("resourceProvenanceHash").asText()
                        .equals(guard.resourceProvenanceHash())
                || !summary.path("sameSeedReplayExact").asBoolean()
                || !summary.path("fullStructuredDiagnosticsReplayExact").asBoolean()
                || summary.path("calibrationExecuted").asBoolean()
                || summary.path("holdoutExecuted").asBoolean()) {
            throw new IllegalStateException(
                    "B1 artifact does not match the current B2 execution guard");
        }
    }

    public record RunResult(
            Phase13GB2CalibrationArtifactWriter.ArtifactSet artifacts,
            int completedFixtureCount,
            int calibrationMatchCount,
            int holdoutMatchCount,
            String runGuardHash
    ) {
    }

    public record ShardResult(
            int shardIndex,
            int shardCount,
            int completedFixtureCount,
            int calibrationMatchCount,
            int holdoutMatchCount,
            String runGuardHash
    ) {
    }

    public record SmokeResult(
            RunGuard runGuard,
            String fixtureId,
            long seed,
            int productionOrchestrationCount,
            List<MatchRow> rows,
            Phase13GB2CalibrationModel.FixedDraftRow fixedDraft,
            Phase13GB2CalibrationModel.DeterminismReplayEvidence determinismReplay,
            Phase13GB2CalibrationArtifactWriter.SmokeArtifactSet artifacts
    ) {
        public SmokeResult {
            rows = List.copyOf(rows);
            Objects.requireNonNull(fixedDraft, "fixedDraft");
            Objects.requireNonNull(determinismReplay, "determinismReplay");
            Objects.requireNonNull(artifacts, "artifacts");
        }
    }
}
