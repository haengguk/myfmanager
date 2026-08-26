package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.composition.CompositionApplicationProvenance;
import com.lolfm.composition.CompositionRuntimeDiagnostics;
import com.lolfm.composition.TeamCompositionContext;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.StructureActionData;
import com.lolfm.domain.StructureActionPhase;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.MatchEngineV9InstrumentationExecutor;
import com.lolfm.simulator.Phase13GB1SimulationExecutor;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import com.lolfm.simulator.StructureKind;
import com.lolfm.simulator.TeamSide;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Explicit, calibration-only real-fixture diagnostic. It never participates in gameplay. */
public final class CompositionV9ApplicationCausalityRunner {
    public static final Path OUTPUT = Path.of("build", "reports",
            "composition-v9-application-causality-hardening-v5");
    public static final int SHARD_COUNT = 4;
    private static final String CHECKPOINT_SCHEMA = "COMPOSITION_V9_CAUSALITY_SHARD_CHECKPOINT_V5";
    private static final String BUILD_START = "// COMPOSITION_V9_APPLICATION_CAUSALITY_BUILD_CONTRACT_START";
    private static final String BUILD_END = "// COMPOSITION_V9_APPLICATION_CAUSALITY_BUILD_CONTRACT_END";

    private final ObjectMapper mapper;
    private final ObjectMapper canonical;
    private final Phase13GB1RealMatchHarness draftHarness;
    private final ConfiguredMatchSimulatorFactory simulators;
    private final SimulationProvenanceService provenance;
    private final PairedDiagnosticAuditGate auditGate;

