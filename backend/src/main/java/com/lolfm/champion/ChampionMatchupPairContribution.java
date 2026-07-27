package com.lolfm.champion;

import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.Objects;

public record ChampionMatchupPairContribution(
        ChampionMatchupPair pair,
        PlayerKey source,
        PlayerKey opponent,
        ProgressionCombatContext context,
        double edge
) {
    public ChampionMatchupPairContribution {
        Objects.requireNonNull(pair, "pair");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(opponent, "opponent");
        Objects.requireNonNull(context, "context");
        if (!Double.isFinite(edge)) throw new IllegalArgumentException("Non-finite matchup edge");
        edge = edge == 0.0 ? 0.0 : edge;
    }
}
