package com.lolfm.champion;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record ChampionRoleMatchupProfile(
        ChampionRoleKey roleKey,
        String profileVersion,
        Map<ChampionMatchupTrait, Integer> traits
) {
    public ChampionRoleMatchupProfile {
        Objects.requireNonNull(roleKey, "roleKey");
        if (profileVersion == null || profileVersion.isBlank()) {
            throw new IllegalArgumentException("profileVersion must not be blank");
        }
        Objects.requireNonNull(traits, "traits");
        EnumMap<ChampionMatchupTrait, Integer> copy =
                new EnumMap<>(ChampionMatchupTrait.class);
        copy.putAll(traits);
        if (copy.size() != ChampionMatchupTrait.values().length) {
            throw new IllegalArgumentException("All matchup traits are required");
        }
        for (ChampionMatchupTrait trait : ChampionMatchupTrait.values()) {
            Integer value = copy.get(trait);
            if (value == null || value < 1 || value > 20) {
                throw new IllegalArgumentException(
                        "Trait " + trait + " must be between 1 and 20");
            }
        }
        traits = Map.copyOf(copy);
    }

    public int trait(ChampionMatchupTrait trait) {
        return traits.get(Objects.requireNonNull(trait, "trait"));
    }

    public double normalizedTrait(ChampionMatchupTrait trait) {
        return (trait(trait) - 1.0) / 19.0;
    }
}
