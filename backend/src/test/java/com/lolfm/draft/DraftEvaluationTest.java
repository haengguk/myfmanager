package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionId;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DraftEvaluationTest {
    private final DraftResourceSet resources = DraftTestSupport.RESOURCES;
    private final RoleAssignmentSolver roles = new RoleAssignmentSolver(resources.champions().catalog());
    private final DraftCompositionEvaluator composition = new DraftCompositionEvaluator(resources.champions().catalog(),
            resources.champions().composition(), roles);
    private final PreDraftPlanner planner = new PreDraftPlanner(resources.champions().catalog(), resources.meta(),
            resources.champions().composition());

    @Test
    void portfolioExistsBeforeFirstBanAndContainsPrimarySecondaryFallbackDirections() {
        DraftPlanPortfolio portfolio = planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.BLUE, java.util.Set.of());
        assertThat(portfolio.plans()).hasSize(3);
        assertThat(portfolio.preferred().coreCandidates()).isNotEmpty();
        assertThat(portfolio.preferred().desiredCapabilities()).isNotEmpty();
        assertThat(portfolio.preferred().structuralVulnerabilities()).isNotEmpty();
    }

    @Test
    void enemyTakingCorePoolLowersPrimaryViabilityAndEnablesPortfolioPivot() {
        DraftPlanPortfolio initial = planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.BLUE, java.util.Set.of());
        DraftPlan original = initial.preferred();
        List<ChampionId> stolen = original.coreCandidates().subList(0, 5);
        DraftPlanPortfolio replanned = planner.replan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.BLUE, java.util.Set.of(), List.of(), stolen);
        double newOriginalViability = replanned.plans().stream().filter(plan -> plan.archetype() == original.archetype())
                .mapToDouble(DraftPlan::viability).findFirst().orElse(Double.NEGATIVE_INFINITY);
        assertThat(newOriginalViability).isLessThan(original.viability());
        assertThat(replanned.preferred().archetype()).isNotEqualTo(original.archetype());
    }

    @Test
    void naafiriKaisaDiveRaisesPoppyAndRenataCompositionResponse() {
        List<ChampionId> dive = List.of(id("naafiri"), id("kaisa"));
        double poppyNeutral = composition.compositionResponse(List.of(), List.of(), id("poppy"),
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL);
        double poppyDive = composition.compositionResponse(List.of(), dive, id("poppy"),
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL);
        double renataDive = composition.compositionResponse(List.of(), dive, id("renata-glasc"),
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL);
        assertThat(poppyDive).isGreaterThan(poppyNeutral);
        assertThat(renataDive).isGreaterThan(poppyNeutral);
    }

    @Test
    void structuralRepairRecognizesMissingEngageAndDamageImbalance() {
        List<ChampionId> lowEngagePhysical = List.of(id("fiora"), id("lillia"), id("zed"), id("caitlyn"));
        double engageRepair = composition.repairValue(lowEngagePhysical, List.of(), id("nautilus"),
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL);
        double noEngageRepair = composition.repairValue(lowEngagePhysical, List.of(), id("soraka"),
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL);
        double magicRepair = composition.repairValue(lowEngagePhysical, List.of(), id("seraphine"),
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL);
        double physicalExtension = composition.repairValue(lowEngagePhysical, List.of(), id("pyke"),
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL);
        assertThat(engageRepair).isGreaterThan(noEngageRepair);
        assertThat(magicRepair).isGreaterThan(physicalExtension);
    }

    @Test
    void pickAndBanEvaluationsExposeEveryRequiredNamedComponent() {
        DraftScoringPolicy policy = DraftScoringPolicy.standard();
        DraftAvailability availability = new DraftAvailability(resources.champions().catalog(), roles);
        DraftMatchupEvaluator matchup = new DraftMatchupEvaluator(roles, resources.champions().matchup());
        PickEvaluator pick = new PickEvaluator(resources.champions().catalog(), resources.meta(), matchup,
                roles, composition, availability, policy);
        BanEvaluator ban = new BanEvaluator(resources.champions().catalog(), resources.meta(), resources.champions().composition(),
                roles, availability, composition, matchup, policy);
        DraftState banState = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        DraftPlanPortfolio bluePlan = planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, TeamSide.BLUE, java.util.Set.of());
        DraftPlanPortfolio redPlan = planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, TeamSide.RED, java.util.Set.of());
        assertThat(ban.evaluate(banState, TeamSide.BLUE, id("poppy"), DraftTestSupport.NEUTRAL,
                DraftTestSupport.NEUTRAL, bluePlan, redPlan).components().keySet())
                .containsExactlyInAnyOrder(BanScoreComponent.values());
        DraftState pickState = DraftTestSupport.stateAfter(List.of("rumble", "vi", "orianna", "varus", "nautilus", "poppy"));
        assertThat(pick.evaluate(pickState, TeamSide.BLUE, id("yasuo"), DraftTestSupport.NEUTRAL,
                DraftTestSupport.NEUTRAL, bluePlan, redPlan).components().keySet())
                .containsExactlyInAnyOrder(PickScoreComponent.values());
    }

    private static ChampionId id(String value) { return DraftTestSupport.id(value); }
}
