package com.lolfm.dto;

public class MatchSimulateRequest {

    private Long seed;

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
}
