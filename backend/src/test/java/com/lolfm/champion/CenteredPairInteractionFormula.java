package com.lolfm.champion;

import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CenteredPairInteractionFormula {
    public static final String VERSION = "pair-interaction-centered-exploit-v1";
    public static final double MAX_ABSOLUTE_EDGE = .30;
    private final ChampionMatchupRuleCatalog rules;

    public CenteredPairInteractionFormula(ChampionMatchupRuleCatalog rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public Result evaluate(ChampionRoleMatchupProfile source,
                           ChampionRoleMatchupProfile opponent,
                           ProgressionCombatContext context) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(opponent, "opponent");
        Objects.requireNonNull(context, "context");
        if (source.roleKey().position() != opponent.roleKey().position()) {
            throw new IllegalArgumentException("Cross-position interaction");
        }
        ChampionMatchupInteractionVector sourceVector =
                ChampionMatchupInteractionVector.from(source);
        ChampionMatchupInteractionVector opponentVector =
                ChampionMatchupInteractionVector.from(opponent);
        List<Contribution> contributions = new ArrayList<>();
        double weightedRaw = 0;
        for (ChampionMatchupRuleType type : ChampionMatchupRuleType.values()) {
            Directional forward = directional(sourceVector, opponentVector, type);
            Directional reverse = directional(opponentVector, sourceVector, type);
            double ruleEdge = zero(forward.value() - reverse.value());
            double weight = rules.weight(context, type);
            double weighted = zero(ruleEdge * weight);
            weightedRaw += weighted;
            contributions.add(new Contribution(type, forward.capability(),
                    forward.vulnerabilityOrDependency(), forward.value(),
                    reverse.capability(), reverse.vulnerabilityOrDependency(),
                    reverse.value(), ruleEdge, weight, weighted));
        }
        weightedRaw = zero(weightedRaw);
        double intensity = rules.intensity(context);
        double unclamped = zero(weightedRaw * intensity * MAX_ABSOLUTE_EDGE);
        double finalEdge = clamp(unclamped);
        return new Result(source.roleKey(), opponent.roleKey(), context,
                "PAIR_INTERACTION_V1", sourceVector.profileMean(),
                opponentVector.profileMean(), contributions, weightedRaw,
                intensity, unclamped, finalEdge,
                Double.compare(unclamped, finalEdge) != 0,
                Double.doubleToRawLongBits(finalEdge)
                        == Double.doubleToRawLongBits(-0.0d),
                source.profileVersion(), ChampionMatchupRuleCatalog.VERSION,
                VERSION);
    }

    private static Directional directional(
            ChampionMatchupInteractionVector source,
            ChampionMatchupInteractionVector opponent,
            ChampionMatchupRuleType type
    ) {
        double capability;
        double dependency;
        switch (type) {
            case RANGE_POKE_PRESSURE -> {
                capability = source.meanStrength(
                        ChampionMatchupTrait.RANGE_CONTROL,
                        ChampionMatchupTrait.POKE);
                dependency = opponent.meanVulnerability(
                        ChampionMatchupTrait.SUSTAIN,
                        ChampionMatchupTrait.MOBILITY,
                        ChampionMatchupTrait.WAVE_CONTROL);
            }
            case ACCESS_ENGAGE_THREAT -> {
                capability = source.meanStrength(
                        ChampionMatchupTrait.GAP_CLOSE,
                        ChampionMatchupTrait.ENGAGE,
                        ChampionMatchupTrait.CROWD_CONTROL);
                dependency = opponent.meanVulnerability(
                        ChampionMatchupTrait.DISENGAGE,
                        ChampionMatchupTrait.MOBILITY,
                        ChampionMatchupTrait.ANTI_DIVE);
            }
            case BURST_PICK_WINDOW -> {
                capability = source.meanStrength(
                        ChampionMatchupTrait.BURST,
                        ChampionMatchupTrait.PICK,
                        ChampionMatchupTrait.CROWD_CONTROL);
                dependency = opponent.meanVulnerability(
                        ChampionMatchupTrait.DURABILITY,
                        ChampionMatchupTrait.MOBILITY,
                        ChampionMatchupTrait.ANTI_DIVE);
            }
            case EXTENDED_FIGHT_PRESSURE -> {
                capability = source.meanStrength(
                        ChampionMatchupTrait.SUSTAINED_DAMAGE,
                        ChampionMatchupTrait.SUSTAIN,
                        ChampionMatchupTrait.ANTI_TANK);
                dependency = opponent.meanVulnerability(
                        ChampionMatchupTrait.DURABILITY,
                        ChampionMatchupTrait.DISENGAGE,
                        ChampionMatchupTrait.RANGE_CONTROL);
            }
            case WAVE_TEMPO_CONTROL -> {
                capability = source.meanStrength(
                        ChampionMatchupTrait.WAVE_CONTROL,
                        ChampionMatchupTrait.RANGE_CONTROL,
                        ChampionMatchupTrait.POKE);
                dependency = opponent.meanVulnerability(
                        ChampionMatchupTrait.WAVE_CONTROL,
                        ChampionMatchupTrait.SUSTAIN,
                        ChampionMatchupTrait.RANGE_CONTROL);
            }
            case PEEL_ANTI_DIVE_RESPONSE -> {
                capability = source.meanStrength(
                        ChampionMatchupTrait.DISENGAGE,
                        ChampionMatchupTrait.ANTI_DIVE,
                        ChampionMatchupTrait.CROWD_CONTROL);
                dependency = opponent.meanStrength(
                        ChampionMatchupTrait.ENGAGE,
                        ChampionMatchupTrait.GAP_CLOSE,
                        ChampionMatchupTrait.BURST);
            }
            case MOBILITY_PICK_ACCESS -> {
                capability = source.meanStrength(
                        ChampionMatchupTrait.MOBILITY,
                        ChampionMatchupTrait.PICK,
                        ChampionMatchupTrait.GAP_CLOSE);
                dependency = opponent.meanVulnerability(
                        ChampionMatchupTrait.MOBILITY,
                        ChampionMatchupTrait.DISENGAGE,
                        ChampionMatchupTrait.DURABILITY);
            }
            default -> throw new IllegalStateException("Unknown rule " + type);
        }
        return new Directional(capability, dependency,
                zero(capability * dependency));
    }

    private static double clamp(double value) {
        return zero(Math.max(-MAX_ABSOLUTE_EDGE,
                Math.min(MAX_ABSOLUTE_EDGE, value)));
    }
    private static double zero(double value) {
        return Math.abs(value) < 1e-12 ? 0.0 : value;
    }
    private record Directional(double capability,
                               double vulnerabilityOrDependency,
                               double value) { }

    public record Contribution(ChampionMatchupRuleType ruleType,
            double sourceCapability, double opponentVulnerabilityOrDependency,
            double directionalSourceToOpponent, double opponentCapability,
            double sourceVulnerabilityOrDependency,
            double directionalOpponentToSource, double antisymmetricRuleEdge,
            double contextWeight, double weightedContribution) { }

    public record Result(ChampionRoleKey sourceRoleKey,
            ChampionRoleKey opponentRoleKey, ProgressionCombatContext context,
            String formulaType, double sourceProfileMean,
            double opponentProfileMean, List<Contribution> ruleContributions,
            double weightedRawEdge, double contextIntensity,
            double unclampedEdge, double finalEdge, boolean clamped,
            boolean negativeZeroNormalized, String profileVersion,
            String ruleVersion, String formulaVersion) {
        public Result {
            ruleContributions = List.copyOf(ruleContributions);
        }
    }
}
