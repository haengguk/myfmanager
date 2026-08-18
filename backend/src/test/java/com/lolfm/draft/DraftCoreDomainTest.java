package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DraftCoreDomainTest {
    private final RoleAssignmentSolver solver = new RoleAssignmentSolver(DraftTestSupport.RESOURCES.champions().catalog());

    @Test
    void professionalRuleSetUsesTheExactTwentyTurnSequence() {
        DraftRuleSet rules = DraftRuleSet.professional();
        assertThat(rules.turns()).hasSize(20);
        assertThat(rules.turns().stream().filter(t -> t.actionType() == DraftActionType.BAN && t.side() == TeamSide.BLUE)).hasSize(5);
        assertThat(rules.turns().stream().filter(t -> t.actionType() == DraftActionType.BAN && t.side() == TeamSide.RED)).hasSize(5);
        assertThat(rules.turns().stream().filter(t -> t.actionType() == DraftActionType.PICK && t.side() == TeamSide.BLUE)).hasSize(5);
        assertThat(rules.turns().stream().filter(t -> t.actionType() == DraftActionType.PICK && t.side() == TeamSide.RED)).hasSize(5);
        assertThat(rules.turns().subList(6, 12).stream().map(DraftTurn::side))
                .containsExactly(TeamSide.BLUE, TeamSide.RED, TeamSide.RED, TeamSide.BLUE, TeamSide.BLUE, TeamSide.RED);
    }

    @Test
    void candidateEvaluationCannotMutateRealDraftStateOrSeriesHistory() {
        SeriesDraftHistory history = new SeriesDraftHistory();
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), history);
        DraftState same = state;
        new RoleAssignmentSolver(DraftTestSupport.RESOURCES.champions().catalog())
                .feasibleAssignments(List.of(DraftTestSupport.id("yasuo")));
        assertThat(state).isEqualTo(same);
        assertThat(state.nextTurnIndex()).isZero();
        assertThat(history.consumedPicks()).isEmpty();
    }

    @Test
    void currentGameBanIsUnavailableButOnlyApplyAdvancesTheTurn() {
        DraftState initial = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        ChampionId rumble = DraftTestSupport.id("rumble");
        DraftState next = initial.apply(new DraftAction(1, TeamSide.BLUE, DraftActionType.BAN, rumble));
        assertThat(initial.nextTurnIndex()).isZero();
        assertThat(next.nextTurnIndex()).isOne();
        assertThat(next.unavailableChampions()).contains(rumble);
        assertThatThrownBy(() -> next.apply(new DraftAction(2, TeamSide.RED, DraftActionType.BAN, rumble)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flexPicksRemainUnresolvedAndNarrowOnlyByStructuralCompatibility() {
        ChampionId yasuo = DraftTestSupport.id("yasuo");
        ChampionId poppy = DraftTestSupport.id("poppy");
        assertThat(solver.feasibleAssignments(List.of(yasuo))).hasSize(3);
        assertThat(solver.feasibleAssignments(List.of(yasuo, poppy))).hasSizeGreaterThan(1);
        assertThat(solver.feasibleAssignments(List.of(DraftTestSupport.id("varus"))))
                .extracting(value -> value.positionOf(DraftTestSupport.id("varus")))
                .containsExactlyInAnyOrder(Position.TOP, Position.ADC);
        assertThat(solver.feasibleAssignments(List.of(DraftTestSupport.id("anivia"))))
                .extracting(value -> value.positionOf(DraftTestSupport.id("anivia")))
                .containsExactlyInAnyOrder(Position.TOP, Position.MID);
        assertThat(solver.feasibleAssignments(List.of(DraftTestSupport.id("taliyah"))))
                .extracting(value -> value.positionOf(DraftTestSupport.id("taliyah")))
                .containsExactlyInAnyOrder(Position.JUNGLE, Position.MID, Position.ADC);
    }

    @Test
    void practicalFlexRespondsToProficiencyWithoutChangingLegality() {
        ChampionId poppy = DraftTestSupport.id("poppy");
        ChampionRoleKey jungle = new ChampionRoleKey(poppy, Position.JUNGLE);
        DraftTeamContext strong = DraftTestSupport.context(Position.JUNGLE, Map.of(jungle, 20));
        DraftTeamContext weak = DraftTestSupport.context(Position.JUNGLE, Map.of(jungle, 2));
        double strongValue = solver.practicalFlexValue(List.of(), poppy, strong);
        double weakValue = solver.practicalFlexValue(List.of(), poppy, weak);
        assertThat(strongValue).isGreaterThan(weakValue);
        assertThat(solver.feasibleAssignments(List.of(poppy))).hasSize(3);
    }
}
