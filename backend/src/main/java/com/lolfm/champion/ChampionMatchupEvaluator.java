package com.lolfm.champion;

import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Stateless, pure production evaluator for the frozen geometric-v2 matchup rule. */
public final class ChampionMatchupEvaluator {
    private static final double MAX_EDGE = .30;
    private static final double ZERO_EPSILON = 1e-12;
    private final ChampionRoleMatchupProfileCatalog profiles;
    private final ChampionMatchupRuleCatalog rules;

    public ChampionMatchupEvaluator(ChampionRoleMatchupProfileCatalog profiles) {
        this(profiles, new ChampionMatchupRuleCatalog());
    }

    ChampionMatchupEvaluator(ChampionRoleMatchupProfileCatalog profiles, ChampionMatchupRuleCatalog rules) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public Result evaluate(ChampionRoleKey source, ChampionRoleKey opponent,
                           ProgressionCombatContext context, ChampionMatchupMode mode) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(opponent, "opponent");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(mode, "mode");
        if (mode == ChampionMatchupMode.OFF) return Result.off(source, opponent, context);
        if (mode != ChampionMatchupMode.GEOMETRIC_V2) {
            throw new IllegalArgumentException("Production evaluator requires OFF or GEOMETRIC_V2");
        }
        if (source.position() != opponent.position()) {
            throw new IllegalArgumentException("Cross-position matchup calculation");
        }
        ChampionRoleMatchupProfile a = required(source);
        ChampionRoleMatchupProfile b = required(opponent);
        ChampionMatchupInteractionVector av = ChampionMatchupInteractionVector.from(a);
        ChampionMatchupInteractionVector bv = ChampionMatchupInteractionVector.from(b);
        List<Contribution> contributions = new ArrayList<>();
        double raw = 0.0;
        for (ChampionMatchupRuleType rule : ChampionMatchupRuleType.values()) {
            double forward = directional(av, bv, rule);
            double reverse = directional(bv, av, rule);
            double antisymmetric = zero(forward - reverse);
            double weight = rules.weight(context, rule);
            double weighted = zero(antisymmetric * weight);
            raw += weighted;
            contributions.add(new Contribution(rule, forward, reverse, antisymmetric, weight, weighted));
        }
        raw = zero(raw);
        double intensity = rules.intensity(context);
        double unclamped = zero(raw * intensity * MAX_EDGE * ChampionMatchupProductionPolicy.GEOMETRIC_V2.gain());
        double edge = clamp(unclamped);
        return new Result(source, opponent, context, true, List.copyOf(contributions), raw,
                intensity, unclamped, edge, edge != unclamped);
    }

    private ChampionRoleMatchupProfile required(ChampionRoleKey key) {
        return profiles.find(key).orElseThrow(() -> new UnsupportedChampionRoleMatchupProfileException(key));
    }

    private static double directional(ChampionMatchupInteractionVector source,
                                      ChampionMatchupInteractionVector opponent,
                                      ChampionMatchupRuleType rule) {
        double capability;
        double exposure;
        if (rule == ChampionMatchupRuleType.PEEL_ANTI_DIVE_RESPONSE) {
            capability = source.meanStrength(ChampionMatchupTrait.DISENGAGE, ChampionMatchupTrait.ANTI_DIVE, ChampionMatchupTrait.CROWD_CONTROL);
            double dependency = opponent.meanStrength(ChampionMatchupTrait.ENGAGE, ChampionMatchupTrait.GAP_CLOSE, ChampionMatchupTrait.BURST);
            exposure = opponent.meanVulnerability(ChampionMatchupTrait.DURABILITY, ChampionMatchupTrait.DISENGAGE, ChampionMatchupTrait.MOBILITY);
            return zero(geometricInteraction(capability, dependency) * exposureGate(exposure));
        }
        switch (rule) {
            case RANGE_POKE_PRESSURE -> { capability = source.meanStrength(ChampionMatchupTrait.RANGE_CONTROL, ChampionMatchupTrait.POKE); exposure = opponent.meanVulnerability(ChampionMatchupTrait.SUSTAIN, ChampionMatchupTrait.MOBILITY, ChampionMatchupTrait.WAVE_CONTROL); }
            case ACCESS_ENGAGE_THREAT -> { capability = source.meanStrength(ChampionMatchupTrait.GAP_CLOSE, ChampionMatchupTrait.ENGAGE, ChampionMatchupTrait.CROWD_CONTROL); exposure = opponent.meanVulnerability(ChampionMatchupTrait.DISENGAGE, ChampionMatchupTrait.MOBILITY, ChampionMatchupTrait.ANTI_DIVE); }
            case BURST_PICK_WINDOW -> { capability = source.meanStrength(ChampionMatchupTrait.BURST, ChampionMatchupTrait.PICK, ChampionMatchupTrait.CROWD_CONTROL); exposure = opponent.meanVulnerability(ChampionMatchupTrait.DURABILITY, ChampionMatchupTrait.MOBILITY, ChampionMatchupTrait.ANTI_DIVE); }
            case EXTENDED_FIGHT_PRESSURE -> { capability = source.meanStrength(ChampionMatchupTrait.SUSTAINED_DAMAGE, ChampionMatchupTrait.SUSTAIN, ChampionMatchupTrait.ANTI_TANK); exposure = opponent.meanVulnerability(ChampionMatchupTrait.DURABILITY, ChampionMatchupTrait.DISENGAGE, ChampionMatchupTrait.RANGE_CONTROL); }
            case WAVE_TEMPO_CONTROL -> { capability = source.meanStrength(ChampionMatchupTrait.WAVE_CONTROL, ChampionMatchupTrait.RANGE_CONTROL, ChampionMatchupTrait.POKE); exposure = opponent.meanVulnerability(ChampionMatchupTrait.WAVE_CONTROL, ChampionMatchupTrait.SUSTAIN, ChampionMatchupTrait.RANGE_CONTROL); }
            case MOBILITY_PICK_ACCESS -> { capability = source.meanStrength(ChampionMatchupTrait.MOBILITY, ChampionMatchupTrait.PICK, ChampionMatchupTrait.GAP_CLOSE); exposure = opponent.meanVulnerability(ChampionMatchupTrait.MOBILITY, ChampionMatchupTrait.DISENGAGE, ChampionMatchupTrait.DURABILITY); }
            default -> throw new IllegalStateException("Unknown matchup rule " + rule);
        }
        return zero(geometricInteraction(capability, exposure));
    }

    public static double geometricInteraction(double a, double b) { return a <= 0 || b <= 0 ? 0.0 : zero(Math.sqrt(a * b)); }
    public static double exposureGate(double exposure) { return zero(.25 + .75 * Math.max(0, Math.min(1, exposure))); }
    private static double clamp(double value) { return zero(Math.max(-MAX_EDGE, Math.min(MAX_EDGE, value))); }
    private static double zero(double value) { return Math.abs(value) < ZERO_EPSILON ? 0.0 : value; }

    public record Contribution(ChampionMatchupRuleType ruleType, double forwardDirectional,
                               double reverseDirectional, double antisymmetricRuleEdge,
                               double contextWeight, double weightedContribution) {}
    public record Result(ChampionRoleKey source, ChampionRoleKey opponent, ProgressionCombatContext context,
                         boolean enabled, List<Contribution> contributions, double weightedRawEdge,
                         double contextIntensity, double unclampedEdge, double finalEdge, boolean clamped) {
        public Result { contributions = List.copyOf(contributions); }
        static Result off(ChampionRoleKey source, ChampionRoleKey opponent, ProgressionCombatContext context) {
            return new Result(source, opponent, context, false, List.of(), 0, 0, 0, 0, false);
        }
    }
}
