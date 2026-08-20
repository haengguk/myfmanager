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
 * Full Phase 13G-A2 audit runner.  This class is test-side by design: it
 * observes the frozen production path and does not change draft semantics.
 */
public final class Phase13GA2StructuralIntegratedAudit {
    public static final String PHASE = "PHASE_13G_A2_STRUCTURAL_AUDIT";
    public static final String AUDIT_VERSION = "13G-A-STRUCTURAL-INTEGRATED-AUDIT-V2";
    public static final String PREDECESSOR_AUDIT_VERSION = "13G-A-STRUCTURAL-INTEGRATED-AUDIT-V1";
    public static final String PREDECESSOR_VERDICT = "PHASE_13G_A_STRUCTURAL_INTEGRATED_AUDIT_COMPLETE_WITH_REVIEWS";
    public static final String OUTPUT_DIRECTORY = "build/reports/phase13g-a-v2";
    public static final String DRAFT_META_HASH =
            "dd1173aadfad92d4ec231f097653ac840809c60812a4920d32b3d9606fa7fe99";
    public static final String LEGAL_ROLE_HASH =
            "18036bba3ec815a732d251e82cdc72d7d6dbed0f9fc3b373b2840da936b72b8e";
    public static final String COMPOSITION_HASH =
            "23d616cab6abea69d5ad783f405b0b4518a14608b0be4eac3d53f669acab6877";
    public static final int GAME_ONE_REPLAY_CASES = 24;
    public static final int SERIES_REPLAY_CASES = 3;
    public static final int GAME_ONE_CASE_COUNT = 120;
    public static final int GAME_ONE_UNORDERED_PAIR_COUNT = 60;
    public static final int FEARLESS_SERIES_COUNT = 12;
    public static final int LATER_FEARLESS_DRAFT_COUNT = 48;
    public static final int INTEGRATION_DRAFT_COUNT = 20;
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
    private final Phase13GA2AuditSchedule.Schedule schedule;
    private final DraftEngine engine;
    private final RoleAssignmentSolver roles;
    private final DraftAvailability availability;
    private final DraftCompositionEvaluator composition;
    private final DraftMatchupEvaluator matchup;
    private final PreDraftPlanner planner;
    private final DraftCandidateGenerator candidates;
    private final DraftScoringPolicy policy;
    private final ObjectMapper mapper;

