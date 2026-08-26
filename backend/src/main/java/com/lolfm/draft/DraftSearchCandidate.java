package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import java.util.Map;
import java.util.Objects;

/** One fully evaluated root candidate; selection must never reevaluate it. */
public record DraftSearchCandidate(
        ChampionId championId,
        double immediateScore,
        double continuationScore,
        double finalSearchScore,
        Map<String, Double> componentBreakdown
) {
    public DraftSearchCandidate {
        Objects.requireNonNull(championId, "championId");
        if (!Double.isFinite(immediateScore) || !Double.isFinite(continuationScore)
                || !Double.isFinite(finalSearchScore)) {
            throw new IllegalArgumentException("Draft candidate scores must be finite");
        }
        componentBreakdown = Map.copyOf(componentBreakdown);
    }
}
