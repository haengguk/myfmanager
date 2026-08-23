package com.lolfm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.FrozenContract;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.HoldoutJob;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.RunGuard;
import com.lolfm.application.Phase13GB3HoldoutModel.FixtureCheckpoint;
import com.lolfm.application.Phase13GB3HoldoutModel.MatchRow;
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

/** One-time frozen holdout runner; no calibration path is accepted. */
public final class Phase13GB3FrozenHoldoutRunner {
    private static final Path B2_REPORT = Path.of("build", "reports", "phase13g-b2");

    private final RealDraftMatchOrchestrator orchestrator;
    private final ConfiguredMatchSimulatorFactory simulators;
    private final ObjectMapper mapper;
    private final ChampionCatalog champions;
    private final PlayerIdentityCatalog identities;
    private final PlayerRatingCatalog ratings;
    private final ChampionProficiencyCatalog proficiencies;

    public Phase13GB3FrozenHoldoutRunner(
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
        if (shardCount != Phase13GB3CheckpointStore.OFFICIAL_SHARD_COUNT
                || shardIndex < 0 || shardIndex >= shardCount) {
            throw new IllegalArgumentException("Invalid B3 shard identity");
        }
        var schedule = Phase13GB1AuditSchedule.requireFrozen(
                Phase13GB1AuditSchedule.create());
        Phase13GB3CheckpointStore store = new Phase13GB3CheckpointStore(mapper);
        FrozenContract contract = store.readFrozenContract(output);
        String contractHash = store.frozenContractHash(output);
        var harness = harness();
        RunGuard guard = guard(backendRoot, contractHash, harness);
        requireFrozenBinding(backendRoot, contract, guard);
        requireB2Binding(backendRoot.resolve(B2_REPORT), contract);
        store.authorizeShard(output, contractHash, shardIndex);

        String guardHash = store.guardHash(guard);
        List<Phase13GB1AuditSchedule.Fixture> fixtures = schedule.allFixtures();
        ArrayList<Phase13GB3HoldoutModel.CheckpointPayloadReceipt> payloadReceipts =
                new ArrayList<>();
        ArrayList<String> ownedFixtures = new ArrayList<>();
        int completedFixtures = 0;
        int completedMatches = 0;
        for (int fixtureIndex = shardIndex; fixtureIndex < fixtures.size();
                fixtureIndex += shardCount) {
            var fixture = fixtures.get(fixtureIndex);
            ownedFixtures.add(fixture.fixtureId());
            Path path = store.checkpointPath(
                    store.checkpointDirectory(output), fixtureIndex, fixture);
            FixtureCheckpoint checkpoint;
            if (Files.isRegularFile(path)) {
                checkpoint = store.readAndValidate(path, contract, guard, fixture);
                payloadReceipts.add(store.readPayloadReceipt(path, fixtureIndex, fixture));
                System.out.printf(LocaleHolder.ROOT,
                        "B3 shard %d/%d authenticated resume fixture %d/%d %s rows=%d%n",
                        shardIndex + 1, shardCount, fixtureIndex + 1, fixtures.size(),
                        fixture.fixtureId(), checkpoint.rows().size());
            } else {
                checkpoint = executeFixture(
                        store, harness, fixture, contractHash, guardHash, guard,
                        fixtureIndex, fixtures.size());
                payloadReceipts.add(store.writeAtomic(
                        path, fixtureIndex, checkpoint, contract, guard, fixture));
                System.out.printf(LocaleHolder.ROOT,
                        "B3 shard %d/%d checkpoint fixture %d/%d %s rows=%d%n",
                        shardIndex + 1, shardCount, fixtureIndex + 1, fixtures.size(),
                        fixture.fixtureId(), checkpoint.rows().size());
            }
            completedFixtures++;
            completedMatches += checkpoint.rows().size();
        }
        if (completedFixtures != 25 || completedMatches != 1_000) {
            throw new IllegalStateException("B3 shard coverage differs from contract");
        }
        var receipt = store.writeWorkerReceipt(
                output, contractHash, guardHash, shardIndex,
                List.copyOf(ownedFixtures), payloadReceipts);
        return new ShardResult(
                shardIndex, shardCount, completedFixtures, completedMatches,
                0, contractHash, guardHash, receipt.workerJvmIdentityHash());
    }

