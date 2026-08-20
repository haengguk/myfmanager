package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DraftTrueFinalSemanticClosureTest {
    private final DraftHardeningFixture f = new DraftHardeningFixture();

    @Test
    void plannerCandidatePlanValueIgnoresImpossibleFlexRoles() {
        DraftState state = finalRedPickState();
        ChampionId taliyah = f.id("taliyah");
        DraftTeamContext low = context(Map.of(
                key(taliyah, Position.JUNGLE), 1,
                key(taliyah, Position.MID), 1,
                key(taliyah, Position.ADC), 1));
        DraftTeamContext impossibleHigh = context(Map.of(
                key(taliyah, Position.JUNGLE), 1,
                key(taliyah, Position.MID), 20,
                key(taliyah, Position.ADC), 20));
        assertThat(f.roles.feasibleCandidatePositions(state.redPicks(), taliyah))
                .containsExactly(Position.JUNGLE);
        assertThat(f.planner.replan(low, DraftTestSupport.NEUTRAL, TeamSide.RED, state))
                .isEqualTo(f.planner.replan(impossibleHigh, DraftTestSupport.NEUTRAL, TeamSide.RED, state));
    }

    @Test
    void plannerOpponentExposureIgnoresImpossibleEnemyRoles() {
        DraftState state = finalRedPickState();
        ChampionId taliyah = f.id("taliyah");
        DraftTeamContext low = context(Map.of(
                key(taliyah, Position.JUNGLE), 1,
                key(taliyah, Position.MID), 1,
                key(taliyah, Position.ADC), 1));
        DraftTeamContext impossibleHigh = context(Map.of(
                key(taliyah, Position.JUNGLE), 1,
                key(taliyah, Position.MID), 20,
                key(taliyah, Position.ADC), 20));
        DraftTeamContext feasibleHigh = context(Map.of(
                key(taliyah, Position.JUNGLE), 20,
                key(taliyah, Position.MID), 1,
                key(taliyah, Position.ADC), 1));
        DraftPlanPortfolio baseline = f.planner.replan(
                DraftTestSupport.NEUTRAL, low, TeamSide.BLUE, state);
        assertThat(f.planner.replan(DraftTestSupport.NEUTRAL, impossibleHigh, TeamSide.BLUE, state))
                .isEqualTo(baseline);
        assertThat(f.planner.replan(DraftTestSupport.NEUTRAL, feasibleHigh, TeamSide.BLUE, state))
                .isNotEqualTo(baseline);
    }

    @Test
    void plannerMissingCapabilityUsesLegalPartialAssignments() {
        List<ChampionId> flex = List.of(f.id("poppy"), f.id("galio"), f.id("taliyah"));
        DraftState state = new DraftState(DraftRuleSet.professional(), 13, flex, List.of(),
                List.of(), List.of(), Set.of());
        DraftPlanPortfolio portfolio = f.planner.replan(
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, TeamSide.BLUE, state);
        List<RoleAssignmentSolver.RoleAssignment> assignments = f.roles.feasibleAssignments(flex);
        assertThat(assignments).hasSizeGreaterThan(1);
        portfolio.plans().forEach(plan -> plan.missingCapabilities().forEach((capability, missing) -> {
            double expectedCurrent = assignments.stream().mapToDouble(assignment ->
                    assignment.positions().entrySet().stream().mapToDouble(entry -> {
                        ChampionRoleKey key = key(entry.getKey(), entry.getValue());
                        return f.composition.profile(key).capability(capability)
                                * (0.75 + DraftTestSupport.NEUTRAL.proficiency(key) / 80.0);
                    }).max().orElse(0.0)).max().orElse(0.0);
            assertThat(missing).isEqualTo(Math.max(0.0, 15.0 - expectedCurrent));
        }));
    }

    @Test
    void futureInfeasibleBanHasOnlyIndependentOwnLostOpportunity() {
        ChampionId candidate = f.id("fiora");
        Set<ChampionId> exclusions = supporting(Position.SUPPORT);
        exclusions.remove(f.id("soraka"));
        DraftState state = new DraftState(DraftRuleSet.professional(), 13,
                List.of(f.id("soraka")), List.of(), List.of(), List.of(), exclusions);
        assertThat(f.availability.canComplete(state, TeamSide.RED, candidate)).isFalse();
        assertThat(f.availability.canComplete(state, TeamSide.BLUE, candidate)).isTrue();
        DraftPlanPortfolio own = f.planner.replan(
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, TeamSide.BLUE, state);
        DraftPlanPortfolio enemy = f.planner.replan(
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, TeamSide.RED, state);
        BanEvaluation value = f.bans.evaluate(state, TeamSide.BLUE, candidate,
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, own, enemy);
        assertThat(value.components()).containsEntry(BanScoreComponent.OPPONENT_EXPECTED_PICK_VALUE, 0.0)
                .containsEntry(BanScoreComponent.META_PRIORITY, 0.0)
                .containsEntry(BanScoreComponent.OPPONENT_FLEX_VALUE, 0.0)
                .containsEntry(BanScoreComponent.THREAT_TO_OUR_PLAN_PORTFOLIO, 0.0)
                .containsEntry(BanScoreComponent.PROTECTION_VALUE, 0.0)
                .containsEntry(BanScoreComponent.ROLE_POOL_COMPRESSION, 0.0);
        assertThat(value.components().get(BanScoreComponent.OUR_LOST_PICK_OPPORTUNITY)).isPositive();
        assertThat(f.candidates.coarseValue(state, TeamSide.BLUE, candidate,
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, own, enemy)).isZero();
        assertThat(f.candidates.searchPriority(state, TeamSide.BLUE, candidate,
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, own, enemy)).isZero();
    }

    @Test
    void protectionUsesPositiveDraftScaleAndCurrentEnemyRole() {
        ChampionId carry = f.id("caitlyn");
        DraftState state = new DraftState(DraftRuleSet.professional(), 13,
                List.of(carry), List.of(), List.of(), List.of(), Set.of());
        ChampionId positive = f.resources.champions().catalog().forPosition(Position.ADC).stream()
                .map(value -> value.id()).filter(id -> !id.equals(carry))
                .max(Comparator.comparingDouble(id -> f.matchup.roleEdge(
                        key(id, Position.ADC), key(carry, Position.ADC)))).orElseThrow();
        ChampionId nonPositive = f.resources.champions().catalog().forPosition(Position.ADC).stream()
                .map(value -> value.id()).filter(id -> !id.equals(carry))
                .min(Comparator.comparingDouble(id -> f.matchup.roleEdge(
                        key(id, Position.ADC), key(carry, Position.ADC)))).orElseThrow();
        double raw = f.matchup.roleEdge(key(positive, Position.ADC), key(carry, Position.ADC));
        double scaled = protection(state, positive, emptyPortfolio());
        assertThat(raw).isPositive();
        assertThat(scaled).isEqualTo(DraftMatchupEvaluator.positiveThreatScore(raw))
                .isBetween(0.0, 20.0).isNotEqualTo(raw);
        assertThat(f.matchup.roleEdge(key(nonPositive, Position.ADC), key(carry, Position.ADC)))
                .isLessThanOrEqualTo(0.0);
        assertThat(protection(state, nonPositive, emptyPortfolio())).isZero();
        assertThat(protection(state, f.id("fiora"), emptyPortfolio())).isZero();

        DraftState jungleOnlyThreat = new DraftState(DraftRuleSet.professional(), 13,
                List.of(carry),
                List.of(f.id("jax"), f.id("orianna"), f.id("kaisa"), f.id("soraka")),
                List.of(), List.of(), Set.of());
        assertThat(f.roles.feasibleCandidatePositions(jungleOnlyThreat.redPicks(), f.id("taliyah")))
                .containsExactly(Position.JUNGLE);
        assertThat(protection(jungleOnlyThreat, f.id("taliyah"), emptyPortfolio())).isZero();
    }

    @Test
    void unavailableOrFutureInfeasibleFutureCoreIsNotProtected() {
        ChampionId carry = f.id("caitlyn");
        ChampionId threat = strongestAdcThreat(carry);
        DraftPlanPortfolio futureCarry = portfolioWithCore(carry);
        DraftState unavailable = new DraftState(DraftRuleSet.professional(), 13,
                List.of(), List.of(), List.of(carry), List.of(), Set.of());
        assertThat(protection(unavailable, threat, futureCarry)).isZero();

        Set<ChampionId> exclusions = supporting(Position.SUPPORT);
        exclusions.remove(f.id("soraka"));
        DraftState futureInfeasible = new DraftState(DraftRuleSet.professional(), 13,
                List.of(), List.of(f.id("soraka")), List.of(), List.of(), exclusions);
        assertThat(f.availability.canComplete(futureInfeasible, TeamSide.BLUE, carry)).isFalse();
        assertThat(f.availability.canComplete(futureInfeasible, TeamSide.RED, threat)).isTrue();
        assertThat(protection(futureInfeasible, threat, futureCarry)).isZero();
    }

    @Test
    void candidateSingleRoleIsNotInflatedByTeammateFlex() {
        ChampionId taliyah = f.id("taliyah");
        List<ChampionId> fixed = List.of(
                f.id("fiora"), f.id("orianna"), f.id("caitlyn"), f.id("soraka"));
        List<ChampionId> teammateFlex = List.of(
                f.id("poppy"), f.id("orianna"), f.id("caitlyn"), f.id("galio"));
        assertThat(f.roles.feasibleCandidatePositions(fixed, taliyah)).containsExactly(Position.JUNGLE);
        assertThat(f.roles.feasibleCandidatePositions(teammateFlex, taliyah)).containsExactly(Position.JUNGLE);
        assertThat(f.roles.feasibleAssignments(append(teammateFlex, taliyah))).hasSizeGreaterThan(1);
        assertThat(f.roles.practicalFlexValue(teammateFlex, taliyah, DraftTestSupport.NEUTRAL))
                .isEqualTo(f.roles.practicalFlexValue(fixed, taliyah, DraftTestSupport.NEUTRAL));
    }

    @Test
    void candidateMultiplePracticalRolesIncreaseFlexValue() {
        ChampionId taliyah = f.id("taliyah");
        List<ChampionId> constrained = List.of(
                f.id("fiora"), f.id("orianna"), f.id("caitlyn"), f.id("soraka"));
        assertThat(f.roles.feasibleCandidatePositions(List.of(), taliyah)).hasSizeGreaterThan(1);
        assertThat(f.roles.practicalFlexValue(List.of(), taliyah, DraftTestSupport.NEUTRAL))
                .isGreaterThan(f.roles.practicalFlexValue(
                        constrained, taliyah, DraftTestSupport.NEUTRAL));
    }

    @Test
    void impossibleCandidateRoleDoesNotIncreaseFlex() {
        ChampionId taliyah = f.id("taliyah");
        List<ChampionId> picks = List.of(
                f.id("poppy"), f.id("orianna"), f.id("caitlyn"), f.id("galio"));
        DraftTeamContext baseline = context(Map.of(key(taliyah, Position.JUNGLE), 1));
        DraftTeamContext impossibleHigh = context(Map.of(
                key(taliyah, Position.JUNGLE), 1,
                key(taliyah, Position.MID), 20,
                key(taliyah, Position.ADC), 20));
        assertThat(f.roles.practicalFlexValue(picks, taliyah, impossibleHigh))
                .isEqualTo(f.roles.practicalFlexValue(picks, taliyah, baseline));
    }

    @Test
    void controlledOneOpponentResponseGreedyTrapIsEscaped() {
        ShallowDraftSearch immediate = search(1);
        ShallowDraftSearch oneResponse = search(2);
        DraftState state = DraftTestSupport.stateAfter(
                List.of("camille", "vi", "poppy", "nautilus"));
        assertThat(state.currentTurn().side()).isEqualTo(TeamSide.BLUE);
        assertThat(state.ruleSet().turns().get(state.nextTurnIndex() + 1).side())
                .isEqualTo(TeamSide.RED);
        ShallowDraftSearch.SearchChoice depthOne = immediate.choose(
                state, DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL);
        ShallowDraftSearch.SearchChoice depthTwo = oneResponse.choose(
                state, DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL);
        assertThat(depthOne.championId()).isEqualTo(f.id("nocturne"));
        assertThat(depthTwo.championId()).isEqualTo(f.id("jarvan-iv"));
        DraftSearchCandidateScore a = score(depthTwo, depthOne.championId());
        DraftSearchCandidateScore b = score(depthTwo, depthTwo.championId());
        assertThat(a.immediateScore()).isGreaterThan(b.immediateScore());
        assertThat(a.continuationScore()).isLessThan(b.continuationScore());
        assertThat(a.finalSearchScore()).isLessThan(b.finalSearchScore());
        assertThat(a.finalSearchScore()).isEqualTo(a.immediateScore() + a.continuationScore());
        assertThat(b.finalSearchScore()).isEqualTo(b.immediateScore() + b.continuationScore());
        assertThat(oneResponse.choose(state, DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL))
                .isEqualTo(depthTwo);
    }

    private ShallowDraftSearch search(int depth) {
        DraftScoringPolicy policy = new DraftScoringPolicy(
                f.policy.candidateLimit(), f.policy.structuralRepairSlots(), depth,
                f.policy.beamWidth(), f.policy.pickWeights(), f.policy.banWeights());
        DraftCandidateGenerator candidates = new DraftCandidateGenerator(
                f.resources.champions().catalog(), f.resources.meta(), f.roles,
                f.composition, f.availability, policy);
        PickEvaluator picks = new PickEvaluator(
                f.resources.champions().catalog(), f.resources.meta(), f.matchup,
                f.roles, f.composition, f.availability, policy);
        BanEvaluator bans = new BanEvaluator(
                f.resources.champions().catalog(), f.resources.meta(),
                f.resources.champions().composition(), f.roles, f.availability,
                f.composition, f.matchup, policy);
        return new ShallowDraftSearch(f.planner, candidates, picks, bans, policy);
    }

    private DraftSearchCandidateScore score(
            ShallowDraftSearch.SearchChoice choice, ChampionId candidate) {
        return choice.rootCandidateScores().stream()
                .filter(value -> value.championId().equals(candidate)).findFirst().orElseThrow();
    }

    private double protection(
            DraftState state, ChampionId candidate, DraftPlanPortfolio ownPortfolio) {
        DraftPlanPortfolio enemy = f.planner.replan(
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, TeamSide.RED, state);
        return f.bans.evaluate(state, TeamSide.BLUE, candidate,
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, ownPortfolio, enemy)
                .components().get(BanScoreComponent.PROTECTION_VALUE);
    }

    private ChampionId strongestAdcThreat(ChampionId carry) {
        return f.resources.champions().catalog().forPosition(Position.ADC).stream()
                .map(value -> value.id()).filter(id -> !id.equals(carry))
                .max(Comparator.comparingDouble(id -> f.matchup.roleEdge(
                        key(id, Position.ADC), key(carry, Position.ADC)))).orElseThrow();
    }

    private DraftPlanPortfolio emptyPortfolio() {
        return portfolioWithCore(null);
    }

    private DraftPlanPortfolio portfolioWithCore(ChampionId core) {
        DraftPlanArchetype type = DraftPlanArchetype.FRONT_TO_BACK;
        return new DraftPlanPortfolio(List.of(new DraftPlan(
                type, type.desired(), type.vulnerabilities(),
                core == null ? List.of() : List.of(core), Map.of(), 10.0)));
    }

    private DraftState finalRedPickState() {
        return new DraftState(DraftRuleSet.professional(), 19, List.of(),
                List.of(f.id("fiora"), f.id("orianna"), f.id("caitlyn"), f.id("soraka")),
                List.of(), List.of(), Set.of());
    }

    private Set<ChampionId> supporting(Position position) {
        Set<ChampionId> result = new HashSet<>();
        f.resources.champions().catalog().forPosition(position)
                .forEach(value -> result.add(value.id()));
        return result;
    }

    private DraftTeamContext context(Map<ChampionRoleKey, Integer> values) {
        EnumMap<Position, ChampionProficiencies> byPosition = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            Map<ChampionRoleKey, Integer> selected = new HashMap<>();
            values.forEach((key, value) -> {
                if (key.position() == position) selected.put(key, value);
            });
            byPosition.put(position, new ChampionProficiencies(selected));
        }
        return new DraftTeamContext(byPosition);
    }

    private ChampionRoleKey key(ChampionId champion, Position position) {
        return new ChampionRoleKey(champion, position);
    }

    private static List<ChampionId> append(List<ChampionId> values, ChampionId value) {
        ArrayList<ChampionId> result = new ArrayList<>(values);
        result.add(value);
        return result;
    }
}
