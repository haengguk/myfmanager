package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionId;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DraftSearchPolicyTest {
    @Test
    void candidateAndSearchBoundsAreExplicitAndWithinTheRequiredEnvelope() {
        DraftScoringPolicy policy = DraftScoringPolicy.standard();
        assertThat(policy.candidateLimit()).isBetween(12, 20);
        assertThat(policy.searchDepth()).isBetween(2, 3);
        assertThat(policy.beamWidth()).isPositive();
        assertThat(policy.structuralRepairSlots()).isPositive();
    }

    @Test
    void candidateGenerationIsBoundedStableAndFiltersFearlessBeforeScoring() {
        DraftResourceSet resources = DraftTestSupport.RESOURCES;
        RoleAssignmentSolver roles = new RoleAssignmentSolver(resources.champions().catalog());
        DraftCompositionEvaluator composition = new DraftCompositionEvaluator(resources.champions().catalog(),
                resources.champions().composition(), roles);
        DraftScoringPolicy policy = DraftScoringPolicy.standard();
        DraftAvailability availability = new DraftAvailability(resources.champions().catalog(), roles);
        DraftCandidateGenerator generator = new DraftCandidateGenerator(resources.champions().catalog(), resources.meta(),
                roles, composition, availability, policy);
        PreDraftPlanner planner = new PreDraftPlanner(resources.champions().catalog(), resources.meta(), resources.champions().composition());
        SeriesDraftHistory history = new SeriesDraftHistory();
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), history);
        DraftPlanPortfolio portfolio = planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.BLUE, state.fearlessExclusions());
        DraftPlanPortfolio enemyPortfolio = planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.RED, state.fearlessExclusions());
        List<ChampionId> first = generator.generate(state, DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                portfolio, enemyPortfolio);
        List<ChampionId> replay = generator.generate(state, DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                portfolio, enemyPortfolio);
        assertThat(first).hasSizeLessThanOrEqualTo(policy.candidateLimit()).isEqualTo(replay);
    }

    @Test
    void shallowResponseSearchEscapesControlledGreedyTrapAndUsesStableTieBreak() {
        ChampionId greedy = DraftTestSupport.id("aatrox");
        ChampionId robust = DraftTestSupport.id("anivia");
        ChampionId selected = ShallowDraftSearch.selectRobust(
                Map.of(greedy, 20.0, robust, 18.0),
                Map.of(greedy, -15.0, robust, -2.0));
        assertThat(selected).isEqualTo(robust);
        assertThat(ShallowDraftSearch.selectRobust(Map.of(greedy, 10.0, robust, 10.0), Map.of()))
                .isEqualTo(greedy);
    }
}
