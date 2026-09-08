package com.lolfm.domain;

import com.lolfm.simulator.*;
import java.util.List;

public record UpperObjectiveData(ObjectiveType objectiveType, String instanceId, String phase,
        TeamSide side, String holderPlayerId, List<String> participants,
        int gold, int experience, int teamGrubs, int expiresAtSeconds,
        Lane lane, String structureTargetId, double damage) {
    public UpperObjectiveData { participants=List.copyOf(participants); }
}
