package com.lolfm.domain;

import java.util.List;

public class Team {

    private final String name;
    private final List<Player> players;

    public Team(String name, List<Player> players) {
        this.name = name;
        this.players = List.copyOf(players);
    }

    public String getName() {
        return name;
    }

    public List<Player> getPlayers() {
        return players;
    }
}
