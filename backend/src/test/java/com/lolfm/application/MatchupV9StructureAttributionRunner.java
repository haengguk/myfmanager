package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.application.MatchupV9StructureAttributionClassifier.Comparison;
import com.lolfm.application.MatchupV9StructureAttributionClassifier.FinalState;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Position;
import com.lolfm.domain.StructureActionData;
import com.lolfm.domain.StructureActionPhase;
import com.lolfm.domain.StructureStateSnapshot;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.MatchEngineV9InstrumentationExecutor;
import com.lolfm.simulator.Phase13GB1SimulationExecutor;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import com.lolfm.simulator.StructureActionSource;
import com.lolfm.simulator.StructureKind;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.TowerTier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Test-only attribution runner. It observes timelines and never changes gameplay. */
public final class MatchupV9StructureAttributionRunner {
    public static final Path OUTPUT = Path.of(
            "build", "reports", "matchup-v9-structure-effect-attribution-v1");
    public static final int SHARD_COUNT = 4;
    private static final String BUILD_START =
            "// MATCHUP_V9_STRUCTURE_ATTRIBUTION_BUILD_CONTRACT_START";
    private static final String BUILD_END =
            "// MATCHUP_V9_STRUCTURE_ATTRIBUTION_BUILD_CONTRACT_END";
    private static final String CHECKPOINT_SCHEMA =
            "MATCHUP_V9_STRUCTURE_ATTRIBUTION_SHARD_CHECKPOINT_V1";

    private final ObjectMapper mapper;
    private final ObjectMapper canonical;
    private final Phase13GB1RealMatchHarness draftHarness;
    private final ConfiguredMatchSimulatorFactory simulators;
    private final SimulationProvenanceService provenance;
    private final PlayerIdentityCatalog identities;

    public MatchupV9StructureAttributionRunner(
            RealDraftMatchOrchestrator orchestrator,
            ConfiguredMatchSimulatorFactory simulators,
            ObjectMapper mapper,
            com.lolfm.champion.ChampionCatalog champions,
            PlayerIdentityCatalog identities,
            PlayerRatingCatalog ratings,
            ChampionProficiencyCatalog proficiencies
    ) {
        this.mapper = Objects.requireNonNull(mapper);
        canonical = mapper.copy().findAndRegisterModules()
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
        var schedule = MatchupV9StructureAttributionContract.requireFrozen(
                MatchupV9StructureAttributionContract.schedule());
        var overlap = MatchupV9StructureAttributionContract.requireNoSeedOverlap(schedule);
        var predecessor = MatchupV9StructureAttributionEvidence.verify(backendRoot, mapper);
        SourceIdentity source = sourceIdentity(backendRoot);
        LinkedHashMap<String, Object> contract = new LinkedHashMap<>();
        contract.put("schemaVersion", MatchupV9StructureAttributionContract.CONTRACT_SCHEMA);
        contract.put("taskName", "MATCHUP_V9_STRUCTURE_EFFECT_ATTRIBUTION_AND_ACCEPTANCE_CONTRACT_REDESIGN");
        contract.put("currentHead", source.gitHead());
        contract.put("referencePredecessorCommit", "66fe9b385423e9fa3623a463347d0c78a650e33a");
        contract.put("referenceStructureBaselineCommit", "c2814b63b6fa40487f893c510fbe5868e508724a");
        contract.put("engineImplementationVersion", SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION);
        contract.put("activeGameplayRulesVersion", SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION);
        contract.put("productionPolicy", MatchEngineV1Policy.authoritative());
        contract.put("profiles", profileBindings());
        contract.put("resourceProvenance", provenance.resourceProvenance());
        contract.put("sourceIdentity", source);
        contract.put("predecessorEvidence", predecessor);
        contract.put("scheduleHash", schedule.scheduleHash());
        contract.put("seedNamespace", schedule.seedNamespace());
        contract.put("seedBindingHash", schedule.seedBindingHash());
        contract.put("seedConsumptionStatus", schedule.consumptionStatus());
        contract.put("fixtureCount", 100);
        contract.put("seedsPerFixture", 4);
        contract.put("profileCount", 2);
        contract.put("matchRowCount", 800);
        contract.put("pairedComparisonCount", 400);
        contract.put("draftReusePolicy", "ONE_PRODUCTION_DRAFT_PER_FIXTURE_SHARED_BY_BOTH_PROFILES_AND_ALL_SEEDS");
        contract.put("severityPriority", List.of(
                "NEXUS_OR_ENDING", "NEXUS_TURRET_PROGRESSION", "INHIBITOR_PROGRESSION",
                "LANE_TOWER_PROGRESSION", "HP_ONLY", "EXACT"));
        contract.put("correctnessExactGates", List.of(
                "INVALID_NON_FINITE_OUT_OF_RANGE_STRUCTURE_HP_0",
                "DUPLICATE_STRUCTURED_STRUCTURE_ACTION_MUTATION_EVENT_0",
                "NEXUS_DESTROYED_WITH_TURRET_ALIVE_0",
                "POST_FINISH_STRUCTURE_MUTATION_EVENT_0",
                "IMPOSSIBLE_RESPAWN_STATE_TRANSITION_0",
                "DISPLAY_NAME_BASED_STRUCTURE_IDENTITY_0",
                "INELIGIBLE_DUPLICATE_STRUCTURE_RANDOM_CONSUMPTION_0"));
        contract.put("causalProvenancePolicy",
                "AGGREGATE_MATCHUP_APPLICATIONS_ONLY; APPLICATION_TIME_POSITION_CONTEXT_UNAVAILABLE; DO_NOT_INFER_CAUSATION_FROM_TEMPORAL_ORDER");
        contract.put("acceptanceThresholdPolicy",
                "CALIBRATION_OBSERVATION_ONLY; NUMERIC_MACRO_THRESHOLDS_REQUIRE_PRODUCT_DECISION_AND_FRESH_HOLDOUT");
        contract.put("productionActivation", false);
        contract.put("gameplayTuning", false);
        contract.put("predecessorRecommendationPreserved", "RECOMMEND_BASELINE_V1");

        Files.createDirectories(output);
        byte[] contractBytes = canonicalBytes(contract);
        String contractHash = MatchupV9StructureAttributionContract.sha256(contractBytes);
        writeFrozen(output.resolve("attribution-contract.json"), contractBytes);
        writeFrozen(output.resolve("attribution-contract.sha256"),
                (contractHash + "  attribution-contract.json\n").getBytes(StandardCharsets.UTF_8));
        writeFrozen(output.resolve("source-input-binding.json"), canonicalBytes(
                new SourceInputBinding("MATCHUP_V9_STRUCTURE_ATTRIBUTION_SOURCE_INPUT_BINDING_V1",
                        contractHash, source, predecessor, MatchEngineV1Policy.authoritative(),
                        profileBindings(), provenance.resourceProvenance(),
                        identities.version(), identities.resourceSha256(), identities.all().size(),
                        true, "RECOMMEND_BASELINE_V1")));
        writeFrozen(output.resolve("seed-overlap-audit.json"), canonicalBytes(overlap));
        writeFrozen(output.resolve("frozen-attribution-schedule.json"), canonicalBytes(schedule));
        writeFrozen(output.resolve("frozen-attribution-schedule.csv"),
                scheduleCsv(schedule).getBytes(StandardCharsets.UTF_8));
        return new FreezeResult(contractHash, schedule.scheduleHash(), source, predecessor, overlap);
    }

