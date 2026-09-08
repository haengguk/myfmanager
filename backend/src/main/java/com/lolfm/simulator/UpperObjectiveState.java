package com.lolfm.simulator;

import com.lolfm.domain.Position;
import java.util.EnumMap;
import java.util.Map;

/** Single spawn cycle with independent grub identities and a single Herald lifecycle. */
public final class UpperObjectiveState {
    public enum HeraldPhase { UNCLAIMED, HELD, SUMMONED, EXPIRED, DESTROYED }
    private final EnumMap<TeamSide,Integer> grubs = new EnumMap<>(TeamSide.class);
    private int capturedGrubs, lastCaptureAt = -1, nextAttempt = UpperObjectiveRuleConfig.GRUB_SPAWN;
    private int lastEvaluationAt = -1;
    private TeamSide heraldOwner;
    private Position holder;
    private HeraldPhase heraldPhase = HeraldPhase.UNCLAIMED;
    private int acquiredAt = -1, expiresAt = -1, chargeAt = -1, charges, lastLifecycleAt = -1;
    private Lane summonLane;
    public UpperObjectiveState() { for (TeamSide s : TeamSide.values()) grubs.put(s,0); }
    public boolean available(ObjectiveType type, int time) {
        return switch (type) {
            case VOID_GRUB -> time >= UpperObjectiveRuleConfig.GRUB_SPAWN
                    && time < UpperObjectiveRuleConfig.GRUB_DESPAWN && capturedGrubs < UpperObjectiveRuleConfig.GRUB_COUNT;
            case RIFT_HERALD -> time >= UpperObjectiveRuleConfig.HERALD_SPAWN
                    && time < UpperObjectiveRuleConfig.HERALD_DESPAWN && heraldPhase == HeraldPhase.UNCLAIMED;
            default -> false;
        };
    }
    public boolean due(ObjectiveType type, int time) { return available(type,time) && time >= nextAttempt; }
    public boolean beginEvaluation(int time) {
        if (time < lastEvaluationAt) throw new IllegalArgumentException("Upper objective evaluation moved backwards");
        if (time == lastEvaluationAt) return false;
        lastEvaluationAt = time;
        return true;
    }
    public void attempted(int time) { nextAttempt = time + UpperObjectiveRuleConfig.ATTEMPT_INTERVAL; }
    public String capture(ObjectiveType type, TeamSide side, Position killer, int time) {
        if (time < lastCaptureAt) throw new IllegalArgumentException("Upper objective time moved backwards");
        if (time == lastCaptureAt || !available(type,time)) return null;
        lastCaptureAt = time;
        attempted(time);
        if (type == ObjectiveType.VOID_GRUB) {
            grubs.merge(side,1,Integer::sum);
            return "VOID_GRUB:1:" + (++capturedGrubs);
        }
        heraldOwner = side; holder = killer; acquiredAt = time;
        expiresAt = time + UpperObjectiveRuleConfig.EYE_LIFETIME;
        heraldPhase = HeraldPhase.HELD;
        return "RIFT_HERALD:1:1";
    }
    public boolean beginLifecycle(int time) {
        if (time < lastLifecycleAt) throw new IllegalArgumentException("Herald lifecycle time moved backwards");
        if (time == lastLifecycleAt) return false;
        lastLifecycleAt = time; return true;
    }
    public void summon(Lane lane, int time) {
        if (heraldPhase != HeraldPhase.HELD || time >= expiresAt) throw new IllegalStateException("Herald eye unavailable");
        summonLane = lane; heraldPhase = HeraldPhase.SUMMONED;
        expiresAt = time + UpperObjectiveRuleConfig.MERCENARY_LIFETIME;
        chargeAt = time + UpperObjectiveRuleConfig.CHARGE_DELAY;
    }
    public void charged(int time) {
        if (heraldPhase != HeraldPhase.SUMMONED || time < chargeAt) throw new IllegalStateException("Herald charge unavailable");
        charges++; chargeAt = time + UpperObjectiveRuleConfig.CHARGE_DELAY;
        if (charges >= UpperObjectiveRuleConfig.MAX_CHARGES) heraldPhase = HeraldPhase.DESTROYED;
    }
    public void expire() { heraldPhase = HeraldPhase.EXPIRED; }
    public void destroy() { heraldPhase = HeraldPhase.DESTROYED; }
    public int grubs(TeamSide side) { return grubs.get(side); }
    public int capturedGrubs() { return capturedGrubs; }
    public int nextAttempt() { return nextAttempt; }
    public TeamSide heraldOwner() { return heraldOwner; }
    public Position holder() { return holder; }
    public HeraldPhase heraldPhase() { return heraldPhase; }
    public int acquiredAt() { return acquiredAt; }
    public int expiresAt() { return expiresAt; }
    public int chargeAt() { return chargeAt; }
    public int charges() { return charges; }
    public Lane summonLane() { return summonLane; }
    public Map<String,Object> snapshot(int time) {
        var m = new java.util.LinkedHashMap<String,Object>();
        m.put("rulesVersion",UpperObjectiveRuleConfig.VERSION);
        m.put("blueGrubs",grubs(TeamSide.BLUE)); m.put("redGrubs",grubs(TeamSide.RED));
        m.put("remainingGrubs",available(ObjectiveType.VOID_GRUB,time) ? UpperObjectiveRuleConfig.GRUB_COUNT-capturedGrubs:0);
        m.put("grubSpawnAt",UpperObjectiveRuleConfig.GRUB_SPAWN);
        m.put("heraldAlive",available(ObjectiveType.RIFT_HERALD,time));
        m.put("heraldPhase",heraldPhase.name()); m.put("heraldCharges",charges);
        m.put("heraldExpiresAt",expiresAt); m.put("heraldChargeAt",chargeAt);
        if(heraldOwner!=null) { m.put("heraldOwner",heraldOwner.name());m.put("holderPosition",holder.name()); }
        if(summonLane!=null) m.put("summonLane",summonLane.name());
        return java.util.Collections.unmodifiableMap(m);
    }
}
