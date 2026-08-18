package com.lolfm.draft;

import com.lolfm.simulator.TeamSide;

public record DraftTurn(int number, TeamSide side, DraftActionType actionType) {
    public DraftTurn {
        if (number < 1) throw new IllegalArgumentException("Draft turn must be positive");
        if (side == null || actionType == null) throw new IllegalArgumentException("Draft turn fields are required");
    }
}
