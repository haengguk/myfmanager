package com.lolfm.champion;

import com.lolfm.simulator.GameState;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.PlayerState;
import com.lolfm.simulator.ProgressionApplicationStage;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CombatChampionPowerEvaluator {
    public ChampionCombatPowerBreakdown evaluate(
            GameState state,
            List<PlayerState> own,
            List<PlayerState> enemy,
            ProgressionCombatContext context,
            ProgressionApplicationStage stage
    ) {
        List<PlayerState> ownEligible = eligible(own, state);
        List<PlayerState> enemyEligible = eligible(enemy, state);
        boolean configured = state.getChampionPowerProfileCatalog().isPresent()
                && state.getChampionAssignments().isPresent();
        boolean enabled = configured && state.isChampionPowerEnabled();
        if (!configured) {
            return new ChampionCombatPowerBreakdown(
                    context, stage, ownEligible.size(), enemyEligible.size(),
                    0, 0, 0, 0, false, Map.of(), Map.of(), false);
        }

        Map<PlayerKey, ChampionPowerBreakdown> ownValues = values(
                state, ownEligible, context);
        Map<PlayerKey, ChampionPowerBreakdown> enemyValues = values(
                state, enemyEligible, context);
        double ownAverage = ChampionCombatPowerBreakdown.averageChampionPower(ownValues);
        double enemyAverage = ChampionCombatPowerBreakdown.averageChampionPower(enemyValues);
        double rawEdge = ownAverage - enemyAverage;
        double edge = ChampionPowerRuleConfig.clampTeamEdge(rawEdge);
        return new ChampionCombatPowerBreakdown(
                context, stage, ownValues.size(), enemyValues.size(),
                ownAverage, enemyAverage, rawEdge, edge,
                Math.abs(rawEdge - edge) > 1e-12,
                ownValues, enemyValues, enabled);
    }

    private Map<PlayerKey, ChampionPowerBreakdown> values(
            GameState state,
            List<PlayerState> players,
            ProgressionCombatContext context
    ) {
        LinkedHashMap<PlayerKey, ChampionPowerBreakdown> result = new LinkedHashMap<>();
        var evaluator = new ChampionPowerProfileEvaluator(
                state.getChampionPowerProfileCatalog().orElseThrow());
        for (PlayerState player : players) {
            PlayerKey key = state.playerKeyOf(player).orElseThrow(
                    () -> new IllegalArgumentException("Non-match participant"));
            ChampionId championId;
            try {
                championId = state.getChampionAssignments().orElseThrow()
                        .get(key).championId();
            } catch (RuntimeException error) {
                state.getChampionPowerExecutionStats().missingAssignment();
                throw error;
            }
            result.put(key, evaluator.evaluate(
                    championId,
                    player.getProgressionState().getLevel(),
                    player.getProgressionState().getItemStage(),
                    context));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static List<PlayerState> eligible(
            List<PlayerState> players,
            GameState state
    ) {
        return players.stream()
                .filter(player -> player.isAlive(state.getCurrentTimeSeconds()))
                .filter(player -> state.playerKeyOf(player).isPresent())
                .distinct()
                .toList();
    }
}
