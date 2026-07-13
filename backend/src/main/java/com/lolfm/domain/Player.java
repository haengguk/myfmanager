package com.lolfm.domain;

public class Player {

    private final String name;
    private final Position position;
    private final PlayerAttributes attributes;

    public Player(String name, Position position, PlayerAttributes attributes) {
        this.name = name;
        this.position = position;
        this.attributes = attributes;
    }

    public String getName() {
        return name;
    }

    public Position getPosition() {
        return position;
    }

    public PlayerAttributes getAttributes() {
        return attributes;
    }
}
