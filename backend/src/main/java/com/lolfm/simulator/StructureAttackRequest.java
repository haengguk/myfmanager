package com.lolfm.simulator;

import com.lolfm.domain.Position;
import com.lolfm.domain.DeterministicEnumSet;
import java.util.Objects;
import java.util.Set;

public record StructureAttackRequest(
        TeamSide attackingSide,
        Lane routeLane,
        LateGameStructureTarget requestedTarget,
        PushReason reason,
        Set<Position> participants,
        String parentActionId,
        StructureAttackMode mode,
        boolean persistent,
        Double fixedDamage,
        StructureActionSource sourceOverride,
        String actionId,
        int attackSequence,
        Integer attackOpportunityOverride,
        Integer durationSecondsOverride
) {
    public StructureAttackRequest {
        Objects.requireNonNull(attackingSide, "attackingSide");
        Objects.requireNonNull(reason, "reason");
        participants = DeterministicEnumSet.copyOf(Position.class, participants);
        Objects.requireNonNull(mode, "mode");
        if (fixedDamage != null && fixedDamage <= 0) throw new IllegalArgumentException("fixedDamage");
        if (attackSequence < 0) throw new IllegalArgumentException("attackSequence");
        if (attackOpportunityOverride != null && attackOpportunityOverride <= 0) {
            throw new IllegalArgumentException("attackOpportunityOverride");
        }
        if (durationSecondsOverride != null && durationSecondsOverride <= 0) {
            throw new IllegalArgumentException("durationSecondsOverride");
        }
    }

    public static StructureAttackRequest siege(TeamSide side, Lane lane,
                                                LateGameStructureTarget requestedTarget,
                                                PushReason reason, Set<Position> participants,
                                                String parentActionId) {
        return new StructureAttackRequest(side, lane, requestedTarget, reason, participants,
                parentActionId, StructureAttackMode.WITH_WAVE, true, null, null, null, 0,
                null, null);
    }

    public static StructureAttackRequest fixed(TeamSide side, Lane lane,
                                                LateGameStructureTarget requestedTarget,
                                                PushReason reason, Set<Position> participants,
                                                double damage, StructureActionSource source,
                                                String actionId) {
        return new StructureAttackRequest(side, lane, requestedTarget, reason, participants,
                null, StructureAttackMode.LANE_PRESSURE, false, damage, source, actionId, 0,
                null, null);
    }

    public static StructureAttackRequest backdoor(
            TeamSide side, Lane lane, LateGameStructureTarget requestedTarget,
            PushReason reason, Set<Position> participants, String parentActionId) {
        return new StructureAttackRequest(side, lane, requestedTarget, reason, participants,
                parentActionId, StructureAttackMode.BACKDOOR, true,
                null, null, null, 0, null, null);
    }

    public static StructureAttackRequest continuation(BaseSiegeState siege) {
        return new StructureAttackRequest(siege.getAttackingSide(), siege.getRouteLane(),
                siege.getCurrentTarget().planningTarget(), siege.getReason(), siege.getParticipants(),
                siege.getParentActionId(), siege.getMode(), true, null, siege.getSource(),
                siege.getActionId(), siege.getAttackSequence(),
                siege.getAttackOpportunityLimit(),
                siege.getExpiresAtSeconds() - siege.getStartedAtSeconds());
    }

    public StructureAttackRequest withSiegeWindow(int attackOpportunities,
                                                  int durationSeconds) {
        if (!persistent) throw new IllegalStateException("Only persistent attacks have a siege window");
        return new StructureAttackRequest(
                attackingSide, routeLane, requestedTarget, reason, participants,
                parentActionId, mode, true, fixedDamage, sourceOverride, actionId,
                attackSequence, attackOpportunities, durationSeconds);
    }
}
