package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import java.util.Objects;

public record DraftSelectionPoolEntry(
        ChampionId championId,
        int canonicalRank,
        double rawFinalSearchScore,
        long canonicalFinalScore,
        long canonicalScoreLoss,
        int rankWeight
) {
    public DraftSelectionPoolEntry {
        Objects.requireNonNull(championId, "championId");
        if (canonicalRank < 1 || canonicalRank > 3 || !Double.isFinite(rawFinalSearchScore)
                || canonicalScoreLoss < 0 || rankWeight < 1) {
            throw new IllegalArgumentException("Invalid Draft selection pool entry");
        }
    }
}
