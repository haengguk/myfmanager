package com.lolfm.simulator;

import java.util.Objects;

/** Immutable resolution of a public profile ID to exact gameplay semantics. */
public record ResolvedSimulationRuntimeProfile(
        SimulationRuntimeProfileId profileId,
        SimulationGameplayConfiguration gameplayConfiguration,
        String configurationHash
) {
    public ResolvedSimulationRuntimeProfile {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(gameplayConfiguration, "gameplayConfiguration");
        configurationHash = Objects.requireNonNull(configurationHash, "configurationHash");
        if (!configurationHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("configurationHash must be lowercase SHA-256");
        }
    }
}
