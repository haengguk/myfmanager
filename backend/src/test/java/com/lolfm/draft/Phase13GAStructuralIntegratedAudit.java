package com.lolfm.draft;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionDefinition;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.ChampionResourceSet;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.composition.ChampionCompositionProfile;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.factory.DummyDataFactory;
import com.lolfm.simulator.EndGameEvaluator;
import com.lolfm.simulator.MatchSimulator;
import com.lolfm.simulator.ObjectiveAttemptResolver;
import com.lolfm.simulator.ObjectiveResolver;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.PostFightResolver;
import com.lolfm.simulator.ProgressionCombatContext;
import com.lolfm.simulator.PushResolver;
import com.lolfm.simulator.SimulationOptions;
import com.lolfm.simulator.SnapshotFactory;
import com.lolfm.simulator.StructureResolver;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.TeamfightResolver;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Full Phase 13G-A audit runner.  This class is test-side by design: it
 * observes the frozen production path and does not change draft semantics.
 */
public final class Phase13GAStructuralIntegratedAudit {
    public static final String PHASE = "PHASE_13G_A_STRUCTURAL_INTEGRATED_AUDIT";
    public static final String OUTPUT_DIRECTORY = "build/reports/phase13g-a";
    public static final String DRAFT_META_HASH =
            "dd1173aadfad92d4ec231f097653ac840809c60812a4920d32b3d9606fa7fe99";
    public static final String LEGAL_ROLE_HASH =
            "18036bba3ec815a732d251e82cdc72d7d6dbed0f9fc3b373b2840da936b72b8e";
    public static final String COMPOSITION_HASH =
            "23d616cab6abea69d5ad783f405b0b4518a14608b0be4eac3d53f669acab6877";
    public static final int GAME_ONE_REPLAY_CASES = 24;
    public static final int SERIES_REPLAY_CASES = 3;
    public static final List<Long> INTEGRATION_SEEDS = List.of(1301L, 1302L);
    private static final List<String> PICK_COMPONENTS = List.of(
            "META_PRIORITY", "PLAYER_FIT", "MATCHUP", "COMPOSITION_FIT",
            "COMPOSITION_RESPONSE", "FLEXIBILITY", "DENIAL", "FUTURE_FEASIBILITY");
    private static final List<String> BAN_COMPONENTS = List.of(
            "OPPONENT_EXPECTED_PICK_VALUE", "THREAT_TO_OUR_PLAN_PORTFOLIO", "META_PRIORITY",
            "OPPONENT_FLEX_VALUE", "ROLE_POOL_COMPRESSION", "PROTECTION_VALUE",
            "OUR_LOST_PICK_OPPORTUNITY");

    private final DraftResourceSet resources;
    private final Map<String, Phase13GASyntheticContextFactory.SyntheticContext> contexts;
    private final Phase13GAAuditSchedule.Schedule schedule;
    private final DraftEngine engine;
    private final RoleAssignmentSolver roles;
    private final DraftAvailability availability;
    private final DraftCompositionEvaluator composition;
    private final DraftMatchupEvaluator matchup;
    private final PreDraftPlanner planner;
    private final DraftCandidateGenerator candidates;
    private final DraftScoringPolicy policy;
    private final ObjectMapper mapper;

