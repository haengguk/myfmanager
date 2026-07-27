package com.lolfm.champion;

import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ChampionMatchupRuleEngine {
    public static final double PROTOTYPE_MAX_ABSOLUTE_EDGE = 0.30;
    private static final double ZERO_EPSILON = 1e-12;

    private final ChampionRoleMatchupProfileCatalog profiles;
    private final ChampionMatchupRuleCatalog rules;
    private final ChampionMatchupOverrideCatalog overrides;

    public ChampionMatchupRuleEngine(
            ChampionRoleMatchupProfileCatalog profiles,
            ChampionMatchupRuleCatalog rules,
            ChampionMatchupOverrideCatalog overrides
    ) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.overrides = Objects.requireNonNull(overrides, "overrides");
    }

    public ChampionMatchupGeneratedResult calculate(
            ChampionRoleKey source,
            ChampionRoleKey opponent,
            ProgressionCombatContext context
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(opponent, "opponent");
        Objects.requireNonNull(context, "context");
        if (source.position() != opponent.position()) {
            throw new IllegalArgumentException("Cross-position matchup calculation");
        }
        var sourceProfile = profiles.find(source);
        var opponentProfile = profiles.find(opponent);
        double intensity = rules.intensity(context);
        List<ChampionMatchupRuleContribution> contributions = new ArrayList<>();
        double weightedRaw = 0.0;
        if (sourceProfile.isPresent() && opponentProfile.isPresent()) {
            for (ChampionMatchupRuleType type : ChampionMatchupRuleType.values()) {
                double forward = directional(
                        sourceProfile.get(), opponentProfile.get(), type);
                double reverse = directional(
                        opponentProfile.get(), sourceProfile.get(), type);
                double edge = zero(.5 * (forward - reverse));
                double weight = rules.weight(context, type);
                double weighted = zero(weight * edge);
                weightedRaw += weighted;
                contributions.add(new ChampionMatchupRuleContribution(
                        type, forward, reverse, edge, weight, weighted));
            }
        }
        weightedRaw = zero(weightedRaw);
        double base = clamp(zero(weightedRaw * intensity
                * PROTOTYPE_MAX_ABSOLUTE_EDGE));
        double adjustment = overrides.adjustment(
                source.championId(), opponent.championId(),
                source.position(), context);
        double unclamped = zero(base + adjustment);
        double result = clamp(unclamped);
        return new ChampionMatchupGeneratedResult(
                source, opponent, context,
                sourceProfile.isPresent(), opponentProfile.isPresent(),
                contributions, weightedRaw, intensity, base, adjustment, result,
                result != unclamped, unclamped, profiles.version(),
                ChampionMatchupRuleCatalog.VERSION, overrides.version());
    }

    private static double directional(
            ChampionRoleMatchupProfile source,
            ChampionRoleMatchupProfile opponent,
            ChampionMatchupRuleType type
    ) {
        return zero(mean(source, type.sourceTraits())
                - mean(opponent, type.opponentTraits()));
    }

    private static double mean(
            ChampionRoleMatchupProfile profile,
            ChampionMatchupTrait[] traits
    ) {
        double sum = 0.0;
        for (ChampionMatchupTrait trait : traits) {
            sum += profile.normalizedTrait(trait);
        }
        return sum / traits.length;
    }

    private static double clamp(double value) {
        return zero(Math.max(-PROTOTYPE_MAX_ABSOLUTE_EDGE,
                Math.min(PROTOTYPE_MAX_ABSOLUTE_EDGE, value)));
    }

    private static double zero(double value) {
        return Math.abs(value) < ZERO_EPSILON ? 0.0 : value;
    }
}
