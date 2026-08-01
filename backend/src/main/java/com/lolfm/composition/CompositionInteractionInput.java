package com.lolfm.composition;

import com.lolfm.champion.ChampionRoleKey;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, analysis-only input for composition interaction evaluation. */
public record CompositionInteractionInput(
        TeamCompositionLineup lineup,
        Map<CompositionCapability, Double> capabilityCoverage,
        Map<CompositionPattern, Double> patternReadiness,
        TeamCompositionExplanation explanation
) {
    public CompositionInteractionInput {
        Objects.requireNonNull(lineup, "lineup");
        Objects.requireNonNull(capabilityCoverage, "capabilityCoverage");
        Objects.requireNonNull(patternReadiness, "patternReadiness");
        Objects.requireNonNull(explanation, "explanation");
        capabilityCoverage = copyCapabilities(capabilityCoverage);
        patternReadiness = copyPatterns(patternReadiness);
    }

    public static CompositionInteractionInput fromAnalysis(TeamCompositionAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis");
        EnumMap<CompositionCapability, Double> capabilities = new EnumMap<>(CompositionCapability.class);
        for (CompositionCapability capability : CompositionCapability.values()) {
            capabilities.put(capability, analysis.coverage().capability(capability).coverage());
        }
        EnumMap<CompositionPattern, Double> patterns = new EnumMap<>(CompositionPattern.class);
        for (CompositionPattern pattern : CompositionPattern.values()) {
            patterns.put(pattern, analysis.patterns().get(pattern).readiness());
        }
        return new CompositionInteractionInput(analysis.lineup(), capabilities, patterns, analysis.explanation());
    }

    public List<ChampionRoleKey> contributors(CompositionSignalRef signal) {
        Objects.requireNonNull(signal, "signal");
        if (signal instanceof CapabilitySignalRef capability) {
            for (CapabilityExplanation explanation : explanation.capabilities()) {
                if (explanation.capability() == capability.capability()) return List.copyOf(explanation.contributors().stream().map(CapabilityContributor::championRoleKey).toList());
            }
        } else {
            CompositionPattern pattern = ((PatternSignalRef) signal).pattern();
            for (PatternExplanation explanation : explanation.patterns()) {
                if (explanation.pattern() == pattern) return List.copyOf(explanation.primaryContributors());
            }
        }
        return List.of();
    }

    private static Map<CompositionCapability, Double> copyCapabilities(Map<CompositionCapability, Double> source) {
        if (source.size() != CompositionCapability.values().length) throw new IllegalArgumentException("Exactly fifteen capabilities required");
        EnumMap<CompositionCapability, Double> copy = new EnumMap<>(CompositionCapability.class);
        for (CompositionCapability capability : CompositionCapability.values()) {
            Double value = source.get(capability);
            validate(value, capability.name());
            copy.put(capability, normalizeZero(value));
        }
        return Map.copyOf(copy);
    }

    private static Map<CompositionPattern, Double> copyPatterns(Map<CompositionPattern, Double> source) {
        if (source.size() != CompositionPattern.values().length) throw new IllegalArgumentException("Exactly six patterns required");
        EnumMap<CompositionPattern, Double> copy = new EnumMap<>(CompositionPattern.class);
        for (CompositionPattern pattern : CompositionPattern.values()) {
            Double value = source.get(pattern);
            validate(value, pattern.name());
            copy.put(pattern, normalizeZero(value));
        }
        return Map.copyOf(copy);
    }

    private static void validate(Double value, String name) {
        if (value == null || !Double.isFinite(value) || value < 0.0 || value > 1.0) throw new IllegalArgumentException("Invalid " + name + " signal");
    }

    private static double normalizeZero(double value) { return value == 0.0 ? 0.0 : value; }
}