    public ShardResult runShard(Path backendRoot, Path output, int shardIndex) throws Exception {
        if (shardIndex < 0 || shardIndex >= SHARD_COUNT) throw new IllegalArgumentException();
        Binding binding = requireBinding(backendRoot, output);
        ArrayList<PairRow> pairs = new ArrayList<>();
        int fixtures = 0;
        int replayChecks = 0;
        int instrumentationChecks = 0;
        List<MatchupV9StructureAttributionContract.Fixture> schedule =
                MatchupV9StructureAttributionContract.schedule().fixtures();
        for (int fixtureIndex = shardIndex; fixtureIndex < schedule.size(); fixtureIndex += SHARD_COUNT) {
            var fixture = schedule.get(fixtureIndex);
            var prepared = draftHarness.prepareFixture(
                    MatchupV9StructureAttributionContract.sourceFixture(fixture));
            RealDraftMatchResult fixed = prepared.realDraftFixture();
            FixedInput input = fixedInput(fixed);
            for (int seedIndex = 0; seedIndex < fixture.seeds().size(); seedIndex++) {
                long seed = fixture.seeds().get(seedIndex);
                Executed baseline = execute(prepared, fixture, seed, SimulationRuntimeProfileId.BASELINE_V1);
                Executed matchup = execute(prepared, fixture, seed,
                        SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
                Verification verification = Verification.notChecked();
                if (seedIndex == 0) {
                    Executed replay = execute(prepared, fixture, seed, SimulationRuntimeProfileId.BASELINE_V1);
                    boolean replayExact = exact(baseline, replay);
                    boolean baselineInstrumentation = instrumentationExact(fixed, fixture, seed, baseline);
                    boolean matchupInstrumentation = instrumentationExact(fixed, fixture, seed, matchup);
                    verification = new Verification(true, replayExact, 2,
                            baselineInstrumentation && matchupInstrumentation);
                    replayChecks++;
                    instrumentationChecks += 2;
                }
                PairRow row = pair(fixture, fixtureIndex, seedIndex, seed, input,
                        baseline, matchup, verification);
                if (!row.inputIdentityExact() || !row.correctness().pass()
                        || (verification.replayChecked() && !verification.replayExact())
                        || (verification.instrumentationProfilesChecked() > 0
                        && !verification.instrumentationTimelineRandomExact())) {
                    throw new IllegalStateException("Attribution exact gate failed: "
                            + fixture.fixtureId() + ":" + seedIndex
                            + " input=" + row.inputIdentityExact()
                            + " correctness=" + row.correctness()
                            + " verification=" + row.verification());
                }
                pairs.add(row);
            }
            fixtures++;
            System.out.printf(Locale.ROOT,
                    "MATCHUP_V9_STRUCTURE_ATTRIBUTION shard=%d/%d fixture=%d/100 %s pairs=%d%n",
                    shardIndex + 1, SHARD_COUNT, fixtureIndex + 1, fixture.fixtureId(),
                    fixture.seeds().size());
        }
        ShardCheckpoint checkpoint = new ShardCheckpoint(
                CHECKPOINT_SCHEMA, binding.contractHash(),
                binding.sourceIdentity().attributionHarnessSourceTree().hash(),
                shardIndex, SHARD_COUNT, workerJvmIdentity(), fixtures,
                pairs.size() * 2, pairs.size(), replayChecks, instrumentationChecks,
                List.copyOf(pairs));
        Path path = output.resolve("checkpoints").resolve("shard-" + shardIndex + ".json");
        writeFrozen(path, canonicalBytes(checkpoint));
        writeFrozen(sidecar(path), (fileHash(path) + "  " + path.getFileName() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        return new ShardResult(shardIndex, fixtures, pairs.size(), replayChecks,
                instrumentationChecks, checkpoint.workerJvmIdentityHash());
    }

    public FinalizationResult finalizeArtifacts(Path backendRoot, Path output) throws Exception {
        Binding binding = requireBinding(backendRoot, output);
        ArrayList<PairRow> pairs = new ArrayList<>();
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            Path path = output.resolve("checkpoints").resolve("shard-" + shard + ".json");
            if (!fileHash(path).equals(firstHash(sidecar(path)))) {
                throw new IllegalStateException("Attribution checkpoint SHA mismatch");
            }
            ShardCheckpoint checkpoint = canonical.readValue(path.toFile(), ShardCheckpoint.class);
            if (!CHECKPOINT_SCHEMA.equals(checkpoint.schemaVersion())
                    || !binding.contractHash().equals(checkpoint.contractHash())
                    || !binding.sourceIdentity().attributionHarnessSourceTree().hash().equals(
                    checkpoint.harnessSourceHash()) || checkpoint.shardIndex() != shard) {
                throw new IllegalStateException("Attribution checkpoint binding mismatch");
            }
            pairs.addAll(checkpoint.pairs());
        }
        pairs.sort(Comparator.comparingInt(PairRow::fixtureIndex)
                .thenComparingInt(PairRow::seedIndex));
        if (pairs.size() != MatchupV9StructureAttributionContract.EXPECTED_PAIRS
                || pairs.stream().map(PairRow::pairKey).distinct().count() != pairs.size()) {
            throw new IllegalStateException("Attribution pair coverage mismatch");
        }
        return MatchupV9StructureAttributionArtifactWriter.write(
                canonical, backendRoot, output, binding, List.copyOf(pairs));
    }

    private PairRow pair(
            MatchupV9StructureAttributionContract.Fixture fixture,
            int fixtureIndex,
            int seedIndex,
            long seed,
            FixedInput input,
            Executed baseline,
            Executed matchup,
            Verification verification
    ) throws Exception {
        SimulationExecutionProvenance a = baseline.provenance();
        SimulationExecutionProvenance b = matchup.provenance();
        boolean inputExact = a.rosterIdentityHash().equals(b.rosterIdentityHash())
                && a.seriesHistoryBeforeHash().equals(b.seriesHistoryBeforeHash())
                && a.draftDecisionHash().equals(b.draftDecisionHash())
                && a.finalDraftHash().equals(b.finalDraftHash())
                && a.finalAssignmentHash().equals(b.finalAssignmentHash())
                && a.matchSeed() == b.matchSeed()
                && a.matchSeed() == seed
                && a.resourceProvenance().resourceProvenanceHash().equals(
                b.resourceProvenance().resourceProvenanceHash());
        RunSummary before = summarize(baseline);
        RunSummary after = summarize(matchup);
        Comparison finalComparison = MatchupV9StructureAttributionClassifier.compare(
                before.finalStructureState(), after.finalStructureState());
        TimingDifference timing = timingDifference(before.structureTimeline(),
                after.structureTimeline());
        Divergence divergence = divergences(
                baseline.execution().timeline(), matchup.execution().timeline());
        Correctness correctness = correctness(before, after);
        boolean winnerChanged = baseline.execution().winnerSide() != matchup.execution().winnerSide();
        boolean objectiveChanged = !objectiveSignature(baseline.execution().timeline()).equals(
                objectiveSignature(matchup.execution().timeline()));
        return new PairRow(
                "MATCHUP_V9_STRUCTURE_ATTRIBUTION_PAIRED_COMPONENTS_V1",
                fixtureIndex, fixture.fixtureId(), fixture.fixtureLane(), fixture.pairId(),
                fixture.blueTeamCode(), fixture.redTeamCode(), fixture.seriesGameNumber(),
                seedIndex, seed, pairKey(fixture.fixtureId(), seedIndex, seed), input,
                a.rosterIdentityHash(), a.seriesHistoryBeforeHash(), a.draftDecisionHash(),
                a.finalDraftHash(), a.finalAssignmentHash(), inputExact,
                before, after, finalComparison, timing, divergence,
                winnerChanged, objectiveChanged,
                after.durationSeconds() - before.durationSeconds(), correctness, verification,
                new LocalAttribution(
                        before.matchupApplications(), after.matchupApplications(),
                        before.matchupEdgeSum(), after.matchupEdgeSum(),
                        before.matchupDirectRandomCalls() + after.matchupDirectRandomCalls(),
                        after.matchupPerspectiveMismatchErrors(),
                        "CAUSAL_PROVENANCE_UNAVAILABLE",
                        "MATCHUP_APPLICATION_TIME_POSITION_CONTEXT_NOT_RETAINED",
                        -1, divergence.firstPublicTimelineDivergenceSeconds(),
                        divergence.firstCombatDivergenceSeconds(),
                        divergence.firstPressureDivergenceSeconds(),
                        divergence.firstEconomyDivergenceSeconds(),
                        divergence.firstStructureDivergenceSeconds()));
    }

    private Executed execute(
            Phase13GB1RealMatchHarness.PreparedFixture prepared,
            MatchupV9StructureAttributionContract.Fixture fixture,
            long seed,
            SimulationRuntimeProfileId profile
    ) {
        RealDraftMatchResult fixed = prepared.realDraftFixture();
        var result = Phase13GB1SimulationExecutor.execute(
                simulators, fixed.blueTeam(), fixed.redTeam(), fixed.matchChampionAssignments(),
                profile, seed, fixture.blueTeamCode(), fixture.redTeamCode());
        var executionProvenance = provenance.create(
                SimulationRuntimeProfiles.resolve(profile), SimulationInstrumentation.enabled(),
                fixture.blueTeamCode(), fixed.blueTeam(), fixture.redTeamCode(), fixed.redTeam(),
                seed, fixture.seriesGameNumber(), fixed.hardFearlessExclusionsBeforeDraft(),
                fixed.draftResult(), result.timeline(), result.randomFingerprint());
        return new Executed(profile, result, executionProvenance);
    }

    private boolean instrumentationExact(
            RealDraftMatchResult fixed,
            MatchupV9StructureAttributionContract.Fixture fixture,
            long seed,
            Executed enabled
    ) {
        var disabled = MatchEngineV9InstrumentationExecutor.execute(
                simulators, fixed.blueTeam(), fixed.redTeam(), fixed.matchChampionAssignments(),
                enabled.profileId(), SimulationInstrumentation.disabled(), seed,
                fixture.blueTeamCode(), fixture.redTeamCode());
        return provenance.timelineHash(disabled.timeline()).equals(enabled.provenance().timelineHash())
                && disabled.randomFingerprint().equals(enabled.execution().randomFingerprint());
    }

    private static boolean exact(Executed first, Executed second) {
        return first.provenance().timelineHash().equals(second.provenance().timelineHash())
                && first.execution().randomFingerprint().equals(second.execution().randomFingerprint())
                && Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                first.execution().structuredDiagnostics()).equals(
                Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                        second.execution().structuredDiagnostics()));
    }

