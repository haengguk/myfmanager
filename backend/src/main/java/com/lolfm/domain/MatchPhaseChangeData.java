package com.lolfm.domain;

import com.lolfm.simulator.Lane;
import com.lolfm.simulator.MatchPhase;
import com.lolfm.simulator.MidGameTransitionReason;
import java.util.List;

public record MatchPhaseChangeData(
        MatchPhase previousPhase,
        MatchPhase newPhase,
        int transitionTimeSeconds,
        MidGameTransitionReason reason,
        List<Lane> alreadyOpenLanes,
        List<Lane> forcedOpenLanes
) {
    public MatchPhaseChangeData {
        alreadyOpenLanes = List.copyOf(alreadyOpenLanes);
        forcedOpenLanes = List.copyOf(forcedOpenLanes);
    }
}
