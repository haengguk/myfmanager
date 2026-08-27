package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import com.lolfm.composition.CompositionApplicationProvenance;
import com.lolfm.composition.CompositionScoreOrientation;
import com.lolfm.composition.TeamCompositionContext;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Position;
import com.lolfm.domain.StructureStateSnapshot;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.Phase13GB1SimulationExecutor;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import com.lolfm.simulator.TeamSide;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Executes the bounded 1,200-run product-sanity population and writes deterministic raw evidence. */
public final class MatchEngineV9ProductionAcceptanceRunner {
    public static final String REVIEWED_HEAD =
            "d49e15882b353316e7ba4d844b1b449836c5e20b";
    public static final int REPLAY_SIMULATION_COUNT = 4;
    public static final int INSTRUMENTATION_ONLY_SIMULATION_COUNT = 2;
    public static final int ROLLBACK_SIMULATION_COUNT = 4;
    public static final int ADDITIONAL_SIMULATION_COUNT = 10;

    private final ObjectMapper mapper;
    private final ObjectMapper canonical;
    private final ChampionCatalog champions;
    private final LckTeamAssembler teams;
    private final ChampionProficiencyCatalog proficiencies;
    private final ConfiguredMatchSimulatorFactory simulators;
    private final SimulationProvenanceService provenance;