    public CompositionV9ApplicationCausalityRunner(
            RealDraftMatchOrchestrator orchestrator,
            ConfiguredMatchSimulatorFactory simulators,
            ObjectMapper mapper,
            com.lolfm.champion.ChampionCatalog champions,
            PlayerIdentityCatalog identities,
            PlayerRatingCatalog ratings,
            ChampionProficiencyCatalog proficiencies) {
        this.mapper = Objects.requireNonNull(mapper);
        this.canonical = mapper.copy().findAndRegisterModules()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.INDENT_OUTPUT);
        this.simulators = Objects.requireNonNull(simulators);
        this.draftHarness = new Phase13GB1RealMatchHarness(
                orchestrator, simulators, mapper, champions, identities, ratings, proficiencies);
        this.provenance = new SimulationProvenanceService(
                mapper, champions, identities, ratings, proficiencies);
        this.auditGate = new PairedDiagnosticAuditGate(mapper);
    }

    public FreezeResult freeze(Path backendRoot, Path output) throws Exception {
        var schedule = CompositionV9ApplicationCausalityContract.requireFrozen(
                CompositionV9ApplicationCausalityContract.schedule());
        var overlap = CompositionV9ApplicationCausalityContract.requireNoSeedOverlap(schedule);
        SourceIdentity source = sourceIdentity(backendRoot);
        LinkedHashMap<String, Object> contract = new LinkedHashMap<>();
        contract.put("schemaVersion", CompositionV9ApplicationCausalityContract.CONTRACT_SCHEMA);
        contract.put("taskName", "COMPOSITION_V9_APPLICATION_AND_CAUSALITY_HARDENING_WITH_ATTRIBUTION_AUDIT_GATE");
        contract.put("diagnosticPurpose", "CALIBRATION_ONLY_APPLICATION_REACHABILITY_AND_CAUSAL_BINDING");
        contract.put("currentHead", source.gitHead());
        contract.put("sourceIdentity", source);
        contract.put("engineImplementationVersion", SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION);
        contract.put("activeGameplayRulesVersion", SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION);
        contract.put("diagnosticSchemaVersion", CompositionRuntimeDiagnostics.SCHEMA_VERSION);
        contract.put("applicationProvenanceSchemaVersion", CompositionApplicationProvenance.SCHEMA_VERSION);
        contract.put("productionPolicy", MatchEngineV1Policy.authoritative());
        contract.put("profiles", profileBindings());
        contract.put("resourceProvenance", provenance.resourceProvenance());
        contract.put("scheduleHash", schedule.scheduleHash());
        contract.put("seedNamespace", schedule.seedNamespace());
        contract.put("seedConsumptionStatus", schedule.consumptionStatus());
        contract.put("fixtureCount", 100);
        contract.put("seedsPerFixture", 4);
        contract.put("profileCount", 2);
        contract.put("coreMatchRows", 800);
        contract.put("pairedComparisons", 400);
        contract.put("verificationExecutions", Map.of("replay", 100, "instrumentation", 200));
        contract.put("totalSimulationCount", 1100);
        contract.put("draftReusePolicy", "ONE_PRODUCTION_DRAFT_PER_FIXTURE_SHARED_BY_BOTH_PROFILES_AND_ALL_SEEDS");
        contract.put("causalRule", "FIRST_PUBLIC_DIVERGENCE_REQUIRES_SAME_TIME_STRUCTURED_PUBLIC_BINDING_FROM_MATCHUP_ONLY_COUNTERFACTUAL_LOCAL_CHANGED_ATTEMPT");
        contract.put("unapprovedContexts", List.of("OBJECTIVE_SETUP", "SIDE_LANE"));
        contract.put("productionActivation", false);
        contract.put("gameplayTuning", false);
        contract.put("officialEligibility", false);
        Files.createDirectories(output);
        byte[] bytes = canonicalBytes(contract);
        String hash = hash(bytes);
        writeAtomic(output.resolve("diagnostic-contract.json"), bytes);
        writeAtomic(output.resolve("diagnostic-contract.sha256"),
                (hash + "  diagnostic-contract.json\n").getBytes(StandardCharsets.UTF_8));
        writeAtomic(output.resolve("frozen-diagnostic-schedule.json"), canonicalBytes(schedule));
        writeAtomic(output.resolve("seed-overlap-audit.json"), canonicalBytes(overlap));
        writeAtomic(output.resolve("source-input-profile-resource-binding.json"), canonicalBytes(Map.of(
                "schemaVersion", "COMPOSITION_V9_SOURCE_INPUT_PROFILE_RESOURCE_BINDING_V3",
                "contractHash", hash, "sourceIdentity", source, "profiles", profileBindings(),
                "resourceProvenance", provenance.resourceProvenance(),
                "productionPolicy", MatchEngineV1Policy.authoritative())));
        return new FreezeResult(hash, schedule.scheduleHash(), source.harnessSourceHash(), overlap);
    }

    public ShardResult runShard(Path backendRoot, Path output, int shardIndex) throws Exception {
        if (shardIndex < 0 || shardIndex >= SHARD_COUNT) throw new IllegalArgumentException("Invalid shard");
        Binding binding = requireBinding(backendRoot, output);
        ArrayList<PairRow> pairs = new ArrayList<>();
        int fixtures = 0, replayChecks = 0, instrumentationChecks = 0;
        List<CompositionV9ApplicationCausalityContract.Fixture> schedule =
                CompositionV9ApplicationCausalityContract.schedule().fixtures();
        for (int fixtureIndex = shardIndex; fixtureIndex < schedule.size(); fixtureIndex += SHARD_COUNT) {
            var fixture = schedule.get(fixtureIndex);
            var prepared = draftHarness.prepareFixture(
                    CompositionV9ApplicationCausalityContract.sourceFixture(fixture));
            RealDraftMatchResult fixed = prepared.realDraftFixture();
            FixedInput input = fixedInput(fixed);
            for (int seedIndex = 0; seedIndex < fixture.seeds().size(); seedIndex++) {
                long seed = fixture.seeds().get(seedIndex);
                Executed matchup = execute(prepared, fixture, seed,
                        SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
                Executed full = execute(prepared, fixture, seed,
                        SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
                Verification verification = Verification.notChecked();
                if (seedIndex == 0) {
                    Executed replay = execute(prepared, fixture, seed,
                            SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1);
                    boolean replayExact = exact(full, replay);
                    boolean matchupInstrumentation = instrumentationExact(fixed, fixture, seed, matchup);
                    boolean fullInstrumentation = instrumentationExact(fixed, fixture, seed, full);
                    verification = new Verification(true, replayExact, 2,
                            matchupInstrumentation && fullInstrumentation);
                    replayChecks++;
                    instrumentationChecks += 2;
                }
                PairRow row = pair(fixtureIndex, fixture, seedIndex, seed, input,
                        matchup, full, verification);
                if (!row.inputIdentityExact() || !row.correctness().pass()
                        || (verification.replayChecked() && !verification.replayExact())
                        || !verification.instrumentationTimelineRandomExact()) {
                    throw new IllegalStateException("Composition causality exact gate failed: " + row.pairKey());
                }
                pairs.add(row);
            }
            fixtures++;
            System.out.printf(Locale.ROOT,
                    "COMPOSITION_V9_CAUSALITY shard=%d/%d fixture=%d/100 %s pairs=4%n",
                    shardIndex + 1, SHARD_COUNT, fixtureIndex + 1, fixture.fixtureId());
        }
        ShardCheckpoint checkpoint = new ShardCheckpoint(CHECKPOINT_SCHEMA, binding.contractHash(),
                binding.sourceIdentity().harnessSourceHash(), shardIndex, SHARD_COUNT,
                fixtures, pairs.size() * 2, pairs.size(), replayChecks, instrumentationChecks,
                List.copyOf(pairs));
        Path path = output.resolve("checkpoints").resolve("shard-" + shardIndex + ".json");
        writeAtomic(path, canonicalBytes(checkpoint));
        writeAtomic(sidecar(path), (fileHash(path) + "  " + path.getFileName() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        var receipt = auditGate.writeShard(output, auditContract(binding), shardIndex, path,
                pairs.stream().map(this::auditEnvelope).toList());
        return new ShardResult(shardIndex, fixtures, pairs.size(), replayChecks,
                instrumentationChecks, receipt.processIdentity().processIdentityHash());
    }

    public FinalizationResult finalizeArtifacts(Path backendRoot, Path output) throws Exception {
        Binding binding = requireBinding(backendRoot, output);
        var verified = auditGate.verify(output, auditContract(binding));
        ArrayList<PairRow> pairs = new ArrayList<>();
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            Path path = output.resolve("checkpoints").resolve("shard-" + shard + ".json");
            if (!fileHash(path).equals(firstHash(sidecar(path)))) {
                throw new IllegalStateException("Source checkpoint SHA mismatch");
            }
            ShardCheckpoint checkpoint = canonical.readValue(path.toFile(), ShardCheckpoint.class);
            if (!CHECKPOINT_SCHEMA.equals(checkpoint.schemaVersion())
                    || !binding.contractHash().equals(checkpoint.contractHash())
                    || !binding.sourceIdentity().harnessSourceHash().equals(checkpoint.harnessSourceHash())
                    || checkpoint.shardIndex() != shard) {
                throw new IllegalStateException("Source checkpoint binding mismatch");
            }
            for (PairRow row : checkpoint.pairs()) {
                var authenticated = verified.rowsByPairKey().get(row.pairKey());
                if (authenticated == null || !authenticated.row().sourcePairPayloadSha256()
                        .equals(auditGate.canonicalHash(row))) {
                    throw new IllegalStateException("Authenticated pair payload mismatch");
                }
                pairs.add(row);
            }
        }
        pairs.sort(Comparator.comparingInt(PairRow::fixtureIndex).thenComparingInt(PairRow::seedIndex));
        if (pairs.size() != CompositionV9ApplicationCausalityContract.EXPECTED_PAIRS
                || pairs.stream().map(PairRow::pairKey).distinct().count() != pairs.size()) {
            throw new IllegalStateException("Final pair coverage mismatch");
        }
        return writeArtifacts(output, binding, List.copyOf(pairs), verified.receiptManifest());
    }

    private PairRow pair(int fixtureIndex, CompositionV9ApplicationCausalityContract.Fixture fixture,
                         int seedIndex, long seed, FixedInput input, Executed matchup,
                         Executed full, Verification verification) throws Exception {
        var a = matchup.provenance();
        var b = full.provenance();
        boolean inputExact = a.rosterIdentityHash().equals(b.rosterIdentityHash())
                && a.seriesHistoryBeforeHash().equals(b.seriesHistoryBeforeHash())
                && a.draftDecisionHash().equals(b.draftDecisionHash())
                && a.finalDraftHash().equals(b.finalDraftHash())
                && a.finalAssignmentHash().equals(b.finalAssignmentHash())
                && a.matchSeed() == b.matchSeed() && a.matchSeed() == seed
                && a.resourceProvenance().resourceProvenanceHash()
                .equals(b.resourceProvenance().resourceProvenanceHash());
        RunSummary before = summarize(matchup);
        RunSummary after = summarize(full);
        Divergence divergence = divergences(matchup.execution().timeline(), full.execution().timeline());
        CausalBinding causal = causalBinding(after.applicationProvenance(), divergence);
        Correctness correctness = correctness(before, after);
        return new PairRow("COMPOSITION_V9_APPLICATION_CAUSALITY_PAIR_V3", fixtureIndex,
                fixture.fixtureId(), fixture.fixtureLane(), fixture.pairId(), fixture.blueTeamCode(),
                fixture.redTeamCode(), fixture.seriesGameNumber(), seedIndex, seed,
                pairKey(fixture.fixtureId(), seedIndex, seed), input,
                a.rosterIdentityHash(), a.seriesHistoryBeforeHash(), a.draftDecisionHash(),
                a.finalDraftHash(), a.finalAssignmentHash(), inputExact, before, after,
                divergence, causal, before.winnerSide() != after.winnerSide(),
                !before.objectiveSignature().equals(after.objectiveSignature()),
                !before.structureSignature().equals(after.structureSignature()),
                after.durationSeconds() - before.durationSeconds(), correctness, verification);
    }

    private Executed execute(Phase13GB1RealMatchHarness.PreparedFixture prepared,
                             CompositionV9ApplicationCausalityContract.Fixture fixture,
                             long seed, SimulationRuntimeProfileId profile) {
        RealDraftMatchResult fixed = prepared.realDraftFixture();
        var result = Phase13GB1SimulationExecutor.execute(simulators, fixed.blueTeam(), fixed.redTeam(),
                fixed.matchChampionAssignments(), profile, seed, fixture.blueTeamCode(), fixture.redTeamCode());
        var executionProvenance = provenance.create(SimulationRuntimeProfiles.resolve(profile),
                SimulationInstrumentation.enabled(), fixture.blueTeamCode(), fixed.blueTeam(),
                fixture.redTeamCode(), fixed.redTeam(), seed, fixture.seriesGameNumber(),
                fixed.hardFearlessExclusionsBeforeDraft(), fixed.draftResult(), result.timeline(),
                result.randomFingerprint());
        return new Executed(profile, result, executionProvenance);
    }

    private boolean instrumentationExact(RealDraftMatchResult fixed,
                                         CompositionV9ApplicationCausalityContract.Fixture fixture,
                                         long seed, Executed enabled) {
        var disabled = MatchEngineV9InstrumentationExecutor.execute(simulators, fixed.blueTeam(),
                fixed.redTeam(), fixed.matchChampionAssignments(), enabled.profileId(),
                SimulationInstrumentation.disabled(), seed, fixture.blueTeamCode(), fixture.redTeamCode());
        return provenance.timelineHash(disabled.timeline()).equals(enabled.provenance().timelineHash())
                && disabled.randomFingerprint().equals(enabled.execution().randomFingerprint());
    }

    private static boolean exact(Executed first, Executed second) {
        return first.provenance().timelineHash().equals(second.provenance().timelineHash())
                && first.execution().randomFingerprint().equals(second.execution().randomFingerprint())
                && Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                first.execution().structuredDiagnostics()).equals(
                Phase13GB1SimulationExecutor.structuredDiagnosticsHash(second.execution().structuredDiagnostics()));
    }

    private RunSummary summarize(Executed run) {
        MatchTimeline timeline = run.execution().timeline();
        MatchSnapshot end = timeline.getSnapshots().getLast();
        var diagnostics = run.execution().structuredDiagnostics();
        CompositionRuntimeDiagnostics composition = diagnostics.composition();
        long integrity = Phase13GB1RealMatchHarness.IntegrityDiagnostics.from(
                SimulationRuntimeProfiles.resolve(run.profileId()).gameplayConfiguration(), diagnostics).errorCount();
        StructureValidation structures = validateStructures(timeline);
        String maxHealth = maximumHealthHash(end);
        return new RunSummary(run.profileId(), run.provenance().configurationHash(),
                run.provenance().engineImplementationVersion(), run.provenance().activeGameplayRulesVersion(),
                run.provenance().resourceProvenance().resourceProvenanceHash(),
                run.provenance().replayProvenanceHash(), run.provenance().timelineHash(),
                run.execution().randomFingerprint().randomDrawCount(),
                run.execution().randomFingerprint().randomTraceHash(), run.execution().winnerSide(),
                run.execution().endReason(), timeline.getDurationSeconds(), objectiveSignature(timeline),
                hash(MatchupV9StructureAttributionClassifier.project(end.getStructureState())), maxHealth,
                composition.initialized(), composition.resolverEvaluationCount(),
                composition.triggerSuccessCount(), composition.actualAttemptCount(),
                composition.mappedActualAttemptCount(), composition.unmappedActualAttemptCount(),
                composition.gameplayApplicationCount(), composition.modifierCalculatedCount(),
                composition.nonZeroModifierCount(), composition.modifierConsumedCount(),
                composition.localDecisionChangedCount(), composition.localDecisionUnchangedCount(),
                composition.publicActionBindingCount(), composition.directRandomCallCount(),
                composition.compositionRandomDrawCount(), composition.duplicateApplicationPointCount(),
                composition.multiContextAttemptCount(), composition.conflictingPerspectiveCount(),
                composition.applicationProvenance(), integrity, structures,
                Phase13GB1SimulationExecutor.structuredDiagnosticsHash(diagnostics));
    }

    private Correctness correctness(RunSummary before, RunSummary after) {
        long timeout = (before.endReason() == GameEndReason.SIMULATION_TIMEOUT ? 1 : 0)
                + (after.endReason() == GameEndReason.SIMULATION_TIMEOUT ? 1 : 0);
        long gameplay = before.gameplayIntegrityErrors() + after.gameplayIntegrityErrors();
        long invalid = before.structureValidation().invalidHealth() + after.structureValidation().invalidHealth();
        long duplicateStructure = before.structureValidation().duplicateStructureActionIds()
                + after.structureValidation().duplicateStructureActionIds();
        long nexus = before.structureValidation().nexusDestroyedWithTurretAlive()
                + after.structureValidation().nexusDestroyedWithTurretAlive();
        long post = before.structureValidation().postFinishMutationOrEvent()
                + after.structureValidation().postFinishMutationOrEvent();
        long respawn = before.structureValidation().impossibleRespawnTransition()
                + after.structureValidation().impossibleRespawnTransition();
        long maxHp = before.maximumStructureHealthHash().equals(after.maximumStructureHealthHash()) ? 0 : 1;
        long random = before.directCompositionRandomCalls() + after.directCompositionRandomCalls()
                + before.compositionRandomDraws() + after.compositionRandomDraws();
        long perspective = before.conflictingPerspectiveCount() + after.conflictingPerspectiveCount();
        long off = before.gameplayApplicationCount() + before.modifierConsumedCount();
        long duplicateApplication = before.duplicateApplicationPointCount()
                + after.duplicateApplicationPointCount() + before.multiContextAttemptCount()
                + after.multiContextAttemptCount();
        long total = timeout + gameplay + invalid + duplicateStructure + nexus + post + respawn
                + maxHp + random + perspective + off + duplicateApplication;
        return new Correctness(timeout, gameplay, invalid, duplicateStructure, nexus, post, respawn,
                maxHp, random, perspective, off, duplicateApplication, total == 0);
    }

    private CausalBinding causalBinding(List<CompositionApplicationProvenance> applications,
                                        Divergence divergence) {
        List<CompositionApplicationProvenance> applied = applications.stream()
                .filter(CompositionApplicationProvenance::applicationApplied).toList();
        List<CompositionApplicationProvenance> changed = applied.stream()
                .filter(CompositionApplicationProvenance::localDecisionChanged).toList();
        int firstApplication = applied.stream().mapToInt(CompositionApplicationProvenance::simulationTimeSeconds)
                .min().orElse(-1);
        int firstChange = changed.stream().mapToInt(CompositionApplicationProvenance::simulationTimeSeconds)
                .min().orElse(-1);
        int publicTime = divergence.firstPublicTimelineDivergenceSeconds();
        CompositionApplicationProvenance cause = changed.stream()
                .filter(value -> value.simulationTimeSeconds() == publicTime)
                .filter(value -> !"NOT_BOUND".equals(value.publicBindingStatus()))
                .findFirst().orElse(null);
        boolean unexplained = publicTime >= 0 && cause == null;
        return new CausalBinding(firstApplication, firstChange, publicTime,
                publicTime < 0 ? "NO_PUBLIC_DIVERGENCE" : cause == null
                        ? "UNEXPLAINED_PUBLIC_DIVERGENCE" : "BOUND_TO_LOCAL_CHANGED_ATTEMPT",
                cause == null ? null : cause.attemptId().sequence(),
                cause == null ? null : cause.publicActionId(),
                cause == null ? null : cause.publicEventType(),
                cause == null ? null : cause.publicCombatSource(),
                cause == null ? null : cause.context(), unexplained);
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
        int objective = objectiveSignature(before).equals(objectiveSignature(after)) ? -1
                : minPresent(publicEvents, publicSnapshots);
        int structure = firstDifferent(structureProjections(before), structureProjections(after));
        return new Divergence(minPresent(publicEvents, publicSnapshots), combat, pressure,
                economy, objective, structure);
    }

    private List<TimedProjection> eventProjections(List<MatchEvent> events, boolean combatOnly) throws Exception {
        ArrayList<TimedProjection> result = new ArrayList<>();
        for (MatchEvent event : events) {
            if (combatOnly && !isCombat(event.getType())) continue;
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("time", event.getTimeSeconds()); value.put("type", event.getType());
            value.put("actionId", event.getActionId()); value.put("parentActionId", event.getParentActionId());
            value.put("actorPlayerId", event.getActorPlayerId()); value.put("killerPlayerId", event.getKillerPlayerId());
            value.put("victimPlayerId", event.getVictimPlayerId()); value.put("assistPlayerIds", event.getAssistPlayerIds());
            value.put("combatSource", event.getCombatSource()); value.put("combatLane", event.getCombatLane());
            value.put("laneCombat", event.getLaneCombat()); value.put("jungleGank", event.getJungleGank());
            value.put("counterGank", event.getCounterGank()); value.put("roam", event.getRoam());
            value.put("objectiveFight", event.getObjectiveFight()); value.put("structureAction", event.getStructureAction());
            value.put("midGameMacroAction", event.getMidGameMacroAction()); value.put("lateGameDecision", event.getLateGameDecision());
            value.put("goldAmount", event.getGoldAmount());
            result.add(new TimedProjection(event.getTimeSeconds(), canonical.writeValueAsString(value)));
        }
        return List.copyOf(result);
    }

    private List<TimedProjection> snapshotProjections(List<MatchSnapshot> snapshots, SnapshotScope scope) throws Exception {
        ArrayList<TimedProjection> result = new ArrayList<>();
        for (MatchSnapshot snapshot : snapshots) {
            Object value = scope == SnapshotScope.PRESSURE ? snapshot.getLaneSnapshots()
                    : scope == SnapshotScope.ECONOMY ? Map.of("blueGold", snapshot.getBlueGold(),
                    "redGold", snapshot.getRedGold(), "players", snapshot.getPlayerSnapshots().stream()
                            .map(player -> List.of(player.getTeamSide(), player.getPosition(), player.getKills(),
                                    player.getDeaths(), player.getAssists(), player.getCs(), player.getGold(),
                                    player.getLevel(), player.getItemStage())).toList())
                    : Map.ofEntries(Map.entry("kills", List.of(snapshot.getBlueKills(), snapshot.getRedKills())),
                    Map.entry("gold", List.of(snapshot.getBlueGold(), snapshot.getRedGold())),
                    Map.entry("dragons", List.of(snapshot.getBlueDragons(), snapshot.getRedDragons())),
                    Map.entry("towers", List.of(snapshot.getBlueTowersDestroyed(), snapshot.getRedTowersDestroyed())),
                    Map.entry("nexus", List.of(snapshot.isBlueNexusAlive(), snapshot.isRedNexusAlive())),
                    Map.entry("players", snapshot.getPlayerSnapshots().stream().map(player -> List.of(
                            player.getTeamSide(), player.getPosition(), player.getKills(), player.getDeaths(),
                            player.getAssists(), player.getCs(), player.getGold(), player.isAlive(),
                            player.getActivityType(), player.getLevel(), player.getItemStage())).toList()),
                    Map.entry("lanes", snapshot.getLaneSnapshots()),
                    Map.entry("structures", MatchupV9StructureAttributionClassifier.project(snapshot.getStructureState())));
            result.add(new TimedProjection(snapshot.getTimeSeconds(), canonical.writeValueAsString(value)));
        }
        return List.copyOf(result);
    }

    private List<TimedProjection> structureProjections(MatchTimeline timeline) throws Exception {
        ArrayList<TimedProjection> result = new ArrayList<>();
        for (MatchSnapshot snapshot : timeline.getSnapshots()) {
            result.add(new TimedProjection(snapshot.getTimeSeconds(), canonical.writeValueAsString(
                    MatchupV9StructureAttributionClassifier.project(snapshot.getStructureState()))));
        }
        return List.copyOf(result);
    }

    private static int firstDifferent(List<TimedProjection> before, List<TimedProjection> after) {
        int count = Math.min(before.size(), after.size());
        for (int index = 0; index < count; index++) {
            if (!before.get(index).value().equals(after.get(index).value())) {
                return Math.min(before.get(index).timeSeconds(), after.get(index).timeSeconds());
            }
        }
        return before.size() == after.size() ? -1
                : (before.size() > count ? before.get(count) : after.get(count)).timeSeconds();
    }

    private static int minPresent(int first, int second) {
        return first < 0 ? second : second < 0 ? first : Math.min(first, second);
    }

    private static boolean isCombat(MatchEventType type) {
        return switch (type) {
            case KILL, ASSIST, JUNGLE_GANK, COUNTER_GANK, LANE_COMBAT, ROAM,
                    TEAMFIGHT, TEAMFIGHT_RESULT, ACE -> true;
            default -> false;
        };
    }

    private StructureValidation validateStructures(MatchTimeline timeline) {
        int invalid = 0, duplicate = 0, nexus = 0, post = 0, respawn = 0;
        Set<String> ids = new HashSet<>();
        Map<String, Boolean> alive = new HashMap<>();
        for (MatchEvent event : timeline.getEvents()) {
            StructureActionData action = event.getStructureAction();
            if (action == null) continue;
            if (event.getTimeSeconds() > timeline.getDurationSeconds()) post++;
            if (event.getActionId() == null || !ids.add(event.getActionId())) duplicate++;
            if (!finiteHealth(action)) invalid++;
            boolean current = alive.getOrDefault(action.targetId(), true);
            if (!current && action.structureKind() == StructureKind.INHIBITOR
                    && action.phase() != StructureActionPhase.RESPAWNED && action.healthBefore() > 0) current = true;
            if (action.phase() == StructureActionPhase.DESTROYED) {
                if (!current) respawn++;
                alive.put(action.targetId(), false);
            } else if (action.phase() == StructureActionPhase.RESPAWNED) {
                if (action.structureKind() != StructureKind.NEXUS_TURRET || current) respawn++;
                alive.put(action.targetId(), true);
            } else if (!current && action.damage() > 0) respawn++;
        }
        for (MatchSnapshot snapshot : timeline.getSnapshots()) {
            if (snapshot.getTimeSeconds() > timeline.getDurationSeconds()) post++;
            for (var team : snapshot.getStructureState().teams().values()) {
                invalid += invalidHealth(team.nexusCurrentHealth(), team.nexusMaxHealth());
                for (double hp : team.nexusTurretCurrentHealth()) invalid += invalidHealth(hp, team.nexusTurretMaxHealth());
                for (var lane : team.lanes().values()) {
                    invalid += invalidHealth(lane.outerTower().current(), lane.outerTower().maximum());
                    invalid += invalidHealth(lane.innerTower().current(), lane.innerTower().maximum());
                    invalid += invalidHealth(lane.inhibitorTower().current(), lane.inhibitorTower().maximum());
                    invalid += invalidHealth(lane.inhibitor().current(), lane.inhibitor().maximum());
                }
                if (!team.nexusAlive() && team.nexusTurretsRemaining() > 0) nexus++;
            }
        }
        return new StructureValidation(invalid, duplicate, nexus, post, respawn);
    }

    private static boolean finiteHealth(StructureActionData value) {
        return Double.isFinite(value.healthBefore()) && Double.isFinite(value.damage())
                && Double.isFinite(value.healthAfter()) && Double.isFinite(value.maxHealth())
                && value.maxHealth() > 0 && value.healthBefore() >= -1e-9
                && value.healthBefore() <= value.maxHealth() + 1e-9 && value.damage() >= -1e-9
                && value.healthAfter() >= -1e-9 && value.healthAfter() <= value.maxHealth() + 1e-9;
    }

    private static int invalidHealth(double current, double maximum) {
        return !Double.isFinite(current) || !Double.isFinite(maximum) || maximum <= 0
                || current < -1e-9 || current > maximum + 1e-9 ? 1 : 0;
    }

    private String maximumHealthHash(MatchSnapshot snapshot) {
        TreeMap<String, Double> values = new TreeMap<>();
        snapshot.getStructureState().teams().forEach((side, team) -> {
            values.put(side + "|NEXUS", team.nexusMaxHealth());
            values.put(side + "|NEXUS_TURRET", team.nexusTurretMaxHealth());
            team.lanes().forEach((lane, state) -> {
                values.put(side + "|" + lane + "|OUTER", state.outerTower().maximum());
                values.put(side + "|" + lane + "|INNER", state.innerTower().maximum());
                values.put(side + "|" + lane + "|INHIBITOR_TURRET", state.inhibitorTower().maximum());
                values.put(side + "|" + lane + "|INHIBITOR", state.inhibitor().maximum());
            });
        });
        return hash(values);
    }

    private static String objectiveSignature(MatchTimeline timeline) {
        MatchSnapshot end = timeline.getSnapshots().getLast();
        long barons = timeline.getEvents().stream().filter(value -> value.getType() == MatchEventType.BARON).count();
        long elders = timeline.getEvents().stream().filter(value -> value.getType() == MatchEventType.ELDER).count();
        return end.getBlueDragons() + ":" + end.getRedDragons() + ":"
                + end.isBlueHasDragonSoul() + ":" + end.isRedHasDragonSoul() + ":" + barons + ":" + elders;
    }

    private FixedInput fixedInput(RealDraftMatchResult fixed) {
        TreeMap<String, String> champions = new TreeMap<>();
        fixed.matchChampionAssignments().asMap().forEach((key, value) ->
                champions.put(key.side() + ":" + key.position(), value.championId().value()));
        TreeMap<String, String> players = new TreeMap<>();
        fixed.playerIdsByMatchSlot().forEach((key, value) ->
                players.put(key.side() + ":" + key.position(), value.value()));
        return new FixedInput(Collections.unmodifiableMap(champions), Collections.unmodifiableMap(players),
                fixed.hardFearlessExclusionsBeforeDraft().stream().map(value -> value.value()).sorted().toList());
    }

    private Binding requireBinding(Path backendRoot, Path output) throws Exception {
        Path contract = output.resolve("diagnostic-contract.json");
        Path sidecar = output.resolve("diagnostic-contract.sha256");
        if (!Files.isRegularFile(contract) || !Files.isRegularFile(sidecar)) {
            throw new IllegalStateException("Diagnostic contract must be frozen first");
        }
        String hash = firstHash(sidecar);
        if (!hash.equals(fileHash(contract))) throw new IllegalStateException("Contract bytes changed");
        SourceIdentity current = sourceIdentity(backendRoot);
        var node = mapper.readTree(contract.toFile());
        if (!node.path("sourceIdentity").path("productionSourceHash").asText().equals(current.productionSourceHash())
                || !node.path("sourceIdentity").path("harnessSourceHash").asText().equals(current.harnessSourceHash())
                || !node.path("scheduleHash").asText().equals(
                CompositionV9ApplicationCausalityContract.schedule().scheduleHash())) {
            throw new IllegalStateException("Current source/schedule differs from frozen contract");
        }
        return new Binding(hash, current);
    }

    private SourceIdentity sourceIdentity(Path backendRoot) throws Exception {
        Path gitRoot = backendRoot.toAbsolutePath().normalize().getParent();
        return new SourceIdentity(git(gitRoot, "rev-parse", "HEAD"), git(gitRoot, "status", "--short"),
                Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot).hash(),
                harnessSourceHash(backendRoot), SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION,
                MatchEngineV1Policy.authoritative().policyHash());
    }

    private String harnessSourceHash(Path backendRoot) throws IOException {
        Path root = backendRoot.toAbsolutePath().normalize();
        TreeMap<String, byte[]> files = new TreeMap<>();
        Path test = root.resolve(Path.of("src", "test", "java"));
        try (var walk = Files.walk(test)) {
            for (Path file : walk.filter(Files::isRegularFile).filter(value -> {
                String name = value.getFileName().toString();
                return name.startsWith("CompositionV9ApplicationCausality")
                        || name.equals("PairedDiagnosticAuditGate.java");
            }).sorted().toList()) {
                files.put(root.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        String build = Files.readString(root.resolve("build.gradle"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        int start = build.indexOf(BUILD_START), end = build.indexOf(BUILD_END);
        if (start < 0 || end < start || build.indexOf(BUILD_START, start + 1) >= 0
                || build.indexOf(BUILD_END, end + 1) >= 0) {
            throw new IllegalStateException("Missing or duplicate Composition Gradle contract");
        }
        files.put("build.gradle#COMPOSITION_V9_APPLICATION_CAUSALITY_BUILD_CONTRACT",
                (build.substring(start, end + BUILD_END.length()) + '\n').getBytes(StandardCharsets.UTF_8));
        StringBuilder value = new StringBuilder();
        files.forEach((path, bytes) -> value.append(path).append('|').append(hash(bytes)).append('\n'));
        return hash(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private PairedDiagnosticAuditGate.Contract auditContract(Binding binding) {
        var schedule = CompositionV9ApplicationCausalityContract.schedule();
        ArrayList<PairedDiagnosticAuditGate.ExpectedPair> expected = new ArrayList<>();
        for (int fixtureIndex = 0; fixtureIndex < schedule.fixtures().size(); fixtureIndex++) {
            var fixture = schedule.fixtures().get(fixtureIndex);
            for (int seedIndex = 0; seedIndex < fixture.seeds().size(); seedIndex++) {
                long seed = fixture.seeds().get(seedIndex);
                expected.add(new PairedDiagnosticAuditGate.ExpectedPair(fixtureIndex, fixture.fixtureId(),
                        fixture.fixtureLane().name(), fixture.pairId(), fixture.blueTeamCode(), fixture.redTeamCode(),
                        fixture.seriesGameNumber(), fixtureIndex % SHARD_COUNT, seedIndex, seed,
                        pairKey(fixture.fixtureId(), seedIndex, seed)));
            }
        }
        List<PairedDiagnosticAuditGate.ProfileContract> profiles =
                CompositionV9ApplicationCausalityContract.PROFILES.stream().map(profileId -> {
                    var profile = SimulationRuntimeProfiles.resolve(profileId);
                    return new PairedDiagnosticAuditGate.ProfileContract(profileId.name(),
                            profile.configurationHash(), profile.activeGameplayRulesVersion());
                }).toList();
        return new PairedDiagnosticAuditGate.Contract(PairedDiagnosticAuditGate.CONTRACT_SCHEMA,
                "COMPOSITION_V9_APPLICATION_CAUSALITY_HARDENING_V5", binding.contractHash(),
                schedule.scheduleHash(), binding.sourceIdentity().harnessSourceHash(),
                binding.sourceIdentity().engineImplementationVersion(),
                provenance.resourceProvenance().resourceProvenanceHash(),
                provenance.draftRuleSetIdentity(), provenance.draftRuleSetHash(),
                provenance.draftScoringPolicyHash(), SHARD_COUNT,
                CompositionV9ApplicationCausalityContract.EXPECTED_FIXTURES,
                CompositionV9ApplicationCausalityContract.EXPECTED_PROFILE_ROWS,
                CompositionV9ApplicationCausalityContract.EXPECTED_PAIRS, profiles, expected,
                new PairedDiagnosticAuditGate.InvariantEvidence(
                        new PairedDiagnosticAuditGate.InvariantProof(PairedDiagnosticAuditGate.FOCUSED_PROOF_STATUS,
                                "CompositionProductionApplicationProvenanceTest#offAndFreshMatchStateRemainExactZeroAndIsolated"),
                        new PairedDiagnosticAuditGate.InvariantProof(PairedDiagnosticAuditGate.FOCUSED_PROOF_STATUS,
                                "CompositionProductionApplicationProvenanceTest#unsupportedContextIsStructuredDisabledAndCannotReachConsumer")));
    }

    private PairedDiagnosticAuditGate.RowEnvelope auditEnvelope(PairRow pair) {
        List<PairedDiagnosticAuditGate.ProfileExecution> profiles = List.of(
                profileExecution(pair.matchupOnly()), profileExecution(pair.fullSystem()));
        Correctness value = pair.correctness();
        var row = new PairedDiagnosticAuditGate.AuditRow(pair.fixtureIndex(), pair.fixtureId(),
                pair.fixtureLane().name(), pair.unorderedTeamPairId(), pair.blueTeamCode(), pair.redTeamCode(),
                pair.seriesGameNumber(), pair.seedIndex(), pair.seed(), pair.pairKey(),
                pair.rosterIdentityHash(), pair.seriesHistoryBeforeHash(), pair.draftDecisionHash(),
                pair.finalDraftHash(), pair.finalAssignmentHash(), pair.inputIdentityExact(),
                auditGate.canonicalHash(pair), profiles,
                new PairedDiagnosticAuditGate.CorrectnessEvidence(value.timeoutCount(),
                        value.gameplayIntegrityErrorCount(), value.invalidStructureHealthCount(),
                        value.duplicateStructureActionCount(), value.nexusDestroyedWithTurretAliveCount(),
                        value.postFinishMutationEventCount(), value.impossibleRespawnTransitionCount(),
                        value.maximumHealthDifferenceCount(), value.directRandomCallCount(),
                        value.perspectiveMismatchCount(), value.offContributionCount(), value.pass()),
                new PairedDiagnosticAuditGate.VerificationEvidence(pair.verification().replayChecked(),
                        pair.verification().replayExact(), pair.verification().instrumentationProfilesChecked(),
                        pair.verification().instrumentationTimelineRandomExact()));
        return auditGate.envelope(row);
    }

    private PairedDiagnosticAuditGate.ProfileExecution profileExecution(RunSummary run) {
        String outcome = auditGate.canonicalHash(Map.of("winner", run.winnerSide(),
                "endReason", run.endReason(), "duration", run.durationSeconds(),
                "objective", run.objectiveSignature(), "timeline", run.timelineHash(),
                "randomCount", run.randomDrawCount(), "randomHash", run.randomTraceHash()));
        String structures = auditGate.canonicalHash(Map.of("signature", run.structureSignature(),
                "validation", run.structureValidation()));
        return new PairedDiagnosticAuditGate.ProfileExecution(run.profileId().name(), run.configurationHash(),
                run.activeGameplayRulesVersion(), run.engineImplementationVersion(),
                run.resourceProvenanceHash(), run.replayProvenanceHash(), outcome, structures,
                run.maximumStructureHealthHash(), run.structuredDiagnosticsHash());
    }

    private FinalizationResult writeArtifacts(Path output, Binding binding, List<PairRow> pairs,
                                               PairedDiagnosticAuditGate.ReceiptManifest receipts) throws Exception {
        writeAtomic(output.resolve("worker-receipt-manifest.json"), canonicalBytes(receipts));
        StringBuilder pairJsonl = new StringBuilder();
        StringBuilder appJsonl = new StringBuilder();
        for (PairRow pair : pairs) {
            pairJsonl.append(canonical.writeValueAsString(pair)).append('\n');
            for (CompositionApplicationProvenance app : pair.fullSystem().applicationProvenance()) {
                appJsonl.append(canonical.writeValueAsString(new ApplicationRow(pair.pairKey(),
                        pair.fixtureId(), pair.blueTeamCode(), pair.redTeamCode(), pair.seedIndex(),
                        pair.seed(), app))).append('\n');
            }
        }
        writeAtomic(output.resolve("paired-comparisons.jsonl"), pairJsonl.toString().getBytes(StandardCharsets.UTF_8));
        writeAtomic(output.resolve("structured-application-provenance.jsonl"), appJsonl.toString().getBytes(StandardCharsets.UTF_8));

        List<RunSummary> full = pairs.stream().map(PairRow::fullSystem).toList();
        long initialized = full.stream().filter(RunSummary::compositionInitialized).count();
        long attempts = full.stream().mapToLong(RunSummary::actualAttemptCount).sum();
        long mapped = full.stream().mapToLong(RunSummary::mappedAttemptCount).sum();
        long unmapped = full.stream().mapToLong(RunSummary::unmappedAttemptCount).sum();
        long applied = full.stream().mapToLong(RunSummary::gameplayApplicationCount).sum();
        long calculated = full.stream().mapToLong(RunSummary::modifierCalculatedCount).sum();
        long nonzero = full.stream().mapToLong(RunSummary::nonZeroModifierCount).sum();
        long consumed = full.stream().mapToLong(RunSummary::modifierConsumedCount).sum();
        long totalEffectApplications = full.stream().flatMap(value -> value.applicationProvenance().stream())
                .filter(CompositionApplicationProvenance::applicationApplied).count();
        long existingNonScalarConsumed = full.stream().flatMap(value -> value.applicationProvenance().stream())
                .filter(CompositionApplicationProvenance::existingNonScalarEffectConsumed).count();
        long changed = full.stream().mapToLong(RunSummary::localDecisionChangedCount).sum();
        long unchanged = full.stream().mapToLong(RunSummary::localDecisionUnchangedCount).sum();
        long existingNonScalar = full.stream().flatMap(value -> value.applicationProvenance().stream())
                .filter(value -> value.modifierConsumed()
                        && Math.abs(value.existingNonScalarCompositionDelta()) > 1e-12).count();
        long publicDiverged = pairs.stream().filter(value ->
                value.divergence().firstPublicTimelineDivergenceSeconds() >= 0).count();
        long unexplained = pairs.stream().filter(value -> value.causalBinding().unexplained()).count();
        long bound = pairs.stream().filter(value ->
                "BOUND_TO_LOCAL_CHANGED_ATTEMPT".equals(value.causalBinding().status())).count();
        TreeMap<String, Long> contexts = new TreeMap<>();
        TreeMap<String, Long> approval = new TreeMap<>();
        TreeMap<String, Long> eligibility = new TreeMap<>();
        for (RunSummary run : full) for (var app : run.applicationProvenance()) {
            contexts.merge(Objects.toString(app.context(), "UNMAPPED") + "|" + app.actionType()
                    + "|" + app.scoreDomain() + "|" + app.applicationPoint(), 1L, Long::sum);
            approval.merge(app.approvalStatus(), 1L, Long::sum);
            eligibility.merge(app.routingEligibility() + "|" + app.eligibilityReason(), 1L, Long::sum);
        }
        Map<String, Object> applicationSummary = new LinkedHashMap<>();
        applicationSummary.put("schemaVersion", "COMPOSITION_V9_APPLICATION_ACCOUNTING_SUMMARY_V3");
        applicationSummary.put("rootCauseClassifications", List.of("APPLICATION_ACCOUNTING_ONLY_ZERO",
                "DIAGNOSTIC_MODE_CONFLATION", "ATTEMPT_OR_CAUSE_IDENTITY_BROKEN"));
        applicationSummary.put("gameplayApplicationReallyZero", false);
        applicationSummary.put("profileMatchCount", 400);
        applicationSummary.put("initializedMatchCount", initialized);
        applicationSummary.put("actualAttemptCount", attempts);
        applicationSummary.put("mappedAttemptCount", mapped);
        applicationSummary.put("unmappedAttemptCount", unmapped);
        applicationSummary.put("modifierCalculatedCount", calculated);
        applicationSummary.put("approvedScalarApplicationCount", applied);
        applicationSummary.put("applicationAppliedCount", totalEffectApplications);
        applicationSummary.put("totalCompositionEffectApplicationCount", totalEffectApplications);
        applicationSummary.put("existingNonScalarEffectConsumedCount", existingNonScalarConsumed);
        applicationSummary.put("nonZeroModifierCount", nonzero);
        applicationSummary.put("modifierConsumedCount", consumed);
        applicationSummary.put("localDecisionChangedCount", changed);
        applicationSummary.put("localDecisionUnchangedCount", unchanged);
        applicationSummary.put("existingNonScalarCompositionContributionCount", existingNonScalar);
        applicationSummary.put("gameplayEffectComponents", List.of(
                "FROZEN_SCALAR_WINNER_MODIFIER",
                "EXISTING_COMPOSITION_SUPPORT_TOOL_TEAMFIGHT_SCORE"));
        applicationSummary.put("causalCounterfactual", "MATCHUP_ONLY_COMPOSITION_OFF_DECISION_INPUT");
        applicationSummary.put("contextActionDomainApplicationPoint", contexts);
        applicationSummary.put("approvalStatus", approval);
        applicationSummary.put("eligibilityReasons", eligibility);
        writeAtomic(output.resolve("application-accounting-root-cause-and-context-summary.json"),
                canonicalBytes(applicationSummary));

        Map<String, Object> causal = new LinkedHashMap<>();
        causal.put("schemaVersion", "COMPOSITION_V9_LOCAL_PUBLIC_CAUSAL_BINDING_SUMMARY_V3");
        causal.put("pairedComparisonCount", pairs.size());
        causal.put("publicTimelineDivergencePairCount", publicDiverged);
        causal.put("boundLocalCausePairCount", bound);
        causal.put("unexplainedPublicDivergenceCount", unexplained);
        causal.put("bindingCoveragePercent", publicDiverged == 0 ? 100.0 : 100.0 * bound / publicDiverged);
        writeAtomic(output.resolve("local-decision-public-divergence-causal-binding-summary.json"), canonicalBytes(causal));
        writeAtomic(output.resolve("unexplained-public-divergence-rows.json"), canonicalBytes(
                pairs.stream().filter(value -> value.causalBinding().unexplained()).toList()));

        long winnerChanged = pairs.stream().filter(PairRow::winnerChanged).count();
        long objectiveChanged = pairs.stream().filter(PairRow::objectiveChanged).count();
        long structureChanged = pairs.stream().filter(PairRow::structureChanged).count();
        double meanDurationDelta = pairs.stream().mapToInt(PairRow::durationDeltaSeconds).average().orElse(0);
        writeAtomic(output.resolve("observational-sensitivity-summary.json"), canonicalBytes(Map.of(
                "schemaVersion", "COMPOSITION_V9_OBSERVATIONAL_SENSITIVITY_V3",
                "pairedComparisonCount", pairs.size(), "winnerChangedCount", winnerChanged,
                "objectiveChangedCount", objectiveChanged, "structureChangedCount", structureChanged,
                "meanDurationDeltaSeconds", meanDurationDelta,
                "productionEligibilityInterpretation", "NOT_EVALUATED_CALIBRATION_ONLY")));

        long correctnessFailures = pairs.stream().filter(value -> !value.correctness().pass()).count();
        writeAtomic(output.resolve("correctness-replay-instrumentation-summary.json"), canonicalBytes(Map.of(
                "schemaVersion", "COMPOSITION_V9_CORRECTNESS_REPLAY_INSTRUMENTATION_V3",
                "correctnessFailurePairs", correctnessFailures,
                "replayChecks", pairs.stream().filter(value -> value.verification().replayChecked()).count(),
                "replayMismatchCount", pairs.stream().filter(value -> value.verification().replayChecked()
                        && !value.verification().replayExact()).count(),
                "instrumentationProfileChecks", pairs.stream().mapToInt(value ->
                        value.verification().instrumentationProfilesChecked()).sum(),
                "instrumentationMismatchCount", pairs.stream().filter(value ->
                        !value.verification().instrumentationTimelineRandomExact()).count(),
                "directCompositionRandomCount", full.stream().mapToLong(RunSummary::directCompositionRandomCalls).sum(),
                "compositionRandomDrawCount", full.stream().mapToLong(RunSummary::compositionRandomDraws).sum(),
                "matchupOnlyCompositionContributionCount", pairs.stream().mapToLong(value ->
                        value.matchupOnly().gameplayApplicationCount()).sum())));

        String verdict = unexplained == 0 && correctnessFailures == 0 && applied > 0 && consumed == applied
                ? "COMPOSITION_APPLICATION_CAUSALITY_HARDENED_READY_FOR_FRESH_REQUALIFICATION_DESIGN"
                : unexplained > 0 ? "COMPOSITION_CAUSALITY_BLOCKED_BY_PROVENANCE_GAP"
                : "COMPOSITION_HARDENING_BLOCKED_BY_GAMEPLAY_INTEGRITY_REGRESSION";
        writeAtomic(output.resolve("final-recommendation.json"), canonicalBytes(Map.of(
                "schemaVersion", "COMPOSITION_V9_HARDENING_RECOMMENDATION_V3", "verdict", verdict,
                "productionActivation", false, "officialEligibility", false,
                "productionProfile", MatchEngineV1Policy.authoritative().retainedRuntimeProfileId(),
                "engineVersionChanged", false, "activeGameplayRulesChanged", false,
                "diagnosticSchema", CompositionRuntimeDiagnostics.SCHEMA_VERSION,
                "applicationProvenanceSchema", CompositionApplicationProvenance.SCHEMA_VERSION)));
        String analysis = "# Composition V9 application and causality hardening\n\n"
                + "- Verdict: `" + verdict + "`\n"
                + "- Core rows/pairs/total simulations: 800 / 400 / 1,100\n"
                + "- Approved scalar applications/consumed/non-zero: " + applied + " / " + consumed + " / " + nonzero + "\n"
                + "- Total Composition effect applications: " + totalEffectApplications + "\n"
                + "- Existing non-scalar Composition support-tool contributions: " + existingNonScalar + "\n"
                + "- Local changed/unchanged: " + changed + " / " + unchanged + "\n"
                + "- Public divergence/bound/unexplained: " + publicDiverged + " / " + bound + " / " + unexplained + "\n"
                + "- This calibration-only evidence does not activate Composition or establish production eligibility.\n";
        writeAtomic(output.resolve("analysis.md"), analysis.getBytes(StandardCharsets.UTF_8));

        writeManifest(output);
        String manifestHash = fileHash(output.resolve("SHA256SUMS.txt"));
        return new FinalizationResult("COMPOSITION_V9_CAUSALITY_FINALIZATION_V3", 800, 400,
                1100, verdict, false, manifestHash);
    }

    private void writeManifest(Path output) throws IOException {
        List<Path> files;
        try (var stream = Files.list(output)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(value -> !value.getFileName().toString().equals("SHA256SUMS.txt"))
                    .sorted(Comparator.comparing(value -> value.getFileName().toString())).toList();
        }
        StringBuilder manifest = new StringBuilder();
        for (Path file : files) manifest.append(fileHash(file)).append("  ")
                .append(file.getFileName()).append('\n');
        writeAtomic(output.resolve("SHA256SUMS.txt"), manifest.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> profileBindings() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (var profile : CompositionV9ApplicationCausalityContract.PROFILES) {
            result.put(profile.name(), SimulationRuntimeProfiles.resolve(profile));
        }
        return result;
    }

    private byte[] canonicalBytes(Object value) throws IOException {
        byte[] raw = canonical.writeValueAsBytes(value);
        byte[] bytes = java.util.Arrays.copyOf(raw, raw.length + 1);
        bytes[raw.length] = '\n';
        return bytes;
    }

    private static void writeAtomic(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        Files.write(temporary, bytes);
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String fileHash(Path path) throws IOException { return hash(Files.readAllBytes(path)); }
    private static String hash(Object value) { return CompositionV9ApplicationCausalityContract.sha256(Objects.toString(value)); }
    private static String hash(byte[] value) { return CompositionV9ApplicationCausalityContract.sha256(value); }
    private static Path sidecar(Path path) { return path.resolveSibling(path.getFileName() + ".sha256"); }
    private static String firstHash(Path path) throws IOException {
        String value = Files.readString(path, StandardCharsets.UTF_8).trim();
        if (value.length() < 64) throw new IllegalStateException("Missing SHA256: " + path);
        return value.substring(0, 64);
    }
    private static String pairKey(String fixture, int seedIndex, long seed) {
        return fixture + "|" + seedIndex + "|" + seed;
    }
    private static String git(Path root, String... args) throws Exception {
        ArrayList<String> command = new ArrayList<>(); command.add("git"); command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) throw new IllegalStateException("git identity failed: " + output);
        return output;
    }

    private enum SnapshotScope { PUBLIC, PRESSURE, ECONOMY }
    private record TimedProjection(int timeSeconds, String value) { }
    private record Executed(SimulationRuntimeProfileId profileId,
                            Phase13GB1SimulationExecutor.Execution execution,
                            SimulationExecutionProvenance provenance) { }
    public record Binding(String contractHash, SourceIdentity sourceIdentity) { }
    public record SourceIdentity(String gitHead, String workingTreeStatus, String productionSourceHash,
                                 String harnessSourceHash, String engineImplementationVersion,
                                 String productionPolicyHash) { }
    public record FreezeResult(String contractHash, String scheduleHash, String harnessSourceHash,
                               CompositionV9ApplicationCausalityContract.SeedOverlapAudit overlapAudit) { }
    public record ShardResult(int shardIndex, int fixtureCount, int pairCount, int replayChecks,
                              int instrumentationChecks, String workerProcessIdentityHash) { }
    public record ShardCheckpoint(String schemaVersion, String contractHash, String harnessSourceHash,
                                  int shardIndex, int shardCount, int fixtureCount, int matchRowCount,
                                  int pairCount, int replayCheckCount, int instrumentationCheckCount,
                                  List<PairRow> pairs) { public ShardCheckpoint { pairs = List.copyOf(pairs); } }
    public record FixedInput(Map<String, String> championsBySidePosition,
                             Map<String, String> playerIdsBySidePosition,
                             List<String> hardFearlessExclusionsBeforeDraft) {
        public FixedInput { championsBySidePosition = Map.copyOf(championsBySidePosition);
            playerIdsBySidePosition = Map.copyOf(playerIdsBySidePosition);
            hardFearlessExclusionsBeforeDraft = List.copyOf(hardFearlessExclusionsBeforeDraft); }
    }
    public record PairRow(String schemaVersion, int fixtureIndex, String fixtureId,
                          Phase13GB1AuditSchedule.FixtureLane fixtureLane, String unorderedTeamPairId,
                          String blueTeamCode, String redTeamCode, int seriesGameNumber, int seedIndex,
                          long seed, String pairKey, FixedInput fixedInput, String rosterIdentityHash,
                          String seriesHistoryBeforeHash, String draftDecisionHash, String finalDraftHash,
                          String finalAssignmentHash, boolean inputIdentityExact, RunSummary matchupOnly,
                          RunSummary fullSystem, Divergence divergence, CausalBinding causalBinding,
                          boolean winnerChanged, boolean objectiveChanged, boolean structureChanged,
                          int durationDeltaSeconds, Correctness correctness, Verification verification) { }
    public record RunSummary(SimulationRuntimeProfileId profileId, String configurationHash,
                             String engineImplementationVersion, String activeGameplayRulesVersion,
                             String resourceProvenanceHash, String replayProvenanceHash, String timelineHash,
                             long randomDrawCount, String randomTraceHash, TeamSide winnerSide,
                             GameEndReason endReason, int durationSeconds, String objectiveSignature,
                             String structureSignature, String maximumStructureHealthHash,
                             boolean compositionInitialized, int resolverEvaluationCount,
                             int triggerSuccessCount, int actualAttemptCount, int mappedAttemptCount,
                             int unmappedAttemptCount, int gameplayApplicationCount,
                             int modifierCalculatedCount, int nonZeroModifierCount, int modifierConsumedCount,
                             int localDecisionChangedCount, int localDecisionUnchangedCount,
                             int publicActionBindingCount, int directCompositionRandomCalls,
                             int compositionRandomDraws, int duplicateApplicationPointCount,
                             int multiContextAttemptCount, int conflictingPerspectiveCount,
                             List<CompositionApplicationProvenance> applicationProvenance,
                             long gameplayIntegrityErrors, StructureValidation structureValidation,
                             String structuredDiagnosticsHash) {
        public RunSummary { applicationProvenance = List.copyOf(applicationProvenance); }
    }
    public record Divergence(int firstPublicTimelineDivergenceSeconds, int firstCombatDivergenceSeconds,
                             int firstPressureDivergenceSeconds, int firstEconomyDivergenceSeconds,
                             int firstObjectiveDivergenceSeconds, int firstStructureDivergenceSeconds) { }
    public record CausalBinding(int firstApplicationSeconds, int firstLocalChangeSeconds,
                                int firstPublicDivergenceSeconds, String status, Long attemptSequence,
                                String publicActionId, MatchEventType publicEventType,
                                String publicCombatSource, TeamCompositionContext context,
                                boolean unexplained) { }
    public record Correctness(long timeoutCount, long gameplayIntegrityErrorCount,
                              long invalidStructureHealthCount, long duplicateStructureActionCount,
                              long nexusDestroyedWithTurretAliveCount, long postFinishMutationEventCount,
                              long impossibleRespawnTransitionCount, long maximumHealthDifferenceCount,
                              long directRandomCallCount, long perspectiveMismatchCount,
                              long offContributionCount, long duplicateApplicationCount, boolean pass) { }
    public record Verification(boolean replayChecked, boolean replayExact,
                               int instrumentationProfilesChecked,
                               boolean instrumentationTimelineRandomExact) {
        static Verification notChecked() { return new Verification(false, true, 0, true); }
    }
    public record StructureValidation(int invalidHealth, int duplicateStructureActionIds,
                                      int nexusDestroyedWithTurretAlive, int postFinishMutationOrEvent,
                                      int impossibleRespawnTransition) { }
    public record ApplicationRow(String pairKey, String fixtureId, String blueTeamCode,
                                 String redTeamCode, int seedIndex, long seed,
                                 CompositionApplicationProvenance application) { }
    public record FinalizationResult(String schemaVersion, int matchRowCount, int pairedComparisonCount,
                                     int totalSimulationCount, String verdict, boolean productionChanged,
                                     String manifestHash) { }
}
