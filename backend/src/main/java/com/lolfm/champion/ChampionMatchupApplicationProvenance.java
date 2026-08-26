package com.lolfm.champion;

import com.lolfm.simulator.ProgressionApplicationStage;
import com.lolfm.simulator.ProgressionCombatContext;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Objects;

/** Immutable observational proof of one actual Matchup consumer input. */
public record ChampionMatchupApplicationProvenance(
        String schemaVersion,
        long applicationSequence,
        String applicationIdentity,
        int simulationTimeSeconds,
        ChampionMatchupMode mode,
        ProgressionCombatContext context,
        ProgressionApplicationStage applicationStage,
        ChampionMatchupApplicationPoint applicationPoint,
        TeamSide perspective,
        ChampionMatchupLaneScope laneScope,
        List<ChampionMatchupPairApplication> pairApplications,
        double aggregateEdge,
        double consumerScoreBefore,
        double consumerScoreAfter,
        double actualConsumerInputDelta,
        boolean consumed,
        boolean nonZero,
        String structuredActionId,
        ChampionMatchupStateMutationLineage stateMutationLineage
) {
    public static final String SCHEMA_VERSION = "CHAMPION_MATCHUP_APPLICATION_PROVENANCE_V2";

    public ChampionMatchupApplicationProvenance {
        if (!SCHEMA_VERSION.equals(schemaVersion) || applicationSequence < 1
                || simulationTimeSeconds < 0) {
            throw new IllegalArgumentException("Invalid Matchup application provenance identity");
        }
        applicationIdentity = Objects.requireNonNull(applicationIdentity, "applicationIdentity");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(applicationStage, "applicationStage");
        Objects.requireNonNull(applicationPoint, "applicationPoint");
        Objects.requireNonNull(perspective, "perspective");
        Objects.requireNonNull(laneScope, "laneScope");
        pairApplications = List.copyOf(pairApplications);
        requireFinite(aggregateEdge, "aggregateEdge");
        requireFinite(consumerScoreBefore, "consumerScoreBefore");
        requireFinite(consumerScoreAfter, "consumerScoreAfter");
        requireFinite(actualConsumerInputDelta, "actualConsumerInputDelta");
        if (!consumed || mode == ChampionMatchupMode.OFF || pairApplications.isEmpty()) {
            throw new IllegalArgumentException("Application provenance requires an enabled consumer");
        }
        if (nonZero != (actualConsumerInputDelta != 0.0)) {
            throw new IllegalArgumentException("Matchup non-zero application mismatch");
        }
        if (Math.abs((consumerScoreAfter - consumerScoreBefore)
                - actualConsumerInputDelta) > 1e-12) {
            throw new IllegalArgumentException("Matchup consumer score decomposition mismatch");
        }
        if (structuredActionId != null && structuredActionId.isBlank()) {
            throw new IllegalArgumentException("Structured action identity must not be blank");
        }
        boolean combatConsumer = applicationPoint == ChampionMatchupApplicationPoint
                .COMBAT_PROGRESSION_SCORE;
        if (combatConsumer == (structuredActionId == null)
                || combatConsumer == (stateMutationLineage != null)) {
            throw new IllegalArgumentException(
                    "Combat applications require an action; pressure applications require a mutation");
        }
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
