package com.lolfm.simulator;

import com.lolfm.domain.Position;
import com.lolfm.domain.DeterministicEnumSet;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Mutable siege continuity owned by the current GameState. */
public final class BaseSiegeState {
    private final TeamSide attackingSide;
    private boolean active;
    private String actionId;
    private Lane routeLane;
    private StructureTargetId currentTarget;
    private PushReason reason;
    private StructureActionSource source;
    private String parentActionId;
    private EnumSet<Position> participants = EnumSet.noneOf(Position.class);
    private StructureAttackMode mode;
    private int startedAtSeconds;
    private int nextAttackAtSeconds;
    private int expiresAtSeconds;
    private int attackSequence;
    private int attackOpportunityLimit;
    private boolean nexusCommitGranted;
    private SiegeStopReason stopReason;

    public BaseSiegeState(TeamSide attackingSide) {
        this.attackingSide = Objects.requireNonNull(attackingSide, "attackingSide");
    }

    public void start(String actionId, Lane routeLane, StructureTargetId target, PushReason reason,
                      StructureActionSource source, String parentActionId, Set<Position> participants,
                      StructureAttackMode mode, int timeSeconds, int durationSeconds,
                      int attackOpportunityLimit) {
        if (active) throw new IllegalStateException("Siege is already active for " + attackingSide);
        if (attackOpportunityLimit <= 0) throw new IllegalArgumentException("attackOpportunityLimit");
        this.active = true;
        this.actionId = Objects.requireNonNull(actionId, "actionId");
        this.routeLane = Objects.requireNonNull(routeLane, "routeLane");
        this.currentTarget = Objects.requireNonNull(target, "target");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.source = Objects.requireNonNull(source, "source");
        this.parentActionId = parentActionId;
        this.participants = participants.isEmpty()
                ? EnumSet.noneOf(Position.class) : EnumSet.copyOf(participants);
        this.mode = Objects.requireNonNull(mode, "mode");
        this.startedAtSeconds = timeSeconds;
        this.nextAttackAtSeconds = timeSeconds;
        this.expiresAtSeconds = timeSeconds + durationSeconds;
        this.attackSequence = 0;
        this.attackOpportunityLimit = attackOpportunityLimit;
        this.nexusCommitGranted = false;
        this.stopReason = null;
    }

    public void scheduleNextAttack(int timeSeconds) {
        nextAttackAtSeconds = timeSeconds + StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS;
        attackSequence++;
    }

    public void retarget(StructureTargetId target) {
        currentTarget = Objects.requireNonNull(target, "target");
    }

    public boolean grantNexusCommit(int timeSeconds) {
        if (nexusCommitGranted) return false;
        nexusCommitGranted = true;
        attackOpportunityLimit = Math.max(attackOpportunityLimit,
                attackSequence + 1 + StructureRuleConfig.NEXUS_COMMIT_BONUS_ATTACKS);
        expiresAtSeconds = Math.max(expiresAtSeconds,
                timeSeconds + StructureRuleConfig.NEXUS_COMMIT_GRACE_SECONDS);
        return true;
    }

    public void stop(SiegeStopReason reason) {
        active = false;
        stopReason = Objects.requireNonNull(reason, "reason");
    }

    public TeamSide getAttackingSide() { return attackingSide; }
    public boolean isActive() { return active; }
    public String getActionId() { return actionId; }
    public Lane getRouteLane() { return routeLane; }
    public StructureTargetId getCurrentTarget() { return currentTarget; }
    public PushReason getReason() { return reason; }
    public StructureActionSource getSource() { return source; }
    public String getParentActionId() { return parentActionId; }
    public Set<Position> getParticipants() {
        return DeterministicEnumSet.copyOf(Position.class, participants);
    }
    public StructureAttackMode getMode() { return mode; }
    public int getStartedAtSeconds() { return startedAtSeconds; }
    public int getNextAttackAtSeconds() { return nextAttackAtSeconds; }
    public int getExpiresAtSeconds() { return expiresAtSeconds; }
    public int getAttackSequence() { return attackSequence; }
    public int getAttackOpportunityLimit() { return attackOpportunityLimit; }
    public boolean isNexusCommitGranted() { return nexusCommitGranted; }
    public SiegeStopReason getStopReason() { return stopReason; }
}
