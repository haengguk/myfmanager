package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StructureCombatCausalityTest {

    @Test
    void currentCombatParticipantsCannotAlsoCreateIndependentMacroPush() {
        GameState state = LateGameTestSupport.state();
        state.advanceTimeSeconds(900);
        state.getBlueTeamState().getPlayers().forEach(state::markMajorCombatParticipant);
        state.getRedTeamState().getPlayers().forEach(state::markMajorCombatParticipant);
        CountingRandom random = new CountingRandom();

        assertThat(new PushResolver().maybeResolveMacroPush(
                state, random, new StructureResolver())).isEmpty();

        assertThat(random.calls).isZero();
        assertThat(state.getMapState().getDestroyedTowerCountByAttackingSide(TeamSide.BLUE))
                .isZero();
        assertThat(state.getMapState().getDestroyedTowerCountByAttackingSide(TeamSide.RED))
                .isZero();
        assertThat(state.getPushFailureCounts())
                .containsEntry(PushFailureReason.COMBAT_PARTICIPANTS_UNAVAILABLE, 2);
    }

    @Test
    void losingSiegeSideIsBlockedFromIndependentStructurePushForExactMatchScopedWindow() {
        Fixture fixture = fixture();
        fixture.state.getRedTeamState().addGold(100_000);

        LateGameMacroResolver.Resolution result = new LateGameMacroResolver().resolveSelected(
                fixture.state, fixture.blue, fixture.red, new ZeroRandom(), new ArrayList<>(),
                new StructureResolver(), new TeamfightResolver(), TeamSide.BLUE,
                LateGameAttackPlan.SIEGE_MID, LateGameDefenseResponse.DEFEND);

        assertThat(result.result()).isEqualTo(LateGameActionResult.SIEGE_FIGHT_DEFENDER_WIN);
        assertThat(result.fightWinner()).isEqualTo(TeamSide.RED);
        assertThat(result.attackerStructure()).isFalse();
        assertThat(fixture.state.getStructurePushBlockedUntilSeconds(TeamSide.BLUE))
                .isEqualTo(1_830);
        assertThat(fixture.state.isStructurePushBlocked(TeamSide.BLUE, 1_829)).isTrue();
        assertThat(fixture.state.isStructurePushBlocked(TeamSide.BLUE, 1_830)).isFalse();
        assertThat(fixture.state.getMapState()
                .getLaneState(TeamSide.RED, Lane.MID).destroyedTowerCount()).isZero();

        Fixture independentMatch = fixture();
        assertThat(independentMatch.state.isStructurePushBlocked(TeamSide.BLUE, 1_800))
                .isFalse();
    }

    private Fixture fixture() {
        Team blue = team("BLUE");
        Team red = team("RED");
        GameState state = new GameState(teamState(blue), teamState(red),
                true, true, true, true, true, true);
        state.advanceTimeSeconds(1_800);
        return new Fixture(blue, red, state);
    }

    private Team team(String name) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values()) {
            players.add(new Player(name + "-" + position, position,
                    new PlayerAttributes(14, 14, 14, 14)));
        }
        return new Team(name, players);
    }

    private TeamState teamState(Team team) {
        return new TeamState(team.getName(), team.getPlayers().stream()
                .map(player -> new PlayerState(player.getName(), player.getPosition(),
                        player.getAttributes(), 500)).toList());
    }

    private record Fixture(Team blue, Team red, GameState state) {}

    private static final class ZeroRandom extends Random {
        @Override public double nextDouble() { return 0; }
        @Override public boolean nextBoolean() { return false; }
        @Override public int nextInt(int bound) { return 0; }
    }

    private static final class CountingRandom extends Random {
        private int calls;
        @Override public double nextDouble() { calls++; return 0; }
        @Override public boolean nextBoolean() { calls++; return false; }
        @Override public int nextInt(int bound) { calls++; return 0; }
    }
}
