package com.lolfm.simulator;

import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/** Immutable match-scoped realization. It never mutates the base player profile. */
public final class PlayerMatchPerformance {
    private final Map<PlayerSkill, Double> realizedRatings;
    private final int championProficiency;

    private PlayerMatchPerformance(Map<PlayerSkill, Double> realizedRatings, int championProficiency) {
        this.realizedRatings = Map.copyOf(realizedRatings);
        this.championProficiency = championProficiency;
    }

    public static PlayerMatchPerformance realize(PlayerRatings base, int proficiency,
                                                 long matchSeed, TeamSide side) {
        EnumMap<PlayerSkill, Double> values = new EnumMap<>(PlayerSkill.class);
        boolean neutralDefault = base.asMap().values().stream().allMatch(value -> value == PlayerRatings.NEUTRAL);
        int consistency = base.get(PlayerSkill.CONSISTENCY);
        double spread = neutralDefault || consistency == PlayerRatings.MAX
                ? 0.0
                : (PlayerRatings.MAX - consistency)
                        * PlayerRatingRuleConfig.REALIZATION_SPREAD_PER_MISSING_CONSISTENCY;
        Random realizationRandom = new Random(realizationSeed(matchSeed, side, base.position()));
        // Random consumption is bound to enum declaration order, never Set iteration order.
        for (PlayerSkill skill : PlayerSkill.orderedForPosition(base.position())) {
            double value = base.get(skill);
            if (skill != PlayerSkill.CONSISTENCY && spread > 0.0) {
                double triangular = realizationRandom.nextDouble() + realizationRandom.nextDouble() - 1.0;
                value = PlayerRatingRuleConfig.clampRating(value + triangular * spread);
            }
            values.put(skill, value);
        }
        return new PlayerMatchPerformance(values, proficiency);
    }

    public double rating(PlayerSkill skill) {
        Double value = realizedRatings.get(skill);
        if (value == null) throw new IllegalArgumentException(skill + " is not part of this player's role");
        return value;
    }

    /** Only champion-tool execution checks call this method; cognitive ratings never do. */
    public double execution(PlayerSkill skill) {
        return PlayerRatingRuleConfig.clampRating(
                rating(skill) + PlayerRatingRuleConfig.proficiencyAdjustment(championProficiency));
    }

    public int championProficiency() { return championProficiency; }
    public Map<PlayerSkill, Double> asMap() { return realizedRatings; }

    private static long realizationSeed(long matchSeed, TeamSide side, com.lolfm.domain.Position position) {
        long value = matchSeed ^ 0x9E3779B97F4A7C15L;
        value ^= (long) (side.ordinal() + 1) * 0xBF58476D1CE4E5B9L;
        value ^= (long) (position.ordinal() + 1) * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
