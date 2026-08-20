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

class DraftFinalClosureTest {
    private final DraftHardeningFixture f = new DraftHardeningFixture();

    @Test
    void repairMembershipDoesNotForceBeamOrdering() {
        DraftState state = DraftTestSupport.stateAfter(List.of(
                "rumble", "vi", "syndra", "varus", "nautilus", "poppy",
                "fiora", "jax", "graves", "lee-sin", "orianna", "zed",
                "ryze", "ezreal", "bard", "gnar", "caitlyn", "kaisa", "rell"));
        TeamSide side = state.currentTurn().side();
        DraftPlanPortfolio own = portfolio(state, side);
        DraftPlanPortfolio enemy = portfolio(state, side.opposite());
        List<ChampionId> legal = f.resources.champions().catalog().all().stream()
                .map(value -> value.id()).filter(id -> !state.unavailableChampions().contains(id))
                .filter(id -> f.roles.isFeasible(append(state.picks(side), id)))
                .filter(id -> f.availability.canComplete(state, side, id)).toList();
        List<ChampionId> repairs = legal.stream().sorted(Comparator
                .comparingDouble((ChampionId id) -> f.composition.repairValue(
                        state.picks(side), state.picks(side.opposite()), id,
                        DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL))
                .reversed().thenComparing(ChampionId::value))
                .limit(f.policy.structuralRepairSlots()).toList();

        List<ChampionId> generated = f.candidates.generate(state, DraftTestSupport.NEUTRAL,
                DraftTestSupport.NEUTRAL, own, enemy);
        List<ChampionId> expectedSearchOrder = generated.stream().sorted(Comparator
                .comparingDouble((ChampionId id) -> f.candidates.searchPriority(state, side, id,
                        DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, own, enemy))
                .reversed().thenComparing(ChampionId::value)).toList();

        assertThat(generated).hasSize(f.policy.candidateLimit()).containsAll(repairs);
        assertThat(generated).containsExactlyElementsOf(expectedSearchOrder);
        assertThat(generated.subList(0, f.policy.beamWidth()))
                .anyMatch(candidate -> !repairs.contains(candidate));
    }

    @Test
    void highestSearchPriorityCandidateCanEnterBeamDespiteRepairReservations() {
        DraftState state = DraftTestSupport.stateAfter(List.of(
                "rumble", "vi", "syndra", "varus", "nautilus", "poppy",
                "fiora", "jax", "graves", "lee-sin", "orianna", "zed",
                "ryze", "ezreal", "bard", "gnar", "caitlyn", "kaisa", "rell"));
        TeamSide side = state.currentTurn().side();
        DraftPlanPortfolio own = portfolio(state, side);
        DraftPlanPortfolio enemy = portfolio(state, side.opposite());
        List<ChampionId> generated = f.candidates.generate(state, DraftTestSupport.NEUTRAL,
                DraftTestSupport.NEUTRAL, own, enemy);
        ChampionId highest = generated.stream().max(Comparator
                .comparingDouble((ChampionId id) -> f.candidates.searchPriority(state, side, id,
                        DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, own, enemy))
                .thenComparing(value -> value.value(), Comparator.reverseOrder())).orElseThrow();
        assertThat(generated.getFirst()).isEqualTo(highest);
        assertThat(generated.subList(0, f.policy.beamWidth())).contains(highest);
    }

    @Test
    void pickMetaAndCoarseUseOnlyCurrentlyFeasibleCandidateRoles() {
        DraftState state = finalRedPickState();
        ChampionId taliyah = f.id("taliyah");
        assertThat(f.roles.feasibleCandidatePositions(state.redPicks(), taliyah))
                .containsExactly(Position.JUNGLE);
        DraftPlanPortfolio empty = emptyPortfolio();
        DraftTeamContext roleSkew = context(Map.of(
                key(taliyah, Position.JUNGLE), 1,
                key(taliyah, Position.MID), 20,
                key(taliyah, Position.ADC), 20));
        PickEvaluation evaluation = f.picks.evaluate(state, TeamSide.RED, taliyah, roleSkew,
                DraftTestSupport.NEUTRAL, empty, empty);
        assertThat(evaluation.legal()).isTrue();
        assertThat(evaluation.components().get(PickScoreComponent.META_PRIORITY))
                .isEqualTo(f.resources.meta().priority(key(taliyah, Position.JUNGLE)));
        assertThat(f.candidates.coarseValue(state, TeamSide.RED, taliyah, roleSkew,
                DraftTestSupport.NEUTRAL, empty, empty))
                .isEqualTo(f.resources.meta().priority(key(taliyah, Position.JUNGLE)) * 0.62 + 1 * 0.38);
    }

