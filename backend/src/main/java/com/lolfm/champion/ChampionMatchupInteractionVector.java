package com.lolfm.champion;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record ChampionMatchupInteractionVector(
        ChampionRoleKey roleKey,
        double profileMean,
        Map<ChampionMatchupTrait, TraitValue> traits
) {
    public ChampionMatchupInteractionVector {
        Objects.requireNonNull(roleKey, "roleKey");
        Objects.requireNonNull(traits, "traits");
        traits = Map.copyOf(new EnumMap<>(traits));
        if (traits.size() != ChampionMatchupTrait.values().length) {
            throw new IllegalArgumentException("All interaction traits required");
        }
        if (!Double.isFinite(profileMean)) {
            throw new IllegalArgumentException("Finite profile mean required");
        }
    }

    public static ChampionMatchupInteractionVector from(
            ChampionRoleMatchupProfile profile
    ) {
        EnumMap<ChampionMatchupTrait, Double> raw =
                new EnumMap<>(ChampionMatchupTrait.class);
        for (ChampionMatchupTrait trait : ChampionMatchupTrait.values()) {
            raw.put(trait, zero(profile.normalizedTrait(trait)));
        }
        double mean = raw.values().stream().mapToDouble(Double::doubleValue)
                .average().orElseThrow();
        EnumMap<ChampionMatchupTrait, TraitValue> values =
                new EnumMap<>(ChampionMatchupTrait.class);
        for (ChampionMatchupTrait trait : ChampionMatchupTrait.values()) {
            double value = raw.get(trait);
            double centered = zero(value - mean);
            double strength = zero(Math.max(centered, 0.0) * value);
            double vulnerability =
                    zero(Math.max(-centered, 0.0) * (1.0 - value));
            values.put(trait, new TraitValue(value, centered,
                    strength, vulnerability));
        }
        return new ChampionMatchupInteractionVector(
                profile.roleKey(), zero(mean), values);
    }

    public TraitValue trait(ChampionMatchupTrait trait) {
        return traits.get(Objects.requireNonNull(trait, "trait"));
    }

    public double meanStrength(ChampionMatchupTrait... selected) {
        return mean(selected, true);
    }

    public double meanVulnerability(ChampionMatchupTrait... selected) {
        return mean(selected, false);
    }

    private double mean(ChampionMatchupTrait[] selected, boolean strength) {
        if (selected.length == 0) throw new IllegalArgumentException("traits required");
        double sum = 0;
        for (ChampionMatchupTrait trait : selected) {
            TraitValue value = trait(trait);
            sum += strength ? value.interactionStrength()
                    : value.interactionVulnerability();
        }
        return zero(sum / selected.length);
    }

    private static double zero(double value) {
        return Math.abs(value) < 1e-12 ? 0.0 : value;
    }

    public record TraitValue(double raw, double centered,
                             double interactionStrength,
                             double interactionVulnerability) {
        public TraitValue {
            if (!Double.isFinite(raw) || !Double.isFinite(centered)
                    || !Double.isFinite(interactionStrength)
                    || !Double.isFinite(interactionVulnerability)) {
                throw new IllegalArgumentException("Finite interaction values required");
            }
            raw = zero(raw);
            centered = zero(centered);
            interactionStrength = zero(interactionStrength);
            interactionVulnerability = zero(interactionVulnerability);
        }
    }
}
