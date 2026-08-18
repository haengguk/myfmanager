package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupResolver;
import com.lolfm.champion.CombatChampionPowerEvaluator;
import com.lolfm.domain.Position;
import java.util.List;

/** Deterministic champion contribution to sustained lane opportunity. */
public final class LaneOpportunityEvaluator {
    private final CombatChampionPowerEvaluator championPower = new CombatChampionPowerEvaluator();
    private final ChampionMatchupResolver matchup = new ChampionMatchupResolver();

    public double attributeDifference(GameState state, Lane lane) {
        List<PlayerState> blue = participants(state.getBlueTeamState(), lane);
        List<PlayerState> red = participants(state.getRedTeamState(), lane);
        if (blue.stream().noneMatch(PlayerState::hasMatchPerformance)
                && red.stream().noneMatch(PlayerState::hasMatchPerformance)) return 0.0;

        double power = championPower.evaluate(state, blue, red,
                ProgressionCombatContext.LANE_COMBAT, ProgressionApplicationStage.INITIATIVE)
                .finalContribution();
        double matchupEdge = matchup.evaluate(state, blue, red,
                ProgressionCombatContext.LANE_COMBAT, ProgressionApplicationStage.INITIATIVE)
                .matchupEdge();
        return clamp((power + matchupEdge) * LanePressureRuleConfig.CHAMPION_LANE_OPPORTUNITY_SCALE,
                -LanePressureRuleConfig.MAX_CHAMPION_LANE_OPPORTUNITY_ATTRIBUTE,
                LanePressureRuleConfig.MAX_CHAMPION_LANE_OPPORTUNITY_ATTRIBUTE);
    }

    private List<PlayerState> participants(TeamState team, Lane lane) {
        return switch (lane) {
            case TOP -> List.of(team.playerAt(Position.TOP));
            case MID -> List.of(team.playerAt(Position.MID));
            case BOT -> List.of(team.playerAt(Position.ADC), team.playerAt(Position.SUPPORT));
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
