package com.lolfm.simulator;

/** Mutable activity state owned by one PlayerState for the lifetime of one match. */
public final class PlayerActivityState {
    private PlayerActivityType activityType = PlayerActivityType.DEFAULT_ROLE;
    private Lane originLane;
    private Lane targetLane;
    private int activityStartedAtSeconds = -1;
    private int activityUntilSeconds = -1;
    private String structuredActionId;

    public PlayerActivityType getActivityType() { return activityType; }
    public Lane getOriginLane() { return originLane; }
    public Lane getTargetLane() { return targetLane; }
    public int getActivityStartedAtSeconds() { return activityStartedAtSeconds; }
    public int getActivityUntilSeconds() { return activityUntilSeconds; }
    public String getStructuredActionId() { return structuredActionId; }

    public void beginRoam(Lane originLane, Lane targetLane, int startedAtSeconds, int untilSeconds) {
        this.activityType = PlayerActivityType.ROAMING;
        this.originLane = originLane;
        this.targetLane = targetLane;
        this.activityStartedAtSeconds = startedAtSeconds;
        this.activityUntilSeconds = untilSeconds;
        this.structuredActionId = null;
    }

    public void beginSiege(Lane routeLane, String actionId,
                           int startedAtSeconds, int untilSeconds) {
        this.activityType = PlayerActivityType.SIEGING;
        this.originLane = routeLane;
        this.targetLane = routeLane;
        this.activityStartedAtSeconds = startedAtSeconds;
        this.activityUntilSeconds = untilSeconds;
        this.structuredActionId = java.util.Objects.requireNonNull(actionId, "actionId");
    }

    public boolean isSiegingAction(String actionId) {
        return activityType == PlayerActivityType.SIEGING
                && java.util.Objects.equals(structuredActionId, actionId);
    }

    public void extendSiege(String actionId, int untilSeconds) {
        if (!isSiegingAction(actionId)) return;
        activityUntilSeconds = Math.max(activityUntilSeconds, untilSeconds);
    }

    public void clearSiege(String actionId) {
        if (isSiegingAction(actionId)) clear();
    }

    public void expireIfNeeded(int currentTimeSeconds) {
        if (activityType != PlayerActivityType.DEFAULT_ROLE
                && currentTimeSeconds >= activityUntilSeconds) clear();
    }

    public void clear() {
        activityType = PlayerActivityType.DEFAULT_ROLE;
        originLane = null;
        targetLane = null;
        activityStartedAtSeconds = -1;
        activityUntilSeconds = -1;
        structuredActionId = null;
    }
}
