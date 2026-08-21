package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.factory.DummyDataFactory;
import com.lolfm.player.PlayerId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchLineupIdentityValidatorTest {
    private final DummyDataFactory teams = new DummyDataFactory();

    @Test
    void tenDistinctStablePlayerIdsPass() {
        assertThatCode(() -> MatchLineupIdentityValidator.validate(
                teams.createBlueTeam(), teams.createRedTeam())).doesNotThrowAnyException();
    }

    @Test
    void duplicateStablePlayerIdIsRejectedWithinOneTeamAndAcrossTeams() {
        Team blue = teams.createBlueTeam();
        Player blueTop = blue.getPlayers().getFirst();
        Team duplicateWithinBlue = replace(blue, 2,
                stable(blueTop.requirePlayerId(), "Different Mid", Position.MID));
        assertThatThrownBy(() -> MatchLineupIdentityValidator.validate(
                duplicateWithinBlue, teams.createRedTeam()))
                .hasMessageContaining("DUPLICATE_MATCH_PLAYER_ID")
                .hasMessageContaining(blueTop.requirePlayerId().value());

        Team red = replace(teams.createRedTeam(), 2,
                stable(blueTop.requirePlayerId(), "Different Red Name", Position.MID));
        assertThatThrownBy(() -> MatchLineupIdentityValidator.validate(blue, red))
                .hasMessageContaining("DUPLICATE_MATCH_PLAYER_ID")
                .hasMessageContaining("BLUE:TOP")
                .hasMessageContaining("RED:MID");
    }

    @Test
    void nicknameEqualityIsNotAnIdentityCollisionButIdEqualityIs() {
        Team blue = teams.createBlueTeam();
        Player blueMid = blue.getPlayers().get(2);
        Player originalRedMid = teams.createRedTeam().getPlayers().get(2);
        Team sameNicknameDifferentId = replace(teams.createRedTeam(), 2,
                stable(originalRedMid.requirePlayerId(), blueMid.getName(), Position.MID));
        assertThatCode(() -> MatchLineupIdentityValidator.validate(blue, sameNicknameDifferentId))
                .doesNotThrowAnyException();

        Team sameIdDifferentNickname = replace(teams.createRedTeam(), 2,
                stable(blueMid.requirePlayerId(), "Not " + blueMid.getName(), Position.MID));
        assertThatThrownBy(() -> MatchLineupIdentityValidator.validate(blue, sameIdDifferentNickname))
                .hasMessageContaining("DUPLICATE_MATCH_PLAYER_ID");
    }

    @Test
    void legacyNullIdsRemainAllowed() {
        Team blue = legacyTeam("BLUE");
        Team red = legacyTeam("RED");
        assertThatCode(() -> MatchLineupIdentityValidator.validate(blue, red))
                .doesNotThrowAnyException();
    }

    @Test
    void simulatorRejectsDuplicateBeforeTheSeededRandomIsConsumed() {
        Team blue = teams.createBlueTeam();
        Team red = replace(teams.createRedTeam(), 2,
                stable(blue.getPlayers().get(2).requirePlayerId(), "Collision", Position.MID));
        ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
        var assignments = new ChampionSelectionValidator(champions).resolve(null);
        SideOrientationRandomTraceObserver random = new SideOrientationRandomTraceObserver(
                73L, "IDENTITY_REJECTION", "BLUE", "RED", true);

        assertThatThrownBy(() -> simulator(champions).simulateWithSideDiagnostics(
                blue, red, assignments, random))
                .hasMessageContaining("DUPLICATE_MATCH_PLAYER_ID");
        assertThat(random.drawCount()).isZero();
    }

    private MatchSimulator simulator(ChampionCatalog champions) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(),
                new SnapshotFactory(champions), new ObjectiveResolver(), new PostFightResolver(),
                new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver());
    }

    private Team replace(Team team, int index, Player replacement) {
        List<Player> players = new ArrayList<>(team.getPlayers());
        players.set(index, replacement);
        return new Team(team.getName(), players);
    }

    private Player stable(PlayerId playerId, String nickname, Position position) {
        return new Player(playerId, nickname, position, new PlayerAttributes(14, 14, 14, 14));
    }

    private Team legacyTeam(String prefix) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values()) {
            players.add(new Player("Same Display " + position, position,
                    new PlayerAttributes(14, 14, 14, 14)));
        }
        return new Team(prefix, players);
    }
}
