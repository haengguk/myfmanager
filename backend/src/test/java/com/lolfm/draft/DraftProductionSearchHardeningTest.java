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
    void productionChoosePathEscapesAtLeastOneImmediateScoreGreedyTrap() {
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
            escapedGreedyTrap = !immediateBest.championId().equals(choice.championId())
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
    void protectionValueIsNotThePlanThreatLinearCombination() {
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        DraftPlanPortfolio own = f.planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.BLUE, SetSupport.none());
        DraftPlanPortfolio enemy = f.planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.RED, SetSupport.none());
        List<ChampionId> candidates = List.of("aatrox", "ambessa", "camille", "fiora", "gnar", "jax",
                "malphite", "naafiri", "ornn", "poppy", "renekton", "rumble", "vi", "nocturne",
                "jarvan-iv", "wukong", "ahri", "syndra").stream().map(f::id).toList();
        List<BanEvaluation> values = candidates.stream().map(candidate -> f.bans.evaluate(state, TeamSide.BLUE,
                candidate, DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, own, enemy)).toList();
        boolean separated = false;
        for (BanEvaluation a : values) for (BanEvaluation b : values) {
            double threatGap = Math.abs(a.components().get(BanScoreComponent.THREAT_TO_OUR_PLAN_PORTFOLIO)
                    - b.components().get(BanScoreComponent.THREAT_TO_OUR_PLAN_PORTFOLIO));
            double protectionGap = Math.abs(a.components().get(BanScoreComponent.PROTECTION_VALUE)
                    - b.components().get(BanScoreComponent.PROTECTION_VALUE));
            if (threatGap < 0.75 && protectionGap > 0.25) separated = true;
        }
        assertThat(separated).isTrue();
    }

    private DraftPlanPortfolio portfolio(DraftPlanArchetype type, List<ChampionId> core, double viability) {
        return new DraftPlanPortfolio(List.of(new DraftPlan(type, type.desired(), type.vulnerabilities(),
                core, Map.of(), viability)));
    }

    private static final class SetSupport {
        private static java.util.Set<ChampionId> none() { return java.util.Set.of(); }
    }
}
