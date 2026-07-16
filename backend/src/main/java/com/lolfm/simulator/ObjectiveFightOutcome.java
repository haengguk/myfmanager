package com.lolfm.simulator;

import java.util.List;

public record ObjectiveFightOutcome(
        TeamSide winningSide,
        int kills,
        List<String> participantPlayerIds
) {
    public ObjectiveFightOutcome {
        participantPlayerIds = List.copyOf(participantPlayerIds);
    }
}
