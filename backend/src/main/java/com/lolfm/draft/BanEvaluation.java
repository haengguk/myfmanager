package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import java.util.Map;

public record BanEvaluation(ChampionId championId, double finalScore,
                            Map<BanScoreComponent, Double> components) {
    public BanEvaluation { components = Map.copyOf(components); }
}