    public RunResult finalizeOfficial(Path backendRoot, Path output) throws IOException {
        var schedule = Phase13GB1AuditSchedule.requireFrozen(
                Phase13GB1AuditSchedule.create());
        Phase13GB3CheckpointStore store = new Phase13GB3CheckpointStore(mapper);
        FrozenContract contract = store.readFrozenContract(output);
        String contractHash = store.frozenContractHash(output);
        var harness = harness();
        RunGuard guard = guard(backendRoot, contractHash, harness);
        requireFrozenBinding(backendRoot, contract, guard);
        requireB2Binding(backendRoot.resolve(B2_REPORT), contract);
        var evidence = store.readOfficialEvidence(output, contract, guard, schedule);
        var artifacts = Phase13GB3ArtifactWriter.writeOfficial(
                mapper, output, schedule, contract, guard, evidence);
        return new RunResult(
                artifacts,
                evidence.checkpoints().size(),
                evidence.checkpoints().stream().mapToInt(value -> value.rows().size()).sum(),
                0,
                contractHash,
                store.guardHash(guard));
    }

    public SmokeResult runSmoke(Path backendRoot, Path output) throws IOException {
        var schedule = Phase13GB1AuditSchedule.requireFrozen(
                Phase13GB1AuditSchedule.create());
        var fixture = schedule.primaryFixtures().stream()
                .filter(value -> value.fixtureId().equals("G1_BFX_BLUE__DK_RED"))
                .findFirst().orElseThrow();
        var harness = harness();
        var prepared = harness.prepareFixture(fixture);
        long seed = Phase13GB1AuditSchedule.dryRunSeed(fixture);
        var runs = harness.executeAllProfiles(
                prepared, Phase13GB1AuditSchedule.SampleLane.DRY_RUN, seed);
        ArrayList<MatchRow> rows = new ArrayList<>();
        Phase13GB3CheckpointStore store = new Phase13GB3CheckpointStore(mapper);
        for (int index = 0; index < runs.size(); index++) {
            MatchRow row = MatchRow.synthetic(fixture, index, prepared, runs.get(index));
            validateRun(row);
            store.validateRowEvidence(row, store.rowEvidence(row));
            rows.add(row);
        }
        var replay = harness.execute(
                prepared, Phase13GB1AuditSchedule.SampleLane.DRY_RUN, seed,
                SimulationRuntimeProfileId.BASELINE_V1);
        var replayEvidence = Phase13GB3HoldoutModel.DeterminismReplayEvidence.from(
                fixture.fixtureId(), 0, seed, runs.getFirst(), replay);
        if (!replayEvidence.exact()) {
            throw new IllegalStateException("B3 dry-run deterministic replay differs");
        }
        var artifacts = Phase13GB3ArtifactWriter.writeSyntheticValidation(
                mapper, output, rows);
        return new SmokeResult(
                fixture.fixtureId(), seed, rows, replayEvidence, artifacts,
                0, 0);
    }

    private FixtureCheckpoint executeFixture(
            Phase13GB3CheckpointStore store,
            Phase13GB1RealMatchHarness harness,
            Phase13GB1AuditSchedule.Fixture fixture,
            String contractHash,
            String guardHash,
            RunGuard guard,
            int fixtureIndex,
            int fixtureCount
    ) {
        var prepared = harness.prepareFixture(fixture);
        List<HoldoutJob> jobs = Phase13GB3FrozenHoldoutContract.jobs(fixture);
        ArrayList<MatchRow> rows = new ArrayList<>(jobs.size());
        Phase13GB1RealMatchHarness.AuditMatchRun firstRun = null;
        Phase13GB3HoldoutModel.DeterminismReplayEvidence replayEvidence = null;
        for (int seedIndex = 0; seedIndex < 8; seedIndex++) {
            long seed = fixture.holdoutSeeds().get(seedIndex);
            if (fixture.calibrationSeeds().contains(seed)) {
                throw new IllegalStateException("B3 attempted a calibration seed");
            }
            var runs = harness.executeAllProfiles(
                    prepared, Phase13GB1AuditSchedule.SampleLane.HOLDOUT, seed);
            if (firstRun == null) firstRun = runs.getFirst();
            for (int profileIndex = 0; profileIndex < runs.size(); profileIndex++) {
                MatchRow row = MatchRow.from(
                        jobs.get(seedIndex * 5 + profileIndex),
                        prepared,
                        runs.get(profileIndex));
                validateRun(row);
                store.validateReplayProvenance(row, guard);
                rows.add(row);
            }
            if (seedIndex == 0) {
                var replay = harness.execute(
                        prepared, Phase13GB1AuditSchedule.SampleLane.HOLDOUT, seed,
                        SimulationRuntimeProfileId.BASELINE_V1);
                replayEvidence = Phase13GB3HoldoutModel.DeterminismReplayEvidence.from(
                        fixture.fixtureId(), seedIndex, seed, runs.getFirst(), replay);
                if (!replayEvidence.exact()) {
                    throw new IllegalStateException(
                            "B3 deterministic replay differs for " + fixture.fixtureId());
                }
            }
            System.out.printf(LocaleHolder.ROOT,
                    "B3 fixture %d/%d %s holdout seed %d/8%n",
                    fixtureIndex + 1, fixtureCount, fixture.fixtureId(), seedIndex + 1);
        }
        assertFixedDraftAcrossRows(rows);
        if (firstRun == null || replayEvidence == null) throw new IllegalStateException();
        return store.createCheckpoint(
                contractHash,
                guardHash,
                guard,
                Phase13GB2CalibrationModel.FixedDraftRow.from(prepared, firstRun),
                replayEvidence,
                rows);
    }

