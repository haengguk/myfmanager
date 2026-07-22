package com.lolfm.champion;
import com.lolfm.simulator.*;
public final class DynamicCombatScoreEvaluator{
 private final ChampionPowerProfileEvaluator champions;
 public DynamicCombatScoreEvaluator(ChampionPowerProfileCatalog catalog){champions=new ChampionPowerProfileEvaluator(catalog);}
 public DynamicCombatScoreBreakdown evaluate(PlayerState p,ChampionId id,ProgressionCombatContext context){
  double mechanics=p.getMechanics()*.45,aggression=p.getAggression()*.15,farming=p.getFarming()*.10,teamfighting=p.getTeamfighting()*.30;
  double attributes=mechanics+aggression+farming+teamfighting;
  double gold=p.getGold()/1000.0;ProgressionPowerBreakdown common=new PlayerProgressionPowerEvaluator().evaluate(p,context,true);
  ChampionPowerBreakdown champion=champions.evaluate(id,p.getProgressionState().getLevel(),p.getProgressionState().getItemStage(),context);
  double score=attributes+gold+common.levelPower()+common.itemPower()+champion.levelModifier()+champion.itemModifier()+champion.contextModifier();
  return new DynamicCombatScoreBreakdown(mechanics,aggression,farming,teamfighting,attributes,gold,common.levelPower(),common.itemPower(),champion.levelModifier(),champion.itemModifier(),champion.contextModifier(),score,champion);
 }
}
