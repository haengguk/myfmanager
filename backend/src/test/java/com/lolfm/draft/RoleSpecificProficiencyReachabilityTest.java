package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Position;
import com.lolfm.player.PlayerRatingKey;
import com.lolfm.simulator.TeamSide;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RoleSpecificProficiencyReachabilityTest {
    private static DraftResourceSet resources;
    private static ChampionCatalog champions;
    private static RoleAssignmentSolver assignments;
    private static DraftAvailability availability;
    private static RealProficiencyCandidateReachabilityGate gate;

    @BeforeAll
    static void setUp() {
        resources = DraftResourceSet.loadDefault();
        champions = resources.champions().catalog();
        assignments = new RoleAssignmentSolver(champions);
        availability = new DraftAvailability(champions, assignments);
        gate = new RealProficiencyCandidateReachabilityGate(resources);
        assertThat(champions.get(id("poppy")).supportedPositions())
                .contains(Position.TOP, Position.JUNGLE, Position.SUPPORT);
    }

    @Test
    void flexChampionPresenceDoesNotMakeAnInfeasibleTargetRoleReachable() {
        DraftState state = pickState(10, List.of("renekton", "nautilus"), Set.of());
        DraftTeamContext own = context(Map.of(Position.JUNGLE, 20, Position.SUPPORT, 18));

        RealProficiencyCandidateReachabilityGate.Result result = evaluate(
                "poppy-only-jungle", state, own, Position.SUPPORT);
        RealProficiencyCandidateReachabilityGate.ScenarioResult scenario = result.scenarios().getFirst();

        assertThat(scenario.championCandidatePresent()).isTrue();
        assertThat(scenario.targetRoleFeasible()).isFalse();
        assertThat(scenario.roleSpecificCompletionFeasible()).isFalse();
        assertThat(scenario.roleKeyReachable()).isFalse();
        assertThat(result.championPresentButTargetRoleInfeasibleCount()).isOne();
        assertThat(result.roleKeyReachable()).isFalse();
        assertThat(result.reason()).isEqualTo("CHAMPION_PRESENT_BUT_TARGET_ROLE_INFEASIBLE");
    }

    @Test
    void flexChampionTargetRoleIsReachableWhenTheRemainingRosterCanComplete() {
        DraftState state = pickState(17, List.of("renekton", "azir", "jinx"), Set.of());
        RealProficiencyCandidateReachabilityGate.Result result = evaluate(
                "poppy-support-completable", state, context(Map.of(Position.SUPPORT, 20)),
                Position.SUPPORT);
        RealProficiencyCandidateReachabilityGate.ScenarioResult scenario = result.scenarios().getFirst();

        assertThat(scenario.championCandidatePresent()).isTrue();
        assertThat(scenario.targetRoleFeasible()).isTrue();
        assertThat(scenario.roleSpecificCompletionFeasible()).isTrue();
        assertThat(scenario.roleKeyReachable()).isTrue();
        assertThat(result.roleSpecificLegalScenarioCount()).isOne();
        assertThat(result.roleKeyReachable()).isTrue();
    }

    @Test
    void targetRoleBaseAssignmentIsNotEnoughWhenRemainingRosterCannotComplete() {
        Set<ChampionId> unavailableJunglers = new HashSet<>(champions.forPosition(Position.JUNGLE)
                .stream().map(value -> value.id()).toList());
        unavailableJunglers.remove(id("poppy"));
        DraftState state = pickState(17, List.of("renekton", "azir", "jinx"), unavailableJunglers);

        assertThat(assignments.feasibleCandidatePositions(state.bluePicks(), id("poppy")))
                .contains(Position.SUPPORT, Position.JUNGLE);
        assertThat(availability.canComplete(state, TeamSide.BLUE, id("poppy"))).isTrue();
        assertThat(availability.canCompleteWithCandidateAtRole(
                state, TeamSide.BLUE, id("poppy"), Position.JUNGLE)).isTrue();
        assertThat(availability.canCompleteWithCandidateAtRole(
                state, TeamSide.BLUE, id("poppy"), Position.SUPPORT)).isFalse();

        RealProficiencyCandidateReachabilityGate.Result result = evaluate(
                "poppy-support-no-jungler", state, context(Map.of(Position.SUPPORT, 20)),
                Position.SUPPORT);
        RealProficiencyCandidateReachabilityGate.ScenarioResult scenario = result.scenarios().getFirst();

        assertThat(scenario.championCandidatePresent()).isTrue();
        assertThat(scenario.targetRoleFeasible()).isTrue();
        assertThat(scenario.roleSpecificCompletionFeasible()).isFalse();
        assertThat(scenario.roleKeyReachable()).isFalse();
        assertThat(result.championPresentButRoleCompletionImpossibleCount()).isOne();
        assertThat(result.reason()).isEqualTo("CHAMPION_PRESENT_BUT_ROLE_COMPLETION_IMPOSSIBLE");
    }

    @Test
    void aLegalFlexChampionCanBeAbsentFromTheProductionShortlist() {
        DraftState state = pickState(6, List.of(), Set.of());
        RealProficiencyCandidateReachabilityGate.Result absent = champions.all().stream()
                .filter(value -> value.supportedPositions().size() > 1)
                .sorted(Comparator.comparing(value -> value.id().value()))
                .flatMap(value -> value.supportedPositions().stream().sorted()
                        .map(position -> evaluate(value.id().value() + "-absent", state,
                                context(value.id(), Map.of(position, 17)), position)))
                .filter(value -> value.championLevelLegalScenarioCount() == 1)
                .filter(value -> value.roleSpecificLegalScenarioCount() == 1)
                .filter(value -> !value.championCandidateScenarioPresence())
                .findFirst().orElseThrow();

        assertThat(champions.get(absent.championId()).supportedPositions().size()).isGreaterThan(1);
        assertThat(absent.scenarios().getFirst().championCandidatePresent()).isFalse();
        assertThat(absent.scenarios().getFirst().targetRoleFeasible()).isTrue();
        assertThat(absent.scenarios().getFirst().roleSpecificCompletionFeasible()).isTrue();
        assertThat(absent.roleKeyReachable()).isFalse();
        assertThat(absent.reason()).isEqualTo("CHAMPION_ABSENT_FROM_ALL_LEGAL_SHORTLISTS");
    }

    private RealProficiencyCandidateReachabilityGate.Result evaluate(
            String scenarioId, DraftState state, DraftTeamContext own, Position targetPosition) {
        ChampionId candidate = own.proficiencies().get(targetPosition).asMap().keySet().stream()
                .map(ChampionRoleKey::championId).findFirst().orElseThrow();
        DraftTeamContext enemy = new DraftTeamContext(Map.of());
        DraftPlanPortfolio portfolio = neutralPortfolio();
        return gate.evaluate(new PlayerRatingKey("GEN", targetPosition),
                new ChampionRoleKey(candidate, targetPosition),
                List.of(new RealProficiencyCandidateReachabilityGate.Scenario(
                        scenarioId, state.currentTurn().side(), state, own, enemy,
                        portfolio, portfolio)));
    }

    private DraftState pickState(int nextTurnIndex, List<String> bluePicks,
                                 Set<ChampionId> fearlessExclusions) {
        return new DraftState(DraftRuleSet.professional(), nextTurnIndex,
                bluePicks.stream().map(RoleSpecificProficiencyReachabilityTest::id).toList(),
                List.of(), List.of(), List.of(), Set.copyOf(fearlessExclusions));
    }

    private DraftTeamContext context(Map<Position, Integer> values) {
        return context(id("poppy"), values);
    }

    private DraftTeamContext context(ChampionId champion, Map<Position, Integer> values) {
        EnumMap<Position, ChampionProficiencies> result = new EnumMap<>(Position.class);
        values.forEach((position, proficiency) -> result.put(position,
                new ChampionProficiencies(Map.of(
                        new ChampionRoleKey(champion, position), proficiency))));
        return new DraftTeamContext(result);
    }

    private DraftPlanPortfolio neutralPortfolio() {
        DraftPlanArchetype archetype = DraftPlanArchetype.FRONT_TO_BACK;
        return new DraftPlanPortfolio(List.of(new DraftPlan(
                archetype, archetype.desired(), archetype.vulnerabilities(),
                List.of(), Map.of(), 0.0)));
    }

    private static ChampionId id(String value) { return new ChampionId(value); }
}
