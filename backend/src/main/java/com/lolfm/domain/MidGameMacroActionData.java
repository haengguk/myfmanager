package com.lolfm.domain;

import com.lolfm.simulator.Lane;
import com.lolfm.simulator.MacroActionResult;
import com.lolfm.simulator.MacroActionType;
import com.lolfm.simulator.ObjectiveType;
import com.lolfm.simulator.StructureKind;
import com.lolfm.simulator.TeamMacroPlan;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.TowerTier;
import java.util.Set;

public record MidGameMacroActionData(
        TeamSide teamSide,
        TeamMacroPlan plan,
        MacroActionType actionType,
        MacroActionResult result,
        Lane targetLane,
        ObjectiveType targetObjective,
        Set<Position> participants,
        TowerTier targetTowerTier,
        double existingBaseChance,
        double goldBonus,
        double aliveBonus,
        double attributeBonus,
        double baronBonus,
        double finalPushChance,
        boolean pushRollExecuted,
        boolean pushSucceeded,
        StructureKind structureKind,
        double signedSetupControl,
        int setupActiveUntilSeconds,
        int farmBlockSeconds
) {
    public MidGameMacroActionData {
        participants = participants == null ? Set.of() : Set.copyOf(participants);
    }
}
