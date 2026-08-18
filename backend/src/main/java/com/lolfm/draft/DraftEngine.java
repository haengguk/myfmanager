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
    private final DraftResourceSet resources;
    private final DraftRuleSet rules;
    private final PreDraftPlanner planner;
    private final RoleAssignmentSolver assignments;
    private final PickEvaluator pickEvaluator;
    private final BanEvaluator banEvaluator;
    private final ShallowDraftSearch search;
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
        planner = new PreDraftPlanner(resources.champions().catalog(), resources.meta(), resources.champions().composition());
        pickEvaluator = new PickEvaluator(resources.champions().catalog(), resources.meta(), matchup,
                assignments, composition, availability, policy);
        banEvaluator = new BanEvaluator(resources.champions().catalog(), resources.meta(), resources.champions().composition(),
                assignments, availability, composition, matchup, policy);
        DraftCandidateGenerator generator = new DraftCandidateGenerator(resources.champions().catalog(), resources.meta(),
                assignments, composition, availability, policy);
        search = new ShallowDraftSearch(planner, generator, pickEvaluator, banEvaluator, policy);
        finalRoles = new FinalRoleAssignmentResolver(assignments, matchup, composition);
    }

    public FinalDraftResult draft(DraftTeamContext blue, DraftTeamContext red, SeriesDraftHistory history) {
        DraftState state = DraftState.fresh(rules, history);
        DraftPlanPortfolio blueInitial = planner.plan(blue, red, TeamSide.BLUE, state.fearlessExclusions());
        DraftPlanPortfolio redInitial = planner.plan(red, blue, TeamSide.RED, state.fearlessExclusions());
        ArrayList<DraftDecision> decisions = new ArrayList<>();
        while (!state.complete()) {
            DraftTurn turn = state.currentTurn();
            ShallowDraftSearch.SearchChoice choice = search.choose(state, blue, red);
            decisions.add(new DraftDecision(turn.number(), turn.side(), turn.actionType(), choice.championId(),
                    choice.immediateScore(), choice.continuationScore(), choice.finalSearchScore(),
                    choice.componentBreakdown(), choice.portfolio().preferred().archetype(),
                    choice.portfolio().preferred().viability(), choice.alternatives()));
            state = state.apply(new DraftAction(turn.number(), turn.side(), turn.actionType(), choice.championId()));
        }
        FinalRoleAssignmentResolver.ResolvedPair resolved = finalRoles.resolve(state.bluePicks(), state.redPicks(), blue, red);
        RoleAssignmentSolver.RoleAssignment blueRoles = resolved.blue();
        RoleAssignmentSolver.RoleAssignment redRoles = resolved.red();
        MatchChampionAssignments matchAssignments = toMatchAssignments(blueRoles, redRoles);
        DraftPlanPortfolio blueFinal = planner.replan(blue, red, TeamSide.BLUE, state);
        DraftPlanPortfolio redFinal = planner.replan(red, blue, TeamSide.RED, state);
        return new FinalDraftResult(rules, state.blueBans(), state.redBans(), state.bluePicks(), state.redPicks(), decisions,
                blueRoles.positions(), redRoles.positions(), matchAssignments, blueInitial, redInitial, blueFinal, redFinal,
                state.fearlessExclusions(), resources.meta().metaVersion(), resources.meta().requiredLegalRoleKeyHash(),
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