    private RunSummary summarize(Executed run) {
        MatchTimeline timeline = run.execution().timeline();
        MatchSnapshot end = timeline.getSnapshots().getLast();
        var diagnostics = run.execution().structuredDiagnostics();
        var gameplayIntegrity = Phase13GB1RealMatchHarness.IntegrityDiagnostics.from(
                SimulationRuntimeProfiles.resolve(run.profileId()).gameplayConfiguration(), diagnostics);
        StructureValidation validation = validateStructureTimeline(timeline);
        var matchup = diagnostics.championMatchup();
        return new RunSummary(
                run.profileId(), run.provenance().configurationHash(),
                run.provenance().replayProvenanceHash(), run.provenance().timelineHash(),
                run.execution().randomFingerprint().randomDrawCount(),
                run.execution().randomFingerprint().randomTraceHash(),
                run.execution().winnerSide(), timeline.getWinner(), run.execution().endReason(),
                timeline.getDurationSeconds(), objectiveSignature(timeline),
                MatchupV9StructureAttributionClassifier.project(end.getStructureState()),
                structureTimeline(timeline), matchup.nonZeroContributionApplications(),
                matchup.finalMatchupEdgeSum(), matchup.directRandomCalls(),
                matchup.featureOffMismatch() + matchup.mirrorMismatch(),
                gameplayIntegrity.errorCount(), validation,
                Phase13GB1SimulationExecutor.structuredDiagnosticsHash(diagnostics));
    }

    private static Correctness correctness(RunSummary before, RunSummary after) {
        long timeout = (before.endReason() == GameEndReason.SIMULATION_TIMEOUT ? 1 : 0)
                + (after.endReason() == GameEndReason.SIMULATION_TIMEOUT ? 1 : 0);
        long gameplay = before.gameplayIntegrityErrors() + after.gameplayIntegrityErrors();
        long invalid = before.structureValidation().invalidHealth()
                + after.structureValidation().invalidHealth();
        long duplicate = before.structureValidation().duplicateStructureActionIds()
                + after.structureValidation().duplicateStructureActionIds();
        long nexus = before.structureValidation().nexusDestroyedWithTurretAlive()
                + after.structureValidation().nexusDestroyedWithTurretAlive();
        long post = before.structureValidation().postFinishMutationOrEvent()
                + after.structureValidation().postFinishMutationOrEvent();
        long respawn = before.structureValidation().impossibleRespawnOrStateTransition()
                + after.structureValidation().impossibleRespawnOrStateTransition();
        long random = before.matchupDirectRandomCalls() + after.matchupDirectRandomCalls();
        long perspective = before.matchupPerspectiveMismatchErrors()
                + after.matchupPerspectiveMismatchErrors();
        long off = before.matchupApplications();
        return new Correctness(timeout, gameplay, invalid, duplicate, nexus, post, respawn,
                0, 0, random, perspective, off,
                timeout + gameplay + invalid + duplicate + nexus + post + respawn
                        + random + perspective + off == 0);
    }

