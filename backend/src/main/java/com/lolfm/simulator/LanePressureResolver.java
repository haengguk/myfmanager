package com.lolfm.simulator;

import com.lolfm.domain.Position;
import java.util.Random;

/** Stateless lane-pressure calculation; all mutable pressure and timing live in GameState. */
public final class LanePressureResolver {
    public void resolve(GameState state, int currentTimeSeconds, Random random) {
        if (!state.shouldResolveLanePressureAt(currentTimeSeconds)) return;
        for (Lane lane : Lane.values()) {
            LaneState laneState = state.laneState(lane);
            double next = calculateNextPressure(laneState.getPressure(), lanePowerDifference(state, lane),
                    goldModifier(state, lane), randomVariation(random));
            laneState.setPressure(next);
        }
        state.markLanePressureResolvedAt(currentTimeSeconds);
    }

    public double calculateNextPressure(double currentPressure, double attributeDifference, double goldModifier, double randomVariation) {
        return clamp(currentPressure * LanePressureRuleConfig.PRESSURE_RETENTION
                + attributeDifference * LanePressureRuleConfig.ATTRIBUTE_DIFFERENCE_FACTOR
                + goldModifier + randomVariation,
                LanePressureRuleConfig.PRESSURE_MIN, LanePressureRuleConfig.PRESSURE_MAX);
    }

    public double lanePowerDifference(GameState state, Lane lane) {
        return lanePower(state.getBlueTeamState(), lane) - lanePower(state.getRedTeamState(), lane);
    }

    public double goldModifier(GameState state, Lane lane) {
        double difference = laneGold(state.getBlueTeamState(), lane) - laneGold(state.getRedTeamState(), lane);
        return clamp(difference / LanePressureRuleConfig.GOLD_DIFFERENCE_DIVISOR,
                LanePressureRuleConfig.GOLD_MODIFIER_MIN, LanePressureRuleConfig.GOLD_MODIFIER_MAX);
    }

    private double lanePower(TeamState team, Lane lane) {
        return switch (lane) {
            case TOP -> power(team.playerAt(Position.TOP), .40, .30, .20, .10);
            case MID -> power(team.playerAt(Position.MID), .35, .30, .20, .15);
            case BOT -> power(team.playerAt(Position.ADC), .35, .40, .10, .15) * LanePressureRuleConfig.BOT_ADC_CONTRIBUTION
                    + power(team.playerAt(Position.SUPPORT), .25, .10, .35, .30) * LanePressureRuleConfig.BOT_SUPPORT_CONTRIBUTION;
        };
    }

    private double laneGold(TeamState team, Lane lane) {
        return switch (lane) {
            case TOP -> team.playerAt(Position.TOP).getGold();
            case MID -> team.playerAt(Position.MID).getGold();
            case BOT -> (team.playerAt(Position.ADC).getGold() + team.playerAt(Position.SUPPORT).getGold()) / 2.0;
        };
    }

    private double power(PlayerState p, double mechanics, double farming, double aggression, double teamfighting) {
        return p.getMechanics() * mechanics + p.getFarming() * farming + p.getAggression() * aggression + p.getTeamfighting() * teamfighting;
    }
    private double randomVariation(Random random) { return LanePressureRuleConfig.RANDOM_VARIATION_MIN + random.nextDouble() * (LanePressureRuleConfig.RANDOM_VARIATION_MAX - LanePressureRuleConfig.RANDOM_VARIATION_MIN); }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
