package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchTimeline;
import java.util.LinkedHashMap;
import java.util.Map;

/** Aggregates deterministic simulation diagnostics without inferring state from event text. */
public final class BountyDiagnostics {

    private int matches;
    private int shutdownCount;
    private long totalShutdownGold;
    private final Map<Integer, Integer> payoutDistribution = new LinkedHashMap<>();

    public BountyDiagnostics() {
        for (int payout = BountyRuleConfig.MIN_VISIBLE_SHUTDOWN_GOLD;
             payout <= BountyRuleConfig.MAX_SHUTDOWN_PAYOUT;
             payout += BountyRuleConfig.BOUNTY_DISPLAY_STEP) {
            payoutDistribution.put(payout, 0);
        }
    }

    public void record(MatchTimeline timeline) {
        matches++;
        for (MatchEvent event : timeline.getEvents()) {
            if (event.getType() != MatchEventType.SHUTDOWN) continue;
            shutdownCount++;
            totalShutdownGold += event.getGoldAmount();
            payoutDistribution.computeIfPresent(event.getGoldAmount(), (key, count) -> count + 1);
        }
    }

    public int getMatches() { return matches; }
    public int getShutdownCount() { return shutdownCount; }
    public long getTotalShutdownGold() { return totalShutdownGold; }
    public double getAverageShutdownGold() { return shutdownCount == 0 ? 0.0 : totalShutdownGold / (double) shutdownCount; }
    public Map<Integer, Integer> getPayoutDistribution() { return Map.copyOf(payoutDistribution); }
}
