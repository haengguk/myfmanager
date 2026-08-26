package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupApplicationProvenance;
import com.lolfm.champion.ChampionMatchupExecutionStatsSnapshot;
import com.lolfm.composition.CompositionApplicationProvenance;
import com.lolfm.composition.CompositionRuntimeDiagnostics;
import com.lolfm.composition.TeamCompositionContext;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Position;
import com.lolfm.domain.StructureStateSnapshot;
import com.lolfm.draft.DraftSelectionTraceHasher;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.Phase13GB1SimulationExecutor;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import com.lolfm.simulator.TeamSide;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** Authenticated, bounded execution lifecycle for the fresh Auto Draft V9 audit. */
public final class MatchEngineV9FreshRequalificationRunner {
    public static final Path OUTPUT = Path.of("build", "reports",
            "match-engine-v9-auto-draft-matchup-composition-fresh-requalification-v2");
    public static final int SHARD_COUNT = 4;
    private static final String CHECKPOINT_SCHEMA =
            "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_FIXTURE_CHECKPOINT_V2";
    private static final String ROW_SCHEMA =
            "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_MATCH_ROW_V2";
    private static final String FULL_RECEIPT_SCHEMA =
            "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_FULL_REGRESSION_RECEIPT_V2";

    private final ObjectMapper mapper;
    private final ObjectMapper canonical;
    private final FreshAutoDraftRealMatchHarness harness;
    private final SimulationProvenanceService provenance;
    private final PlayerIdentityCatalog identities;
    private long gameplayExecutionCount;

