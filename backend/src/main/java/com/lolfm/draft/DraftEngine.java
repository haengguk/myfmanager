package com.lolfm.draft;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.Position;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DraftEngine {
    public static final String DETERMINISTIC_BEST_REFERENCE_POLICY_ID =
            "DETERMINISTIC_BEST_REFERENCE_V1";
    public static final String DETERMINISTIC_BEST_REFERENCE_POLICY_HASH =
            "f4c1cc238fa2da61e1f4202bf5a3e8e1d6401be453f00bfef8365ae543087899";

    private final DraftResourceSet resources;
    private final DraftRuleSet rules;
    private final PreDraftPlanner planner;
    private final RoleAssignmentSolver assignments;
    private final PickEvaluator pickEvaluator;
    private final BanEvaluator banEvaluator;
    private final ShallowDraftSearch search;
    private final AutoDraftSelector selector;
    private final FinalRoleAssignmentResolver finalRoles;

    public DraftEngine(DraftResourceSet resources) {
        this(resources, DraftRuleSet.professional(), DraftScoringPolicy.standard());
    }
    public DraftEngine(DraftResourceSet resources, DraftRuleSet rules, DraftScoringPolicy policy) {
        this.resources = resources; this.rules = rules;
        assignments = new RoleAssignmentSolver(resources.champions().catalog());
        DraftCompositionEvaluator composition = new DraftCompositionEvaluator(resources.champions().catalog(),
                resources.champions().composition(), assignments);
        DraftAvailability availability = new DraftAvailability(resources.champions().catalog(), assignments);
        DraftMatchupEvaluator matchup = new DraftMatchupEvaluator(assignments, resources.champions().matchup());
        planner = new PreDraftPlanner(resources.champions().catalog(), resources.meta(),
                resources.champions().composition(), assignments);
        pickEvaluator = new PickEvaluator(resources.champions().catalog(), resources.meta(), matchup,
                assignments, composition, availability, policy);
        banEvaluator = new BanEvaluator(resources.champions().catalog(), resources.meta(), resources.champions().composition(),
                assignments, availability, composition, matchup, policy);
        DraftCandidateGenerator generator = new DraftCandidateGenerator(resources.champions().catalog(), resources.meta(),
                assignments, composition, availability, policy);
        search = new ShallowDraftSearch(planner, generator, pickEvaluator, banEvaluator, policy);
        selector = new AutoDraftSelector(AutoDraftSelectionPolicy.production());
        finalRoles = new FinalRoleAssignmentResolver(assignments, matchup, composition);
    }

    /** Authoritative seeded production boundary. */
    public FinalDraftResult draft(DraftTeamContext blue, DraftTeamContext red,
                                  SeriesDraftHistory history,
                                  DraftSelectionContext selectionContext) {
        if (selectionContext.seriesGameNumber() != history.committedGameCount() + 1) {
            throw new IllegalArgumentException("Draft selection series game number mismatch");
        }
        return executeDraft(blue, red, history, selectionContext);
    }

    /** Explicit unseeded reference used only to prove that scoring/search did not change. */
    public FinalDraftResult draftDeterministicBest(DraftTeamContext blue,
                                                   DraftTeamContext red,
                                                   SeriesDraftHistory history) {
        return executeDraft(blue, red, history, null);
    }

    private FinalDraftResult executeDraft(DraftTeamContext blue, DraftTeamContext red,
                                          SeriesDraftHistory history,
                                          DraftSelectionContext selectionContext) {
        DraftComputationContext context = DraftComputationContext.cached();
        DraftState state = DraftState.fresh(rules, history);
        DraftPlanPortfolio blueInitial = planner.plan(
                blue, red, TeamSide.BLUE, state.fearlessExclusions(), context);
        DraftPlanPortfolio redInitial = planner.plan(
                red, blue, TeamSide.RED, state.fearlessExclusions(), context);
        ArrayList<DraftDecision> decisions = new ArrayList<>();
        ArrayList<DraftSelectionTrace> selectionTraces = new ArrayList<>();
        while (!state.complete()) {
            DraftTurn turn = state.currentTurn();
            ShallowDraftSearch.SearchResult evaluated = search.evaluate(
                    state, blue, red, context);
            DraftSearchCandidate selected;
            if (selectionContext == null) {
                selected = evaluated.rankedCandidates().getFirst();
            } else {
                AutoDraftSelector.Selection selection = selector.select(
                        state, evaluated, selectionContext);
                selected = selection.selectedCandidate();
                selectionTraces.add(selection.trace());
            }
            ShallowDraftSearch.SearchChoice choice =
                    ShallowDraftSearch.SearchChoice.fromSelection(evaluated, selected);
            decisions.add(new DraftDecision(turn.number(), turn.side(), turn.actionType(), choice.championId(),
                    choice.immediateScore(), choice.continuationScore(), choice.finalSearchScore(),
                    choice.componentBreakdown(), choice.portfolio().preferred().archetype(),
                    choice.portfolio().preferred().viability(), choice.alternatives()));
            state = state.apply(new DraftAction(turn.number(), turn.side(), turn.actionType(), choice.championId()));
        }
        FinalRoleAssignmentResolver.ResolvedPair resolved = finalRoles.resolve(
                state.bluePicks(), state.redPicks(), blue, red, context);
        RoleAssignmentSolver.RoleAssignment blueRoles = resolved.blue();
        RoleAssignmentSolver.RoleAssignment redRoles = resolved.red();
        MatchChampionAssignments matchAssignments = toMatchAssignments(blueRoles, redRoles);
        DraftPlanPortfolio blueFinal = planner.replan(
                blue, red, TeamSide.BLUE, state, context);
        DraftPlanPortfolio redFinal = planner.replan(
                red, blue, TeamSide.RED, state, context);
        String selectionPolicyId = selectionContext == null
                ? DETERMINISTIC_BEST_REFERENCE_POLICY_ID
                : AutoDraftSelectionPolicy.production().policyId();
        String selectionPolicyHash = selectionContext == null
                ? DETERMINISTIC_BEST_REFERENCE_POLICY_HASH
                : AutoDraftSelectionPolicy.production().policyHash();
        return new FinalDraftResult(rules, state.blueBans(), state.redBans(), state.bluePicks(), state.redPicks(), decisions,
                blueRoles.positions(), redRoles.positions(), matchAssignments, blueInitial, redInitial, blueFinal, redFinal,
                state.fearlessExclusions(), selectionPolicyId, selectionPolicyHash,
                selectionTraces, resources.meta().metaVersion(), resources.meta().requiredLegalRoleKeyHash(),
                resources.meta().actualLegalRoleKeyHash());
    }

    private static MatchChampionAssignments toMatchAssignments(RoleAssignmentSolver.RoleAssignment blue,
                                                                RoleAssignmentSolver.RoleAssignment red) {
        ArrayList<ChampionAssignment> values = new ArrayList<>();
        addAssignments(values, TeamSide.BLUE, blue);
        addAssignments(values, TeamSide.RED, red);
        return new MatchChampionAssignments(values, ChampionSelectionMode.EXPLICIT);
    }
    private static void addAssignments(List<ChampionAssignment> target, TeamSide side,
                                       RoleAssignmentSolver.RoleAssignment assignment) {
        assignment.positions().entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry ->
                target.add(new ChampionAssignment(new PlayerKey(side, entry.getValue()), entry.getKey(), entry.getValue())));
    }
}
