package com.lolfm.champion;

import com.lolfm.simulator.ItemProgressStage;
import com.lolfm.simulator.ProgressionCombatContext;

public final class ChampionPowerProfileEvaluator {
    private final ChampionPowerProfileCatalog catalog;
    public ChampionPowerProfileEvaluator(ChampionPowerProfileCatalog catalog) { this.catalog = catalog; }
    public ChampionPowerBreakdown evaluate(ChampionId id, int level, ItemProgressStage itemStage, ProgressionCombatContext context) {
        ChampionPowerProfile profile = catalog.get(id);
        double levelValue = profile.levelCurve().valueAt(level);
        double itemValue = profile.itemModifiers().get(itemStage);
        double contextValue = profile.contextModifiers().get(context);
        double raw = levelValue + itemValue + contextValue;
        double clamped = ChampionPowerRuleConfig.clampPlayer(raw);
        return new ChampionPowerBreakdown(id, profile.profileVersion(), level, itemStage, context,
                levelValue, itemValue, contextValue, raw, clamped, Math.abs(raw - clamped) > 1e-12);
    }
}
