package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.composition.CompositionCapability;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record DraftPlan(
        DraftPlanArchetype archetype,
        Set<CompositionCapability> desiredCapabilities,
        Set<CompositionCapability> structuralVulnerabilities,
        List<ChampionId> coreCandidates,
        Map<CompositionCapability, Double> missingCapabilities,
        double viability
) {
    public DraftPlan {
        desiredCapabilities = orderedCapabilities(desiredCapabilities);
        structuralVulnerabilities = orderedCapabilities(structuralVulnerabilities);
        coreCandidates = List.copyOf(coreCandidates);
        missingCapabilities = Map.copyOf(missingCapabilities);
    }

    private static Set<CompositionCapability> orderedCapabilities(
            Set<CompositionCapability> values
    ) {
        LinkedHashSet<CompositionCapability> ordered = new LinkedHashSet<>();
        values.stream().sorted().forEach(ordered::add);
        return Collections.unmodifiableSet(ordered);
    }
}
