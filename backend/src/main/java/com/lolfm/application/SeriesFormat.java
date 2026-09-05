package com.lolfm.application;

/** Closed V1 match-series formats. */
public enum SeriesFormat {
    BO1(1, 1),
    BO3(2, 3),
    BO5(3, 5);

    private final int winsRequired;
    private final int maximumGames;

    SeriesFormat(int winsRequired, int maximumGames) {
        this.winsRequired = winsRequired;
        this.maximumGames = maximumGames;
    }

    public int winsRequired() { return winsRequired; }
    public int maximumGames() { return maximumGames; }
}
