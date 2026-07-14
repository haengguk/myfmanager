package com.lolfm.simulator;

/** Mutable activity state owned by one PlayerState for the lifetime of one match. */
public final class PlayerActivityState {
    private PlayerActivityType activityType = PlayerActivityType.DEFAULT_ROLE;
    private Lane originLane;
    private Lane targetLane;
    private int activityStartedAtSeconds = -1;
    private int activityUntilSeconds = -1;

    public PlayerActivityType getActivityType() { return activityType; }
    public Lane getOriginLane() { return originLane; }
    public Lane getTargetLane() { return targetLane; }
    public int getActivityStartedAtSeconds() { return activityStartedAtSeconds; }
    public int getActivityUntilSeconds() { return activityUntilSeconds; }

    public void beginRoam(Lane originLane, Lane targetLane, int startedAtSeconds, int untilSeconds) {
        this.activityType = PlayerActivityType.ROAMING;
        this.originLane = originLane;
        this.targetLane = targetLane;
        this.activityStartedAtSeconds = startedAtSeconds;
        this.activityUntilSeconds = untilSeconds;
    }

    public void expireIfNeeded(int currentTimeSeconds) {
        if (activityType == PlayerActivityType.ROAMING && currentTimeSeconds >= activityUntilSeconds) clear();
    }

    public void clear() {
        activityType = PlayerActivityType.DEFAULT_ROLE;
        originLane = null;
        targetLane = null;
        activityStartedAtSeconds = -1;
        activityUntilSeconds = -1;
    }
}
