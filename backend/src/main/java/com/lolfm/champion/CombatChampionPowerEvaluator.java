package com.lolfm.champion;
import com.lolfm.simulator.*;import java.util.*;
public final class CombatChampionPowerEvaluator{
 public ChampionCombatPowerBreakdown evaluate(GameState s,List<PlayerState>own,List<PlayerState>enemy,ProgressionCombatContext c,ProgressionApplicationStage stage){
  List<PlayerState>a=eligible(own,s),b=eligible(enemy,s);boolean configured=s.getChampionPowerProfileCatalog().isPresent()&&s.getChampionAssignments().isPresent(),enabled=configured&&s.isChampionPowerEnabled();
  if(!configured)return new ChampionCombatPowerBreakdown(c,stage,a.size(),b.size(),0,0,0,0,false,Map.of(),Map.of(),false);
  Map<PlayerKey,ChampionPowerBreakdown>av=values(s,a,c),bv=values(s,b,c);double aa=average(av),ba=average(bv),raw=aa-ba,edge=ChampionPowerRuleConfig.clampTeamEdge(raw);
  return new ChampionCombatPowerBreakdown(c,stage,av.size(),bv.size(),aa,ba,raw,edge,Math.abs(raw-edge)>1e-12,av,bv,enabled);
 }
 private Map<PlayerKey,ChampionPowerBreakdown>values(GameState s,List<PlayerState>players,ProgressionCombatContext c){LinkedHashMap<PlayerKey,ChampionPowerBreakdown>r=new LinkedHashMap<>();var e=new ChampionPowerProfileEvaluator(s.getChampionPowerProfileCatalog().orElseThrow());for(PlayerState p:players){PlayerKey k=s.playerKeyOf(p).orElseThrow(()->new IllegalArgumentException("Non-match participant"));ChampionId id;try{id=s.getChampionAssignments().orElseThrow().get(k).championId();}catch(RuntimeException x){s.getChampionPowerExecutionStats().missingAssignment();throw x;}r.put(k,e.evaluate(id,p.getProgressionState().getLevel(),p.getProgressionState().getItemStage(),c));}return Map.copyOf(r);}
 private static List<PlayerState>eligible(List<PlayerState>players,GameState state){return players.stream().filter(x->x.isAlive(state.getCurrentTimeSeconds())).filter(x->state.playerKeyOf(x).isPresent()).distinct().toList();}
 private static double average(Map<PlayerKey,ChampionPowerBreakdown>v){return v.values().stream().mapToDouble(ChampionPowerBreakdown::clampedPlayerChampionPower).average().orElse(0);}
}
