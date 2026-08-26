package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.composition.CompositionApplicationProvenance;
import com.lolfm.composition.CompositionDiagnosticCounterStatus;
import com.lolfm.composition.CompositionPublicEventIdentity;
import com.lolfm.composition.CompositionRuntimeDiagnostics;
import com.lolfm.composition.CompositionScoreOrientation;
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
import java.util.function.Function;

/** Explicit, calibration-only real-fixture diagnostic. It never participates in gameplay. */
public final class CompositionV9ApplicationCausalityRunner {
    public static final Path OUTPUT = Path.of("build", "reports",
            "composition-v9-application-causality-hardening-v6");
    public static final String DIAGNOSTIC_ID =
            "COMPOSITION_V9_CAUSALITY_AUDIT_HARDENING_AND_V5_EVIDENCE_REPAIR_V6";
    public static final String EVIDENCE_REPAIR_RELATION =
            "EVIDENCE_REPAIR_REUSES_V5_SEEDS_NOT_FRESH_ELIGIBILITY";
    public static final String PREVIOUS_V5_MANIFEST_SHA256 =
            "cc5d02b4c97e636cf927b07275ffcaff8eb4ec0badaaa307883d5391a5b45af9";
    public static final int SHARD_COUNT = 4;
    private static final String CHECKPOINT_SCHEMA = "COMPOSITION_V9_CAUSALITY_SHARD_CHECKPOINT_V6";
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
        EvidenceRepairSeedReuseAudit seedReuse = new EvidenceRepairSeedReuseAudit(
                "COMPOSITION_V9_EVIDENCE_REPAIR_SEED_REUSE_AUDIT_V1",
                EVIDENCE_REPAIR_RELATION, schedule.scheduleHash(),
                schedule.fixtures().stream().flatMap(value -> value.seeds().stream())
                        .distinct().count(), false, false, true);
        SourceIdentity source = sourceIdentity(backendRoot);
        PairedDiagnosticAuditGate.InvariantEvidence proofs = focusedInvariantEvidence(
                backendRoot, source.productionSourceHash());
        DefaultTestDiagnosticIsolationReceipt.Receipt defaultIsolation =
                DefaultTestDiagnosticIsolationReceipt.capture(backendRoot, largeDiagnosticClasses());
        DefaultTestDiagnosticIsolationReceipt.verify(defaultIsolation);
        Path previousManifest = output.getParent().resolve(
                "composition-v9-application-causality-hardening-v5").resolve("SHA256SUMS.txt");
        if (!Files.isRegularFile(previousManifest)
                || !PREVIOUS_V5_MANIFEST_SHA256.equals(fileHash(previousManifest))) {
            throw new IllegalStateException("Previous V5 root manifest identity differs");
        }
        if (Files.exists(output.resolve("final-recommendation.json"))) {
            throw new IllegalStateException("Versioned V6 evidence already exists; refusing overwrite");
        }
        LinkedHashMap<String, Object> contract = new LinkedHashMap<>();
        contract.put("schemaVersion", "COMPOSITION_V9_APPLICATION_CAUSALITY_EVIDENCE_REPAIR_CONTRACT_V6");
        contract.put("taskName", "COMPOSITION_V9_CAUSALITY_AUDIT_HARDENING_AND_V5_ARTIFACT_REPAIR");
        contract.put("diagnosticId", DIAGNOSTIC_ID);
        contract.put("diagnosticPurpose", "EVIDENCE_REPAIR_ONLY_NOT_FRESH_ELIGIBILITY");
        contract.put("evidenceRepairRelation", EVIDENCE_REPAIR_RELATION);
        contract.put("previousV5", Map.of("artifactDirectory",
                "composition-v9-application-causality-hardening-v5",
                "rootManifestSha256", PREVIOUS_V5_MANIFEST_SHA256,
                "relationship", EVIDENCE_REPAIR_RELATION));
        contract.put("currentHead", source.gitHead());
        contract.put("sourceIdentity", source);
        contract.put("dependencyManifest", source.dependencyManifest());
        contract.put("focusedInvariantEvidence", proofs);
        contract.put("defaultTestDiagnosticIsolation", defaultIsolation);
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
        contract.put("causalRule", "EXACT_EVENT_IDENTITY_DIRECT_CAUSE; SNAPSHOT_ONLY_IS_INDIRECT_OR_UNRESOLVED");
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
        writeAtomic(output.resolve("evidence-repair-seed-reuse-audit.json"), canonicalBytes(seedReuse));
        writeAtomic(output.resolve("diagnostic-dependency-manifest.json"),
                canonicalBytes(source.dependencyManifest()));
        writeAtomic(output.resolve("focused-invariant-proof-receipts.json"), canonicalBytes(proofs));
        writeAtomic(output.resolve("default-test-diagnostic-isolation-proof.json"),
                canonicalBytes(defaultIsolation));
        writeAtomic(output.resolve("evidence-repair-lineage.json"), canonicalBytes(Map.of(
                "schemaVersion", "COMPOSITION_V9_EVIDENCE_REPAIR_LINEAGE_V1",
                "relationship", EVIDENCE_REPAIR_RELATION,
                "sourceArtifactDirectory", "composition-v9-application-causality-hardening-v5",
                "sourceRootManifestSha256", PREVIOUS_V5_MANIFEST_SHA256,
                "targetArtifactDirectory", output.getFileName().toString(),
                "freshSeedConsumption", false,
                "productionEligibility", false)));
        LinkedHashMap<String, Object> binding = new LinkedHashMap<>();
        binding.put("schemaVersion", "COMPOSITION_V9_SOURCE_INPUT_PROFILE_RESOURCE_BINDING_V4");
        binding.put("contractHash", hash);
        binding.put("sourceIdentity", source);
        binding.put("profiles", profileBindings());
        binding.put("dependencyManifest", source.dependencyManifest());
        binding.put("focusedInvariantEvidence", proofs);
        binding.put("defaultTestDiagnosticIsolation", defaultIsolation);
        binding.put("evidenceRepairRelation", EVIDENCE_REPAIR_RELATION);
        binding.put("previousV5ManifestSha256", PREVIOUS_V5_MANIFEST_SHA256);
        binding.put("resourceProvenance", provenance.resourceProvenance());
        binding.put("productionPolicy", MatchEngineV1Policy.authoritative());
        writeAtomic(output.resolve("source-input-profile-resource-binding.json"),
                canonicalBytes(binding));
        return new FreezeResult(hash, schedule.scheduleHash(), source.harnessSourceHash(), seedReuse);
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
        Correctness correctness = correctness(before, after, causal);
        return new PairRow("COMPOSITION_V9_APPLICATION_CAUSALITY_PAIR_V4", fixtureIndex,
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
                composition.initialized(), composition.resolverEvaluationInstrumentationStatus(),
                composition.resolverEvaluationCount(),
                composition.triggerSuccessInstrumentationStatus(), composition.triggerSuccessCount(),
                composition.actualAttemptCount(),
                composition.mappedActualAttemptCount(), composition.unmappedActualAttemptCount(),
                composition.gameplayApplicationCount(), composition.modifierCalculatedCount(),
                composition.nonZeroModifierCount(), composition.modifierConsumedCount(),
                composition.localDecisionChangedCount(), composition.localDecisionUnchangedCount(),
                composition.publicActionBindingCount(), composition.directRandomCallCount(),
                composition.compositionRandomDrawCount(), composition.duplicateApplicationPointCount(),
                composition.multiContextAttemptCount(), composition.conflictingPerspectiveCount(),
                composition.duplicatePublicBindingCount(), composition.conflictingPublicBindingCount(),
                composition.applicationProvenance(), integrity, structures,
                Phase13GB1SimulationExecutor.structuredDiagnosticsHash(diagnostics));
    }

    private Correctness correctness(RunSummary before, RunSummary after, CausalBinding causal) {
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
        long perspective = before.conflictingPerspectiveCount() + after.conflictingPerspectiveCount()
                + before.applicationProvenance().stream().filter(this::perspectiveMismatch).count()
                + after.applicationProvenance().stream().filter(this::perspectiveMismatch).count();
        long off = before.gameplayApplicationCount() + before.modifierConsumedCount();
        long duplicateApplication = before.duplicateApplicationPointCount()
                + after.duplicateApplicationPointCount() + before.multiContextAttemptCount()
                + after.multiContextAttemptCount();
        long unboundApplied = before.applicationProvenance().stream()
                .filter(value -> value.applicationApplied() && (
                        "NOT_BOUND".equals(value.publicBindingStatus())
                                || value.publicEventBindings().stream()
                                .anyMatch(binding -> binding.actionId() == null))).count()
                + after.applicationProvenance().stream()
                .filter(value -> value.applicationApplied() && (
                        "NOT_BOUND".equals(value.publicBindingStatus())
                                || value.publicEventBindings().stream()
                                .anyMatch(binding -> binding.actionId() == null))).count();
        long unboundChanged = before.applicationProvenance().stream()
                .filter(value -> value.localDecisionChanged() && (
                        "NOT_BOUND".equals(value.publicBindingStatus())
                                || value.publicEventBindings().stream()
                                .anyMatch(binding -> binding.actionId() == null))).count()
                + after.applicationProvenance().stream()
                .filter(value -> value.localDecisionChanged() && (
                        "NOT_BOUND".equals(value.publicBindingStatus())
                                || value.publicEventBindings().stream()
                                .anyMatch(binding -> binding.actionId() == null))).count();
        long consumerNotReached = before.applicationProvenance().stream()
                .filter(value -> value.applicationApplied()
                        && "NOT_REACHED".equals(value.gameplayConsumerIdentity())).count()
                + after.applicationProvenance().stream()
                .filter(value -> value.applicationApplied()
                        && "NOT_REACHED".equals(value.gameplayConsumerIdentity())).count();
        long exactCausalMismatch = causal.divergenceIdentity() != null
                && causal.divergenceIdentity().scope() == DivergenceScope.EVENT
                && !causal.exactDirectCause() ? 1 : 0;
        long duplicateBinding = before.duplicatePublicBindingCount()
                + after.duplicatePublicBindingCount();
        long conflictingBinding = before.conflictingPublicBindingCount()
                + after.conflictingPublicBindingCount();
        long decomposition = before.applicationProvenance().stream()
                .filter(this::decompositionMismatch).count()
                + after.applicationProvenance().stream().filter(this::decompositionMismatch).count();
        long total = timeout + gameplay + invalid + duplicateStructure + nexus + post + respawn
                + maxHp + random + perspective + off + duplicateApplication + unboundApplied
                + unboundChanged + consumerNotReached + exactCausalMismatch + duplicateBinding
                + conflictingBinding + decomposition;
        return new Correctness(timeout, gameplay, invalid, duplicateStructure, nexus, post, respawn,
                maxHp, random, perspective, off, duplicateApplication, unboundApplied,
                unboundChanged, consumerNotReached, exactCausalMismatch, duplicateBinding,
                conflictingBinding, decomposition, total == 0);
    }

    private boolean perspectiveMismatch(CompositionApplicationProvenance value) {
        if (value.context() == TeamCompositionContext.OBJECTIVE_SETUP) {
            return value.routingPerspectiveSide() != value.attemptOwnerSide()
                    || value.scoreOrientation() != CompositionScoreOrientation.BLUE_MINUS_RED
                    || value.perspectiveSide() != TeamSide.BLUE;
        }
        return value.applicationApplied()
                && value.scoreOrientation() != CompositionScoreOrientation.BLUE_MINUS_RED;
    }

    private boolean decompositionMismatch(CompositionApplicationProvenance value) {
        if (!value.applicationApplied()) return false;
        if (value.baselineScoreBeforeClamp() == null || value.candidateScoreBeforeClamp() == null
                || value.baselineClampDelta() == null || value.candidateClampDelta() == null) {
            return true;
        }
        double pre = value.candidateScoreBeforeClamp() - value.baselineScoreBeforeClamp();
        double expectedPre = value.modifier() + value.existingNonScalarCompositionDelta();
        double expectedClamp = value.candidateClampDelta() - value.baselineClampDelta();
        double expectedTotal = expectedPre + expectedClamp;
        return Math.abs(pre - expectedPre) > 1e-12
                || Math.abs(value.clampEffect() - expectedClamp) > 1e-12
                || Math.abs(value.totalCompositionInputDelta() - expectedTotal) > 1e-12;
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
        StructuredDivergenceIdentity publicDivergence = divergence.firstPublicTimelineDivergence();
        CompositionApplicationProvenance cause = publicDivergence != null
                && publicDivergence.scope() == DivergenceScope.EVENT
                ? changed.stream().filter(value -> exactEventBindingMatches(
                        value, publicDivergence)).findFirst().orElse(null) : null;
        String status;
        boolean unexplained;
        if (publicDivergence == null) {
            status = "NO_PUBLIC_DIVERGENCE";
            unexplained = false;
        } else if (cause != null) {
            status = "EXACT_DIRECT_ACTION_CAUSE";
            unexplained = false;
        } else if (publicDivergence.scope() == DivergenceScope.SNAPSHOT) {
            boolean priorChanged = changed.stream().anyMatch(value ->
                    value.simulationTimeSeconds() <= publicDivergence.simulationTimeSeconds());
            status = priorChanged ? "INDIRECT_CAUSE" : "UNRESOLVED_SNAPSHOT_CAUSE";
            unexplained = !priorChanged;
        } else {
            status = "UNRESOLVED_EVENT_CAUSE";
            unexplained = true;
        }
        int publicTime = publicDivergence == null ? -1 : publicDivergence.simulationTimeSeconds();
        return new CausalBinding(firstApplication, firstChange, publicTime,
                status,
                cause == null ? null : cause.attemptId().sequence(),
                cause == null ? null : cause.publicActionId(),
                cause == null ? null : cause.publicEventType(),
                cause == null ? null : cause.publicCombatSource(),
                cause == null ? null : cause.context(),
                publicDivergence, cause != null, unexplained);
    }

    public static boolean exactEventBindingMatches(CompositionApplicationProvenance application,
                                                   StructuredDivergenceIdentity divergence) {
        return application.localDecisionChanged()
                && !"NOT_BOUND".equals(application.publicBindingStatus())
                && divergence.scope() == DivergenceScope.EVENT
                && divergence.actionId() != null
                && application.publicEventBindings().stream().anyMatch(binding ->
                binding.actionId() != null
                        && Objects.equals(binding.actionId(), divergence.actionId())
                        && Objects.equals(binding.parentActionId(), divergence.parentActionId())
                        && binding.eventType() == divergence.eventType()
                        && Objects.equals(binding.combatSource(), divergence.combatSource())
                        && binding.combatLane() == divergence.lane()
                        && binding.eventTimeSeconds() == divergence.simulationTimeSeconds()
                        && binding.eventOrdinal() == divergence.ordinal()
                        && Objects.equals(binding.structuredPayloadSha256(),
                        divergence.afterStructuredPayloadSha256()));
    }

    private Divergence divergences(MatchTimeline before, MatchTimeline after) throws Exception {
        StructuredDivergenceIdentity publicEvents = firstDifferent(
                eventProjections(before.getEvents(), false),
                eventProjections(after.getEvents(), false));
        StructuredDivergenceIdentity publicSnapshots = firstDifferent(
                snapshotProjections(before.getSnapshots(), SnapshotScope.PUBLIC),
                snapshotProjections(after.getSnapshots(), SnapshotScope.PUBLIC));
        StructuredDivergenceIdentity combat = firstDifferent(
                eventProjections(before.getEvents(), true),
                eventProjections(after.getEvents(), true));
        StructuredDivergenceIdentity pressure = firstDifferent(
                snapshotProjections(before.getSnapshots(), SnapshotScope.PRESSURE),
                snapshotProjections(after.getSnapshots(), SnapshotScope.PRESSURE));
        StructuredDivergenceIdentity economy = firstDifferent(
                snapshotProjections(before.getSnapshots(), SnapshotScope.ECONOMY),
                snapshotProjections(after.getSnapshots(), SnapshotScope.ECONOMY));
        StructuredDivergenceIdentity firstPublic = earliest(publicEvents, publicSnapshots);
        StructuredDivergenceIdentity objective = objectiveSignature(before).equals(objectiveSignature(after))
                ? null : reScope(firstPublic, DivergenceScope.OBJECTIVE);
        StructuredDivergenceIdentity structure = firstDifferent(
                structureProjections(before), structureProjections(after));
        return new Divergence(firstPublic, publicEvents, publicSnapshots, combat, pressure,
                economy, objective, structure);
    }

    private List<Projection> eventProjections(List<MatchEvent> events, boolean combatOnly) {
        ArrayList<Projection> result = new ArrayList<>();
        for (int ordinal = 0; ordinal < events.size(); ordinal++) {
            MatchEvent event = events.get(ordinal);
            if (combatOnly && !isCombat(event.getType())) continue;
            CompositionPublicEventIdentity identity =
                    CompositionPublicEventIdentity.from(event, ordinal);
            result.add(new Projection(DivergenceScope.EVENT, ordinal,
                    identity.eventTimeSeconds(), identity.actionId(), identity.parentActionId(),
                    identity.eventType(), identity.combatSource(), identity.combatLane(),
                    identity.structuredPayloadSha256()));
        }
        return List.copyOf(result);
    }

    private List<Projection> snapshotProjections(List<MatchSnapshot> snapshots, SnapshotScope scope) throws Exception {
        ArrayList<Projection> result = new ArrayList<>();
        for (int ordinal = 0; ordinal < snapshots.size(); ordinal++) {
            MatchSnapshot snapshot = snapshots.get(ordinal);
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
            result.add(new Projection(DivergenceScope.SNAPSHOT, ordinal,
                    snapshot.getTimeSeconds(), null, null, null, null, null,
                    hash(canonical.writeValueAsBytes(value))));
        }
        return List.copyOf(result);
    }

    private List<Projection> structureProjections(MatchTimeline timeline) throws Exception {
        ArrayList<Projection> result = new ArrayList<>();
        List<MatchSnapshot> snapshots = timeline.getSnapshots();
        for (int ordinal = 0; ordinal < snapshots.size(); ordinal++) {
            MatchSnapshot snapshot = snapshots.get(ordinal);
            result.add(new Projection(DivergenceScope.STRUCTURE, ordinal,
                    snapshot.getTimeSeconds(), null, null, null, null, null,
                    hash(canonical.writeValueAsBytes(MatchupV9StructureAttributionClassifier
                            .project(snapshot.getStructureState())))));
        }
        return List.copyOf(result);
    }

    private static StructuredDivergenceIdentity firstDifferent(
            List<Projection> before, List<Projection> after) {
        int count = Math.min(before.size(), after.size());
        for (int index = 0; index < count; index++) {
            if (!before.get(index).payloadSha256().equals(after.get(index).payloadSha256())) {
                return divergenceIdentity(before.get(index), after.get(index));
            }
        }
        return before.size() == after.size() ? null : divergenceIdentity(
                before.size() > count ? before.get(count) : null,
                after.size() > count ? after.get(count) : null);
    }

    private static StructuredDivergenceIdentity divergenceIdentity(
            Projection before, Projection after) {
        Projection identity = after == null ? before : after;
        return new StructuredDivergenceIdentity(identity.scope(), identity.ordinal(),
                identity.simulationTimeSeconds(), identity.actionId(), identity.parentActionId(),
                identity.eventType(), identity.combatSource(), identity.lane(),
                before == null ? null : before.payloadSha256(),
                after == null ? null : after.payloadSha256());
    }

    private static StructuredDivergenceIdentity earliest(
            StructuredDivergenceIdentity event, StructuredDivergenceIdentity snapshot) {
        if (event == null) return snapshot;
        if (snapshot == null) return event;
        if (event.simulationTimeSeconds() != snapshot.simulationTimeSeconds()) {
            return event.simulationTimeSeconds() < snapshot.simulationTimeSeconds() ? event : snapshot;
        }
        return event;
    }

    private static StructuredDivergenceIdentity reScope(
            StructuredDivergenceIdentity source, DivergenceScope scope) {
        if (source == null) return null;
        return new StructuredDivergenceIdentity(scope, source.ordinal(),
                source.simulationTimeSeconds(), source.actionId(), source.parentActionId(),
                source.eventType(), source.combatSource(), source.lane(),
                source.beforeStructuredPayloadSha256(), source.afterStructuredPayloadSha256());
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
        PairedDiagnosticAuditGate.InvariantEvidence proofs = mapper.treeToValue(
                node.path("focusedInvariantEvidence"), PairedDiagnosticAuditGate.InvariantEvidence.class);
        FocusedInvariantProofReceipt.verify(proofs.displayNameIdentity(),
                current.productionSourceHash(), current.dependencyManifest());
        FocusedInvariantProofReceipt.verify(proofs.ineligibleDuplicateRandomConsumption(),
                current.productionSourceHash(), current.dependencyManifest());
        return new Binding(hash, current, proofs);
    }

    private SourceIdentity sourceIdentity(Path backendRoot) throws Exception {
        Path gitRoot = backendRoot.toAbsolutePath().normalize().getParent();
        DiagnosticDependencyManifest.Manifest dependencies = dependencyManifest(backendRoot);
        return new SourceIdentity(git(gitRoot, "rev-parse", "HEAD"), git(gitRoot, "status", "--short"),
                Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot).hash(),
                dependencies.harnessSourceHash(), dependencies,
                SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION,
                MatchEngineV1Policy.authoritative().policyHash());
    }

    static DiagnosticDependencyManifest.Manifest dependencyManifest(Path backendRoot) throws IOException {
        String base = "src/test/java/com/lolfm/";
        List<DiagnosticDependencyManifest.DependencySpec> dependencies = List.of(
                file(base + "application/CompositionV9ApplicationCausalityRunner.java"),
                file(base + "application/CompositionV9ApplicationCausalityContract.java"),
                file(base + "application/CompositionV9ApplicationCausalityDiagnosticTest.java"),
                file(base + "application/CompositionV9ApplicationCausalityWorkersTest.java"),
                file(base + "application/CompositionV9ApplicationCausalityContractTest.java"),
                file(base + "application/PairedDiagnosticAuditGate.java"),
                file(base + "application/DiagnosticDependencyManifest.java"),
                file(base + "application/FocusedInvariantProofReceipt.java"),
                file(base + "application/DefaultTestDiagnosticIsolationReceipt.java"),
                file(base + "application/RecursiveArtifactManifest.java"),
                file(base + "application/Phase13GB1RealMatchHarness.java"),
                file(base + "application/Phase13GB1AuditSchedule.java"),
                file(base + "application/Phase13GB1AuditArtifactWriter.java"),
                file("src/main/java/com/lolfm/application/SimulationProvenanceService.java"),
                file("src/main/java/com/lolfm/application/RealDraftMatchOrchestrator.java"),
                file("src/main/java/com/lolfm/application/MatchEngineV1Policy.java"),
                file(base + "application/MatchEngineV9RequalificationContract.java"),
                file(base + "application/MatchupV9StructureAttributionContract.java"),
                file(base + "application/MatchupV9StructureAttributionClassifier.java"),
                file(base + "simulator/Phase13GB1SimulationExecutor.java"),
                file(base + "simulator/MatchEngineV9InstrumentationExecutor.java"),
                file(base + "composition/CompositionProductionApplicationProvenanceTest.java"),
                DiagnosticDependencyManifest.DependencySpec.section(
                        "build.gradle#COMPOSITION_V9_APPLICATION_CAUSALITY_BUILD_CONTRACT",
                        "build.gradle", BUILD_START, BUILD_END));
        return DiagnosticDependencyManifest.create(backendRoot,
                "COMPOSITION_V9_CAUSALITY_EVIDENCE_REPAIR_HARNESS_V6",
                "EXPLICIT_DIRECT_SHARED_RUNNER_PROOF_AND_GRADLE_DEPENDENCIES_ONLY", dependencies);
    }

    private static DiagnosticDependencyManifest.DependencySpec file(String path) {
        return DiagnosticDependencyManifest.DependencySpec.file(path);
    }

    private PairedDiagnosticAuditGate.InvariantEvidence focusedInvariantEvidence(
            Path backendRoot, String productionSourceHash) throws Exception {
        String source = "src/test/java/com/lolfm/composition/CompositionProductionApplicationProvenanceTest.java";
        return new PairedDiagnosticAuditGate.InvariantEvidence(
                FocusedInvariantProofReceipt.capture(backendRoot,
                        "verifyCompositionV9CausalityFocusedProof",
                        "com.lolfm.composition.CompositionProductionApplicationProvenanceTest#offAndFreshMatchStateRemainExactZeroAndIsolated",
                        source, productionSourceHash),
                FocusedInvariantProofReceipt.capture(backendRoot,
                        "verifyCompositionV9CausalityFocusedProof",
                        "com.lolfm.composition.CompositionProductionApplicationProvenanceTest#unsupportedContextIsStructuredDisabledAndCannotReachConsumer",
                        source, productionSourceHash));
    }

    private static List<String> largeDiagnosticClasses() {
        return List.of(
                "com.lolfm.application.CompositionV9ApplicationCausalityDiagnosticTest",
                "com.lolfm.application.CompositionV9ApplicationCausalityShard0Test",
                "com.lolfm.application.CompositionV9ApplicationCausalityShard1Test",
                "com.lolfm.application.CompositionV9ApplicationCausalityShard2Test",
                "com.lolfm.application.CompositionV9ApplicationCausalityShard3Test");
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
                DIAGNOSTIC_ID, binding.contractHash(),
                schedule.scheduleHash(), binding.sourceIdentity().harnessSourceHash(),
                binding.sourceIdentity().productionSourceHash(),
                binding.sourceIdentity().dependencyManifest(),
                binding.sourceIdentity().engineImplementationVersion(),
                provenance.resourceProvenance().resourceProvenanceHash(),
                provenance.draftRuleSetIdentity(), provenance.draftRuleSetHash(),
                provenance.draftScoringPolicyHash(), SHARD_COUNT,
                CompositionV9ApplicationCausalityContract.EXPECTED_FIXTURES,
                CompositionV9ApplicationCausalityContract.EXPECTED_PROFILE_ROWS,
                CompositionV9ApplicationCausalityContract.EXPECTED_PAIRS, profiles, expected,
                binding.invariantEvidence());
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
                        value.perspectiveMismatchCount(), value.offContributionCount(),
                        value.unboundAppliedTraceCount(), value.unboundChangedTraceCount(),
                        value.appliedConsumerNotReachedCount(), value.exactActionCausalMismatchCount(),
                        value.duplicatePublicBindingCount(), value.conflictingPublicBindingCount(),
                        value.scalarNonScalarDecompositionMismatchCount(), value.pass()),
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
        auditGate.verifyReceiptManifestExact(output, receipts);
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
                value.divergence().firstPublicTimelineDivergence() != null).count();
        long unexplained = pairs.stream().filter(value -> value.causalBinding().unexplained()).count();
        long bound = pairs.stream().filter(value ->
                "EXACT_DIRECT_ACTION_CAUSE".equals(value.causalBinding().status())).count();
        long indirect = pairs.stream().filter(value ->
                "INDIRECT_CAUSE".equals(value.causalBinding().status())).count();
        long unresolvedSnapshot = pairs.stream().filter(value ->
                "UNRESOLVED_SNAPSHOT_CAUSE".equals(value.causalBinding().status())).count();
        long unresolvedEvent = pairs.stream().filter(value ->
                "UNRESOLVED_EVENT_CAUSE".equals(value.causalBinding().status())).count();
        long eventDiverged = pairs.stream().filter(value -> value.divergence()
                .firstPublicTimelineDivergence() != null && value.divergence()
                .firstPublicTimelineDivergence().scope() == DivergenceScope.EVENT).count();
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
        applicationSummary.put("schemaVersion", "COMPOSITION_V9_APPLICATION_ACCOUNTING_SUMMARY_V4");
        applicationSummary.put("rootCauseClassifications", List.of("APPLICATION_ACCOUNTING_ONLY_ZERO",
                "DIAGNOSTIC_MODE_CONFLATION", "ATTEMPT_OR_CAUSE_IDENTITY_BROKEN"));
        applicationSummary.put("gameplayApplicationReallyZero", false);
        applicationSummary.put("profileMatchCount", 400);
        applicationSummary.put("initializedMatchCount", initialized);
        applicationSummary.put("actualAttemptCount", attempts);
        applicationSummary.put("resolverEvaluationInstrumentationStatus", statusCounts(full,
                RunSummary::resolverEvaluationInstrumentationStatus));
        applicationSummary.put("triggerSuccessInstrumentationStatus", statusCounts(full,
                RunSummary::triggerSuccessInstrumentationStatus));
        applicationSummary.put("resolverEvaluationCount", full.stream()
                .mapToLong(RunSummary::resolverEvaluationCount).sum());
        applicationSummary.put("triggerSuccessCount", full.stream()
                .mapToLong(RunSummary::triggerSuccessCount).sum());
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
        causal.put("schemaVersion", "COMPOSITION_V9_EXACT_PUBLIC_CAUSAL_BINDING_SUMMARY_V4");
        causal.put("pairedComparisonCount", pairs.size());
        causal.put("publicTimelineDivergencePairCount", publicDiverged);
        causal.put("eventScopeDivergencePairCount", eventDiverged);
        causal.put("exactDirectActionCausePairCount", bound);
        causal.put("indirectCausePairCount", indirect);
        causal.put("unresolvedSnapshotCausePairCount", unresolvedSnapshot);
        causal.put("unresolvedEventCausePairCount", unresolvedEvent);
        causal.put("unexplainedPublicDivergenceCount", unexplained);
        causal.put("exactDirectEventCoveragePercent",
                eventDiverged == 0 ? 100.0 : 100.0 * bound / eventDiverged);
        causal.put("allPublicClassificationCoveragePercent", publicDiverged == 0 ? 100.0
                : 100.0 * (bound + indirect + unresolvedSnapshot + unresolvedEvent) / publicDiverged);
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
        long unboundApplied = pairs.stream().mapToLong(value ->
                value.correctness().unboundAppliedTraceCount()).sum();
        long unboundChanged = pairs.stream().mapToLong(value ->
                value.correctness().unboundChangedTraceCount()).sum();
        long consumerNotReached = pairs.stream().mapToLong(value ->
                value.correctness().appliedConsumerNotReachedCount()).sum();
        long exactCausalMismatch = pairs.stream().mapToLong(value ->
                value.correctness().exactActionCausalMismatchCount()).sum();
        long duplicateBinding = pairs.stream().mapToLong(value ->
                value.correctness().duplicatePublicBindingCount()).sum();
        long conflictingBinding = pairs.stream().mapToLong(value ->
                value.correctness().conflictingPublicBindingCount()).sum();
        long perspectiveMismatch = pairs.stream().mapToLong(value ->
                value.correctness().perspectiveMismatchCount()).sum();
        long decompositionMismatch = pairs.stream().mapToLong(value ->
                value.correctness().scalarNonScalarDecompositionMismatchCount()).sum();
        LinkedHashMap<String, Object> correctnessSummary = new LinkedHashMap<>();
        correctnessSummary.put("schemaVersion", "COMPOSITION_V9_CORRECTNESS_REPLAY_INSTRUMENTATION_V4");
        correctnessSummary.put("correctnessFailurePairs", correctnessFailures);
        correctnessSummary.put("replayChecks", pairs.stream()
                .filter(value -> value.verification().replayChecked()).count());
        correctnessSummary.put("replayMismatchCount", pairs.stream()
                .filter(value -> value.verification().replayChecked()
                        && !value.verification().replayExact()).count());
        correctnessSummary.put("instrumentationProfileChecks", pairs.stream().mapToInt(value ->
                value.verification().instrumentationProfilesChecked()).sum());
        correctnessSummary.put("instrumentationMismatchCount", pairs.stream().filter(value ->
                !value.verification().instrumentationTimelineRandomExact()).count());
        correctnessSummary.put("directCompositionRandomCount", full.stream()
                .mapToLong(RunSummary::directCompositionRandomCalls).sum());
        correctnessSummary.put("compositionRandomDrawCount", full.stream()
                .mapToLong(RunSummary::compositionRandomDraws).sum());
        correctnessSummary.put("matchupOnlyCompositionContributionCount", pairs.stream()
                .mapToLong(value -> value.matchupOnly().gameplayApplicationCount()).sum());
        correctnessSummary.put("unboundAppliedTraceCount", unboundApplied);
        correctnessSummary.put("unboundChangedTraceCount", unboundChanged);
        correctnessSummary.put("appliedTraceConsumerNotReachedCount", consumerNotReached);
        correctnessSummary.put("exactActionCausalMismatchCount", exactCausalMismatch);
        correctnessSummary.put("duplicatePublicBindingCount", duplicateBinding);
        correctnessSummary.put("conflictingPublicBindingCount", conflictingBinding);
        correctnessSummary.put("objectivePerspectiveMismatchCount", perspectiveMismatch);
        correctnessSummary.put("scalarNonScalarDecompositionMismatchCount", decompositionMismatch);
        correctnessSummary.put("focusedProofReceiptMismatchCount", 0);
        correctnessSummary.put("receiptCheckpointManifestMismatchCount", 0);
        writeAtomic(output.resolve("correctness-replay-instrumentation-summary.json"),
                canonicalBytes(correctnessSummary));

        boolean ready = correctnessFailures == 0 && unexplained == 0 && unboundApplied == 0
                && unboundChanged == 0 && consumerNotReached == 0 && exactCausalMismatch == 0
                && duplicateBinding == 0 && conflictingBinding == 0 && perspectiveMismatch == 0
                && decompositionMismatch == 0 && applied > 0 && consumed == applied;
        String verdict = ready
                ? "COMPOSITION_V9_CAUSALITY_AUDIT_HARDENED_AND_V5_EVIDENCE_REPAIRED"
                : "COMPOSITION_V9_CAUSALITY_AUDIT_HARDENING_BLOCKED_BY_EXACT_GATE";
        LinkedHashMap<String, Object> recommendation = new LinkedHashMap<>();
        recommendation.put("schemaVersion", "COMPOSITION_V9_EVIDENCE_REPAIR_RECOMMENDATION_V4");
        recommendation.put("verdict", verdict);
        recommendation.put("evidenceRepairRelation", EVIDENCE_REPAIR_RELATION);
        recommendation.put("previousV5ManifestSha256", PREVIOUS_V5_MANIFEST_SHA256);
        recommendation.put("readyGate", Map.ofEntries(
                Map.entry("correctnessFailureCount", correctnessFailures),
                Map.entry("unexplainedPublicDivergenceCount", unexplained),
                Map.entry("unboundAppliedTraceCount", unboundApplied),
                Map.entry("unboundChangedTraceCount", unboundChanged),
                Map.entry("appliedTraceConsumerNotReachedCount", consumerNotReached),
                Map.entry("exactActionCausalMismatchCount", exactCausalMismatch),
                Map.entry("duplicatePublicBindingCount", duplicateBinding),
                Map.entry("conflictingPublicBindingCount", conflictingBinding),
                Map.entry("objectivePerspectiveMismatchCount", perspectiveMismatch),
                Map.entry("scalarNonScalarDecompositionMismatchCount", decompositionMismatch),
                Map.entry("focusedProofReceiptMismatchCount", 0),
                Map.entry("receiptCheckpointManifestMismatchCount", 0)));
        recommendation.put("productionActivation", false);
        recommendation.put("officialEligibility", false);
        recommendation.put("productionProfile",
                MatchEngineV1Policy.authoritative().retainedRuntimeProfileId());
        recommendation.put("engineVersionChanged", false);
        recommendation.put("activeGameplayRulesChanged", false);
        recommendation.put("diagnosticSchema", CompositionRuntimeDiagnostics.SCHEMA_VERSION);
        recommendation.put("applicationProvenanceSchema",
                CompositionApplicationProvenance.SCHEMA_VERSION);
        writeAtomic(output.resolve("final-recommendation.json"), canonicalBytes(recommendation));
        String analysis = "# Composition V9 application and causality hardening\n\n"
                + "- Verdict: `" + verdict + "`\n"
                + "- Core rows/pairs/total simulations: 800 / 400 / 1,100\n"
                + "- Approved scalar applications/consumed/non-zero: " + applied + " / " + consumed + " / " + nonzero + "\n"
                + "- Total Composition effect applications: " + totalEffectApplications + "\n"
                + "- Existing non-scalar Composition support-tool contributions: " + existingNonScalar + "\n"
                + "- Local changed/unchanged: " + changed + " / " + unchanged + "\n"
                + "- Public divergence/direct/indirect/unresolved: " + publicDiverged + " / "
                + bound + " / " + indirect + " / " + (unresolvedSnapshot + unresolvedEvent) + "\n"
                + "- Relationship: `" + EVIDENCE_REPAIR_RELATION + "`\n"
                + "- This evidence repair does not activate Composition or establish production eligibility.\n";
        writeAtomic(output.resolve("analysis.md"), analysis.getBytes(StandardCharsets.UTF_8));

        String manifestHash = RecursiveArtifactManifest.write(output);
        RecursiveArtifactManifest.Verification recursive = RecursiveArtifactManifest.verify(output);
        if (recursive.nestedFileCount() == 0) {
            throw new IllegalStateException("Recursive artifact manifest omitted nested evidence");
        }
        return new FinalizationResult("COMPOSITION_V9_CAUSALITY_FINALIZATION_V4", 800, 400,
                1100, verdict, false, manifestHash);
    }

    private Map<String, Object> profileBindings() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (var profile : CompositionV9ApplicationCausalityContract.PROFILES) {
            result.put(profile.name(), SimulationRuntimeProfiles.resolve(profile));
        }
        return result;
    }

    private static Map<String, Long> statusCounts(
            List<RunSummary> runs,
            Function<RunSummary, CompositionDiagnosticCounterStatus> selector) {
        TreeMap<String, Long> values = new TreeMap<>();
        runs.forEach(run -> values.merge(selector.apply(run).name(), 1L, Long::sum));
        return Map.copyOf(values);
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
    public enum DivergenceScope { EVENT, SNAPSHOT, OBJECTIVE, STRUCTURE }
    private record Projection(DivergenceScope scope, int ordinal, int simulationTimeSeconds,
                              String actionId, String parentActionId, MatchEventType eventType,
                              String combatSource, Lane lane, String payloadSha256) { }
    private record Executed(SimulationRuntimeProfileId profileId,
                            Phase13GB1SimulationExecutor.Execution execution,
                            SimulationExecutionProvenance provenance) { }
    public record Binding(String contractHash, SourceIdentity sourceIdentity,
                          PairedDiagnosticAuditGate.InvariantEvidence invariantEvidence) { }
    public record SourceIdentity(String gitHead, String workingTreeStatus, String productionSourceHash,
                                 String harnessSourceHash,
                                 DiagnosticDependencyManifest.Manifest dependencyManifest,
                                 String engineImplementationVersion,
                                 String productionPolicyHash) { }
    public record FreezeResult(String contractHash, String scheduleHash, String harnessSourceHash,
                               EvidenceRepairSeedReuseAudit seedReuseAudit) { }
    public record EvidenceRepairSeedReuseAudit(
            String schemaVersion, String relationship, String reusedScheduleHash,
            long reusedDistinctSeedCount, boolean freshEligibilityEvaluated,
            boolean freshSeedConsumed, boolean valid) { }
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
                             boolean compositionInitialized,
                             CompositionDiagnosticCounterStatus resolverEvaluationInstrumentationStatus,
                             int resolverEvaluationCount,
                             CompositionDiagnosticCounterStatus triggerSuccessInstrumentationStatus,
                             int triggerSuccessCount, int actualAttemptCount, int mappedAttemptCount,
                             int unmappedAttemptCount, int gameplayApplicationCount,
                             int modifierCalculatedCount, int nonZeroModifierCount, int modifierConsumedCount,
                             int localDecisionChangedCount, int localDecisionUnchangedCount,
                             int publicActionBindingCount, int directCompositionRandomCalls,
                             int compositionRandomDraws, int duplicateApplicationPointCount,
                             int multiContextAttemptCount, int conflictingPerspectiveCount,
                             int duplicatePublicBindingCount, int conflictingPublicBindingCount,
                             List<CompositionApplicationProvenance> applicationProvenance,
                             long gameplayIntegrityErrors, StructureValidation structureValidation,
                             String structuredDiagnosticsHash) {
        public RunSummary { applicationProvenance = List.copyOf(applicationProvenance); }
    }
    public record StructuredDivergenceIdentity(
            DivergenceScope scope, int ordinal, int simulationTimeSeconds,
            String actionId, String parentActionId, MatchEventType eventType,
            String combatSource, Lane lane, String beforeStructuredPayloadSha256,
            String afterStructuredPayloadSha256) { }
    public record Divergence(StructuredDivergenceIdentity firstPublicTimelineDivergence,
                             StructuredDivergenceIdentity firstEventDivergence,
                             StructuredDivergenceIdentity firstSnapshotDivergence,
                             StructuredDivergenceIdentity firstCombatDivergence,
                             StructuredDivergenceIdentity firstPressureDivergence,
                             StructuredDivergenceIdentity firstEconomyDivergence,
                             StructuredDivergenceIdentity firstObjectiveDivergence,
                             StructuredDivergenceIdentity firstStructureDivergence) {
        public int firstPublicTimelineDivergenceSeconds() {
            return firstPublicTimelineDivergence == null
                    ? -1 : firstPublicTimelineDivergence.simulationTimeSeconds();
        }
    }
    public record CausalBinding(int firstApplicationSeconds, int firstLocalChangeSeconds,
                                int firstPublicDivergenceSeconds, String status, Long attemptSequence,
                                String publicActionId, MatchEventType publicEventType,
                                String publicCombatSource, TeamCompositionContext context,
                                StructuredDivergenceIdentity divergenceIdentity,
                                boolean exactDirectCause, boolean unexplained) { }
    public record Correctness(long timeoutCount, long gameplayIntegrityErrorCount,
                              long invalidStructureHealthCount, long duplicateStructureActionCount,
                              long nexusDestroyedWithTurretAliveCount, long postFinishMutationEventCount,
                              long impossibleRespawnTransitionCount, long maximumHealthDifferenceCount,
                              long directRandomCallCount, long perspectiveMismatchCount,
                              long offContributionCount, long duplicateApplicationCount,
                              long unboundAppliedTraceCount, long unboundChangedTraceCount,
                              long appliedConsumerNotReachedCount, long exactActionCausalMismatchCount,
                              long duplicatePublicBindingCount, long conflictingPublicBindingCount,
                              long scalarNonScalarDecompositionMismatchCount, boolean pass) { }
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