    private static StructureValidation validateStructureTimeline(MatchTimeline timeline) {
        int invalid = 0;
        int duplicateIds = 0;
        int nexus = 0;
        int post = 0;
        int impossible = 0;
        Set<String> ids = new HashSet<>();
        Map<String, Boolean> alive = new HashMap<>();
        for (MatchEvent event : timeline.getEvents()) {
            StructureActionData action = event.getStructureAction();
            if (action == null) continue;
            if (event.getTimeSeconds() > timeline.getDurationSeconds()) post++;
            if (event.getActionId() == null || !ids.add(event.getActionId())) duplicateIds++;
            if (!finiteHealth(action)) invalid++;
            boolean currentAlive = alive.getOrDefault(action.targetId(), true);
            if (!currentAlive && action.structureKind() == StructureKind.INHIBITOR
                    && action.phase() != StructureActionPhase.RESPAWNED
                    && action.healthBefore() > 0.0) {
                // Inhibitors refresh in match-scoped state without a lifecycle event.
                // A positive structured healthBefore is the observable respawn boundary.
                currentAlive = true;
                alive.put(action.targetId(), true);
            }
            if (action.phase() == StructureActionPhase.DESTROYED) {
                if (!currentAlive) impossible++;
                alive.put(action.targetId(), false);
            } else if (action.phase() == StructureActionPhase.RESPAWNED) {
                if (action.structureKind() != StructureKind.NEXUS_TURRET || currentAlive) impossible++;
                alive.put(action.targetId(), true);
            } else if (!currentAlive && action.damage() > 0.0) {
                impossible++;
            }
        }
        for (MatchSnapshot snapshot : timeline.getSnapshots()) {
            if (snapshot.getTimeSeconds() > timeline.getDurationSeconds()) post++;
            StructureStateSnapshot state = snapshot.getStructureState();
            for (var team : state.teams().values()) {
                invalid += invalidHealth(team.nexusCurrentHealth(), team.nexusMaxHealth());
                for (double health : team.nexusTurretCurrentHealth()) {
                    invalid += invalidHealth(health, team.nexusTurretMaxHealth());
                }
                for (var lane : team.lanes().values()) {
                    invalid += invalidHealth(lane.outerTower().current(), lane.outerTower().maximum());
                    invalid += invalidHealth(lane.innerTower().current(), lane.innerTower().maximum());
                    invalid += invalidHealth(lane.inhibitorTower().current(), lane.inhibitorTower().maximum());
                    invalid += invalidHealth(lane.inhibitor().current(), lane.inhibitor().maximum());
                }
                if (!team.nexusAlive() && team.nexusTurretsRemaining() > 0) nexus++;
            }
        }
        return new StructureValidation(invalid, duplicateIds, nexus, post, impossible);
    }

    private static boolean finiteHealth(StructureActionData action) {
        return Double.isFinite(action.healthBefore()) && Double.isFinite(action.damage())
                && Double.isFinite(action.healthAfter()) && Double.isFinite(action.maxHealth())
                && action.maxHealth() > 0.0 && action.healthBefore() >= -1.0e-9
                && action.healthBefore() <= action.maxHealth() + 1.0e-9
                && action.damage() >= -1.0e-9 && action.healthAfter() >= -1.0e-9
                && action.healthAfter() <= action.maxHealth() + 1.0e-9;
    }

    private static int invalidHealth(double current, double maximum) {
        return !Double.isFinite(current) || !Double.isFinite(maximum) || maximum <= 0.0
                || current < -1.0e-9 || current > maximum + 1.0e-9 ? 1 : 0;
    }

    private static StructureTimeline structureTimeline(MatchTimeline timeline) {
        Milestone firstDamage = Milestone.none();
        Milestone firstTower = Milestone.none();
        Milestone firstInhibitor = Milestone.none();
        Milestone firstNexusTurret = Milestone.none();
        Milestone nexusDestruction = Milestone.none();
        int damageEvents = 0;
        int destroyedEvents = 0;
        int started = 0;
        int stopped = 0;
        int nexusRespawns = 0;
        TreeMap<String, Integer> sourceLane = new TreeMap<>();
        Map<String, Integer> starts = new HashMap<>();
        long duration = 0;
        for (MatchEvent event : timeline.getEvents()) {
            StructureActionData action = event.getStructureAction();
            if (action == null) continue;
            Milestone value = milestone(event, action);
            if (action.damage() > 0.0 && !firstDamage.present()) firstDamage = value;
            if (action.phase() == StructureActionPhase.DESTROYED) {
                destroyedEvents++;
                if (action.structureKind() == StructureKind.TOWER && !firstTower.present()) firstTower = value;
                if (action.structureKind() == StructureKind.INHIBITOR && !firstInhibitor.present()) firstInhibitor = value;
                if (action.structureKind() == StructureKind.NEXUS_TURRET && !firstNexusTurret.present()) firstNexusTurret = value;
                if (action.structureKind() == StructureKind.NEXUS && !nexusDestruction.present()) nexusDestruction = value;
            }
            if (action.phase() == StructureActionPhase.DAMAGE) damageEvents++;
            if (action.phase() == StructureActionPhase.STARTED) {
                started++;
                starts.putIfAbsent(rootActionId(event.getActionId()), event.getTimeSeconds());
            }
            if (action.phase() == StructureActionPhase.REPELLED
                    || action.phase() == StructureActionPhase.ABORTED) {
                stopped++;
                Integer start = starts.remove(rootActionId(event.getActionId()));
                if (start != null) duration += Math.max(0, event.getTimeSeconds() - start);
            }
            if (action.phase() == StructureActionPhase.RESPAWNED) nexusRespawns++;
            String distributionKey = action.phase() + "|" + action.structureKind() + "|"
                    + Objects.toString(action.towerTier(), "NONE") + "|"
                    + Objects.toString(action.lane(), "NONE") + "|"
                    + Objects.toString(action.source(), "NONE") + "|"
                    + Objects.toString(action.attackingSide(), "NONE");
            sourceLane.merge(distributionKey, 1, Integer::sum);
        }
        for (int start : starts.values()) duration += Math.max(0, timeline.getDurationSeconds() - start);
        List<InhibitorRespawn> inhibitorRespawns = inhibitorRespawns(timeline);
        return new StructureTimeline(firstDamage, firstTower, firstInhibitor, firstInhibitor,
                firstNexusTurret, nexusDestruction, damageEvents, destroyedEvents,
                started, stopped, duration, nexusRespawns, inhibitorRespawns,
                Collections.unmodifiableMap(sourceLane));
    }

