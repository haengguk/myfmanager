package com.lolfm.dto;

import com.lolfm.champion.ChampionSelectionRequest;

public class MatchSimulateRequest {

    private Long seed;
    private ChampionSelectionRequest championSelection;

    public MatchSimulateRequest() {
    }

    public MatchSimulateRequest(Long seed) {
        this.seed = seed;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public ChampionSelectionRequest getChampionSelection() { return championSelection; }
    public void setChampionSelection(ChampionSelectionRequest championSelection) { this.championSelection = championSelection; }
}
