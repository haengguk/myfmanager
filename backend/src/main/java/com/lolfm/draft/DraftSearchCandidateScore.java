package com.lolfm.draft;

import com.lolfm.champion.ChampionId;

public record DraftSearchCandidateScore(
        ChampionId championId,
        double immediateScore,
        double continuationScore,
        double finalSearchScore
) { }