    @Test
    void denialAndBanExpectedValueUseOnlyEnemyCurrentlyFeasibleRoles() {
        ChampionId taliyah = f.id("taliyah");
        DraftState pickState = asymmetricFourPickState(18);
        DraftPlanPortfolio empty = emptyPortfolio();
        DraftTeamContext neutral = DraftTestSupport.NEUTRAL;
        DraftTeamContext impossibleRoleHigh = context(Map.of(key(taliyah, Position.MID), 20));
        DraftTeamContext feasibleRoleHigh = context(Map.of(key(taliyah, Position.JUNGLE), 20));
        double neutralDenial = denial(pickState, taliyah, neutral, empty);
        assertThat(denial(pickState, taliyah, impossibleRoleHigh, empty)).isEqualTo(neutralDenial);
        assertThat(denial(pickState, taliyah, feasibleRoleHigh, empty)).isGreaterThan(neutralDenial);

        DraftState banState = asymmetricFourPickState(12);
        double neutralExpected = banExpected(banState, TeamSide.RED, taliyah, neutral, empty);
        assertThat(banExpected(banState, TeamSide.RED, taliyah, impossibleRoleHigh, empty))
                .isEqualTo(neutralExpected);
        assertThat(banExpected(banState, TeamSide.RED, taliyah, feasibleRoleHigh, empty))
                .isGreaterThan(neutralExpected);
        double neutralCoarse = f.candidates.coarseValue(banState, TeamSide.RED, taliyah,
                DraftTestSupport.NEUTRAL, neutral, empty, empty);
        assertThat(f.candidates.coarseValue(banState, TeamSide.RED, taliyah,
                DraftTestSupport.NEUTRAL, impossibleRoleHigh, empty, empty)).isEqualTo(neutralCoarse);
        assertThat(f.candidates.coarseValue(banState, TeamSide.RED, taliyah,
                DraftTestSupport.NEUTRAL, feasibleRoleHigh, empty, empty)).isGreaterThan(neutralCoarse);
    }

    @Test
    void opponentFutureInfeasiblePickHasNoDenialOrBanExpectedValue() {
        ChampionId candidate = f.id("fiora");
        Set<ChampionId> exclusions = supporting(Position.SUPPORT);
        exclusions.remove(f.id("soraka"));
        DraftState pickState = new DraftState(DraftRuleSet.professional(), 18,
                List.of(f.id("soraka")), List.of(), List.of(), List.of(), exclusions);
        assertThat(f.availability.canComplete(pickState, TeamSide.BLUE, candidate)).isTrue();
        assertThat(f.availability.canComplete(pickState, TeamSide.RED, candidate)).isFalse();
        assertThat(denial(pickState, candidate, DraftTestSupport.NEUTRAL, emptyPortfolio())).isZero();

        DraftState banState = new DraftState(DraftRuleSet.professional(), 12,
                List.of(), List.of(f.id("soraka")), List.of(), List.of(), exclusions);
        assertThat(f.availability.canComplete(banState, TeamSide.BLUE, candidate)).isFalse();
        assertThat(banExpected(banState, TeamSide.RED, candidate,
                DraftTestSupport.NEUTRAL, emptyPortfolio())).isZero();

        Set<ChampionId> viableExclusions = supporting(Position.SUPPORT);
        viableExclusions.remove(f.id("nautilus"));
        DraftState viablePickState = new DraftState(DraftRuleSet.professional(), 18,
                List.of(), List.of(), List.of(), List.of(), viableExclusions);
        DraftState viableBanState = new DraftState(DraftRuleSet.professional(), 12,
                List.of(), List.of(), List.of(), List.of(), viableExclusions);
        assertThat(denial(viablePickState, f.id("nautilus"),
                DraftTestSupport.NEUTRAL, emptyPortfolio())).isPositive();
        assertThat(banExpected(viableBanState, TeamSide.RED, f.id("nautilus"),
                DraftTestSupport.NEUTRAL, emptyPortfolio())).isPositive();
    }

