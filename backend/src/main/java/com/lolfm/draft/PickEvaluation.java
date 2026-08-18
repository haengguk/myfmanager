package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import java.util.Map;

public record PickEvaluation(ChampionId championId, double finalScore,
                             Map<PickScoreComponent, Double> components,
                             boolean legal) {
    public PickEvaluation { components = Map.copyOf(components); }
}
