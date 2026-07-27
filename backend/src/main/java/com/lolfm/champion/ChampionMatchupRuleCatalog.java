package com.lolfm.champion;

import com.lolfm.simulator.ProgressionCombatContext;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class ChampionMatchupRuleCatalog {
    public static final String VERSION =
            "generic-matchup-rules-prototype-v2-committed-lane";
    private static final double SUM_TOLERANCE = 1e-12;
    private final Map<ProgressionCombatContext, ContextRules> rules;

    public ChampionMatchupRuleCatalog() {
        EnumMap<ProgressionCombatContext, ContextRules> values =
                new EnumMap<>(ProgressionCombatContext.class);
        values.put(ProgressionCombatContext.LANE_COMBAT,
                rules(1.00, .05, .25, .20, .20, .05, .20, .05));
        values.put(ProgressionCombatContext.JUNGLE_GANK,
                rules(.80, .05, .30, .25, .05, .00, .15, .20));
        values.put(ProgressionCombatContext.COUNTER_GANK,
                rules(.80, .05, .15, .15, .20, .00, .25, .20));
        values.put(ProgressionCombatContext.ROAM,
                rules(.80, .05, .20, .25, .05, .10, .10, .25));
        values.put(ProgressionCombatContext.GENERIC_SKIRMISH,
                rules(.60, .10, .15, .20, .25, .05, .10, .15));
        values.put(ProgressionCombatContext.TEAMFIGHT,
                rules(.35, .10, .15, .10, .20, .05, .25, .15));
        values.put(ProgressionCombatContext.OBJECTIVE_FIGHT,
                rules(.45, .15, .15, .10, .20, .05, .15, .20));
        values.put(ProgressionCombatContext.LATE_GAME_SIEGE,
                rules(.25, .25, .05, .05, .10, .25, .20, .10));
        values.put(ProgressionCombatContext.BASE_DEFENSE,
                rules(.25, .10, .05, .10, .10, .25, .30, .10));
        if (values.size() != ProgressionCombatContext.values().length) {
            throw new IllegalStateException("Every combat context requires rules");
        }
        rules = Map.copyOf(values);
    }

    public double weight(
            ProgressionCombatContext context,
            ChampionMatchupRuleType type
    ) {
        return context(context).weights().get(Objects.requireNonNull(type, "type"));
    }

    public double intensity(ProgressionCombatContext context) {
        return context(context).intensity();
    }

    public Map<ChampionMatchupRuleType, Double> weights(
            ProgressionCombatContext context
    ) {
        return context(context).weights();
    }

    public double weightSum(ProgressionCombatContext context) {
        return weights(context).values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private ContextRules context(ProgressionCombatContext context) {
        return rules.get(Objects.requireNonNull(context, "context"));
    }

    private static ContextRules rules(
            double intensity,
            double range, double engage, double burst, double extended,
            double wave, double peel, double mobility
    ) {
        EnumMap<ChampionMatchupRuleType, Double> weights =
                new EnumMap<>(ChampionMatchupRuleType.class);
        weights.put(ChampionMatchupRuleType.RANGE_POKE_PRESSURE, range);
        weights.put(ChampionMatchupRuleType.ACCESS_ENGAGE_THREAT, engage);
        weights.put(ChampionMatchupRuleType.BURST_PICK_WINDOW, burst);
        weights.put(ChampionMatchupRuleType.EXTENDED_FIGHT_PRESSURE, extended);
        weights.put(ChampionMatchupRuleType.WAVE_TEMPO_CONTROL, wave);
        weights.put(ChampionMatchupRuleType.PEEL_ANTI_DIVE_RESPONSE, peel);
        weights.put(ChampionMatchupRuleType.MOBILITY_PICK_ACCESS, mobility);
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > SUM_TOLERANCE) {
            throw new IllegalArgumentException("Rule weights must sum to 1.0: " + sum);
        }
        if (!Double.isFinite(intensity) || intensity < 0.0 || intensity > 1.0) {
            throw new IllegalArgumentException("Invalid context intensity");
        }
        return new ContextRules(intensity, Map.copyOf(weights));
    }

    private record ContextRules(
            double intensity,
            Map<ChampionMatchupRuleType, Double> weights
    ) {
    }
}
