package com.lolfm.simulator;

import com.lolfm.domain.MatchTimeline;
import java.util.Objects;

/** Public match output plus observational Random identity; neither field controls gameplay. */
public record ObservedMatchSimulation(
        MatchTimeline timeline,
        SimulationRandomFingerprint randomFingerprint
) {
    public ObservedMatchSimulation {
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(randomFingerprint, "randomFingerprint");
    }
}