    private static List<InhibitorRespawn> inhibitorRespawns(MatchTimeline timeline) {
        EnumMap<TeamSide, EnumMap<Lane, Boolean>> previous = new EnumMap<>(TeamSide.class);
        ArrayList<InhibitorRespawn> result = new ArrayList<>();
        for (MatchSnapshot snapshot : timeline.getSnapshots()) {
            for (TeamSide side : TeamSide.values()) {
                var team = snapshot.getStructureState().teams().get(side);
                if (team == null) continue;
                EnumMap<Lane, Boolean> lanes = previous.computeIfAbsent(side,
                        ignored -> new EnumMap<>(Lane.class));
                for (Lane lane : Lane.values()) {
                    boolean current = team.lanes().get(lane).inhibitor().alive();
                    Boolean before = lanes.put(lane, current);
                    if (Boolean.FALSE.equals(before) && current) {
                        result.add(new InhibitorRespawn(snapshot.getTimeSeconds(), side, lane));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static String rootActionId(String actionId) {
        if (actionId == null) return "NONE";
        int separator = actionId.lastIndexOf(':');
        return separator < 0 ? actionId : actionId.substring(0, separator);
    }

    private static Milestone milestone(MatchEvent event, StructureActionData action) {
        return new Milestone(event.getTimeSeconds(), Objects.toString(action.lane(), "NONE"),
                Objects.toString(action.source(), "NONE"),
                Objects.toString(action.attackingSide(), "NONE"), action.targetId(), true);
    }

    private static TimingDifference timingDifference(StructureTimeline before, StructureTimeline after) {
        return new TimingDifference(
                milestoneDifference(before.firstTower(), after.firstTower()),
                milestoneDifference(before.firstInhibitor(), after.firstInhibitor()),
                milestoneDifference(before.baseOpen(), after.baseOpen()),
                milestoneDifference(before.firstNexusTurret(), after.firstNexusTurret()),
                milestoneDifference(before.nexusDestruction(), after.nexusDestruction()),
                before.structureDamageEvents() != after.structureDamageEvents(),
                before.structureDestroyedEvents() != after.structureDestroyedEvents(),
                before.persistentSiegeStarted() != after.persistentSiegeStarted(),
                before.persistentSiegeStopped() != after.persistentSiegeStopped(),
                before.persistentSiegeDurationSeconds() != after.persistentSiegeDurationSeconds(),
                !before.inhibitorRespawns().equals(after.inhibitorRespawns()),
                before.nexusTurretRespawns() != after.nexusTurretRespawns(),
                !before.sourceLaneDistribution().equals(after.sourceLaneDistribution()));
    }

    private static MilestoneDifference milestoneDifference(Milestone before, Milestone after) {
        return new MilestoneDifference(!before.equals(after),
                before.timeSeconds() != after.timeSeconds(),
                !before.lane().equals(after.lane()),
                !before.source().equals(after.source()), before, after);
    }

    private Divergence divergences(MatchTimeline before, MatchTimeline after) throws Exception {
        int publicEvents = firstDifferent(eventProjections(before.getEvents(), false),
                eventProjections(after.getEvents(), false));
        int publicSnapshots = firstDifferent(snapshotProjections(before.getSnapshots(), SnapshotScope.PUBLIC),
                snapshotProjections(after.getSnapshots(), SnapshotScope.PUBLIC));
        int combat = firstDifferent(eventProjections(before.getEvents(), true),
                eventProjections(after.getEvents(), true));
        int pressure = firstDifferent(snapshotProjections(before.getSnapshots(), SnapshotScope.PRESSURE),
                snapshotProjections(after.getSnapshots(), SnapshotScope.PRESSURE));
        int economy = firstDifferent(snapshotProjections(before.getSnapshots(), SnapshotScope.ECONOMY),
                snapshotProjections(after.getSnapshots(), SnapshotScope.ECONOMY));
        int structureEvents = firstDifferent(structureEventProjections(before.getEvents()),
                structureEventProjections(after.getEvents()));
        int structureSnapshots = firstDifferent(
                structureSnapshotProjections(before.getSnapshots()),
                structureSnapshotProjections(after.getSnapshots()));
        return new Divergence(minPresent(publicEvents, publicSnapshots), combat, pressure,
                economy, minPresent(structureEvents, structureSnapshots));
    }

    private List<TimedProjection> eventProjections(List<MatchEvent> events, boolean combatOnly)
            throws Exception {
        ArrayList<TimedProjection> result = new ArrayList<>();
        for (MatchEvent event : events) {
            if (combatOnly && !isCombat(event.getType())) continue;
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("time", event.getTimeSeconds());
            value.put("type", event.getType());
            value.put("actionId", event.getActionId());
            value.put("parentActionId", event.getParentActionId());
            value.put("actorPlayerId", event.getActorPlayerId());
            value.put("killerPlayerId", event.getKillerPlayerId());
            value.put("victimPlayerId", event.getVictimPlayerId());
            value.put("assistPlayerIds", event.getAssistPlayerIds());
            value.put("combatSource", event.getCombatSource());
            value.put("combatLane", event.getCombatLane());
            value.put("laneCombat", event.getLaneCombat());
            value.put("jungleGank", event.getJungleGank());
            value.put("counterGank", event.getCounterGank());
            value.put("roam", event.getRoam());
            value.put("objectiveFight", event.getObjectiveFight());
            value.put("structureAction", event.getStructureAction());
            value.put("midGameMacroAction", event.getMidGameMacroAction());
            value.put("lateGameDecision", event.getLateGameDecision());
            value.put("goldAmount", event.getGoldAmount());
            result.add(new TimedProjection(event.getTimeSeconds(), canonical.writeValueAsString(value)));
        }
        return List.copyOf(result);
    }

    private List<TimedProjection> structureEventProjections(List<MatchEvent> events) throws Exception {
        ArrayList<TimedProjection> result = new ArrayList<>();
        for (MatchEvent event : events) {
            if (event.getStructureAction() == null) continue;
            result.add(new TimedProjection(event.getTimeSeconds(), canonical.writeValueAsString(Map.of(
                    "actionId", Objects.toString(event.getActionId(), "NONE"),
                    "parentActionId", Objects.toString(event.getParentActionId(), "NONE"),
                    "action", event.getStructureAction()))));
        }
        return List.copyOf(result);
    }

    private List<TimedProjection> snapshotProjections(
            List<MatchSnapshot> snapshots, SnapshotScope scope) throws Exception {
        ArrayList<TimedProjection> result = new ArrayList<>();
        for (MatchSnapshot snapshot : snapshots) {
            Object value;
            if (scope == SnapshotScope.PRESSURE) {
                value = snapshot.getLaneSnapshots();
            } else if (scope == SnapshotScope.ECONOMY) {
                value = Map.of(
                        "blueGold", snapshot.getBlueGold(), "redGold", snapshot.getRedGold(),
                        "players", snapshot.getPlayerSnapshots().stream().map(player -> Map.ofEntries(
                                Map.entry("side", player.getTeamSide()),
                                Map.entry("position", player.getPosition()),
                                Map.entry("kills", player.getKills()),
                                Map.entry("deaths", player.getDeaths()),
                                Map.entry("assists", player.getAssists()),
                                Map.entry("cs", player.getCs()),
                                Map.entry("gold", player.getGold()),
                                Map.entry("level", player.getLevel()),
                                Map.entry("item", player.getItemStage()))).toList());
            } else {
                value = Map.ofEntries(
                        Map.entry("kills", List.of(snapshot.getBlueKills(), snapshot.getRedKills())),
                        Map.entry("gold", List.of(snapshot.getBlueGold(), snapshot.getRedGold())),
                        Map.entry("dragons", List.of(snapshot.getBlueDragons(), snapshot.getRedDragons())),
                        Map.entry("towers", List.of(snapshot.getBlueTowersDestroyed(), snapshot.getRedTowersDestroyed())),
                        Map.entry("inhibitors", List.of(snapshot.getBlueInhibitorsRemaining(), snapshot.getRedInhibitorsRemaining())),
                        Map.entry("nexusTurrets", List.of(snapshot.getBlueNexusTurretsRemaining(), snapshot.getRedNexusTurretsRemaining())),
                        Map.entry("nexusAlive", List.of(snapshot.isBlueNexusAlive(), snapshot.isRedNexusAlive())),
                        Map.entry("players", snapshot.getPlayerSnapshots().stream().map(player -> Map.ofEntries(
                                Map.entry("side", player.getTeamSide()), Map.entry("position", player.getPosition()),
                                Map.entry("kda", List.of(player.getKills(), player.getDeaths(), player.getAssists())),
                                Map.entry("cs", player.getCs()), Map.entry("gold", player.getGold()),
                                Map.entry("alive", player.isAlive()), Map.entry("activity", player.getActivityType()),
                                Map.entry("level", player.getLevel()), Map.entry("item", player.getItemStage()))).toList()),
                        Map.entry("lanes", snapshot.getLaneSnapshots()),
                        Map.entry("structures", MatchupV9StructureAttributionClassifier.project(snapshot.getStructureState())));
            }
            result.add(new TimedProjection(snapshot.getTimeSeconds(), canonical.writeValueAsString(value)));
        }
        return List.copyOf(result);
    }

    private List<TimedProjection> structureSnapshotProjections(List<MatchSnapshot> snapshots)
            throws Exception {
        ArrayList<TimedProjection> result = new ArrayList<>();
        for (MatchSnapshot snapshot : snapshots) {
            result.add(new TimedProjection(snapshot.getTimeSeconds(), canonical.writeValueAsString(
                    MatchupV9StructureAttributionClassifier.project(snapshot.getStructureState()))));
        }
        return List.copyOf(result);
    }

    private static boolean isCombat(MatchEventType type) {
        return switch (type) {
            case KILL, ASSIST, JUNGLE_GANK, COUNTER_GANK, LANE_COMBAT, ROAM,
                    TEAMFIGHT, TEAMFIGHT_RESULT, ACE -> true;
            default -> false;
        };
    }

    private static int firstDifferent(List<TimedProjection> before, List<TimedProjection> after) {
        int count = Math.min(before.size(), after.size());
        for (int index = 0; index < count; index++) {
            if (!before.get(index).value().equals(after.get(index).value())) {
                return Math.min(before.get(index).timeSeconds(), after.get(index).timeSeconds());
            }
        }
        if (before.size() == after.size()) return -1;
        return (before.size() > count ? before.get(count) : after.get(count)).timeSeconds();
    }

    private static int minPresent(int first, int second) {
        if (first < 0) return second;
        if (second < 0) return first;
        return Math.min(first, second);
    }

    private static String objectiveSignature(MatchTimeline timeline) {
        MatchSnapshot end = timeline.getSnapshots().getLast();
        long barons = timeline.getEvents().stream()
                .filter(value -> value.getType() == MatchEventType.BARON).count();
        long elders = timeline.getEvents().stream()
                .filter(value -> value.getType() == MatchEventType.ELDER).count();
        return end.getBlueDragons() + ":" + end.getRedDragons() + ":"
                + end.isBlueHasDragonSoul() + ":" + end.isRedHasDragonSoul() + ":"
                + barons + ":" + elders;
    }

    private static FixedInput fixedInput(RealDraftMatchResult fixed) {
        TreeMap<String, String> champions = new TreeMap<>();
        fixed.matchChampionAssignments().asMap().forEach((key, value) ->
                champions.put(key.side() + ":" + key.position(), value.championId().value()));
        TreeMap<String, String> players = new TreeMap<>();
        fixed.playerIdsByMatchSlot().forEach((key, value) ->
                players.put(key.side() + ":" + key.position(), value.value()));
        return new FixedInput(Collections.unmodifiableMap(champions),
                Collections.unmodifiableMap(players),
                fixed.hardFearlessExclusionsBeforeDraft().stream()
                        .map(value -> value.value()).sorted().toList());
    }

    private Binding requireBinding(Path backendRoot, Path output) throws Exception {
        Path contractPath = output.resolve("attribution-contract.json");
        Path shaPath = output.resolve("attribution-contract.sha256");
        if (!Files.isRegularFile(contractPath) || !Files.isRegularFile(shaPath)) {
            throw new IllegalStateException("Attribution contract must be frozen first");
        }
        String contractHash = firstHash(shaPath);
        if (!contractHash.equals(fileHash(contractPath))) {
            throw new IllegalStateException("Attribution contract bytes changed");
        }
        SourceIdentity current = sourceIdentity(backendRoot);
        var node = mapper.readTree(contractPath.toFile()).path("sourceIdentity");
        if (!node.path("productionSourceTree").path("hash").asText()
                .equals(current.productionSourceTree().hash())
                || !node.path("attributionHarnessSourceTree").path("hash").asText()
                .equals(current.attributionHarnessSourceTree().hash())
                || !mapper.readTree(contractPath.toFile()).path("scheduleHash").asText()
                .equals(MatchupV9StructureAttributionContract.schedule().scheduleHash())) {
            throw new IllegalStateException("Current source/schedule differs from frozen attribution contract");
        }
        return new Binding(contractHash, current);
    }

    private SourceIdentity sourceIdentity(Path backendRoot) throws Exception {
        Path gitRoot = backendRoot.toAbsolutePath().normalize().getParent();
        return new SourceIdentity(
                git(gitRoot, "rev-parse", "HEAD"), git(gitRoot, "status", "--short"),
                Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot),
                attributionHarnessSourceTree(backendRoot),
                SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION,
                MatchEngineV1Policy.authoritative().policyHash());
    }

    static Phase13GB1AuditArtifactWriter.SourceTreeIdentity attributionHarnessSourceTree(
            Path backendRoot) throws IOException {
        Path normalized = backendRoot.toAbsolutePath().normalize();
        TreeMap<String, byte[]> files = new TreeMap<>();
        Path testRoot = normalized.resolve(Path.of("src", "test", "java"));
        try (var walk = Files.walk(testRoot)) {
            for (Path file : walk.filter(Files::isRegularFile)
                    .filter(value -> value.getFileName().toString()
                            .startsWith("MatchupV9StructureAttribution"))
                    .sorted().toList()) {
                files.put(portable(normalized.relativize(file)), Files.readAllBytes(file));
            }
        }
        String build = Files.readString(normalized.resolve("build.gradle"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        int start = build.indexOf(BUILD_START);
        int end = build.indexOf(BUILD_END);
        if (start < 0 || end < start || build.indexOf(BUILD_START, start + 1) >= 0
                || build.indexOf(BUILD_END, end + 1) >= 0) {
            throw new IllegalStateException("Missing or duplicate attribution Gradle contract");
        }
        files.put("build.gradle#MATCHUP_V9_STRUCTURE_ATTRIBUTION_BUILD_CONTRACT",
                (build.substring(start, end + BUILD_END.length()) + '\n')
                        .getBytes(StandardCharsets.UTF_8));
        StringBuilder canonical = new StringBuilder();
        files.forEach((path, bytes) -> canonical.append(path).append('|')
                .append(MatchupV9StructureAttributionContract.sha256(bytes)).append('\n'));
        return new Phase13GB1AuditArtifactWriter.SourceTreeIdentity(
                "SHA256_UTF8_SORTED_LOGICAL_PATH_PIPE_RAW_OR_NORMALIZED_FILE_SHA256_LINES_V2",
                MatchupV9StructureAttributionContract.sha256(canonical.toString()), files.size());
    }

    private static String portable(Path value) {
        return value.normalize().toString().replace('\\', '/');
    }

    private static String git(Path root, String... args) throws Exception {
        ArrayList<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) throw new IllegalStateException("git identity failed: " + output);
        return output;
    }

    private Map<String, Object> profileBindings() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (var profile : MatchupV9StructureAttributionContract.PROFILES) {
            result.put(profile.name(), SimulationRuntimeProfiles.resolve(profile));
        }
        return result;
    }

    private String scheduleCsv(MatchupV9StructureAttributionContract.Schedule schedule) {
        StringBuilder result = new StringBuilder(
                "fixture_id,fixture_lane,pair_id,blue_team,red_team,series_game,seed_index,seed,consumption_status\n");
        for (var fixture : schedule.fixtures()) {
            for (int index = 0; index < fixture.seeds().size(); index++) {
                result.append(fixture.fixtureId()).append(',').append(fixture.fixtureLane()).append(',')
                        .append(fixture.pairId()).append(',').append(fixture.blueTeamCode()).append(',')
                        .append(fixture.redTeamCode()).append(',').append(fixture.seriesGameNumber()).append(',')
                        .append(index).append(',').append(fixture.seeds().get(index)).append(',')
                        .append(schedule.consumptionStatus()).append('\n');
            }
        }
        return result.toString();
    }

    byte[] canonicalBytes(Object value) throws IOException {
        byte[] raw = canonical.writeValueAsBytes(value);
        byte[] result = java.util.Arrays.copyOf(raw, raw.length + 1);
        result[raw.length] = '\n';
        return result;
    }

    static void writeFrozen(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.exists(path)) {
            if (!java.util.Arrays.equals(Files.readAllBytes(path), bytes)) {
                throw new IllegalStateException("Frozen artifact differs: " + path);
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
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    static String fileHash(Path path) throws IOException {
        return MatchupV9StructureAttributionContract.sha256(Files.readAllBytes(path));
    }

    private static String firstHash(Path path) throws IOException {
        String value = Files.readString(path, StandardCharsets.UTF_8).trim();
        if (value.length() < 64) throw new IllegalStateException("Missing SHA-256: " + path);
        return value.substring(0, 64);
    }

    private static Path sidecar(Path path) {
        return path.resolveSibling(path.getFileName() + ".sha256");
    }

    private static String workerJvmIdentity() {
        return MatchupV9StructureAttributionContract.sha256(
                "java.version=" + System.getProperty("java.version") + '\n'
                        + "java.vendor=" + System.getProperty("java.vendor") + '\n'
                        + "os.name=" + System.getProperty("os.name") + '\n'
                        + "os.arch=" + System.getProperty("os.arch") + '\n');
    }

    private static String pairKey(String fixture, int seedIndex, long seed) {
        return fixture + "|" + seedIndex + "|" + seed;
    }

    private enum SnapshotScope { PUBLIC, PRESSURE, ECONOMY }

    private record TimedProjection(int timeSeconds, String value) { }

    private record Executed(
            SimulationRuntimeProfileId profileId,
            Phase13GB1SimulationExecutor.Execution execution,
            SimulationExecutionProvenance provenance
    ) { }

    public record Binding(String contractHash, SourceIdentity sourceIdentity) { }

    public record SourceIdentity(
            String gitHead,
            String workingTreeStatus,
            Phase13GB1AuditArtifactWriter.SourceTreeIdentity productionSourceTree,
            Phase13GB1AuditArtifactWriter.SourceTreeIdentity attributionHarnessSourceTree,
            String engineImplementationVersion,
            String productionPolicyHash
    ) { }

    public record SourceInputBinding(
            String schemaVersion,
            String contractHash,
            SourceIdentity currentSourceIdentity,
            MatchupV9StructureAttributionEvidence.PredecessorAudit predecessorEvidence,
            MatchEngineV1Policy.Snapshot productionPolicy,
            Map<String, Object> profiles,
            SimulationResourceProvenance resourceProvenance,
            String playerIdentityVersion,
            String playerIdentityResourceHash,
            int stablePlayerCount,
            boolean predecessorRecommendationPreserved,
            String preservedRecommendation
    ) { }

    public record FreezeResult(
            String contractHash,
            String scheduleHash,
            SourceIdentity sourceIdentity,
            MatchupV9StructureAttributionEvidence.PredecessorAudit predecessorEvidence,
            MatchupV9StructureAttributionContract.SeedOverlapAudit seedOverlapAudit
    ) { }

    public record ShardResult(
            int shardIndex,
            int fixtureCount,
            int pairCount,
            int replayChecks,
            int instrumentationChecks,
            String workerJvmIdentityHash
    ) { }

    public record ShardCheckpoint(
            String schemaVersion,
            String contractHash,
            String harnessSourceHash,
            int shardIndex,
            int shardCount,
            String workerJvmIdentityHash,
            int fixtureCount,
            int matchRowCount,
            int pairCount,
            int replayCheckCount,
            int instrumentationCheckCount,
            List<PairRow> pairs
    ) {
        public ShardCheckpoint {
            pairs = List.copyOf(pairs);
        }
    }

    public record FixedInput(
            Map<String, String> championsBySidePosition,
            Map<String, String> playerIdsBySidePosition,
            List<String> hardFearlessExclusionsBeforeDraft
    ) {
        public FixedInput {
            championsBySidePosition = Map.copyOf(championsBySidePosition);
            playerIdsBySidePosition = Map.copyOf(playerIdsBySidePosition);
            hardFearlessExclusionsBeforeDraft = List.copyOf(hardFearlessExclusionsBeforeDraft);
        }
    }

    public record PairRow(
            String schemaVersion,
            int fixtureIndex,
            String fixtureId,
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String unorderedTeamPairId,
            String blueTeamCode,
            String redTeamCode,
            int seriesGameNumber,
            int seedIndex,
            long seed,
            String pairKey,
            FixedInput fixedInput,
            String rosterIdentityHash,
            String seriesHistoryBeforeHash,
            String draftDecisionHash,
            String finalDraftHash,
            String finalAssignmentHash,
            boolean inputIdentityExact,
            RunSummary baseline,
            RunSummary matchupCandidate,
            Comparison finalStructureComponents,
            TimingDifference timingAndEventDifferences,
            Divergence divergence,
            boolean winnerChanged,
            boolean objectiveChanged,
            int durationDeltaSeconds,
            Correctness correctness,
            Verification verification,
            LocalAttribution localAttribution
    ) { }

    public record RunSummary(
            SimulationRuntimeProfileId profileId,
            String configurationHash,
            String replayProvenanceHash,
            String timelineHash,
            long randomDrawCount,
            String randomTraceHash,
            TeamSide winnerSide,
            String winnerTeamCode,
            GameEndReason endReason,
            int durationSeconds,
            String objectiveSignature,
            FinalState finalStructureState,
            StructureTimeline structureTimeline,
            int matchupApplications,
            double matchupEdgeSum,
            int matchupDirectRandomCalls,
            int matchupPerspectiveMismatchErrors,
            long gameplayIntegrityErrors,
            StructureValidation structureValidation,
            String structuredDiagnosticsHash
    ) { }

    public record StructureValidation(
            int invalidHealth,
            int duplicateStructureActionIds,
            int nexusDestroyedWithTurretAlive,
            int postFinishMutationOrEvent,
            int impossibleRespawnOrStateTransition
    ) { }

    public record Milestone(
            int timeSeconds,
            String lane,
            String source,
            String attackingSide,
            String targetId,
            boolean present
    ) {
        static Milestone none() {
            return new Milestone(-1, "NONE", "NONE", "NONE", "NONE", false);
        }
    }

    public record InhibitorRespawn(int timeSeconds, TeamSide defendingSide, Lane lane) { }

    public record StructureTimeline(
            Milestone firstStructureDamage,
            Milestone firstTower,
            Milestone firstInhibitor,
            Milestone baseOpen,
            Milestone firstNexusTurret,
            Milestone nexusDestruction,
            int structureDamageEvents,
            int structureDestroyedEvents,
            int persistentSiegeStarted,
            int persistentSiegeStopped,
            long persistentSiegeDurationSeconds,
            int nexusTurretRespawns,
            List<InhibitorRespawn> inhibitorRespawns,
            Map<String, Integer> sourceLaneDistribution
    ) {
        public StructureTimeline {
            inhibitorRespawns = List.copyOf(inhibitorRespawns);
            sourceLaneDistribution = Map.copyOf(sourceLaneDistribution);
        }
    }

    public record MilestoneDifference(
            boolean anyDifference,
            boolean timeDifference,
            boolean laneDifference,
            boolean sourceDifference,
            Milestone baseline,
            Milestone matchupCandidate
    ) { }

    public record TimingDifference(
            MilestoneDifference firstTower,
            MilestoneDifference firstInhibitor,
            MilestoneDifference baseOpen,
            MilestoneDifference firstNexusTurret,
            MilestoneDifference nexusDestruction,
            boolean structureDamageEventCountDifference,
            boolean structureDestroyedEventCountDifference,
            boolean persistentSiegeStartCountDifference,
            boolean persistentSiegeStopCountDifference,
            boolean persistentSiegeDurationDifference,
            boolean inhibitorRespawnHistoryDifference,
            boolean nexusTurretRespawnCountDifference,
            boolean sourceLaneDistributionDifference
    ) { }

    public record Divergence(
            int firstPublicTimelineDivergenceSeconds,
            int firstCombatDivergenceSeconds,
            int firstPressureDivergenceSeconds,
            int firstEconomyDivergenceSeconds,
            int firstStructureDivergenceSeconds
    ) { }

    public record Correctness(
            long timeoutCount,
            long gameplayIntegrityErrorCount,
            long invalidStructureHealthCount,
            long duplicateStructuredStructureActionCount,
            long nexusDestroyedWithTurretAliveCount,
            long postFinishStructureMutationEventCount,
            long impossibleRespawnStateTransitionCount,
            long displayNameBasedStructureIdentityCount,
            long ineligibleDuplicateStructureRandomConsumptionErrorCount,
            long matchupDirectRandomCallCount,
            long matchupPerspectiveMismatchCount,
            long matchupOffContributionCount,
            boolean pass
    ) { }

    public record Verification(
            boolean replayChecked,
            boolean replayExact,
            int instrumentationProfilesChecked,
            boolean instrumentationTimelineRandomExact
    ) {
        static Verification notChecked() {
            return new Verification(false, true, 0, true);
        }
    }

    public record LocalAttribution(
            int baselineMatchupApplications,
            int candidateMatchupApplications,
            double baselineMatchupEdgeSum,
            double candidateMatchupEdgeSum,
            int directRandomCalls,
            int perspectiveMismatchErrors,
            String causalProvenanceStatus,
            String causalProvenanceReason,
            int firstMatchupLocalCauseSeconds,
            int firstPublicTimelineDivergenceSeconds,
            int firstCombatDivergenceSeconds,
            int firstPressureDivergenceSeconds,
            int firstEconomyDivergenceSeconds,
            int firstStructureDivergenceSeconds
    ) { }

    public record FinalizationResult(
            String schemaVersion,
            int matchRowCount,
            int pairedComparisonCount,
            String recommendation,
            boolean productionChanged,
            String manifestHash
    ) { }
}
