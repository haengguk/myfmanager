package com.lolfm.simulator;

import java.util.EnumMap;
import java.util.Map;

/** Mutable per-game state for one team's jungler. */
public final class JungleActionState {
    private int lastGankAttemptAtSeconds = -1;
    private int jungleFarmBlockedUntilSeconds = -1;
    private final EnumMap<Lane, Integer> lastGankAttemptByLane = new EnumMap<>(Lane.class);

    public JungleActionState() {
        for (Lane lane : Lane.values()) lastGankAttemptByLane.put(lane, -1);
    }

    public int getLastGankAttemptAtSeconds() { return lastGankAttemptAtSeconds; }
    public int getJungleFarmBlockedUntilSeconds() { return jungleFarmBlockedUntilSeconds; }
    public int getLastGankAttemptAtSeconds(Lane lane) { return lastGankAttemptByLane.get(lane); }
    public Map<Lane, Integer> getLastGankAttemptByLane() { return Map.copyOf(lastGankAttemptByLane); }

    public void recordAttempt(int time, Lane lane) {
        lastGankAttemptAtSeconds = time;
        lastGankAttemptByLane.put(lane, time);
        jungleFarmBlockedUntilSeconds = Math.max(jungleFarmBlockedUntilSeconds,
                time + JungleGankRuleConfig.JUNGLE_FARM_BLOCK_SECONDS);
    }
}
