package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic test-side probe that evaluates ROLE_POOL_COMPRESSION through production BanEvaluator. */
public final class RolePoolCompressionGateProbe {
    private static final int DEPLETED_ADC_COUNT = 28;

    private final DraftResourceSet resources;
    private final ChampionCatalog champions;
    private final RoleAssignmentSolver roles;
    private final DraftAvailability availability;
    private final DraftCompositionEvaluator composition;
    private final DraftMatchupEvaluator matchup;
    private final BanEvaluator evaluator;

    public RolePoolCompressionGateProbe(DraftResourceSet resources) {
        this.resources = java.util.Objects.requireNonNull(resources, "resources");
        champions = resources.champions().catalog();
        roles = new RoleAssignmentSolver(champions);
        availability = new DraftAvailability(champions, roles);
        composition = new DraftCompositionEvaluator(champions,
                resources.champions().composition(), roles);
        matchup = new DraftMatchupEvaluator(roles, resources.champions().matchup());
        evaluator = new BanEvaluator(champions, resources.meta(),
                resources.champions().composition(), roles, availability, composition, matchup,
                DraftScoringPolicy.standard());
    }

    public Result run() {
        List<ChampionId> depletedAdc = champions.forPosition(Position.ADC).stream()
                .map(value -> value.id())
                .sorted(Comparator.comparing(ChampionId::value))
                .limit(DEPLETED_ADC_COUNT)
                .toList();
        DraftState state = new DraftState(DraftRuleSet.professional(), 0, List.of(), List.of(),
                List.of(), List.of(), Set.copyOf(depletedAdc));
        DraftTeamContext neutral = new DraftTeamContext(Map.of());
        PreDraftPlanner planner = new PreDraftPlanner(champions, resources.meta(),
                resources.champions().composition(), roles);
        DraftPlanPortfolio bluePortfolio = planner.replan(neutral, neutral, TeamSide.BLUE, state);
        DraftPlanPortfolio redPortfolio = planner.replan(neutral, neutral, TeamSide.RED, state);
        List<CandidateEvaluation> positive = new ArrayList<>();
        int legalCandidateCount = 0;
        for (var definition : champions.all().stream()
                .sorted(Comparator.comparing(value -> value.id().value())).toList()) {
            ChampionId candidate = definition.id();
            if (state.unavailableChampions().contains(candidate)) continue;
            legalCandidateCount++;
            BanEvaluation evaluation = evaluator.evaluate(state, TeamSide.BLUE, candidate,
                    neutral, neutral, bluePortfolio, redPortfolio);
            double component = evaluation.components().get(BanScoreComponent.ROLE_POOL_COMPRESSION);
            if (component > 0.0) positive.add(new CandidateEvaluation(candidate, component, evaluation));
        }
        boolean stateLegal = state.currentTurn().actionType() == DraftActionType.BAN
                && state.bluePicks().isEmpty() && state.redPicks().isEmpty()
                && roles.isFeasible(state.bluePicks()) && roles.isFeasible(state.redPicks());
        boolean stateCompletable = legalCandidateCount > 0
                && canCompleteAfterAnyPick(state, TeamSide.BLUE)
                && canCompleteAfterAnyPick(state, TeamSide.RED);
        return new Result(state, DEPLETED_ADC_COUNT, stateLegal, stateCompletable,
                availability.poolHealth(state, TeamSide.RED, null), positive, BanEvaluator.class.getName());
    }

    private boolean canCompleteAfterAnyPick(DraftState state, TeamSide side) {
        return champions.all().stream().map(value -> value.id())
                .filter(candidate -> !state.unavailableChampions().contains(candidate))
                .anyMatch(candidate -> availability.canComplete(state, side, candidate));
    }

    public record CandidateEvaluation(ChampionId championId, double componentValue,
                                      BanEvaluation evaluation) { }

    public record Result(
            DraftState state,
            int depletedRoleCount,
            boolean stateLegal,
            boolean stateCompletable,
            double rolePoolHealthBeforeCandidate,
            List<CandidateEvaluation> positiveCandidates,
            String evaluatorClass
    ) {
        public Result {
            positiveCandidates = List.copyOf(positiveCandidates);
        }
    }
}
