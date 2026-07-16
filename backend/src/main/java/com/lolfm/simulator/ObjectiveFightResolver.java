package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Stateless adapter around the existing teamfight score and common kill/reward path. */
public final class ObjectiveFightResolver {
    private final TeamfightResolver teamfights = new TeamfightResolver();

    public ObjectiveFightOutcome resolve(GameState state, Random random, List<MatchEvent> events) {
        Team blue = domainTeam(state.getBlueTeamState());
        Team red = domainTeam(state.getRedTeamState());
        double advantage = (state.getBlueTeamState().getGold() - state.getRedTeamState().getGold()) / 500.0
                + (state.getBlueTeamState().getKills() - state.getRedTeamState().getKills()) * 11.0
                + teamfights.teamfightScore(state, TeamSide.BLUE, blue)
                - teamfights.teamfightScore(state, TeamSide.RED, red)
                + (random.nextDouble() - .5) * 56.0;
        TeamSide winner = advantage >= 0 ? TeamSide.BLUE : TeamSide.RED;
        Team winningTeam = winner == TeamSide.BLUE ? blue : red;
        Team losingTeam = winner == TeamSide.BLUE ? red : blue;
        TeamState winningState = state.getTeamState(winner);
        TeamState losingState = state.getTeamState(winner.opposite());
        List<String> participants = new ArrayList<>();
        markParticipants(state, state.getBlueTeamState(), participants);
        markParticipants(state, state.getRedTeamState(), participants);
        events.add(new MatchEvent(state.getCurrentTimeSeconds(), MatchEventType.TEAMFIGHT,
                "오브젝트 지역에서 양 팀이 교전을 시작합니다.", null, null, List.of()));
        Set<String> dead = new HashSet<>();
        int before = events.size();
        boolean killed = teamfights.resolveKill(state.getCurrentTimeSeconds(), random, winningTeam, winningState,
                losingTeam, losingState, events, true, dead);
        if (killed) {
            for (int i = before; i < events.size(); i++) {
                if (events.get(i).getType() == MatchEventType.KILL) events.get(i).setCombatSource(CombatSource.OBJECTIVE_FIGHT);
            }
        }
        teamfights.commitPendingCombatProgress(state.getBlueTeamState());
        teamfights.commitPendingCombatProgress(state.getRedTeamState());
        events.add(new MatchEvent(state.getCurrentTimeSeconds(), MatchEventType.TEAMFIGHT_RESULT,
                winningState.getTeamName() + "가 오브젝트 교전에서 승리합니다.", null, null, List.of()));
        return new ObjectiveFightOutcome(winner, killed ? 1 : 0, participants);
    }

    private void markParticipants(GameState state, TeamState team, List<String> participants) {
        int time = state.getCurrentTimeSeconds();
        for (PlayerState player : team.getPlayers()) {
            if (player.canParticipateInMajorCombatAt(time)) {
                state.markMajorCombatParticipant(player);
                participants.add(player.getPlayerName());
            }
        }
    }

    private Team domainTeam(TeamState state) {
        List<Player> players = new ArrayList<>();
        for (PlayerState player : state.getPlayers()) {
            players.add(new Player(player.getPlayerName(), player.getPosition(), new PlayerAttributes(
                    player.getMechanics(), player.getAggression(), player.getFarming(), player.getTeamfighting())));
        }
        return new Team(state.getTeamName(), players);
    }
}
