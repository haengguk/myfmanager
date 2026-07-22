package com.lolfm.champion;import java.util.Set;
public record ChampionPowerProfileSnapshot(String profileVersion,String levelCurveId,String itemCurveId,Set<ChampionTag> tags,String profileSummary,double currentLevelModifier,double currentItemModifier,double currentNonContextModifier,boolean championPowerEnabled){public ChampionPowerProfileSnapshot{tags=Set.copyOf(tags);}}
