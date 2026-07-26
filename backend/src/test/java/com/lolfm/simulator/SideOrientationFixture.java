package com.lolfm.simulator;

import com.lolfm.champion.ChampionLineupRequest;
import com.lolfm.champion.ChampionSelectionRequest;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

record SideOrientationFixture(String id, LogicalLineup teamA, LogicalLineup teamB) {
    SideOrientationFixture {
        teamA = teamA.copy();
        teamB = teamB.copy();
    }

    OrientedFixture orient(Orientation orientation) {
        LogicalLineup blue = orientation == Orientation.ORIGINAL ? teamA : teamB;
        LogicalLineup red = orientation == Orientation.ORIGINAL ? teamB : teamA;
        return new OrientedFixture(
                id,
                orientation,
                blue.team(),
                red.team(),
                new ChampionSelectionRequest(blue.championRequest(), red.championRequest()),
                orientation == Orientation.ORIGINAL ? LogicalTeamId.TEAM_A : LogicalTeamId.TEAM_B,
                orientation == Orientation.ORIGINAL ? LogicalTeamId.TEAM_B : LogicalTeamId.TEAM_A
        );
    }

    SideOrientationFixture mirror() {
        return new SideOrientationFixture(id, teamB, teamA);
    }

    record LogicalLineup(String name, int[] attributes, String[] champions) {
        LogicalLineup {
            attributes = Arrays.copyOf(attributes, attributes.length);
            champions = Arrays.copyOf(champions, champions.length);
            if (attributes.length != Position.values().length || champions.length != Position.values().length) {
                throw new IllegalArgumentException("A complete five-position lineup is required");
            }
        }

        LogicalLineup copy() {
            return new LogicalLineup(name, attributes, champions);
        }

        Team team() {
            List<Player> players = new ArrayList<>();
            for (Position position : Position.values()) {
                int value = attributes[position.ordinal()];
                players.add(new Player(
                        name + "-" + position,
                        position,
                        new PlayerAttributes(value, value, value, value)
                ));
            }
            return new Team(name, players);
        }

        ChampionLineupRequest championRequest() {
            return new ChampionLineupRequest(
                    champions[0], champions[1], champions[2], champions[3], champions[4]);
        }
    }

    record OrientedFixture(
            String fixtureId,
            Orientation orientation,
            Team blue,
            Team red,
            ChampionSelectionRequest champions,
            LogicalTeamId blueLogicalTeam,
            LogicalTeamId redLogicalTeam
    ) {
        LogicalTeamId logicalWinner(TeamSide winner) {
            return winner == TeamSide.BLUE ? blueLogicalTeam : redLogicalTeam;
        }
    }

    enum Orientation {
        ORIGINAL, MIRRORED
    }

    enum LogicalTeamId {
        TEAM_A, TEAM_B
    }
}
