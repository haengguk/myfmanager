package com.lolfm.simulator;

import com.lolfm.domain.Position;
import com.lolfm.domain.DeterministicEnumSet;
import java.util.Set;

public record StructureAttackResult(
        StructureTargetId target,
        double healthBefore,
        double damage,
        double healthAfter,
        int platesClaimed,
        boolean firstTurretBonus,
        StructureOutcome destruction,
        String actionId,
        int attackSequence,
        String parentActionId,
        StructureActionSource source,
        Set<Position> participants,
        StructureAttackMode mode,
        boolean siegeStarted,
        boolean siegeContinues
) {
    public StructureAttackResult {
        participants = DeterministicEnumSet.copyOf(Position.class, participants);
    }

    public boolean destroyed() { return destruction != null; }
    public boolean gameEnded() { return destruction != null && destruction.gameEnded(); }

    public StructureAttackResult withDestruction(StructureOutcome outcome) {
        return new StructureAttackResult(target, healthBefore, damage, healthAfter, platesClaimed,
                firstTurretBonus, outcome, actionId, attackSequence, parentActionId, source,
                participants, mode, siegeStarted, siegeContinues);
    }
}
