package com.lolfm.champion;

import com.lolfm.simulator.Lane;
import java.util.Objects;

/** Immutable identity and decomposition of a Matchup-influenced lane-pressure mutation. */
public record ChampionMatchupStateMutationLineage(
        String mutationIdentity,
        long mutationVersion,
        int simulationTimeSeconds,
        Lane lane,
        double pressureBefore,
        double pressureAfter,
        double matchupPressureDelta,
        double clampEffect
) {
    public ChampionMatchupStateMutationLineage {
        if (mutationVersion < 1 || simulationTimeSeconds < 0) {
            throw new IllegalArgumentException("Invalid Matchup state mutation identity");
        }
        mutationIdentity = Objects.requireNonNull(mutationIdentity, "mutationIdentity");
        if (mutationIdentity.isBlank()) {
            throw new IllegalArgumentException("Mutation identity must not be blank");
        }
        Objects.requireNonNull(lane, "lane");
        requireFinite(pressureBefore, "pressureBefore");
        requireFinite(pressureAfter, "pressureAfter");
        requireFinite(matchupPressureDelta, "matchupPressureDelta");
        requireFinite(clampEffect, "clampEffect");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
