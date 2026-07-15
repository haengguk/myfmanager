package com.lolfm.domain;

import com.lolfm.simulator.MatchPhase;
import com.lolfm.simulator.MidGameTransitionReason;
import java.util.List;

public record LanePhaseSnapshot(
        boolean enabled,
        MatchPhase matchPhase,
        int midGameStartedAtSeconds,
        MidGameTransitionReason transitionReason,
        List<LanePhaseLaneSnapshot> lanes
) {
    public LanePhaseSnapshot { lanes = List.copyOf(lanes); }
}
