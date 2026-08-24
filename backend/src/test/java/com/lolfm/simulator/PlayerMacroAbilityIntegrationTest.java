package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerMacroAbilityIntegrationTest {

    @Test
    void sideLaneSkillRatherThanFarmingDrivesMidAndLateCrossMapEdges() {
        PlayerRatings strongSideWeakFarm = ratings(Position.TOP)
                .with(PlayerSkill.SIDE_LANE, 20)
                .with(PlayerSkill.FARMING, 5);
        PlayerRatings weakSideStrongFarm = ratings(Position.TOP)
                .with(PlayerSkill.SIDE_LANE, 5)
                .with(PlayerSkill.FARMING, 20);
        GameState state = state(strongSideWeakFarm, weakSideStrongFarm);

        double midEdge = new MidGameMacroResolver().sideLaneEdge(
                state, TeamSide.BLUE, Position.TOP);
        double lateEdge = new LateGameMacroResolver().farmingEdge(
                state, TeamSide.BLUE, Lane.TOP);

        assertThat(midEdge).isPositive();
        assertThat(lateEdge).isPositive();
        assertThat(midEdge).isEqualTo(lateEdge);
    }

    @Test
    void legacyProfilesRetainFarmingFallbackForSideMacroCompatibility() {
        TeamState blue = legacyTeam("blue", 20);
        TeamState red = legacyTeam("red", 5);
        GameState state = new GameState(blue, red);

        assertThat(new MidGameMacroResolver().sideLaneEdge(
                state, TeamSide.BLUE, Position.TOP)).isPositive();
        assertThat(new LateGameMacroResolver().farmingEdge(
                state, TeamSide.BLUE, Lane.TOP)).isPositive();
    }

    private GameState state(PlayerRatings blueTop, PlayerRatings redTop) {
        return new GameState(
                explicitTeam("blue", TeamSide.BLUE, blueTop),
                explicitTeam("red", TeamSide.RED, redTop));
    }

    private TeamState explicitTeam(String name, TeamSide side, PlayerRatings topRatings) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) {
            PlayerRatings playerRatings = position == Position.TOP
                    ? topRatings : ratings(position);
            PlayerMatchPerformance performance = PlayerMatchPerformance.realize(
                    playerRatings, 14, 991L, side);
            players.add(new PlayerState(name + '-' + position, position,
                    new PlayerAttributes(14, 14, 14, 14), performance, 500, true));
        }
        return new TeamState(name, players);
    }

    private TeamState legacyTeam(String name, int topFarming) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) {
            int farming = position == Position.TOP ? topFarming : 14;
            players.add(new PlayerState(name + '-' + position, position,
                    new PlayerAttributes(14, 14, farming, 14), 500));
        }
        return new TeamState(name, players);
    }

    private PlayerRatings ratings(Position position) {
        return PlayerRatings.neutral(position).with(PlayerSkill.CONSISTENCY, 20);
    }
}
