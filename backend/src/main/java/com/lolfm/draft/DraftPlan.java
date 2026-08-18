package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.composition.CompositionCapability;
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
        desiredCapabilities = Set.copyOf(desiredCapabilities);
        structuralVulnerabilities = Set.copyOf(structuralVulnerabilities);
        coreCandidates = List.copyOf(coreCandidates);
        missingCapabilities = Map.copyOf(missingCapabilities);
    }
}
