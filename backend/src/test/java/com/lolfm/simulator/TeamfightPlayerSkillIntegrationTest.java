package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.player.PlayerId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamfightPlayerSkillIntegrationTest {
    private final TeamfightResolver resolver = new TeamfightResolver();

    @Test
    void engageAndProtectionIncreaseTeamfightScoreWithCompositionOff() {
        Fixture baseline = fixture(14, 14);
        Fixture strong = fixture(20, 20);

        double baselineScore = resolver.teamfightScore(
                baseline.state(), TeamSide.BLUE, baseline.blue());
        double strongScore = resolver.teamfightScore(
                strong.state(), TeamSide.BLUE, strong.blue());

        assertTrue(strongScore > baselineScore);
        assertEquals((20 * .70 + 14 * .30 - 14)
                        * CombatParticipantRuleConfig.COMPOSITION_OFF_ENGAGE_SCORE_PER_POINT
                        + (20 * .70 + 14 * .30 - 14)
                        * CombatParticipantRuleConfig.COMPOSITION_OFF_PROTECTION_SCORE_PER_POINT,
                strongScore - baselineScore, 1e-9);
    }

    @Test
    void unavailableSupportContributesNeitherEngageNorProtection() {
        Fixture baseline = fixture(14, 14);
        Fixture strong = fixture(20, 20);
        baseline.state().advanceTimeSeconds(300);
        strong.state().advanceTimeSeconds(300);
        baseline.state().getBlueTeamState().playerAt(Position.SUPPORT)
                .beginRoamActivity(Lane.BOT, Lane.MID, 300);
        strong.state().getBlueTeamState().playerAt(Position.SUPPORT)
                .beginRoamActivity(Lane.BOT, Lane.MID, 300);

        assertEquals(resolver.teamfightScore(
                        baseline.state(), TeamSide.BLUE, baseline.blue()),
                resolver.teamfightScore(strong.state(), TeamSide.BLUE, strong.blue()), 1e-9);

        Fixture deadBaseline = fixture(14, 14);
        Fixture deadStrong = fixture(20, 20);
        deadBaseline.state().getBlueTeamState().playerAt(Position.SUPPORT).markDead(0, 30);
        deadStrong.state().getBlueTeamState().playerAt(Position.SUPPORT).markDead(0, 30);
        assertEquals(resolver.teamfightScore(
                        deadBaseline.state(), TeamSide.BLUE, deadBaseline.blue()),
                resolver.teamfightScore(deadStrong.state(), TeamSide.BLUE, deadStrong.blue()), 1e-9);
    }

    @Test
    void aliveRoamingPlayerIsExcludedFromTeamfightPowerAndHeadcount() {
        Fixture fixture = fixture(14, 14);
        double available = resolver.teamfightScore(
                fixture.state(), TeamSide.BLUE, fixture.blue());
        fixture.state().advanceTimeSeconds(300);
        fixture.state().getBlueTeamState().playerAt(Position.TOP)
                .beginRoamActivity(Lane.TOP, Lane.MID, 300);

        double unavailable = resolver.teamfightScore(
                fixture.state(), TeamSide.BLUE, fixture.blue());

        assertEquals(PlayerImpactRuleConfig.ALIVE_PLAYER_SCORE_WEIGHT,
                available - unavailable, 1e-9);
    }

    private Fixture fixture(int engage, int protection) {
        TeamState blueState = explicitTeam(TeamSide.BLUE, engage, protection);
        TeamState redState = explicitTeam(TeamSide.RED, 14, 14);
        return new Fixture(domainTeam("BLUE"), domainTeam("RED"),
                new GameState(blueState, redState));
    }

    private TeamState explicitTeam(TeamSide side, int engage, int protection) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) {
            PlayerRatings ratings = PlayerRatings.neutral(position)
                    .with(PlayerSkill.CONSISTENCY, 20);
            if (position == Position.SUPPORT) {
                ratings = ratings.with(PlayerSkill.ENGAGE_EXECUTION, engage)
                        .with(PlayerSkill.ALLY_PROTECTION, protection);
            }
            PlayerMatchPerformance performance = PlayerMatchPerformance.realize(
                    ratings, 14, 91L, side);
            players.add(new PlayerState(new PlayerKey(side, position),
                    new PlayerId("player-teamfight-" + side.name().toLowerCase()
                            + "-" + position.name().toLowerCase()),
                    side + "-" + position, position, new PlayerAttributes(14, 14, 14, 14),
                    performance, 500, true));
        }
        return new TeamState(side.name(), players);
    }

    private Team domainTeam(String name) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values()) {
            players.add(new Player(name + "-" + position, position,
                    new PlayerAttributes(14, 14, 14, 14)));
        }
        return new Team(name, players);
    }

    private record Fixture(Team blue, Team red, GameState state) { }
}
