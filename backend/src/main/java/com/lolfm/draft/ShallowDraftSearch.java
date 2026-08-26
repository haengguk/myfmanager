package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShallowDraftSearch {
    private final PreDraftPlanner planner;
    private final DraftCandidateGenerator candidates;
    private final PickEvaluator picks;
    private final BanEvaluator bans;
    private final DraftScoringPolicy policy;

    public ShallowDraftSearch(PreDraftPlanner planner, DraftCandidateGenerator candidates,
                              PickEvaluator picks, BanEvaluator bans, DraftScoringPolicy policy) {
        this.planner = planner; this.candidates = candidates; this.picks = picks; this.bans = bans; this.policy = policy;
    }

    public SearchChoice choose(DraftState state, DraftTeamContext blue, DraftTeamContext red) {
        return choose(state, blue, red, DraftComputationContext.uncached());
    }

    SearchChoice choose(DraftState state, DraftTeamContext blue, DraftTeamContext red,
                        DraftComputationContext context) {
        return SearchChoice.deterministicBest(evaluate(state, blue, red, context));
    }

    public SearchResult evaluate(DraftState state, DraftTeamContext blue, DraftTeamContext red) {
        return evaluate(state, blue, red, DraftComputationContext.uncached());
    }

    SearchResult evaluate(DraftState state, DraftTeamContext blue, DraftTeamContext red,
                          DraftComputationContext context) {
        TeamSide root = state.currentTurn().side();
        DraftPlanPortfolio rootPortfolio = portfolio(state, root, blue, red, context);
        DraftPlanPortfolio enemyPortfolio = portfolio(
                state, root.opposite(), blue, red, context);
        List<ChampionId> rootCandidates = candidates.generate(state, context(root, blue, red),
                context(root.opposite(), blue, red), rootPortfolio, enemyPortfolio, context);
        if (rootCandidates.isEmpty()) throw new IllegalStateException("No legal draft candidate at turn " + state.currentTurn().number());
        ArrayList<DraftSearchCandidate> scored = new ArrayList<>();
        for (ChampionId candidate : rootCandidates) {
            ActionEvaluation evaluation = actionEvaluation(state, candidate, blue, red,
                    rootPortfolio, enemyPortfolio, context);
            double immediate = evaluation.score();
            DraftState next = state.apply(action(state, candidate));
            double continuation = utility(next, root, blue, red,
                    policy.searchDepth() - 1, 0.72, context);
            scored.add(new DraftSearchCandidate(candidate, immediate, continuation,
                    immediate + continuation, evaluation.components()));
        }
        scored.sort(Comparator.comparingDouble(DraftSearchCandidate::finalSearchScore).reversed()
                .thenComparing(value -> value.championId().value()));
        return new SearchResult(scored, rootPortfolio);
    }

    public static ChampionId selectRobust(Map<ChampionId, Double> immediate,
                                          Map<ChampionId, Double> opponentResponse) {
        return immediate.keySet().stream().sorted(Comparator
                .comparingDouble((ChampionId id) -> immediate.get(id)
                        + 0.72 * opponentResponse.getOrDefault(id, 0.0))
                .reversed().thenComparing(ChampionId::value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("At least one candidate is required"));
    }

    private double utility(DraftState state, TeamSide root, DraftTeamContext blue, DraftTeamContext red,
                           int depth, double discount,
                           DraftComputationContext context) {
        if (depth == 0 || state.complete()) return terminalRoleSecurity(state, root);
        TeamSide actor = state.currentTurn().side();
        DraftPlanPortfolio actorPortfolio = portfolio(state, actor, blue, red, context);
        DraftPlanPortfolio enemyPortfolio = portfolio(
                state, actor.opposite(), blue, red, context);
        List<ChampionId> generated = candidates.generate(state, context(actor, blue, red),
                context(actor.opposite(), blue, red), actorPortfolio, enemyPortfolio,
                context)
                .stream().limit(policy.beamWidth()).toList();
        if (generated.isEmpty()) return actor == root ? -1000.0 : 1000.0;
        double best = actor == root ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (ChampionId candidate : generated) {
            double signed = (actor == root ? 1.0 : -1.0)
                    * actionEvaluation(state, candidate, blue, red, actorPortfolio,
                    enemyPortfolio, context).score() * discount;
            double value = signed + utility(state.apply(action(state, candidate)), root,
                    blue, red, depth - 1, discount * 0.72, context);
            best = actor == root ? Math.max(best, value) : Math.min(best, value);
        }
        return best;
    }

    private ActionEvaluation actionEvaluation(DraftState state, ChampionId candidate,
                                              DraftTeamContext blue, DraftTeamContext red,
                                              DraftPlanPortfolio ownPlan,
                                              DraftPlanPortfolio enemyPlan,
                                              DraftComputationContext context) {
        TeamSide side = state.currentTurn().side();
        DraftTeamContext own = context(side, blue, red), enemy = context(side.opposite(), blue, red);
        if (state.currentTurn().actionType() == DraftActionType.PICK) {
            PickEvaluation value = picks.evaluate(state, side, candidate, own, enemy,
                    ownPlan, enemyPlan, context);
            Map<String, Double> components = new LinkedHashMap<>();
            value.components().forEach((key, component) -> components.put(key.name(), component));
            return new ActionEvaluation(value.finalScore(), components);
        }
        BanEvaluation value = bans.evaluate(state, side, candidate, own, enemy,
                ownPlan, enemyPlan, context);
        Map<String, Double> components = new LinkedHashMap<>();
        value.components().forEach((key, component) -> components.put(key.name(), component));
        return new ActionEvaluation(value.finalScore(), components);
    }

    private DraftPlanPortfolio portfolio(DraftState state, TeamSide side,
                                         DraftTeamContext blue, DraftTeamContext red,
                                         DraftComputationContext computation) {
        return planner.replan(context(side, blue, red), context(side.opposite(), blue, red),
                side, state, computation);
    }
    private static DraftTeamContext context(TeamSide side, DraftTeamContext blue, DraftTeamContext red) {
        return side == TeamSide.BLUE ? blue : red;
    }
    private static DraftAction action(DraftState state, ChampionId candidate) {
        DraftTurn turn = state.currentTurn();
        return new DraftAction(turn.number(), turn.side(), turn.actionType(), candidate);
    }
    private static double terminalRoleSecurity(DraftState state, TeamSide root) {
        return state.picks(root).size() * 0.05 - state.picks(root.opposite()).size() * 0.05;
    }
    public record SearchChoice(ChampionId championId, double immediateScore, double continuationScore,
                               double finalSearchScore, Map<String, Double> componentBreakdown,
                               List<DraftAlternative> alternatives, DraftPlanPortfolio portfolio,
                               List<DraftSearchCandidateScore> rootCandidateScores) {
        public SearchChoice {
            componentBreakdown = Map.copyOf(componentBreakdown);
            rootCandidateScores = List.copyOf(rootCandidateScores);
        }

        static SearchChoice deterministicBest(SearchResult result) {
            DraftSearchCandidate selected = result.rankedCandidates().getFirst();
            return fromSelection(result, selected);
        }

        static SearchChoice fromSelection(SearchResult result, DraftSearchCandidate selected) {
            List<DraftAlternative> alternatives = result.rankedCandidates().stream()
                    .filter(value -> !value.championId().equals(selected.championId()))
                    .limit(3)
                    .map(value -> new DraftAlternative(
                            value.championId(), value.finalSearchScore())).toList();
            List<DraftSearchCandidateScore> rootScores = result.rankedCandidates().stream()
                    .map(value -> new DraftSearchCandidateScore(
                            value.championId(), value.immediateScore(),
                            value.continuationScore(), value.finalSearchScore())).toList();
            return new SearchChoice(selected.championId(), selected.immediateScore(),
                    selected.continuationScore(), selected.finalSearchScore(),
                    selected.componentBreakdown(), alternatives, result.portfolio(), rootScores);
        }
    }

    public record SearchResult(List<DraftSearchCandidate> rankedCandidates,
                               DraftPlanPortfolio portfolio) {
        public SearchResult {
            rankedCandidates = List.copyOf(rankedCandidates);
            if (rankedCandidates.isEmpty()) {
                throw new IllegalArgumentException("At least one evaluated candidate is required");
            }
        }
    }
    private record ActionEvaluation(double score, Map<String, Double> components) { }
}
