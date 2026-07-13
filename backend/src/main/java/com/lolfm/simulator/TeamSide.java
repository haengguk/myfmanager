package com.lolfm.simulator;

public enum TeamSide {
    BLUE,
    RED;

    public TeamSide opposite() {
        return this == BLUE ? RED : BLUE;
    }
}
