package com.lolfm.dto;

import com.lolfm.champion.ChampionMatchMetadata;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Team;

public class MatchSimulateResponse {

    private final long seed;
    private final Team blueTeam;
    private final Team redTeam;
    private final MatchTimeline timeline;
    private final ChampionMatchMetadata championMetadata;

    public MatchSimulateResponse(long seed, Team blueTeam, Team redTeam, MatchTimeline timeline) {
        this(seed, blueTeam, redTeam, timeline, null);
    }

    public MatchSimulateResponse(long seed, Team blueTeam, Team redTeam, MatchTimeline timeline,
                                 ChampionMatchMetadata championMetadata) {
        this.seed = seed;
        this.blueTeam = blueTeam;
        this.redTeam = redTeam;
        this.timeline = timeline;
        this.championMetadata = championMetadata;
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

    public ChampionMatchMetadata getChampionMetadata() { return championMetadata; }
}
