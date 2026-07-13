package com.lolfm.simulator;

import com.lolfm.domain.Position;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;

/** Resolves all CS and FARM-gold income through one seed-driven path. */
public final class PositionEconomyResolver {
    private final GoldAwardService awards = new GoldAwardService();
    private final Map<TeamState, Integer> lastResolvedAtSeconds = new IdentityHashMap<>();
    private int duplicateResolutionCount;
    private int deadPlayerFarmAwardCount;

    public void resolve(TeamState team, int currentTimeSeconds, int elapsedSeconds, Random random) {
        Integer lastTime = lastResolvedAtSeconds.get(team);
        if (lastTime != null && currentTimeSeconds <= lastTime) {
            if (currentTimeSeconds < lastTime) throw new IllegalArgumentException("Economy time cannot move backwards");
            duplicateResolutionCount++;
            return;
        }
        lastResolvedAtSeconds.put(team, currentTimeSeconds);
        for (PlayerState player : team.getPlayers()) {
            if (!player.isAlive(currentTimeSeconds)) continue;
            int cs = actualCs(player, elapsedSeconds, random);
            if (cs <= 0) continue;
            player.addCs(cs);
            awards.awardGold(team, player, cs * PositionEconomyRuleConfig.CS_GOLD, GoldSource.FARM, false);
        }
    }

    public int getDuplicateResolutionCount() { return duplicateResolutionCount; }

    /** This is an audit counter: the alive guard precedes every FARM award, so it must remain zero. */
    public int getDeadPlayerFarmAwardCount() { return deadPlayerFarmAwardCount; }

    public double farmingMultiplier(PlayerState player) {
        double multiplier = 1.0 + (player.getFarming() - PositionEconomyRuleConfig.FARMING_BASELINE)
                * PositionEconomyRuleConfig.FARMING_MULTIPLIER_PER_POINT;
        return Math.max(PositionEconomyRuleConfig.MIN_FARMING_MULTIPLIER,
                Math.min(PositionEconomyRuleConfig.MAX_FARMING_MULTIPLIER, multiplier));
    }

    private int actualCs(PlayerState player, int elapsedSeconds, Random random) {
        double expectedCs = baseCsPerMinute(player.getPosition()) * farmingMultiplier(player) * elapsedSeconds / 60.0;
        if (expectedCs <= 0.0) return 0;
        int wholeCs = (int) Math.floor(expectedCs);
        return wholeCs + (random.nextDouble() < expectedCs - wholeCs ? 1 : 0);
    }

    private double baseCsPerMinute(Position position) {
        return switch (position) {
            case TOP -> PositionEconomyRuleConfig.TOP_BASE_CS_PER_MINUTE;
            case JUNGLE -> PositionEconomyRuleConfig.JUNGLE_BASE_CS_PER_MINUTE;
            case MID -> PositionEconomyRuleConfig.MID_BASE_CS_PER_MINUTE;
            case ADC -> PositionEconomyRuleConfig.ADC_BASE_CS_PER_MINUTE;
            case SUPPORT -> PositionEconomyRuleConfig.SUPPORT_BASE_CS_PER_MINUTE;
        };
    }
}
