package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.composition.TeamCompositionContext;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Position;
import com.lolfm.domain.StructureActionPhase;
import com.lolfm.domain.StructureStateSnapshot;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.Phase13GB1SimulationExecutor;
import com.lolfm.simulator.MatchEngineV9InstrumentationExecutor;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import com.lolfm.simulator.TeamSide;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Test-only, fixture-atomic execution harness for the bounded V9 requalification. */
public final class MatchEngineV9RequalificationRunner {
    public static final Path OUTPUT = Path.of(
            "build", "reports", "match-engine-v9-matchup-composition-requalification-v1");
    public static final int SHARD_COUNT = 4;
    private static final String CHECKPOINT_SCHEMA =
            "MATCH_ENGINE_V9_REQUALIFICATION_FIXTURE_CHECKPOINT_V1";

    private final ObjectMapper mapper;
    private final ObjectMapper canonical;
    private final Phase13GB1RealMatchHarness draftHarness;
    private final ConfiguredMatchSimulatorFactory simulators;
    private final SimulationProvenanceService provenance;
    private final PlayerIdentityCatalog identities;

    public MatchEngineV9RequalificationRunner(
            RealDraftMatchOrchestrator orchestrator,
            ConfiguredMatchSimulatorFactory simulators,
            ObjectMapper mapper,
            com.lolfm.champion.ChampionCatalog champions,
            PlayerIdentityCatalog identities,
            PlayerRatingCatalog ratings,
            ChampionProficiencyCatalog proficiencies
    ) {
        this.mapper = Objects.requireNonNull(mapper);
        canonical = mapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.INDENT_OUTPUT);
        this.simulators = Objects.requireNonNull(simulators);
        this.identities = Objects.requireNonNull(identities);
        draftHarness = new Phase13GB1RealMatchHarness(
                orchestrator, simulators, mapper, champions, identities, ratings, proficiencies);
        provenance = new SimulationProvenanceService(
                mapper, champions, identities, ratings, proficiencies);
    }

    public FreezeResult freeze(Path backendRoot, Path output) throws Exception {
        var schedule = MatchEngineV9RequalificationContract.requireFrozen(
                MatchEngineV9RequalificationContract.schedule());
        var overlap = MatchEngineV9RequalificationContract.requireNoSeedOverlap(schedule);
        SourceIdentity identity = sourceIdentity(backendRoot);
        LinkedHashMap<String, Object> contract = new LinkedHashMap<>();
        contract.put("schemaVersion", MatchEngineV9RequalificationContract.CONTRACT_SCHEMA);
        contract.put("referenceBaselineCommit", "c2814b63b6fa40487f893c510fbe5868e508724a");
        contract.put("currentHead", identity.gitHead());
        contract.put("engineImplementationVersion",
                SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION);
        contract.put("productionPolicy", MatchEngineV1Policy.authoritative());
        contract.put("profiles", profileBindings());
        contract.put("activeGameplayRulesVersion",
                SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION);
        contract.put("resourceProvenance", provenance.resourceProvenance());
        contract.put("draftRuleSetIdentity", provenance.draftRuleSetIdentity());
        contract.put("draftRuleSetHash", provenance.draftRuleSetHash());
        contract.put("draftScoringPolicyHash", provenance.draftScoringPolicyHash());
        contract.put("teamCodes", schedule.fixtures().stream()
                .flatMap(value -> java.util.stream.Stream.of(
                        value.blueTeamCode(), value.redTeamCode())).distinct().sorted().toList());
        contract.put("stablePlayerIds", identities.all().stream()
                .map(value -> value.playerId().value()).sorted().toList());
        contract.put("scheduleHash", schedule.scheduleHash());
        contract.put("seedNamespace", schedule.seedNamespace());
        contract.put("seedBindingHash", schedule.seedBindingHash());
        contract.put("fixtureCount", 100);
        contract.put("calibrationSeedsPerFixture", 8);
        contract.put("holdoutSeedsPerFixture", 4);
        contract.put("profileCount", 3);
        contract.put("officialPairedMatchRows", 3_600);
        contract.put("acceptanceGates", MatchEngineV9RequalificationContract.GATES);
        contract.put("exactGates", List.of(
                "TIMEOUT_0", "DOMAIN_INTEGRITY_ERROR_0", "DUPLICATE_MUTATION_REWARD_EVENT_0",
                "INVALID_STRUCTURE_STATE_0", "NEXUS_WITH_TURRET_ALIVE_0",
                "POST_FINISH_MUTATION_EVENT_0", "SAME_SEED_REPLAY_EXACT",
                "FRESH_JVM_CANONICAL_EXACT", "INSTRUMENTATION_PARITY_EXACT",
                "PROFILE_SUPPORT_COMPLETE", "PARTICIPANT_ASSIGNMENT_STALE_ERROR_0",
                "RANDOM_AND_REPLAY_PROVENANCE_EXACT", "SUPPORT_FARM_CS_0"));
        contract.put("matchupEligibilityGates", List.of(
                "GEOMETRIC_V2_REACHABILITY_GT_0", "OFF_CONTRIBUTION_0",
                "STRUCTURED_IDENTITY_ONLY", "SIDE_PERSPECTIVE_ERROR_0",
                "DIRECT_RANDOM_0", "EDGE_DIRECTION_MISMATCH_0"));
        contract.put("compositionEligibilityGates", List.of(
                "MATCHUP_ELIGIBLE_FIRST", "PRODUCTION_V2_REACHABILITY_GT_0",
                "OFF_AND_MATCHUP_ONLY_CONTRIBUTION_0",
                "DUPLICATE_MULTI_CONTEXT_CONFLICT_0",
                "SKIRMISH_TEAMFIGHT_SIEGE_BASE_DEFENSE_SEPARATE",
                "NO_DIRECT_OBJECTIVE_STRUCTURE_MUTATION", "DIRECT_RANDOM_0",
                "LOCAL_CAUSE_BEFORE_PUBLIC_DIVERGENCE"));
        contract.put("draftReusePolicy",
                "ONE_PRODUCTION_DRAFT_PER_FIXTURE_OUTSIDE_PROFILE_AND_SEED_LOOPS");
        contract.put("productionActivation", false);
        contract.put("sourceIdentity", identity);

        Files.createDirectories(output);
        byte[] contractBytes = canonicalBytes(contract);
        String contractHash = MatchEngineV9RequalificationContract.sha256(contractBytes);
        writeFrozen(output.resolve("contract.json"), contractBytes);
        writeFrozen(output.resolve("contract.sha256"),
                (contractHash + "  contract.json\n").getBytes(StandardCharsets.UTF_8));
        writeFrozen(output.resolve("source-resource-runtime-identity.json"),
                canonicalBytes(new IdentityArtifact(identity, provenance.resourceProvenance(),
                        MatchEngineV1Policy.authoritative(), profileBindings(),
                        identities.version(), identities.resourceSha256(),
                        identities.all().size())));
        writeFrozen(output.resolve("frozen-schedule.json"), canonicalBytes(schedule));
        writeFrozen(output.resolve("frozen-schedule.csv"), scheduleCsv(schedule).getBytes(StandardCharsets.UTF_8));
        writeFrozen(output.resolve("seed-overlap-audit.json"), canonicalBytes(overlap));
        return new FreezeResult(contractHash, schedule.scheduleHash(), identity, overlap);
    }

    public SmokeResult smoke(Path backendRoot, Path output) throws Exception {
        Binding binding = requireBinding(backendRoot, output);
        var fixtures = MatchEngineV9RequalificationContract.schedule().fixtures();
        List<MatchEngineV9RequalificationContract.Fixture> smokeFixtures = List.of(
                fixtures.stream().filter(value -> value.fixtureId()
                        .equals("G1_BFX_BLUE__DK_RED")).findFirst().orElseThrow(),
                fixtures.get(90));
        ArrayList<MatchRow> rows = new ArrayList<>();
        boolean replayExact = true;
        boolean instrumentationExact = true;
        for (var fixture : smokeFixtures) {
            var prepared = draftHarness.prepareFixture(
                    MatchEngineV9RequalificationContract.sourceFixture(fixture));
            long seed = MatchEngineV9RequalificationContract.dryRunSeed(fixture);
            List<Executed> runs = executeProfiles(prepared, fixture, seed);
            rows.addAll(toRows(fixture, MatchEngineV9RequalificationContract.SampleLane.DRY_RUN,
                    0, runs));
            Executed replay = execute(prepared, fixture, seed,
                    SimulationRuntimeProfileId.BASELINE_V1);
            replayExact &= exact(runs.getFirst(), replay);
            for (SimulationRuntimeProfileId profile : MatchEngineV9RequalificationContract.PROFILES) {
                Executed enabled = runs.stream().filter(value -> value.profileId() == profile)
                        .findFirst().orElseThrow();
                instrumentationExact &= instrumentationExact(prepared.realDraftFixture(), fixture,
                        seed, enabled);
            }
        }
        boolean matchupReachable = rows.stream()
                .filter(value -> value.profileId() == SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1)
                .anyMatch(value -> value.matchupApplications() > 0);
        boolean compositionReachable = rows.stream()
                .filter(value -> value.profileId() == SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1)
                .allMatch(value -> value.compositionProfileInitialized()
                        && value.compositionActualAttempts() > 0);
        SmokeResult result = new SmokeResult(binding.contractHash(), rows.size(), replayExact,
                instrumentationExact, matchupReachable, compositionReachable,
                rows.stream().map(MatchRow::fixedDraftHash).distinct().count()
                        == smokeFixtures.size(),
                rows.stream().mapToLong(MatchRow::integrityErrorCount).sum());
        if (!result.clean()) throw new IllegalStateException("V9 smoke gate failed: " + result);
        writeReplace(output.resolve("smoke-review.json"), canonicalBytes(result));
        return result;
    }

    public ShardResult runShard(
            Path backendRoot, Path output,
            MatchEngineV9RequalificationContract.SampleLane lane,
            int shardIndex
    ) throws Exception {
        if (shardIndex < 0 || shardIndex >= SHARD_COUNT) throw new IllegalArgumentException();
        Binding binding = requireBinding(backendRoot, output);
        if (lane == MatchEngineV9RequalificationContract.SampleLane.HOLDOUT) {
            requireHoldoutAuthorized(output, binding);
            if (Files.exists(output.resolve("holdout-consumed.json"))) {
                throw new IllegalStateException("Frozen V9 holdout is already consumed");
            }
        }
        Path directory = checkpointDirectory(output, lane);
        Files.createDirectories(directory);
        int fixtureCount = 0;
        int rowCount = 0;
        List<String> payloadHashes = new ArrayList<>();
        List<MatchEngineV9RequalificationContract.Fixture> fixtures =
                MatchEngineV9RequalificationContract.schedule().fixtures();
        for (int fixtureIndex = shardIndex; fixtureIndex < fixtures.size(); fixtureIndex += SHARD_COUNT) {
            var fixture = fixtures.get(fixtureIndex);
            Path checkpointPath = directory.resolve(String.format(Locale.ROOT,
                    "%03d-%s.json", fixtureIndex, fixture.fixtureId()));
            FixtureCheckpoint checkpoint;
            if (Files.isRegularFile(checkpointPath)) {
                checkpoint = readCheckpoint(checkpointPath, binding, fixture, lane);
            } else {
                checkpoint = executeFixture(binding, fixtureIndex, fixture, lane);
                writeCheckpoint(checkpointPath, checkpoint);
            }
            fixtureCount++;
            rowCount += checkpoint.rows().size();
            payloadHashes.add(fileHash(checkpointPath));
            System.out.printf(Locale.ROOT, "V9 %s shard %d/%d fixture %d/100 %s rows=%d%n",
                    lane, shardIndex + 1, SHARD_COUNT, fixtureIndex + 1,
                    fixture.fixtureId(), checkpoint.rows().size());
        }
        WorkerReceipt receipt = new WorkerReceipt(
                "MATCH_ENGINE_V9_REQUALIFICATION_WORKER_RECEIPT_V1",
                binding.contractHash(), binding.sourceIdentity().harnessSourceTree().hash(),
                lane, shardIndex, SHARD_COUNT, workerJvmIdentity(),
                fixtureCount, rowCount, List.copyOf(payloadHashes));
        Path receiptPath = output.resolve("worker-receipts").resolve(
                lane.name().toLowerCase(Locale.ROOT) + "-shard-" + shardIndex + ".json");
        Files.createDirectories(receiptPath.getParent());
        writeReplace(receiptPath, canonicalBytes(receipt));
        return new ShardResult(lane, shardIndex, fixtureCount, rowCount,
                receipt.workerJvmIdentityHash());
    }

    public CalibrationReview finalizeCalibration(Path backendRoot, Path output) throws Exception {
        Binding binding = requireBinding(backendRoot, output);
        List<FixtureCheckpoint> checkpoints = readAllCheckpoints(
                output, binding, MatchEngineV9RequalificationContract.SampleLane.CALIBRATION);
        List<MatchRow> rows = checkpoints.stream().flatMap(value -> value.rows().stream()).toList();
        if (rows.size() != MatchEngineV9RequalificationContract.EXPECTED_CALIBRATION_ROWS) {
            throw new IllegalStateException("Calibration coverage incomplete");
        }
        CalibrationReview review = new CalibrationReview(
                "MATCH_ENGINE_V9_REQUALIFICATION_CALIBRATION_REVIEW_V1",
                binding.contractHash(), 100, 8, 3, rows.size(),
                sensitivity(rows), exactIntegrity(rows),
                "PRE_FROZEN_GATES_RETAINED_WITHOUT_AUTOMATIC_TUNING");
        writeReplace(output.resolve("calibration-review.json"), canonicalBytes(review));
        HoldoutAuthorization authorization = new HoldoutAuthorization(
                "MATCH_ENGINE_V9_REQUALIFICATION_HOLDOUT_AUTHORIZATION_V1",
                binding.contractHash(), binding.sourceIdentity().harnessSourceTree().hash(),
                MatchEngineV9RequalificationContract.schedule().scheduleHash(),
                fileHash(output.resolve("calibration-review.json")),
                MatchEngineV9RequalificationContract.GATES,
                "HOLDOUT_AUTHORIZED_ON_FROZEN_CONTRACT_WITH_UNCHANGED_GATES");
        writeFrozen(output.resolve("holdout-authorization.json"), canonicalBytes(authorization));
        return review;
    }

    public ArtifactResult writeCandidate(
            Path backendRoot, Path output, Path candidateOutput) throws Exception {
        Binding binding = requireBinding(backendRoot, output);
        List<FixtureCheckpoint> calibration = readAllCheckpoints(output, binding,
                MatchEngineV9RequalificationContract.SampleLane.CALIBRATION);
        List<FixtureCheckpoint> holdout = readAllCheckpoints(output, binding,
                MatchEngineV9RequalificationContract.SampleLane.HOLDOUT);
        return MatchEngineV9RequalificationArtifactWriter.write(
                canonical, output, candidateOutput, binding,
                calibration, holdout, provenance, identities);
    }

    public ArtifactResult promoteOfficial(
            Path backendRoot, Path output, Path candidateA, Path candidateB) throws Exception {
        requireTreeByteEquality(candidateA, candidateB);
        copyTree(candidateA, output);
        ArtifactResult result = canonical.readValue(
                output.resolve("artifact-result.json").toFile(), ArtifactResult.class);
        Binding binding = requireBinding(backendRoot, output);
        writeFrozen(output.resolve("holdout-consumed.json"), canonicalBytes(Map.of(
                "schemaVersion", "MATCH_ENGINE_V9_REQUALIFICATION_HOLDOUT_CONSUMED_V1",
                "contractHash", binding.contractHash(),
                "holdoutRowCount", 1_200,
                "officialManifestHash", fileHash(output.resolve("SHA256SUMS.txt")),
                "freshJvmCandidateA", workerJvmIdentity(),
                "candidateTreesByteEqual", true)));
        return result;
    }

    private FixtureCheckpoint executeFixture(
            Binding binding, int fixtureIndex,
            MatchEngineV9RequalificationContract.Fixture fixture,
            MatchEngineV9RequalificationContract.SampleLane lane
    ) throws Exception {
        var prepared = draftHarness.prepareFixture(
                MatchEngineV9RequalificationContract.sourceFixture(fixture));
        List<Long> seeds = lane == MatchEngineV9RequalificationContract.SampleLane.CALIBRATION
                ? fixture.calibrationSeeds() : fixture.holdoutSeeds();
        ArrayList<MatchRow> rows = new ArrayList<>(seeds.size() * 3);
        boolean replayExact = true;
        for (int seedIndex = 0; seedIndex < seeds.size(); seedIndex++) {
            long seed = seeds.get(seedIndex);
            List<Executed> runs = executeProfiles(prepared, fixture, seed);
            rows.addAll(toRows(fixture, lane, seedIndex, runs));
            if (seedIndex == 0) {
                replayExact &= exact(runs.getFirst(), execute(prepared, fixture, seed,
                        SimulationRuntimeProfileId.BASELINE_V1));
            }
        }
        if (!replayExact) throw new IllegalStateException("Same-seed replay mismatch");
        FixedDraftRow draft = fixedDraft(fixture, prepared.realDraftFixture(), rows.getFirst());
        return new FixtureCheckpoint(
                CHECKPOINT_SCHEMA, binding.contractHash(),
                binding.sourceIdentity().harnessSourceTree().hash(), fixtureIndex,
                fixture.fixtureId(), lane, workerJvmIdentity(), draft, replayExact,
                List.copyOf(rows));
    }

    private List<Executed> executeProfiles(
            Phase13GB1RealMatchHarness.PreparedFixture prepared,
            MatchEngineV9RequalificationContract.Fixture fixture,
            long seed
    ) {
        ArrayList<Executed> result = new ArrayList<>();
        for (var profile : MatchEngineV9RequalificationContract.PROFILES) {
            result.add(execute(prepared, fixture, seed, profile));
        }
        if (result.stream().map(value -> value.provenance().finalDraftHash()).distinct().count() != 1
                || result.stream().map(value -> value.provenance().finalAssignmentHash()).distinct().count() != 1
                || result.stream().map(value -> value.provenance().rosterIdentityHash()).distinct().count() != 1) {
            throw new IllegalStateException("Profile loop did not reuse one fixed production Draft");
        }
        return List.copyOf(result);
    }

    private Executed execute(
            Phase13GB1RealMatchHarness.PreparedFixture prepared,
            MatchEngineV9RequalificationContract.Fixture fixture,
            long seed,
            SimulationRuntimeProfileId profileId
    ) {
        RealDraftMatchResult fixed = prepared.realDraftFixture();
        var profile = SimulationRuntimeProfiles.resolve(profileId);
        var execution = Phase13GB1SimulationExecutor.execute(
                simulators, fixed.blueTeam(), fixed.redTeam(), fixed.matchChampionAssignments(),
                profileId, seed, fixture.blueTeamCode(), fixture.redTeamCode());
        var executionProvenance = provenance.create(
                profile, SimulationInstrumentation.enabled(), fixture.blueTeamCode(),
                fixed.blueTeam(), fixture.redTeamCode(), fixed.redTeam(), seed,
                fixture.seriesGameNumber(), fixed.hardFearlessExclusionsBeforeDraft(),
                fixed.draftResult(), execution.timeline(), execution.randomFingerprint());
        return new Executed(profileId, execution, executionProvenance);
    }

    private List<MatchRow> toRows(
            MatchEngineV9RequalificationContract.Fixture fixture,
            MatchEngineV9RequalificationContract.SampleLane lane,
            int seedIndex,
            List<Executed> runs
    ) {
        int fullDivergence = firstPublicDivergence(runs.get(1).execution().timeline(),
                runs.get(2).execution().timeline());
        ArrayList<MatchRow> rows = new ArrayList<>(3);
        for (int index = 0; index < runs.size(); index++) {
            Executed run = runs.get(index);
            rows.add(row(fixture, lane, seedIndex, index, run,
                    index == 2 ? fullDivergence : -1));
        }
        return List.copyOf(rows);
    }

    private MatchRow row(
            MatchEngineV9RequalificationContract.Fixture fixture,
            MatchEngineV9RequalificationContract.SampleLane lane,
            int seedIndex,
            int profileIndex,
            Executed run,
            int firstPublicDivergenceSeconds
    ) {
        MatchTimeline timeline = run.execution().timeline();
        MatchSnapshot end = timeline.getSnapshots().getLast();
        var diagnostics = run.execution().structuredDiagnostics();
        var integrity = Phase13GB1RealMatchHarness.IntegrityDiagnostics.from(
                SimulationRuntimeProfiles.resolve(run.profileId()).gameplayConfiguration(), diagnostics);
        TimelineIntegrity timelineIntegrity = timelineIntegrity(timeline);
        long totalIntegrity = integrity.errorCount() + timelineIntegrity.errorCount();
        int matchupApplications = diagnostics.championMatchup().nonZeroContributionApplications();
        var composition = diagnostics.composition();
        int localChangeCount = (int) composition.localDecisionComparisons().stream()
                .filter(value -> value.comparisonAvailable() && value.localOutcomeChanged()).count();
        int firstLocalChange = composition.localDecisionComparisons().stream()
                .filter(value -> value.comparisonAvailable() && value.localOutcomeChanged())
                .mapToInt(value -> value.matchTimeSeconds()).min().orElse(-1);
        int localCauseViolations = firstPublicDivergenceSeconds >= 0
                && (firstLocalChange < 0 || firstPublicDivergenceSeconds < firstLocalChange) ? 1 : 0;
        Map<String, Integer> contexts = new TreeMap<>();
        for (TeamCompositionContext context : TeamCompositionContext.values()) contexts.put(context.name(), 0);
        composition.candidateApplications().stream().filter(value -> value.applicationApplied())
                .forEach(value -> contexts.compute(value.context().name(),
                        (key, count) -> count == null ? 1 : count + 1));
        StructureMetrics structure = structureMetrics(timeline);
        return new MatchRow(
                "MATCH_ENGINE_V9_REQUALIFICATION_MATCH_ROW_V1", fixture.fixtureId(),
                fixture.fixtureLane(), fixture.pairId(), fixture.blueTeamCode(),
                fixture.redTeamCode(), fixture.seriesGameNumber(), lane, seedIndex,
                run.provenance().matchSeed(), profileIndex, run.profileId(),
                run.provenance().configurationHash(), run.provenance().activeGameplayRulesVersion(),
                run.provenance().engineImplementationVersion(),
                run.provenance().resourceProvenance().resourceProvenanceHash(),
                run.provenance().rosterIdentityHash(), run.provenance().draftDecisionHash(),
                run.provenance().finalDraftHash(), run.provenance().finalAssignmentHash(),
                run.provenance().replayProvenanceHash(), run.provenance().timelineHash(),
                run.execution().randomFingerprint().randomDrawCount(),
                run.execution().randomFingerprint().randomTraceHash(),
                timeline.getWinner(), run.execution().winnerSide(), run.execution().endReason(),
                timeline.getDurationSeconds(), end.getBlueKills(), end.getRedKills(),
                end.getBlueGold(), end.getRedGold(), end.getBlueDragons(), end.getRedDragons(),
                end.getBlueTowersDestroyed(), end.getRedTowersDestroyed(),
                objectiveSignature(timeline, end), structure.signature(), structure.firstTowerSeconds(),
                structure.firstTowerSource(), structure.firstTowerLane(), structure.damageEvents(),
                structure.destroyedEvents(), structure.siegeStarted(), structure.siegeStopped(),
                contexts.getOrDefault(TeamCompositionContext.BASE_DEFENSE.name(), 0), matchupApplications,
                diagnostics.championMatchup().finalMatchupEdgeSum(),
                diagnostics.championMatchup().directRandomCalls(),
                composition.initialized(), composition.actualAttemptCount(),
                composition.gameplayApplicationCount(), composition.nonZeroModifierCount(),
                composition.directRandomCallCount(), composition.compositionRandomDrawCount(),
                contexts, localChangeCount, firstLocalChange, firstPublicDivergenceSeconds,
                localCauseViolations, totalIntegrity, timelineIntegrity.invalidStructureState(),
                timelineIntegrity.nexusDestroyedWithTurretAlive(),
                timelineIntegrity.postFinishMutationOrEvent(), timelineIntegrity.supportFarmCsErrors(),
                Phase13GB1SimulationExecutor.structuredDiagnosticsHash(diagnostics));
    }

    private boolean instrumentationExact(
            RealDraftMatchResult fixed,
            MatchEngineV9RequalificationContract.Fixture fixture,
            long seed,
            Executed enabled
    ) {
        var disabled = MatchEngineV9InstrumentationExecutor.execute(
                simulators, fixed.blueTeam(), fixed.redTeam(), fixed.matchChampionAssignments(),
                enabled.profileId(), SimulationInstrumentation.disabled(), seed,
                fixture.blueTeamCode(), fixture.redTeamCode());
        SimulationRandomFingerprint fingerprint = disabled.randomFingerprint();
        return provenance.timelineHash(disabled.timeline()).equals(
                enabled.provenance().timelineHash())
                && fingerprint.equals(enabled.execution().randomFingerprint());
    }

    private static boolean exact(Executed first, Executed second) {
        return first.provenance().timelineHash().equals(second.provenance().timelineHash())
                && first.execution().randomFingerprint().equals(second.execution().randomFingerprint())
                && Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                        first.execution().structuredDiagnostics()).equals(
                        Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                                second.execution().structuredDiagnostics()));
    }

    private TimelineIntegrity timelineIntegrity(MatchTimeline timeline) {
        int invalidStructure = 0;
        int nexusWithTurret = 0;
        int postFinish = 0;
        int supportCs = 0;
        for (MatchSnapshot snapshot : timeline.getSnapshots()) {
            if (snapshot.getTimeSeconds() > timeline.getDurationSeconds()) postFinish++;
            for (var player : snapshot.getPlayerSnapshots()) {
                if (player.getPosition() == Position.SUPPORT && player.getCs() != 0) supportCs++;
            }
            StructureStateSnapshot state = snapshot.getStructureState();
            for (var team : state.teams().values()) {
                invalidStructure += invalidHealth(team.nexusCurrentHealth(), team.nexusMaxHealth());
                for (double health : team.nexusTurretCurrentHealth()) {
                    invalidStructure += invalidHealth(health, team.nexusTurretMaxHealth());
                }
                for (var lane : team.lanes().values()) {
                    invalidStructure += invalidHealth(lane.outerTower());
                    invalidStructure += invalidHealth(lane.innerTower());
                    invalidStructure += invalidHealth(lane.inhibitorTower());
                    invalidStructure += invalidHealth(lane.inhibitor());
                }
                if (!team.nexusAlive() && team.nexusTurretsRemaining() > 0) nexusWithTurret++;
            }
        }
        for (MatchEvent event : timeline.getEvents()) {
            if (event.getTimeSeconds() > timeline.getDurationSeconds()) postFinish++;
        }
        return new TimelineIntegrity(invalidStructure, nexusWithTurret, postFinish, supportCs);
    }

    private static int invalidHealth(StructureStateSnapshot.Health health) {
        return invalidHealth(health.current(), health.maximum());
    }

    private static int invalidHealth(double current, double maximum) {
        return !Double.isFinite(current) || !Double.isFinite(maximum) || maximum < 0.0
                || current < -1.0e-9 || current > maximum + 1.0e-9 ? 1 : 0;
    }

    private static StructureMetrics structureMetrics(MatchTimeline timeline) {
        int firstTower = -1;
        String source = "NONE";
        String lane = "NONE";
        int damage = 0;
        int destroyed = 0;
        int started = 0;
        int stopped = 0;
        for (MatchEvent event : timeline.getEvents()) {
            if (event.getType() == MatchEventType.TOWER && firstTower < 0) {
                firstTower = event.getTimeSeconds();
                source = event.getStructureActionSource() == null
                        ? "UNKNOWN" : event.getStructureActionSource().name();
                lane = event.getStructureLane() == null ? "UNKNOWN" : event.getStructureLane().name();
            }
            if (event.getStructureAction() != null) {
                var action = event.getStructureAction();
                if (action.phase() == StructureActionPhase.DAMAGE) damage++;
                if (action.phase() == StructureActionPhase.DESTROYED) destroyed++;
                if (action.phase() == StructureActionPhase.STARTED) started++;
                if (action.phase() == StructureActionPhase.REPELLED
                        || action.phase() == StructureActionPhase.ABORTED) stopped++;
            }
        }
        MatchSnapshot end = timeline.getSnapshots().getLast();
        String signature = end.getBlueTowersDestroyed() + ":" + end.getRedTowersDestroyed()
                + ":" + end.getBlueInhibitorsRemaining() + ":" + end.getRedInhibitorsRemaining()
                + ":" + end.getBlueNexusTurretsRemaining() + ":" + end.getRedNexusTurretsRemaining()
                + ":" + end.isBlueNexusAlive() + ":" + end.isRedNexusAlive()
                + ":" + MatchEngineV9RequalificationContract.sha256(
                        canonicalStructureState(end.getStructureState()));
        return new StructureMetrics(signature, firstTower, source, lane, damage,
                destroyed, started, stopped, 0);
    }

    private static String canonicalStructureState(StructureStateSnapshot state) {
        StringBuilder value = new StringBuilder();
        state.teams().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> value.append(entry.getKey()).append('=')
                        .append(entry.getValue()).append('\n'));
        return value.toString();
    }

    private static String objectiveSignature(MatchTimeline timeline, MatchSnapshot end) {
        long barons = timeline.getEvents().stream().filter(value -> value.getType() == MatchEventType.BARON).count();
        long elders = timeline.getEvents().stream().filter(value -> value.getType() == MatchEventType.ELDER).count();
        return end.getBlueDragons() + ":" + end.getRedDragons() + ":"
                + end.isBlueHasDragonSoul() + ":" + end.isRedHasDragonSoul() + ":"
                + barons + ":" + elders;
    }

    private int firstPublicDivergence(MatchTimeline first, MatchTimeline second) {
        if (provenance.timelineHash(first).equals(provenance.timelineHash(second))) return -1;
        int events = Math.min(first.getEvents().size(), second.getEvents().size());
        for (int index = 0; index < events; index++) {
            MatchEvent a = first.getEvents().get(index);
            MatchEvent b = second.getEvents().get(index);
            if (!eventIdentity(a).equals(eventIdentity(b))) return Math.min(a.getTimeSeconds(), b.getTimeSeconds());
        }
        if (first.getEvents().size() != second.getEvents().size()) {
            return (first.getEvents().size() > events ? first.getEvents().get(events)
                    : second.getEvents().get(events)).getTimeSeconds();
        }
        return Math.min(first.getDurationSeconds(), second.getDurationSeconds());
    }

    private static String eventIdentity(MatchEvent value) {
        return value.getTimeSeconds() + "|" + value.getType() + "|" + value.getActionId()
                + "|" + value.getParentActionId() + "|" + value.getKillerPlayerId()
                + "|" + value.getVictimPlayerId() + "|" + value.getAssistPlayerIds()
                + "|" + value.getCombatSource() + "|" + value.getStructureAction();
    }

    private FixedDraftRow fixedDraft(
            MatchEngineV9RequalificationContract.Fixture fixture,
            RealDraftMatchResult prepared,
            MatchRow row
    ) {
        FinalDraftResult draft = prepared.draftResult();
        return new FixedDraftRow(fixture.fixtureId(), fixture.seriesGameNumber(),
                row.rosterIdentityHash(), row.draftDecisionHash(), row.fixedDraftHash(),
                row.finalAssignmentHash(), draft.blueBans().stream().map(value -> value.value()).toList(),
                draft.redBans().stream().map(value -> value.value()).toList(),
                draft.bluePicks().stream().map(value -> value.value()).toList(),
                draft.redPicks().stream().map(value -> value.value()).toList(),
                draft.blueFinalRoleAssignments().entrySet().stream()
                        .sorted(Map.Entry.comparingByValue()).map(value -> value.getValue() + "=" + value.getKey().value()).toList(),
                draft.redFinalRoleAssignments().entrySet().stream()
                        .sorted(Map.Entry.comparingByValue()).map(value -> value.getValue() + "=" + value.getKey().value()).toList(),
                prepared.hardFearlessExclusionsBeforeDraft().stream().map(value -> value.value()).sorted().toList());
    }

    private Binding requireBinding(Path backendRoot, Path output) throws Exception {
        Path contractPath = output.resolve("contract.json");
        Path hashPath = output.resolve("contract.sha256");
        if (!Files.isRegularFile(contractPath) || !Files.isRegularFile(hashPath)) {
            throw new IllegalStateException("V9 contract must be frozen first");
        }
        String contractHash = Files.readString(hashPath, StandardCharsets.UTF_8).trim().split("\\s+")[0];
        if (!fileHash(contractPath).equals(contractHash)) {
            throw new IllegalStateException("Frozen V9 contract bytes changed");
        }
        SourceIdentity current = sourceIdentity(backendRoot);
        var node = mapper.readTree(contractPath.toFile());
        if (!node.path("sourceIdentity").path("productionSourceTree").path("hash").asText()
                .equals(current.productionSourceTree().hash())
                || !node.path("sourceIdentity").path("harnessSourceTree").path("hash").asText()
                .equals(current.harnessSourceTree().hash())
                || !node.path("scheduleHash").asText().equals(
                        MatchEngineV9RequalificationContract.schedule().scheduleHash())) {
            throw new IllegalStateException("Current source/schedule differs from frozen V9 contract");
        }
        return new Binding(contractHash, current);
    }

    private SourceIdentity sourceIdentity(Path backendRoot) throws Exception {
        var production = Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot);
        var harness = Phase13GB1AuditArtifactWriter.phaseTestSourceTree(
                backendRoot, "MatchEngineV9Requalification");
        Path gitRoot = backendRoot.toAbsolutePath().normalize().getParent();
        return new SourceIdentity(
                git(gitRoot, "rev-parse", "HEAD"),
                git(gitRoot, "status", "--short"), production, harness,
                SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION,
                MatchEngineV1Policy.authoritative().policyHash());
    }

    private static String git(Path root, String... args) throws Exception {
        ArrayList<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) throw new IllegalStateException("git identity failed: " + output);
        return output;
    }

    private Map<String, Object> profileBindings() {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (var profile : MatchEngineV9RequalificationContract.PROFILES) {
            values.put(profile.name(), SimulationRuntimeProfiles.resolve(profile));
        }
        return values;
    }

    private FixtureCheckpoint readCheckpoint(
            Path path, Binding binding,
            MatchEngineV9RequalificationContract.Fixture fixture,
            MatchEngineV9RequalificationContract.SampleLane lane
    ) throws Exception {
        requireSidecar(path);
        FixtureCheckpoint value = canonical.readValue(path.toFile(), FixtureCheckpoint.class);
        if (!CHECKPOINT_SCHEMA.equals(value.schemaVersion())
                || !binding.contractHash().equals(value.contractHash())
                || !binding.sourceIdentity().harnessSourceTree().hash().equals(value.harnessSourceHash())
                || !fixture.fixtureId().equals(value.fixtureId()) || lane != value.sampleLane()
                || !value.replayExact()) {
            throw new IllegalStateException("Stale or relabelled V9 checkpoint: " + path);
        }
        return value;
    }

    private List<FixtureCheckpoint> readAllCheckpoints(
            Path output, Binding binding,
            MatchEngineV9RequalificationContract.SampleLane lane
    ) throws Exception {
        ArrayList<FixtureCheckpoint> result = new ArrayList<>();
        List<MatchEngineV9RequalificationContract.Fixture> fixtures =
                MatchEngineV9RequalificationContract.schedule().fixtures();
        for (int index = 0; index < fixtures.size(); index++) {
            var fixture = fixtures.get(index);
            Path path = checkpointDirectory(output, lane).resolve(String.format(Locale.ROOT,
                    "%03d-%s.json", index, fixture.fixtureId()));
            result.add(readCheckpoint(path, binding, fixture, lane));
        }
        return List.copyOf(result);
    }

    private void writeCheckpoint(Path path, FixtureCheckpoint checkpoint) throws Exception {
        writeFrozen(path, canonicalBytes(checkpoint));
        writeFrozen(sidecar(path), (fileHash(path) + "  " + path.getFileName() + "\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private void requireSidecar(Path path) throws IOException {
        Path sidecar = sidecar(path);
        if (!Files.isRegularFile(path) || !Files.isRegularFile(sidecar)) {
            throw new IllegalStateException("Missing fixture checkpoint payload/receipt");
        }
        String expected = Files.readString(sidecar, StandardCharsets.UTF_8).trim().split("\\s+")[0];
        if (!expected.equals(fileHash(path))) throw new IllegalStateException("Checkpoint digest mismatch");
    }

    private static Path sidecar(Path path) {
        return path.resolveSibling(path.getFileName() + ".sha256");
    }

    private static Path checkpointDirectory(
            Path output, MatchEngineV9RequalificationContract.SampleLane lane) {
        return output.resolve("checkpoints").resolve(lane.name().toLowerCase(Locale.ROOT));
    }

    private void requireHoldoutAuthorized(Path output, Binding binding) throws Exception {
        Path path = output.resolve("holdout-authorization.json");
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Holdout not authorized");
        HoldoutAuthorization authorization = canonical.readValue(path.toFile(), HoldoutAuthorization.class);
        if (!authorization.contractHash().equals(binding.contractHash())
                || !authorization.harnessSourceHash().equals(
                        binding.sourceIdentity().harnessSourceTree().hash())
                || !authorization.frozenGates().equals(MatchEngineV9RequalificationContract.GATES)) {
            throw new IllegalStateException("Holdout authorization binding mismatch");
        }
    }

    private static Sensitivity sensitivity(List<MatchRow> rows) {
        return MatchEngineV9RequalificationArtifactWriter.sensitivity(rows);
    }

    private static ExactIntegrity exactIntegrity(List<MatchRow> rows) {
        return MatchEngineV9RequalificationArtifactWriter.exactIntegrity(rows);
    }

    private String scheduleCsv(MatchEngineV9RequalificationContract.Schedule schedule) {
        StringBuilder value = new StringBuilder(
                "fixture_id,fixture_lane,pair_id,blue_team,red_team,series_game,sample_lane,seed_index,seed\n");
        for (var fixture : schedule.fixtures()) {
            appendScheduleRows(value, fixture, MatchEngineV9RequalificationContract.SampleLane.CALIBRATION,
                    fixture.calibrationSeeds());
            appendScheduleRows(value, fixture, MatchEngineV9RequalificationContract.SampleLane.HOLDOUT,
                    fixture.holdoutSeeds());
        }
        return value.toString();
    }

    private static void appendScheduleRows(
            StringBuilder target, MatchEngineV9RequalificationContract.Fixture fixture,
            MatchEngineV9RequalificationContract.SampleLane lane, List<Long> seeds) {
        for (int index = 0; index < seeds.size(); index++) {
            target.append(fixture.fixtureId()).append(',').append(fixture.fixtureLane()).append(',')
                    .append(fixture.pairId()).append(',').append(fixture.blueTeamCode()).append(',')
                    .append(fixture.redTeamCode()).append(',').append(fixture.seriesGameNumber()).append(',')
                    .append(lane).append(',').append(index).append(',').append(seeds.get(index)).append('\n');
        }
    }

    private byte[] canonicalBytes(Object value) throws IOException {
        byte[] raw = canonical.writeValueAsBytes(value);
        byte[] withNewline = java.util.Arrays.copyOf(raw, raw.length + 1);
        withNewline[raw.length] = '\n';
        return withNewline;
    }

    private static void writeFrozen(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.exists(path)) {
            if (!java.util.Arrays.equals(Files.readAllBytes(path), bytes)) {
                throw new IllegalStateException("Frozen artifact already exists with different bytes: " + path);
            }
            return;
        }
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        Files.write(temporary, bytes);
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
    }

    static void writeReplace(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        Files.write(temporary, bytes);
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static String fileHash(Path path) throws IOException {
        return MatchEngineV9RequalificationContract.sha256(Files.readAllBytes(path));
    }

    private static String workerJvmIdentity() {
        return MatchEngineV9RequalificationContract.sha256(
                "java.version=" + System.getProperty("java.version") + '\n'
                        + "java.vendor=" + System.getProperty("java.vendor") + '\n'
                        + "os.name=" + System.getProperty("os.name") + '\n'
                        + "os.arch=" + System.getProperty("os.arch") + '\n');
    }

    private static void requireTreeByteEquality(Path first, Path second) throws IOException {
        List<Path> firstFiles;
        List<Path> secondFiles;
        try (var files = Files.walk(first)) {
            firstFiles = files.filter(Files::isRegularFile).map(first::relativize).sorted().toList();
        }
        try (var files = Files.walk(second)) {
            secondFiles = files.filter(Files::isRegularFile).map(second::relativize).sorted().toList();
        }
        if (!firstFiles.equals(secondFiles)) throw new IllegalStateException("Fresh JVM file sets differ");
        for (Path relative : firstFiles) {
            if (!java.util.Arrays.equals(Files.readAllBytes(first.resolve(relative)),
                    Files.readAllBytes(second.resolve(relative)))) {
                throw new IllegalStateException("Fresh JVM artifact bytes differ: " + relative);
            }
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else if (Files.exists(destination)) {
                    if (!java.util.Arrays.equals(Files.readAllBytes(path), Files.readAllBytes(destination))) {
                        throw new IllegalStateException("Official artifact conflicts with frozen root: " + destination);
                    }
                } else Files.copy(path, destination);
            }
        }
    }

    public record Binding(String contractHash, SourceIdentity sourceIdentity) { }
    public record SourceIdentity(
            String gitHead, String workingTreeStatus,
            Phase13GB1AuditArtifactWriter.SourceTreeIdentity productionSourceTree,
            Phase13GB1AuditArtifactWriter.SourceTreeIdentity harnessSourceTree,
            String engineImplementationVersion, String productionPolicyHash) { }
    public record IdentityArtifact(
            SourceIdentity sourceIdentity, SimulationResourceProvenance resourceProvenance,
            MatchEngineV1Policy.Snapshot productionPolicy, Map<String, Object> profiles,
            String playerIdentityVersion, String playerIdentityResourceHash,
            int stablePlayerCount) { }
    public record FreezeResult(
            String contractHash, String scheduleHash, SourceIdentity sourceIdentity,
            MatchEngineV9RequalificationContract.SeedOverlapAudit seedOverlapAudit) { }
    public record SmokeResult(
            String contractHash, int matchRows, boolean replayExact,
            boolean instrumentationExact, boolean matchupReachable,
            boolean compositionReachable, boolean fixedDraftReuseExact,
            long integrityErrorCount) {
        public boolean clean() {
            return replayExact && instrumentationExact && matchupReachable
                    && compositionReachable && fixedDraftReuseExact && integrityErrorCount == 0;
        }
    }
    public record ShardResult(
            MatchEngineV9RequalificationContract.SampleLane lane, int shardIndex,
            int fixtureCount, int rowCount, String workerJvmIdentityHash) { }
    public record WorkerReceipt(
            String schemaVersion, String contractHash, String harnessSourceHash,
            MatchEngineV9RequalificationContract.SampleLane sampleLane,
            int shardIndex, int shardCount, String workerJvmIdentityHash,
            int fixtureCount, int rowCount, List<String> checkpointPayloadHashes) { }
    public record HoldoutAuthorization(
            String schemaVersion, String contractHash, String harnessSourceHash,
            String scheduleHash, String calibrationReviewHash,
            MatchEngineV9RequalificationContract.AcceptanceGates frozenGates,
            String status) { }
    public record CalibrationReview(
            String schemaVersion, String contractHash, int fixtureCount,
            int seedsPerFixture, int profileCount, int matchRowCount,
            Sensitivity observedSensitivity, ExactIntegrity exactIntegrity,
            String gatePolicy) { }
    public record Executed(
            SimulationRuntimeProfileId profileId,
            Phase13GB1SimulationExecutor.Execution execution,
            SimulationExecutionProvenance provenance) { }
    public record FixtureCheckpoint(
            String schemaVersion, String contractHash, String harnessSourceHash,
            int fixtureIndex, String fixtureId,
            MatchEngineV9RequalificationContract.SampleLane sampleLane,
            String workerJvmIdentityHash, FixedDraftRow fixedDraft,
            boolean replayExact, List<MatchRow> rows) {
        public FixtureCheckpoint { rows = List.copyOf(rows); }
    }
    public record FixedDraftRow(
            String fixtureId, int seriesGameNumber, String rosterIdentityHash,
            String draftDecisionHash, String finalDraftHash, String finalAssignmentHash,
            List<String> blueBans, List<String> redBans,
            List<String> bluePicks, List<String> redPicks,
            List<String> blueAssignments, List<String> redAssignments,
            List<String> hardFearlessExclusionsBeforeDraft) { }
    public record TimelineIntegrity(
            int invalidStructureState, int nexusDestroyedWithTurretAlive,
            int postFinishMutationOrEvent, int supportFarmCsErrors) {
        public long errorCount() {
            return (long) invalidStructureState + nexusDestroyedWithTurretAlive
                    + postFinishMutationOrEvent + supportFarmCsErrors;
        }
    }
    public record StructureMetrics(
            String signature, int firstTowerSeconds, String firstTowerSource,
            String firstTowerLane, int damageEvents, int destroyedEvents,
            int siegeStarted, int siegeStopped, int baseDefenseApplications) { }
    public record MatchRow(
            String schemaVersion, String fixtureId,
            Phase13GB1AuditSchedule.FixtureLane fixtureLane, String pairId,
            String blueTeamCode, String redTeamCode, int seriesGameNumber,
            MatchEngineV9RequalificationContract.SampleLane sampleLane,
            int seedIndex, long seed, int profileIndex,
            SimulationRuntimeProfileId profileId, String configurationHash,
            String activeGameplayRulesVersion, String engineImplementationVersion,
            String resourceProvenanceHash, String rosterIdentityHash,
            String draftDecisionHash, String fixedDraftHash, String finalAssignmentHash,
            String replayProvenanceHash, String timelineHash,
            long randomDrawCount, String randomTraceHash,
            String winnerTeamCode, TeamSide winnerSide, GameEndReason endReason,
            int durationSeconds, int blueKills, int redKills, int blueGold, int redGold,
            int blueDragons, int redDragons, int blueTowers, int redTowers,
            String objectiveSignature, String structureSignature,
            int firstTowerSeconds, String firstTowerSource, String firstTowerLane,
            int structureDamageEvents, int structureDestroyedEvents,
            int siegeStarted, int siegeStopped, int baseDefenseApplications,
            int matchupApplications, double matchupEdgeSum, int matchupDirectRandomCalls,
            boolean compositionProfileInitialized, int compositionActualAttempts,
            int compositionApplications, int compositionNonZeroModifiers,
            int compositionDirectRandomCalls, int compositionRandomDraws,
            Map<String, Integer> compositionApplicationsByContext,
            int compositionLocalChangeCount, int firstCompositionLocalChangeSeconds,
            int firstPublicDivergenceSeconds, int localCauseViolations,
            long integrityErrorCount, int invalidStructureState,
            int nexusDestroyedWithTurretAlive, int postFinishMutationOrEvent,
            int supportFarmCsErrors, String structuredDiagnosticsHash) {
        public MatchRow {
            compositionApplicationsByContext = Map.copyOf(compositionApplicationsByContext);
        }
    }
    public record Sensitivity(
            Marginal matchupMinusBaseline, Marginal fullMinusMatchup) { }
    public record Marginal(
            int pairCount, double blueWinRateDeltaPercentagePoints,
            double pairedWinnerChangedRatePercent, double objectiveChangedRatePercent,
            double structureChangedRatePercent, double meanDurationDeltaSeconds,
            double p95DurationDeltaSeconds, boolean macroSafetyPass) { }
    public record ExactIntegrity(
            int rowCount, long timeoutCount, long integrityErrorCount,
            long invalidStructureStateCount, long nexusWithTurretCount,
            long postFinishMutationCount, long supportFarmCsErrorCount,
            long matchupDirectRandomCalls, long compositionDirectRandomCalls,
            long compositionRandomDraws, boolean pass) { }
    public record ArtifactResult(
            String schemaVersion, int calibrationRows, int holdoutRows,
            String baselineStatus, String matchupStatus, String compositionStatus,
            String recommendation, String recommendationReason,
            Sensitivity calibrationSensitivity, Sensitivity holdoutSensitivity,
            ExactIntegrity exactIntegrity, String manifestHash) { }
}
