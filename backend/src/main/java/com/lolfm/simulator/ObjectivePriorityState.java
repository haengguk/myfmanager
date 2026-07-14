package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import java.util.HashSet;
import java.util.Set;

/** Match-scoped mutable recent-control state; reads never decay or mutate it. */
public final class ObjectivePriorityState {
    private final boolean enabled;
    private final ObjectivePriorityExecutionStats executionStats;
    private double dragonRecentControl;
    private double baronRecentControl;
    private int lastDecayAppliedAtSeconds;
    private final Set<ImpactKey> appliedImpactKeys = new HashSet<>();

    public ObjectivePriorityState() { this(true, new ObjectivePriorityExecutionStats()); }

    ObjectivePriorityState(boolean enabled, ObjectivePriorityExecutionStats executionStats) {
        this.enabled = enabled;
        this.executionStats = executionStats;
    }

    public boolean isEnabled() { return enabled; }

    public void advanceTo(int time) {
        if (!enabled) return;
        if (time < lastDecayAppliedAtSeconds) {
            throw new IllegalArgumentException("Objective priority time cannot move backwards");
        }
        int elapsed = time - lastDecayAppliedAtSeconds;
        double amount = elapsed / (double) ObjectivePriorityRuleConfig.RECENT_CONTROL_DECAY_SECONDS_PER_POINT;
        double dragonBefore = dragonRecentControl;
        double baronBefore = baronRecentControl;
        dragonRecentControl = decay(dragonRecentControl, amount);
        baronRecentControl = decay(baronRecentControl, amount);
        lastDecayAppliedAtSeconds = time;
        executionStats.recordDecay(elapsed, dragonBefore, dragonRecentControl, baronBefore, baronRecentControl);
    }

    private double decay(double value, double amount) {
        return value > 0 ? Math.max(0, value - amount) : Math.min(0, value + amount);
    }

    public double getDragonRecentControl() { return dragonRecentControl; }
    public double getBaronRecentControl() { return baronRecentControl; }
    public int getLastDecayAppliedAtSeconds() { return lastDecayAppliedAtSeconds; }
    public int getAppliedImpactCount() { return appliedImpactKeys.size(); }

    boolean applyImpactOnce(ImpactKey key, double dragon, double baron) {
        executionStats.recordImpactAttempt();
        if (!enabled) {
            executionStats.recordDisabledImpact();
            return false;
        }
        if (!appliedImpactKeys.add(key)) {
            executionStats.recordDuplicateImpact();
            return false;
        }
        double dragonBefore = dragonRecentControl;
        double baronBefore = baronRecentControl;
        double dragonUnclamped = dragonBefore + dragon;
        double baronUnclamped = baronBefore + baron;
        dragonRecentControl = clamp(dragonUnclamped);
        baronRecentControl = clamp(baronUnclamped);
        executionStats.recordImpact(key.source(), key.targetLane(), key.winningSide(), dragon,
                dragonRecentControl - dragonBefore, baron, baronRecentControl - baronBefore,
                dragonRecentControl != dragonUnclamped, baronRecentControl != baronUnclamped);
        return true;
    }

    void recordNoKillImpact() { executionStats.recordNoKill(); }

    private double clamp(double value) {
        return Math.max(-ObjectivePriorityRuleConfig.MAX_RECENT_CONTROL,
                Math.min(ObjectivePriorityRuleConfig.MAX_RECENT_CONTROL, value));
    }

    record ImpactKey(int timeSeconds, CombatSource source, Lane targetLane,
                     TeamSide winningSide, FightGrade fightGrade) { }
}