    private RunGuard guard(
            Path backendRoot,
            String contractHash,
            Phase13GB1RealMatchHarness harness
    ) throws IOException {
        var schedule = Phase13GB1AuditSchedule.create();
        EnumMap<SimulationRuntimeProfileId, String> configurations =
                new EnumMap<>(SimulationRuntimeProfileId.class);
        Phase13GB3FrozenHoldoutContract.PROFILE_ORDER.forEach(profile -> configurations.put(
                profile, SimulationRuntimeProfiles.resolve(profile).configurationHash()));
        return new RunGuard(
                Phase13GB3FrozenHoldoutContract.RUN_GUARD_SCHEMA,
                contractHash,
                schedule.scheduleVersion(),
                schedule.scheduleHash(),
                SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION,
                configurations,
                harness.resourceProvenance().resourceProvenanceHash(),
                harness.draftRuleSetIdentity(),
                harness.draftRuleSetHash(),
                harness.draftScoringPolicyHash(),
                Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot),
                Phase13GB1AuditArtifactWriter.phaseTestSourceTree(
                        backendRoot, "Phase13GB1"),
                Phase13GB1AuditArtifactWriter.phaseTestSourceTree(
                        backendRoot, "Phase13GB2"),
                Phase13GB1AuditArtifactWriter.phaseTestSourceTree(
                        backendRoot, "Phase13GB3"),
                100,
                4_000,
                0);
    }

    private void requireFrozenBinding(
            Path backendRoot,
            FrozenContract contract,
            RunGuard guard
    ) throws IOException {
        var frozen = contract.identities();
        if (!contract.scheduleVersion().equals(guard.scheduleVersion())
                || !contract.scheduleHash().equals(guard.scheduleHash())
                || !frozen.engineImplementationVersion().equals(
                        guard.engineImplementationVersion())
                || !frozen.configurationHashes().equals(guard.configurationHashes())
                || !frozen.resourceProvenanceHash().equals(guard.resourceProvenanceHash())
                || !frozen.draftRuleSetIdentity().equals(guard.draftRuleSetIdentity())
                || !frozen.draftRuleSetHash().equals(guard.draftRuleSetHash())
                || !frozen.draftScoringPolicyHash().equals(guard.draftScoringPolicyHash())
                || !frozen.productionSourceTree().equals(guard.productionSourceTree())
                || !frozen.phase13GB1HarnessSourceTree().equals(
                        guard.phase13GB1HarnessSourceTree())
                || !frozen.phase13GB2HarnessSourceTree().equals(
                        guard.phase13GB2HarnessSourceTree())
                || !frozen.phase13GB3HarnessSourceTree().equals(
                        guard.phase13GB3HarnessSourceTree())
                || contract.population().calibrationMatchExecutionCount() != 0
                || contract.holdoutExecutionCountAtFreeze() != 0) {
            throw new IllegalStateException("Current tree differs from frozen B3 contract");
        }
        for (SimulationRuntimeProfileId profile
                : Phase13GB3FrozenHoldoutContract.PROFILE_ORDER) {
            if (!frozen.activeGameplayRulesVersions().get(profile).equals(
                    SimulationRuntimeProfiles.resolve(profile)
                            .activeGameplayRulesVersion())) {
                throw new IllegalStateException("Active gameplay rules changed after freeze");
            }
        }
    }

    private void requireB2Binding(Path b2Report, FrozenContract contract) throws IOException {
        var binding = contract.b2Evidence();
        if (!sha(b2Report.resolve("phase13g-b2-calibration-contract.json"))
                        .equals(binding.contractFileSha256())
                || !sha(b2Report.resolve("phase13g-b2-review.json"))
                        .equals(binding.reviewFileSha256())
                || !sha(b2Report.resolve("phase13g-b2-integrity.json"))
                        .equals(binding.integrityFileSha256())
                || !sha(b2Report.resolve("SHA256SUMS.txt"))
                        .equals(binding.shaManifestSha256())) {
            throw new IllegalStateException("B2 evidence bytes changed after B3 freeze");
        }
        List<String> manifest = Files.readAllLines(
                b2Report.resolve("SHA256SUMS.txt"), StandardCharsets.UTF_8);
        if (manifest.size() != binding.shaManifestEntryCount()) {
            throw new IllegalStateException("B2 manifest count changed after freeze");
        }
        for (String line : manifest) {
            String[] fields = line.split("  ", 2);
            if (fields.length != 2 || !Files.isRegularFile(b2Report.resolve(fields[1]))
                    || !sha(b2Report.resolve(fields[1])).equals(fields[0])) {
                throw new IllegalStateException("B2 SHA manifest is no longer exact");
            }
        }
        JsonNode current = mapper.readTree(
                b2Report.resolve("phase13g-b2-calibration-contract.json").toFile());
        if (!current.path("runGuard").equals(binding.calibrationRunGuard())
                || !current.path("runGuardHash").asText().equals(binding.runGuardHash())) {
            throw new IllegalStateException("B2 run guard changed after B3 freeze");
        }
    }

    private String sha(Path path) throws IOException {
        return Phase13GB3CheckpointStore.sha256(Files.readAllBytes(path));
    }

    private static void validateRun(MatchRow row) {
        if (!row.integrityClean() || row.integrityErrorCount() != 0
                || row.randomDrawCount() <= 0 || row.blueSupportCs() != 0
                || row.redSupportCs() != 0 || !profileContract(row)) {
            throw new IllegalStateException("B3 structural integrity blocker in " + row.jobId());
        }
    }

    private static boolean profileContract(MatchRow row) {
        if (row.profileIndex() < 3) {
            return row.jungleEconomyEvaluations() == 0
                    && row.tempoEconomyUpdates() == 0
                    && row.tempoGankConsumptions() == 0
                    && row.tempoCounterGankConsumptions() == 0;
        }
        if (row.profileIndex() == 3) {
            return row.jungleEconomyEvaluations() > 0
                    && row.tempoEconomyUpdates() == 0
                    && row.tempoGankConsumptions() == 0
                    && row.tempoCounterGankConsumptions() == 0;
        }
        return row.jungleEconomyEvaluations() > 0
                && row.tempoEconomyUpdates() > 0
                && row.tempoGankConsumptions() == row.jungleGankAttempts()
                && row.tempoCounterGankConsumptions() == row.counterGankAttempts();
    }

    private static void assertFixedDraftAcrossRows(List<MatchRow> rows) {
        if (rows.stream().map(MatchRow::draftDecisionHash).distinct().count() != 1
                || rows.stream().map(MatchRow::finalDraftHash).distinct().count() != 1
                || rows.stream().map(MatchRow::finalAssignmentHash).distinct().count() != 1
                || rows.stream().map(MatchRow::rosterIdentityHash).distinct().count() != 1
                || rows.stream().map(MatchRow::seriesHistoryBeforeHash).distinct().count() != 1
                || rows.stream().map(MatchRow::resourceProvenanceHash).distinct().count() != 1) {
            throw new IllegalStateException("B3 fixture did not preserve one fixed Draft");
        }
    }

    private Phase13GB1RealMatchHarness harness() {
        return new Phase13GB1RealMatchHarness(
                orchestrator, simulators, mapper, champions, identities, ratings,
                proficiencies);
    }

    private static final class LocaleHolder {
        private static final java.util.Locale ROOT = java.util.Locale.ROOT;
    }

    public record ShardResult(
            int shardIndex,
            int shardCount,
            int completedFixtureCount,
            int holdoutMatchCount,
            int calibrationMatchCount,
            String frozenContractHash,
            String runGuardHash,
            String workerJvmIdentityHash
    ) {
    }

    public record RunResult(
            Phase13GB3ArtifactWriter.ArtifactSet artifacts,
            int completedFixtureCount,
            int holdoutMatchCount,
            int calibrationMatchCount,
            String frozenContractHash,
            String runGuardHash
    ) {
    }

    public record SmokeResult(
            String fixtureId,
            long seed,
            List<MatchRow> rows,
            Phase13GB3HoldoutModel.DeterminismReplayEvidence replay,
            Phase13GB3ArtifactWriter.SyntheticArtifactSet artifacts,
            int holdoutMatchCount,
            int calibrationMatchCount
    ) {
    }
}
