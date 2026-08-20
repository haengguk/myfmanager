package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionId;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DraftProductionSearchHardeningTest {
    private final DraftHardeningFixture f = new DraftHardeningFixture();

    @Test
    void productionOpponentResponseGreedyTrapIsEscaped() {
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        boolean escapedGreedyTrap = false;
        while (!state.complete() && !escapedGreedyTrap) {
            ShallowDraftSearch.SearchChoice choice = f.search.choose(state,
                    DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL);
            DraftSearchCandidateScore immediateBest = choice.rootCandidateScores().stream()
                    .max(java.util.Comparator.comparingDouble(DraftSearchCandidateScore::immediateScore)
                            .thenComparing(value -> value.championId().value(), java.util.Comparator.reverseOrder()))
                    .orElseThrow();
            DraftSearchCandidateScore finalBest = choice.rootCandidateScores().getFirst();
            assertThat(finalBest.championId()).isEqualTo(choice.championId());
            assertThat(choice.finalSearchScore()).isEqualTo(choice.immediateScore() + choice.continuationScore());
            boolean nextTurnIsOpponent = state.nextTurnIndex() + 1 < state.ruleSet().turns().size()
                    && state.ruleSet().turns().get(state.nextTurnIndex() + 1).side()
                    == state.currentTurn().side().opposite();
            escapedGreedyTrap = nextTurnIsOpponent
                    && !immediateBest.championId().equals(choice.championId())
                    && choice.finalSearchScore() > immediateBest.finalSearchScore();
            DraftTurn turn = state.currentTurn();
            state = state.apply(new DraftAction(turn.number(), turn.side(), turn.actionType(), choice.championId()));
        }
        assertThat(escapedGreedyTrap).isTrue();
    }

    @Test
    void enemyPortfolioChangesOpponentExpectedPickValue() {
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        ChampionId candidate = f.id("yasuo");
        DraftPlanPortfolio own = portfolio(DraftPlanArchetype.POKE_SIEGE, List.of(), 10);
        DraftPlanPortfolio enemyLow = portfolio(DraftPlanArchetype.FRONT_TO_BACK, List.of(), 2);
        DraftPlanPortfolio enemyHigh = portfolio(DraftPlanArchetype.FRONT_TO_BACK, List.of(candidate), 20);
        double low = f.bans.evaluate(state, TeamSide.BLUE, candidate, DraftTestSupport.NEUTRAL,
                DraftTestSupport.NEUTRAL, own, enemyLow).components()
                .get(BanScoreComponent.OPPONENT_EXPECTED_PICK_VALUE);
        double high = f.bans.evaluate(state, TeamSide.BLUE, candidate, DraftTestSupport.NEUTRAL,
                DraftTestSupport.NEUTRAL, own, enemyHigh).components()
                .get(BanScoreComponent.OPPONENT_EXPECTED_PICK_VALUE);
        assertThat(high).isGreaterThan(low);
    }

    @Test
    void protectionValueRemainsSeparateFromPlanStructuralThreat() {
        DraftState state = new DraftState(DraftRuleSet.professional(), 13,
                List.of(f.id("caitlyn")), List.of(), List.of(), List.of(), java.util.Set.of());
        DraftPlanPortfolio own = f.planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.BLUE, SetSupport.none());
        DraftPlanPortfolio enemy = f.planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.RED, SetSupport.none());
        List<ChampionId> candidates = List.of("aatrox", "ambessa", "camille", "fiora", "gnar", "jax",
                "malphite", "naafiri", "ornn", "poppy", "renekton", "rumble", "vi", "nocturne",
                "jarvan-iv", "wukong", "ahri", "syndra").stream().map(f::id).toList();
        List<BanEvaluation> values = candidates.stream().map(candidate -> f.bans.evaluate(state, TeamSide.BLUE,
                candidate, DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, own, enemy)).toList();
        assertThat(values).anyMatch(value ->
                value.components().get(BanScoreComponent.THREAT_TO_OUR_PLAN_PORTFOLIO) > 0.0
                        && value.components().get(BanScoreComponent.PROTECTION_VALUE) == 0.0);
        assertThat(values).anyMatch(value ->
                value.components().get(BanScoreComponent.PROTECTION_VALUE) > 0.0);
    }

    private DraftPlanPortfolio portfolio(DraftPlanArchetype type, List<ChampionId> core, double viability) {
        return new DraftPlanPortfolio(List.of(new DraftPlan(type, type.desired(), type.vulnerabilities(),
                core, Map.of(), viability)));
    }

    private static final class SetSupport {
        private static java.util.Set<ChampionId> none() { return java.util.Set.of(); }
    }
}
