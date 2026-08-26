package com.lolfm.draft;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Test-only, production-equivalent decomposition of {@link DraftEngine#draft}.
 * Timings are deliberately coarse. Deep search work is observed only through deterministic
 * counters so observation cannot affect ordering, scoring, or the production implementation.
 */
public final class AutoDraftObservationHarnessV1 {
    private static final double CONTINUATION_DISCOUNT = 0.72;

    private final DraftResourceSet resources;
    private final DraftRuleSet rules;
    private final PreDraftPlanner planner;
    private final RoleAssignmentSolver assignments;
    private final PickEvaluator picks;
    private final BanEvaluator bans;
    private final DraftCandidateGenerator candidates;
    private final FinalRoleAssignmentResolver finalRoles;
    private final DraftScoringPolicy policy;
    private final LongSupplier clock;

    public AutoDraftObservationHarnessV1(DraftEngine engine) {
        this(engine, System::nanoTime);
    }

    AutoDraftObservationHarnessV1(DraftEngine engine, LongSupplier clock) {
        Objects.requireNonNull(engine, "engine");
        this.clock = Objects.requireNonNull(clock, "clock");
        resources = field(engine, "resources", DraftResourceSet.class);
        rules = field(engine, "rules", DraftRuleSet.class);
        planner = field(engine, "planner", PreDraftPlanner.class);
        assignments = field(engine, "assignments", RoleAssignmentSolver.class);
        picks = field(engine, "pickEvaluator", PickEvaluator.class);
        bans = field(engine, "banEvaluator", BanEvaluator.class);
        ShallowDraftSearch search = field(engine, "search", ShallowDraftSearch.class);
        candidates = field(search, "candidates", DraftCandidateGenerator.class);
        policy = field(search, "policy", DraftScoringPolicy.class);
        finalRoles = field(engine, "finalRoles", FinalRoleAssignmentResolver.class);
    }

    public Observation observe(DraftTeamContext blue, DraftTeamContext red,
                               SeriesDraftHistory history) {
        return observe(blue, red, history, true);
    }

    public Observation observeUncached(DraftTeamContext blue, DraftTeamContext red,
                                       SeriesDraftHistory history) {
        return observe(blue, red, history, false);
    }

    private Observation observe(DraftTeamContext blue, DraftTeamContext red,
                                SeriesDraftHistory history, boolean cacheEnabled) {
        Objects.requireNonNull(blue, "blue");
        Objects.requireNonNull(red, "red");
        Objects.requireNonNull(history, "history");
        DraftComputationContext computation = cacheEnabled
                ? DraftComputationContext.cached() : DraftComputationContext.uncached();
        Counters counters = new Counters();
        long draftStart = clock.getAsLong();
        DraftState state = DraftState.fresh(rules, history);

        long initialPlanStart = clock.getAsLong();
        DraftPlanPortfolio blueInitial = plan(blue, red, TeamSide.BLUE,
                state.fearlessExclusions(), counters, computation);
        DraftPlanPortfolio redInitial = plan(red, blue, TeamSide.RED,
                state.fearlessExclusions(), counters, computation);
        long initialPlanNanos = elapsed(initialPlanStart);

        ArrayList<DraftDecision> decisions = new ArrayList<>();
        ArrayList<TurnObservation> turns = new ArrayList<>();
        while (!state.complete()) {
            DraftTurn turn = state.currentTurn();
            CounterSnapshot before = counters.snapshot();
            long turnStart = clock.getAsLong();
            SearchChoice choice = choose(state, blue, red, counters, computation);
            long turnNanos = elapsed(turnStart);
            DraftDecision decision = new DraftDecision(
                    turn.number(), turn.side(), turn.actionType(), choice.championId(),
                    choice.immediateScore(), choice.continuationScore(),
                    choice.finalSearchScore(), choice.componentBreakdown(),
                    choice.portfolio().preferred().archetype(),
                    choice.portfolio().preferred().viability(), choice.alternatives());
            decisions.add(decision);
            state = state.apply(action(state, choice.championId()));
            turns.add(new TurnObservation(turn.number(), turn.side(), turn.actionType(),
                    choice.championId(), turnNanos, choice.rootCandidateScores(),
                    counters.snapshot().minus(before)));
        }

        long finalRoleStart = clock.getAsLong();
        FinalRoleAssignmentResolver.ResolvedPair resolved = finalRoles.resolve(
                state.bluePicks(), state.redPicks(), blue, red, computation);
        long finalRoleNanos = elapsed(finalRoleStart);
        RoleAssignmentSolver.RoleAssignment blueRoles = resolved.blue();
        RoleAssignmentSolver.RoleAssignment redRoles = resolved.red();

        long matchAssignmentStart = clock.getAsLong();
        MatchChampionAssignments matchAssignments = toMatchAssignments(blueRoles, redRoles);
        long matchAssignmentNanos = elapsed(matchAssignmentStart);

        long finalPlanStart = clock.getAsLong();
        DraftPlanPortfolio blueFinal = replan(
                blue, red, TeamSide.BLUE, state, counters, computation);
        DraftPlanPortfolio redFinal = replan(
                red, blue, TeamSide.RED, state, counters, computation);
        long finalPlanNanos = elapsed(finalPlanStart);

        FinalDraftResult result = new FinalDraftResult(
                rules, state.blueBans(), state.redBans(), state.bluePicks(), state.redPicks(),
                decisions, blueRoles.positions(), redRoles.positions(), matchAssignments,
                blueInitial, redInitial, blueFinal, redFinal, state.fearlessExclusions(),
                DraftEngine.DETERMINISTIC_BEST_REFERENCE_POLICY_ID,
                DraftEngine.DETERMINISTIC_BEST_REFERENCE_POLICY_HASH, List.of(),
                resources.meta().metaVersion(), resources.meta().requiredLegalRoleKeyHash(),
                resources.meta().actualLegalRoleKeyHash());
        return new Observation(result, elapsed(draftStart), initialPlanNanos, finalRoleNanos,
                finalPlanNanos, matchAssignmentNanos, List.copyOf(turns), counters.snapshot(),
                ComputationSnapshot.from(computation.snapshot()));
    }

    public static boolean productionEquivalent(FinalDraftResult left,
                                               FinalDraftResult right) {
        return left.ruleSet().equals(right.ruleSet())
                && left.blueBans().equals(right.blueBans())
                && left.redBans().equals(right.redBans())
                && left.bluePicks().equals(right.bluePicks())
                && left.redPicks().equals(right.redPicks())
                && left.decisions().equals(right.decisions())
                && left.blueFinalRoleAssignments().equals(
                        right.blueFinalRoleAssignments())
                && left.redFinalRoleAssignments().equals(
                        right.redFinalRoleAssignments())
                && left.matchChampionAssignments().asMap().equals(
                        right.matchChampionAssignments().asMap())
                && left.matchChampionAssignments().selectionMode()
                        == right.matchChampionAssignments().selectionMode()
                && left.blueInitialPortfolio().equals(right.blueInitialPortfolio())
                && left.redInitialPortfolio().equals(right.redInitialPortfolio())
                && left.blueFinalPortfolio().equals(right.blueFinalPortfolio())
                && left.redFinalPortfolio().equals(right.redFinalPortfolio())
                && left.hardFearlessExclusions().equals(right.hardFearlessExclusions())
                && left.draftMetaVersion().equals(right.draftMetaVersion())
                && left.requiredLegalRoleKeyHash().equals(
                        right.requiredLegalRoleKeyHash())
                && left.actualLegalRoleKeyHash().equals(right.actualLegalRoleKeyHash())
                && left.draftIdentity().equals(right.draftIdentity());
    }

    private SearchChoice choose(DraftState state, DraftTeamContext blue, DraftTeamContext red,
                                Counters counters,
                                DraftComputationContext computation) {
        TeamSide root = state.currentTurn().side();
        DraftPlanPortfolio rootPortfolio = portfolio(
                state, root, blue, red, counters, computation);
        DraftPlanPortfolio enemyPortfolio = portfolio(
                state, root.opposite(), blue, red, counters, computation);
        List<ChampionId> rootCandidates = generate(state, context(root, blue, red),
                context(root.opposite(), blue, red), rootPortfolio, enemyPortfolio, true,
                counters, computation);
        if (rootCandidates.isEmpty()) {
            throw new IllegalStateException(
                    "No legal draft candidate at turn " + state.currentTurn().number());
        }
        ArrayList<ScoredCandidate> scored = new ArrayList<>();
        for (ChampionId candidate : rootCandidates) {
            ActionEvaluation evaluation = actionEvaluation(state, candidate, blue, red,
                    rootPortfolio, enemyPortfolio, true, counters, computation);
            double immediate = evaluation.score();
            DraftState next = state.apply(action(state, candidate));
            double continuation = utility(next, root, blue, red,
                    policy.searchDepth() - 1, CONTINUATION_DISCOUNT, counters,
                    computation);
            scored.add(new ScoredCandidate(candidate, immediate, continuation,
                    immediate + continuation, evaluation.components()));
        }
        scored.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(value -> value.championId().value()));
        ScoredCandidate selected = scored.getFirst();
        List<DraftAlternative> alternatives = scored.stream().skip(1).limit(3)
                .map(value -> new DraftAlternative(value.championId(), value.score())).toList();
        List<DraftSearchCandidateScore> rootScores = scored.stream()
                .map(value -> new DraftSearchCandidateScore(value.championId(),
                        value.immediateScore(), value.continuationScore(),
                        value.finalSearchScore())).toList();
        return new SearchChoice(selected.championId(), selected.immediateScore(),
                selected.continuationScore(), selected.finalSearchScore(),
                selected.components(), alternatives, rootPortfolio, rootScores);
    }

    private double utility(DraftState state, TeamSide root, DraftTeamContext blue,
                           DraftTeamContext red, int depth, double discount,
                           Counters counters,
                           DraftComputationContext computation) {
        counters.continuationNodes++;
        counters.maximumContinuationDepth = Math.max(
                counters.maximumContinuationDepth, policy.searchDepth() - depth);
        if (depth == 0 || state.complete()) {
            counters.continuationTerminalNodes++;
            return terminalRoleSecurity(state, root);
        }
        TeamSide actor = state.currentTurn().side();
        DraftPlanPortfolio actorPortfolio = portfolio(
                state, actor, blue, red, counters, computation);
        DraftPlanPortfolio enemyPortfolio = portfolio(
                state, actor.opposite(), blue, red, counters, computation);
        List<ChampionId> generated = generate(state, context(actor, blue, red),
                context(actor.opposite(), blue, red), actorPortfolio, enemyPortfolio,
                false, counters, computation).stream().limit(policy.beamWidth()).toList();
        if (generated.isEmpty()) {
            counters.emptyContinuationCandidateSets++;
            return actor == root ? -1000.0 : 1000.0;
        }
        double best = actor == root ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (ChampionId candidate : generated) {
            double signed = (actor == root ? 1.0 : -1.0)
                    * actionEvaluation(state, candidate, blue, red, actorPortfolio,
                    enemyPortfolio, false, counters, computation).score() * discount;
            double value = signed + utility(state.apply(action(state, candidate)), root,
                    blue, red, depth - 1, discount * CONTINUATION_DISCOUNT, counters,
                    computation);
            best = actor == root ? Math.max(best, value) : Math.min(best, value);
        }
        return best;
    }

    private ActionEvaluation actionEvaluation(DraftState state, ChampionId candidate,
                                              DraftTeamContext blue, DraftTeamContext red,
                                              DraftPlanPortfolio ownPlan,
                                              DraftPlanPortfolio enemyPlan,
                                              boolean rootEvaluation, Counters counters,
                                              DraftComputationContext computation) {
        TeamSide side = state.currentTurn().side();
        DraftTeamContext own = context(side, blue, red);
        DraftTeamContext enemy = context(side.opposite(), blue, red);
        counters.actionEvaluations++;
        if (rootEvaluation) counters.rootActionEvaluations++;
        else counters.continuationActionEvaluations++;
        if (state.currentTurn().actionType() == DraftActionType.PICK) {
            counters.pickEvaluations++;
            PickEvaluation value = picks.evaluate(
                    state, side, candidate, own, enemy, ownPlan, enemyPlan, computation);
            Map<String, Double> components = new LinkedHashMap<>();
            value.components().forEach(
                    (key, component) -> components.put(key.name(), component));
            return new ActionEvaluation(value.finalScore(), components);
        }
        counters.banEvaluations++;
        BanEvaluation value = bans.evaluate(
                state, side, candidate, own, enemy, ownPlan, enemyPlan, computation);
        Map<String, Double> components = new LinkedHashMap<>();
        value.components().forEach(
                (key, component) -> components.put(key.name(), component));
        return new ActionEvaluation(value.finalScore(), components);
    }

    private List<ChampionId> generate(DraftState state, DraftTeamContext own,
                                      DraftTeamContext enemy,
                                      DraftPlanPortfolio ownPortfolio,
                                      DraftPlanPortfolio enemyPortfolio,
                                      boolean rootGeneration, Counters counters,
                                      DraftComputationContext computation) {
        counters.candidateGenerationCalls++;
        if (rootGeneration) counters.rootCandidateGenerationCalls++;
        else counters.continuationCandidateGenerationCalls++;
        List<ChampionId> generated = candidates.generate(
                state, own, enemy, ownPortfolio, enemyPortfolio, computation);
        counters.candidatesReturned += generated.size();
        if (rootGeneration) counters.rootCandidatesReturned += generated.size();
        else counters.continuationCandidatesReturned += generated.size();
        return generated;
    }

    private DraftPlanPortfolio plan(DraftTeamContext own, DraftTeamContext enemy,
                                    TeamSide side, java.util.Set<ChampionId> exclusions,
                                    Counters counters,
                                    DraftComputationContext computation) {
        counters.initialPlanCalls++;
        return planner.plan(own, enemy, side, exclusions, computation);
    }

    private DraftPlanPortfolio replan(DraftTeamContext own, DraftTeamContext enemy,
                                      TeamSide side, DraftState state, Counters counters,
                                      DraftComputationContext computation) {
        counters.replanCalls++;
        return planner.replan(own, enemy, side, state, computation);
    }

    private DraftPlanPortfolio portfolio(DraftState state, TeamSide side,
                                         DraftTeamContext blue, DraftTeamContext red,
                                         Counters counters,
                                         DraftComputationContext computation) {
        return replan(context(side, blue, red), context(side.opposite(), blue, red),
                side, state, counters, computation);
    }

    private long elapsed(long start) {
        long value = clock.getAsLong() - start;
        if (value < 0L) throw new IllegalStateException("AUTO_DRAFT_CLOCK_MOVED_BACKWARDS");
        return value;
    }

    private static DraftTeamContext context(TeamSide side, DraftTeamContext blue,
                                            DraftTeamContext red) {
        return side == TeamSide.BLUE ? blue : red;
    }

    private static DraftAction action(DraftState state, ChampionId candidate) {
        DraftTurn turn = state.currentTurn();
        return new DraftAction(turn.number(), turn.side(), turn.actionType(), candidate);
    }

    private static double terminalRoleSecurity(DraftState state, TeamSide root) {
        return state.picks(root).size() * 0.05
                - state.picks(root.opposite()).size() * 0.05;
    }

    private static MatchChampionAssignments toMatchAssignments(
            RoleAssignmentSolver.RoleAssignment blue,
            RoleAssignmentSolver.RoleAssignment red) {
        ArrayList<ChampionAssignment> values = new ArrayList<>();
        addAssignments(values, TeamSide.BLUE, blue);
        addAssignments(values, TeamSide.RED, red);
        return new MatchChampionAssignments(values, ChampionSelectionMode.EXPLICIT);
    }

    private static void addAssignments(List<ChampionAssignment> target, TeamSide side,
                                       RoleAssignmentSolver.RoleAssignment assignment) {
        assignment.positions().entrySet().stream().sorted(Map.Entry.comparingByValue())
                .forEach(entry -> target.add(new ChampionAssignment(
                        new PlayerKey(side, entry.getValue()), entry.getKey(),
                        entry.getValue())));
    }

    private static <T> T field(Object owner, String name, Class<T> type) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                    "Production Draft structure changed at field " + name, error);
        }
    }

    public record Observation(FinalDraftResult result, long fullDraftNanos,
                              long initialPlanNanos, long finalRoleResolutionNanos,
                              long finalPlanNanos, long matchAssignmentProjectionNanos,
                              List<TurnObservation> turns, CounterSnapshot counters,
                              ComputationSnapshot computation) {
        public Observation {
            turns = List.copyOf(turns);
        }
    }

    public record ComputationSnapshot(
            boolean cacheEnabled, long roleAssignmentRequests,
            long roleAssignmentHits, long roleAssignmentMisses,
            long roleAssignmentPhysicalComputations, int roleAssignmentEntries,
            long rolePositionRequests, long rolePositionHits,
            long rolePositionMisses, int rolePositionEntries,
            long completionRequests, long completionHits, long completionMisses,
            long completionPhysicalComputations, int completionEntries,
            long poolHealthRequests, long poolHealthHits, long poolHealthMisses,
            long poolHealthPhysicalComputations, int poolHealthEntries,
            long plannerCandidatePhysicalComputations,
            long plannerCandidateLocalReuses, int peakEntries) {
        private static ComputationSnapshot from(DraftComputationContext.Snapshot value) {
            return new ComputationSnapshot(value.cacheEnabled(),
                    value.roleAssignmentRequests(), value.roleAssignmentHits(),
                    value.roleAssignmentMisses(),
                    value.roleAssignmentPhysicalComputations(),
                    value.roleAssignmentEntries(), value.rolePositionRequests(),
                    value.rolePositionHits(), value.rolePositionMisses(),
                    value.rolePositionEntries(), value.completionRequests(),
                    value.completionHits(), value.completionMisses(),
                    value.completionPhysicalComputations(), value.completionEntries(),
                    value.poolHealthRequests(), value.poolHealthHits(),
                    value.poolHealthMisses(), value.poolHealthPhysicalComputations(),
                    value.poolHealthEntries(),
                    value.plannerCandidatePhysicalComputations(),
                    value.plannerCandidateLocalReuses(), value.peakEntries());
        }
    }

    public record TurnObservation(int turn, TeamSide side, DraftActionType actionType,
                                  ChampionId selectedChampionId, long turnNanos,
                                  List<DraftSearchCandidateScore> rootCandidateScores,
                                  CounterSnapshot counters) {
        public TurnObservation {
            rootCandidateScores = List.copyOf(rootCandidateScores);
        }
    }

    public record CounterSnapshot(long initialPlanCalls, long replanCalls,
                                  long candidateGenerationCalls,
                                  long rootCandidateGenerationCalls,
                                  long continuationCandidateGenerationCalls,
                                  long candidatesReturned, long rootCandidatesReturned,
                                  long continuationCandidatesReturned,
                                  long actionEvaluations, long rootActionEvaluations,
                                  long continuationActionEvaluations,
                                  long pickEvaluations, long banEvaluations,
                                  long continuationNodes, long continuationTerminalNodes,
                                  long emptyContinuationCandidateSets,
                                  int maximumContinuationDepth) {
        CounterSnapshot minus(CounterSnapshot before) {
            return new CounterSnapshot(
                    initialPlanCalls - before.initialPlanCalls,
                    replanCalls - before.replanCalls,
                    candidateGenerationCalls - before.candidateGenerationCalls,
                    rootCandidateGenerationCalls - before.rootCandidateGenerationCalls,
                    continuationCandidateGenerationCalls
                            - before.continuationCandidateGenerationCalls,
                    candidatesReturned - before.candidatesReturned,
                    rootCandidatesReturned - before.rootCandidatesReturned,
                    continuationCandidatesReturned - before.continuationCandidatesReturned,
                    actionEvaluations - before.actionEvaluations,
                    rootActionEvaluations - before.rootActionEvaluations,
                    continuationActionEvaluations - before.continuationActionEvaluations,
                    pickEvaluations - before.pickEvaluations,
                    banEvaluations - before.banEvaluations,
                    continuationNodes - before.continuationNodes,
                    continuationTerminalNodes - before.continuationTerminalNodes,
                    emptyContinuationCandidateSets - before.emptyContinuationCandidateSets,
                    maximumContinuationDepth);
        }
    }

    private static final class Counters {
        long initialPlanCalls;
        long replanCalls;
        long candidateGenerationCalls;
        long rootCandidateGenerationCalls;
        long continuationCandidateGenerationCalls;
        long candidatesReturned;
        long rootCandidatesReturned;
        long continuationCandidatesReturned;
        long actionEvaluations;
        long rootActionEvaluations;
        long continuationActionEvaluations;
        long pickEvaluations;
        long banEvaluations;
        long continuationNodes;
        long continuationTerminalNodes;
        long emptyContinuationCandidateSets;
        int maximumContinuationDepth;

        CounterSnapshot snapshot() {
            return new CounterSnapshot(initialPlanCalls, replanCalls,
                    candidateGenerationCalls, rootCandidateGenerationCalls,
                    continuationCandidateGenerationCalls, candidatesReturned,
                    rootCandidatesReturned, continuationCandidatesReturned,
                    actionEvaluations, rootActionEvaluations,
                    continuationActionEvaluations, pickEvaluations, banEvaluations,
                    continuationNodes, continuationTerminalNodes,
                    emptyContinuationCandidateSets, maximumContinuationDepth);
        }
    }

    private record ActionEvaluation(double score, Map<String, Double> components) { }

    private record ScoredCandidate(ChampionId championId, double immediateScore,
                                   double continuationScore, double finalSearchScore,
                                   Map<String, Double> components) {
        double score() { return finalSearchScore; }
    }

    private record SearchChoice(ChampionId championId, double immediateScore,
                                double continuationScore, double finalSearchScore,
                                Map<String, Double> componentBreakdown,
                                List<DraftAlternative> alternatives,
                                DraftPlanPortfolio portfolio,
                                List<DraftSearchCandidateScore> rootCandidateScores) { }
}
