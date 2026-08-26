package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupResolver;
import com.lolfm.champion.CombatChampionPowerEvaluator;
import java.util.List;

/** Computes combat progression inputs and records execution diagnostics only on the runtime path. */
public final class CombatProgressionEvaluator {
    public CombatProgressionBreakdown evaluate(
            GameState state, ProgressionCombatContext context,
            List<PlayerState> own, List<PlayerState> enemy) {
        return evaluateAndRecord(state, context, own, enemy, Double.NaN, Double.NaN,
                ProgressionApplicationStage.COMBAT_SCORE);
    }

    public CombatProgressionBreakdown evaluate(
            GameState state, ProgressionCombatContext context,
            List<PlayerState> own, List<PlayerState> enemy,
            double existingScoreBeforeProgression, double goldContribution) {
        return evaluateAndRecord(state, context, own, enemy, existingScoreBeforeProgression,
                goldContribution, ProgressionApplicationStage.COMBAT_SCORE);
    }

    public CombatProgressionBreakdown evaluate(
            GameState state, ProgressionCombatContext context,
            List<PlayerState> own, List<PlayerState> enemy,
            double existingScoreBeforeProgression, double goldContribution,
            ProgressionApplicationStage stage) {
        return evaluateAndRecord(state, context, own, enemy, existingScoreBeforeProgression,
                goldContribution, stage);
    }

    public CombatProgressionBreakdown evaluateAndRecord(
            GameState state, ProgressionCombatContext context,
            List<PlayerState> own, List<PlayerState> enemy,
            double existingScoreBeforeProgression, double goldContribution,
            ProgressionApplicationStage stage) {
        return evaluate(state, context, own, enemy, existingScoreBeforeProgression,
                goldContribution, stage, true);
    }

    public CombatProgressionBreakdown evaluatePure(
            GameState state, ProgressionCombatContext context,
            List<PlayerState> own, List<PlayerState> enemy,
            double existingScoreBeforeProgression, double goldContribution,
            ProgressionApplicationStage stage) {
        return evaluate(state, context, own, enemy, existingScoreBeforeProgression,
                goldContribution, stage, false);
    }

    private CombatProgressionBreakdown evaluate(
            GameState state, ProgressionCombatContext context,
            List<PlayerState> own, List<PlayerState> enemy,
            double existingScoreBeforeProgression, double goldContribution,
            ProgressionApplicationStage stage, boolean recordDiagnostics) {
        int time = state.getCurrentTimeSeconds();
        List<PlayerState> eligibleOwn = eligible(own, time);
        List<PlayerState> eligibleEnemy = eligible(enemy, time);
        boolean progressionEnabled = state.isProgressionPowerEnabled();
        Averages ownAverages = averages(eligibleOwn, context, progressionEnabled);
        Averages enemyAverages = averages(eligibleEnemy, context, progressionEnabled);
        double levelEdge = ownAverages.level - enemyAverages.level;
        double itemEdge = ownAverages.item - enemyAverages.item;
        double progressionEdge = ownAverages.total - enemyAverages.total;
        double multiplier = ProgressionRuleConfig.contextMultiplier(context);
        double levelContribution = levelEdge * multiplier;
        double itemContribution = itemEdge * multiplier;
        double progressionContribution = progressionEdge * multiplier;
        TeamSide ownSide = sideOf(state, eligibleOwn, own);

        if (recordDiagnostics) {
            state.getProgressionExecutionStats().combatSample(new ProgressionCombatSample(
                    time, context, stage, ownSide, eligibleOwn.size(), eligibleEnemy.size(),
                    ownAverages.level, enemyAverages.level, ownAverages.item, enemyAverages.item,
                    ownAverages.total, enemyAverages.total,
                    ownAverages.clamped + enemyAverages.clamped, levelEdge, itemEdge,
                    progressionEdge, multiplier, levelContribution, itemContribution,
                    progressionContribution, existingScoreBeforeProgression, goldContribution));
            if (progressionEnabled) {
                state.getProgressionExecutionStats().power(
                        context, progressionEdge, progressionContribution);
            }
        }

        CombatChampionPowerEvaluator championEvaluator = new CombatChampionPowerEvaluator();
        var champion = recordDiagnostics
                ? championEvaluator.evaluate(state, eligibleOwn, eligibleEnemy, context, stage)
                : championEvaluator.evaluatePure(state, eligibleOwn, eligibleEnemy, context, stage);
        double championContribution = champion.finalContribution();
        double scoreBeforeMatchup = progressionContribution + championContribution;
        ChampionMatchupResolver matchupResolver = new ChampionMatchupResolver();
        var matchup = recordDiagnostics
                ? matchupResolver.evaluate(state, own, enemy, context, stage)
                : matchupResolver.evaluatePure(state, own, enemy, context, stage);
        double matchupContribution = matchup.matchupEdge();
        double combined = scoreBeforeMatchup + matchupContribution;

        if (recordDiagnostics) {
            state.getChampionPowerExecutionStats().record(
                    new com.lolfm.champion.ChampionPowerCombatSample(
                            time, context, stage, ownSide, eligibleOwn.size(), eligibleEnemy.size(),
                            keys(state, eligibleOwn), keys(state, eligibleEnemy),
                            champion.ownAverageChampionPower(), champion.enemyAverageChampionPower(),
                            champion.rawChampionEdge(), champion.finalChampionEdge(),
                            champion.levelContribution(), champion.itemContribution(),
                            champion.contextContribution(), championContribution,
                            existingScoreBeforeProgression, goldContribution,
                            levelContribution, itemContribution, scoreBeforeMatchup,
                            champion.ownParticipants().values().stream().anyMatch(
                                    com.lolfm.champion.ChampionPowerBreakdown::playerClampApplied)
                                    || champion.enemyParticipants().values().stream().anyMatch(
                                    com.lolfm.champion.ChampionPowerBreakdown::playerClampApplied),
                            champion.teamEdgeClampApplied(), champion.championPowerEnabled()));
        }

        return new CombatProgressionBreakdown(context, eligibleOwn.size(), eligibleEnemy.size(),
                ownAverages.total, enemyAverages.total, progressionEdge, multiplier,
                progressionContribution, championContribution, matchupContribution,
                scoreBeforeMatchup, combined, champion, matchup);
    }

