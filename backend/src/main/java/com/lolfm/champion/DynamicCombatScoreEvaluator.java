package com.lolfm.champion;

import com.lolfm.simulator.PlayerProgressionPowerEvaluator;
import com.lolfm.simulator.PlayerState;
import com.lolfm.simulator.ProgressionCombatContext;
import com.lolfm.simulator.ProgressionPowerBreakdown;

public final class DynamicCombatScoreEvaluator {
    private final ChampionPowerProfileEvaluator champions;

    public DynamicCombatScoreEvaluator(ChampionPowerProfileCatalog catalog) {
        champions = new ChampionPowerProfileEvaluator(catalog);
    }

    public DynamicCombatScoreBreakdown evaluate(
            PlayerState player,
            ChampionId championId,
            ProgressionCombatContext context
    ) {
        return evaluate(player, championId, context, 0.0);
    }

    public DynamicCombatScoreBreakdown evaluate(
            PlayerState player,
            ChampionId championId,
            ProgressionCombatContext context,
            double championMatchupEdge
    ) {
        double mechanics = player.getMechanics() * .45;
        double aggression = player.getAggression() * .15;
        double farming = player.getFarming() * .10;
        double teamfighting = player.getTeamfighting() * .30;
        double attributes = mechanics + aggression + farming + teamfighting;
        double gold = player.getGold() / 1000.0;
        ProgressionPowerBreakdown common = new PlayerProgressionPowerEvaluator()
                .evaluate(player, context, true);
        ChampionPowerBreakdown champion = champions.evaluate(
                championId, player.getProgressionState().getLevel(),
                player.getProgressionState().getItemStage(), context);
        double championPower = champion.levelModifier() + champion.itemModifier()
                + champion.contextModifier();
        double beforeMatchup = attributes + gold + common.levelPower()
                + common.itemPower() + champion.levelModifier()
                + champion.itemModifier() + champion.contextModifier();
        double normalizedMatchup = championMatchupEdge == 0.0 ? 0.0 : championMatchupEdge;
        double afterMatchup = beforeMatchup + normalizedMatchup;
        return new DynamicCombatScoreBreakdown(
                mechanics, aggression, farming, teamfighting, attributes, gold,
                common.levelPower(), common.itemPower(), champion.levelModifier(),
                champion.itemModifier(), champion.contextModifier(), championPower,
                beforeMatchup, normalizedMatchup, afterMatchup, afterMatchup, champion);
    }
}
