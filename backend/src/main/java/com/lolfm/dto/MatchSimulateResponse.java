package com.lolfm.dto;

import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Team;

public class MatchSimulateResponse {

    private final long seed;
    private final Team blueTeam;
    private final Team redTeam;
    private final MatchTimeline timeline;

    public MatchSimulateResponse(long seed, Team blueTeam, Team redTeam, MatchTimeline timeline) {
        this.seed = seed;
        this.blueTeam = blueTeam;
        this.redTeam = redTeam;
        this.timeline = timeline;
    }

    public long getSeed() {
        return seed;
    }

    public Team getBlueTeam() {
        return blueTeam;
    }

    public Team getRedTeam() {
        return redTeam;
    }

    public MatchTimeline getTimeline() {
        return timeline;
    }
}