    public MatchEngineV9FreshRequalificationRunner(
            ObjectMapper mapper,
            ChampionCatalog champions,
            LckTeamAssembler teams,
            RealDraftMatchPreflightValidator preflight,
            MatchEngineV1InputFactory inputs,
            ConfiguredMatchSimulatorFactory simulators,
            PlayerIdentityCatalog identities,
            PlayerRatingCatalog ratings,
            ChampionProficiencyCatalog proficiencies
    ) {
        this.mapper = Objects.requireNonNull(mapper);
        canonical = mapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.INDENT_OUTPUT);
        this.identities = Objects.requireNonNull(identities);
        harness = new FreshAutoDraftRealMatchHarness(mapper, champions, teams, preflight,
                inputs, simulators, identities, ratings, proficiencies);
        provenance = harness.provenance();
    }

    public FreezeResult freeze(Path backendRoot, Path output) throws Exception {
        var schedule = MatchEngineV9FreshRequalificationContract.requireFrozen(
                MatchEngineV9FreshRequalificationContract.schedule());
        var ledger = MatchEngineV9ConsumedSeedLedger.create(mapper, backendRoot);
        if (!ledger.complete()) throw new IllegalStateException("Consumed-seed ledger incomplete");
        var overlap = MatchEngineV9FreshRequalificationContract.requireNoSeedOverlap(
                schedule, ledger.seedSet());
        SourceIdentity identity = sourceIdentity(backendRoot);
        Files.createDirectories(output);
        writeFrozen(output.resolve("consumed-seed-ledger.json"), canonicalBytes(ledger));
        String ledgerHash = fileHash(output.resolve("consumed-seed-ledger.json"));
        LinkedHashMap<String, Object> contract = new LinkedHashMap<>();
        contract.put("schemaVersion", MatchEngineV9FreshRequalificationContract.CONTRACT_SCHEMA);
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
        contract.put("draftSelectionPolicyId",
                com.lolfm.draft.AutoDraftSelectionPolicy.production().policyId());
        contract.put("draftSelectionPolicyHash",
                com.lolfm.draft.AutoDraftSelectionPolicy.production().policyHash());
        contract.put("draftSelectionTraceSchema", "AUTO_DRAFT_SELECTION_TRACE_V2");
        contract.put("draftSelectionTraceHashAlgorithm",
                DraftSelectionTraceHasher.TRACE_HASH_ALGORITHM);
        contract.put("scheduleHash", schedule.scheduleHash());
        contract.put("seedLedgerHash", ledgerHash);
        contract.put("seedNamespace", schedule.seedNamespace());
        contract.put("seedBindingHash", schedule.seedBindingHash());
        contract.put("draftReusePolicy", schedule.draftReusePolicy());
        contract.put("fixtureCount", MatchEngineV9FreshRequalificationContract.EXPECTED_FIXTURES);
        contract.put("calibrationSeedsPerFixture", 4);
        contract.put("holdoutSeedsPerFixture", 4);
        contract.put("productionAutoDraftCount", 800);
        contract.put("coreSimulationCount", 2_400);
        contract.put("marginalPairCount", 1_600);
        contract.put("replayCheckCount", 300);
        contract.put("instrumentationCheckCount", 300);
        contract.put("maximumOfficialSimulationCount", 3_000);
        contract.put("acceptanceGates", MatchEngineV9FreshRequalificationContract.GATES);
        contract.put("correctnessExactZeroGates", List.of(
                "TIMEOUT_0", "DOMAIN_STRUCTURED_DIAGNOSTICS_INTEGRITY_ERROR_0",
                "DUPLICATE_MUTATION_REWARD_DEATH_EVENT_0", "INVALID_STRUCTURE_HP_STATE_0",
                "NEXUS_ORDERING_ERROR_0", "POST_FINISH_MUTATION_EVENT_0",
                "SUPPORT_FARM_CS_0", "STALE_PARTICIPANT_ASSIGNMENT_ERROR_0",
                "DIRECT_FEATURE_RANDOM_0", "SAME_PROFILE_REPLAY_MISMATCH_0",
                "INSTRUMENTATION_GAMEPLAY_RANDOM_MISMATCH_0",
                "DRAFT_PROFILE_INPUT_PROVENANCE_BINDING_MISMATCH_0",
                "CROSS_MATCH_MUTABLE_STATE_LEAK_0"));
        contract.put("matchupCausalGates", List.of(
                "GEOMETRIC_V2_NON_ZERO_CONSUMED_APPLICATION_GT_0",
                "ALL_FIVE_POSITIONS_STRUCTURED_COVERAGE",
                "BASELINE_APPLICATION_EXACT_ZERO", "PAIR_PARTICIPANT_PERSPECTIVE_ERROR_0",
                "DUPLICATE_CONFLICTING_UNBOUND_APPLICATION_0", "DIRECT_RANDOM_0",
                "UNRESOLVED_SNAPSHOT_CAUSE_0",
                "UNEXPLAINED_PUBLIC_DIVERGENCE_0",
                "DIRECT_OBJECTIVE_STRUCTURE_MUTATION_0"));
        contract.put("compositionCausalGates", List.of(
                "FULL_INITIALIZED_ACTUAL_MAPPED_GT_0",
                "SCALAR_CALCULATED_APPLIED_CONSUMED_EXACT",
                "NON_ZERO_SCALAR_APPLICATION_GT_0", "NON_SCALAR_SEPARATELY_DECOMPOSED",
                "SKIRMISH_TEAMFIGHT_SIEGE_BASE_DEFENSE_REACHABLE",
                "OBJECTIVE_SETUP_SCALAR_APPLICATION_0",
                "MATCHUP_ONLY_APPLICATION_EXACT_ZERO",
                "DUPLICATE_CONFLICTING_ORIENTATION_DECOMPOSITION_ERROR_0",
                "DIRECT_RANDOM_0", "PUBLIC_DIVERGENCE_DIRECT_CAUSE_COVERAGE_100_PERCENT"));
        contract.put("structureSeverity", List.of("EXACT", "HP_ONLY",
                "LANE_TOWER_PROGRESSION", "INHIBITOR_PROGRESSION",
                "NEXUS_TURRET_PROGRESSION", "NEXUS_OR_ENDING"));
        contract.put("automaticHoldoutWhenCalibrationOperationalGateClean", true);
        contract.put("productionActivation", false);
        contract.put("sourceIdentity", identity);
        byte[] bytes = canonicalBytes(contract);
        writeFrozen(output.resolve("contract.json"), bytes);
        String contractHash = MatchEngineV9FreshRequalificationContract.sha256(bytes);
        writeFrozen(output.resolve("contract.sha256"),
                (contractHash + "  contract.json\n").getBytes(StandardCharsets.UTF_8));
        writeFrozen(output.resolve("frozen-schedule.json"), canonicalBytes(schedule));
        writeFrozen(output.resolve("frozen-schedule.csv"), scheduleCsv(schedule)
                .getBytes(StandardCharsets.UTF_8));
        writeFrozen(output.resolve("seed-overlap-audit.json"), canonicalBytes(overlap));
        writeFrozen(output.resolve("source-resource-runtime-identity.json"), canonicalBytes(Map.of(
                "sourceIdentity", identity,
                "resourceProvenance", provenance.resourceProvenance(),
                "productionPolicy", MatchEngineV1Policy.authoritative(),
                "profiles", profileBindings(),
                "stablePlayerCount", identities.all().size())));
        return new FreezeResult(contractHash, schedule.scheduleHash(), ledgerHash, identity, overlap);
    }

    public FullRegressionReceipt recordFullRegressionReceipt(
            Path backendRoot, Path output, int tests, int failures, int errors,
            int skipped, long durationMillis
    ) throws Exception {
        Binding binding = requireBinding(backendRoot, output, false);
        if (tests <= 0 || failures != 0 || errors != 0) {
            throw new IllegalArgumentException("Full backend regression was not clean");
        }
        FullRegressionReceipt receipt = new FullRegressionReceipt(
                FULL_RECEIPT_SCHEMA, binding.contractHash(),
                binding.sourceIdentity().combinedSourceHash(), tests, failures, errors,
                skipped, durationMillis, Instant.now().toString(), true);
        writeFrozen(output.resolve("full-regression-receipt.json"), canonicalBytes(receipt));
        return receipt;
    }

    public SmokeResult smoke(Path backendRoot, Path output) throws Exception {
        String contractHash = Files.isRegularFile(output.resolve("contract.json"))
                ? requireBinding(backendRoot, output, false).contractHash()
                : "PRE_FREEZE_SOURCE:"
                + sourceIdentity(backendRoot).combinedSourceHash();
        List<MatchEngineV9FreshRequalificationContract.Fixture> fixtures = List.of(
                MatchEngineV9FreshRequalificationContract.schedule().fixtures().get(2),
                MatchEngineV9FreshRequalificationContract.schedule().fixtures().get(90));
        int rows = 0;
        int drafts = 0;
        boolean replay = true;
        boolean instrumentation = true;
        boolean bindingExact = true;
        boolean matchupReachable = false;
        boolean compositionReachable = false;
        boolean finalizerTransformsExact = true;
        long errors = 0;
        ArrayList<MatchRow> artifactRows = new ArrayList<>(6);
        ArrayList<PairObservation> artifactPairs = new ArrayList<>(4);
        for (var fixture : fixtures) {
            var prepared = harness.prepare(fixture,
                    MatchEngineV9FreshRequalificationContract.dryRunSeed(fixture));
            drafts += prepared.targetProductionAutoDraftCount();
            var runs = harness.executeProfiles(prepared);
            rows += runs.size();
            ArrayList<MatchRow> fixtureRows = new ArrayList<>(runs.size());
            for (var run : runs) {
                MatchRow smokeRow = toRow(fixture,
                        MatchEngineV9FreshRequalificationContract.SampleLane.DRY_RUN,
                        0, MatchEngineV9FreshRequalificationContract.PROFILES
                                .indexOf(run.profileId()), run);
                fixtureRows.add(smokeRow);
                artifactRows.add(smokeRow);
                if (!smokeRow.payloadDigest().equals(rowPayloadDigest(
                        withPayloadDigest(smokeRow, "UNSIGNED")))) {
                    bindingExact = false;
                }
                var repeated = harness.execute(prepared, run.profileId());
                replay &= exact(run, repeated);
                var disabled = harness.executeInstrumentationDisabled(prepared, run.profileId());
                instrumentation &= provenance.timelineHash(disabled.timeline()).equals(
                        run.provenance().timelineHash())
                        && disabled.randomFingerprint().equals(run.execution().randomFingerprint());
                errors += integrity(run).errorCount();
            }
            artifactPairs.add(pairObservation(fixtureRows.get(0), fixtureRows.get(1),
                    runs.get(0).execution().timeline(), runs.get(1).execution().timeline(),
                    MarginalKind.MATCHUP_MINUS_BASELINE));
            artifactPairs.add(pairObservation(fixtureRows.get(1), fixtureRows.get(2),
                    runs.get(1).execution().timeline(), runs.get(2).execution().timeline(),
                    MarginalKind.FULL_MINUS_MATCHUP));
            bindingExact &= runs.stream().map(value -> value.prepared().input().inputHash())
                    .distinct().count() == 1;
            matchupReachable |= runs.stream()
                    .filter(value -> value.profileId()
                            == SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1)
                    .anyMatch(value -> value.execution().structuredDiagnostics().championMatchup()
                            .nonZeroConsumedApplicationCount() > 0);
            compositionReachable |= runs.stream()
                    .filter(value -> value.profileId()
                            == SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1)
                    .anyMatch(value -> value.execution().structuredDiagnostics().composition()
                            .modifierConsumedCount() > 0);
        }
        try {
            String paired = pairedCsv(artifactPairs);
            String segmented = segmentedSensitivity(artifactPairs);
            Map<String, Map<String, Long>> severity =
                    structureSeveritySummary(artifactPairs);
            Sensitivity sensitivity = sensitivity(artifactPairs);
            finalizerTransformsExact = artifactRows.size() == rows
                    && artifactPairs.size() == 4
                    && paired.lines().count() == artifactPairs.size() + 1L
                    && segmented.lines().count() == artifactPairs.size() + 1L
                    && severity.size() == MarginalKind.values().length
                    && sensitivity.matchupMinusBaseline().pairCount() == 2
                    && sensitivity.fullMinusMatchup().pairCount() == 2;
        } catch (RuntimeException exception) {
            finalizerTransformsExact = false;
        }
        SmokeResult result = new SmokeResult(contractHash, fixtures.size(), drafts,
                rows, replay, instrumentation, bindingExact, matchupReachable,
                compositionReachable, finalizerTransformsExact, errors);
        if (!result.clean()) throw new IllegalStateException("Fresh smoke gate failed: " + result);
        writeReplace(output.resolve("smoke-review.json"), canonicalBytes(result));
        return result;
    }

    public ShardResult runShard(
            Path backendRoot,
            Path output,
            MatchEngineV9FreshRequalificationContract.SampleLane lane,
            int shardIndex
    ) throws Exception {
        if (lane == MatchEngineV9FreshRequalificationContract.SampleLane.DRY_RUN
                || shardIndex < 0 || shardIndex >= SHARD_COUNT) {
            throw new IllegalArgumentException("Invalid official worker lane/shard");
        }
        Binding binding = requireBinding(backendRoot, output, true);
        Path receiptPath = output.resolve("worker-receipts").resolve(
                lane.name().toLowerCase(Locale.ROOT) + "-shard-" + shardIndex + ".json");
        if (Files.isRegularFile(receiptPath)) {
            WorkerReceipt receipt = canonical.readValue(receiptPath.toFile(), WorkerReceipt.class);
            if (!"MATCH_ENGINE_V9_FRESH_REQUALIFICATION_WORKER_RECEIPT_V2"
                    .equals(receipt.schemaVersion())
                    || !receipt.contractHash().equals(binding.contractHash())
                    || !receipt.combinedSourceHash().equals(
                    binding.sourceIdentity().combinedSourceHash())
                    || receipt.sampleLane() != lane || receipt.shardIndex() != shardIndex
                    || receipt.shardCount() != SHARD_COUNT || receipt.fixtureCount() != 25) {
                throw new IllegalStateException(
                        "Existing worker receipt has stale or conflicting ownership: " + receiptPath);
            }
            return new ShardResult(lane, shardIndex, receipt.fixtureCount(),
                    receipt.draftCount(), receipt.rowCount(), receipt.replayCheckCount(),
                    receipt.instrumentationCheckCount(), receipt.workerJvmIdentityHash());
        }
        if (lane == MatchEngineV9FreshRequalificationContract.SampleLane.HOLDOUT) {
            requireAndStartHoldout(output, binding);
            if (Files.exists(output.resolve("holdout-completion-receipt.json"))) {
                throw new IllegalStateException("Frozen holdout has already completed");
            }
        }
        Path directory = checkpointDirectory(output, lane);
        Files.createDirectories(directory);
        int fixtureCount = 0;
        int rowCount = 0;
        int draftCount = 0;
        int replayChecks = 0;
        int instrumentationChecks = 0;
        ArrayList<String> checkpointHashes = new ArrayList<>();
        var fixtures = MatchEngineV9FreshRequalificationContract.schedule().fixtures();
        for (int fixtureIndex = shardIndex; fixtureIndex < fixtures.size();
             fixtureIndex += SHARD_COUNT) {
            var fixture = fixtures.get(fixtureIndex);
            Path path = directory.resolve(String.format(Locale.ROOT,
                    "%03d-%s.json", fixtureIndex, fixture.fixtureId()));
            FixtureCheckpoint checkpoint;
            if (Files.isRegularFile(path)) {
                checkpoint = readCheckpoint(path, binding, fixture, lane);
            } else {
                checkpoint = executeFixture(binding, fixtureIndex, fixture, lane);
                writeCheckpoint(path, checkpoint);
            }
            fixtureCount++;
            rowCount += checkpoint.rows().size();
            draftCount += checkpoint.drafts().size();
            replayChecks += checkpoint.replayChecks().size();
            instrumentationChecks += checkpoint.instrumentationChecks().size();
            checkpointHashes.add(fileHash(path));
            System.out.printf(Locale.ROOT,
                    "FRESH_V9 %s shard %d/%d fixture %d/100 %s drafts=%d rows=%d%n",
                    lane, shardIndex + 1, SHARD_COUNT, fixtureIndex + 1,
                    fixture.fixtureId(), checkpoint.drafts().size(), checkpoint.rows().size());
        }
        WorkerReceipt receipt = new WorkerReceipt(
                "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_WORKER_RECEIPT_V2",
                binding.contractHash(), binding.sourceIdentity().combinedSourceHash(),
                lane, shardIndex, SHARD_COUNT, workerJvmIdentity(), fixtureCount,
                draftCount, rowCount, replayChecks, instrumentationChecks,
                List.copyOf(checkpointHashes));
        Files.createDirectories(receiptPath.getParent());
        writeFrozen(receiptPath, canonicalBytes(receipt));
        return new ShardResult(lane, shardIndex, fixtureCount, draftCount, rowCount,
                replayChecks, instrumentationChecks, receipt.workerJvmIdentityHash());
    }

    public CalibrationReview finalizeCalibration(Path backendRoot, Path output) throws Exception {
        long executionsBefore = gameplayExecutionCount;
        Binding binding = requireBinding(backendRoot, output, true);
        List<FixtureCheckpoint> checkpoints = readAllCheckpoints(output, binding,
                MatchEngineV9FreshRequalificationContract.SampleLane.CALIBRATION);
        requireWorkerReceipts(output, binding,
                MatchEngineV9FreshRequalificationContract.SampleLane.CALIBRATION);
        Population population = population(checkpoints);
        if (population.rows().size()
                != MatchEngineV9FreshRequalificationContract.EXPECTED_CALIBRATION_ROWS
                || population.drafts().size() != 400
                || population.replayChecks().size() != 300
                || population.instrumentationChecks().size() != 300) {
            throw new IllegalStateException("Calibration coverage incomplete");
        }
        ExactIntegrity integrity = exactIntegrity(population);
        CausalGate matchup = matchupCausalGate(population);
        CausalGate composition = compositionCausalGate(population);
        boolean operationalClean = integrity.pass() && matchup.pass() && composition.pass()
                && population.replayChecks().stream().allMatch(ReplayCheck::exact)
                && population.instrumentationChecks().stream()
                .allMatch(InstrumentationCheck::exact);
        CalibrationReview review = new CalibrationReview(
                "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_CALIBRATION_REVIEW_V2",
                binding.contractHash(), 100, 4, 3, population.drafts().size(),
                population.rows().size(), population.pairs().size(),
                population.replayChecks().size(), population.instrumentationChecks().size(),
                integrity, matchup, composition, sensitivity(population.pairs()),
                operationalClean,
                "FROZEN_GATES_RETAINED_WITHOUT_TUNING_OR_SEED_FIXTURE_CHANGE");
        writeReplace(output.resolve("calibration-review.json"), canonicalBytes(review));
        writeFinalizerExecutionProof(output, "CALIBRATION", executionsBefore);
        if (!operationalClean) {
            writeReplace(output.resolve("calibration-operational-gate-failed.json"),
                    canonicalBytes(Map.of("contractHash", binding.contractHash(),
                            "holdoutAuthorized", false, "review", review)));
            return review;
        }
        HoldoutAuthorization authorization = new HoldoutAuthorization(
                "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_HOLDOUT_AUTHORIZATION_V2",
                binding.contractHash(), binding.sourceIdentity().combinedSourceHash(),
                MatchEngineV9FreshRequalificationContract.schedule().scheduleHash(),
                fileHash(output.resolve("consumed-seed-ledger.json")),
                fileHash(output.resolve("calibration-review.json")),
                MatchEngineV9FreshRequalificationContract.GATES,
                "AUTHORIZED", false);
        writeFrozen(output.resolve("holdout-authorization.authorized"),
                canonicalBytes(authorization));
        return review;
    }

    public FinalArtifactResult finalizeOfficial(Path backendRoot, Path output) throws Exception {
        long executionsBefore = gameplayExecutionCount;
        Binding binding = requireBinding(backendRoot, output, true);
        Path started = output.resolve("holdout-authorization.started");
        if (!Files.isRegularFile(started)) {
            throw new IllegalStateException("Holdout was not started from authorization");
        }
        List<FixtureCheckpoint> calibration = readAllCheckpoints(output, binding,
                MatchEngineV9FreshRequalificationContract.SampleLane.CALIBRATION);
        List<FixtureCheckpoint> holdout = readAllCheckpoints(output, binding,
                MatchEngineV9FreshRequalificationContract.SampleLane.HOLDOUT);
        requireWorkerReceipts(output, binding,
                MatchEngineV9FreshRequalificationContract.SampleLane.CALIBRATION);
        requireWorkerReceipts(output, binding,
                MatchEngineV9FreshRequalificationContract.SampleLane.HOLDOUT);
        Population calibrationPopulation = population(calibration);
        Population holdoutPopulation = population(holdout);
        Population all = combine(calibrationPopulation, holdoutPopulation);
        if (all.drafts().size() != MatchEngineV9FreshRequalificationContract.EXPECTED_DRAFTS
                || all.rows().size() != MatchEngineV9FreshRequalificationContract.EXPECTED_CORE_ROWS
                || all.pairs().size()
                != MatchEngineV9FreshRequalificationContract.EXPECTED_MARGINAL_PAIRS
                || calibrationPopulation.replayChecks().size() != 300
                || calibrationPopulation.instrumentationChecks().size() != 300
                || officialSimulationCount(all) >
                MatchEngineV9FreshRequalificationContract.MAX_OFFICIAL_SIMULATIONS) {
            throw new IllegalStateException("Official population count/budget mismatch");
        }
        ExactIntegrity integrity = exactIntegrity(all);
        CausalGate matchupCausal = matchupCausalGate(all);
        CausalGate compositionCausal = compositionCausalGate(all);
        Sensitivity calibrationSensitivity = sensitivity(calibrationPopulation.pairs());
        Sensitivity holdoutSensitivity = sensitivity(holdoutPopulation.pairs());
        Marginal matchupMacro = holdoutSensitivity.matchupMinusBaseline();
        Marginal compositionMacro = holdoutSensitivity.fullMinusMatchup();
        boolean baselineStable = integrity.pass()
                && all.rows().stream().filter(value -> value.profileId()
                == SimulationRuntimeProfileId.BASELINE_V1)
                .allMatch(value -> value.matchup().consumedApplicationCount() == 0
                        && value.composition().gameplayApplicationCount() == 0);
        boolean matchupEligible = baselineStable && matchupCausal.pass()
                && matchupMacro.macroSafetyPass();
        boolean compositionEligible = matchupEligible && compositionCausal.pass()
                && compositionMacro.macroSafetyPass();
        List<SimulationRuntimeProfileId> eligibleProfiles = new ArrayList<>();
        eligibleProfiles.add(SimulationRuntimeProfileId.BASELINE_V1);
        if (matchupEligible) eligibleProfiles.add(
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
        if (compositionEligible) eligibleProfiles.add(
                SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
        ProfileDecision decision = decide(baselineStable, matchupEligible,
                compositionEligible, eligibleProfiles);
        FinalArtifactResult result = new FinalArtifactResult(
                "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_FINAL_ARTIFACT_V2",
                binding.contractHash(), all.drafts().size(), all.rows().size(),
                all.pairs().size(), calibrationPopulation.replayChecks().size(),
                calibrationPopulation.instrumentationChecks().size(),
                officialSimulationCount(all), baselineStable,
                matchupCausal, compositionCausal, calibrationSensitivity,
                holdoutSensitivity, List.copyOf(eligibleProfiles), decision,
                "SEE_SHA256SUMS_TXT_RAW_SHA256");
        writeFinalArtifacts(output, binding, calibrationPopulation, holdoutPopulation,
                all, integrity, result);
        HoldoutCompletion completion = new HoldoutCompletion(
                "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_HOLDOUT_COMPLETION_V2",
                binding.contractHash(), 100, 400, 1_200, true,
                fileHash(output.resolve("final-recommendation.json")));
        writeFrozen(output.resolve("holdout-completion-receipt.json"),
                canonicalBytes(completion));
        writeFinalizerExecutionProof(output, "FINAL", executionsBefore);
        writeReplace(output.resolve("SHA256SUMS.txt"),
                recursiveManifest(output).getBytes(StandardCharsets.UTF_8));
        return result;
    }

    /** Artifact-only fresh-JVM finalizer; it never invokes Draft or Match simulation. */
    public FinalArtifactResult writeFreshJvmCandidate(
            Path backendRoot, Path output, Path candidate
    ) throws Exception {
        long executionsBefore = gameplayExecutionCount;
        Binding binding = requireBinding(backendRoot, output, true);
        if (!Files.isRegularFile(output.resolve("holdout-authorization.started"))) {
            throw new IllegalStateException("Holdout must be consumed before artifact finalization");
        }
        Population calibration = population(readAllCheckpoints(output, binding,
                MatchEngineV9FreshRequalificationContract.SampleLane.CALIBRATION));
        Population holdout = population(readAllCheckpoints(output, binding,
                MatchEngineV9FreshRequalificationContract.SampleLane.HOLDOUT));
        Population all = combine(calibration, holdout);
        ExactIntegrity integrity = exactIntegrity(all);
        CausalGate matchupCausal = matchupCausalGate(all);
        CausalGate compositionCausal = compositionCausalGate(all);
        Sensitivity calibrationSensitivity = sensitivity(calibration.pairs());
        Sensitivity holdoutSensitivity = sensitivity(holdout.pairs());
        boolean baselineStable = integrity.pass()
                && profile(all.rows(), SimulationRuntimeProfileId.BASELINE_V1).stream()
                .allMatch(value -> value.matchup().consumedApplicationCount() == 0
                        && value.composition().gameplayApplicationCount() == 0);
        boolean matchupEligible = baselineStable && matchupCausal.pass()
                && holdoutSensitivity.matchupMinusBaseline().macroSafetyPass();
        boolean compositionEligible = matchupEligible && compositionCausal.pass()
                && holdoutSensitivity.fullMinusMatchup().macroSafetyPass();
        ArrayList<SimulationRuntimeProfileId> eligible = new ArrayList<>();
        eligible.add(SimulationRuntimeProfileId.BASELINE_V1);
        if (matchupEligible) eligible.add(SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
        if (compositionEligible) eligible.add(SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
        FinalArtifactResult result = new FinalArtifactResult(
                "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_FINAL_ARTIFACT_V2",
                binding.contractHash(), all.drafts().size(), all.rows().size(),
                all.pairs().size(), calibration.replayChecks().size(),
                calibration.instrumentationChecks().size(), officialSimulationCount(all),
                baselineStable, matchupCausal, compositionCausal, calibrationSensitivity,
                holdoutSensitivity, eligible,
                decide(baselineStable, matchupEligible, compositionEligible, eligible),
                "SEE_SHA256SUMS_TXT_RAW_SHA256");
        if (all.drafts().size() != 800 || all.rows().size() != 2_400
                || all.pairs().size() != 1_600 || officialSimulationCount(all) != 3_000) {
            throw new IllegalStateException("Artifact finalizer population coverage mismatch");
        }
        Files.createDirectories(candidate);
        writeFinalArtifacts(candidate, binding, calibration, holdout, all, integrity, result);
        writeFinalizerExecutionProof(candidate, "FINAL_CANDIDATE", executionsBefore);
        return result;
    }

    public FinalArtifactResult promoteFreshJvmCandidates(
            Path backendRoot, Path output, Path candidateA, Path candidateB
    ) throws Exception {
        Binding binding = requireBinding(backendRoot, output, true);
        requireTreeByteEquality(candidateA, candidateB);
        try (var stream = Files.walk(candidateA)) {
            for (Path source : stream.filter(Files::isRegularFile).toList()) {
                Path relative = candidateA.relativize(source);
                writeReplace(output.resolve(relative), Files.readAllBytes(source));
            }
        }
        FinalArtifactResult result = canonical.readValue(
                output.resolve("artifact-result.json").toFile(), FinalArtifactResult.class);
        if (!result.contractHash().equals(binding.contractHash())) {
            throw new IllegalStateException("Fresh-JVM result contract mismatch");
        }
        HoldoutCompletion completion = new HoldoutCompletion(
                "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_HOLDOUT_COMPLETION_V2",
                binding.contractHash(), 100, 400, 1_200, true,
                fileHash(output.resolve("final-recommendation.json")));
        writeFrozen(output.resolve("holdout-completion-receipt.json"),
                canonicalBytes(completion));
        writeReplace(output.resolve("SHA256SUMS.txt"),
                recursiveManifest(output).getBytes(StandardCharsets.UTF_8));
        return result;
    }

    private static void requireTreeByteEquality(Path first, Path second) throws IOException {
        Map<String, String> a = treeHashes(first);
        Map<String, String> b = treeHashes(second);
        if (!a.equals(b)) {
            throw new IllegalStateException("Fresh-JVM artifact candidates differ");
        }
    }

    private static Map<String, String> treeHashes(Path root) throws IOException {
        TreeMap<String, String> result = new TreeMap<>();
        try (var stream = Files.walk(root)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                result.put(root.relativize(path).toString().replace('\\', '/'), fileHash(path));
            }
        }
        return Map.copyOf(result);
    }

    private FixtureCheckpoint executeFixture(
            Binding binding,
            int fixtureIndex,
            MatchEngineV9FreshRequalificationContract.Fixture fixture,
            MatchEngineV9FreshRequalificationContract.SampleLane lane
    ) throws Exception {
        List<Long> seeds = lane == MatchEngineV9FreshRequalificationContract.SampleLane.CALIBRATION
                ? fixture.calibrationSeeds() : fixture.holdoutSeeds();
        ArrayList<DraftEvidence> drafts = new ArrayList<>(4);
        ArrayList<MatchRow> rows = new ArrayList<>(12);
        ArrayList<PairObservation> pairs = new ArrayList<>(8);
        ArrayList<ReplayCheck> replayChecks = new ArrayList<>(3);
        ArrayList<InstrumentationCheck> instrumentationChecks = new ArrayList<>(3);
        for (int seedIndex = 0; seedIndex < seeds.size(); seedIndex++) {
            long seed = seeds.get(seedIndex);
            var prepared = harness.prepare(fixture, seed);
            drafts.add(draftEvidence(fixture, lane, seedIndex, prepared));
            List<FreshAutoDraftRealMatchHarness.Executed> runs = harness.executeProfiles(prepared);
            gameplayExecutionCount += runs.size();
            List<MatchRow> seedRows = new ArrayList<>(3);
            for (int profileIndex = 0; profileIndex < runs.size(); profileIndex++) {
                seedRows.add(toRow(fixture, lane, seedIndex, profileIndex,
                        runs.get(profileIndex)));
            }
            rows.addAll(seedRows);
            pairs.add(pairObservation(seedRows.get(0), seedRows.get(1),
                    runs.get(0).execution().timeline(), runs.get(1).execution().timeline(),
                    MarginalKind.MATCHUP_MINUS_BASELINE));
            pairs.add(pairObservation(seedRows.get(1), seedRows.get(2),
                    runs.get(1).execution().timeline(), runs.get(2).execution().timeline(),
                    MarginalKind.FULL_MINUS_MATCHUP));
            if (lane == MatchEngineV9FreshRequalificationContract.SampleLane.CALIBRATION
                    && seedIndex == 0) {
                for (FreshAutoDraftRealMatchHarness.Executed run : runs) {
                    var repeated = harness.execute(prepared, run.profileId());
                    gameplayExecutionCount++;
                    replayChecks.add(new ReplayCheck(fixture.fixtureId(), seed,
                            run.profileId(), exact(run, repeated),
                            run.provenance().timelineHash(),
                            repeated.provenance().timelineHash(),
                            run.execution().randomFingerprint(),
                            repeated.execution().randomFingerprint()));
                    var disabled = harness.executeInstrumentationDisabled(prepared, run.profileId());
                    gameplayExecutionCount++;
                    boolean exact = provenance.timelineHash(disabled.timeline()).equals(
                            run.provenance().timelineHash())
                            && disabled.randomFingerprint().equals(
                            run.execution().randomFingerprint());
                    instrumentationChecks.add(new InstrumentationCheck(
                            fixture.fixtureId(), seed, run.profileId(), exact,
                            run.provenance().timelineHash(),
                            provenance.timelineHash(disabled.timeline()),
                            run.execution().randomFingerprint(), disabled.randomFingerprint()));
                }
            }
        }
        String payloadDigest = checkpointPayloadDigest(binding, fixtureIndex, fixture,
                lane, drafts, rows, pairs, replayChecks, instrumentationChecks);
        return new FixtureCheckpoint(CHECKPOINT_SCHEMA, binding.contractHash(),
                binding.sourceIdentity().combinedSourceHash(), fixtureIndex,
                fixture.fixtureId(), lane, workerJvmIdentity(), payloadDigest,
                List.copyOf(drafts), List.copyOf(rows), List.copyOf(pairs),
                List.copyOf(replayChecks), List.copyOf(instrumentationChecks));
    }

    private DraftEvidence draftEvidence(
            MatchEngineV9FreshRequalificationContract.Fixture fixture,
            MatchEngineV9FreshRequalificationContract.SampleLane lane,
            int seedIndex,
            FreshAutoDraftRealMatchHarness.PreparedInput prepared
    ) {
        var draft = prepared.input().finalDraft();
        return new DraftEvidence(
                "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_DRAFT_EVIDENCE_V2",
                fixture.fixtureId(), lane, seedIndex, prepared.seed(),
                draft.draftSelectionPolicyId(), draft.draftSelectionPolicyHash(),
                DraftSelectionTraceHasher.TRACE_HASH_ALGORITHM,
                draft.draftSelectionTraceHash(), draft.draftDecisionHash(),
                draft.finalDraftHash(), draft.finalAssignmentHash(),
                prepared.input().inputHash(), prepared.input().rosterIdentityHash(),
                prepared.input().seriesHistoryBeforeHash(),
                draft.selectionTraces().size(), prepared.targetProductionAutoDraftCount(),
                draft.selectionTraces().stream().map(value -> value.selectedRank()).toList(),
                draft.selectionTraces().stream().map(value -> value.eligiblePool().size()).toList(),
                draft.selectionTraces().stream()
                        .map(value -> value.selectedCanonicalScoreLoss()).toList(),
                draft.bluePicks().stream().map(value -> value.value()).toList(),
                draft.redPicks().stream().map(value -> value.value()).toList(),
                prepared.input().championAssignments().stream()
                        .sorted(Comparator.comparing(value -> value.teamSide().name()
                                + ":" + value.position().name()))
                        .map(value -> value.teamSide() + ":" + value.position() + "="
                                + value.playerId().value() + ":" + value.championId().value())
                        .toList());
    }

    private MatchRow toRow(
            MatchEngineV9FreshRequalificationContract.Fixture fixture,
            MatchEngineV9FreshRequalificationContract.SampleLane lane,
            int seedIndex,
            int profileIndex,
            FreshAutoDraftRealMatchHarness.Executed run
    ) throws Exception {
        var prepared = run.prepared();
        var execution = run.execution();
        MatchTimeline timeline = execution.timeline();
        MatchSnapshot end = timeline.getSnapshots().getLast();
        var diagnostics = execution.structuredDiagnostics();
        IntegrityObservation integrity = integrity(run);
        StructureObservation structure = structureObservation(end);
        MatchupEvidence matchup = matchupEvidence(diagnostics.championMatchup());
        CompositionEvidence composition = compositionEvidence(diagnostics.composition());
        Map<String, String> componentHashes = diagnosticsComponentHashes(diagnostics);
        MatchRow unsigned = new MatchRow(
                ROW_SCHEMA, fixture.fixtureId(), fixture.fixtureLane(), fixture.pairId(),
                fixture.blueTeamCode(), fixture.redTeamCode(), fixture.seriesGameNumber(),
                lane, seedIndex, prepared.seed(), profileIndex, run.profileId(),
                SimulationRuntimeProfiles.resolve(run.profileId()).configurationHash(),
                SimulationRuntimeProfiles.resolve(run.profileId()).activeGameplayRulesVersion(),
                run.provenance().engineImplementationVersion(),
                run.provenance().resourceProvenance().resourceProvenanceHash(),
                prepared.input().inputHash(), prepared.input().rosterIdentityHash(),
                prepared.input().seriesHistoryBeforeHash(),
                prepared.input().finalDraft().draftSelectionPolicyId(),
                prepared.input().finalDraft().draftSelectionPolicyHash(),
                DraftSelectionTraceHasher.TRACE_HASH_ALGORITHM,
                prepared.input().finalDraft().draftSelectionTraceHash(),
                prepared.input().finalDraft().draftDecisionHash(),
                prepared.input().finalDraft().finalDraftHash(),
                prepared.input().finalDraft().finalAssignmentHash(),
                run.provenance().replayProvenanceHash(), run.provenance().timelineHash(),
                execution.randomFingerprint(), timeline.getWinner(), execution.winnerSide(),
                execution.endReason(), timeline.getDurationSeconds(), end.getBlueKills(),
                end.getRedKills(), end.getBlueGold(), end.getRedGold(), end.getBlueDragons(),
                end.getRedDragons(), objectiveSignature(timeline), structure, matchup,
                composition, integrity,
                Phase13GB1SimulationExecutor.structuredDiagnosticsHash(diagnostics),
                componentHashes, "UNSIGNED");
        return withPayloadDigest(unsigned, rowPayloadDigest(unsigned));
    }

    private MatchupEvidence matchupEvidence(ChampionMatchupExecutionStatsSnapshot value) {
        EnumSet<Position> positions = EnumSet.noneOf(Position.class);
        value.applicationProvenance().forEach(application -> application.pairApplications()
                .forEach(pair -> {
                    positions.add(pair.source().position());
                    positions.add(pair.opponent().position());
                }));
        return new MatchupEvidence(value.consumedApplicationCount(),
                value.nonZeroConsumedApplicationCount(),
                value.idempotentDuplicateConsumedApplicationCount(),
                value.duplicateConsumedApplicationErrors(), value.applicationBindingErrors(),
                value.staleAssignmentParticipantErrors(), value.missingAssignmentErrors(),
                value.deadParticipantErrors(), value.nonParticipantErrors(),
                value.sameTeamPairErrors(), value.crossPositionErrors(),
                value.duplicateApplicationErrors(), value.staleStateErrors(),
                value.directRandomCalls(), value.finalMatchupEdgeSum(),
                positions.stream().map(Enum::name).sorted().toList(),
                value.applicationProvenance(), value.stateConsumerProvenance());
    }

    private static CompositionEvidence compositionEvidence(CompositionRuntimeDiagnostics value) {
        Map<String, Long> contexts = value.applicationProvenance().stream()
                .filter(CompositionApplicationProvenance::applicationApplied)
                .filter(application -> application.context() != null)
                .collect(Collectors.groupingBy(application -> application.context().name(),
                        TreeMap::new, Collectors.counting()));
        int scalarApplied = (int) value.applicationProvenance().stream()
                .filter(CompositionApplicationProvenance::modifierConsumed).count();
        int nonZeroScalar = (int) value.applicationProvenance().stream()
                .filter(application -> application.modifierConsumed()
                        && application.nonZeroModifier()).count();
        int objectiveSetupScalar = (int) value.applicationProvenance().stream()
                .filter(application -> application.context()
                        == TeamCompositionContext.OBJECTIVE_SETUP)
                .filter(CompositionApplicationProvenance::modifierConsumed).count();
        int decompositionErrors = (int) value.applicationProvenance().stream()
                .filter(CompositionApplicationProvenance::applicationApplied)
                .filter(application -> Math.abs(application.totalCompositionInputDelta()
                        - (application.modifier()
                        + application.existingNonScalarCompositionDelta()
                        + application.clampEffect())) > 1e-12).count();
        return new CompositionEvidence(value.mode().name(), value.initialized(),
                value.actualAttemptCount(), value.mappedActualAttemptCount(),
                value.unmappedActualAttemptCount(), value.modifierCalculatedCount(),
                scalarApplied, value.modifierConsumedCount(), nonZeroScalar,
                value.existingNonScalarEffectConsumedCount(),
                value.totalCompositionEffectApplicationCount(),
                value.gameplayApplicationCount(), value.nonZeroModifierCount(),
                objectiveSetupScalar, value.duplicateObservationCount(),
                value.multiContextAttemptCount(), value.conflictingPerspectiveCount(),
                value.duplicateApplicationPointCount(), value.duplicatePublicBindingCount(),
                value.conflictingPublicBindingCount(), decompositionErrors,
                value.directRandomCallCount(), value.compositionRandomDrawCount(),
                value.publicActionBindingCount(), contexts, value.applicationProvenance());
    }

    private PairObservation pairObservation(
            MatchRow before,
            MatchRow after,
            MatchTimeline beforeTimeline,
            MatchTimeline afterTimeline,
            MarginalKind kind
    ) throws Exception {
        requirePairedIdentity(before, after);
        Divergence divergence = firstDivergence(beforeTimeline, afterTimeline);
        int direct = 0;
        int indirect = 0;
        int unresolved = 0;
        int unexplained = 0;
        CausalClassification classification = CausalClassification.NO_PUBLIC_DIVERGENCE;
        if (divergence.present()) {
            if (kind == MarginalKind.MATCHUP_MINUS_BASELINE) {
                List<ChampionMatchupApplicationProvenance> applications =
                        after.matchup().applications();
                boolean exactDirect = applications.stream().anyMatch(value ->
                        exactDirectBinding(value, divergence.timeSeconds(),
                                divergence.actionIds(), divergence.contexts(),
                                divergence.stages()));
                if (exactDirect) {
                    direct = 1;
                    classification = CausalClassification.EXACT_DIRECT_ACTION_CAUSE;
                } else {
                    boolean exactIndirect = exactIndirectBinding(
                            after.matchup().stateConsumers(), divergence.timeSeconds(),
                            divergence.actionIds(), divergence.contexts());
                    if (exactIndirect) {
                        indirect = 1;
                        classification = CausalClassification.INDIRECT_PRIOR_STATE_CAUSE;
                    } else {
                        boolean stateObservedWithoutConsumer = unresolvedStateObserved(
                                applications, divergence.timeSeconds());
                        if (stateObservedWithoutConsumer) {
                            unresolved = 1;
                            classification = CausalClassification.UNRESOLVED_SNAPSHOT_CAUSE;
                        } else {
                            unexplained = 1;
                            classification = CausalClassification.UNEXPLAINED_PUBLIC_DIVERGENCE;
                        }
                    }
                }
            } else {
                List<CompositionApplicationProvenance> applications =
                        after.composition().applications();
                direct = (int) applications.stream().filter(value ->
                        value.publicActionId() != null
                                && divergence.actionIds().contains(value.publicActionId())
                                && value.publicEventTimeSeconds() != null
                                && value.publicEventTimeSeconds() == divergence.timeSeconds())
                        .count();
                if (direct > 0) {
                    classification = CausalClassification.EXACT_DIRECT_ACTION_CAUSE;
                } else {
                    unexplained = 1;
                    classification = CausalClassification.UNEXPLAINED_PUBLIC_DIVERGENCE;
                }
            }
        }
        StructureSeverity severity = severity(before.structure(), after.structure());
        return new PairObservation(
                "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_PAIR_OBSERVATION_V2",
                before.fixtureId(), before.sampleLane(), before.seedIndex(), before.seed(), kind,
                before.profileId(), after.profileId(), before.inputHash(),
                before.draftSelectionTraceHash(), before.finalDraftHash(),
                before.finalAssignmentHash(), before.winnerSide(), after.winnerSide(),
                before.winnerSide() != after.winnerSide(),
                !before.objectiveSignature().equals(after.objectiveSignature()), severity,
                before.durationSeconds(), after.durationSeconds(),
                after.durationSeconds() - before.durationSeconds(),
                before.endReason() == GameEndReason.SIMULATION_TIMEOUT,
                after.endReason() == GameEndReason.SIMULATION_TIMEOUT,
                divergence.present(), divergence.timeSeconds(), classification, direct, indirect,
                unresolved, unexplained, divergence.actionIds(), divergence.contexts(),
                divergence.stages());
    }

    private static void requirePairedIdentity(MatchRow before, MatchRow after) {
        if (!before.fixtureId().equals(after.fixtureId()) || before.seed() != after.seed()
                || before.sampleLane() != after.sampleLane()
                || !before.inputHash().equals(after.inputHash())
                || !before.draftSelectionTraceHash().equals(after.draftSelectionTraceHash())
                || !before.draftDecisionHash().equals(after.draftDecisionHash())
                || !before.finalDraftHash().equals(after.finalDraftHash())
                || !before.finalAssignmentHash().equals(after.finalAssignmentHash())
                || !before.rosterIdentityHash().equals(after.rosterIdentityHash())
                || !before.seriesHistoryBeforeHash().equals(after.seriesHistoryBeforeHash())) {
            throw new IllegalStateException("Paired profiles do not share immutable Draft input");
        }
    }

    private Divergence firstDivergence(MatchTimeline first, MatchTimeline second) throws Exception {
        if (provenance.timelineHash(first).equals(provenance.timelineHash(second))) {
            return new Divergence(false, -1, List.of(), List.of(), List.of());
        }
        int events = Math.min(first.getEvents().size(), second.getEvents().size());
        for (int index = 0; index < events; index++) {
            MatchEvent left = first.getEvents().get(index);
            MatchEvent right = second.getEvents().get(index);
            if (!eventIdentity(left).equals(eventIdentity(right))) {
                return divergence(left, right);
            }
        }
        if (first.getEvents().size() != second.getEvents().size()) {
            MatchEvent value = first.getEvents().size() > events
                    ? first.getEvents().get(events) : second.getEvents().get(events);
            return divergence(value, null);
        }
        int snapshots = Math.min(first.getSnapshots().size(), second.getSnapshots().size());
        for (int index = 0; index < snapshots; index++) {
            MatchSnapshot left = first.getSnapshots().get(index);
            MatchSnapshot right = second.getSnapshots().get(index);
            if (!java.util.Arrays.equals(canonicalBytes(left), canonicalBytes(right))) {
                return new Divergence(true,
                        Math.min(left.getTimeSeconds(), right.getTimeSeconds()), List.of(),
                        List.of(), List.of());
            }
        }
        return new Divergence(true,
                Math.min(first.getDurationSeconds(), second.getDurationSeconds()), List.of(),
                List.of(), List.of());
    }

    private static Divergence divergence(MatchEvent first, MatchEvent second) {
        LinkedHashSet<String> actionIds = new LinkedHashSet<>();
        EnumSet<com.lolfm.simulator.ProgressionCombatContext> contexts = EnumSet.noneOf(
                com.lolfm.simulator.ProgressionCombatContext.class);
        EnumSet<com.lolfm.simulator.ProgressionApplicationStage> stages = EnumSet.noneOf(
                com.lolfm.simulator.ProgressionApplicationStage.class);
        for (MatchEvent value : new MatchEvent[]{first, second}) {
            if (value == null) continue;
            if (value.getActionId() != null) actionIds.add(value.getActionId());
            if (value.getParentActionId() != null) actionIds.add(value.getParentActionId());
            contextOf(value).ifPresent(contexts::add);
            stageOf(value).ifPresent(stages::add);
        }
        int time = second == null ? first.getTimeSeconds()
                : Math.min(first.getTimeSeconds(), second.getTimeSeconds());
        return new Divergence(true, time, canonicalActionIds(actionIds),
                contexts.stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList(),
                stages.stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList());
    }

    static boolean exactDirectBinding(
            ChampionMatchupApplicationProvenance application,
            int divergenceTimeSeconds,
            java.util.Collection<String> divergenceActionIds,
            java.util.Collection<com.lolfm.simulator.ProgressionCombatContext> contexts,
            java.util.Collection<com.lolfm.simulator.ProgressionApplicationStage> stages
    ) {
        return application.nonZero()
                && application.applicationPoint()
                == com.lolfm.champion.ChampionMatchupApplicationPoint
                .COMBAT_PROGRESSION_SCORE
                && application.structuredActionId() != null
                && divergenceActionIds.contains(application.structuredActionId())
                && application.simulationTimeSeconds() == divergenceTimeSeconds
                && contexts.contains(application.context())
                && stages.contains(application.applicationStage());
    }

    static boolean exactIndirectBinding(
            java.util.Collection<com.lolfm.champion.ChampionMatchupStateConsumerProvenance>
                    consumers,
            int divergenceTimeSeconds,
            java.util.Collection<String> divergenceActionIds,
            java.util.Collection<com.lolfm.simulator.ProgressionCombatContext> contexts
    ) {
        return consumers.stream().anyMatch(value -> value.matchupPressureDelta() != 0.0
                && value.consumerTimeSeconds() == divergenceTimeSeconds
                && divergenceActionIds.contains(value.consumerActionId())
                && contexts.contains(value.consumerContext()));
    }

    static boolean unresolvedStateObserved(
            java.util.Collection<ChampionMatchupApplicationProvenance> applications,
            int divergenceTimeSeconds
    ) {
        return applications.stream()
                .filter(ChampionMatchupApplicationProvenance::nonZero)
                .map(ChampionMatchupApplicationProvenance::stateMutationLineage)
                .filter(Objects::nonNull)
                .anyMatch(value -> value.simulationTimeSeconds() <= divergenceTimeSeconds);
    }

    private static java.util.Optional<com.lolfm.simulator.ProgressionCombatContext> contextOf(
            MatchEvent event) {
        if (event.getCombatSource() != null) {
            return switch (event.getCombatSource()) {
                case LANE_COMBAT -> java.util.Optional.of(
                        com.lolfm.simulator.ProgressionCombatContext.LANE_COMBAT);
                case JUNGLE_GANK -> java.util.Optional.of(
                        com.lolfm.simulator.ProgressionCombatContext.JUNGLE_GANK);
                case COUNTER_GANK -> java.util.Optional.of(
                        com.lolfm.simulator.ProgressionCombatContext.COUNTER_GANK);
                case ROAM -> java.util.Optional.of(
                        com.lolfm.simulator.ProgressionCombatContext.ROAM);
                case SKIRMISH -> java.util.Optional.of(
                        com.lolfm.simulator.ProgressionCombatContext.GENERIC_SKIRMISH);
                case TEAMFIGHT -> java.util.Optional.of(
                        com.lolfm.simulator.ProgressionCombatContext.TEAMFIGHT);
                case OBJECTIVE_FIGHT -> java.util.Optional.of(
                        com.lolfm.simulator.ProgressionCombatContext.OBJECTIVE_FIGHT);
                case LATE_GAME_SIEGE -> java.util.Optional.of(
                        com.lolfm.simulator.ProgressionCombatContext.LATE_GAME_SIEGE);
                case BASE_DEFENSE -> java.util.Optional.of(
                        com.lolfm.simulator.ProgressionCombatContext.BASE_DEFENSE);
                case OTHER -> java.util.Optional.empty();
            };
        }
        return switch (event.getType()) {
            case LANE_COMBAT -> java.util.Optional.of(
                    com.lolfm.simulator.ProgressionCombatContext.LANE_COMBAT);
            case JUNGLE_GANK -> java.util.Optional.of(
                    com.lolfm.simulator.ProgressionCombatContext.JUNGLE_GANK);
            case COUNTER_GANK -> java.util.Optional.of(
                    com.lolfm.simulator.ProgressionCombatContext.COUNTER_GANK);
            case ROAM -> java.util.Optional.of(
                    com.lolfm.simulator.ProgressionCombatContext.ROAM);
            case TEAMFIGHT, TEAMFIGHT_RESULT, ACE -> java.util.Optional.of(
                    com.lolfm.simulator.ProgressionCombatContext.TEAMFIGHT);
            default -> java.util.Optional.empty();
        };
    }

    private static java.util.Optional<com.lolfm.simulator.ProgressionApplicationStage> stageOf(
            MatchEvent event) {
        if (event.getCombatSource() == com.lolfm.domain.CombatSource.SKIRMISH) {
            return java.util.Optional.of(
                    com.lolfm.simulator.ProgressionApplicationStage.INITIATIVE);
        }
        if (event.getType() == MatchEventType.TEAMFIGHT_RESULT
                || event.getType() == MatchEventType.ACE) {
            return java.util.Optional.of(
                    com.lolfm.simulator.ProgressionApplicationStage.FIGHT_GRADE);
        }
        return contextOf(event).map(ignored ->
                com.lolfm.simulator.ProgressionApplicationStage.COMBAT_SCORE);
    }

    /** Signed evidence order is lexical canonical order, not gameplay/event order. */
    static List<String> canonicalActionIds(java.util.Collection<String> actionIds) {
        Objects.requireNonNull(actionIds, "actionIds");
        if (actionIds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Signed action identities must be non-blank");
        }
        return actionIds.stream().distinct().sorted().toList();
    }

    private static String eventIdentity(MatchEvent value) {
        return value.getTimeSeconds() + "|" + value.getType() + "|" + value.getActionId()
                + "|" + value.getParentActionId() + "|" + value.getActorPlayerId()
                + "|" + value.getKillerPlayerId() + "|" + value.getVictimPlayerId()
                + "|" + value.getAssistPlayerIds() + "|" + value.getCombatSource()
                + "|" + value.getCombatLane() + "|" + value.getObjectiveDecision()
                + "|" + value.getObjectiveFight() + "|" + value.getStructureAction();
    }

    private IntegrityObservation integrity(FreshAutoDraftRealMatchHarness.Executed run) {
        var diagnostics = run.execution().structuredDiagnostics();
        var configuration = SimulationRuntimeProfiles.resolve(run.profileId())
                .gameplayConfiguration();
        long domain = Phase13GB1RealMatchHarness.IntegrityDiagnostics
                .from(configuration, diagnostics).errorCount();
        int invalidStructure = 0;
        int nexusOrdering = 0;
        int postFinish = 0;
        int supportFarm = 0;
        MatchTimeline timeline = run.execution().timeline();
        for (MatchSnapshot snapshot : timeline.getSnapshots()) {
            if (snapshot.getTimeSeconds() > timeline.getDurationSeconds()) postFinish++;
            for (var player : snapshot.getPlayerSnapshots()) {
                if (player.getPosition() == Position.SUPPORT && player.getCs() != 0) {
                    supportFarm++;
                }
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
                if (!team.nexusAlive() && team.nexusTurretsRemaining() > 0) nexusOrdering++;
            }
        }
        for (MatchEvent event : timeline.getEvents()) {
            if (event.getTimeSeconds() > timeline.getDurationSeconds()) postFinish++;
        }
        var matchup = diagnostics.championMatchup();
        var composition = diagnostics.composition();
        long newStructuredErrors = (long) matchup.duplicateConsumedApplicationErrors()
                + matchup.applicationBindingErrors()
                + composition.duplicatePublicBindingCount()
                + composition.conflictingPublicBindingCount();
        return new IntegrityObservation(
                run.execution().endReason() == GameEndReason.SIMULATION_TIMEOUT ? 1 : 0,
                domain, newStructuredErrors, invalidStructure, nexusOrdering,
                postFinish, supportFarm, matchup.directRandomCalls(),
                composition.directRandomCallCount(), composition.compositionRandomDrawCount());
    }

    private static int invalidHealth(StructureStateSnapshot.Health value) {
        return invalidHealth(value.current(), value.maximum());
    }

    private static int invalidHealth(double current, double maximum) {
        return !Double.isFinite(current) || !Double.isFinite(maximum) || maximum < 0.0
                || current < -1.0e-9 || current > maximum + 1.0e-9 ? 1 : 0;
    }

    private StructureObservation structureObservation(MatchSnapshot end) throws Exception {
        StructureProgression progression = new StructureProgression(
                end.getBlueTowersDestroyed(), end.getRedTowersDestroyed(),
                end.getBlueInhibitorsRemaining(), end.getRedInhibitorsRemaining(),
                end.getBlueNexusTurretsRemaining(), end.getRedNexusTurretsRemaining(),
                end.isBlueNexusAlive(), end.isRedNexusAlive());
        return new StructureObservation(progression,
                MatchEngineV9FreshRequalificationContract.sha256(
                        canonicalBytes(end.getStructureState())));
    }

    private static StructureSeverity severity(
            StructureObservation before, StructureObservation after
    ) {
        if (before.fullStateHash().equals(after.fullStateHash())) return StructureSeverity.EXACT;
        StructureProgression a = before.progression();
        StructureProgression b = after.progression();
        if (a.equals(b)) return StructureSeverity.HP_ONLY;
        if (a.blueNexusAlive() != b.blueNexusAlive()
                || a.redNexusAlive() != b.redNexusAlive()) {
            return StructureSeverity.NEXUS_OR_ENDING;
        }
        if (a.blueNexusTurretsRemaining() != b.blueNexusTurretsRemaining()
                || a.redNexusTurretsRemaining() != b.redNexusTurretsRemaining()) {
            return StructureSeverity.NEXUS_TURRET_PROGRESSION;
        }
        if (a.blueInhibitorsRemaining() != b.blueInhibitorsRemaining()
                || a.redInhibitorsRemaining() != b.redInhibitorsRemaining()) {
            return StructureSeverity.INHIBITOR_PROGRESSION;
        }
        return StructureSeverity.LANE_TOWER_PROGRESSION;
    }

    private static String objectiveSignature(MatchTimeline timeline) {
        StringBuilder value = new StringBuilder();
        timeline.getEvents().stream().filter(event -> event.getType() == MatchEventType.DRAGON
                        || event.getType() == MatchEventType.BARON
                        || event.getType() == MatchEventType.ELDER)
                .forEach(event -> value.append(event.getTimeSeconds()).append('|')
                        .append(event.getType()).append('|').append(event.getActionId()).append('|')
                        .append(event.getActorPlayerId()).append('|')
                        .append(event.getObjectiveDecision()).append('|')
                        .append(event.getObjectiveFight()).append('\n'));
        return MatchEngineV9FreshRequalificationContract.sha256(value.toString());
    }

    private Map<String, String> diagnosticsComponentHashes(
            Phase13GB1SimulationExecutor.StructuredDiagnostics diagnostics
    ) throws Exception {
        TreeMap<String, String> result = new TreeMap<>();
        for (var component : diagnostics.getClass().getRecordComponents()) {
            Object value = component.getAccessor().invoke(diagnostics);
            result.put(component.getName(),
                    Phase13GB1SimulationExecutor.structuredValueHash(value));
        }
        return Map.copyOf(result);
    }

    private static boolean exact(
            FreshAutoDraftRealMatchHarness.Executed first,
            FreshAutoDraftRealMatchHarness.Executed second
    ) {
        return first.provenance().timelineHash().equals(second.provenance().timelineHash())
                && first.execution().randomFingerprint().equals(
                second.execution().randomFingerprint())
                && Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                first.execution().structuredDiagnostics()).equals(
                Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                        second.execution().structuredDiagnostics()));
    }

    private MatchRow withPayloadDigest(MatchRow row, String digest) {
        return new MatchRow(row.schemaVersion(), row.fixtureId(), row.fixtureLane(), row.pairId(),
                row.blueTeamCode(), row.redTeamCode(), row.seriesGameNumber(), row.sampleLane(),
                row.seedIndex(), row.seed(), row.profileIndex(), row.profileId(),
                row.configurationHash(), row.activeGameplayRulesVersion(),
                row.engineImplementationVersion(), row.resourceProvenanceHash(), row.inputHash(),
                row.rosterIdentityHash(), row.seriesHistoryBeforeHash(),
                row.draftSelectionPolicyId(), row.draftSelectionPolicyHash(),
                row.draftSelectionTraceHashAlgorithm(), row.draftSelectionTraceHash(),
                row.draftDecisionHash(), row.finalDraftHash(), row.finalAssignmentHash(),
                row.replayProvenanceHash(), row.timelineHash(), row.randomFingerprint(),
                row.winnerTeamCode(), row.winnerSide(), row.endReason(), row.durationSeconds(),
                row.blueKills(), row.redKills(), row.blueGold(), row.redGold(),
                row.blueDragons(), row.redDragons(), row.objectiveSignature(), row.structure(),
                row.matchup(), row.composition(), row.integrity(),
                row.structuredDiagnosticsHash(), row.diagnosticsComponentHashes(), digest);
    }

    private String rowPayloadDigest(MatchRow row) throws Exception {
        return MatchEngineV9FreshRequalificationContract.sha256(canonicalBytes(Map.ofEntries(
                Map.entry("schemaVersion", row.schemaVersion()),
                Map.entry("jobIdentity", row.fixtureId() + "|" + row.sampleLane() + "|"
                        + row.seedIndex() + "|" + row.seed() + "|" + row.profileIndex()),
                Map.entry("teams", row.blueTeamCode() + "|" + row.redTeamCode()),
                Map.entry("seriesGameNumber", row.seriesGameNumber()),
                Map.entry("rosterIdentityHash", row.rosterIdentityHash()),
                Map.entry("seriesHistoryBeforeHash", row.seriesHistoryBeforeHash()),
                Map.entry("draftSelectionPolicyId", row.draftSelectionPolicyId()),
                Map.entry("draftSelectionPolicyHash", row.draftSelectionPolicyHash()),
                Map.entry("draftSelectionTraceHash", row.draftSelectionTraceHash()),
                Map.entry("draftDecisionHash", row.draftDecisionHash()),
                Map.entry("finalDraftHash", row.finalDraftHash()),
                Map.entry("finalAssignmentHash", row.finalAssignmentHash()),
                Map.entry("inputHash", row.inputHash()),
                Map.entry("profileId", row.profileId()),
                Map.entry("configurationHash", row.configurationHash()),
                Map.entry("rules", row.activeGameplayRulesVersion()),
                Map.entry("engine", row.engineImplementationVersion()),
                Map.entry("resource", row.resourceProvenanceHash()),
                Map.entry("replay", row.replayProvenanceHash()),
                Map.entry("timeline", row.timelineHash()),
                Map.entry("random", row.randomFingerprint()),
                Map.entry("outcome", java.util.Arrays.asList(
                        row.winnerTeamCode(), row.winnerSide(),
                        row.endReason(), row.durationSeconds())),
                Map.entry("objective", row.objectiveSignature()),
                Map.entry("structure", row.structure()),
                Map.entry("matchup", row.matchup()),
                Map.entry("composition", row.composition()),
                Map.entry("integrity", row.integrity()),
                Map.entry("diagnostics", row.diagnosticsComponentHashes()))));
    }

    private String checkpointPayloadDigest(
            Binding binding,
            int fixtureIndex,
            MatchEngineV9FreshRequalificationContract.Fixture fixture,
            MatchEngineV9FreshRequalificationContract.SampleLane lane,
            List<DraftEvidence> drafts,
            List<MatchRow> rows,
            List<PairObservation> pairs,
            List<ReplayCheck> replayChecks,
            List<InstrumentationCheck> instrumentationChecks
    ) throws Exception {
        return MatchEngineV9FreshRequalificationContract.sha256(canonicalBytes(Map.of(
                "contractHash", binding.contractHash(),
                "sourceHash", binding.sourceIdentity().combinedSourceHash(),
                "fixtureIndex", fixtureIndex,
                "fixture", fixture,
                "sampleLane", lane,
                "drafts", drafts,
                "rows", rows,
                "pairs", pairs,
                "replayChecks", replayChecks,
                "instrumentationChecks", instrumentationChecks)));
    }

    private Population population(List<FixtureCheckpoint> checkpoints) {
        return new Population(
                checkpoints.stream().flatMap(value -> value.drafts().stream()).toList(),
                checkpoints.stream().flatMap(value -> value.rows().stream()).toList(),
                checkpoints.stream().flatMap(value -> value.pairs().stream()).toList(),
                checkpoints.stream().flatMap(value -> value.replayChecks().stream()).toList(),
                checkpoints.stream().flatMap(value -> value.instrumentationChecks().stream())
                        .toList());
    }

    private static Population combine(Population first, Population second) {
        return new Population(concat(first.drafts(), second.drafts()),
                concat(first.rows(), second.rows()), concat(first.pairs(), second.pairs()),
                concat(first.replayChecks(), second.replayChecks()),
                concat(first.instrumentationChecks(), second.instrumentationChecks()));
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        ArrayList<T> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    private static int officialSimulationCount(Population population) {
        return population.rows().size() + population.replayChecks().size()
                + population.instrumentationChecks().size();
    }

    private static ExactIntegrity exactIntegrity(Population population) {
        long timeout = population.rows().stream().mapToLong(value ->
                value.integrity().timeoutCount()).sum();
        long domain = population.rows().stream().mapToLong(value ->
                value.integrity().domainIntegrityErrors()).sum();
        long structured = population.rows().stream().mapToLong(value ->
                value.integrity().structuredBindingErrors()).sum();
        long invalidStructure = population.rows().stream().mapToLong(value ->
                value.integrity().invalidStructureState()).sum();
        long nexus = population.rows().stream().mapToLong(value ->
                value.integrity().nexusOrderingErrors()).sum();
        long postFinish = population.rows().stream().mapToLong(value ->
                value.integrity().postFinishMutationOrEvent()).sum();
        long support = population.rows().stream().mapToLong(value ->
                value.integrity().supportFarmCsErrors()).sum();
        long matchupRandom = population.rows().stream().mapToLong(value ->
                value.integrity().matchupDirectRandomCalls()).sum();
        long compositionRandom = population.rows().stream().mapToLong(value ->
                value.integrity().compositionDirectRandomCalls()).sum();
        long compositionDraws = population.rows().stream().mapToLong(value ->
                value.integrity().compositionRandomDraws()).sum();
        long replayMismatch = population.replayChecks().stream()
                .filter(value -> !value.exact()).count();
        long instrumentationMismatch = population.instrumentationChecks().stream()
                .filter(value -> !value.exact()).count();
        boolean pass = timeout == 0 && domain == 0 && structured == 0
                && invalidStructure == 0 && nexus == 0 && postFinish == 0 && support == 0
                && matchupRandom == 0 && compositionRandom == 0 && compositionDraws == 0
                && replayMismatch == 0 && instrumentationMismatch == 0;
        return new ExactIntegrity(population.rows().size(), timeout, domain, structured,
                invalidStructure, nexus, postFinish, support, matchupRandom,
                compositionRandom, compositionDraws, replayMismatch,
                instrumentationMismatch, pass);
    }

    private static CausalGate matchupCausalGate(Population population) {
        List<MatchRow> baseline = profile(population.rows(), SimulationRuntimeProfileId.BASELINE_V1);
        List<MatchRow> matchup = profile(population.rows(),
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
        long baselineApplications = baseline.stream().mapToLong(value ->
                value.matchup().consumedApplicationCount()).sum();
        long applications = matchup.stream().mapToLong(value ->
                value.matchup().consumedApplicationCount()).sum();
        long nonZero = matchup.stream().mapToLong(value ->
                value.matchup().nonZeroConsumedApplicationCount()).sum();
        Set<String> positions = matchup.stream().flatMap(value ->
                value.matchup().coveredPositions().stream()).collect(Collectors.toSet());
        long bindingErrors = matchup.stream().mapToLong(value ->
                value.matchup().bindingErrorCount()).sum();
        long duplicates = matchup.stream().mapToLong(value ->
                value.matchup().duplicateErrorCount()).sum();
        long directRandom = matchup.stream().mapToLong(value ->
                value.matchup().directRandomCalls()).sum();
        List<PairObservation> pairs = population.pairs().stream()
                .filter(value -> value.kind() == MarginalKind.MATCHUP_MINUS_BASELINE).toList();
        long publicDivergence = pairs.stream().filter(PairObservation::publicDivergence).count();
        long direct = pairs.stream().mapToLong(PairObservation::directCauseCount).sum();
        long indirect = pairs.stream().mapToLong(PairObservation::indirectCauseCount).sum();
        long unresolved = pairs.stream()
                .mapToLong(PairObservation::unresolvedSnapshotCauseCount).sum();
        long unexplained = pairs.stream().mapToLong(PairObservation::unexplainedCount).sum();
        long objectiveStructureDirectMutation = matchup.stream()
                .flatMap(value -> value.matchup().applications().stream())
                .filter(value -> value.applicationPoint().name().contains("OBJECTIVE")
                        || value.applicationPoint().name().contains("STRUCTURE"))
                .count();
        boolean pass = baselineApplications == 0 && applications > 0 && nonZero > 0
                && positions.containsAll(List.of("TOP", "JUNGLE", "MID", "ADC", "SUPPORT"))
                && bindingErrors == 0 && duplicates == 0 && directRandom == 0
                && unresolved == 0 && unexplained == 0
                && objectiveStructureDirectMutation == 0;
        return new CausalGate("MATCHUP", pass, Map.ofEntries(
                Map.entry("baselineApplications", baselineApplications),
                Map.entry("consumedApplications", applications),
                Map.entry("nonZeroConsumedApplications", nonZero),
                Map.entry("coveredPositionCount", (long) positions.size()),
                Map.entry("bindingErrors", bindingErrors),
                Map.entry("duplicateErrors", duplicates),
                Map.entry("directRandomCalls", directRandom),
                Map.entry("publicDivergencePairs", publicDivergence),
                Map.entry("directCauseCount", direct),
                Map.entry("indirectCauseCount", indirect),
                Map.entry("unresolvedSnapshotCause", unresolved),
                Map.entry("unexplainedPublicDivergence", unexplained),
                Map.entry("objectiveStructureDirectMutation", objectiveStructureDirectMutation)),
                positions.stream().sorted().toList(), pass ? List.of() : failureReasons(
                baselineApplications != 0, "BASELINE_APPLICATION_NOT_ZERO",
                applications == 0 || nonZero == 0, "MATCHUP_APPLICATION_NOT_REACHED",
                positions.size() != 5, "POSITION_COVERAGE_INCOMPLETE",
                bindingErrors + duplicates > 0, "APPLICATION_BINDING_OR_DUPLICATE_ERROR",
                directRandom != 0, "DIRECT_RANDOM_NON_ZERO",
                unresolved != 0, "UNRESOLVED_SNAPSHOT_CAUSE",
                unexplained != 0, "UNEXPLAINED_PUBLIC_DIVERGENCE",
                objectiveStructureDirectMutation != 0, "DIRECT_OBJECTIVE_STRUCTURE_MUTATION"));
    }

    private static CausalGate compositionCausalGate(Population population) {
        List<MatchRow> matchup = profile(population.rows(),
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
        List<MatchRow> full = profile(population.rows(),
                SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
        long offApplications = matchup.stream().mapToLong(value ->
                value.composition().gameplayApplicationCount()).sum();
        long initialized = full.stream().filter(value -> value.composition().initialized()).count();
        long attempts = full.stream().mapToLong(value ->
                value.composition().actualAttemptCount()).sum();
        long mapped = full.stream().mapToLong(value ->
                value.composition().mappedActualAttemptCount()).sum();
        long calculated = full.stream().mapToLong(value ->
                value.composition().modifierCalculatedCount()).sum();
        long applied = full.stream().mapToLong(value ->
                value.composition().scalarAppliedCount()).sum();
        long consumed = full.stream().mapToLong(value ->
                value.composition().modifierConsumedCount()).sum();
        long nonZero = full.stream().mapToLong(value ->
                value.composition().nonZeroScalarApplicationCount()).sum();
        long nonScalar = full.stream().mapToLong(value ->
                value.composition().existingNonScalarEffectConsumedCount()).sum();
        long objectiveSetup = full.stream().mapToLong(value ->
                value.composition().objectiveSetupScalarApplicationCount()).sum();
        long errors = full.stream().mapToLong(value ->
                value.composition().causalErrorCount()).sum();
        long directRandom = full.stream().mapToLong(value ->
                value.composition().directRandomCalls()
                        + value.composition().compositionRandomDraws()).sum();
        Set<String> contexts = full.stream().flatMap(value ->
                value.composition().applicationsByContext().keySet().stream())
                .collect(Collectors.toSet());
        List<PairObservation> pairs = population.pairs().stream()
                .filter(value -> value.kind() == MarginalKind.FULL_MINUS_MATCHUP).toList();
        long divergence = pairs.stream().filter(PairObservation::publicDivergence).count();
        long directCovered = pairs.stream()
                .filter(PairObservation::publicDivergence)
                .filter(value -> value.directCauseCount() > 0).count();
        long indirect = pairs.stream().mapToLong(PairObservation::indirectCauseCount).sum();
        long unexplained = pairs.stream().mapToLong(PairObservation::unexplainedCount).sum();
        boolean cardinalityExact = calculated == applied && applied == consumed;
        boolean contextsComplete = contexts.containsAll(List.of("SKIRMISH", "TEAMFIGHT",
                "SIEGE", "BASE_DEFENSE"));
        boolean directCoverage = divergence == directCovered;
        boolean pass = offApplications == 0 && initialized == full.size() && attempts > 0
                && mapped > 0 && cardinalityExact && nonZero > 0 && nonScalar > 0
                && objectiveSetup == 0 && contextsComplete && errors == 0
                && directRandom == 0 && directCoverage && indirect == 0 && unexplained == 0;
        return new CausalGate("COMPOSITION", pass, Map.ofEntries(
                Map.entry("matchupOnlyApplications", offApplications),
                Map.entry("initializedRows", initialized),
                Map.entry("actualAttempts", attempts), Map.entry("mappedAttempts", mapped),
                Map.entry("modifierCalculated", calculated), Map.entry("scalarApplied", applied),
                Map.entry("modifierConsumed", consumed), Map.entry("nonZeroScalar", nonZero),
                Map.entry("existingNonScalarConsumed", nonScalar),
                Map.entry("objectiveSetupScalarApplications", objectiveSetup),
                Map.entry("causalErrors", errors), Map.entry("directRandom", directRandom),
                Map.entry("publicDivergencePairs", divergence),
                Map.entry("directCoveredDivergencePairs", directCovered),
                Map.entry("indirectCauseCount", indirect),
                Map.entry("unexplainedPublicDivergence", unexplained)),
                contexts.stream().sorted().toList(), pass ? List.of() : failureReasons(
                offApplications != 0, "MATCHUP_ONLY_COMPOSITION_APPLICATION_NOT_ZERO",
                initialized != full.size() || attempts == 0 || mapped == 0,
                "COMPOSITION_REACHABILITY_INCOMPLETE",
                !cardinalityExact, "SCALAR_CARDINALITY_MISMATCH",
                nonZero == 0 || nonScalar == 0, "SCALAR_OR_NON_SCALAR_CHANNEL_MISSING",
                objectiveSetup != 0, "OBJECTIVE_SETUP_SCALAR_APPLICATION",
                !contextsComplete, "APPROVED_CONTEXT_COVERAGE_INCOMPLETE",
                errors != 0, "COMPOSITION_CAUSAL_INTEGRITY_ERROR",
                directRandom != 0, "DIRECT_RANDOM_NON_ZERO",
                !directCoverage || indirect != 0 || unexplained != 0,
                "PUBLIC_DIVERGENCE_DIRECT_COVERAGE_INCOMPLETE"));
    }

    private static List<String> failureReasons(Object... conditionsAndReasons) {
        ArrayList<String> result = new ArrayList<>();
        for (int index = 0; index < conditionsAndReasons.length; index += 2) {
            if ((Boolean) conditionsAndReasons[index]) {
                result.add((String) conditionsAndReasons[index + 1]);
            }
        }
        return List.copyOf(result);
    }

    private static Sensitivity sensitivity(List<PairObservation> pairs) {
        return new Sensitivity(marginal(pairs, MarginalKind.MATCHUP_MINUS_BASELINE),
                marginal(pairs, MarginalKind.FULL_MINUS_MATCHUP));
    }

    private static Marginal marginal(List<PairObservation> all, MarginalKind kind) {
        List<PairObservation> pairs = all.stream().filter(value -> value.kind() == kind).toList();
        if (pairs.isEmpty()) {
            return new Marginal(kind, 0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0, false);
        }
        int count = pairs.size();
        long beforeBlueWins = pairs.stream().filter(value ->
                value.beforeWinnerSide() == TeamSide.BLUE).count();
        long afterBlueWins = pairs.stream().filter(value ->
                value.afterWinnerSide() == TeamSide.BLUE).count();
        long blueToRed = pairs.stream().filter(value ->
                value.beforeWinnerSide() == TeamSide.BLUE
                        && value.afterWinnerSide() == TeamSide.RED).count();
        long redToBlue = pairs.stream().filter(value ->
                value.beforeWinnerSide() == TeamSide.RED
                        && value.afterWinnerSide() == TeamSide.BLUE).count();
        long winnerChanged = pairs.stream().filter(PairObservation::winnerChanged).count();
        long objectiveChanged = pairs.stream().filter(PairObservation::objectiveChanged).count();
        long actualStructure = pairs.stream().filter(value -> value.structureSeverity()
                != StructureSeverity.EXACT && value.structureSeverity()
                != StructureSeverity.HP_ONLY).count();
        long nexusEnding = pairs.stream().filter(value -> value.structureSeverity()
                == StructureSeverity.NEXUS_TURRET_PROGRESSION
                || value.structureSeverity() == StructureSeverity.NEXUS_OR_ENDING).count();
        double blueDelta = 100.0 * (afterBlueWins - beforeBlueWins) / count;
        double flipImbalance = 100.0 * Math.abs(blueToRed - redToBlue) / count;
        double winnerRate = 100.0 * winnerChanged / count;
        double objectiveRate = 100.0 * objectiveChanged / count;
        double actualStructureRate = 100.0 * actualStructure / count;
        double nexusEndingRate = 100.0 * nexusEnding / count;
        double meanDurationDelta = pairs.stream().mapToInt(PairObservation::durationDeltaSeconds)
                .average().orElse(0.0);
        double p95Delta = percentile(pairs.stream().mapToDouble(
                        PairObservation::afterDurationSeconds).toArray(), 0.95)
                - percentile(pairs.stream().mapToDouble(
                        PairObservation::beforeDurationSeconds).toArray(), 0.95);
        long timeoutIncrease = pairs.stream().filter(value ->
                !value.beforeTimeout() && value.afterTimeout()).count();
        var gates = MatchEngineV9FreshRequalificationContract.GATES;
        boolean pass = Math.abs(blueDelta)
                <= gates.absoluteBlueWinRateDeltaPercentagePoints()
                && flipImbalance <= gates.directionalWinnerFlipImbalancePercentagePoints()
                && winnerRate <= gates.pairedWinnerChangedRatePercent()
                && objectiveRate <= gates.objectiveChangedRatePercent()
                && actualStructureRate <= gates.actualStructureProgressionChangedRatePercent()
                && nexusEndingRate <= gates.nexusOrEndingProgressionChangedRatePercent()
                && Math.abs(meanDurationDelta) <= gates.absoluteMeanDurationDeltaSeconds()
                && Math.abs(p95Delta) <= gates.absoluteAggregateP95DurationDeltaSeconds()
                && timeoutIncrease == gates.timeoutIncrease();
        return new Marginal(kind, count, blueDelta, flipImbalance, winnerRate,
                objectiveRate, actualStructureRate, nexusEndingRate, meanDurationDelta,
                p95Delta, timeoutIncrease, pass);
    }

    private static double percentile(double[] values, double percentile) {
        java.util.Arrays.sort(values);
        if (values.length == 0) return 0.0;
        int index = (int) Math.ceil(percentile * values.length) - 1;
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }

    private static List<MatchRow> profile(
            List<MatchRow> rows, SimulationRuntimeProfileId profile
    ) {
        return rows.stream().filter(value -> value.profileId() == profile).toList();
    }

    private static ProfileDecision decide(
            boolean baselineStable,
            boolean matchupEligible,
            boolean compositionEligible,
            List<SimulationRuntimeProfileId> eligibleProfiles
    ) {
        if (!baselineStable) {
            return new ProfileDecision("BLOCKED_BASELINE_CORRECTNESS_FAILURE",
                    "MATCHUP_NOT_EVALUATED", "COMPOSITION_NOT_EVALUATED",
                    "BASELINE_V1", List.copyOf(eligibleProfiles),
                    Map.of("ALL_CANDIDATES", "BASELINE_CORRECTNESS_FAILURE"),
                    "PRODUCTION_PROFILE_DECISION");
        }
        if (!matchupEligible) {
            return new ProfileDecision("BASELINE_V1_STABLE",
                    "MATCHUP_V9_NOT_ELIGIBLE",
                    "COMPOSITION_V9_INCREMENT_NOT_ELIGIBLE",
                    "BASELINE_V1", List.copyOf(eligibleProfiles), Map.of(
                    "MATCHUP_ONLY_CANDIDATE_V1", "MATCHUP_EXACT_CAUSAL_OR_MACRO_GATE_FAILED",
                    "FULL_SYSTEM_CANDIDATE_V1", "BLOCKED_BY_MATCHUP_DEPENDENCY"),
                    "PRODUCTION_PROFILE_DECISION");
        }
        if (!compositionEligible) {
            return new ProfileDecision("BASELINE_V1_STABLE",
                    "MATCHUP_V9_ELIGIBLE_FOR_PRODUCTION_DECISION",
                    "COMPOSITION_V9_INCREMENT_NOT_ELIGIBLE",
                    "NO_AUTOMATIC_PRODUCTION_CHANGE", List.copyOf(eligibleProfiles), Map.of(
                    "FULL_SYSTEM_CANDIDATE_V1", "COMPOSITION_EXACT_CAUSAL_OR_MACRO_GATE_FAILED"),
                    "PRODUCTION_PROFILE_DECISION");
        }
        return new ProfileDecision("BASELINE_V1_STABLE",
                "MATCHUP_V9_ELIGIBLE_FOR_PRODUCTION_DECISION",
                "COMPOSITION_V9_INCREMENT_ELIGIBLE",
                "NO_AUTOMATIC_PRODUCTION_CHANGE", List.copyOf(eligibleProfiles), Map.of(),
                "PRODUCTION_PROFILE_DECISION");
    }

    private Binding requireBinding(Path backendRoot, Path output, boolean requireFullReceipt)
            throws Exception {
        Path contract = output.resolve("contract.json");
        Path contractHashPath = output.resolve("contract.sha256");
        if (!Files.isRegularFile(contract) || !Files.isRegularFile(contractHashPath)) {
            throw new IllegalStateException("Fresh contract must be frozen first");
        }
        String contractHash = Files.readString(contractHashPath, StandardCharsets.UTF_8)
                .trim().split("\\s+")[0];
        if (!fileHash(contract).equals(contractHash)) {
            throw new IllegalStateException("Frozen contract raw bytes changed");
        }
        SourceIdentity current = sourceIdentity(backendRoot);
        var node = mapper.readTree(contract.toFile());
        if (!node.path("sourceIdentity").path("combinedSourceHash").asText()
                .equals(current.combinedSourceHash())
                || !node.path("scheduleHash").asText().equals(
                MatchEngineV9FreshRequalificationContract.schedule().scheduleHash())) {
            throw new IllegalStateException("Current source differs from frozen contract");
        }
        var ledger = MatchEngineV9ConsumedSeedLedger.create(mapper, backendRoot);
        String currentLedgerHash = MatchEngineV9FreshRequalificationContract.sha256(
                canonicalBytes(ledger));
        if (!node.path("seedLedgerHash").asText().equals(currentLedgerHash)
                || !fileHash(output.resolve("consumed-seed-ledger.json"))
                .equals(currentLedgerHash)) {
            throw new IllegalStateException("Consumed-seed ledger differs from frozen contract");
        }
        Binding binding = new Binding(contractHash, current, currentLedgerHash);
        if (requireFullReceipt) {
            Path receiptPath = output.resolve("full-regression-receipt.json");
            if (!Files.isRegularFile(receiptPath)) {
                throw new IllegalStateException("Clean full regression receipt is required");
            }
            FullRegressionReceipt receipt = canonical.readValue(
                    receiptPath.toFile(), FullRegressionReceipt.class);
            if (!FULL_RECEIPT_SCHEMA.equals(receipt.schemaVersion()) || !receipt.clean()
                    || !receipt.contractHash().equals(contractHash)
                    || !receipt.combinedSourceHash().equals(current.combinedSourceHash())) {
                throw new IllegalStateException("Full regression receipt binding mismatch");
            }
        }
        return binding;
    }

    private SourceIdentity sourceIdentity(Path backendRoot) throws Exception {
        var production = Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot);
        List<Phase13GB1AuditArtifactWriter.SourceTreeIdentity> dependencies = List.of(
                Phase13GB1AuditArtifactWriter.phaseTestSourceTree(
                        backendRoot, "MatchEngineV9Fresh"),
                Phase13GB1AuditArtifactWriter.phaseTestSourceTree(
                        backendRoot, "FreshAutoDraftRealMatchHarness"),
                Phase13GB1AuditArtifactWriter.phaseTestSourceTree(backendRoot, "Phase13GB1"),
                Phase13GB1AuditArtifactWriter.phaseTestSourceTree(
                        backendRoot, "MatchEngineV9InstrumentationExecutor"));
        String buildHash = fileHash(backendRoot.resolve("build.gradle"));
        String combined = MatchEngineV9FreshRequalificationContract.sha256(
                production.hash() + "\n" + dependencies.stream()
                        .map(Phase13GB1AuditArtifactWriter.SourceTreeIdentity::hash)
                        .collect(Collectors.joining("\n")) + "\n" + buildHash + "\n");
        Path gitRoot = backendRoot.toAbsolutePath().normalize().getParent();
        return new SourceIdentity(git(gitRoot, "rev-parse", "HEAD"),
                git(gitRoot, "status", "--short"), production, dependencies,
                buildHash, combined, SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION,
                MatchEngineV1Policy.authoritative().policyHash());
    }

    private static String git(Path root, String... args) throws Exception {
        ArrayList<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git identity failed: " + output);
        }
        return output;
    }

    private Map<String, Object> profileBindings() {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (var profile : MatchEngineV9FreshRequalificationContract.PROFILES) {
            values.put(profile.name(), SimulationRuntimeProfiles.resolve(profile));
        }
        return values;
    }

    private FixtureCheckpoint readCheckpoint(
            Path path,
            Binding binding,
            MatchEngineV9FreshRequalificationContract.Fixture fixture,
            MatchEngineV9FreshRequalificationContract.SampleLane lane
    ) throws Exception {
        requireSidecar(path);
        FixtureCheckpoint value = canonical.readValue(path.toFile(), FixtureCheckpoint.class);
        byte[] rawBytes = Files.readAllBytes(path);
        byte[] typedBytes = canonicalBytes(value);
        byte[] treeBytes = canonical.writeValueAsBytes(canonical.readTree(rawBytes));
        if (!java.util.Arrays.equals(rawBytes, typedBytes)
                || !java.util.Arrays.equals(rawBytes, treeBytes)) {
            throw new IllegalStateException(
                    "Checkpoint typed/tree canonical bytes differ: " + path);
        }
        if (!CHECKPOINT_SCHEMA.equals(value.schemaVersion())
                || !value.contractHash().equals(binding.contractHash())
                || !value.combinedSourceHash().equals(
                binding.sourceIdentity().combinedSourceHash())
                || !value.fixtureId().equals(fixture.fixtureId())
                || value.sampleLane() != lane || value.drafts().size() != 4
                || value.rows().size() != 12 || value.pairs().size() != 8) {
            throw new IllegalStateException("Stale, incomplete, or relabelled checkpoint: " + path);
        }
        String expected = checkpointPayloadDigest(binding, value.fixtureIndex(), fixture, lane,
                value.drafts(), value.rows(), value.pairs(), value.replayChecks(),
                value.instrumentationChecks());
        if (!expected.equals(value.payloadDigest())) {
            throw new IllegalStateException("Checkpoint canonical payload digest mismatch: " + path);
        }
        for (MatchRow row : value.rows()) {
            if (!row.payloadDigest().equals(rowPayloadDigest(withPayloadDigest(row, "UNSIGNED")))) {
                throw new IllegalStateException("Match row payload digest mismatch: " + path);
            }
        }
        requireCheckpointPairing(value);
        return value;
    }

    private static void requireCheckpointPairing(FixtureCheckpoint checkpoint) {
        Map<String, List<MatchRow>> grouped = checkpoint.rows().stream().collect(
                Collectors.groupingBy(value -> value.seedIndex() + "|" + value.seed()));
        if (grouped.size() != 4 || grouped.values().stream().anyMatch(values -> values.size() != 3
                || values.stream().map(MatchRow::profileId).distinct().count() != 3
                || values.stream().map(MatchRow::inputHash).distinct().count() != 1
                || values.stream().map(MatchRow::draftSelectionTraceHash).distinct().count() != 1
                || values.stream().map(MatchRow::finalDraftHash).distinct().count() != 1
                || values.stream().map(MatchRow::finalAssignmentHash).distinct().count() != 1)) {
            throw new IllegalStateException("Duplicate, missing, or differently drafted profile row");
        }
        if (checkpoint.sampleLane()
                == MatchEngineV9FreshRequalificationContract.SampleLane.CALIBRATION) {
            if (checkpoint.replayChecks().size() != 3
                    || checkpoint.instrumentationChecks().size() != 3) {
                throw new IllegalStateException("Calibration replay/instrumentation evidence missing");
            }
        } else if (!checkpoint.replayChecks().isEmpty()
                || !checkpoint.instrumentationChecks().isEmpty()) {
            throw new IllegalStateException("Holdout contains unscheduled extra simulations");
        }
    }

    private List<FixtureCheckpoint> readAllCheckpoints(
            Path output,
            Binding binding,
            MatchEngineV9FreshRequalificationContract.SampleLane lane
    ) throws Exception {
        ArrayList<FixtureCheckpoint> result = new ArrayList<>(100);
        var fixtures = MatchEngineV9FreshRequalificationContract.schedule().fixtures();
        for (int index = 0; index < fixtures.size(); index++) {
            Path path = checkpointDirectory(output, lane).resolve(String.format(Locale.ROOT,
                    "%03d-%s.json", index, fixtures.get(index).fixtureId()));
            result.add(readCheckpoint(path, binding, fixtures.get(index), lane));
        }
        return List.copyOf(result);
    }

    private void requireWorkerReceipts(
            Path output,
            Binding binding,
            MatchEngineV9FreshRequalificationContract.SampleLane lane
    ) throws Exception {
        HashSet<String> jvms = new HashSet<>();
        int fixtures = 0;
        int rows = 0;
        int drafts = 0;
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            Path path = output.resolve("worker-receipts").resolve(
                    lane.name().toLowerCase(Locale.ROOT) + "-shard-" + shard + ".json");
            WorkerReceipt receipt = canonical.readValue(path.toFile(), WorkerReceipt.class);
            byte[] rawReceipt = Files.readAllBytes(path);
            if (!java.util.Arrays.equals(rawReceipt, canonicalBytes(receipt))
                    || !java.util.Arrays.equals(rawReceipt,
                    canonical.writeValueAsBytes(canonical.readTree(rawReceipt)))) {
                throw new IllegalStateException(
                        "Worker receipt typed/tree canonical bytes differ: " + path);
            }
            if (!"MATCH_ENGINE_V9_FRESH_REQUALIFICATION_WORKER_RECEIPT_V2"
                    .equals(receipt.schemaVersion())
                    || !receipt.contractHash().equals(binding.contractHash())
                    || !receipt.combinedSourceHash().equals(
                    binding.sourceIdentity().combinedSourceHash())
                    || receipt.sampleLane() != lane || receipt.shardIndex() != shard
                    || receipt.shardCount() != SHARD_COUNT || receipt.fixtureCount() != 25
                    || !jvms.add(receipt.workerJvmIdentityHash())) {
                throw new IllegalStateException("Worker ownership/JVM receipt mismatch: " + path);
            }
            fixtures += receipt.fixtureCount();
            rows += receipt.rowCount();
            drafts += receipt.draftCount();
        }
        int expectedRows = lane == MatchEngineV9FreshRequalificationContract.SampleLane.CALIBRATION
                ? 1_200 : 1_200;
        if (fixtures != 100 || rows != expectedRows || drafts != 400 || jvms.size() != 4) {
            throw new IllegalStateException("Four-worker coverage mismatch");
        }
    }

    private void requireAndStartHoldout(Path output, Binding binding) throws Exception {
        Path authorized = output.resolve("holdout-authorization.authorized");
        Path started = output.resolve("holdout-authorization.started");
        if (Files.isRegularFile(started)) {
            HoldoutAuthorization value = canonical.readValue(started.toFile(),
                    HoldoutAuthorization.class);
            requireAuthorization(value, binding);
            return;
        }
        if (!Files.isRegularFile(authorized)) {
            throw new IllegalStateException("Holdout is not authorized by clean calibration");
        }
        HoldoutAuthorization value = canonical.readValue(authorized.toFile(),
                HoldoutAuthorization.class);
        requireAuthorization(value, binding);
        try {
            Files.move(authorized, started, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.NoSuchFileException ignored) {
            if (!Files.isRegularFile(started)) throw ignored;
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(authorized, started);
        }
    }

    private void requireAuthorization(HoldoutAuthorization value, Binding binding)
            throws IOException {
        if (!value.contractHash().equals(binding.contractHash())
                || !value.combinedSourceHash().equals(
                binding.sourceIdentity().combinedSourceHash())
                || !value.scheduleHash().equals(
                MatchEngineV9FreshRequalificationContract.schedule().scheduleHash())
                || !value.seedLedgerHash().equals(binding.seedLedgerHash())
                || !value.frozenGates().equals(MatchEngineV9FreshRequalificationContract.GATES)) {
            throw new IllegalStateException("Holdout authorization binding mismatch");
        }
    }

    private void writeFinalArtifacts(
            Path output,
            Binding binding,
            Population calibration,
            Population holdout,
            Population all,
            ExactIntegrity integrity,
            FinalArtifactResult result
    ) throws Exception {
        writeReplace(output.resolve("draft-evidence.jsonl"),
                jsonl(all.drafts()).getBytes(StandardCharsets.UTF_8));
        writeReplace(output.resolve("match-rows.jsonl"),
                jsonl(all.rows()).getBytes(StandardCharsets.UTF_8));
        writeReplace(output.resolve("paired-marginals.csv"),
                pairedCsv(all.pairs()).getBytes(StandardCharsets.UTF_8));
        writeReplace(output.resolve("baseline-summary.json"), canonicalBytes(Map.of(
                "schemaVersion", "MATCH_ENGINE_V9_FRESH_BASELINE_SUMMARY_V2",
                "status", result.baselineStable() ? "BASELINE_V1_STABLE" : "BLOCKED",
                "calibrationRows", profile(calibration.rows(),
                        SimulationRuntimeProfileId.BASELINE_V1).size(),
                "holdoutRows", profile(holdout.rows(),
                        SimulationRuntimeProfileId.BASELINE_V1).size(),
                "exactIntegrity", integrity,
                "currentProductionProfile", "BASELINE_V1")));
        writeReplace(output.resolve("matchup-eligibility.json"), canonicalBytes(Map.of(
                "schemaVersion", "MATCHUP_V9_FRESH_ELIGIBILITY_V2",
                "status", result.decision().matchupStatus(),
                "causalGate", result.matchupCausalGate(),
                "calibrationMacro", result.calibrationSensitivity().matchupMinusBaseline(),
                "holdoutMacro", result.holdoutSensitivity().matchupMinusBaseline())));
        writeReplace(output.resolve("composition-eligibility.json"), canonicalBytes(Map.of(
                "schemaVersion", "COMPOSITION_V9_FRESH_ELIGIBILITY_V2",
                "status", result.decision().compositionStatus(),
                "causalGate", result.compositionCausalGate(),
                "calibrationMacro", result.calibrationSensitivity().fullMinusMatchup(),
                "holdoutMacro", result.holdoutSensitivity().fullMinusMatchup())));
        writeReplace(output.resolve("structure-severity-summary.json"), canonicalBytes(Map.of(
                "schemaVersion", "MATCH_ENGINE_V9_FRESH_STRUCTURE_SEVERITY_SUMMARY_V2",
                "calibration", structureSeveritySummary(calibration.pairs()),
                "holdout", structureSeveritySummary(holdout.pairs()),
                "hardGateExcludesHpOnly", true)));
        writeReplace(output.resolve("causal-coverage-summary.json"), canonicalBytes(Map.of(
                "schemaVersion", "MATCH_ENGINE_V9_FRESH_CAUSAL_COVERAGE_SUMMARY_V2",
                "matchup", result.matchupCausalGate(),
                "composition", result.compositionCausalGate())));
        writeReplace(output.resolve("segmented-sensitivity.csv"),
                segmentedSensitivity(all.pairs()).getBytes(StandardCharsets.UTF_8));
        writeReplace(output.resolve("eligible-production-profiles.json"), canonicalBytes(Map.of(
                "schemaVersion", "MATCH_ENGINE_V9_FRESH_ELIGIBLE_PROFILES_V2",
                "currentProductionProfile", "BASELINE_V1",
                "productionChanged", false,
                "eligibleProfiles", result.eligibleProfiles(),
                "rejectedProfiles", result.decision().rejectedProfiles())));
        Map<String, Object> recommendation = new LinkedHashMap<>();
        recommendation.put("schemaVersion",
                "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_FINAL_RECOMMENDATION_V2");
        recommendation.put("currentProductionProfile", "BASELINE_V1");
        recommendation.put("productionChanged", false);
        recommendation.put("engineImplementationVersion",
                SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION);
        recommendation.put("activeGameplayRulesVersion",
                SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION);
        recommendation.put("profileBindings", profileBindings());
        recommendation.put("resourceProvenance", provenance.resourceProvenance());
        recommendation.put("combinedSourceHash",
                binding.sourceIdentity().combinedSourceHash());
        recommendation.put("contractHash", binding.contractHash());
        recommendation.put("seedLedgerHash", binding.seedLedgerHash());
        recommendation.put("scheduleHash",
                MatchEngineV9FreshRequalificationContract.schedule().scheduleHash());
        recommendation.put("draftSelectionPolicyId",
                com.lolfm.draft.AutoDraftSelectionPolicy.production().policyId());
        recommendation.put("draftSelectionPolicyHash",
                com.lolfm.draft.AutoDraftSelectionPolicy.production().policyHash());
        recommendation.put("draftSelectionTraceSchema", "AUTO_DRAFT_SELECTION_TRACE_V2");
        recommendation.put("draftSelectionTraceHashAlgorithm",
                DraftSelectionTraceHasher.TRACE_HASH_ALGORITHM);
        recommendation.put("sampleCounts", Map.of(
                "productionAutoDrafts", all.drafts().size(),
                "coreMatchRows", all.rows().size(),
                "pairedMarginals", all.pairs().size(),
                "replayChecks", calibration.replayChecks().size(),
                "instrumentationChecks", calibration.instrumentationChecks().size(),
                "officialSimulations", officialSimulationCount(all)));
        recommendation.put("holdoutConsumed", true);
        recommendation.put("matchup", Map.of(
                "status", result.decision().matchupStatus(),
                "causal", result.matchupCausalGate(),
                "macro", result.holdoutSensitivity().matchupMinusBaseline()));
        recommendation.put("composition", Map.of(
                "status", result.decision().compositionStatus(),
                "causal", result.compositionCausalGate(),
                "macro", result.holdoutSensitivity().fullMinusMatchup()));
        recommendation.put("eligibleProfiles", result.eligibleProfiles());
        recommendation.put("rejectedProfiles", result.decision().rejectedProfiles());
        recommendation.put("nextStep", "PRODUCTION_PROFILE_DECISION");
        writeReplace(output.resolve("final-recommendation.json"),
                canonicalBytes(recommendation));
        writeReplace(output.resolve("artifact-result.json"), canonicalBytes(result));
        writeReplace(output.resolve("analysis.md"), analysis(result, all)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Map<String, Long>> structureSeveritySummary(
            List<PairObservation> pairs
    ) {
        TreeMap<String, Map<String, Long>> result = new TreeMap<>();
        for (MarginalKind kind : MarginalKind.values()) {
            EnumMap<StructureSeverity, Long> counts = new EnumMap<>(StructureSeverity.class);
            for (StructureSeverity severity : StructureSeverity.values()) counts.put(severity, 0L);
            pairs.stream().filter(value -> value.kind() == kind).forEach(value ->
                    counts.merge(value.structureSeverity(), 1L, Long::sum));
            result.put(kind.name(), counts.entrySet().stream().collect(Collectors.toMap(
                    entry -> entry.getKey().name(), Map.Entry::getValue,
                    (left, right) -> left, TreeMap::new)));
        }
        return Map.copyOf(result);
    }

    private String jsonl(List<?> values) throws Exception {
        StringBuilder result = new StringBuilder();
        for (Object value : values) {
            result.append(new String(canonicalBytes(value), StandardCharsets.UTF_8)).append('\n');
        }
        return result.toString();
    }

    private static String pairedCsv(List<PairObservation> pairs) {
        StringBuilder value = new StringBuilder(
                "fixture_id,sample_lane,seed_index,seed,marginal,before_profile,after_profile,"
                        + "winner_changed,objective_changed,structure_severity,before_duration,"
                        + "after_duration,duration_delta,public_divergence,direct_causes,"
                        + "causal_classification,indirect_causes,unresolved_snapshot,"
                        + "unexplained\n");
        pairs.stream().sorted(Comparator.comparing(PairObservation::fixtureId)
                        .thenComparing(PairObservation::sampleLane)
                        .thenComparingInt(PairObservation::seedIndex)
                        .thenComparing(PairObservation::kind))
                .forEach(pair -> value.append(pair.fixtureId()).append(',')
                        .append(pair.sampleLane()).append(',').append(pair.seedIndex()).append(',')
                        .append(pair.seed()).append(',').append(pair.kind()).append(',')
                        .append(pair.beforeProfile()).append(',').append(pair.afterProfile()).append(',')
                        .append(pair.winnerChanged()).append(',').append(pair.objectiveChanged())
                        .append(',').append(pair.structureSeverity()).append(',')
                        .append(pair.beforeDurationSeconds()).append(',')
                        .append(pair.afterDurationSeconds()).append(',')
                        .append(pair.durationDeltaSeconds()).append(',')
                        .append(pair.publicDivergence()).append(',')
                        .append(pair.directCauseCount()).append(',')
                        .append(pair.causalClassification()).append(',')
                        .append(pair.indirectCauseCount()).append(',')
                        .append(pair.unresolvedSnapshotCauseCount()).append(',')
                        .append(pair.unexplainedCount()).append('\n'));
        return value.toString();
    }

    private static String segmentedSensitivity(List<PairObservation> pairs) {
        record Key(String fixture, MarginalKind marginal) { }
        Map<Key, List<PairObservation>> grouped = pairs.stream().collect(
                Collectors.groupingBy(value -> new Key(value.fixtureId(), value.kind()),
                        LinkedHashMap::new, Collectors.toList()));
        StringBuilder value = new StringBuilder(
                "fixture_id,marginal,pairs,winner_changed,objective_changed,actual_structure_changed,nexus_or_ending_changed\n");
        grouped.entrySet().stream()
                .sorted(Comparator.comparing(
                                (Map.Entry<Key, List<PairObservation>> entry) ->
                                        entry.getKey().fixture())
                        .thenComparing(entry -> entry.getKey().marginal()))
                .forEach(entry -> {
                    Key key = entry.getKey();
                    List<PairObservation> rows = entry.getValue();
                    value.append(key.fixture()).append(',')
                            .append(key.marginal()).append(',').append(rows.size()).append(',')
                            .append(rows.stream().filter(PairObservation::winnerChanged).count())
                            .append(',')
                            .append(rows.stream().filter(PairObservation::objectiveChanged).count())
                            .append(',')
                            .append(rows.stream().filter(row -> row.structureSeverity()
                                    != StructureSeverity.EXACT && row.structureSeverity()
                                    != StructureSeverity.HP_ONLY).count()).append(',')
                            .append(rows.stream().filter(row -> row.structureSeverity()
                                    == StructureSeverity.NEXUS_TURRET_PROGRESSION
                                    || row.structureSeverity() == StructureSeverity.NEXUS_OR_ENDING)
                                    .count()).append('\n');
                });
        return value.toString();
    }

    private static String analysis(FinalArtifactResult result, Population all) {
        long uniqueDrafts = all.drafts().stream().map(DraftEvidence::finalDraftHash)
                .distinct().count();
        long uniquePicks = all.drafts().stream().map(value ->
                value.bluePicks() + "|" + value.redPicks()).distinct().count();
        Map<Integer, Long> selectedRanks = all.drafts().stream()
                .flatMap(value -> value.selectedRanks().stream())
                .collect(Collectors.groupingBy(value -> value, TreeMap::new, Collectors.counting()));
        return "# Match Engine V9 fresh Auto Draft requalification\n\n"
                + "- production profile: `BASELINE_V1` (unchanged)\n"
                + "- production Auto Drafts: " + result.productionAutoDraftCount() + "\n"
                + "- core simulations: " + result.coreMatchRows() + "\n"
                + "- marginal pairs: " + result.marginalPairRows() + "\n"
                + "- official simulations including replay/instrumentation: "
                + result.officialSimulationCount() + "\n"
                + "- unique complete Draft hashes: " + uniqueDrafts + "\n"
                + "- unique final pick tuples: " + uniquePicks + "\n"
                + "- selected rank distribution: " + selectedRanks + "\n"
                + "- Matchup status: `" + result.decision().matchupStatus() + "`\n"
                + "- Composition status: `" + result.decision().compositionStatus() + "`\n"
                + "- eligible profiles: " + result.eligibleProfiles() + "\n"
                + "- next step: `PRODUCTION_PROFILE_DECISION`\n\n"
                + "Generated evidence is diagnostic output, not a production source or test oracle.\n";
    }

    private static String scheduleCsv(
            MatchEngineV9FreshRequalificationContract.Schedule schedule
    ) {
        StringBuilder value = new StringBuilder(
                "fixture_id,fixture_lane,pair_id,blue_team,red_team,series_game,sample_lane,seed_index,seed\n");
        for (var fixture : schedule.fixtures()) {
            appendSchedule(value, fixture,
                    MatchEngineV9FreshRequalificationContract.SampleLane.CALIBRATION,
                    fixture.calibrationSeeds());
            appendSchedule(value, fixture,
                    MatchEngineV9FreshRequalificationContract.SampleLane.HOLDOUT,
                    fixture.holdoutSeeds());
        }
        return value.toString();
    }

    private static void appendSchedule(
            StringBuilder target,
            MatchEngineV9FreshRequalificationContract.Fixture fixture,
            MatchEngineV9FreshRequalificationContract.SampleLane lane,
            List<Long> seeds
    ) {
        for (int index = 0; index < seeds.size(); index++) {
            target.append(fixture.fixtureId()).append(',').append(fixture.fixtureLane())
                    .append(',').append(fixture.pairId()).append(',')
                    .append(fixture.blueTeamCode()).append(',').append(fixture.redTeamCode())
                    .append(',').append(fixture.seriesGameNumber()).append(',').append(lane)
                    .append(',').append(index).append(',').append(seeds.get(index)).append('\n');
        }
    }

    private byte[] canonicalBytes(Object value) throws Exception {
        byte[] raw = canonical.writeValueAsBytes(value);
        Object normalized = canonical.readValue(raw, Object.class);
        return canonical.writeValueAsBytes(normalized);
    }

    public long gameplayExecutionCount() {
        return gameplayExecutionCount;
    }

    private void writeFinalizerExecutionProof(
            Path output, String finalizer, long executionsBefore) throws Exception {
        long executed = gameplayExecutionCount - executionsBefore;
        if (executed != 0) {
            throw new IllegalStateException("Artifact finalizer executed gameplay: " + executed);
        }
        writeReplace(output.resolve(finalizer.toLowerCase(Locale.ROOT)
                        + "-finalizer-zero-simulation-proof.json"),
                canonicalBytes(Map.of(
                        "schemaVersion",
                        "MATCH_ENGINE_V9_FRESH_FINALIZER_ZERO_SIMULATION_PROOF_V2",
                        "finalizer", finalizer,
                        "coreSimulationCount", 0,
                        "gameplayExecutionCountBefore", executionsBefore,
                        "gameplayExecutionCountAfter", gameplayExecutionCount)));
    }

    private static void writeFrozen(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.exists(path)) {
            if (!java.util.Arrays.equals(Files.readAllBytes(path), bytes)) {
                throw new IllegalStateException("Frozen artifact differs: " + path);
            }
            return;
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp-" + ProcessHandle.current().pid());
        Files.write(temporary, bytes);
        atomicMove(temporary, path);
    }

    static void writeReplace(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp-" + ProcessHandle.current().pid());
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(source, target);
        }
    }

    private void writeCheckpoint(Path path, FixtureCheckpoint checkpoint) throws Exception {
        writeFrozen(path, canonicalBytes(checkpoint));
        writeFrozen(sidecar(path), (fileHash(path) + "  " + path.getFileName() + "\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static Path sidecar(Path path) {
        return path.resolveSibling(path.getFileName() + ".sha256");
    }

    private static Path checkpointDirectory(
            Path output, MatchEngineV9FreshRequalificationContract.SampleLane lane
    ) {
        return output.resolve("checkpoints").resolve(lane.name().toLowerCase(Locale.ROOT));
    }

    private static void requireSidecar(Path path) throws IOException {
        Path sidecar = sidecar(path);
        if (!Files.isRegularFile(path) || !Files.isRegularFile(sidecar)) {
            throw new IllegalStateException("Missing authenticated checkpoint/sidecar: " + path);
        }
        String expected = Files.readString(sidecar, StandardCharsets.UTF_8)
                .trim().split("\\s+")[0];
        if (!fileHash(path).equals(expected)) {
            throw new IllegalStateException("Checkpoint raw-byte hash mismatch: " + path);
        }
    }

    static String fileHash(Path path) throws IOException {
        return MatchEngineV9FreshRequalificationContract.sha256(Files.readAllBytes(path));
    }

    private static String workerJvmIdentity() {
        return MatchEngineV9FreshRequalificationContract.sha256(
                ProcessHandle.current().pid() + "|" + System.getProperty("java.runtime.version")
                        + "|" + System.getProperty("java.vm.name") + "|"
                        + java.lang.management.ManagementFactory.getRuntimeMXBean().getName());
    }

    private static String recursiveManifest(Path output) throws IOException {
        List<Path> files;
        try (var stream = Files.walk(output)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("SHA256SUMS.txt"))
                    .filter(path -> !path.getFileName().toString().contains(".tmp-"))
                    .sorted(Comparator.comparing(path -> output.relativize(path).toString()
                            .replace('\\', '/'))).toList();
        }
        StringBuilder result = new StringBuilder();
        for (Path file : files) {
            result.append(fileHash(file)).append("  ")
                    .append(output.relativize(file).toString().replace('\\', '/'))
                    .append('\n');
        }
        return result.toString();
    }

    public record Binding(
            String contractHash, SourceIdentity sourceIdentity, String seedLedgerHash
    ) { }

    public record SourceIdentity(
            String gitHead,
            String workingTreeStatus,
            Phase13GB1AuditArtifactWriter.SourceTreeIdentity productionSourceTree,
            List<Phase13GB1AuditArtifactWriter.SourceTreeIdentity> diagnosticDependencies,
            String buildGradleHash,
            String combinedSourceHash,
            String engineImplementationVersion,
            String productionPolicyHash
    ) {
        public SourceIdentity {
            diagnosticDependencies = List.copyOf(diagnosticDependencies);
        }
    }

    public record FreezeResult(
            String contractHash,
            String scheduleHash,
            String seedLedgerHash,
            SourceIdentity sourceIdentity,
            MatchEngineV9FreshRequalificationContract.SeedOverlapAudit seedOverlapAudit
    ) { }

    public record FullRegressionReceipt(
            String schemaVersion,
            String contractHash,
            String combinedSourceHash,
            int tests,
            int failures,
            int errors,
            int skipped,
            long durationMillis,
            String recordedAt,
            boolean clean
    ) { }

    public record SmokeResult(
            String contractHash,
            int fixtureCount,
            int productionAutoDraftCount,
            int matchRows,
            boolean replayExact,
            boolean instrumentationExact,
            boolean immutableDraftInputShared,
            boolean matchupReachable,
            boolean compositionReachable,
            boolean finalizerTransformsExact,
            long integrityErrorCount
    ) {
        public boolean clean() {
            return fixtureCount == 2 && productionAutoDraftCount == 2 && matchRows == 6
                    && replayExact && instrumentationExact && immutableDraftInputShared
                    && matchupReachable && compositionReachable && finalizerTransformsExact
                    && integrityErrorCount == 0;
        }
    }

    public record ShardResult(
            MatchEngineV9FreshRequalificationContract.SampleLane sampleLane,
            int shardIndex,
            int fixtureCount,
            int draftCount,
            int rowCount,
            int replayChecks,
            int instrumentationChecks,
            String workerJvmIdentityHash
    ) { }

    public record WorkerReceipt(
            String schemaVersion,
            String contractHash,
            String combinedSourceHash,
            MatchEngineV9FreshRequalificationContract.SampleLane sampleLane,
            int shardIndex,
            int shardCount,
            String workerJvmIdentityHash,
            int fixtureCount,
            int draftCount,
            int rowCount,
            int replayCheckCount,
            int instrumentationCheckCount,
            List<String> checkpointPayloadHashes
    ) {
        public WorkerReceipt {
            checkpointPayloadHashes = List.copyOf(checkpointPayloadHashes);
        }
    }

    public record HoldoutAuthorization(
            String schemaVersion,
            String contractHash,
            String combinedSourceHash,
            String scheduleHash,
            String seedLedgerHash,
            String calibrationReviewHash,
            MatchEngineV9FreshRequalificationContract.AcceptanceGates frozenGates,
            String status,
            boolean holdoutConsumed
    ) { }

    public record CalibrationReview(
            String schemaVersion,
            String contractHash,
            int fixtureCount,
            int seedsPerFixture,
            int profileCount,
            int draftCount,
            int matchRowCount,
            int pairCount,
            int replayCheckCount,
            int instrumentationCheckCount,
            ExactIntegrity exactIntegrity,
            CausalGate matchupCausalGate,
            CausalGate compositionCausalGate,
            Sensitivity observedSensitivity,
            boolean operationalGateClean,
            String frozenGatePolicy
    ) { }

    public record DraftEvidence(
            String schemaVersion,
            String fixtureId,
            MatchEngineV9FreshRequalificationContract.SampleLane sampleLane,
            int seedIndex,
            long seed,
            String selectionPolicyId,
            String selectionPolicyHash,
            String selectionTraceHashAlgorithm,
            String selectionTraceHash,
            String draftDecisionHash,
            String finalDraftHash,
            String finalAssignmentHash,
            String inputHash,
            String rosterIdentityHash,
            String seriesHistoryBeforeHash,
            int selectionTraceCount,
            int productionAutoDraftCount,
            List<Integer> selectedRanks,
            List<Integer> eligiblePoolSizes,
            List<Long> selectedCanonicalScoreLosses,
            List<String> bluePicks,
            List<String> redPicks,
            List<String> finalRoleAssignments
    ) {
        public DraftEvidence {
            selectedRanks = List.copyOf(selectedRanks);
            eligiblePoolSizes = List.copyOf(eligiblePoolSizes);
            selectedCanonicalScoreLosses = List.copyOf(selectedCanonicalScoreLosses);
            bluePicks = List.copyOf(bluePicks);
            redPicks = List.copyOf(redPicks);
            finalRoleAssignments = List.copyOf(finalRoleAssignments);
        }
    }

    public record MatchupEvidence(
            int consumedApplicationCount,
            int nonZeroConsumedApplicationCount,
            int idempotentDuplicateConsumedApplicationCount,
            int duplicateConsumedApplicationErrors,
            int applicationBindingErrors,
            int staleAssignmentParticipantErrors,
            int missingAssignmentErrors,
            int deadParticipantErrors,
            int nonParticipantErrors,
            int sameTeamPairErrors,
            int crossPositionErrors,
            int duplicateApplicationErrors,
            int staleStateErrors,
            int directRandomCalls,
            double aggregateFinalEdge,
            List<String> coveredPositions,
            List<ChampionMatchupApplicationProvenance> applications,
            List<com.lolfm.champion.ChampionMatchupStateConsumerProvenance> stateConsumers
    ) {
        public MatchupEvidence {
            coveredPositions = List.copyOf(coveredPositions);
            applications = List.copyOf(applications);
            stateConsumers = List.copyOf(stateConsumers);
        }

        public long bindingErrorCount() {
            return (long) applicationBindingErrors + staleAssignmentParticipantErrors
                    + missingAssignmentErrors
                    + deadParticipantErrors + nonParticipantErrors + sameTeamPairErrors
                    + crossPositionErrors + staleStateErrors;
        }

        public long duplicateErrorCount() {
            return (long) duplicateConsumedApplicationErrors + duplicateApplicationErrors;
        }
    }

    public record CompositionEvidence(
            String mode,
            boolean initialized,
            int actualAttemptCount,
            int mappedActualAttemptCount,
            int unmappedActualAttemptCount,
            int modifierCalculatedCount,
            int scalarAppliedCount,
            int modifierConsumedCount,
            int nonZeroScalarApplicationCount,
            int existingNonScalarEffectConsumedCount,
            int totalCompositionEffectApplicationCount,
            int gameplayApplicationCount,
            int nonZeroModifierCount,
            int objectiveSetupScalarApplicationCount,
            int duplicateObservationCount,
            int multiContextAttemptCount,
            int conflictingPerspectiveCount,
            int duplicateApplicationPointCount,
            int duplicatePublicBindingCount,
            int conflictingPublicBindingCount,
            int decompositionErrorCount,
            int directRandomCalls,
            int compositionRandomDraws,
            int publicActionBindingCount,
            Map<String, Long> applicationsByContext,
            List<CompositionApplicationProvenance> applications
    ) {
        public CompositionEvidence {
            applicationsByContext = Map.copyOf(applicationsByContext);
            applications = List.copyOf(applications);
        }

        public long causalErrorCount() {
            return (long) unmappedActualAttemptCount + duplicateObservationCount
                    + multiContextAttemptCount + conflictingPerspectiveCount
                    + duplicateApplicationPointCount + duplicatePublicBindingCount
                    + conflictingPublicBindingCount + decompositionErrorCount;
        }
    }

    public record IntegrityObservation(
            int timeoutCount,
            long domainIntegrityErrors,
            long structuredBindingErrors,
            int invalidStructureState,
            int nexusOrderingErrors,
            int postFinishMutationOrEvent,
            int supportFarmCsErrors,
            int matchupDirectRandomCalls,
            int compositionDirectRandomCalls,
            int compositionRandomDraws
    ) {
        public long errorCount() {
            return timeoutCount + domainIntegrityErrors + structuredBindingErrors
                    + invalidStructureState + nexusOrderingErrors + postFinishMutationOrEvent
                    + supportFarmCsErrors + matchupDirectRandomCalls
                    + compositionDirectRandomCalls + compositionRandomDraws;
        }
    }

    public record StructureProgression(
            int blueTowersDestroyed,
            int redTowersDestroyed,
            int blueInhibitorsRemaining,
            int redInhibitorsRemaining,
            int blueNexusTurretsRemaining,
            int redNexusTurretsRemaining,
            boolean blueNexusAlive,
            boolean redNexusAlive
    ) { }

    public record StructureObservation(
            StructureProgression progression, String fullStateHash
    ) { }

    public record MatchRow(
            String schemaVersion,
            String fixtureId,
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String pairId,
            String blueTeamCode,
            String redTeamCode,
            int seriesGameNumber,
            MatchEngineV9FreshRequalificationContract.SampleLane sampleLane,
            int seedIndex,
            long seed,
            int profileIndex,
            SimulationRuntimeProfileId profileId,
            String configurationHash,
            String activeGameplayRulesVersion,
            String engineImplementationVersion,
            String resourceProvenanceHash,
            String inputHash,
            String rosterIdentityHash,
            String seriesHistoryBeforeHash,
            String draftSelectionPolicyId,
            String draftSelectionPolicyHash,
            String draftSelectionTraceHashAlgorithm,
            String draftSelectionTraceHash,
            String draftDecisionHash,
            String finalDraftHash,
            String finalAssignmentHash,
            String replayProvenanceHash,
            String timelineHash,
            SimulationRandomFingerprint randomFingerprint,
            String winnerTeamCode,
            TeamSide winnerSide,
            GameEndReason endReason,
            int durationSeconds,
            int blueKills,
            int redKills,
            int blueGold,
            int redGold,
            int blueDragons,
            int redDragons,
            String objectiveSignature,
            StructureObservation structure,
            MatchupEvidence matchup,
            CompositionEvidence composition,
            IntegrityObservation integrity,
            String structuredDiagnosticsHash,
            Map<String, String> diagnosticsComponentHashes,
            String payloadDigest
    ) {
        public MatchRow {
            diagnosticsComponentHashes = Map.copyOf(diagnosticsComponentHashes);
        }
    }

    public enum MarginalKind { MATCHUP_MINUS_BASELINE, FULL_MINUS_MATCHUP }

    public enum CausalClassification {
        NO_PUBLIC_DIVERGENCE,
        EXACT_DIRECT_ACTION_CAUSE,
        INDIRECT_PRIOR_STATE_CAUSE,
        UNRESOLVED_SNAPSHOT_CAUSE,
        UNEXPLAINED_PUBLIC_DIVERGENCE
    }

    public enum StructureSeverity {
        EXACT,
        HP_ONLY,
        LANE_TOWER_PROGRESSION,
        INHIBITOR_PROGRESSION,
        NEXUS_TURRET_PROGRESSION,
        NEXUS_OR_ENDING
    }

    public record PairObservation(
            String schemaVersion,
            String fixtureId,
            MatchEngineV9FreshRequalificationContract.SampleLane sampleLane,
            int seedIndex,
            long seed,
            MarginalKind kind,
            SimulationRuntimeProfileId beforeProfile,
            SimulationRuntimeProfileId afterProfile,
            String inputHash,
            String selectionTraceHash,
            String finalDraftHash,
            String finalAssignmentHash,
            TeamSide beforeWinnerSide,
            TeamSide afterWinnerSide,
            boolean winnerChanged,
            boolean objectiveChanged,
            StructureSeverity structureSeverity,
            int beforeDurationSeconds,
            int afterDurationSeconds,
            int durationDeltaSeconds,
            boolean beforeTimeout,
            boolean afterTimeout,
            boolean publicDivergence,
            int firstPublicDivergenceSeconds,
            CausalClassification causalClassification,
            int directCauseCount,
            int indirectCauseCount,
            int unresolvedSnapshotCauseCount,
            int unexplainedCount,
            List<String> divergenceActionIds,
            List<com.lolfm.simulator.ProgressionCombatContext> divergenceContexts,
            List<com.lolfm.simulator.ProgressionApplicationStage> divergenceStages
    ) {
        public PairObservation {
            Objects.requireNonNull(causalClassification, "causalClassification");
            divergenceActionIds = canonicalActionIds(divergenceActionIds);
            divergenceContexts = divergenceContexts.stream()
                    .distinct().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
            divergenceStages = divergenceStages.stream()
                    .distinct().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
            int classified = (directCauseCount > 0 ? 1 : 0)
                    + (indirectCauseCount > 0 ? 1 : 0)
                    + (unresolvedSnapshotCauseCount > 0 ? 1 : 0)
                    + (unexplainedCount > 0 ? 1 : 0);
            if (publicDivergence != (classified == 1)) {
                throw new IllegalArgumentException("Public divergence cause must be exclusive");
            }
        }
    }

    private record Divergence(
            boolean present,
            int timeSeconds,
            List<String> actionIds,
            List<com.lolfm.simulator.ProgressionCombatContext> contexts,
            List<com.lolfm.simulator.ProgressionApplicationStage> stages
    ) {
        private Divergence {
            actionIds = canonicalActionIds(actionIds);
            contexts = contexts.stream().distinct()
                    .sorted(Comparator.comparingInt(Enum::ordinal)).toList();
            stages = stages.stream().distinct()
                    .sorted(Comparator.comparingInt(Enum::ordinal)).toList();
        }
    }

    public record ReplayCheck(
            String fixtureId,
            long seed,
            SimulationRuntimeProfileId profileId,
            boolean exact,
            String firstTimelineHash,
            String replayTimelineHash,
            SimulationRandomFingerprint firstRandom,
            SimulationRandomFingerprint replayRandom
    ) { }

    public record InstrumentationCheck(
            String fixtureId,
            long seed,
            SimulationRuntimeProfileId profileId,
            boolean exact,
            String enabledTimelineHash,
            String disabledTimelineHash,
            SimulationRandomFingerprint enabledRandom,
            SimulationRandomFingerprint disabledRandom
    ) { }

    public record FixtureCheckpoint(
            String schemaVersion,
            String contractHash,
            String combinedSourceHash,
            int fixtureIndex,
            String fixtureId,
            MatchEngineV9FreshRequalificationContract.SampleLane sampleLane,
            String workerJvmIdentityHash,
            String payloadDigest,
            List<DraftEvidence> drafts,
            List<MatchRow> rows,
            List<PairObservation> pairs,
            List<ReplayCheck> replayChecks,
            List<InstrumentationCheck> instrumentationChecks
    ) {
        public FixtureCheckpoint {
            drafts = List.copyOf(drafts);
            rows = List.copyOf(rows);
            pairs = List.copyOf(pairs);
            replayChecks = List.copyOf(replayChecks);
            instrumentationChecks = List.copyOf(instrumentationChecks);
        }
    }

    private record Population(
            List<DraftEvidence> drafts,
            List<MatchRow> rows,
            List<PairObservation> pairs,
            List<ReplayCheck> replayChecks,
            List<InstrumentationCheck> instrumentationChecks
    ) {
        private Population {
            drafts = List.copyOf(drafts);
            rows = List.copyOf(rows);
            pairs = List.copyOf(pairs);
            replayChecks = List.copyOf(replayChecks);
            instrumentationChecks = List.copyOf(instrumentationChecks);
        }
    }

    public record CausalGate(
            String candidate,
            boolean pass,
            Map<String, Long> counters,
            List<String> structuredCoverage,
            List<String> failureReasons
    ) {
        public CausalGate {
            counters = Map.copyOf(counters);
            structuredCoverage = List.copyOf(structuredCoverage);
            failureReasons = List.copyOf(failureReasons);
        }
    }

    public record ExactIntegrity(
            int rowCount,
            long timeoutCount,
            long domainIntegrityErrors,
            long structuredBindingErrors,
            long invalidStructureStateCount,
            long nexusOrderingErrorCount,
            long postFinishMutationCount,
            long supportFarmCsErrorCount,
            long matchupDirectRandomCalls,
            long compositionDirectRandomCalls,
            long compositionRandomDraws,
            long replayMismatchCount,
            long instrumentationMismatchCount,
            boolean pass
    ) { }

    public record Sensitivity(Marginal matchupMinusBaseline, Marginal fullMinusMatchup) { }

    public record Marginal(
            MarginalKind kind,
            int pairCount,
            double blueWinRateDeltaPercentagePoints,
            double directionalWinnerFlipImbalancePercentagePoints,
            double pairedWinnerChangedRatePercent,
            double objectiveChangedRatePercent,
            double actualStructureProgressionChangedRatePercent,
            double nexusOrEndingProgressionChangedRatePercent,
            double meanDurationDeltaSeconds,
            double aggregateP95DurationDeltaSeconds,
            long timeoutIncrease,
            boolean macroSafetyPass
    ) { }

    public record ProfileDecision(
            String baselineStatus,
            String matchupStatus,
            String compositionStatus,
            String recommendation,
            List<SimulationRuntimeProfileId> eligibleProfiles,
            Map<String, String> rejectedProfiles,
            String nextStep
    ) {
        public ProfileDecision {
            eligibleProfiles = List.copyOf(eligibleProfiles);
            rejectedProfiles = Map.copyOf(rejectedProfiles);
        }
    }

    public record FinalArtifactResult(
            String schemaVersion,
            String contractHash,
            int productionAutoDraftCount,
            int coreMatchRows,
            int marginalPairRows,
            int replayChecks,
            int instrumentationChecks,
            int officialSimulationCount,
            boolean baselineStable,
            CausalGate matchupCausalGate,
            CausalGate compositionCausalGate,
            Sensitivity calibrationSensitivity,
            Sensitivity holdoutSensitivity,
            List<SimulationRuntimeProfileId> eligibleProfiles,
            ProfileDecision decision,
            String manifestIdentity
    ) {
        public FinalArtifactResult {
            eligibleProfiles = List.copyOf(eligibleProfiles);
        }
    }

    public record HoldoutCompletion(
            String schemaVersion,
            String contractHash,
            int fixtureCount,
            int draftCount,
            int matchRowCount,
            boolean consumed,
            String finalRecommendationHash
    ) { }
}