    public double contribution(GameState state, ProgressionCombatContext context,
                               List<PlayerState> own, List<PlayerState> enemy) {
        return evaluate(state, context, own, enemy).finalContribution();
    }

    public double contribution(GameState state, ProgressionCombatContext context,
                               List<PlayerState> own, List<PlayerState> enemy,
                               double existingScoreBeforeProgression, double goldContribution) {
        return evaluate(state, context, own, enemy, existingScoreBeforeProgression,
                goldContribution).finalContribution();
    }

    public double contribution(GameState state, ProgressionCombatContext context,
                               List<PlayerState> own, List<PlayerState> enemy,
                               double existingScoreBeforeProgression, double goldContribution,
                               ProgressionApplicationStage stage) {
        return evaluate(state, context, own, enemy, existingScoreBeforeProgression,
                goldContribution, stage).finalContribution();
    }

    private List<PlayerState> eligible(List<PlayerState> players, int time) {
        return players.stream().filter(player -> player.isAlive(time)).distinct().toList();
    }

    private Averages averages(List<PlayerState> players, ProgressionCombatContext context,
                              boolean enabled) {
        if (players.isEmpty()) return new Averages(0, 0, 0, 0);
        double level = 0, item = 0, total = 0;
        int clamped = 0;
        PlayerProgressionPowerEvaluator evaluator = new PlayerProgressionPowerEvaluator();
        for (PlayerState player : players) {
            ProgressionPowerBreakdown value = evaluator.evaluate(player, context, enabled);
            level += value.levelPower();
            item += value.itemPower();
            total += value.clampedTotalPower();
            if (value.clampedTotalPower() >= ProgressionRuleConfig.MAX_PLAYER_PROGRESSION_POWER) {
                clamped++;
            }
        }
        return new Averages(level / players.size(), item / players.size(),
                total / players.size(), clamped);
    }

    private List<PlayerKey> keys(GameState state, List<PlayerState> players) {
        return players.stream().map(state::playerKeyOf).flatMap(java.util.Optional::stream).toList();
    }

    private TeamSide sideOf(GameState state, List<PlayerState> eligible, List<PlayerState> original) {
        PlayerState sample = !eligible.isEmpty() ? eligible.getFirst()
                : original.stream().findFirst().orElse(null);
        if (sample == null) return null;
        if (state.getBlueTeamState().getPlayers().contains(sample)) return TeamSide.BLUE;
        if (state.getRedTeamState().getPlayers().contains(sample)) return TeamSide.RED;
        return null;
    }

    private record Averages(double level, double item, double total, int clamped) { }
}