    public MatchEngineV9ProductionAcceptanceRunner(
            ObjectMapper mapper,
            ChampionCatalog champions,
            LckTeamAssembler teams,
            ChampionProficiencyCatalog proficiencies,
            ConfiguredMatchSimulatorFactory simulators,
            SimulationProvenanceService provenance
    ) {
        this.mapper = Objects.requireNonNull(mapper);
        this.canonical = mapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.INDENT_OUTPUT);
        this.champions = Objects.requireNonNull(champions);
        this.teams = Objects.requireNonNull(teams);
        this.proficiencies = Objects.requireNonNull(proficiencies);
        this.simulators = Objects.requireNonNull(simulators);
        this.provenance = Objects.requireNonNull(provenance);
    }

    public DiagnosticResult run(Path backendRoot, Path rawOutput) throws Exception {
        backendRoot = backendRoot.toAbsolutePath().normalize();
        Files.createDirectories(rawOutput);
        var preflight = MatchEngineV9ProductionAcceptanceContract.preflight(
                champions, teams, proficiencies,
                ChampionCompositionProfileCatalog.loadDefault());
        writeJson(rawOutput.resolve("fixed-draft-archetypes.json"), preflight);
        Files.writeString(rawOutput.resolve("player-proficiency-bindings.csv"),
                proficiencyCsv(preflight.bindings()), StandardCharsets.UTF_8);
        writeJson(rawOutput.resolve("acceptance-contract.json"),
                acceptanceContract(backendRoot, preflight));

        ArrayList<RunRow> rows = new ArrayList<>(
                MatchEngineV9ProductionAcceptanceContract.CORE_SIMULATION_COUNT);
        TreeMap<String, ContextAccumulator> contexts = new TreeMap<>();
        int completed = 0;
        for (var scenario : MatchEngineV9ProductionAcceptanceContract.SCENARIOS) {
            for (var orientation : MatchEngineV9ProductionAcceptanceContract.ORIENTATIONS) {
                for (SimulationRuntimeProfileId profile
                        : MatchEngineV9ProductionAcceptanceContract.PROFILES) {
                    for (int seedIndex = 0;
                         seedIndex < MatchEngineV9ProductionAcceptanceContract.SEED_COUNT;
                         seedIndex++) {
                        RunRow row = execute(scenario, orientation, profile, seedIndex,
                                MatchEngineV9ProductionAcceptanceContract.seed(seedIndex),
                                SimulationInstrumentation.enabled());
                        rows.add(row);
                        accumulateContexts(contexts, row);
                        completed++;
                        if (completed % 100 == 0) {
                            System.out.println("production acceptance core simulations=" + completed);
                        }
                    }
                }
            }
        }
        if (rows.size() != MatchEngineV9ProductionAcceptanceContract.CORE_SIMULATION_COUNT) {
            throw new IllegalStateException("Core simulation count mismatch");
        }
        List<PairRow> pairs = pairRows(rows);
        if (pairs.size() != MatchEngineV9ProductionAcceptanceContract.PAIRED_CELL_COUNT) {
            throw new IllegalStateException("Paired cell count mismatch");
        }
        Files.writeString(rawOutput.resolve("fixed-draft-runs.csv"), runsCsv(rows),
                StandardCharsets.UTF_8);
        Files.writeString(rawOutput.resolve("paired-profile-effects.csv"), pairsCsv(pairs),
                StandardCharsets.UTF_8);
        Files.writeString(rawOutput.resolve("composition-context-effects.csv"),
                contextsCsv(contexts), StandardCharsets.UTF_8);

        AdditionalChecks additional = additionalChecks();
        writeJson(rawOutput.resolve("baseline-rollback-oracle.json"), additional.rollbackOracle());
        writeJson(rawOutput.resolve("diagnostic-checks.json"), additional);
        Correctness correctness = correctness(rows, pairs, additional);
        writeJson(rawOutput.resolve("raw-correctness.json"), correctness);
        if (!correctness.clean()) {
            throw new IllegalStateException("MATCH_ENGINE_V9_PRODUCTION_ACCEPTANCE_RAW_BLOCKED");
        }
        return new DiagnosticResult(rows.size(), pairs.size(), ADDITIONAL_SIMULATION_COUNT,
                correctness, preflight.scheduleHash());
    }

    private RunRow execute(
            MatchEngineV9ProductionAcceptanceContract.Scenario scenario,
            MatchEngineV9ProductionAcceptanceContract.Orientation orientation,
            SimulationRuntimeProfileId profile,
            int seedIndex,
            long seed,
            SimulationInstrumentation instrumentation
    ) throws Exception {
        var blueLineup = MatchEngineV9ProductionAcceptanceContract.lineupFor(
                scenario, orientation.blueRole());
        var redLineup = MatchEngineV9ProductionAcceptanceContract.lineupFor(
                scenario, orientation.redRole());
        var execution = Phase13GB1SimulationExecutor.execute(
                simulators,
                teams.assemble(orientation.blueTeamCode()),
                teams.assemble(orientation.redTeamCode()),
                assignments(blueLineup, redLineup), profile, seed,
                orientation.blueTeamCode(), orientation.redTeamCode(),
                MatchEngineV9ProductionAcceptanceContract.DIAGNOSTIC_IDENTITY,
                instrumentation);
        MatchTimeline timeline = execution.timeline();
        MatchSnapshot end = timeline.getSnapshots().getLast();
        var diagnostics = execution.structuredDiagnostics();
        var registered = SimulationRuntimeProfiles.resolve(profile);
        long domainErrors = Phase13GB1RealMatchHarness.IntegrityDiagnostics.from(
                registered.gameplayConfiguration(), diagnostics).errorCount();
        TimelineIntegrity timelineIntegrity = timelineIntegrity(timeline);
        var matchup = diagnostics.championMatchup();
        var composition = diagnostics.composition();
        long orientationErrors = composition.applicationProvenance().stream()
                .filter(MatchEngineV9ProductionAcceptanceRunner::perspectiveMismatch).count();
        long structuredErrors = (long) composition.duplicateApplicationPointCount()
                + composition.multiContextAttemptCount()
                + composition.conflictingPerspectiveCount()
                + composition.duplicatePublicBindingCount()
                + composition.conflictingPublicBindingCount()
                + orientationErrors
                + matchup.duplicateConsumedApplicationErrors()
                + matchup.applicationBindingErrors();
        long directCausal = composition.applicationProvenance().stream()
                .filter(CompositionApplicationProvenance::applicationApplied)
                .filter(value -> !"NOT_BOUND".equals(value.publicBindingStatus())).count();
        long indirectCausal = composition.applicationProvenance().stream()
                .filter(CompositionApplicationProvenance::applicationApplied)
                .filter(value -> "NOT_BOUND".equals(value.publicBindingStatus()))
                .filter(value -> value.existingNonScalarEffectConsumed()
                        && !"NOT_REACHED".equals(value.gameplayConsumerIdentity())).count();
        long unresolvedCausal = composition.applicationProvenance().stream()
                .filter(CompositionApplicationProvenance::applicationApplied)
                .filter(value -> "NOT_BOUND".equals(value.publicBindingStatus()))
                .count() - indirectCausal;
        StructureMetrics structure = structureMetrics(end, execution.endReason());
        String winnerSide = execution.winnerSide() == null ? "NONE" : execution.winnerSide().name();
        String winnerTeam = execution.winnerSide() == TeamSide.BLUE
                ? orientation.blueTeamCode()
                : execution.winnerSide() == TeamSide.RED ? orientation.redTeamCode() : "NONE";
        String winnerRole = execution.winnerSide() == TeamSide.BLUE
                ? orientation.blueRole().name()
                : execution.winnerSide() == TeamSide.RED ? orientation.redRole().name() : "NONE";
        EnumMap<TeamCompositionContext, ContextMetrics> contextMetrics = contextMetrics(
                composition.applicationProvenance());
        long jungleEconomy = (long) diagnostics.jungleEconomy().evaluations()
                + diagnostics.jungleEconomy().eligibleOutcomes()
                + diagnostics.jungleEconomy().awardedCs()
                + diagnostics.jungleEconomy().awardedGold()
                + diagnostics.jungleEconomy().awardedExperience();
        long jungleTempo = diagnostics.jungleTempo().economyUpdates()
                + diagnostics.jungleTempo().continuityResets()
                + diagnostics.jungleTempo().actualConsumptions().values().stream()
                .mapToLong(Integer::longValue).sum();
        return new RunRow(scenario.id(), orientation.id(), profile.name(), seedIndex, seed,
                orientation.blueTeamCode(), orientation.redTeamCode(), blueLineup.id(),
                redLineup.id(), orientation.blueRole() == MatchEngineV9ProductionAcceptanceContract.Role.ARCHETYPE
                ? "BLUE" : "RED", winnerSide, winnerTeam, winnerRole,
                execution.endReason().name(), timeline.getDurationSeconds(),
                objectiveSignature(timeline), structure.progressionSignature(),
                structure.fullStateHash(), structure.nexusEndingSignature(),
                provenance.timelineHash(timeline), execution.randomFingerprint().randomDrawCount(),
                execution.randomFingerprint().randomTraceHash(),
                Phase13GB1SimulationExecutor.structuredDiagnosticsHash(diagnostics),
                registered.configurationHash(), domainErrors,
                timelineIntegrity.invalidStructureState(), timelineIntegrity.nexusOrderingErrors(),
                timelineIntegrity.postFinishMutationOrEvent(),
                timelineIntegrity.supportFarmCsErrors(), structuredErrors,
                matchup.consumedApplicationCount(), matchup.nonZeroConsumedApplicationCount(),
                matchup.directRandomCalls(), composition.resolverEvaluationCount(),
                composition.modifierCalculatedCount(), composition.modifierConsumedCount(),
                composition.gameplayApplicationCount(), composition.nonZeroModifierCount(),
                directCausal, indirectCausal, unresolvedCausal,
                composition.directRandomCallCount(), composition.compositionRandomDrawCount(),
                jungleEconomy, jungleTempo, contextMetrics,
                domainErrors + timelineIntegrity.errorCount() + structuredErrors);
    }

    private AdditionalChecks additionalChecks() throws Exception {
        ArrayList<ReplayCheck> replay = new ArrayList<>();
        ArrayList<InstrumentationCheck> instrumentation = new ArrayList<>();
        List<ExtraFixture> fixtures = List.of(
                new ExtraFixture("POKE_PRODUCTION_REPLAY", "GEN", "T1",
                        MatchEngineV9ProductionAcceptanceContract.POKE,
                        MatchEngineV9ProductionAcceptanceContract.COUNTER, 73L),
                new ExtraFixture("ENGAGE_PRODUCTION_REPLAY", "T1", "GEN",
                        MatchEngineV9ProductionAcceptanceContract.ENGAGE,
                        MatchEngineV9ProductionAcceptanceContract.COUNTER, -73L));
        for (ExtraFixture fixture : fixtures) {
            var first = executeExtra(fixture,
                    SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1,
                    SimulationInstrumentation.enabled());
            var second = executeExtra(fixture,
                    SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1,
                    SimulationInstrumentation.enabled());
            replay.add(new ReplayCheck(fixture.id(), exact(first, second, true),
                    provenance.timelineHash(first.timeline()),
                    provenance.timelineHash(second.timeline()),
                    first.randomFingerprint().randomTraceHash(),
                    second.randomFingerprint().randomTraceHash(),
                    Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                            first.structuredDiagnostics()),
                    Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                            second.structuredDiagnostics())));
            var disabled = executeExtra(fixture,
                    SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1,
                    SimulationInstrumentation.disabled());
            instrumentation.add(new InstrumentationCheck(fixture.id(),
                    timelineAndRandomExact(first, disabled),
                    provenance.timelineHash(first.timeline()),
                    provenance.timelineHash(disabled.timeline()),
                    first.randomFingerprint().randomTraceHash(),
                    disabled.randomFingerprint().randomTraceHash()));
        }
        RollbackOracle rollback = verifyRollbackOracle();
        return new AdditionalChecks(REPLAY_SIMULATION_COUNT,
                INSTRUMENTATION_ONLY_SIMULATION_COUNT, ROLLBACK_SIMULATION_COUNT,
                ADDITIONAL_SIMULATION_COUNT, List.copyOf(replay),
                List.copyOf(instrumentation), rollback,
                replay.stream().allMatch(ReplayCheck::exact)
                        && instrumentation.stream().allMatch(InstrumentationCheck::exact)
                        && rollback.clean());
    }

    /** Small correctness oracle used by both the focused lane and the bounded diagnostic. */
    public RollbackOracle verifyRollbackOracle() throws Exception {
        List<ExtraFixture> fixtures = List.of(
                new ExtraFixture("GEN_BLUE__T1_RED__POKE_COUNTER__SEED_73",
                        "GEN", "T1", MatchEngineV9ProductionAcceptanceContract.POKE,
                        MatchEngineV9ProductionAcceptanceContract.COUNTER, 73L),
                new ExtraFixture("T1_BLUE__GEN_RED__ENGAGE_COUNTER__SEED_NEGATIVE_73",
                        "T1", "GEN", MatchEngineV9ProductionAcceptanceContract.ENGAGE,
                        MatchEngineV9ProductionAcceptanceContract.COUNTER, -73L));
        ArrayList<RollbackFixture> observations = new ArrayList<>();
        for (ExtraFixture fixture : fixtures) {
            var first = executeExtra(fixture, SimulationRuntimeProfileId.BASELINE_V1,
                    SimulationInstrumentation.enabled());
            var second = executeExtra(fixture, SimulationRuntimeProfileId.BASELINE_V1,
                    SimulationInstrumentation.enabled());
            String firstTimelineBytes = sha256(canonical.writeValueAsBytes(first.timeline()));
            String secondTimelineBytes = sha256(canonical.writeValueAsBytes(second.timeline()));
            String firstOutput = outputHash(first);
            String secondOutput = outputHash(second);
            boolean exact = exact(first, second, true)
                    && firstTimelineBytes.equals(secondTimelineBytes)
                    && firstOutput.equals(secondOutput)
                    && first.endReason() == second.endReason()
                    && first.winnerSide() == second.winnerSide();
            observations.add(new RollbackFixture(fixture.id(), fixture.blueTeamCode(),
                    fixture.redTeamCode(), fixture.blueLineup().id(), fixture.redLineup().id(),
                    fixture.seed(), SimulationRuntimeProfileId.BASELINE_V1.name(),
                    SimulationRuntimeProfiles.resolve(SimulationRuntimeProfileId.BASELINE_V1)
                            .configurationHash(), exact, firstTimelineBytes, secondTimelineBytes,
                    provenance.timelineHash(first.timeline()),
                    provenance.timelineHash(second.timeline()), firstOutput, secondOutput,
                    first.randomFingerprint(), second.randomFingerprint(),
                    Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                            first.structuredDiagnostics()),
                    Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                            second.structuredDiagnostics())));
        }
        boolean clean = observations.stream().allMatch(RollbackFixture::exact);
        return new RollbackOracle("MATCH_ENGINE_V9_ACCEPTANCE_TIME_BASELINE_REPLAY_ORACLE_V1",
                "CURRENT_ACCEPTANCE_TREE_EXPLICIT_BASELINE_REPLAY_ONLY",
                "NO_CROSS_COMMIT_BYTE_PARITY_CLAIM_WITHOUT_MATCHING_PRE_ACTIVATION_ORACLE",
                MatchEngineV1Policy.ROLLBACK_MODE, false, List.copyOf(observations), clean);
    }

    private Phase13GB1SimulationExecutor.Execution executeExtra(
            ExtraFixture fixture, SimulationRuntimeProfileId profile,
            SimulationInstrumentation instrumentation
    ) {
        return Phase13GB1SimulationExecutor.execute(simulators,
                teams.assemble(fixture.blueTeamCode()), teams.assemble(fixture.redTeamCode()),
                assignments(fixture.blueLineup(), fixture.redLineup()), profile, fixture.seed(),
                fixture.blueTeamCode(), fixture.redTeamCode(),
                MatchEngineV9ProductionAcceptanceContract.DIAGNOSTIC_IDENTITY, instrumentation);
    }

    private Correctness correctness(List<RunRow> rows, List<PairRow> pairs,
                                    AdditionalChecks additional) {
        long timeouts = rows.stream().filter(value -> value.endReason().equals(
                GameEndReason.SIMULATION_TIMEOUT.name())).count();
        long errors = rows.stream().mapToLong(RunRow::totalIntegrityErrors).sum();
        long matchupRandom = rows.stream().mapToLong(RunRow::matchupDirectRandomCalls).sum();
        long compositionRandom = rows.stream().mapToLong(RunRow::compositionDirectRandomCalls).sum()
                + rows.stream().mapToLong(RunRow::compositionRandomDraws).sum();
        long jungle = rows.stream().mapToLong(value -> value.jungleEconomyNonZero()
                + value.jungleTempoNonZero()).sum();
        long productionApplications = rows.stream()
                .filter(value -> value.profileId().equals(
                        SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1.name()))
                .mapToLong(RunRow::compositionGameplayApplications).sum();
        long productionModifierConsumption = rows.stream()
                .filter(value -> value.profileId().equals(
                        SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1.name()))
                .mapToLong(RunRow::compositionModifierConsumed).sum();
        List<String> missingContexts = Arrays.stream(TeamCompositionContext.values())
                .filter(context -> context == TeamCompositionContext.SKIRMISH
                        || context == TeamCompositionContext.TEAMFIGHT
                        || context == TeamCompositionContext.SIEGE
                        || context == TeamCompositionContext.BASE_DEFENSE)
                .filter(context -> rows.stream().filter(value -> value.profileId().equals(
                                SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1.name()))
                        .map(value -> value.contextMetrics().get(context))
                        .filter(Objects::nonNull).mapToLong(ContextMetrics::modifierConsumed).sum() == 0)
                .map(Enum::name).toList();
        boolean clean = timeouts == 0 && errors == 0 && matchupRandom == 0
                && compositionRandom == 0 && jungle == 0
                && productionApplications > 0 && productionModifierConsumption > 0
                && missingContexts.isEmpty() && additional.clean()
                && pairs.size() == MatchEngineV9ProductionAcceptanceContract.PAIRED_CELL_COUNT;
        return new Correctness(rows.size(), pairs.size(), timeouts, errors,
                matchupRandom, compositionRandom, jungle, productionApplications,
                productionModifierConsumption, missingContexts,
                additional.replayChecks().stream().filter(value -> !value.exact()).count(),
                additional.instrumentationChecks().stream().filter(value -> !value.exact()).count(),
                additional.rollbackOracle().clean(), clean);
    }

    private Map<String, Object> acceptanceContract(
            Path backendRoot, MatchEngineV9ProductionAcceptanceContract.Preflight preflight
    ) throws IOException {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", MatchEngineV9ProductionAcceptanceContract.CONTRACT_SCHEMA);
        value.put("diagnosticIdentity", MatchEngineV9ProductionAcceptanceContract.DIAGNOSTIC_IDENTITY);
        value.put("purposeNamespace", MatchEngineV9ProductionAcceptanceContract.PURPOSE_NAMESPACE);
        value.put("banControl", MatchEngineV9ProductionAcceptanceContract.BAN_CONTROL);
        value.put("reviewedHead", REVIEWED_HEAD);
        value.put("currentHead", currentHead(backendRoot.getParent()));
        value.put("productionSourceTree",
                Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot));
        value.put("resourceProvenance", provenance.resourceProvenance());
        value.put("productionPolicy", MatchEngineV1Policy.authoritative());
        value.put("profiles", MatchEngineV9ProductionAcceptanceContract.PROFILES.stream()
                .map(SimulationRuntimeProfiles::resolve).toList());
        value.put("lineups", List.of(MatchEngineV9ProductionAcceptanceContract.POKE,
                MatchEngineV9ProductionAcceptanceContract.ENGAGE,
                MatchEngineV9ProductionAcceptanceContract.COUNTER));
        value.put("scenarios", MatchEngineV9ProductionAcceptanceContract.SCENARIOS);
        value.put("orientations", MatchEngineV9ProductionAcceptanceContract.ORIENTATIONS);
        value.put("seedFormula", "9270001 + 104729 * i; i=0..49");
        value.put("scheduleHash", preflight.scheduleHash());
        value.put("coreSimulationCount",
                MatchEngineV9ProductionAcceptanceContract.CORE_SIMULATION_COUNT);
        value.put("replaySimulationCount", REPLAY_SIMULATION_COUNT);
        value.put("instrumentationOnlySimulationCount",
                INSTRUMENTATION_ONLY_SIMULATION_COUNT);
        value.put("rollbackSimulationCount", ROLLBACK_SIMULATION_COUNT);
        value.put("additionalUniqueSimulationCount", ADDITIONAL_SIMULATION_COUNT);
        value.put("finalizerSimulationCount", 0);
        value.put("statisticalHoldout", false);
        value.put("balanceApproval", false);
        value.put("automaticFallback", false);
        return value;
    }

    private static MatchChampionAssignments assignments(
            MatchEngineV9ProductionAcceptanceContract.Lineup blue,
            MatchEngineV9ProductionAcceptanceContract.Lineup red
    ) {
        ArrayList<ChampionAssignment> values = new ArrayList<>(10);
        for (Position position : Position.values()) {
            values.add(new ChampionAssignment(new PlayerKey(TeamSide.BLUE, position),
                    blue.roles().get(position).championId(), position));
            values.add(new ChampionAssignment(new PlayerKey(TeamSide.RED, position),
                    red.roles().get(position).championId(), position));
        }
        return new MatchChampionAssignments(values, ChampionSelectionMode.EXPLICIT);
    }

    private StructureMetrics structureMetrics(MatchSnapshot end, GameEndReason endReason)
            throws Exception {
        String progression = end.getBlueTowersDestroyed() + ":"
                + end.getRedTowersDestroyed() + ":"
                + end.getBlueInhibitorsRemaining() + ":"
                + end.getRedInhibitorsRemaining() + ":"
                + end.getBlueNexusTurretsRemaining() + ":"
                + end.getRedNexusTurretsRemaining();
        String nexus = end.isBlueNexusAlive() + ":" + end.isRedNexusAlive() + ":"
                + end.getBlueNexusTurretsRemaining() + ":"
                + end.getRedNexusTurretsRemaining() + ":" + endReason.name();
        return new StructureMetrics(progression,
                sha256(canonical.writeValueAsBytes(end.getStructureState())), nexus);
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
        return MatchEngineV9ProductionAcceptanceContract.sha256(value.toString());
    }

    private static TimelineIntegrity timelineIntegrity(MatchTimeline timeline) {
        long invalid = 0;
        long nexus = 0;
        long post = 0;
        long support = 0;
        for (MatchSnapshot snapshot : timeline.getSnapshots()) {
            for (var team : snapshot.getStructureState().teams().values()) {
                for (var lane : team.lanes().values()) {
                    invalid += invalidHealth(lane.outerTower()) + invalidHealth(lane.innerTower())
                            + invalidHealth(lane.inhibitorTower()) + invalidHealth(lane.inhibitor());
                }
                for (double current : team.nexusTurretCurrentHealth()) {
                    invalid += invalidHealth(current, team.nexusTurretMaxHealth());
                }
                invalid += invalidHealth(team.nexusCurrentHealth(), team.nexusMaxHealth());
                if (!team.nexusAlive() && team.nexusTurretsRemaining() > 0) nexus++;
            }
            support += snapshot.getPlayerSnapshots().stream()
                    .filter(player -> player.getPosition() == Position.SUPPORT && player.getCs() != 0)
                    .count();
        }
        post += timeline.getEvents().stream()
                .filter(event -> event.getTimeSeconds() > timeline.getDurationSeconds()).count();
        return new TimelineIntegrity(invalid, nexus, post, support);
    }

    private static int invalidHealth(StructureStateSnapshot.Health value) {
        return invalidHealth(value.current(), value.maximum());
    }

    private static int invalidHealth(double current, double maximum) {
        return !Double.isFinite(current) || !Double.isFinite(maximum) || maximum < 0.0
                || current < -1.0e-9 || current > maximum + 1.0e-9 ? 1 : 0;
    }

    private static boolean perspectiveMismatch(CompositionApplicationProvenance value) {
        if (value.context() == TeamCompositionContext.OBJECTIVE_SETUP) {
            return value.routingPerspectiveSide() != value.attemptOwnerSide()
                    || value.scoreOrientation() != CompositionScoreOrientation.BLUE_MINUS_RED
                    || value.perspectiveSide() != TeamSide.BLUE;
        }
        return value.applicationApplied()
                && value.scoreOrientation() != CompositionScoreOrientation.BLUE_MINUS_RED;
    }

    private static EnumMap<TeamCompositionContext, ContextMetrics> contextMetrics(
            List<CompositionApplicationProvenance> applications
    ) {
        EnumMap<TeamCompositionContext, ContextMetrics> result =
                new EnumMap<>(TeamCompositionContext.class);
        for (TeamCompositionContext context : TeamCompositionContext.values()) {
            List<CompositionApplicationProvenance> values = applications.stream()
                    .filter(value -> value.context() == context).toList();
            List<Double> modifiers = values.stream()
                    .filter(CompositionApplicationProvenance::modifierConsumed)
                    .map(CompositionApplicationProvenance::modifier).toList();
            result.put(context, new ContextMetrics(values.size(),
                    values.stream().filter(CompositionApplicationProvenance::applicationApplied)
                            .count(), modifiers.size(),
                    modifiers.stream().mapToDouble(Double::doubleValue).sum(),
                    modifiers.stream().mapToDouble(Double::doubleValue).min().orElse(0.0),
                    modifiers.stream().mapToDouble(Double::doubleValue).max().orElse(0.0)));
        }
        return result;
    }

    private static void accumulateContexts(TreeMap<String, ContextAccumulator> aggregate,
                                           RunRow row) {
        for (TeamCompositionContext context : TeamCompositionContext.values()) {
            String key = row.scenarioId() + "|" + row.orientationId() + "|"
                    + row.profileId() + "|" + context.name();
            aggregate.computeIfAbsent(key, ignored -> new ContextAccumulator(
                    row.scenarioId(), row.orientationId(), row.profileId(), context.name()))
                    .add(row.contextMetrics().get(context));
        }
    }

    private static List<PairRow> pairRows(List<RunRow> rows) {
        Map<String, List<RunRow>> grouped = rows.stream().collect(Collectors.groupingBy(
                value -> value.scenarioId() + "|" + value.orientationId() + "|"
                        + value.seedIndex(), TreeMap::new, Collectors.toList()));
        ArrayList<PairRow> result = new ArrayList<>(grouped.size());
        for (List<RunRow> group : grouped.values()) {
            RunRow baseline = profile(group, SimulationRuntimeProfileId.BASELINE_V1);
            RunRow matchup = profile(group, SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
            RunRow production = profile(group,
                    SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1);
            result.add(new PairRow(baseline.scenarioId(), baseline.orientationId(),
                    baseline.seedIndex(), baseline.seed(), baseline.winnerRole(),
                    matchup.winnerRole(), production.winnerRole(),
                    transition(baseline, matchup), transition(matchup, production),
                    transition(baseline, production),
                    matchup.durationSeconds() - baseline.durationSeconds(),
                    production.durationSeconds() - matchup.durationSeconds(),
                    production.durationSeconds() - baseline.durationSeconds(),
                    !baseline.objectiveSignature().equals(matchup.objectiveSignature()),
                    !matchup.objectiveSignature().equals(production.objectiveSignature()),
                    !baseline.objectiveSignature().equals(production.objectiveSignature()),
                    !baseline.structureStateHash().equals(matchup.structureStateHash()),
                    !matchup.structureStateHash().equals(production.structureStateHash()),
                    !baseline.structureStateHash().equals(production.structureStateHash()),
                    !baseline.nexusEndingSignature().equals(matchup.nexusEndingSignature()),
                    !matchup.nexusEndingSignature().equals(production.nexusEndingSignature()),
                    !baseline.nexusEndingSignature().equals(production.nexusEndingSignature()),
                    production.compositionGameplayApplications() > 0));
        }
        result.sort(Comparator.comparing(PairRow::scenarioId)
                .thenComparing(PairRow::orientationId).thenComparingInt(PairRow::seedIndex));
        return List.copyOf(result);
    }

    private static RunRow profile(List<RunRow> rows, SimulationRuntimeProfileId profile) {
        return rows.stream().filter(value -> value.profileId().equals(profile.name()))
                .findFirst().orElseThrow();
    }

    private static String transition(RunRow before, RunRow after) {
        return before.winnerRole() + "_TO_" + after.winnerRole();
    }

    private boolean exact(Phase13GB1SimulationExecutor.Execution first,
                          Phase13GB1SimulationExecutor.Execution second,
                          boolean diagnostics) throws Exception {
        return timelineAndRandomExact(first, second)
                && (!diagnostics || Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                first.structuredDiagnostics()).equals(
                Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                        second.structuredDiagnostics())));
    }

    private boolean timelineAndRandomExact(Phase13GB1SimulationExecutor.Execution first,
                                           Phase13GB1SimulationExecutor.Execution second)
            throws Exception {
        return Arrays.equals(canonical.writeValueAsBytes(first.timeline()),
                canonical.writeValueAsBytes(second.timeline()))
                && first.endReason() == second.endReason()
                && first.winnerSide() == second.winnerSide()
                && first.randomFingerprint().equals(second.randomFingerprint());
    }

    private String outputHash(Phase13GB1SimulationExecutor.Execution execution) throws Exception {
        return sha256(canonical.writeValueAsBytes(Map.of(
                "timeline", execution.timeline(),
                "endReason", execution.endReason(),
                "winnerSide", execution.winnerSide(),
                "random", execution.randomFingerprint(),
                "diagnosticsHash", Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                        execution.structuredDiagnostics()))));
    }

    private void writeJson(Path target, Object value) throws IOException {
        Files.write(target, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        Files.writeString(target, "\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
    }

    private static String currentHead(Path repositoryRoot) throws IOException {
        Path head = repositoryRoot.resolve(".git").resolve("HEAD");
        String value = Files.readString(head, StandardCharsets.UTF_8).trim();
        if (!value.startsWith("ref: ")) return value;
        Path ref = repositoryRoot.resolve(".git").resolve(value.substring(5));
        if (Files.isRegularFile(ref)) return Files.readString(ref, StandardCharsets.UTF_8).trim();
        return "UNRESOLVED_" + value.substring(5);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String proficiencyCsv(
            List<MatchEngineV9ProductionAcceptanceContract.ProficiencyBinding> values
    ) {
        StringBuilder out = new StringBuilder(
                "teamCode,position,playerId,lineupId,championId,proficiency,authored\n");
        values.forEach(value -> out.append(value.teamCode()).append(',')
                .append(value.position()).append(',').append(value.playerId()).append(',')
                .append(value.lineupId()).append(',').append(value.championId()).append(',')
                .append(value.proficiency()).append(',').append(value.authored()).append('\n'));
        return out.toString();
    }

    private static String runsCsv(List<RunRow> values) {
        StringBuilder out = new StringBuilder("scenarioId,orientationId,profileId,seedIndex,seed,"
                + "blueTeamCode,redTeamCode,blueLineupId,redLineupId,archetypeSide,winnerSide,"
                + "winnerTeamCode,winnerRole,endReason,durationSeconds,objectiveSignature,"
                + "structureProgressionSignature,structureStateHash,nexusEndingSignature,"
                + "timelineHash,randomDrawCount,randomTraceHash,structuredDiagnosticsHash,"
                + "configurationHash,domainIntegrityErrors,invalidStructureState,nexusOrderingErrors,"
                + "postFinishMutationOrEvent,supportFarmCsErrors,structuredApplicationErrors,"
                + "matchupApplications,matchupNonZeroApplications,matchupDirectRandomCalls,"
                + "compositionEvaluations,compositionModifierCalculated,compositionModifierConsumed,"
                + "compositionGameplayApplications,compositionNonZeroModifiers,causalDirectBindings,"
                + "causalIndirectBindings,causalUnresolvedBindings,compositionDirectRandomCalls,"
                + "compositionRandomDraws,jungleEconomyNonZero,jungleTempoNonZero,totalIntegrityErrors\n");
        for (RunRow value : values) out.append(value.scalarCsv()).append('\n');
        return out.toString();
    }

    private static String pairsCsv(List<PairRow> values) {
        StringBuilder out = new StringBuilder("scenarioId,orientationId,seedIndex,seed,"
                + "baselineWinnerRole,matchupWinnerRole,productionWinnerRole,"
                + "baselineToMatchupTransition,matchupToProductionTransition,baselineToProductionTransition,"
                + "matchupDurationDelta,compositionDurationDelta,productDurationDelta,"
                + "matchupObjectiveChanged,compositionObjectiveChanged,productObjectiveChanged,"
                + "matchupStructureChanged,compositionStructureChanged,productStructureChanged,"
                + "matchupNexusEndingChanged,compositionNexusEndingChanged,productNexusEndingChanged,"
                + "productionCompositionReachable\n");
        values.forEach(value -> out.append(value.csv()).append('\n'));
        return out.toString();
    }

    private static String contextsCsv(TreeMap<String, ContextAccumulator> values) {
        StringBuilder out = new StringBuilder("scenarioId,orientationId,profileId,context,games,"
                + "observationCount,applicationCount,modifierConsumedCount,signedModifierMean,"
                + "signedModifierMin,signedModifierMax\n");
        values.values().forEach(value -> out.append(value.csv()).append('\n'));
        return out.toString();
    }

    private record StructureMetrics(String progressionSignature, String fullStateHash,
                                    String nexusEndingSignature) { }

    private record TimelineIntegrity(long invalidStructureState, long nexusOrderingErrors,
                                     long postFinishMutationOrEvent,
                                     long supportFarmCsErrors) {
        long errorCount() {
            return invalidStructureState + nexusOrderingErrors + postFinishMutationOrEvent
                    + supportFarmCsErrors;
        }
    }

    public record ContextMetrics(long observations, long applications, long modifierConsumed,
                                 double modifierSum, double modifierMin, double modifierMax) { }

    public record RunRow(
            String scenarioId, String orientationId, String profileId, int seedIndex, long seed,
            String blueTeamCode, String redTeamCode, String blueLineupId, String redLineupId,
            String archetypeSide, String winnerSide, String winnerTeamCode, String winnerRole,
            String endReason, int durationSeconds, String objectiveSignature,
            String structureProgressionSignature, String structureStateHash,
            String nexusEndingSignature, String timelineHash, long randomDrawCount,
            String randomTraceHash, String structuredDiagnosticsHash, String configurationHash,
            long domainIntegrityErrors, long invalidStructureState, long nexusOrderingErrors,
            long postFinishMutationOrEvent, long supportFarmCsErrors,
            long structuredApplicationErrors, long matchupApplications,
            long matchupNonZeroApplications, long matchupDirectRandomCalls,
            long compositionEvaluations, long compositionModifierCalculated,
            long compositionModifierConsumed, long compositionGameplayApplications,
            long compositionNonZeroModifiers, long causalDirectBindings,
            long causalIndirectBindings, long causalUnresolvedBindings,
            long compositionDirectRandomCalls, long compositionRandomDraws,
            long jungleEconomyNonZero, long jungleTempoNonZero,
            Map<TeamCompositionContext, ContextMetrics> contextMetrics,
            long totalIntegrityErrors
    ) {
        public RunRow {
            contextMetrics = Map.copyOf(contextMetrics);
        }

        String scalarCsv() {
            return String.join(",", scenarioId, orientationId, profileId,
                    Integer.toString(seedIndex), Long.toString(seed), blueTeamCode, redTeamCode,
                    blueLineupId, redLineupId, archetypeSide, winnerSide, winnerTeamCode,
                    winnerRole, endReason, Integer.toString(durationSeconds), objectiveSignature,
                    structureProgressionSignature, structureStateHash, nexusEndingSignature,
                    timelineHash, Long.toString(randomDrawCount), randomTraceHash,
                    structuredDiagnosticsHash, configurationHash,
                    Long.toString(domainIntegrityErrors), Long.toString(invalidStructureState),
                    Long.toString(nexusOrderingErrors), Long.toString(postFinishMutationOrEvent),
                    Long.toString(supportFarmCsErrors), Long.toString(structuredApplicationErrors),
                    Long.toString(matchupApplications), Long.toString(matchupNonZeroApplications),
                    Long.toString(matchupDirectRandomCalls), Long.toString(compositionEvaluations),
                    Long.toString(compositionModifierCalculated),
                    Long.toString(compositionModifierConsumed),
                    Long.toString(compositionGameplayApplications),
                    Long.toString(compositionNonZeroModifiers), Long.toString(causalDirectBindings),
                    Long.toString(causalIndirectBindings), Long.toString(causalUnresolvedBindings),
                    Long.toString(compositionDirectRandomCalls),
                    Long.toString(compositionRandomDraws), Long.toString(jungleEconomyNonZero),
                    Long.toString(jungleTempoNonZero), Long.toString(totalIntegrityErrors));
        }
    }

    public record PairRow(
            String scenarioId, String orientationId, int seedIndex, long seed,
            String baselineWinnerRole, String matchupWinnerRole, String productionWinnerRole,
            String baselineToMatchupTransition, String matchupToProductionTransition,
            String baselineToProductionTransition, int matchupDurationDelta,
            int compositionDurationDelta, int productDurationDelta,
            boolean matchupObjectiveChanged, boolean compositionObjectiveChanged,
            boolean productObjectiveChanged, boolean matchupStructureChanged,
            boolean compositionStructureChanged, boolean productStructureChanged,
            boolean matchupNexusEndingChanged, boolean compositionNexusEndingChanged,
            boolean productNexusEndingChanged, boolean productionCompositionReachable
    ) {
        String csv() {
            return String.join(",", scenarioId, orientationId, Integer.toString(seedIndex),
                    Long.toString(seed), baselineWinnerRole, matchupWinnerRole, productionWinnerRole,
                    baselineToMatchupTransition, matchupToProductionTransition,
                    baselineToProductionTransition, Integer.toString(matchupDurationDelta),
                    Integer.toString(compositionDurationDelta), Integer.toString(productDurationDelta),
                    Boolean.toString(matchupObjectiveChanged),
                    Boolean.toString(compositionObjectiveChanged),
                    Boolean.toString(productObjectiveChanged),
                    Boolean.toString(matchupStructureChanged),
                    Boolean.toString(compositionStructureChanged),
                    Boolean.toString(productStructureChanged),
                    Boolean.toString(matchupNexusEndingChanged),
                    Boolean.toString(compositionNexusEndingChanged),
                    Boolean.toString(productNexusEndingChanged),
                    Boolean.toString(productionCompositionReachable));
        }
    }

    private static final class ContextAccumulator {
        private final String scenario;
        private final String orientation;
        private final String profile;
        private final String context;
        private long games;
        private long observations;
        private long applications;
        private long consumed;
        private double sum;
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;

        ContextAccumulator(String scenario, String orientation, String profile, String context) {
            this.scenario = scenario;
            this.orientation = orientation;
            this.profile = profile;
            this.context = context;
        }

        void add(ContextMetrics value) {
            games++;
            observations += value.observations();
            applications += value.applications();
            consumed += value.modifierConsumed();
            sum += value.modifierSum();
            if (value.modifierConsumed() > 0) {
                min = Math.min(min, value.modifierMin());
                max = Math.max(max, value.modifierMax());
            }
        }

        String csv() {
            return String.join(",", scenario, orientation, profile, context,
                    Long.toString(games), Long.toString(observations),
                    Long.toString(applications), Long.toString(consumed),
                    Double.toString(consumed == 0 ? 0.0 : sum / consumed),
                    Double.toString(consumed == 0 ? 0.0 : min),
                    Double.toString(consumed == 0 ? 0.0 : max));
        }
    }

    private record ExtraFixture(String id, String blueTeamCode, String redTeamCode,
                                MatchEngineV9ProductionAcceptanceContract.Lineup blueLineup,
                                MatchEngineV9ProductionAcceptanceContract.Lineup redLineup,
                                long seed) { }

    public record ReplayCheck(String fixtureId, boolean exact, String firstTimelineHash,
                              String secondTimelineHash, String firstRandomHash,
                              String secondRandomHash, String firstDiagnosticsHash,
                              String secondDiagnosticsHash) { }

    public record InstrumentationCheck(String fixtureId, boolean exact,
                                       String enabledTimelineHash, String disabledTimelineHash,
                                       String enabledRandomHash, String disabledRandomHash) { }

    public record RollbackFixture(String fixtureId, String blueTeamCode, String redTeamCode,
                                  String blueLineupId, String redLineupId, long seed,
                                  String profileId, String configurationHash, boolean exact,
                                  String firstFullTimelineByteHash,
                                  String secondFullTimelineByteHash,
                                  String firstTimelineHash, String secondTimelineHash,
                                  String firstOutputHash, String secondOutputHash,
                                  Object firstRandomFingerprint, Object secondRandomFingerprint,
                                  String firstStructuredDiagnosticsHash,
                                  String secondStructuredDiagnosticsHash) { }

    public record RollbackOracle(String schemaVersion, String evidenceScope,
                                 String crossCommitClaim, String rollbackMode,
                                 boolean automaticFallback, List<RollbackFixture> fixtures,
                                 boolean clean) {
        public RollbackOracle { fixtures = List.copyOf(fixtures); }
    }

    public record AdditionalChecks(int replaySimulationCount,
                                   int instrumentationOnlySimulationCount,
                                   int rollbackSimulationCount,
                                   int additionalUniqueSimulationCount,
                                   List<ReplayCheck> replayChecks,
                                   List<InstrumentationCheck> instrumentationChecks,
                                   RollbackOracle rollbackOracle, boolean clean) {
        public AdditionalChecks {
            replayChecks = List.copyOf(replayChecks);
            instrumentationChecks = List.copyOf(instrumentationChecks);
        }
    }

    public record Correctness(int coreSimulationCount, int pairedCellCount, long timeoutCount,
                              long totalIntegrityErrors, long matchupDirectRandomCalls,
                              long compositionRandomCallsOrDraws, long jungleNonZeroCount,
                              long productionCompositionApplications,
                              long productionModifierConsumptions,
                              List<String> missingEligibleContexts, long replayMismatchCount,
                              long instrumentationMismatchCount, boolean rollbackOracleClean,
                              boolean clean) {
        public Correctness { missingEligibleContexts = List.copyOf(missingEligibleContexts); }
    }

    public record DiagnosticResult(int coreSimulationCount, int pairedCellCount,
                                   int additionalSimulationCount, Correctness correctness,
                                   String scheduleHash) { }
}
