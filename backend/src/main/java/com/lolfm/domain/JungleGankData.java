package com.lolfm.domain;

import com.lolfm.simulator.JungleGankOutcome;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.TeamSide;
import java.util.List;

public record JungleGankData(
        TeamSide gankingSide,
        String junglerPlayerId,
        Lane targetLane,
        JungleGankOutcome outcome,
        TeamSide winningSide,
        String killerPlayerId,
        String victimPlayerId,
        List<String> assistantPlayerIds,
        double pressureBefore,
        double pressureAfter,
        double enemyOverextension,
        int jungleFarmBlockedUntilSeconds,
        double attemptChance,
        double targetWeight,
        double combatEdge,
        double decisiveChance,
        double gankSuccessChance,
        boolean blueTriggered,
        boolean redTriggered
) {
    public JungleGankData { assistantPlayerIds = List.copyOf(assistantPlayerIds); }
}
