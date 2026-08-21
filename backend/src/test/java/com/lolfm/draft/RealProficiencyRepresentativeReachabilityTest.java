package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.player.PlayerId;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.player.PlayerRatingKey;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealProficiencyRepresentativeReachabilityTest {
    private static final List<String> ACTIONS = List.of(
            "aatrox", "akali", "akshan", "annie", "amumu", "brand",
            "camille", "vi", "poppy", "nautilus");

    @Test
    void representativeRealHighKeysUseStableIdentityAndReplayExactly() {
        DraftResourceSet resources = DraftResourceSet.loadDefault();
        PlayerRatingCatalog ratings = PlayerRatingCatalog.loadDefault();
        ChampionProficiencyCatalog proficiencies = ChampionProficiencyCatalog.loadDefault(
                ratings, resources.champions().catalog());
        LckTeamAssembler teams = new LckTeamAssembler(ratings, proficiencies);
        RealProficiencyCandidateReachabilityGate gate =
                new RealProficiencyCandidateReachabilityGate(resources, ratings);
        PreDraftPlanner planner = new PreDraftPlanner(resources.champions().catalog(), resources.meta(),
                resources.champions().composition(),
                new RoleAssignmentSolver(resources.champions().catalog()));

        for (Representative representative : List.of(
                new Representative("player-chovy", "GEN", Position.MID, "azir", 20, "T1"),
                new Representative("player-faker", "T1", Position.MID, "leblanc", 20, "GEN"),
                new Representative("player-keria", "T1", Position.SUPPORT, "bard", 20, "GEN"))) {
            PlayerId playerId = new PlayerId(representative.playerId());
            PlayerRatingKey playerKey = new PlayerRatingKey(
                    representative.teamCode(), representative.position());
            ChampionRoleKey roleKey = new ChampionRoleKey(
                    new ChampionId(representative.championId()), representative.position());
            var own = DraftTeamContext.from(teams.assemble(representative.teamCode()));
            var enemy = DraftTeamContext.from(teams.assemble(representative.opponentCode()));
            List<RealProficiencyCandidateReachabilityGate.Scenario> scenarios =
                    scenarios(representative.teamCode(), own, enemy, planner);

            var first = gate.evaluate(playerId, playerKey, roleKey, scenarios);
            var replay = gate.evaluate(playerId, playerKey, roleKey, scenarios);

            assertThat(first).isEqualTo(replay);
            assertThat(first.proficiency()).isEqualTo(representative.proficiency());
            assertThat(first.scenarioCount()).isEqualTo(3);
            assertThat(first.championLevelLegalScenarioCount()).isPositive();
            assertThat(first.roleSpecificLegalScenarioCount())
                    .isLessThanOrEqualTo(first.championLevelLegalScenarioCount());
            assertThat(first.playerId()).isEqualTo(playerId);
            assertThat(first.playerKey()).isEqualTo(playerKey);
        }
    }

    private List<RealProficiencyCandidateReachabilityGate.Scenario> scenarios(
            String teamCode, DraftTeamContext own, DraftTeamContext enemy, PreDraftPlanner planner) {
        List<RealProficiencyCandidateReachabilityGate.Scenario> result = new ArrayList<>();
        for (int actionCount : List.of(6, 8, 10)) {
            DraftState state = stateAfter(ACTIONS.subList(0, actionCount));
            TeamSide side = state.currentTurn().side();
            result.add(new RealProficiencyCandidateReachabilityGate.Scenario(
                    teamCode.toLowerCase() + "-representative-" + actionCount,
                    side, state, own, enemy,
                    planner.replan(own, enemy, side, state),
                    planner.replan(enemy, own, side.opposite(), state)));
        }
        return List.copyOf(result);
    }

    private DraftState stateAfter(List<String> championIds) {
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        for (String championId : championIds) {
            DraftTurn turn = state.currentTurn();
            state = state.apply(new DraftAction(turn.number(), turn.side(), turn.actionType(),
                    new ChampionId(championId)));
        }
        return state;
    }

    private record Representative(String playerId, String teamCode, Position position,
                                  String championId, int proficiency, String opponentCode) { }
}
