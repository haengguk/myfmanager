package com.lolfm.simulator;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Finite lane opportunities and duplicate clocks, owned by one match. */
public final class LaneResourceState {
    private final Map<PlayerKey, Integer> csTicks = new HashMap<>();
    private final Map<TeamSide, EnumMap<Lane, Wave>> waves = new EnumMap<>(TeamSide.class);
    private int lastXpTick = -1;
    public boolean beginXp(int time) {
        if (time < lastXpTick) throw new IllegalArgumentException("Lane XP time moved backwards");
        if (time == lastXpTick) return false;
        lastXpTick = time;
        return true;
    }
    public boolean beginCs(PlayerKey player, Lane lane, int time) {
        Integer previous = csTicks.get(player);
        if (previous != null && time < previous) throw new IllegalArgumentException("Lane CS time moved backwards");
        if (previous != null && time == previous || available(player.side(), lane, time) == 0) return false;
        csTicks.put(player, time);
        return true;
    }
    public int consume(TeamSide side, Lane lane, int time, int requested) {
        Wave wave = wave(side, lane, time);
        int amount = Math.min(requested, wave.remaining);
        wave.remaining -= amount;
        return amount;
    }
    private int available(TeamSide side, Lane lane, int time) { return wave(side, lane, time).remaining; }
    private Wave wave(TeamSide side, Lane lane, int time) {
        int period = MatchRealismRuleConfig.wavePeriod(time);
        int epoch = time / period;
        var lanes = waves.computeIfAbsent(side, ignored -> new EnumMap<>(Lane.class));
        Wave old = lanes.get(lane);
        if (old == null || old.period != period || old.epoch != epoch) {
            old = new Wave(period, epoch, MatchRealismRuleConfig.waveMinions(time));
            lanes.put(lane, old);
        }
        return old;
    }
    private static final class Wave {
        final int period, epoch;
        int remaining;
        Wave(int period, int epoch, int remaining) { this.period = period; this.epoch = epoch; this.remaining = remaining; }
    }
}
