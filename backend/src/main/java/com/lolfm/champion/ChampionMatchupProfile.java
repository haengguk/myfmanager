package com.lolfm.champion;

import com.lolfm.simulator.ProgressionCombatContext;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record ChampionMatchupProfile(
        ChampionMatchupPair pair,
        Map<ProgressionCombatContext, Double> firstChampionEdges
) {
    public ChampionMatchupProfile {
        Objects.requireNonNull(pair, "pair");
        Objects.requireNonNull(firstChampionEdges, "firstChampionEdges");
        EnumMap<ProgressionCombatContext, Double> values =
                new EnumMap<>(ProgressionCombatContext.class);
        for (ProgressionCombatContext context : ProgressionCombatContext.values()) {
            double edge = firstChampionEdges.getOrDefault(context, 0.0);
            if (!Double.isFinite(edge)) {
                throw new IllegalArgumentException("Non-finite matchup edge for " + context);
            }
            values.put(context, normalizeZero(edge));
        }
        firstChampionEdges = Map.copyOf(values);
    }

    public double edge(ProgressionCombatContext context) {
        return firstChampionEdges.get(Objects.requireNonNull(context, "context"));
    }

    private static double normalizeZero(double value) {
        return value == 0.0 ? 0.0 : value;
    }
}
