package com.lolfm.champion;

import com.lolfm.simulator.ProgressionApplicationStage;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.Objects;

/** Observational proof that an actual action consumed one exact lane-pressure version. */
public record ChampionMatchupStateConsumerProvenance(
        String mutationIdentity,
        long mutationVersion,
        int mutationTimeSeconds,
        ChampionMatchupLaneScope laneScope,
        double matchupPressureDelta,
        int consumerTimeSeconds,
        ProgressionCombatContext consumerContext,
        ProgressionApplicationStage consumerStage,
        String consumerActionId
) {
    public ChampionMatchupStateConsumerProvenance {
        mutationIdentity = Objects.requireNonNull(mutationIdentity, "mutationIdentity");
        laneScope = Objects.requireNonNull(laneScope, "laneScope");
        consumerContext = Objects.requireNonNull(consumerContext, "consumerContext");
        consumerStage = Objects.requireNonNull(consumerStage, "consumerStage");
        consumerActionId = Objects.requireNonNull(consumerActionId, "consumerActionId");
        if (mutationIdentity.isBlank() || consumerActionId.isBlank() || mutationVersion < 1
                || mutationTimeSeconds < 0 || consumerTimeSeconds < mutationTimeSeconds
                || !Double.isFinite(matchupPressureDelta)) {
            throw new IllegalArgumentException("Invalid Matchup state consumer provenance");
        }
    }
}
