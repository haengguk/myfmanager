package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.simulator.TeamSide;

public record DraftAction(int turn, TeamSide side, DraftActionType actionType, ChampionId championId) {
    public DraftAction {
        if (turn < 1 || side == null || actionType == null || championId == null) throw new IllegalArgumentException("Complete DraftAction required");
    }
}
