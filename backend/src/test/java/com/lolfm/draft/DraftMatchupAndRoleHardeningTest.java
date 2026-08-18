package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionMatchupEvaluator;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import com.lolfm.simulator.TeamSide;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DraftMatchupAndRoleHardeningTest {
    private final DraftHardeningFixture f = new DraftHardeningFixture();

    @Test
    void draftRoleEdgeIsExactlyTheProductionGeometricV2LaneCombatEdge() {
        ChampionRoleKey source = key("renekton", Position.TOP);
        ChampionRoleKey opponent = key("jax", Position.TOP);
        double expected = new ChampionMatchupEvaluator(f.resources.champions().matchup())
                .evaluate(source, opponent, ProgressionCombatContext.LANE_COMBAT,
                        ChampionMatchupMode.GEOMETRIC_V2).finalEdge();
        assertThat(f.matchup.roleEdge(source, opponent)).isEqualTo(expected);
    }

    @Test
    void unknownOpponentRoleIsNeutralInsteadOfAutomaticallyFavorable() {
        DraftState state = DraftTestSupport.stateAfter(List.of(
                "rumble", "vi", "syndra", "varus", "nautilus", "poppy",
                "rell", "fiora", "lee-sin"));
        DraftPlanPortfolio own = f.planner.replan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.BLUE, state);
        DraftPlanPortfolio enemy = f.planner.replan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.RED, state);
        PickEvaluation evaluation = f.picks.evaluate(state, TeamSide.BLUE, f.id("jinx"),
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL, own, enemy);
        assertThat(evaluation.components().get(PickScoreComponent.MATCHUP)).isEqualTo(10.0);
    }

    @Test
    void unresolvedFlexUsesMaxOurAssignmentMinOpponentAssignment() {
        List<ChampionId> own = List.of(f.id("yasuo"), f.id("poppy"), f.id("varus"));
        List<ChampionId> enemy = List.of(f.id("anivia"), f.id("taliyah"), f.id("cassiopeia"));
        double expectedEdge = f.roles.feasibleAssignments(own).stream().mapToDouble(ours ->
                f.roles.feasibleAssignments(enemy).stream().mapToDouble(theirs ->
                        f.matchup.assignmentEdge(ours, theirs)).min().orElse(0.0)).max().orElse(0.0);
        assertThat(f.matchup.robustScore(own, enemy)).isEqualTo(DraftMatchupEvaluator.normalize(expectedEdge));
    }

    @Test
    void compositionResponseUsesOnlyTheCandidateRoleStillFeasibleInThePartialDraft() {
        List<ChampionId> own = List.of(f.id("fiora"), f.id("orianna"), f.id("caitlyn"), f.id("soraka"));
        List<ChampionId> enemy = List.of(f.id("naafiri"), f.id("kaisa"));
        ChampionId poppy = f.id("poppy");
        assertThat(f.composition.feasibleCandidatePositions(own, poppy)).containsExactly(Position.JUNGLE);
        double production = f.composition.compositionResponse(own, enemy, poppy,
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL);
        double jungleOnly = f.composition.compositionResponseForRole(enemy,
                new ChampionRoleKey(poppy, Position.JUNGLE));
        assertThat(production).isEqualTo(jungleOnly);
    }

    @Test
    void finalResolverUsesRobustUtilityRatherThanProficiencyAlone() {
        List<ChampionId> flex = List.of(f.id("yasuo"), f.id("poppy"), f.id("taliyah"),
                f.id("varus"), f.id("galio"));
        Map<ChampionRoleKey, Integer> values = new HashMap<>();
        values.put(key("yasuo", Position.MID), 15);
        values.put(key("yasuo", Position.TOP), 14);
        DraftTeamContext context = context(values);
        RoleAssignmentSolver.RoleAssignment proficiencyOnly = f.roles.bestAssignment(flex, context);
        FinalRoleAssignmentResolver resolver = new FinalRoleAssignmentResolver(f.roles, f.matchup, f.composition);
        List<List<ChampionId>> opponents = List.of(
                List.of(f.id("fiora"), f.id("lee-sin"), f.id("orianna"), f.id("kaisa"), f.id("nautilus")),
                List.of(f.id("jax"), f.id("vi"), f.id("syndra"), f.id("caitlyn"), f.id("rell")),
                List.of(f.id("malphite"), f.id("nocturne"), f.id("azir"), f.id("jinx"), f.id("braum")));
        boolean nonProficiencyChoice = false;
        for (List<ChampionId> opponent : opponents) {
            RoleAssignmentSolver.RoleAssignment robust = resolver.resolve(flex, opponent, context,
                    DraftTestSupport.NEUTRAL).blue();
            double robustWorst = f.roles.feasibleAssignments(opponent).stream()
                    .mapToDouble(enemy -> resolver.utility(robust, enemy, context)).min().orElseThrow();
            double proficiencyWorst = f.roles.feasibleAssignments(opponent).stream()
                    .mapToDouble(enemy -> resolver.utility(proficiencyOnly, enemy, context)).min().orElseThrow();
            assertThat(robustWorst).isGreaterThanOrEqualTo(proficiencyWorst);
            nonProficiencyChoice |= !robust.equals(proficiencyOnly);
        }
        assertThat(nonProficiencyChoice).isTrue();
    }

    @Test
    void clearProficiencyAdvantageStillControlsWhenOtherSignalsAreClose() {
        List<ChampionId> flex = List.of(f.id("yasuo"), f.id("poppy"), f.id("taliyah"),
                f.id("varus"), f.id("galio"));
        List<ChampionId> mirror = List.copyOf(flex);
        Map<ChampionRoleKey, Integer> values = new HashMap<>();
        values.put(key("yasuo", Position.TOP), 20);
        values.put(key("yasuo", Position.MID), 1);
        values.put(key("yasuo", Position.ADC), 1);
        DraftTeamContext context = context(values);
        FinalRoleAssignmentResolver resolver = new FinalRoleAssignmentResolver(f.roles, f.matchup, f.composition);
        assertThat(resolver.resolve(flex, mirror, context, DraftTestSupport.NEUTRAL)
                .blue().positionOf(f.id("yasuo"))).isEqualTo(Position.TOP);
    }

    private ChampionRoleKey key(String id, Position position) { return new ChampionRoleKey(f.id(id), position); }
    private DraftTeamContext context(Map<ChampionRoleKey, Integer> values) {
        EnumMap<Position, ChampionProficiencies> byPosition = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            Map<ChampionRoleKey, Integer> selected = values.entrySet().stream()
                    .filter(entry -> entry.getKey().position() == position)
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            byPosition.put(position, new ChampionProficiencies(selected));
        }
        return new DraftTeamContext(byPosition);
    }
}