    @Test
    void protectionIncludesPickedChampionAndCrossRoleHasZeroDirectValue() {
        ChampionId carry = f.id("caitlyn");
        DraftState state = new DraftState(DraftRuleSet.professional(), 13,
                List.of(carry), List.of(), List.of(), List.of(), Set.of());
        ChampionId strongestAdcThreat = f.resources.champions().catalog().forPosition(Position.ADC).stream()
                .map(value -> value.id()).filter(id -> !id.equals(carry))
                .max(Comparator.comparingDouble(id -> f.matchup.roleEdge(
                        key(id, Position.ADC), key(carry, Position.ADC)))).orElseThrow();
        double positiveEdge = f.matchup.roleEdge(
                key(strongestAdcThreat, Position.ADC), key(carry, Position.ADC));
        assertThat(positiveEdge).isPositive();
        DraftPlanPortfolio empty = emptyPortfolio();
        double sameRole = protection(state, strongestAdcThreat, empty);
        double crossRole = protection(state, f.id("fiora"), empty);
        assertThat(sameRole).isEqualTo(DraftMatchupEvaluator.positiveThreatScore(positiveEdge));
        assertThat(crossRole).isZero();
    }

    @Test
    void sameSideContinuationRemainsExactlyDeterministic() {
        DraftState state = DraftTestSupport.stateAfter(List.of(
                "rumble", "vi", "syndra", "varus", "nautilus", "poppy", "fiora"));
        assertThat(state.currentTurn().side()).isEqualTo(TeamSide.RED);
        assertThat(state.ruleSet().turns().get(state.nextTurnIndex() + 1).side()).isEqualTo(TeamSide.RED);
        assertThat(f.search.choose(state, DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL))
                .isEqualTo(f.search.choose(state, DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL));
    }

    private double denial(DraftState state, ChampionId candidate, DraftTeamContext enemy,
                          DraftPlanPortfolio portfolio) {
        return f.picks.evaluate(state, TeamSide.BLUE, candidate, DraftTestSupport.NEUTRAL,
                enemy, portfolio, portfolio).components().get(PickScoreComponent.DENIAL);
    }

    private double banExpected(DraftState state, TeamSide side, ChampionId candidate,
                               DraftTeamContext enemy, DraftPlanPortfolio portfolio) {
        return f.bans.evaluate(state, side, candidate, DraftTestSupport.NEUTRAL,
                enemy, portfolio, portfolio).components().get(BanScoreComponent.OPPONENT_EXPECTED_PICK_VALUE);
    }

    private double protection(DraftState state, ChampionId candidate, DraftPlanPortfolio portfolio) {
        return f.bans.evaluate(state, TeamSide.BLUE, candidate, DraftTestSupport.NEUTRAL,
                DraftTestSupport.NEUTRAL, portfolio, portfolio).components().get(BanScoreComponent.PROTECTION_VALUE);
    }

    private DraftPlanPortfolio portfolio(DraftState state, TeamSide side) {
        return f.planner.replan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, side, state);
    }

    private DraftPlanPortfolio emptyPortfolio() {
        DraftPlanArchetype type = DraftPlanArchetype.FRONT_TO_BACK;
        return new DraftPlanPortfolio(List.of(new DraftPlan(type, type.desired(), type.vulnerabilities(),
                List.of(), Map.of(), 10.0)));
    }

    private DraftState finalRedPickState() {
        return new DraftState(DraftRuleSet.professional(), 19, List.of(),
                List.of(f.id("fiora"), f.id("orianna"), f.id("caitlyn"), f.id("soraka")),
                List.of(), List.of(), Set.of());
    }

    private DraftState asymmetricFourPickState(int nextTurnIndex) {
        return new DraftState(DraftRuleSet.professional(), nextTurnIndex,
                List.of(f.id("jax"), f.id("syndra"), f.id("kaisa"), f.id("nautilus")),
                List.of(f.id("fiora"), f.id("orianna"), f.id("caitlyn"), f.id("soraka")),
                List.of(), List.of(), Set.of());
    }

    private Set<ChampionId> supporting(Position position) {
        Set<ChampionId> result = new HashSet<>();
        f.resources.champions().catalog().forPosition(position).forEach(value -> result.add(value.id()));
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
