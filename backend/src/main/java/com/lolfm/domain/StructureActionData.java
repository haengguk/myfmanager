package com.lolfm.domain;

import com.lolfm.simulator.BaseThreatLevel;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.SiegeContinuationDecisionReason;
import com.lolfm.simulator.SiegeStopReason;
import com.lolfm.simulator.StructureActionSource;
import com.lolfm.simulator.StructureKind;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.TowerTier;
import java.util.Set;

/** Additive structured structure/siege event payload. */
public record StructureActionData(
        StructureActionPhase phase,
        String targetId,
        StructureKind structureKind,
        Lane lane,
        TowerTier towerTier,
        Integer nexusTurretIndex,
        TeamSide attackingSide,
        TeamSide defendingSide,
        StructureActionSource source,
        double healthBefore,
        double damage,
        double healthAfter,
        double maxHealth,
        int platesClaimed,
        boolean firstTurretBonus,
        Set<Position> participants,
        boolean wavePresent,
        boolean backdoorProtected,
        boolean siegeContinues,
        SiegeStopReason stopReason,
        BaseThreatLevel ownBaseThreatLevelAtDecision,
        SiegeContinuationDecisionReason strategicContinuationDecision,
        Boolean strategicallyAllowed
) {
    public StructureActionData {
        participants = DeterministicEnumSet.copyOfNullable(Position.class, participants);
    }
}
