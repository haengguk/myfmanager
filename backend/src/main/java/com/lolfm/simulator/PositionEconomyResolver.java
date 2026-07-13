package com.lolfm.simulator;

import com.lolfm.domain.Position;
import java.util.Random;

/** Resolves all CS and FARM-gold income through one seed-driven path. */
public final class PositionEconomyResolver {
    private final GoldAwardService awards = new GoldAwardService();

    public void resolve(TeamState team, int currentTimeSeconds, int elapsedSeconds, Random random) {
        resolve(null, team, null, currentTimeSeconds, elapsedSeconds, random);
    }

    public void resolve(GameState gameState, TeamState team, TeamSide side, int currentTimeSeconds, int elapsedSeconds, Random random) {
        for (PlayerState player : team.getPlayers()) {
            if (!player.canFarmAt(currentTimeSeconds)) continue;
            if (player.getPosition() == Position.JUNGLE && gameState != null && side != null
                    && currentTimeSeconds < gameState.jungleActionState(side).getJungleFarmBlockedUntilSeconds()) continue;
            int cs = actualCs(player, gameState, side, elapsedSeconds, random);
            if (cs <= 0) continue;
            player.addCs(cs);
            awards.awardGold(team, player, cs * PositionEconomyRuleConfig.CS_GOLD, GoldSource.FARM, false);
        }
    }

    public double farmingMultiplier(PlayerState player) {
        double multiplier = 1.0 + (player.getFarming() - PositionEconomyRuleConfig.FARMING_BASELINE)
                * PositionEconomyRuleConfig.FARMING_MULTIPLIER_PER_POINT;
        return Math.max(PositionEconomyRuleConfig.MIN_FARMING_MULTIPLIER,
                Math.min(PositionEconomyRuleConfig.MAX_FARMING_MULTIPLIER, multiplier));
    }

    private int actualCs(PlayerState player, GameState gameState, TeamSide side, int elapsedSeconds, Random random) {
        double expectedCs = baseCsPerMinute(player.getPosition()) * farmingMultiplier(player)
                * laneCsMultiplier(player.getPosition(), gameState, side) * elapsedSeconds / 60.0;
        if (expectedCs <= 0.0) return 0;
        int wholeCs = (int) Math.floor(expectedCs);
        return wholeCs + (random.nextDouble() < expectedCs - wholeCs ? 1 : 0);
    }

    public double laneCsMultiplier(Position position, GameState gameState, TeamSide side) {
        if (gameState == null || side == null) return 1.0;
        Lane lane = switch (position) {
            case TOP -> Lane.TOP;
            case MID -> Lane.MID;
            case ADC -> Lane.BOT;
            case JUNGLE, SUPPORT -> null;
        };
        if (lane == null) return 1.0;
        double signedModifier = Math.max(-LanePressureRuleConfig.MAX_LANE_CS_MODIFIER,
                Math.min(LanePressureRuleConfig.MAX_LANE_CS_MODIFIER,
                        gameState.laneState(lane).getPressure() / 100.0 * LanePressureRuleConfig.MAX_LANE_CS_MODIFIER));
        return side == TeamSide.BLUE ? 1.0 + signedModifier : 1.0 - signedModifier;
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
