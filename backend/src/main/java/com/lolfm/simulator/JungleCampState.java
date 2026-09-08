package com.lolfm.simulator;

import java.util.EnumMap;
import java.util.Map;

/** Six own-side camp abstractions. No invading, individual small monsters or camp health simulation. */
public final class JungleCampState {
    public enum Camp { BLUE, GROMP, WOLVES, RAPTORS, RED, KRUGS }
    private final EnumMap<Camp, Integer> respawns = new EnumMap<>(Camp.class);
    private Camp clearing;
    private double workSeconds;
    private int completed;
    public JungleCampState() {
        for (Camp camp : Camp.values()) respawns.put(camp,
                camp == Camp.GROMP || camp == Camp.KRUGS ? MatchRealismRuleConfig.DELAYED_CAMP_SPAWN_SECONDS : MatchRealismRuleConfig.FIRST_CAMP_SPAWN_SECONDS);
    }
    public boolean hasAvailable(int time) {
        return clearing != null || respawns.values().stream().anyMatch(t -> t <= time);
    }
    public boolean clear(int time, int elapsed, double efficiency) {
        if (clearing == null) {
            for (Camp camp : Camp.values()) if (respawns.get(camp) <= time) { clearing = camp; break; }
        }
        if (clearing == null) return false;
        // Only the present tick is worked. Missed travel/death ticks never catch up.
        workSeconds += Math.min(elapsed, JungleEconomyRuleConfig.STANDARD_TICK_SECONDS) * efficiency;
        if (workSeconds < MatchRealismRuleConfig.CAMP_CLEAR_WORK_SECONDS) return false;
        respawns.put(clearing, time + (clearing == Camp.BLUE || clearing == Camp.RED
                ? MatchRealismRuleConfig.BUFF_CAMP_RESPAWN_SECONDS : MatchRealismRuleConfig.CAMP_RESPAWN_SECONDS));
        clearing = null;
        workSeconds = 0;
        completed++;
        return true;
    }
    public int completed() { return completed; }
    public Map<Camp, Integer> respawns() { return Map.copyOf(respawns); }
    public Camp clearing() { return clearing; }
    public double workSeconds() { return workSeconds; }
}
