package com.lolfm.domain;

public class PlayerAttributes {

    private final int mechanics;
    private final int aggression;
    private final int farming;
    private final int teamfighting;

    public PlayerAttributes(int mechanics, int aggression, int farming, int teamfighting) {
        this.mechanics = mechanics;
        this.aggression = aggression;
        this.farming = farming;
        this.teamfighting = teamfighting;
    }

    public int getMechanics() {
        return mechanics;
    }

    public int getAggression() {
        return aggression;
    }

    public int getFarming() {
        return farming;
    }

    public int getTeamfighting() {
        return teamfighting;
    }
}
