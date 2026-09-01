package com.lolfm.league;

/** Closed schedule formats understood by the V1 pure domain. */
public enum LeagueScheduleFormat {
    SINGLE_ROUND_ROBIN(1),
    DOUBLE_ROUND_ROBIN(2);

    private final int legs;

    LeagueScheduleFormat(int legs) {
        this.legs = legs;
    }

    public int legs() {
        return legs;
    }
}
