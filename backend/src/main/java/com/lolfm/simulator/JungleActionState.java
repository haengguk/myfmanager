package com.lolfm.simulator;

import java.util.EnumMap;
import java.util.Map;

/** Mutable per-game state for one team's jungler. Ganks and counter-ganks share one action clock. */
public final class JungleActionState {
    private int lastJungleActionAtSeconds = -1;
    private int lastGankAttemptAtSeconds = -1;
    private int lastCounterGankAttemptAtSeconds = -1;
    private int jungleFarmBlockedUntilSeconds = -1;
    private final EnumMap<Lane, Integer> lastJungleActionByLane = new EnumMap<>(Lane.class);

    public JungleActionState() {
        for (Lane lane : Lane.values()) lastJungleActionByLane.put(lane, -1);
    }

    public int getLastJungleActionAtSeconds() { return lastJungleActionAtSeconds; }
    public int getLastGankAttemptAtSeconds() { return lastGankAttemptAtSeconds; }
    public int getLastCounterGankAttemptAtSeconds() { return lastCounterGankAttemptAtSeconds; }
    public int getJungleFarmBlockedUntilSeconds() { return jungleFarmBlockedUntilSeconds; }
    /** Shared per-lane action clock retained under the legacy getter for API compatibility. */
    public int getLastGankAttemptAtSeconds(Lane lane) { return lastJungleActionByLane.get(lane); }
    public Map<Lane, Integer> getLastGankAttemptByLane() { return Map.copyOf(lastJungleActionByLane); }

    public void recordAttempt(int time, Lane lane) { recordGankAttempt(time, lane); }

    public void recordGankAttempt(int time, Lane lane) {
        recordAction(time, lane, JungleGankRuleConfig.JUNGLE_FARM_BLOCK_SECONDS);
        lastGankAttemptAtSeconds = time;
    }

    public void recordCounterGankAttempt(int time, Lane lane) {
        recordAction(time, lane, CounterGankRuleConfig.COUNTER_GANK_FARM_BLOCK_SECONDS);
        lastCounterGankAttemptAtSeconds = time;
    }

    private void recordAction(int time, Lane lane, int farmBlockSeconds) {
        lastJungleActionAtSeconds = time;
        lastJungleActionByLane.put(lane, time);
        jungleFarmBlockedUntilSeconds = Math.max(jungleFarmBlockedUntilSeconds, time + farmBlockSeconds);
    }
}
