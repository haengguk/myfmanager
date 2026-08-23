package com.lolfm.simulator;

import com.lolfm.domain.MatchTimeline;
import java.util.Objects;

/**
 * Structured terminal match facts exposed without promoting internal diagnostics to an
 * application contract. A timeout has no winner; every other supported end reason must have one.
 */
public record StructuredMatchSimulationOutcome(
        MatchTimeline timeline,
        TeamSide winnerSide,
        GameEndReason endReason,
        int endedAtSeconds,
        SimulationRandomFingerprint randomFingerprint
) {
    public StructuredMatchSimulationOutcome {
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(endReason, "endReason");
        Objects.requireNonNull(randomFingerprint, "randomFingerprint");
        if (endedAtSeconds < 0 || timeline.getDurationSeconds() != endedAtSeconds) {
            throw new IllegalArgumentException("Structured outcome duration mismatch");
        }
        if (endReason == GameEndReason.NEXUS_DESTROYED && winnerSide == null) {
            throw new IllegalArgumentException("Nexus destruction requires a structured winner");
        }
        if (endReason == GameEndReason.SIMULATION_TIMEOUT && winnerSide != null) {
            throw new IllegalArgumentException("Simulation timeout must not invent a winner");
        }
    }
}
