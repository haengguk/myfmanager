package com.lolfm.draft;

import com.lolfm.champion.*;
import com.lolfm.domain.*;
import com.lolfm.simulator.TeamSide;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DraftAbilityTest {
    private static final DraftResourceSet RESOURCES=DraftResourceSet.loadDefault();
    @Test void currentSkillsProficiencyAndStrategyAffectIndependentForecastTerms() {
        var e=new DraftAbilityEvaluator(RESOURCES.champions());
        var team=new DraftTeamContext(Map.of());
        var role=RESOURCES.champions().catalog().all().stream().flatMap(c->c.supportedPositions().stream().map(p->new ChampionRoleKey(c.id(),p))).findFirst().orElseThrow();
        var f=e.evaluate(role,team,DraftPlanArchetype.DIVE);
        var strong=new DraftTeamContext(Map.of(),Map.of(),Map.of(role.position(),PlayerRatings.neutral(role.position()).with(PlayerSkill.MECHANICS,20)));
        var changed=e.evaluate(role,strong,DraftPlanArchetype.DIVE);
        assertThat(changed.playerFit()).isGreaterThan(f.playerFit());
        assertThat(changed.early()).isEqualTo(f.early());
        var late=e.evaluate(role,team,DraftPlanArchetype.FRONT_TO_BACK);
        assertThat(late.late()).isGreaterThan(f.late());
        assertThat(late.early()).isLessThan(f.early());
    }
    @Test void powerCurveOnlyChangeIsReadThroughRuntimeEvaluatorAndProficiencyIsIndependent() {
        var resources=RESOURCES.champions();
        var role=new ChampionRoleKey(new ChampionId("aatrox"),Position.TOP);
        var team=new DraftTeamContext(Map.of());
        var evaluator=new DraftAbilityEvaluator(resources);
        var before=evaluator.evaluate(role,team,DraftPlanArchetype.DIVE);
        var power=org.mockito.Mockito.spy(resources.power());
        var original=power.get(role.championId());
        var anchors=new HashMap<>(original.levelCurve().anchors());
        anchors.compute(6,(k,v)->v+0.2);
        var changed=new ChampionPowerProfile(original.championId(),original.levelCurveId(),original.itemCurveId(),
                new LevelPowerCurve(original.levelCurve().id(),anchors),original.itemModifiers(),original.contextModifiers(),original.tags(),original.profileVersion());
        org.mockito.Mockito.doReturn(changed).when(power).get(role.championId());
        var variant=new ChampionResourceSet(resources.manifest(),resources.catalog(),power,resources.matchup(),resources.composition(),resources.jungleClear());
        var after=new DraftAbilityEvaluator(variant).evaluate(role,team,DraftPlanArchetype.DIVE);
        assertThat(after.early()).isGreaterThan(before.early());
        assertThat(after.mid()).isEqualTo(before.mid());
        assertThat(after.late()).isEqualTo(before.late());
        assertThat(after.playerFit()).isEqualTo(before.playerFit());
        var proficient=evaluator.evaluate(role,new DraftTeamContext(Map.of(role.position(),new ChampionProficiencies(Map.of(role,20)))),DraftPlanArchetype.DIVE);
        assertThat(proficient.playerFit()).isGreaterThan(before.playerFit());
        assertThat(proficient.early()).isEqualTo(before.early());
    }
    @Test void opponentModelCannotReadPrivateSeededStrategy() {
        var context=DraftComputationContext.cached();
        context.bindStrategy(new DraftSelectionContext(42,"blue","red","a".repeat(64),1,"b".repeat(64)));
        double own=context.strategyPreference(TeamSide.BLUE,DraftPlanArchetype.DIVE);
        context.observeStrategyAs(TeamSide.BLUE);
        assertThat(context.strategyPreference(TeamSide.BLUE,DraftPlanArchetype.DIVE)).isEqualTo(own);
        for(var plan:DraftPlanArchetype.values())assertThat(context.strategyPreference(TeamSide.RED,plan)).isZero();
    }
    @Test void strongerPublicReplacementReducesBanValueWithoutChangingCandidate() {
        var r=RESOURCES.champions(); var team=new DraftTeamContext(Map.of());
        var assignments=new RoleAssignmentSolver(r.catalog());
        var availability=new DraftAvailability(r.catalog(),assignments);
        var composition=new DraftCompositionEvaluator(r.catalog(),r.composition(),assignments);
        var matchup=new DraftMatchupEvaluator(assignments,r.matchup());
        var ability=org.mockito.Mockito.mock(DraftAbilityEvaluator.class);
        org.mockito.Mockito.when(ability.evaluate(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call->new DraftAbilityEvaluator.Forecast(((ChampionRoleKey)call.getArgument(0)).championId().value().equals("aatrox")?40:20,0,0,0,0,0));
        var planner=new PreDraftPlanner(r.catalog(),RESOURCES.meta(),r.composition(),assignments,ability,0);
        var plan=planner.plan(team,team,TeamSide.BLUE,Set.of());
        var bans=new BanEvaluator(r.catalog(),RESOURCES.meta(),r.composition(),assignments,availability,composition,matchup,DraftScoringPolicy.ability(0),ability);
        var state=DraftState.fresh(DraftRuleSet.professional(),new SeriesDraftHistory());
        var id=new ChampionId("aatrox");
        var before=bans.evaluate(state,TeamSide.BLUE,id,team,team,plan,plan);
        org.mockito.Mockito.doReturn(new DraftAbilityEvaluator.Forecast(39,0,0,0,0,0)).when(ability)
                .evaluate(org.mockito.ArgumentMatchers.eq(new ChampionRoleKey(new ChampionId("jax"),Position.TOP)),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        var after=bans.evaluate(state,TeamSide.BLUE,id,team,team,plan,plan);
        assertThat(after.components().get(BanScoreComponent.OPPONENT_AVAILABLE_VALUE)).isEqualTo(before.components().get(BanScoreComponent.OPPONENT_AVAILABLE_VALUE));
        assertThat(after.components().get(BanScoreComponent.OPPONENT_REPLACEMENT_VALUE)).isGreaterThan(before.components().get(BanScoreComponent.OPPONENT_REPLACEMENT_VALUE));
        assertThat(after.components().get(BanScoreComponent.OPPONENT_EXPECTED_PICK_VALUE)).isLessThan(before.components().get(BanScoreComponent.OPPONENT_EXPECTED_PICK_VALUE));
    }
    @Test void actualOrderChangesOwnAndOpponentPickOpportunity() {
        var fresh=DraftState.fresh(DraftRuleSet.professional(),new SeriesDraftHistory());
        assertThat(BanEvaluator.nextPickDistance(fresh,TeamSide.BLUE)).isLessThan(BanEvaluator.nextPickDistance(fresh,TeamSide.RED));
        var second=new DraftState(fresh.ruleSet(),12,List.of(),List.of(),List.of(),List.of(),Set.of());
        assertThat(BanEvaluator.nextPickDistance(second,TeamSide.RED)).isLessThan(BanEvaluator.nextPickDistance(second,TeamSide.BLUE));
    }
    @Test void metaDisabledDraftIsLegalSeededAndUsesForecastComponents() {
        var engine=new DraftEngine(RESOURCES,DraftRuleSet.professional(),DraftScoringPolicy.ability(0));
        var context=new DraftSelectionContext(91008011,"blue","red","a".repeat(64),1,"b".repeat(64));
        var team=new DraftTeamContext(Map.of());
        var first=engine.draft(team,team,new SeriesDraftHistory(),context);
        var again=engine.draft(team,team,new SeriesDraftHistory(),context);
        assertThat(first.draftIdentity()).isEqualTo(again.draftIdentity());
        assertThat(first.matchChampionAssignments().asMap()).hasSize(10);
        assertThat(first.draftSelectionPolicyId()).isEqualTo("AUTO_DRAFT_ABILITY_V1");
        assertThat(first.decisions()).allSatisfy(d->assertThat(d.componentBreakdown().get("META_PRIORITY")).isZero());
        assertThat(first.decisions().stream().filter(d->d.actionType()==DraftActionType.PICK).toList())
                .allSatisfy(d->assertThat(d.componentBreakdown()).containsKeys("EARLY_POWER","MID_POWER","LATE_POWER","PLAYER_FIT"));
    }
    @Test void restrictedPoolForecastAndReplacementUseOnlyCompletableRoles() {
        var c=RESOURCES.champions();var solver=new RoleAssignmentSolver(c.catalog());
        var avail=new DraftAvailability(c.catalog(),solver);
        var allowed=Set.of("aatrox","jinx","nautilus","morgana","vi","zyra");
        var exclusions=new HashSet<ChampionId>();
        c.catalog().all().stream().filter(x->!allowed.contains(x.id().value())).forEach(x->exclusions.add(x.id()));
        var state=new DraftState(DraftRuleSet.professional(),16,
                List.of(new ChampionId("aatrox"),new ChampionId("jinx"),new ChampionId("nautilus")),List.of(),List.of(),List.of(),exclusions);
        var ratings=new EnumMap<Position,PlayerRatings>(Position.class);
        for(var p:Position.values()) {
            var a=PlayerRatings.neutral(p);
            for(var skill:PlayerSkill.orderedForPosition(p))a=a.with(skill,p==Position.JUNGLE?20:1);
            ratings.put(p,a);
        }
        var team=new DraftTeamContext(Map.of(),Map.of(),ratings);var ability=new DraftAbilityEvaluator(c);
        var composition=new DraftCompositionEvaluator(c.catalog(),c.composition(),solver);
        var matchup=new DraftMatchupEvaluator(solver,c.matchup());
        var plan=new PreDraftPlanner(c.catalog(),RESOURCES.meta(),c.composition(),solver,ability,0).replan(team,team,TeamSide.BLUE,state);
        var id=new ChampionId("morgana");var policy=DraftScoringPolicy.abilityV2();
        for(var context:List.of(DraftComputationContext.cached(),DraftComputationContext.uncached())) {
            assertThat(avail.evaluationPositions(state,TeamSide.BLUE,id,policy,context)).containsExactly(Position.MID);
            var picks=new PickEvaluator(c.catalog(),RESOURCES.meta(),matchup,solver,composition,avail,policy,ability);
            var result=picks.evaluate(state,TeamSide.BLUE,id,team,team,plan,plan,context);
            assertThat(result.components().get(PickScoreComponent.PLAYER_FIT))
                    .isEqualTo(ability.evaluate(new ChampionRoleKey(id,Position.MID),team,plan.preferred().archetype()).playerFit());
            assertThat(result.components().get(PickScoreComponent.JUNGLE_CLEAR)).isZero();
            var removed=avail.syntheticUnavailable(state,id);
            assertThat(avail.evaluationPositions(removed,TeamSide.BLUE,new ChampionId("vi"),policy,context)).isEmpty();
            var bans=new BanEvaluator(c.catalog(),RESOURCES.meta(),c.composition(),solver,avail,composition,matchup,policy,ability);
            var ban=bans.evaluate(state,TeamSide.RED,id,team,team,plan,plan,context);
            assertThat(ban.components().get(BanScoreComponent.OPPONENT_REPLACEMENT_VALUE)).isZero();
        }
        var old=new PickEvaluator(c.catalog(),RESOURCES.meta(),matchup,solver,composition,avail,DraftScoringPolicy.ability(),ability)
                .evaluate(state,TeamSide.BLUE,id,team,team,plan,plan);
        assertThat(old.components().get(PickScoreComponent.JUNGLE_CLEAR)).isPositive();
    }

}