    public Phase13GA2StructuralIntegratedAudit() {
        resources = DraftResourceSet.loadDefault();
        List<Phase13GASyntheticContextFactory.SyntheticContext> values =
                Phase13GASyntheticContextFactory.create(resources);
        contexts = values.stream().collect(Collectors.toUnmodifiableMap(
                Phase13GASyntheticContextFactory.SyntheticContext::id, value -> value));
        schedule = Phase13GA2AuditSchedule.freeze(values);
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

    public DraftAudit auditSingle(String caseId, String blueContextId, String redContextId,
                                  SeriesDraftHistory history) {
        return executeDraft(caseId, blueContextId, redContextId, history);
    }

    public List<Map<String, Object>> candidateCoverageFor(List<DraftAudit> audits) {
        return candidateCoverage(audits);
    }

    public List<ComponentDistribution> componentDistributionFor(String scope, List<DraftAudit> audits) {
        return componentDistribution(scope, audits);
    }

    public static List<DraftAudit> laterFearlessDraftsForAudit(List<FearlessSeriesAudit> series) {
        return series.stream().flatMap(value -> value.games().stream().skip(1)).toList();
    }

    public static boolean candidateStarvationEligible(Map<String, Object> row) {
        return ((Number) row.getOrDefault("highProficiencyContextCount", 0)).intValue() > 0
                && ((Number) row.getOrDefault("candidateAppearanceCount", 0)).intValue() == 0;
    }

    public static void main(String[] args) throws Exception {
        Phase13GA2StructuralIntegratedAudit audit = new Phase13GA2StructuralIntegratedAudit();
        AuditRun result = audit.run();
        Path output = Path.of(System.getProperty("phase13g.outputDir", OUTPUT_DIRECTORY));
        Phase13GA2AuditArtifactWriter.write(result, output);
        System.out.println("PHASE13G_A2_VERDICT=" + result.summary().get("verdict"));
        System.out.println("PHASE13G_A2_GAME1_CASES=" + result.gameOneDrafts().size());
        System.out.println("PHASE13G_A2_FEARLESS_SERIES=" + result.fearlessSeries().size());
        System.out.println("PHASE13G_A2_INTEGRATION_MATCHES=" + result.integrations().size());
        System.out.println("PHASE13G_A2_REVIEWS=" + result.reviewCodes());
        System.out.println("PHASE13G_A2_BLOCKERS=" + result.blockerCodes());
    }

    public AuditRun run() {
        Instant started = Instant.now();
        StaticIntegrity staticIntegrity = inspectStaticIntegrity();
        List<DraftAudit> gameOne = runGameOneSchedule();
        List<FearlessSeriesAudit> fearless = runFearlessSchedule();
        List<DraftAudit> controlledDrafts = runControlledSchedule();
        int draftReplayMismatches = replayGameOne(gameOne);
        int seriesReplayMismatches = replaySeries(fearless);
        GameOneDistribution distribution = aggregateGameOne(gameOne);
        List<DraftAudit> laterFearlessDrafts = fearless.stream()
                .flatMap(series -> series.games().stream().skip(1)).toList();
        List<ComponentDistribution> componentDistribution = new ArrayList<>();
        componentDistribution.addAll(componentDistribution("GAME1", gameOne));
        componentDistribution.addAll(componentDistribution("LATER_FEARLESS", laterFearlessDrafts));
        List<Map<String, Object>> candidateCoverage = candidateCoverage(gameOne);
        ControlledProbeResult probes = controlledProbes(distribution, componentDistribution);
        List<IntegrationAudit> integrations = runIntegrationProbes(gameOne, fearless);
        int matchReplayMismatches = (int) integrations.stream()
                .filter(IntegrationAudit::replayMismatch).count();
        Map<String, Object> serialEngineOnly = serialEngineOnlyProbes(gameOne, fearless);

        List<String> blockers = new ArrayList<>(staticIntegrity.blockers());
        if (!scheduleShapeValid()) blockers.add("BLOCKED_BY_PHASE_13G_A_V2_SCHEDULE");
        if (gameOne.size() != GAME_ONE_CASE_COUNT) blockers.add("BLOCKED_BY_PHASE_13G_A_V2_GAME1_CASE_COUNT");
        if (gameOne.stream().anyMatch(value -> !value.success())) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_V2_DRAFT_LEGALITY");
        }
        if (fearless.size() != FEARLESS_SERIES_COUNT
                || fearless.stream().anyMatch(value -> !value.complete())) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_V2_HARD_FEARLESS_COMPLETION");
        }
        if (laterFearlessDrafts.size() != LATER_FEARLESS_DRAFT_COUNT) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_V2_LATER_FEARLESS_SAMPLE");
        }
        if (draftReplayMismatches + seriesReplayMismatches > 0) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_V2_DETERMINISM");
        }
        if (controlledDrafts.size() != schedule.controlledProbes().size()
                || controlledDrafts.stream().anyMatch(value -> !value.success())) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_V2_CONTROLLED_PROBE");
        }
        if (candidateCoverage.size() != resources.champions().catalog().all().size()) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_V2_CANDIDATE_COVERAGE");
        }
        if (integrations.size() != INTEGRATION_SEEDS.size() * 20
                || integrations.stream().anyMatch(value -> !value.success())) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_V2_MATCH_INTEGRATION");
        }
        long nonFinite = componentDistribution.stream()
                .mapToLong(ComponentDistribution::nonFiniteCount).sum();
        if (nonFinite > 0) blockers.add("BLOCKED_BY_PHASE_13G_A_V2_NONFINITE_SCORE");
        if (integrations.stream().anyMatch(IntegrationAudit::replayMismatch)) {
            blockers.add("BLOCKED_BY_PHASE_13G_A_V2_MATCH_REPLAY");
        }

        List<ReviewDetail> reviewDetails = reviewDetails(distribution, componentDistribution,
                probes, candidateCoverage, gameOne, serialEngineOnly);
        List<String> infoCodes = infoCodes(distribution, candidateCoverage, componentDistribution);
        List<String> reviews = reviewCodes(reviewDetails);
        List<String> uniqueBlockers = blockers.stream().distinct().sorted().toList();
        List<String> uniqueReviews = reviews.stream().distinct().sorted().toList();
        Map<String, Object> summary = summary(staticIntegrity, distribution, componentDistribution,
                probes, controlledDrafts, candidateCoverage, gameOne, fearless, integrations,
                serialEngineOnly, draftReplayMismatches, seriesReplayMismatches,
                matchReplayMismatches, infoCodes, uniqueReviews, uniqueBlockers,
                Duration.between(started, Instant.now()).toMillis());
        return new AuditRun(resources, contexts.values().stream()
                .sorted(Comparator.comparing(Phase13GASyntheticContextFactory.SyntheticContext::id)).toList(),
                schedule, staticIntegrity.values(), gameOne, fearless, distribution,
                componentDistribution, probes.values(), controlledDrafts, candidateCoverage,
                integrations, reviewDetails, infoCodes, uniqueReviews, uniqueBlockers, summary);
    }

    public record AuditRun(
            DraftResourceSet resources,
            List<Phase13GASyntheticContextFactory.SyntheticContext> syntheticContexts,
            Phase13GA2AuditSchedule.Schedule schedule,
            Map<String, Object> staticIntegrity,
            List<DraftAudit> gameOneDrafts,
            List<FearlessSeriesAudit> fearlessSeries,
            GameOneDistribution gameOneDistribution,
            List<ComponentDistribution> componentDistribution,
            Map<String, Object> controlledProbes,
            List<DraftAudit> controlledDrafts,
            List<Map<String, Object>> candidateCoverage,
            List<IntegrationAudit> integrations,
            List<ReviewDetail> reviewDetails,
            List<String> infoCodes,
            List<String> reviewCodes,
            List<String> blockerCodes,
            Map<String, Object> summary
    ) {
        public AuditRun {
            syntheticContexts = List.copyOf(syntheticContexts);
            gameOneDrafts = List.copyOf(gameOneDrafts);
            fearlessSeries = List.copyOf(fearlessSeries);
            componentDistribution = List.copyOf(componentDistribution);
            controlledDrafts = List.copyOf(controlledDrafts);
            candidateCoverage = List.copyOf(candidateCoverage);
            integrations = List.copyOf(integrations);
            reviewDetails = List.copyOf(reviewDetails);
            infoCodes = List.copyOf(infoCodes);
            reviewCodes = List.copyOf(reviewCodes);
            blockerCodes = List.copyOf(blockerCodes);
        }
    }

    public record CandidateTrace(
            int turn,
            String side,
            String actionType,
            int rawLegalActionCandidateCount,
            int generatedShortlistCount,
            List<String> candidates,
            boolean selectedInsideGeneratedShortlist,
            int rawAvailableChampionCount,
            int rawAvailableLegalRoleKeyCount,
            int structuralRepairCandidateCount,
            int ordinaryRankedCandidateCount,
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
            long engineDraftMillis,
            long validationMillis,
            long totalAuditCaseMillis,
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
            String scope,
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
            String source,
            String seriesId,
            int gameNumber,
            String blueContextId,
            String redContextId,
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

    public record ReviewDetail(String code, String subject, String scope, Object value, Object threshold, String explanation) { }


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

    private boolean scheduleShapeValid() {
        Set<String> ordered = new HashSet<>();
        Set<String> unordered = new HashSet<>();
        for (Phase13GA2AuditSchedule.GameOneCase value : schedule.gameOneCases()) {
            if (value.blueContextId().equals(value.redContextId()) || !ordered.add(value.orderedKey())) return false;
            String first = value.blueContextId().compareTo(value.redContextId()) <= 0
                    ? value.blueContextId() : value.redContextId();
            String second = value.blueContextId().compareTo(value.redContextId()) <= 0
                    ? value.redContextId() : value.blueContextId();
            if (!unordered.add(first + "|" + second)) {
                // Each unordered pair is intentionally represented by two ordered cases.
                long occurrences = schedule.gameOneCases().stream().filter(candidate -> {
                    String a = candidate.blueContextId().compareTo(candidate.redContextId()) <= 0
                            ? candidate.blueContextId() : candidate.redContextId();
                    String b = candidate.blueContextId().compareTo(candidate.redContextId()) <= 0
                            ? candidate.redContextId() : candidate.blueContextId();
                    return (a + "|" + b).equals(first + "|" + second);
                }).count();
                if (occurrences != 2) return false;
            }
        }
        return schedule.permutation().size() == 24
                && schedule.unorderedPairs().size() == GAME_ONE_UNORDERED_PAIR_COUNT
                && schedule.gameOneCases().size() == GAME_ONE_CASE_COUNT
                && schedule.fearlessSeries().size() == FEARLESS_SERIES_COUNT
                && schedule.fearlessSeries().stream().map(value -> value.blueContextId() + "|" + value.redContextId())
                .distinct().count() == FEARLESS_SERIES_COUNT;
    }

    private List<DraftAudit> runControlledSchedule() {
        return schedule.controlledProbes().stream()
                .map(value -> executeDraft("controlled-" + value.probeId(), value.blueContextId(),
                        value.redContextId(), new SeriesDraftHistory()))
                .sorted(Comparator.comparing(DraftAudit::caseId)).toList();
    }

    private List<DraftAudit> runGameOneSchedule() {
        List<Phase13GA2AuditSchedule.GameOneCase> cases = schedule.gameOneCases();
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
        long engineStarted = System.nanoTime();
        try {
            FinalDraftResult result = engine.draft(blue.draftContext(), red.draftContext(), history);
            long engineDraftMillis = elapsedMillis(engineStarted);
            long validationStarted = System.nanoTime();
            Validation validation = validateDraft(id, blue, red, history, result);
            long validationMillis = elapsedMillis(validationStarted);
            long totalAuditCaseMillis = engineDraftMillis + validationMillis;
            String digest = validation.violations().isEmpty() ? canonicalDraft(result, validation.trace()) :
                    "INVALID:" + String.join("|", validation.violations());
            return new DraftAudit(id, blueContextId, redContextId, result, validation.trace(),
                    validation.violations(), engineDraftMillis, validationMillis, totalAuditCaseMillis, digest,
                    validation.preferredPlans(), validation.secondaryPlans(), validation.fallbackPlans(),
                    validation.pivotByCause());
        } catch (RuntimeException error) {
            return failedAudit(id, blueContextId, redContextId, error);
        }
    }

    private DraftAudit failedAudit(String id, String blueContextId, String redContextId, Throwable error) {
        String detail = error.getClass().getSimpleName() + ":" + String.valueOf(error.getMessage());
        return new DraftAudit(id, blueContextId, redContextId, null, List.of(), List.of(detail), 0L, 0L, 0L,
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
            DraftState traceState = state;
            List<ChampionId> generated = candidates.generate(state, own, enemy, ownPortfolio, enemyPortfolio);
            List<ChampionId> rawLegal = rawLegalCandidates(state, side);
            int rawAvailableChampionCount = resources.champions().catalog().all().size() - state.unavailableChampions().size();
            int rawAvailableLegalRoleKeyCount = (int) resources.champions().catalog().legalRoleKeys().stream()
                    .filter(key -> !traceState.unavailableChampions().contains(key.championId())).count();
            List<ChampionId> repairCandidates = rawLegal.stream().filter(value -> turn.actionType() == DraftActionType.PICK)
                    .sorted(Comparator.comparingDouble((ChampionId value) -> composition.repairValue(traceState.picks(side), traceState.picks(side.opposite()), value, own, enemy))
                            .reversed().thenComparing(ChampionId::value)).limit(policy.structuralRepairSlots()).toList();
            int structuralRepairCandidateCount = (int) generated.stream().filter(repairCandidates::contains).count();
            int ordinaryRankedCandidateCount = Math.max(0, generated.size() - structuralRepairCandidateCount);
            if (rawLegal.size() < generated.size()) violations.add("RAW_LEGAL_BELOW_SHORTLIST:" + turn.number());
            if (generated.isEmpty()) violations.add("CANDIDATE_EMPTY:" + turn.number());
            if (generated.size() > policy.candidateLimit()) violations.add("SHORTLIST_ABOVE_PRODUCTION_LIMIT:" + turn.number());
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
            trace.add(new CandidateTrace(turn.number(), side.name(), turn.actionType().name(), rawLegal.size(), generated.size(),
                     generated.stream().map(ChampionId::value).toList(), generated.contains(decision.selectedChampionId()), rawAvailableChampionCount, rawAvailableLegalRoleKeyCount, structuralRepairCandidateCount, ordinaryRankedCandidateCount, portfolioSignature(ownPortfolio),
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

    private List<ChampionId> rawLegalCandidates(DraftState state, TeamSide side) {
        return resources.champions().catalog().all().stream().map(ChampionDefinition::id)
                .filter(id -> !state.unavailableChampions().contains(id))
                .filter(id -> state.currentTurn().actionType() == DraftActionType.BAN
                        || (roles.isFeasible(append(state.picks(side), id)) && availability.canComplete(state, side, id)))
                .sorted(Comparator.comparing(ChampionId::value)).toList();
    }

    private static List<ChampionId> append(List<ChampionId> values, ChampionId value) {
        List<ChampionId> result = new ArrayList<>(values);
        result.add(value);
        return result;
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

    private List<Map<String, Object>> candidateCoverage(List<DraftAudit> audits) {
        List<String> championIds = resources.champions().catalog().all().stream().map(ChampionDefinition::id)
                .map(ChampionId::value).sorted().toList();
        Map<String, Integer> picks = new TreeMap<>(), bans = new TreeMap<>(), presence = new TreeMap<>();
        Map<String, Integer> candidate = new TreeMap<>(), pickCandidate = new TreeMap<>(), banCandidate = new TreeMap<>(), cases = new TreeMap<>();
        Map<String, Integer> highContexts = new TreeMap<>(), highSlots = new TreeMap<>(), rolesCount = new TreeMap<>();
        Map<String, Set<String>> candidateCases = new TreeMap<>();
        Map<String, Set<String>> roleKeys = new TreeMap<>();
        championIds.forEach(id -> { picks.put(id, 0); bans.put(id, 0); presence.put(id, 0); candidate.put(id, 0);
            pickCandidate.put(id, 0); banCandidate.put(id, 0); cases.put(id, 0); highContexts.put(id, 0); highSlots.put(id, 0); rolesCount.put(id, 0);
            candidateCases.put(id, new HashSet<>()); roleKeys.put(id, new HashSet<>()); });
        for (Phase13GASyntheticContextFactory.SyntheticContext context : contexts.values()) {
            for (String id : championIds) {
                boolean high = resources.champions().catalog().get(new ChampionId(id)).supportedPositions().stream()
                        .map(position -> new ChampionRoleKey(new ChampionId(id), position))
                        .anyMatch(key -> context.proficiencyByRole().getOrDefault(key, 0) >= 17);
                if (high) highContexts.merge(id, 1, Integer::sum);
            }
        }
        for (DraftAudit audit : audits) {
            if (!audit.success()) continue;
            Phase13GASyntheticContextFactory.SyntheticContext blue = requiredContext(audit.blueContextId());
            Phase13GASyntheticContextFactory.SyntheticContext red = requiredContext(audit.redContextId());
            for (String id : championIds) {
                ChampionId champion = new ChampionId(id);
                boolean blueHigh = championPositionsHigh(blue, champion);
                boolean redHigh = championPositionsHigh(red, champion);
                if (blueHigh) highSlots.merge(id, 1, Integer::sum);
                if (redHigh) highSlots.merge(id, 1, Integer::sum);
            }
            Set<String> casePresence = new HashSet<>();
            audit.candidateTrace().forEach(trace -> trace.candidates().forEach(id -> {
                candidate.merge(id, 1, Integer::sum);
                candidateCases.computeIfAbsent(id, ignored -> new HashSet<>()).add(audit.caseId());
                if (trace.actionType().equals(DraftActionType.PICK.name())) pickCandidate.merge(id, 1, Integer::sum);
                else banCandidate.merge(id, 1, Integer::sum);
            }));
            audit.result().bluePicks().forEach(id -> { picks.merge(id.value(), 1, Integer::sum); casePresence.add(id.value()); });
            audit.result().redPicks().forEach(id -> { picks.merge(id.value(), 1, Integer::sum); casePresence.add(id.value()); });
            audit.result().blueBans().forEach(id -> { bans.merge(id.value(), 1, Integer::sum); casePresence.add(id.value()); });
            audit.result().redBans().forEach(id -> { bans.merge(id.value(), 1, Integer::sum); casePresence.add(id.value()); });
            casePresence.forEach(id -> presence.merge(id, 1, Integer::sum));
            for (Map.Entry<ChampionId, Position> entry : audit.result().blueFinalRoleAssignments().entrySet()) {
                rolesCount.merge(entry.getKey().value(), 1, Integer::sum);
                roleKeys.computeIfAbsent(entry.getKey().value(), ignored -> new HashSet<>()).add(entry.getKey().value() + ":" + entry.getValue());
            }
            for (Map.Entry<ChampionId, Position> entry : audit.result().redFinalRoleAssignments().entrySet()) {
                rolesCount.merge(entry.getKey().value(), 1, Integer::sum);
                roleKeys.computeIfAbsent(entry.getKey().value(), ignored -> new HashSet<>()).add(entry.getKey().value() + ":" + entry.getValue());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : championIds) {
            int candidateCount = candidate.get(id);
            int selected = picks.get(id) + bans.get(id);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("championId", id); row.put("pickOccurrences", picks.get(id)); row.put("banOccurrences", bans.get(id));
            row.put("pickOrBanPresence", presence.get(id)); row.put("candidateAppearanceCount", candidateCount);
            row.put("pickCandidateAppearanceCount", pickCandidate.get(id)); row.put("banCandidateAppearanceCount", banCandidate.get(id));
            row.put("candidateCasePresence", candidateCases.getOrDefault(id, Set.of()).size());
            row.put("highProficiencyContextCount", highContexts.get(id)); row.put("highProficiencyTeamSlotCount", highSlots.get(id));
            row.put("roleAssignmentOccurrences", rolesCount.get(id)); row.put("roleAssignmentKeys", roleKeys.getOrDefault(id, Set.of()).stream().sorted().toList());
            row.put("candidateAppearanceRate", audits.isEmpty() ? 0.0 : candidateCases.getOrDefault(id, Set.of()).size() / (double) audits.size());
            row.put("selectedFromCandidateRate", candidateCount == 0 ? 0.0 : selected / (double) candidateCount);
            result.add(row);
        }
        return result;
    }

    private boolean championPositionsHigh(Phase13GASyntheticContextFactory.SyntheticContext context, ChampionId champion) {
        return resources.champions().catalog().get(champion).supportedPositions().stream()
                .map(position -> new ChampionRoleKey(champion, position))
                .anyMatch(key -> context.proficiencyByRole().getOrDefault(key, 0) >= 17);
    }


    private List<ComponentDistribution> componentDistribution(String scope, List<DraftAudit> audits) {
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
            result.add(stats(scope, parts[0], parts[1], sample));
        });
        return result;
    }

    private ComponentDistribution stats(String scope, String actionType, String component, List<Double> sample) {
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
        return new ComponentDistribution(scope, actionType, component, sample.size(), min, max, mean, median, p10, p90,
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

    public record IntegrationSelection(String draftId, String source, String seriesId, int gameNumber,
                                        String blueContextId, String redContextId) { }

    public List<IntegrationSelection> plannedIntegrationSelections() {
        List<IntegrationSelection> result = new ArrayList<>();
        for (Phase13GA2AuditSchedule.FearlessSeriesCase series : schedule.fearlessSeries()) {
            String first = series.blueContextId().compareTo(series.redContextId()) <= 0
                    ? series.blueContextId() : series.redContextId();
            Phase13GA2AuditSchedule.GameOneCase selected = schedule.gameOneCases().stream()
                    .filter(value -> value.blueContextId().equals(first)
                            && normalizedPair(value.blueContextId(), value.redContextId())
                            .equals(series.unorderedKey()))
                    .findFirst().orElseThrow();
            result.add(new IntegrationSelection("game1-" + selected.caseId(), "GAME1", "", 1,
                    selected.blueContextId(), selected.redContextId()));
        }
        List<Integer> laterGames = List.of(2, 3, 4, 5, 2, 3, 4, 5);
        List<Phase13GA2AuditSchedule.FearlessSeriesCase> series = schedule.fearlessSeries();
        for (int index = 0; index < laterGames.size(); index++) {
            Phase13GA2AuditSchedule.FearlessSeriesCase value = series.get(index);
            int gameNumber = laterGames.get(index);
            result.add(new IntegrationSelection("fearless-" + value.seriesId() + "-game-" + gameNumber,
                    "LATER_FEARLESS", value.seriesId(), gameNumber,
                    value.blueContextId(), value.redContextId()));
        }
        return List.copyOf(result);
    }

    private List<IntegrationAudit> runIntegrationProbes(List<DraftAudit> gameOne,
                                                         List<FearlessSeriesAudit> fearless) {
        Map<String, DraftAudit> gameOneById = gameOne.stream()
                .collect(Collectors.toMap(DraftAudit::caseId, value -> value, (left, right) -> left));
        Map<String, FearlessSeriesAudit> seriesById = fearless.stream()
                .collect(Collectors.toMap(FearlessSeriesAudit::seriesId, value -> value, (left, right) -> left));
        List<IntegrationAudit> result = new ArrayList<>();
        int index = 0;
        for (IntegrationSelection selection : plannedIntegrationSelections()) {
            FinalDraftResult draftResult = null;
            if (selection.source().equals("GAME1")) {
                String caseId = selection.draftId().substring("game1-".length());
                DraftAudit audit = gameOneById.get(caseId);
                draftResult = audit == null ? null : audit.result();
            } else {
                FearlessSeriesAudit series = seriesById.get(selection.seriesId());
                if (series != null && series.games().size() >= selection.gameNumber()) {
                    draftResult = series.games().get(selection.gameNumber() - 1).result();
                }
            }
            IntegrationDraft draft = new IntegrationDraft(selection, draftResult);
            for (long seed : INTEGRATION_SEEDS) {
                result.add(integrate(draft, seed, index++));
            }
        }
        return List.copyOf(result);
    }

    private record IntegrationDraft(IntegrationSelection selection, FinalDraftResult result) { }

    private IntegrationAudit integrate(IntegrationDraft draft, long seed, int index) {
        IntegrationSelection selection = draft.selection();
        List<String> violations = new ArrayList<>();
        if (draft.result() == null) {
            violations.add("MISSING_DRAFT_RESULT");
            return new IntegrationAudit(selection.draftId(), selection.source(), selection.seriesId(), selection.gameNumber(),
                    selection.blueContextId(), selection.redContextId(), normalizedPair(selection.blueContextId(), selection.redContextId()),
                    seed, false, false, 0, "", "", violations);
        }
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
            Phase13GASyntheticContextFactory.SyntheticContext blue = requiredContext(selection.blueContextId());
            Phase13GASyntheticContextFactory.SyntheticContext red = requiredContext(selection.redContextId());
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
        return new IntegrationAudit(selection.draftId(), selection.source(), selection.seriesId(), selection.gameNumber(),
                selection.blueContextId(), selection.redContextId(), normalizedPair(selection.blueContextId(), selection.redContextId()), seed,
                violations.isEmpty() && !replayMismatch, replayMismatch, duration, winner, digest, violations);
    }

    private static String normalizedPair(String left, String right) {
        return left.compareTo(right) <= 0 ? left + "|" + right : right + "|" + left;
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

    private Map<String, Object> serialEngineOnlyProbes(List<DraftAudit> gameOne,
                                                          List<FearlessSeriesAudit> fearless) {
        Map<String, Object> result = new TreeMap<>();
        List<IntegrationSelection> selections = plannedIntegrationSelections();
        IntegrationSelection gameOneSelection = selections.stream()
                .filter(value -> value.source().equals("GAME1")).findFirst().orElseThrow();
        long gameOneMillis = measureEngineOnly(gameOneSelection.blueContextId(),
                gameOneSelection.redContextId(), new SeriesDraftHistory());
        result.put("serialEngineOnlyGame1Millis", gameOneMillis);

        IntegrationSelection laterSelection = selections.stream()
                .filter(value -> value.source().equals("LATER_FEARLESS")).findFirst().orElseThrow();
        FearlessSeriesAudit series = fearless.stream()
                .filter(value -> value.seriesId().equals(laterSelection.seriesId())).findFirst().orElse(null);
        long laterMillis = -1L;
        if (series != null && !series.games().isEmpty() && series.games().getFirst().result() != null) {
            SeriesDraftHistory history = new SeriesDraftHistory();
            history.commitCompleted(series.games().getFirst().result());
            laterMillis = measureEngineOnly(laterSelection.blueContextId(),
                    laterSelection.redContextId(), history);
        }
        result.put("serialEngineOnlyLaterFearlessMillis", laterMillis);
        result.put("executionMode", "SERIAL_ENGINE_ONLY");
        return result;
    }

    private long measureEngineOnly(String blueContextId, String redContextId, SeriesDraftHistory history) {
        long started = System.nanoTime();
        engine.draft(requiredContext(blueContextId).draftContext(),
                requiredContext(redContextId).draftContext(), history);
        return elapsedMillis(started);
    }

    private List<ReviewDetail> reviewDetails(GameOneDistribution distribution,
                                                   List<ComponentDistribution> components,
                                                   ControlledProbeResult probes,
                                                   List<Map<String, Object>> coverage,
                                                   List<DraftAudit> gameOne,
                                                   Map<String, Object> serialEngineOnly) {
        List<ReviewDetail> result = new ArrayList<>();
        int caseCount = ((Number) distribution.metrics().getOrDefault("caseCount", 0)).intValue();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(distribution.pickOrBanPresence()).entrySet()) {
            double share = entry.getValue() / (double) Math.max(1, caseCount);
            if (share >= .90) {
                result.add(new ReviewDetail("REVIEW_EXTREME_DRAFT_LOCK", entry.getKey(), "GAME1",
                        share, .90, "Pick/ban presence reaches the V2 extreme-lock threshold."));
            } else if (share >= .75) {
                result.add(new ReviewDetail("REVIEW_HIGH_DRAFT_LOCK", entry.getKey(), "GAME1",
                        share, .75, "Pick/ban presence reaches the V2 high-lock threshold."));
            }
        }
        for (Map.Entry<String, Integer> entry : new TreeMap<>(distribution.roleAssignmentOccurrences()).entrySet()) {
            int split = entry.getKey().lastIndexOf(':');
            String champion = split < 0 ? entry.getKey() : entry.getKey().substring(0, split);
            String role = split < 0 ? "UNKNOWN" : entry.getKey().substring(split + 1);
            int total = distribution.roleAssignmentOccurrences().entrySet().stream()
                    .filter(value -> value.getKey().endsWith(":" + role))
                    .mapToInt(Map.Entry::getValue).sum();
            double share = entry.getValue() / (double) Math.max(1, total);
            if (share >= .50) {
                result.add(new ReviewDetail("REVIEW_EXTREME_ROLE_CONCENTRATION", champion + ":" + role,
                        "GAME1", share, .50, "Role assignment concentration reaches the extreme threshold."));
            } else if (share >= .35) {
                result.add(new ReviewDetail("REVIEW_ROLE_CONCENTRATION", champion + ":" + role,
                        "GAME1", share, .35, "Role assignment concentration reaches the review threshold."));
            }
        }
        for (Map<String, Object> row : coverage) {
            int highContexts = ((Number) row.getOrDefault("highProficiencyContextCount", 0)).intValue();
            int candidateCount = ((Number) row.getOrDefault("candidateAppearanceCount", 0)).intValue();
            if (highContexts > 0 && candidateCount == 0) {
                result.add(new ReviewDetail("REVIEW_SYSTEMIC_CANDIDATE_STARVATION",
                        String.valueOf(row.get("championId")), "GAME1",
                        Map.of("highProficiencyContextCount", highContexts,
                                "candidateAppearanceCount", candidateCount), 1,
                        "A synthetic high-proficiency opportunity has no generated candidate appearance."));
            }
        }
        for (ComponentDistribution component : components) {
            if (component.sampleCount() == 0 || component.nonFiniteCount() != 0
                    || component.min() != component.max()) continue;
            boolean saturatedFutureFeasibility = component.actionType().equals("PICK")
                    && component.component().equals("FUTURE_FEASIBILITY")
                    && component.min() == 20.0;
            if (!saturatedFutureFeasibility) {
                result.add(new ReviewDetail(
                        "REVIEW_SELECTED_COMPONENT_CONSTANT:" + component.scope() + ":"
                                + component.actionType() + ":" + component.component(),
                        component.component(), component.scope(), component.min(), component.min(),
                        "Selected-decision component is constant in this audit scope; inspect production semantics before tuning."));
            }
        }
        if (!probes.planPivot()) {
            result.add(new ReviewDetail("REVIEW_PLAN_PIVOT_INERT", "plan-pivot", "CONTROLLED_PROBES",
                    false, true, "The controlled plan-pivot scenario did not change the preferred plan."));
        }
        Object preferred = distribution.metrics().get("finalPreferredPlanFrequency");
        if (preferred instanceof Map<?, ?> values && values.size() == 1) {
            result.add(new ReviewDetail("REVIEW_PLAN_ARCHETYPE_COLLAPSE", "finalPreferredPlan", "GAME1",
                    values, 2, "Only one final preferred-plan key was observed across both sides."));
        }
        for (Map.Entry<String, Object> entry : serialEngineOnly.entrySet()) {
            if (!entry.getKey().startsWith("serialEngineOnly") || !(entry.getValue() instanceof Number number)
                    || number.longValue() <= 30_000L) continue;
            result.add(new ReviewDetail("REVIEW_ENGINE_ONLY_LATENCY_OUTLIER", entry.getKey(), "SERIAL_ENGINE_ONLY",
                    number.longValue(), 30_000L, "Serial engine-only draft time exceeded the review threshold."));
        }
        return result;
    }

    private List<String> reviewCodes(List<ReviewDetail> details) {
        return details.stream().map(ReviewDetail::code).distinct().sorted().toList();
    }

    private List<String> infoCodes(GameOneDistribution distribution,
                                   List<Map<String, Object>> coverage,
                                   List<ComponentDistribution> components) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add("INFO_SYNTHETIC_CONTEXTS_ARE_NOT_REAL_PLAYER_DATA");
        for (Map<String, Object> row : coverage) {
            int picks = ((Number) row.getOrDefault("pickOccurrences", 0)).intValue();
            if (picks == 0) result.add("INFO_NEVER_SELECTED_CHAMPION:" + row.get("championId"));
        }
        for (ComponentDistribution component : components) {
            if (component.scope().equals("GAME1") && component.actionType().equals("PICK")
                    && component.component().equals("FUTURE_FEASIBILITY")
                    && component.sampleCount() > 0 && component.min() == component.max()
                    && component.min() == 20.0) {
                result.add("INFO_SELECTED_FUTURE_FEASIBILITY_SATURATED:GAME1");
            }
        }
        return result.stream().sorted().toList();
    }

    public static String computedVerdict(List<String> blockers, List<String> reviews) {
        if (!blockers.isEmpty()) return "PHASE_13G_A_V2_STRUCTURAL_BASELINE_BLOCKED";
        return reviews.isEmpty() ? "PHASE_13G_A_V2_STRUCTURAL_BASELINE_COMPLETE"
                : "PHASE_13G_A_V2_STRUCTURAL_BASELINE_COMPLETE_WITH_REVIEWS";
    }

    private Map<String, Object> summary(StaticIntegrity integrity, GameOneDistribution distribution,
                                        List<ComponentDistribution> components, ControlledProbeResult probes,
                                        List<DraftAudit> controlledDrafts, List<Map<String, Object>> coverage,
                                        List<DraftAudit> gameOne, List<FearlessSeriesAudit> fearless,
                                        List<IntegrationAudit> integrations, Map<String, Object> serialEngineOnly,
                                        int draftReplay, int seriesReplay, int matchReplay,
                                        List<String> infoCodes, List<String> reviews, List<String> blockers,
                                        long wallMillis) {
        Map<String, Object> summary = new TreeMap<>();
        summary.put("phase", PHASE);
        summary.put("auditVersion", AUDIT_VERSION);
        summary.put("predecessorAuditVersion", PREDECESSOR_AUDIT_VERSION);
        summary.put("predecessorVerdict", PREDECESSOR_VERDICT);
        summary.put("timestamp", "INFORMATIONAL_OMITTED_FOR_DETERMINISM");
        summary.put("championVersion", integrity.values().get("championVersion"));
        summary.put("powerVersion", integrity.values().get("powerVersion"));
        summary.put("matchupVersion", integrity.values().get("matchupVersion"));
        summary.put("compositionVersion", integrity.values().get("compositionVersion"));
        summary.put("draftMetaVersion", integrity.values().get("draftMetaVersion"));
        summary.put("championCount", integrity.values().get("championCount"));
        summary.put("legalRoleKeyCount", integrity.values().get("legalRoleKeyCount"));
        summary.put("roleCounts", integrity.values().get("roleCounts"));
        summary.put("draftMetaHash", integrity.values().get("draftMetaHash"));
        summary.put("legalRoleHash", integrity.values().get("legalRoleHash"));
        summary.put("compositionHash", integrity.values().get("compositionHash"));
        summary.put("syntheticContextAlgorithm", Phase13GASyntheticContextFactory.ALGORITHM_VERSION);
        summary.put("syntheticContextCount", contexts.size());
        summary.put("scheduleVersion", Phase13GA2AuditSchedule.SCHEDULE_VERSION);
        summary.put("scheduleHash", schedule.scheduleHash());
        summary.put("game1UnorderedPairCount", schedule.unorderedPairs().size());
        summary.put("game1CaseCount", gameOne.size());
        Map<String, Integer> game1Total = exposure(gameOne, null);
        Map<String, Integer> game1Blue = exposure(gameOne, TeamSide.BLUE);
        Map<String, Integer> game1Red = exposure(gameOne, TeamSide.RED);
        summary.put("game1ContextMinAppearances", minExposure(game1Total));
        summary.put("game1ContextMaxAppearances", maxExposure(game1Total));
        summary.put("game1BlueMinAppearances", minExposure(game1Blue));
        summary.put("game1BlueMaxAppearances", maxExposure(game1Blue));
        summary.put("game1RedMinAppearances", minExposure(game1Red));
        summary.put("game1RedMaxAppearances", maxExposure(game1Red));
        summary.put("game1ContextExposure", game1Total);
        summary.put("game1BlueExposure", game1Blue);
        summary.put("game1RedExposure", game1Red);
        Map<String, Integer> fearlessExposure = fearlessExposure(fearless);
        summary.put("fearlessSeriesCount", fearless.size());
        summary.put("fearlessDraftCount", fearless.stream().mapToInt(value -> value.games().size()).sum());
        summary.put("fearlessContextMinAppearances", minExposure(fearlessExposure));
        summary.put("fearlessContextMaxAppearances", maxExposure(fearlessExposure));
        summary.put("fearlessContextExposure", fearlessExposure);
        summary.put("controlledProbeCount", controlledDrafts.size());
        summary.put("draftCompletionFailures", gameOne.stream().filter(value -> !value.success()).count());
        summary.put("illegalActionCount", gameOne.stream().flatMap(value -> value.violations().stream())
                .filter(value -> value.startsWith("ILLEGAL_ACTION") || value.startsWith("SELECTED_OUTSIDE")
                        || value.startsWith("ACTION_TURN_MISMATCH")).count());
        summary.put("illegalRoleAssignmentCount", distribution.metrics().get("impossibleFinalRoleCount"));
        summary.put("candidateEmptyCount", gameOne.stream().flatMap(value -> value.violations().stream())
                .filter(value -> value.startsWith("CANDIDATE_EMPTY")).count());
        summary.put("fearlessReuseCount", fearless.stream().mapToInt(FearlessSeriesAudit::reuseCount).sum());
        summary.put("fearlessBanConsumptionViolations", fearless.stream()
                .mapToInt(FearlessSeriesAudit::banConsumptionViolations).sum());
        summary.put("determinismMismatchCount", draftReplay + seriesReplay);
        summary.put("matchReplayMismatchCount", matchReplay);
        summary.put("nonFiniteComponentCount", components.stream().mapToLong(ComponentDistribution::nonFiniteCount).sum());
        summary.put("uniquePickedChampions", distribution.metrics().get("uniquePickedChampions"));
        summary.put("uniqueBannedChampions", distribution.metrics().get("uniqueBannedChampions"));
        summary.put("uniquePickOrBanChampions", distribution.metrics().get("uniquePickOrBanChampions"));
        summary.put("candidateCoveredChampions", coverage.stream()
                .filter(row -> ((Number) row.getOrDefault("candidateAppearanceCount", 0)).intValue() > 0).count());
        summary.put("candidateStarvedHighProficiencyChampions", coverage.stream()
                .filter(row -> ((Number) row.getOrDefault("highProficiencyContextCount", 0)).intValue() > 0
                        && ((Number) row.getOrDefault("candidateAppearanceCount", 0)).intValue() == 0)
                .map(row -> row.get("championId")).sorted().toList());
        summary.put("pickHHI", castMap(distribution.metrics().get("pickConcentration")).get("hhi"));
        summary.put("banHHI", castMap(distribution.metrics().get("banConcentration")).get("hhi"));
        summary.put("roleSpecificHHI", distribution.metrics().get("roleSpecificHHI"));
        summary.put("integrationDraftCount", integrations.stream().map(IntegrationAudit::draftId).distinct().count());
        summary.put("integrationMatchCount", integrations.size());
        summary.put("integrationContextCoverage", integrationContextCoverage(integrations));
        summary.put("infoCodes", infoCodes);
        summary.put("reviewCodes", reviews);
        summary.put("blockerCodes", blockers);
        summary.put("latency", latency(gameOne, fearless, serialEngineOnly, wallMillis));
        summary.put("serialEngineOnlyGame1Millis", serialEngineOnly.get("serialEngineOnlyGame1Millis"));
        summary.put("serialEngineOnlyLaterFearlessMillis", serialEngineOnly.get("serialEngineOnlyLaterFearlessMillis"));
        summary.put("backendTests", -1);
        summary.put("backendFailures", -1);
        summary.put("backendErrors", -1);
        summary.put("backendSkipped", -1);
        boolean allowed = blockers.isEmpty();
        String verdict = computedVerdict(blockers, reviews);
        summary.put("verdict", verdict);
        summary.put("phase13GRealDataPopulationAllowed", allowed);
        summary.put("nextPhase", allowed ? "PHASE_13G_REAL_PLAYER_DATA_POPULATION"
                : "PHASE_13G_A_V2_FIX_REQUIRED");
        summary.put("activationProbesPass", probes.activationPass());
        return summary;
    }

    private Map<String, Object> latency(List<DraftAudit> gameOne, List<FearlessSeriesAudit> fearless,
                                        Map<String, Object> serialEngineOnly, long wallMillis) {
        List<DraftAudit> later = fearless.stream()
                .flatMap(series -> series.games().stream().skip(1)).toList();
        Map<String, Object> result = new TreeMap<>();
        result.put("executionMode", "PARALLEL_AUDIT");
        result.put("workerCount", auditWorkerCount());
        result.put("auditWallMillis", wallMillis);
        result.put("game1", latencyStats(gameOne));
        result.put("laterFearless", latencyStats(later));
        result.put("serialEngineOnlyGame1Millis", serialEngineOnly.get("serialEngineOnlyGame1Millis"));
        result.put("serialEngineOnlyLaterFearlessMillis", serialEngineOnly.get("serialEngineOnlyLaterFearlessMillis"));
        return result;
    }

    private Map<String, Object> latencyStats(List<DraftAudit> values) {
        Map<String, Object> result = new TreeMap<>();
        result.put("count", values.size());
        result.put("engineDraftMillis", scalarLatency(values.stream().map(DraftAudit::engineDraftMillis).sorted().toList()));
        result.put("validationMillis", scalarLatency(values.stream().map(DraftAudit::validationMillis).sorted().toList()));
        result.put("totalAuditCaseMillis", scalarLatency(values.stream().map(DraftAudit::totalAuditCaseMillis).sorted().toList()));
        return result;
    }

    private Map<String, Object> scalarLatency(List<Long> values) {
        Map<String, Object> result = new TreeMap<>();
        result.put("meanMillis", values.stream().mapToLong(Long::longValue).average().orElse(0.0));
        result.put("medianMillis", percentileLong(values, .50));
        result.put("p90Millis", percentileLong(values, .90));
        result.put("p95Millis", percentileLong(values, .95));
        result.put("maxMillis", values.stream().mapToLong(Long::longValue).max().orElse(0L));
        return result;
    }

    private static int auditWorkerCount() {
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
    }

    private Map<String, Integer> exposure(List<DraftAudit> audits, TeamSide selectedSide) {
        Map<String, Integer> result = new TreeMap<>();
        contexts.keySet().forEach(id -> result.put(id, 0));
        for (DraftAudit audit : audits) {
            if (!audit.success()) continue;
            if (selectedSide == null || selectedSide == TeamSide.BLUE) result.merge(audit.blueContextId(), 1, Integer::sum);
            if (selectedSide == null || selectedSide == TeamSide.RED) result.merge(audit.redContextId(), 1, Integer::sum);
        }
        return result;
    }

    private Map<String, Integer> fearlessExposure(List<FearlessSeriesAudit> fearless) {
        Map<String, Integer> result = new TreeMap<>();
        contexts.keySet().forEach(id -> result.put(id, 0));
        fearless.forEach(series -> {
            result.merge(series.blueContextId(), 1, Integer::sum);
            result.merge(series.redContextId(), 1, Integer::sum);
        });
        return result;
    }

    private static int minExposure(Map<String, Integer> values) {
        return values.values().stream().mapToInt(Integer::intValue).min().orElse(0);
    }

    private static int maxExposure(Map<String, Integer> values) {
        return values.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private Map<String, Object> integrationContextCoverage(List<IntegrationAudit> integrations) {
        Map<String, Integer> all = new TreeMap<>();
        Map<String, Integer> game1 = new TreeMap<>();
        contexts.keySet().forEach(id -> { all.put(id, 0); game1.put(id, 0); });
        integrations.stream().collect(Collectors.toMap(IntegrationAudit::draftId, value -> value,
                        (left, right) -> left, TreeMap::new)).values().forEach(value -> {
            all.merge(value.blueContextId(), 1, Integer::sum);
            all.merge(value.redContextId(), 1, Integer::sum);
            if (value.source().equals("GAME1")) {
                game1.merge(value.blueContextId(), 1, Integer::sum);
                game1.merge(value.redContextId(), 1, Integer::sum);
            }
        });
        Map<String, Object> result = new TreeMap<>();
        result.put("all24Covered", game1.values().stream().allMatch(value -> value > 0));
        result.put("game1ContextCounts", game1);
        result.put("allSelectedDraftContextCounts", all);
        result.put("game1CoveredContextCount", game1.values().stream().filter(value -> value > 0).count());
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
        try (InputStream input = Phase13GA2StructuralIntegratedAudit.class.getClassLoader().getResourceAsStream(resource)) {
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
