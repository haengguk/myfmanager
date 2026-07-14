package com.lolfm.simulator;

import java.util.EnumMap;
import java.util.Map;

/** Match-scoped roam cooldown, FARM restriction, and target history for one player. */
public final class RoamActionState {
    private int lastRoamAttemptAtSeconds = -1;
    private int roamFarmBlockedUntilSeconds = -1;
    private final EnumMap<Lane, Integer> lastRoamAttemptByTargetLane = new EnumMap<>(Lane.class);

    public RoamActionState() {
        for (Lane lane : Lane.values()) lastRoamAttemptByTargetLane.put(lane, -1);
    }

    public int getLastRoamAttemptAtSeconds() { return lastRoamAttemptAtSeconds; }
    public int getRoamFarmBlockedUntilSeconds() { return roamFarmBlockedUntilSeconds; }
    public int getLastRoamAttemptAtSeconds(Lane lane) { return lastRoamAttemptByTargetLane.get(lane); }
    public Map<Lane, Integer> getLastRoamAttemptByTargetLane() { return Map.copyOf(lastRoamAttemptByTargetLane); }

    public void recordAttempt(int timeSeconds, Lane targetLane, int farmBlockSeconds) {
        lastRoamAttemptAtSeconds = timeSeconds;
        lastRoamAttemptByTargetLane.put(targetLane, timeSeconds);
        roamFarmBlockedUntilSeconds = Math.max(roamFarmBlockedUntilSeconds, timeSeconds + farmBlockSeconds);
    }
}
