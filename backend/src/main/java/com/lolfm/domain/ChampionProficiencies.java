package com.lolfm.domain;

import com.lolfm.champion.ChampionRoleKey;
import java.util.Map;
import java.util.Objects;

public final class ChampionProficiencies {
    public static final int NEUTRAL = 14;
    private final Map<ChampionRoleKey, Integer> values;

    public ChampionProficiencies(Map<ChampionRoleKey, Integer> values) {
        java.util.HashMap<ChampionRoleKey, Integer> copy = new java.util.HashMap<>();
        values.forEach((key, value) -> {
            Objects.requireNonNull(key, "championRoleKey");
            if (value == null || value < 1 || value > 20) {
                throw new IllegalArgumentException("Champion proficiency must be 1..20: " + value);
            }
            copy.put(key, value);
        });
        this.values = Map.copyOf(copy);
    }

    public static ChampionProficiencies neutral() { return new ChampionProficiencies(Map.of()); }
    public int get(ChampionRoleKey key) { return values.getOrDefault(key, NEUTRAL); }
    public Map<ChampionRoleKey, Integer> asMap() { return values; }


    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ChampionProficiencies that && values.equals(that.values);
    }


    @Override
    public int hashCode() { return values.hashCode(); }
}
