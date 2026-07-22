package com.lolfm.champion;

import com.lolfm.simulator.ItemProgressStage;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.Map;
import java.util.Set;

public record ChampionPowerProfile(ChampionId championId, String levelCurveId, String itemCurveId,
        LevelPowerCurve levelCurve, Map<ItemProgressStage, Double> itemModifiers,
        Map<ProgressionCombatContext, Double> contextModifiers, Set<ChampionTag> tags, String profileVersion) {
    public ChampionPowerProfile {
        itemModifiers = Map.copyOf(itemModifiers); contextModifiers = Map.copyOf(contextModifiers); tags = Set.copyOf(tags);
        if (itemModifiers.size() != ItemProgressStage.values().length) throw new IllegalArgumentException("Incomplete item curve: " + championId);
        if (contextModifiers.size() != ProgressionCombatContext.values().length) throw new IllegalArgumentException("Incomplete contexts: " + championId);
        itemModifiers.values().forEach(ChampionPowerProfile::validateValue);
        contextModifiers.values().forEach(ChampionPowerProfile::validateValue);
    }
    private static void validateValue(double value) {
        if (!Double.isFinite(value) || value < ChampionPowerRuleConfig.PROFILE_VALUE_MIN || value > ChampionPowerRuleConfig.PROFILE_VALUE_MAX) throw new IllegalArgumentException("Profile modifier out of range: " + value);
    }
    public String summaryKo() { return tags.stream().limit(4).map(ChampionTag::displayNameKo).reduce((a, b) -> a + " · " + b).orElse("균형"); }
}