    public Phase13GAStructuralIntegratedAudit() {
        resources = DraftResourceSet.loadDefault();
        List<Phase13GASyntheticContextFactory.SyntheticContext> values =
                Phase13GASyntheticContextFactory.create(resources);
        contexts = values.stream().collect(Collectors.toUnmodifiableMap(
                Phase13GASyntheticContextFactory.SyntheticContext::id, value -> value));
        schedule = Phase13GAAuditSchedule.freeze(values);
        policy = DraftScoringPolicy.standard();
        roles = new RoleAssignmentSolver(resources.champions().catalog());
        availability = new DraftAvailability(resources.champions().catalog(), roles);
        composition = new DraftCompositionEvaluator(resources.champions().catalog(),
                resources.champions().composition(), roles);
        matchup = new DraftMatchupEvaluator(roles, resources.champions().matchup());
        planner = new PreDraftPlanner(resources.champions().catalog(), resources.meta(),
                resources.champions().composition(), roles);
        candidates = new DraftCandidateGenerator(resources.champions().catalog(), resources.meta(),
                roles, composition, availability, policy);
        engine = new DraftEngine(resources);
        mapper = new ObjectMapper()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public static void main(String[] args) throws Exception {
        Phase13GAStructuralIntegratedAudit audit = new Phase13GAStructuralIntegratedAudit();
        AuditRun result = audit.run();
        Path output = Path.of(System.getProperty("phase13g.outputDir", OUTPUT_DIRECTORY));
        Phase13GAAuditArtifactWriter.write(result, output);
        System.out.println("PHASE13G_A_VERDICT=" + result.summary().get("verdict"));
        System.out.println("PHASE13G_A_GAME1_CASES=" + result.gameOneDrafts().size());
        System.out.println("PHASE13G_A_FEARLESS_SERIES=" + result.fearlessSeries().size());
        System.out.println("PHASE13G_A_INTEGRATION_MATCHES=" + result.integrations().size());
        System.out.println("PHASE13G_A_REVIEWS=" + result.reviewCodes());
        System.out.println("PHASE13G_A_BLOCKERS=" + result.blockerCodes());
    }

    public AuditRun run() {
        Instant started = Instant.now();
        StaticIntegrity staticIntegrity = inspectStaticIntegrity();
        List<DraftAudit> gameOne = runGameOneSchedule();
        List<FearlessSeriesAudit> fearless = runFearlessSchedule();
        int draftReplayMismatches = replayGameOne(gameOne);
        int seriesReplayMismatches = replaySeries(fearless);
        GameOneDistribution distribution = aggregateGameOne(gameOne);
        List<ComponentDistribution> componentDistribution = componentDistribution(gameOne);
        ControlledProbeResult probes = controlledProbes(distribution, componentDistribution);
        List<IntegrationAudit> integrations = runIntegrationProbes(gameOne, fearless);
        int matchReplayMismatches = (int) integrations.stream().filter(value -> value.replayMismatch()).count();

        List<String> blockers = new ArrayList<>(staticIntegrity.blockers());
        if (gameOne.stream().anyMatch(value -> !value.success())) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_DRAFT_LEGALITY");
        }
        if (fearless.stream().anyMatch(value -> !value.complete())) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_HARD_FEARLESS_COMPLETION");
        }
        if (draftReplayMismatches + seriesReplayMismatches > 0) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_DETERMINISM");
        }
        if (integrations.stream().anyMatch(value -> !value.success())) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_MATCH_INTEGRATION");
        }
        long nonFinite = componentDistribution.stream().mapToLong(ComponentDistribution::nonFiniteCount).sum();
        if (nonFinite > 0) blockers.add("BLOCKED_BY_PHASE_13G_A_NONFINITE_SCORE");
        if (integrations.stream().anyMatch(value -> value.replayMismatch())) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_MATCH_INTEGRATION");
        }

        List<String> reviews = reviewCodes(distribution, componentDistribution, probes,
                gameOne, fearless, integrations);
        List<String> uniqueBlockers = blockers.stream().distinct().sorted().toList();
        List<String> uniqueReviews = reviews.stream().distinct().sorted().toList();
        Map<String, Object> summary = summary(staticIntegrity, distribution, componentDistribution,
                probes, gameOne, fearless, integrations, draftReplayMismatches,
                seriesReplayMismatches, matchReplayMismatches, uniqueReviews, uniqueBlockers,
                Duration.between(started, Instant.now()).toMillis());
        return new AuditRun(resources, contexts.values().stream()
                .sorted(Comparator.comparing(Phase13GASyntheticContextFactory.SyntheticContext::id)).toList(),
                schedule, staticIntegrity.values(), gameOne, fearless, distribution,
                componentDistribution, probes.values(), integrations, uniqueReviews,
                uniqueBlockers, summary);
    }

    public record AuditRun(
            DraftResourceSet resources,
            List<Phase13GASyntheticContextFactory.SyntheticContext> syntheticContexts,
            Phase13GAAuditSchedule.Schedule schedule,
            Map<String, Object> staticIntegrity,
            List<DraftAudit> gameOneDrafts,
            List<FearlessSeriesAudit> fearlessSeries,
            GameOneDistribution gameOneDistribution,
            List<ComponentDistribution> componentDistribution,
            Map<String, Object> controlledProbes,
            List<IntegrationAudit> integrations,
            List<String> reviewCodes,
            List<String> blockerCodes,
            Map<String, Object> summary
    ) {
        public AuditRun {
            syntheticContexts = List.copyOf(syntheticContexts);
            gameOneDrafts = List.copyOf(gameOneDrafts);
            fearlessSeries = List.copyOf(fearlessSeries);
            componentDistribution = List.copyOf(componentDistribution);
            integrations = List.copyOf(integrations);
            reviewCodes = List.copyOf(reviewCodes);
            blockerCodes = List.copyOf(blockerCodes);
        }
    }

    public record CandidateTrace(
            int turn,
            String side,
            String actionType,
            List<String> candidates,
            String portfolio,
            String selected,
            String componentValues,
            String preferredPlan
    ) {
        public CandidateTrace {
            candidates = List.copyOf(candidates);
        }
    }

    public record DraftAudit(
            String caseId,
            String blueContextId,
            String redContextId,
            FinalDraftResult result,
            List<CandidateTrace> candidateTrace,
            List<String> violations,
            long latencyMillis,
            String digest,
            Map<String, Integer> preferredPlans,
            Map<String, Integer> secondaryPlans,
            Map<String, Integer> fallbackPlans,
            Map<String, Integer> pivotByCause
    ) {
        public DraftAudit {
            candidateTrace = List.copyOf(candidateTrace);
            violations = List.copyOf(violations);
            preferredPlans = Map.copyOf(preferredPlans);
            secondaryPlans = Map.copyOf(secondaryPlans);
            fallbackPlans = Map.copyOf(fallbackPlans);
            pivotByCause = Map.copyOf(pivotByCause);
        }
        public boolean success() { return result != null && violations.isEmpty(); }
    }

    public record FearlessSeriesAudit(
            String seriesId,
            String blueContextId,
            String redContextId,
            List<DraftAudit> games,
            boolean complete,
            int reuseCount,
            int banConsumptionViolations,
            String digest
    ) {
        public FearlessSeriesAudit { games = List.copyOf(games); }
    }

    public record GameOneDistribution(
            Map<String, Integer> pickOccurrences,
            Map<String, Integer> banOccurrences,
            Map<String, Integer> pickOrBanPresence,
            Map<String, Integer> roleAssignmentOccurrences,
            Map<String, Map<String, Integer>> flexRoleDistribution,
            Map<String, Object> metrics
    ) {
        public GameOneDistribution {
            pickOccurrences = Map.copyOf(pickOccurrences);
            banOccurrences = Map.copyOf(banOccurrences);
            pickOrBanPresence = Map.copyOf(pickOrBanPresence);
            roleAssignmentOccurrences = Map.copyOf(roleAssignmentOccurrences);
            flexRoleDistribution = Map.copyOf(flexRoleDistribution);
            metrics = Map.copyOf(metrics);
        }
    }

    public record ComponentDistribution(
            String actionType,
            String component,
            int sampleCount,
            double min,
            double max,
            double mean,
            double median,
            double p10,
            double p90,
            double zeroRate,
            double positiveRate,
            long nonFiniteCount
    ) { }

    public record IntegrationAudit(
            String draftId,
            String contextPair,
            long seed,
            boolean success,
            boolean replayMismatch,
            int durationSeconds,
            String winner,
            String timelineDigest,
            List<String> violations
    ) {
        public IntegrationAudit { violations = List.copyOf(violations); }
    }

    private record StaticIntegrity(Map<String, Object> values, List<String> blockers) { }

    private record Validation(
            List<CandidateTrace> trace,
            List<String> violations,
            Map<String, Integer> preferredPlans,
            Map<String, Integer> secondaryPlans,
            Map<String, Integer> fallbackPlans,
            Map<String, Integer> pivotByCause
    ) { }

    private record ControlledProbeResult(Map<String, Object> values, boolean protectionPositive,
                                         boolean planPivot, boolean flexLegal, boolean activationPass) { }

    private StaticIntegrity inspectStaticIntegrity() {
        var catalog = resources.champions().catalog();
        Set<ChampionId> ids = catalog.all().stream().map(ChampionDefinition::id).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<ChampionRoleKey> legal = catalog.legalRoleKeys();
        Map<String, Object> values = new TreeMap<>();
        List<String> blockers = new ArrayList<>();
        Map<String, Integer> roleCounts = new TreeMap<>();
        for (Position position : Position.values()) roleCounts.put(position.name(), catalog.forPosition(position).size());
        Map<String, Integer> expectedRoles = Map.of("TOP", 54, "JUNGLE", 51, "MID", 45, "ADC", 31, "SUPPORT", 35);
        if (catalog.all().size() != 173 || ids.size() != 173 || legal.size() != 216
                || !roleCounts.equals(expectedRoles)) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_RESOURCE_INTEGRITY");
        }
        Set<ChampionRoleKey> matchupKeys = resources.champions().matchup().profiles().keySet();
        Set<ChampionRoleKey> compositionKeys = resources.champions().composition().profiles().keySet();
        Set<ChampionId> powerIds = resources.champions().power().all().stream()
                .map(value -> value.championId()).collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        for (ChampionRoleKey key : legal) {
            if (!catalog.supports(key) || !powerIds.contains(key.championId())
                    || !matchupKeys.contains(key) || !compositionKeys.contains(key)) {
                missing.add(key.stableId());
            }
        }
        List<String> orphan = new ArrayList<>();
        orphan.addAll(matchupKeys.stream().filter(key -> !legal.contains(key)).map(ChampionRoleKey::stableId).toList());
        orphan.addAll(compositionKeys.stream().filter(key -> !legal.contains(key)).map(ChampionRoleKey::stableId).toList());
        orphan.addAll(powerIds.stream().filter(id -> !ids.contains(id)).map(ChampionId::value).toList());
        String metaHash = resourceHash(DraftMetaCatalog.RESOURCE);
        String legalHash = resources.meta().actualLegalRoleKeyHash();
        String compositionHash = resources.champions().composition().profileHash();
        if (!DRAFT_META_HASH.equals(metaHash) || !LEGAL_ROLE_HASH.equals(legalHash)
                || !COMPOSITION_HASH.equals(compositionHash)) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_RESOURCE_INTEGRITY");
        }
        if (!catalog.supports(key("varus", Position.TOP))
                || !catalog.supports(key("anivia", Position.TOP))
                || !catalog.supports(key("cassiopeia", Position.ADC))
                || !catalog.supports(key("taliyah", Position.ADC))
                || catalog.supports(key("anivia", Position.SUPPORT))) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_RESOURCE_INTEGRITY");
        }
        values.put("championCount", catalog.all().size());
        values.put("duplicateChampionIdCount", catalog.all().size() - ids.size());
        values.put("legalRoleKeyCount", legal.size());
        values.put("roleCounts", roleCounts);
        values.put("powerProfileCount", powerIds.size());
        values.put("matchupRoleProfileCount", matchupKeys.size());
        values.put("compositionRoleProfileCount", compositionKeys.size());
        values.put("missing", missing.stream().sorted().toList());
        values.put("orphan", orphan.stream().sorted().toList());
        values.put("duplicateRoleProfileCount", 0);
        values.put("illegalExtraProfileCount", orphan.size());
        values.put("draftMetaHash", metaHash);
        values.put("legalRoleHash", legalHash);
        values.put("compositionHash", compositionHash);
        values.put("draftMetaVersion", resources.meta().metaVersion());
        values.put("championVersion", catalog.championPoolVersion());
        values.put("powerVersion", resources.champions().power().profileVersion());
        values.put("matchupVersion", resources.champions().matchup().version());
        values.put("compositionVersion", resources.champions().composition().version());
        values.put("fourCorrectedRoles", List.of("varus:TOP", "anivia:TOP", "cassiopeia:ADC", "taliyah:ADC"));
        values.put("illegalRole", "anivia:SUPPORT");
        return new StaticIntegrity(values, blockers);
    }

    private List<DraftAudit> runGameOneSchedule() {
        List<Phase13GAAuditSchedule.GameOneCase> cases = schedule.gameOneCases();
        return runParallel(cases.stream().map(value -> new CaseInput(value.caseId(),
                value.blueContextId(), value.redContextId(), new SeriesDraftHistory())).toList())
                .stream().sorted(Comparator.comparing(DraftAudit::caseId)).toList();
    }

    private List<DraftAudit> runParallel(List<CaseInput> inputs) {
        int workers = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<DraftAudit>> futures = inputs.stream()
                    .map(input -> executor.submit(() -> executeDraft(input.caseId(), input.blueContextId(),
                            input.redContextId(), input.history)))
                    .toList();
            List<DraftAudit> result = new ArrayList<>();
            for (Future<DraftAudit> future : futures) {
                try {
                    result.add(future.get());
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    result.add(failedAudit("executor-interrupted", "", "", error));
                } catch (ExecutionException error) {
                    result.add(failedAudit("executor-failure", "", "", error.getCause()));
                }
            }
            return result;
        } finally {
            executor.shutdown();
        }
    }

    private DraftAudit executeDraft(String id, String blueContextId, String redContextId,
                                    SeriesDraftHistory history) {
        Phase13GASyntheticContextFactory.SyntheticContext blue = requiredContext(blueContextId);
        Phase13GASyntheticContextFactory.SyntheticContext red = requiredContext(redContextId);
        long started = System.nanoTime();
        try {
            FinalDraftResult result = engine.draft(blue.draftContext(), red.draftContext(), history);
            Validation validation = validateDraft(id, blue, red, history, result);
            String digest = validation.violations().isEmpty() ? canonicalDraft(result, validation.trace()) :
                    "INVALID:" + String.join("|", validation.violations());
            return new DraftAudit(id, blueContextId, redContextId, result, validation.trace(),
                    validation.violations(), elapsedMillis(started), digest,
                    validation.preferredPlans(), validation.secondaryPlans(), validation.fallbackPlans(),
                    validation.pivotByCause());
        } catch (RuntimeException error) {
            return failedAudit(id, blueContextId, redContextId, error);
        }
    }

    private DraftAudit failedAudit(String id, String blueContextId, String redContextId, Throwable error) {
        String detail = error.getClass().getSimpleName() + ":" + String.valueOf(error.getMessage());
        return new DraftAudit(id, blueContextId, redContextId, null, List.of(), List.of(detail), 0L,
                "ERROR:" + detail, Map.of(), Map.of(), Map.of(), Map.of());
    }

    private Validation validateDraft(String id,
                                     Phase13GASyntheticContextFactory.SyntheticContext blue,
                                     Phase13GASyntheticContextFactory.SyntheticContext red,
                                     SeriesDraftHistory history, FinalDraftResult result) {
        List<String> violations = new ArrayList<>();
        List<CandidateTrace> trace = new ArrayList<>();
        Map<String, Integer> preferred = new TreeMap<>();
        Map<String, Integer> secondary = new TreeMap<>();
        Map<String, Integer> fallback = new TreeMap<>();
        Map<String, Integer> pivots = new TreeMap<>();
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), history);
        if (result.decisions().size() != 20) violations.add("DRAFT_ACTION_COUNT:" + result.decisions().size());
        for (DraftDecision decision : result.decisions()) {
            if (state.complete()) {
                violations.add("ACTION_AFTER_COMPLETION:" + decision.turn());
                break;
            }
            DraftTurn turn = state.currentTurn();
            TeamSide side = turn.side();
            DraftTeamContext own = side == TeamSide.BLUE ? blue.draftContext() : red.draftContext();
            DraftTeamContext enemy = side == TeamSide.BLUE ? red.draftContext() : blue.draftContext();
            DraftPlanPortfolio ownPortfolio = planner.replan(own, enemy, side, state);
            DraftPlanPortfolio enemyPortfolio = planner.replan(enemy, own, side.opposite(), state);
            List<ChampionId> generated = candidates.generate(state, own, enemy, ownPortfolio, enemyPortfolio);
            if (generated.isEmpty()) violations.add("CANDIDATE_EMPTY:" + turn.number());
            if (!generated.contains(decision.selectedChampionId())) {
                violations.add("SELECTED_OUTSIDE_CANDIDATES:" + turn.number());
            }
            if (decision.turn() != turn.number() || decision.side() != side
                    || decision.actionType() != turn.actionType()) {
                violations.add("ACTION_TURN_MISMATCH:" + turn.number());
            }
            if (decision.componentBreakdown().values().stream().anyMatch(value -> !Double.isFinite(value))) {
                violations.add("NONFINITE_COMPONENT:" + turn.number());
            }
            trace.add(new CandidateTrace(turn.number(), side.name(), turn.actionType().name(),
                    generated.stream().map(ChampionId::value).toList(), portfolioSignature(ownPortfolio),
                    decision.selectedChampionId().value(), componentSignature(decision.componentBreakdown()),
                    ownPortfolio.preferred().archetype().name()));
            addPlanCount(preferred, ownPortfolio, 0);
            addPlanCount(secondary, ownPortfolio, 1);
            addPlanCount(fallback, ownPortfolio, 2);
            Map<TeamSide, DraftPlanArchetype> before = Map.of(
                    TeamSide.BLUE, planner.replan(blue.draftContext(), red.draftContext(), TeamSide.BLUE, state).preferred().archetype(),
                    TeamSide.RED, planner.replan(red.draftContext(), blue.draftContext(), TeamSide.RED, state).preferred().archetype());
            try {
                state = state.apply(new DraftAction(turn.number(), turn.side(), turn.actionType(), decision.selectedChampionId()));
            } catch (RuntimeException error) {
                violations.add("ILLEGAL_ACTION:" + turn.number() + ":" + error.getMessage());
                break;
            }
            for (TeamSide changedSide : TeamSide.values()) {
                DraftTeamContext changedOwn = changedSide == TeamSide.BLUE ? blue.draftContext() : red.draftContext();
                DraftTeamContext changedEnemy = changedSide == TeamSide.BLUE ? red.draftContext() : blue.draftContext();
                DraftPlanArchetype after = planner.replan(changedOwn, changedEnemy, changedSide, state)
                        .preferred().archetype();
                if (before.get(changedSide) != after) {
                    String ownership = changedSide == side ? "own" : "enemy";
                    String action = turn.actionType() == DraftActionType.BAN ? "ban" : "pick";
                    pivots.merge(ownership + "-" + action, 1, Integer::sum);
                }
            }
        }
        if (!state.complete()) violations.add("DRAFT_NOT_COMPLETE");
        Set<ChampionId> all = new HashSet<>();
        all.addAll(result.bluePicks()); all.addAll(result.redPicks());
        all.addAll(result.blueBans()); all.addAll(result.redBans());
        if (all.size() != 20) violations.add("CURRENT_GAME_DUPLICATE_CHAMPION");
        if (result.bluePicks().size() != 5 || result.redPicks().size() != 5
                || result.blueBans().size() != 5 || result.redBans().size() != 5) {
            violations.add("SIDE_ACTION_COUNT");
        }
        validateFinalAssignments(result, violations);
        DraftPlanPortfolio expectedBlueInitial = planner.plan(blue.draftContext(), red.draftContext(),
                TeamSide.BLUE, history.consumedPicks());
        DraftPlanPortfolio expectedRedInitial = planner.plan(red.draftContext(), blue.draftContext(),
                TeamSide.RED, history.consumedPicks());
        if (!portfolioSignature(expectedBlueInitial).equals(portfolioSignature(result.blueInitialPortfolio()))) {
            violations.add("INITIAL_BLUE_PORTFOLIO_MISMATCH");
        }
        if (!portfolioSignature(expectedRedInitial).equals(portfolioSignature(result.redInitialPortfolio()))) {
            violations.add("INITIAL_RED_PORTFOLIO_MISMATCH");
        }
        DraftPlanPortfolio expectedBlueFinal = planner.replan(blue.draftContext(), red.draftContext(), TeamSide.BLUE, state);
        DraftPlanPortfolio expectedRedFinal = planner.replan(red.draftContext(), blue.draftContext(), TeamSide.RED, state);
        if (!portfolioSignature(expectedBlueFinal).equals(portfolioSignature(result.blueFinalPortfolio()))) {
            violations.add("FINAL_BLUE_PORTFOLIO_MISMATCH");
        }
        if (!portfolioSignature(expectedRedFinal).equals(portfolioSignature(result.redFinalPortfolio()))) {
            violations.add("FINAL_RED_PORTFOLIO_MISMATCH");
        }
        return new Validation(trace, violations, preferred, secondary, fallback, pivots);
    }

    private void validateFinalAssignments(FinalDraftResult result, List<String> violations) {
        if (result.matchChampionAssignments().selectionMode() != ChampionSelectionMode.EXPLICIT) {
            violations.add("MATCH_ASSIGNMENT_NOT_EXPLICIT");
        }
        if (result.blueFinalRoleAssignments().size() != 5 || result.redFinalRoleAssignments().size() != 5
                || !new HashSet<>(result.blueFinalRoleAssignments().values()).equals(Set.of(Position.values()))
                || !new HashSet<>(result.redFinalRoleAssignments().values()).equals(Set.of(Position.values()))) {
            violations.add("FINAL_ROLE_ASSIGNMENT_INVALID");
        }
        for (TeamSide side : TeamSide.values()) {
            Map<ChampionId, Position> finalRoles = side == TeamSide.BLUE
                    ? result.blueFinalRoleAssignments() : result.redFinalRoleAssignments();
            for (Map.Entry<ChampionId, Position> entry : finalRoles.entrySet()) {
                ChampionRoleKey key = new ChampionRoleKey(entry.getKey(), entry.getValue());
                if (!resources.champions().catalog().supports(key)) violations.add("ILLEGAL_FINAL_ROLE:" + key.stableId());
                ChampionAssignment assignment;
                try {
                    assignment = result.matchChampionAssignments().get(new PlayerKey(side, entry.getValue()));
                } catch (RuntimeException error) {
                    violations.add("MISSING_MATCH_ASSIGNMENT:" + side + ":" + entry.getValue());
                    continue;
                }
                if (!assignment.championId().equals(entry.getKey()) || assignment.selectedPosition() != entry.getValue()) {
                    violations.add("MATCH_ASSIGNMENT_ROLE_MISMATCH:" + side + ":" + entry.getValue());
                }
            }
        }
        if (result.matchChampionAssignments().asMap().size() != 10) violations.add("MATCH_ASSIGNMENT_COUNT");
    }

    private List<FearlessSeriesAudit> runFearlessSchedule() {
        List<CaseInput> inputs = schedule.fearlessSeries().stream().map(value ->
                new CaseInput(value.seriesId(), value.blueContextId(), value.redContextId(), new SeriesDraftHistory())).toList();
        int workers = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<FearlessSeriesAudit>> futures = inputs.stream().map(input -> executor.submit(() ->
                    executeSeries(input.caseId(), input.blueContextId(), input.redContextId()))).toList();
            List<FearlessSeriesAudit> result = new ArrayList<>();
            for (Future<FearlessSeriesAudit> future : futures) {
                try {
                    result.add(future.get());
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    result.add(failedSeries("executor-interrupted", error));
                } catch (ExecutionException error) {
                    result.add(failedSeries("executor-failure", error.getCause()));
                }
            }
            return result.stream().sorted(Comparator.comparing(FearlessSeriesAudit::seriesId)).toList();
        } finally {
            executor.shutdown();
        }
    }

    private FearlessSeriesAudit executeSeries(String seriesId, String blueId, String redId) {
        SeriesDraftHistory history = new SeriesDraftHistory();
        Set<ChampionId> priorPicks = new HashSet<>();
        List<DraftAudit> games = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int reuse = 0;
        int banConsumption = 0;
        for (int game = 1; game <= 5; game++) {
            Set<ChampionId> before = history.consumedPicks();
            DraftAudit audit = executeDraft(seriesId + "-game-" + game, blueId, redId, history);
            games.add(audit);
            errors.addAll(audit.violations());
            if (!audit.success()) break;
            FinalDraftResult result = audit.result();
            if (!result.hardFearlessExclusions().equals(before)) errors.add("FEARLESS_HISTORY_MISMATCH:game-" + game);
            List<ChampionId> picks = new ArrayList<>(result.bluePicks());
            picks.addAll(result.redPicks());
            Set<ChampionId> current = new HashSet<>(picks);
            if (picks.size() != 10 || current.size() != 10) errors.add("NEW_PICK_COUNT:game-" + game);
            Set<ChampionId> reused = new HashSet<>(current);
            reused.retainAll(priorPicks);
            reuse += reused.size();
            if (!reused.isEmpty()) errors.add("FEARLESS_PICK_REUSE:game-" + game + ":" + reused);
            Set<ChampionId> banned = new HashSet<>(result.blueBans());
            banned.addAll(result.redBans());
            Set<ChampionId> bannedBefore = new HashSet<>(banned);
            bannedBefore.retainAll(before);
            if (!bannedBefore.isEmpty()) {
                banConsumption += bannedBefore.size();
                errors.add("BAN_CONSUMES_PRIOR_PICK:game-" + game);
            }
            history.commitCompleted(result);
            Set<ChampionId> after = history.consumedPicks();
            Set<ChampionId> consumedBans = new HashSet<>(banned);
            consumedBans.retainAll(after);
            if (!consumedBans.isEmpty()) {
                banConsumption += consumedBans.size();
                errors.add("BAN_CONSUMPTION:game-" + game);
            }
            priorPicks.addAll(current);
        }
        boolean complete = games.size() == 5 && games.stream().allMatch(DraftAudit::success)
                && errors.isEmpty();
        String digest = games.stream().map(DraftAudit::digest).collect(Collectors.joining("\n"));
        return new FearlessSeriesAudit(seriesId, blueId, redId, games, complete, reuse, banConsumption,
                sha256(digest));
    }

    private FearlessSeriesAudit failedSeries(String id, Throwable error) {
        return new FearlessSeriesAudit(id, "", "", List.of(), false, 0, 0,
                "ERROR:" + error.getClass().getSimpleName() + ":" + error.getMessage());
    }

    private int replayGameOne(List<DraftAudit> originals) {
        int mismatches = 0;
        for (DraftAudit original : originals.stream().limit(GAME_ONE_REPLAY_CASES).toList()) {
            DraftAudit replay = executeDraft(original.caseId(), original.blueContextId(), original.redContextId(),
                    new SeriesDraftHistory());
            if (!Objects.equals(original.digest(), replay.digest())) mismatches++;
        }
        return mismatches;
    }

    private int replaySeries(List<FearlessSeriesAudit> originals) {
        int mismatches = 0;
        for (FearlessSeriesAudit original : originals.stream().limit(SERIES_REPLAY_CASES).toList()) {
            FearlessSeriesAudit replay = executeSeries(original.seriesId(), original.blueContextId(), original.redContextId());
            if (!Objects.equals(original.digest(), replay.digest())) mismatches++;
        }
        return mismatches;
    }

    private GameOneDistribution aggregateGameOne(List<DraftAudit> audits) {
        Map<String, Integer> picks = new TreeMap<>();
        Map<String, Integer> bans = new TreeMap<>();
        Map<String, Integer> presence = new TreeMap<>();
        Map<String, Integer> rolesByKey = new TreeMap<>();
        Map<String, Map<String, Integer>> flexRoles = new TreeMap<>();
        Map<String, Integer> sidePicks = new TreeMap<>();
        Map<String, Integer> sideBans = new TreeMap<>();
        Map<String, Set<String>> sidePickChampions = new TreeMap<>();
        Map<String, Set<String>> sideBanChampions = new TreeMap<>();
        Map<String, Integer> firstPickEquivalent = new TreeMap<>();
        Map<String, Integer> preferred = new TreeMap<>();
        Map<String, Integer> secondary = new TreeMap<>();
        Map<String, Integer> fallback = new TreeMap<>();
        Map<String, Integer> pivots = new TreeMap<>();
        Map<String, Integer> finalPreferred = new TreeMap<>();
        int unresolvedFlex = 0;
        int draftedFlex = 0;
        int finalFlexResolutions = 0;
        int impossibleFinalRoles = 0;
        for (DraftAudit audit : audits) {
            if (!audit.success()) continue;
            FinalDraftResult result = audit.result();
            Set<String> casePresence = new HashSet<>();
            count(picks, result.bluePicks()); count(picks, result.redPicks());
            count(bans, result.blueBans()); count(bans, result.redBans());
            result.bluePicks().forEach(id -> { casePresence.add(id.value()); sidePicks.compute("BLUE", (k, v) -> v == null ? 1 : v + 1); });
            result.redPicks().forEach(id -> { casePresence.add(id.value()); sidePicks.compute("RED", (k, v) -> v == null ? 1 : v + 1); });
            result.blueBans().forEach(id -> { casePresence.add(id.value()); sideBans.compute("BLUE", (k, v) -> v == null ? 1 : v + 1); });
            result.redBans().forEach(id -> { casePresence.add(id.value()); sideBans.compute("RED", (k, v) -> v == null ? 1 : v + 1); });
            for (String champion : casePresence) presence.merge(champion, 1, Integer::sum);
            sidePickChampions.computeIfAbsent("BLUE", key -> new HashSet<>()).addAll(result.bluePicks().stream().map(ChampionId::value).toList());
            sidePickChampions.computeIfAbsent("RED", key -> new HashSet<>()).addAll(result.redPicks().stream().map(ChampionId::value).toList());
            sideBanChampions.computeIfAbsent("BLUE", key -> new HashSet<>()).addAll(result.blueBans().stream().map(ChampionId::value).toList());
            sideBanChampions.computeIfAbsent("RED", key -> new HashSet<>()).addAll(result.redBans().stream().map(ChampionId::value).toList());
            result.decisions().stream().filter(value -> value.actionType() == DraftActionType.PICK)
                    .findFirst().ifPresent(decision -> firstPickEquivalent.merge(decision.side().name(), 1, Integer::sum));
            addPlanFrequency(preferred, audit.preferredPlans());
            addPlanFrequency(secondary, audit.secondaryPlans());
            addPlanFrequency(fallback, audit.fallbackPlans());
            addPlanFrequency(pivots, audit.pivotByCause());
            finalPreferred.merge("BLUE:" + result.blueFinalPortfolio().preferred().archetype(), 1, Integer::sum);
            finalPreferred.merge("RED:" + result.redFinalPortfolio().preferred().archetype(), 1, Integer::sum);
            for (TeamSide side : TeamSide.values()) {
                Map<ChampionId, Position> rolesByChampion = side == TeamSide.BLUE
                        ? result.blueFinalRoleAssignments() : result.redFinalRoleAssignments();
                List<ChampionId> picksBySide = side == TeamSide.BLUE ? result.bluePicks() : result.redPicks();
                for (ChampionId id : picksBySide) {
                    ChampionDefinition champion = resources.champions().catalog().get(id);
                    if (champion.supportedPositions().size() > 1) {
                        draftedFlex++;
                        if (roles.feasiblePickedPositions(picksBySide, id).size() > 1) unresolvedFlex++;
                        Position finalPosition = rolesByChampion.get(id);
                        if (finalPosition != null) {
                            finalFlexResolutions++;
                            flexRoles.computeIfAbsent(id.value(), key -> new TreeMap<>())
                                    .merge(finalPosition.name(), 1, Integer::sum);
                        }
                    }
                    Position finalPosition = rolesByChampion.get(id);
                    if (finalPosition == null || !champion.supportedPositions().contains(finalPosition)) impossibleFinalRoles++;
                    else rolesByKey.merge(id.value() + ":" + finalPosition.name(), 1, Integer::sum);
                }
            }
        }
        Map<String, Object> metrics = new TreeMap<>();
        int cases = (int) audits.stream().filter(DraftAudit::success).count();
        int totalPicks = picks.values().stream().mapToInt(Integer::intValue).sum();
        int totalBans = bans.values().stream().mapToInt(Integer::intValue).sum();
        metrics.put("caseCount", cases);
        metrics.put("uniquePickedChampions", picks.size());
        metrics.put("uniqueBannedChampions", bans.size());
        metrics.put("uniquePickOrBanChampions", presence.size());
        metrics.put("totalPickOccurrences", totalPicks);
        metrics.put("totalBanOccurrences", totalBans);
        metrics.put("championPickRateDenominator", totalPicks);
        metrics.put("championBanRateDenominator", totalBans);
        metrics.put("roleAssignmentCount", rolesByKey.values().stream().mapToInt(Integer::intValue).sum());
        metrics.put("uniqueAssignedTop", uniqueRoleChampions(rolesByKey, "TOP"));
        metrics.put("uniqueAssignedJungle", uniqueRoleChampions(rolesByKey, "JUNGLE"));
        metrics.put("uniqueAssignedMid", uniqueRoleChampions(rolesByKey, "MID"));
        metrics.put("uniqueAssignedAdc", uniqueRoleChampions(rolesByKey, "ADC"));
        metrics.put("uniqueAssignedSupport", uniqueRoleChampions(rolesByKey, "SUPPORT"));
        metrics.put("flexDraftOccurrences", draftedFlex);
        metrics.put("preFinalUnresolvedFlexOccurrences", unresolvedFlex);
        metrics.put("finalFlexResolutionCount", finalFlexResolutions);
        metrics.put("impossibleFinalRoleCount", impossibleFinalRoles);
        metrics.put("sidePickOccurrences", sidePicks);
        metrics.put("sideBanOccurrences", sideBans);
        metrics.put("sidePickDiversity", sidePickChampions.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, value -> value.getValue().size(), (a, b) -> a, TreeMap::new)));
        metrics.put("sideBanDiversity", sideBanChampions.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, value -> value.getValue().size(), (a, b) -> a, TreeMap::new)));
        metrics.put("firstPickEquivalent", firstPickEquivalent);
        metrics.put("preferredPlanFrequency", preferred);
        metrics.put("secondaryPlanFrequency", secondary);
        metrics.put("fallbackPlanFrequency", fallback);
        metrics.put("pivotByCause", pivots);
        metrics.put("finalPreferredPlanFrequency", finalPreferred);
        metrics.put("neverPicked", resources.champions().catalog().all().stream().map(ChampionDefinition::id)
                .map(ChampionId::value).filter(id -> !picks.containsKey(id)).sorted().toList());
        metrics.put("neverBanned", resources.champions().catalog().all().stream().map(ChampionDefinition::id)
                .map(ChampionId::value).filter(id -> !bans.containsKey(id)).sorted().toList());
        metrics.put("neverPickOrBan", resources.champions().catalog().all().stream().map(ChampionDefinition::id)
                .map(ChampionId::value).filter(id -> !presence.containsKey(id)).sorted().toList());
        metrics.put("pickConcentration", concentration(picks, totalPicks));
        metrics.put("banConcentration", concentration(bans, totalBans));
        metrics.put("roleSpecificHHI", roleHhi(rolesByKey));
        metrics.put("normalizedPickEntropy", entropy(picks));
        metrics.put("normalizedBanEntropy", entropy(bans));
        return new GameOneDistribution(picks, bans, presence, rolesByKey, flexRoles, metrics);
    }

    private List<ComponentDistribution> componentDistribution(List<DraftAudit> audits) {
        Map<String, List<Double>> values = new TreeMap<>();
        for (String component : PICK_COMPONENTS) values.put("PICK:" + component, new ArrayList<>());
        for (String component : BAN_COMPONENTS) values.put("BAN:" + component, new ArrayList<>());
        for (DraftAudit audit : audits) {
            if (!audit.success()) continue;
            for (DraftDecision decision : audit.result().decisions()) {
                List<String> expected = decision.actionType() == DraftActionType.PICK ? PICK_COMPONENTS : BAN_COMPONENTS;
                for (String component : expected) values.get(decision.actionType().name() + ":" + component)
                        .add(decision.componentBreakdown().getOrDefault(component, 0.0));
            }
        }
        List<ComponentDistribution> result = new ArrayList<>();
        values.forEach((key, sample) -> {
            String[] parts = key.split(":", 2);
            result.add(stats(parts[0], parts[1], sample));
        });
        return result;
    }

    private ComponentDistribution stats(String actionType, String component, List<Double> sample) {
        List<Double> finite = sample.stream().filter(Double::isFinite).sorted().toList();
        long nonFinite = sample.stream().filter(value -> !Double.isFinite(value)).count();
        double min = finite.isEmpty() ? 0.0 : finite.getFirst();
        double max = finite.isEmpty() ? 0.0 : finite.getLast();
        double mean = finite.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double median = percentile(finite, .50);
        double p10 = percentile(finite, .10);
        double p90 = percentile(finite, .90);
        double zero = sample.isEmpty() ? 0.0 : sample.stream().filter(value -> value == 0.0).count() / (double) sample.size();
        double positive = sample.isEmpty() ? 0.0 : sample.stream().filter(value -> Double.isFinite(value) && value > 0.0).count() / (double) sample.size();
        return new ComponentDistribution(actionType, component, sample.size(), min, max, mean, median, p10, p90,
                zero, positive, nonFinite);
    }

    private ControlledProbeResult controlledProbes(GameOneDistribution distribution,
                                                   List<ComponentDistribution> components) {
        Map<String, Object> values = new TreeMap<>();
        boolean activation = false;
        boolean power = resources.champions().power().all().stream()
                .anyMatch(profile -> profile.contextModifiers().values().stream().anyMatch(value -> value != 0.0));
        values.put("championFoundation", resources.champions().catalog().legalRoleKeys().size() == 216);
        values.put("championPowerNonNeutralCase", power);
        if (power) activation = true;

        ChampionRoleKey positiveSource = null;
        ChampionRoleKey positiveTarget = null;
        List<ChampionRoleKey> legal = resources.champions().catalog().legalRoleKeys().stream()
                .sorted(Comparator.comparing(ChampionRoleKey::stableId)).toList();
        for (ChampionRoleKey source : legal) {
            for (ChampionRoleKey target : legal) {
                if (source.position() == target.position() && !source.championId().equals(target.championId())
                        && matchup.roleEdge(source, target) != 0.0) {
                    positiveSource = source; positiveTarget = target; break;
                }
            }
            if (positiveSource != null) break;
        }
        double rawEdge = positiveSource == null ? 0.0 : matchup.roleEdge(positiveSource, positiveTarget);
        values.put("matchupSameRoleNonZeroSource", positiveSource == null ? "" : positiveSource.stableId());
        values.put("matchupSameRoleNonZeroTarget", positiveTarget == null ? "" : positiveTarget.stableId());
        values.put("matchupRawEdge", rawEdge);
        if (positiveSource != null) activation = true;

        List<ChampionId> lineupA = ids("malphite", "sejuani", "orianna", "jinx", "lulu");
        List<ChampionId> lineupB = ids("fiora", "lee-sin", "syndra", "caitlyn", "nautilus");
        DraftPlanPortfolio plan = planner.plan(requiredContext("synthetic-neutral").draftContext(),
                requiredContext("synthetic-neutral").draftContext(), TeamSide.BLUE, Set.of());
        double compositionA = roles.feasibleAssignments(lineupA).stream()
                .mapToDouble(composition::assignmentQuality).max().orElse(0.0);
        double compositionB = roles.feasibleAssignments(lineupB).stream()
                .mapToDouble(composition::assignmentQuality).max().orElse(0.0);
        values.put("compositionControlledLineupA", compositionA);
        values.put("compositionControlledLineupB", compositionB);
        values.put("compositionDiffersFromControlledPair", compositionA != compositionB);
        if (compositionA != compositionB) activation = true;
        values.put("draftComponentPositiveObserved", components.stream().anyMatch(value -> value.positiveRate() > 0.0));
        if (components.stream().anyMatch(value -> value.positiveRate() > 0.0)) activation = true;

        ProtectionProbe protectionProbe = protectionProbe();
        values.putAll(protectionProbe.values());
        boolean protectionPositive = protectionProbe.positive();
        if (protectionPositive) activation = true;
        PlanPivotProbe pivot = planPivotProbe();
        values.putAll(pivot.values());
        boolean flexLegal = flexProbe(values);
        values.put("flexControlledLegal", flexLegal);
        if (flexLegal) activation = true;
        values.put("productionPowerContext", ProgressionCombatContext.LANE_COMBAT.name());
        values.put("productionSearchBounds", Map.of("candidateLimit", policy.candidateLimit(),
                "structuralRepairSlots", policy.structuralRepairSlots(), "searchDepth", policy.searchDepth(),
                "beamWidth", policy.beamWidth()));
        return new ControlledProbeResult(values, protectionPositive, pivot.pivot(), flexLegal, activation);
    }

    private record ProtectionProbe(Map<String, Object> values, boolean positive) { }

    private ProtectionProbe protectionProbe() {
        ChampionId carry = id("caitlyn");
        DraftState state = new DraftState(DraftRuleSet.professional(), 13,
                List.of(carry), List.of(), List.of(), List.of(), Set.of());
        ChampionId threat = resources.champions().catalog().forPosition(Position.ADC).stream()
                .map(ChampionDefinition::id).filter(value -> !value.equals(carry))
                .max(Comparator.comparingDouble(value -> matchup.roleEdge(
                        new ChampionRoleKey(value, Position.ADC), new ChampionRoleKey(carry, Position.ADC))))
                .orElseThrow();
        DraftPlanPortfolio own = new DraftPlanPortfolio(List.of(new DraftPlan(
                DraftPlanArchetype.FRONT_TO_BACK, DraftPlanArchetype.FRONT_TO_BACK.desired(),
                DraftPlanArchetype.FRONT_TO_BACK.vulnerabilities(), List.of(), Map.of(), 10.0)));
        BanEvaluator evaluator = new BanEvaluator(resources.champions().catalog(), resources.meta(),
                resources.champions().composition(), roles, availability, composition, matchup, policy);
        BanEvaluation positive = evaluator.evaluate(state, TeamSide.BLUE, threat,
                requiredContext("synthetic-neutral").draftContext(), requiredContext("synthetic-neutral").draftContext(), own, own);
        double crossRole = evaluator.evaluate(state, TeamSide.BLUE, id("fiora"),
                requiredContext("synthetic-neutral").draftContext(), requiredContext("synthetic-neutral").draftContext(), own, own)
                .components().get(BanScoreComponent.PROTECTION_VALUE);
        Map<String, Object> values = new TreeMap<>();
        values.put("protectionThreat", threat.value());
        values.put("protectionPositiveValue", positive.components().get(BanScoreComponent.PROTECTION_VALUE));
        values.put("protectionCrossRoleValue", crossRole);
        values.put("protectionSameRolePositiveOnly", positive.components().get(BanScoreComponent.PROTECTION_VALUE) > 0.0 && crossRole == 0.0);
        return new ProtectionProbe(values, positive.components().get(BanScoreComponent.PROTECTION_VALUE) > 0.0
                && crossRole == 0.0);
    }

    private record PlanPivotProbe(Map<String, Object> values, boolean pivot) { }

    private PlanPivotProbe planPivotProbe() {
        DraftState state = stateAfter(List.of("camille", "vi", "poppy", "nautilus", "fiora", "jax", "syndra", "varus", "ryze", "ezreal", "bard", "kaisa"));
        DraftTeamContext blue = requiredContext("synthetic-meta-contrarian").draftContext();
        DraftTeamContext red = requiredContext("synthetic-neutral").draftContext();
        DraftPlanArchetype before = planner.replan(blue, red, TeamSide.BLUE, state).preferred().archetype();
        ChampionId candidate = id("anivia");
        Map<String, Object> values = new TreeMap<>();
        DraftState after = state.apply(new DraftAction(state.currentTurn().number(), state.currentTurn().side(),
                state.currentTurn().actionType(), candidate));
        values.put("planPivotStateTurn", state.currentTurn().number());
        values.put("planPivotInitialPreferred", before.name());
        values.put("planPivotCandidate", candidate == null ? "" : candidate.value());
        boolean pivot = planner.replan(blue, red, TeamSide.BLUE, after).preferred().archetype() != before;
        values.put("planPivotObserved", pivot);
        return new PlanPivotProbe(values, pivot);
    }

    private boolean flexProbe(Map<String, Object> values) {
        List<ChampionId> blue = ids("taliyah", "varus", "poppy", "gnar", "graves");
        List<ChampionId> red = ids("galio", "rumble", "lee-sin", "ezreal", "janna");
        boolean feasible = roles.isFeasible(blue) && roles.isFeasible(red);
        if (feasible) {
            RoleAssignmentSolver.RoleAssignment blueAssignment = roles.bestAssignment(blue,
                    requiredContext("synthetic-flex-wide").draftContext());
            RoleAssignmentSolver.RoleAssignment redAssignment = roles.bestAssignment(red,
                    requiredContext("synthetic-flex-narrow").draftContext());
            feasible = blueAssignment.positions().size() == 5 && redAssignment.positions().size() == 5;
            values.put("flexBlueAssignment", assignmentSignature(blueAssignment));
            values.put("flexRedAssignment", assignmentSignature(redAssignment));
            values.put("flexControlledChampions", List.of("taliyah", "varus", "poppy", "galio"));
        }
        return feasible;
    }

    private List<IntegrationAudit> runIntegrationProbes(List<DraftAudit> gameOne,
                                                         List<FearlessSeriesAudit> fearless) {
        List<IntegrationDraft> selected = new ArrayList<>();
        gameOne.stream().filter(DraftAudit::success).sorted(Comparator.comparing(DraftAudit::caseId))
                .limit(12).forEach(value -> selected.add(new IntegrationDraft(
                        "game1-" + value.caseId(), value.blueContextId(), value.redContextId(), value.result())));
        fearless.stream().sorted(Comparator.comparing(FearlessSeriesAudit::seriesId)).limit(8)
                .forEach(series -> {
                    if (series.games().size() > 1 && series.games().get(1).success()) {
                        DraftAudit game = series.games().get(1);
                        selected.add(new IntegrationDraft("fearless-" + series.seriesId() + "-game-2",
                                game.blueContextId(), game.redContextId(), game.result()));
                    }
                });
        List<IntegrationAudit> result = new ArrayList<>();
        int index = 0;
        for (IntegrationDraft draft : selected) {
            for (long seed : INTEGRATION_SEEDS) {
                result.add(integrate(draft, seed, index++));
            }
        }
        return List.copyOf(result);
    }

    private record IntegrationDraft(String id, String blueContextId, String redContextId, FinalDraftResult result) { }

    private IntegrationAudit integrate(IntegrationDraft draft, long seed, int index) {
        List<String> violations = new ArrayList<>();
        if (draft.result().matchChampionAssignments().selectionMode() != ChampionSelectionMode.EXPLICIT) {
            violations.add("ASSIGNMENT_MODE");
        }
        MatchChampionAssignments assignments = draft.result().matchChampionAssignments();
        for (TeamSide side : TeamSide.values()) {
            for (Position position : Position.values()) {
                ChampionAssignment assignment;
                try {
                    assignment = assignments.get(new PlayerKey(side, position));
                } catch (RuntimeException error) {
                    violations.add("MISSING_ASSIGNMENT:" + side + ":" + position);
                    continue;
                }
                if (!resources.champions().catalog().supports(new ChampionRoleKey(
                        assignment.championId(), assignment.selectedPosition()))) {
                    violations.add("ILLEGAL_ASSIGNMENT:" + assignment.championId() + ":" + assignment.selectedPosition());
                }
            }
        }
        String digest = "";
        int duration = 0;
        String winner = "";
        boolean replayMismatch = false;
        try {
            Phase13GASyntheticContextFactory.SyntheticContext blue = requiredContext(draft.blueContextId());
            Phase13GASyntheticContextFactory.SyntheticContext red = requiredContext(draft.redContextId());
            MatchSimulator simulator = simulator();
            MatchTimeline first = simulator.simulate(syntheticTeam(TeamSide.BLUE, blue), syntheticTeam(TeamSide.RED, red), seed, assignments);
            MatchTimeline replay = simulator.simulate(syntheticTeam(TeamSide.BLUE, blue), syntheticTeam(TeamSide.RED, red), seed, assignments);
            digest = timelineDigest(first);
            replayMismatch = !digest.equals(timelineDigest(replay));
            duration = first.getDurationSeconds();
            winner = first.getWinner();
            if (duration <= 0 || duration > MatchSimulator.SIMULATION_SAFETY_TIMEOUT_SECONDS) violations.add("INVALID_DURATION");
            if (winner == null || winner.isBlank()) violations.add("MISSING_WINNER");
            if (first.getEvents().isEmpty()) violations.add("EMPTY_EVENTS");
            if (first.getSnapshots().isEmpty()) violations.add("EMPTY_SNAPSHOTS");
            if (replayMismatch) violations.add("TIMELINE_REPLAY_MISMATCH");
        } catch (RuntimeException error) {
            violations.add(error.getClass().getSimpleName() + ":" + error.getMessage());
        }
        return new IntegrationAudit(draft.id(), draft.blueContextId() + "-vs-" + draft.redContextId(), seed,
                violations.isEmpty() && !replayMismatch, replayMismatch, duration, winner, digest, violations);
    }

    private MatchSimulator simulator() {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(),
                new SnapshotFactory(resources.champions().catalog()), new ObjectiveResolver(),
                new com.lolfm.simulator.PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), SimulationOptions.productionDefaults(),
                resources.champions().matchup());
    }

    private Team syntheticTeam(TeamSide side, Phase13GASyntheticContextFactory.SyntheticContext context) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values()) {
            ChampionProficiencies proficiencies = context.draftContext().proficiencies().get(position);
            players.add(new Player("synthetic-" + side.name().toLowerCase() + "-" + position.name().toLowerCase(),
                    position, PlayerRatings.neutral(position), proficiencies));
        }
        return new Team("synthetic-" + side.name().toLowerCase() + "-" + context.id(), players);
    }

    private List<String> reviewCodes(GameOneDistribution distribution,
                                     List<ComponentDistribution> components,
                                     ControlledProbeResult probes,
                                     List<DraftAudit> gameOne,
                                     List<FearlessSeriesAudit> fearless,
                                     List<IntegrationAudit> integrations) {
        List<String> result = new ArrayList<>();
        Map<String, Object> pickConcentration = castMap(distribution.metrics().get("pickConcentration"));
        Map<String, Object> banConcentration = castMap(distribution.metrics().get("banConcentration"));
        if (number(pickConcentration.get("top1Share")) >= .75 || number(banConcentration.get("top1Share")) >= .75) {
            result.add("REVIEW_HIGH_DRAFT_LOCK");
        }
        if (number(pickConcentration.get("top1Share")) >= .90 || number(banConcentration.get("top1Share")) >= .90) {
            result.add("REVIEW_EXTREME_DRAFT_LOCK");
        }
        if (distribution.metrics().get("caseCount") instanceof Number cases && cases.intValue() < 120) {
            result.add("REVIEW_SYNTHETIC_SAMPLE_LIMITATION");
        }
        for (Map.Entry<String, Integer> entry : distribution.pickOrBanPresence().entrySet()) {
            double share = entry.getValue() / (double) Math.max(1, distribution.metrics().get("caseCount") instanceof Number n ? n.intValue() : 1);
            if (share >= .90) result.add("REVIEW_EXTREME_DRAFT_LOCK");
            else if (share >= .75) result.add("REVIEW_HIGH_DRAFT_LOCK");
        }
        for (Map.Entry<String, Integer> entry : distribution.roleAssignmentOccurrences().entrySet()) {
            String role = entry.getKey().substring(entry.getKey().lastIndexOf(':') + 1);
            int total = distribution.roleAssignmentOccurrences().entrySet().stream()
                    .filter(value -> value.getKey().endsWith(":" + role)).mapToInt(Map.Entry::getValue).sum();
            double share = entry.getValue() / (double) Math.max(1, total);
            if (share >= .50) result.add("REVIEW_EXTREME_ROLE_CONCENTRATION");
            else if (share >= .35) result.add("REVIEW_ROLE_CONCENTRATION");
        }
        if (distribution.metrics().get("neverPickOrBan") instanceof Collection<?> never && !never.isEmpty()) {
            result.add("REVIEW_SYSTEMIC_CHAMPION_STARVATION");
        }
        if (distribution.metrics().get("pivotByCause") instanceof Map<?, ?> pivots
                && pivots.values().stream().mapToInt(value -> ((Number) value).intValue()).sum() == 0) {
            result.add("REVIEW_PLAN_PIVOT_INERT");
        }
        Object preferred = distribution.metrics().get("finalPreferredPlanFrequency");
        if (preferred instanceof Map<?, ?> values && values.size() == 1) result.add("REVIEW_PLAN_ARCHETYPE_COLLAPSE");
        for (ComponentDistribution component : components) {
            if (component.sampleCount() > 0 && component.nonFiniteCount() == 0
                    && component.min() == component.max()) result.add("REVIEW_DRAFT_COMPONENT_INERT");
        }
        if (!probes.planPivot()) result.add("REVIEW_PLAN_PIVOT_INERT");
        if (gameOne.stream().anyMatch(value -> value.latencyMillis() > 30_000)) result.add("REVIEW_DRAFT_LATENCY_OUTLIER");
        return result;
    }

    private Map<String, Object> summary(StaticIntegrity integrity, GameOneDistribution distribution,
                                        List<ComponentDistribution> components, ControlledProbeResult probes,
                                        List<DraftAudit> gameOne, List<FearlessSeriesAudit> fearless,
                                        List<IntegrationAudit> integrations, int draftReplay,
                                        int seriesReplay, int matchReplay, List<String> reviews,
                                        List<String> blockers, long wallMillis) {
        Map<String, Object> summary = new TreeMap<>();
        summary.put("phase", PHASE);
        summary.put("auditVersion", "13G-A-STRUCTURAL-INTEGRATED-AUDIT-V1");
        summary.put("timestamp", "INFORMATIONAL_OMITTED_FOR_DETERMINISM");
        summary.put("championVersion", integrity.values().get("championVersion"));
        summary.put("powerVersion", integrity.values().get("powerVersion"));
        summary.put("matchupVersion", integrity.values().get("matchupVersion"));
        summary.put("compositionVersion", integrity.values().get("compositionVersion"));
        summary.put("draftMetaVersion", integrity.values().get("draftMetaVersion"));
        summary.put("championCount", integrity.values().get("championCount"));
        summary.put("legalRoleKeyCount", integrity.values().get("legalRoleKeyCount"));
        summary.put("draftMetaHash", integrity.values().get("draftMetaHash"));
        summary.put("legalRoleHash", integrity.values().get("legalRoleHash"));
        summary.put("compositionHash", integrity.values().get("compositionHash"));
        summary.put("syntheticContextAlgorithm", Phase13GASyntheticContextFactory.ALGORITHM_VERSION);
        summary.put("syntheticContextCount", contexts.size());
        summary.put("game1CaseCount", gameOne.size());
        summary.put("fearlessSeriesCount", fearless.size());
        summary.put("fearlessDraftCount", fearless.stream().mapToInt(value -> value.games().size()).sum());
        summary.put("integrationDraftCount", integrations.stream().map(IntegrationAudit::draftId).distinct().count());
        summary.put("integrationMatchCount", integrations.size());
        summary.put("draftCompletionFailures", gameOne.stream().filter(value -> !value.success()).count());
        summary.put("illegalActionCount", gameOne.stream().flatMap(value -> value.violations().stream())
                .filter(value -> value.startsWith("ILLEGAL_ACTION") || value.startsWith("SELECTED_OUTSIDE"))
                .count());
        summary.put("illegalRoleAssignmentCount", distribution.metrics().get("impossibleFinalRoleCount"));
        summary.put("candidateEmptyCount", gameOne.stream().flatMap(value -> value.violations().stream())
                .filter(value -> value.startsWith("CANDIDATE_EMPTY")).count());
        summary.put("fearlessReuseCount", fearless.stream().mapToInt(FearlessSeriesAudit::reuseCount).sum());
        summary.put("fearlessBanConsumptionViolations", fearless.stream().mapToInt(FearlessSeriesAudit::banConsumptionViolations).sum());
        summary.put("determinismMismatchCount", draftReplay + seriesReplay);
        summary.put("matchReplayMismatchCount", matchReplay);
        summary.put("nonFiniteComponentCount", components.stream().mapToLong(ComponentDistribution::nonFiniteCount).sum());
        summary.put("uniquePickedChampions", distribution.metrics().get("uniquePickedChampions"));
        summary.put("uniqueBannedChampions", distribution.metrics().get("uniqueBannedChampions"));
        summary.put("uniquePickOrBanChampions", distribution.metrics().get("uniquePickOrBanChampions"));
        summary.put("pickHHI", castMap(distribution.metrics().get("pickConcentration")).get("hhi"));
        summary.put("banHHI", castMap(distribution.metrics().get("banConcentration")).get("hhi"));
        summary.put("reviewCodes", reviews);
        summary.put("warningCodes", List.of("SYNTHETIC_CONTEXTS_ARE_NOT_REAL_PLAYER_DATA"));
        summary.put("blockerCodes", blockers);
        summary.put("latency", latency(gameOne, fearless, wallMillis));
        summary.put("backendTests", -1);
        summary.put("backendFailures", -1);
        summary.put("backendErrors", -1);
        summary.put("backendSkipped", -1);
        String verdict;
        boolean allowed = blockers.isEmpty();
        if (!allowed) verdict = "PHASE_13G_A_STRUCTURAL_INTEGRATED_AUDIT_BLOCKED";
        else if (!reviews.isEmpty()) verdict = "PHASE_13G_A_STRUCTURAL_INTEGRATED_AUDIT_COMPLETE_WITH_REVIEWS";
        else verdict = "PHASE_13G_A_STRUCTURAL_INTEGRATED_AUDIT_COMPLETE";
        summary.put("verdict", verdict);
        summary.put("phase13GRealDataPopulationAllowed", allowed);
        summary.put("nextPhase", allowed ? "PHASE_13G_REAL_PLAYER_DATA_POPULATION" : "PHASE_13G_A_STRUCTURAL_FIX_REQUIRED");
        summary.put("activationProbesPass", probes.activationPass());
        return summary;
    }

    private Map<String, Object> latency(List<DraftAudit> gameOne, List<FearlessSeriesAudit> fearless, long wallMillis) {
        List<Long> game = gameOne.stream().map(DraftAudit::latencyMillis).sorted().toList();
        List<Long> later = fearless.stream().flatMap(value -> value.games().stream()).skip(12)
                .map(DraftAudit::latencyMillis).sorted().toList();
        Map<String, Object> result = new TreeMap<>();
        result.put("auditWallMillis", wallMillis);
        result.put("game1", latencyStats(game));
        result.put("laterFearless", latencyStats(later));
        return result;
    }

    private Map<String, Object> latencyStats(List<Long> values) {
        Map<String, Object> result = new TreeMap<>();
        result.put("count", values.size());
        result.put("meanMillis", values.stream().mapToLong(Long::longValue).average().orElse(0.0));
        result.put("medianMillis", percentileLong(values, .50));
        result.put("p90Millis", percentileLong(values, .90));
        result.put("p95Millis", percentileLong(values, .95));
        result.put("maxMillis", values.stream().mapToLong(Long::longValue).max().orElse(0L));
        return result;
    }

    private static Map<String, Object> concentration(Map<String, Integer> counts, int total) {
        List<Integer> ordered = counts.values().stream().sorted(Comparator.reverseOrder()).toList();
        Map<String, Object> result = new TreeMap<>();
        result.put("top1Share", share(ordered, total, 1));
        result.put("top5Share", share(ordered, total, 5));
        result.put("top10Share", share(ordered, total, 10));
        result.put("top20Share", share(ordered, total, 20));
        result.put("hhi", counts.values().stream().mapToDouble(value -> {
            double p = total == 0 ? 0.0 : value / (double) total; return p * p;
        }).sum());
        return result;
    }

    private static Map<String, Double> roleHhi(Map<String, Integer> roles) {
        Map<String, Double> result = new TreeMap<>();
        for (Position position : Position.values()) {
            int total = roles.entrySet().stream().filter(value -> value.getKey().endsWith(":" + position.name()))
                    .mapToInt(Map.Entry::getValue).sum();
            double hhi = roles.entrySet().stream().filter(value -> value.getKey().endsWith(":" + position.name()))
                    .mapToDouble(value -> { double p = total == 0 ? 0.0 : value.getValue() / (double) total; return p * p; }).sum();
            result.put(position.name(), hhi);
        }
        return result;
    }

    private static double entropy(Map<String, Integer> counts) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0 || counts.size() <= 1) return 0.0;
        double value = counts.values().stream().mapToDouble(count -> {
            double p = count / (double) total; return -p * Math.log(p);
        }).sum();
        return value / Math.log(counts.size());
    }

    private static double share(List<Integer> counts, int total, int limit) {
        if (total == 0) return 0.0;
        return counts.stream().limit(limit).mapToInt(Integer::intValue).sum() / (double) total;
    }

    private static int uniqueRoleChampions(Map<String, Integer> roles, String role) {
        return (int) roles.keySet().stream().filter(value -> value.endsWith(":" + role)).count();
    }

    private static void count(Map<String, Integer> target, List<ChampionId> ids) {
        ids.forEach(id -> target.merge(id.value(), 1, Integer::sum));
    }

    private static void addPlanCount(Map<String, Integer> target, DraftPlanPortfolio portfolio, int index) {
        if (portfolio.plans().size() > index) target.merge(portfolio.plans().get(index).archetype().name(), 1, Integer::sum);
    }

    private static void addPlanFrequency(Map<String, Integer> target, Map<String, Integer> source) {
        source.forEach((key, value) -> target.merge(key, value, Integer::sum));
    }

    private static double percentile(List<Double> values, double p) {
        if (values.isEmpty()) return 0.0;
        int index = (int) Math.round((values.size() - 1) * p);
        return values.get(Math.max(0, Math.min(values.size() - 1, index)));
    }

    private static long percentileLong(List<Long> values, double p) {
        if (values.isEmpty()) return 0L;
        int index = (int) Math.round((values.size() - 1) * p);
        return values.get(Math.max(0, Math.min(values.size() - 1, index)));
    }

    private static long elapsedMillis(long started) { return (System.nanoTime() - started) / 1_000_000L; }

    private static ChampionRoleKey key(String champion, Position position) {
        return new ChampionRoleKey(new ChampionId(champion), position);
    }

    private static ChampionId id(String champion) { return new ChampionId(champion); }

    private static List<ChampionId> ids(String... values) {
        return java.util.Arrays.stream(values).map(ChampionId::new).toList();
    }

    private static DraftState stateAfter(List<String> values) {
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        for (String value : values) {
            DraftTurn turn = state.currentTurn();
            state = state.apply(new DraftAction(turn.number(), turn.side(), turn.actionType(), id(value)));
        }
        return state;
    }

    private Phase13GASyntheticContextFactory.SyntheticContext requiredContext(String id) {
        Phase13GASyntheticContextFactory.SyntheticContext value = contexts.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown synthetic context: " + id);
        return value;
    }

    private String canonicalDraft(FinalDraftResult result, List<CandidateTrace> trace) {
        String decisions = result.decisions().stream().map(decision -> decision.turn() + ":" + decision.side()
                + ":" + decision.actionType() + ":" + decision.selectedChampionId().value() + ":"
                + Double.toString(decision.immediateScore()) + ":" + Double.toString(decision.continuationScore())
                + ":" + Double.toString(decision.finalSearchScore()) + ":" + componentSignature(decision.componentBreakdown())
                + ":" + decision.preferredPlan() + ":" + Double.toString(decision.preferredPlanViability()) + ":"
                + decision.topAlternatives().stream().map(value -> value.championId().value() + "="
                        + Double.toString(value.finalSearchScore())).collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n"));
        String roles = Streamable(result.blueFinalRoleAssignments()) + "|" + Streamable(result.redFinalRoleAssignments());
        String assignments = result.matchChampionAssignments().asMap().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing((PlayerKey key) -> key.side().name())
                        .thenComparing(key -> key.position().name())))
                .map(entry -> entry.getKey().side() + ":" + entry.getKey().position() + ":"
                        + entry.getValue().championId().value() + ":" + entry.getValue().selectedPosition())
                .collect(Collectors.joining("|"));
        return result.blueBans().stream().map(ChampionId::value).collect(Collectors.joining(",")) + ";"
                + result.redBans().stream().map(ChampionId::value).collect(Collectors.joining(",")) + ";"
                + result.bluePicks().stream().map(ChampionId::value).collect(Collectors.joining(",")) + ";"
                + result.redPicks().stream().map(ChampionId::value).collect(Collectors.joining(",")) + ";"
                + decisions + ";" + roles + ";" + assignments + ";"
                + portfolioSignature(result.blueInitialPortfolio()) + ";" + portfolioSignature(result.redInitialPortfolio()) + ";"
                + portfolioSignature(result.blueFinalPortfolio()) + ";" + portfolioSignature(result.redFinalPortfolio()) + ";"
                + result.hardFearlessExclusions().stream().map(ChampionId::value).sorted().collect(Collectors.joining(",")) + ";"
                + trace.stream().map(value -> value.turn() + ":" + String.join(",", value.candidates()) + ":"
                        + value.portfolio() + ":" + value.selected() + ":" + value.componentValues())
                .collect(Collectors.joining("\n"));
    }

    private static String Streamable(Map<ChampionId, Position> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ChampionId::value)))
                .map(entry -> entry.getKey().value() + "=" + entry.getValue()).collect(Collectors.joining("|"));
    }

    private static String assignmentSignature(RoleAssignmentSolver.RoleAssignment assignment) {
        return Streamable(assignment.positions());
    }

    private static String componentSignature(Map<String, Double> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + Double.toString(entry.getValue())).collect(Collectors.joining("|"));
    }

    private static String portfolioSignature(DraftPlanPortfolio portfolio) {
        return portfolio.plans().stream().map(plan -> plan.archetype().name() + "=" + Double.toString(plan.viability())
                + "[core=" + plan.coreCandidates().stream().map(ChampionId::value).sorted().collect(Collectors.joining(","))
                + ";missing=" + plan.missingCapabilities().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + Double.toString(entry.getValue()))
                .collect(Collectors.joining(",")) + "]").collect(Collectors.joining("|"));
    }

    private static String timelineDigest(MatchTimeline timeline) {
        try {
            ObjectMapper mapper = new ObjectMapper().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            return sha256(mapper.writeValueAsString(timeline));
        } catch (IOException error) {
            throw new IllegalStateException("Unable to serialize timeline", error);
        }
    }

    private static String resourceHash(String resource) {
        try (InputStream input = Phase13GAStructuralIntegratedAudit.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing resource: " + resource);
            return sha256(input.readAllBytes());
        } catch (IOException error) {
            throw new IllegalStateException("Unable to hash resource: " + resource, error);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? map.entrySet().stream().collect(Collectors.toMap(
                entry -> String.valueOf(entry.getKey()), entry -> entry.getValue(), (a, b) -> a, TreeMap::new)) : Map.of();
    }

    private static double number(Object value) { return value instanceof Number number ? number.doubleValue() : 0.0; }

    private record CaseInput(String caseId, String blueContextId, String redContextId, SeriesDraftHistory history) { }
}
