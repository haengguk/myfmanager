package com.lolfm.champion;
import com.lolfm.simulator.*;import java.util.Map;
public record ChampionCombatPowerBreakdown(ProgressionCombatContext context,ProgressionApplicationStage applicationStage,int ownParticipantCount,int enemyParticipantCount,double ownAverageChampionPower,double enemyAverageChampionPower,double rawChampionEdge,double finalChampionEdge,boolean teamEdgeClampApplied,Map<PlayerKey,ChampionPowerBreakdown> ownParticipants,Map<PlayerKey,ChampionPowerBreakdown> enemyParticipants,boolean championPowerEnabled){
 public ChampionCombatPowerBreakdown{ownParticipants=Map.copyOf(ownParticipants);enemyParticipants=Map.copyOf(enemyParticipants);}
 public double finalContribution(){return championPowerEnabled?finalChampionEdge:0;}
 public double levelContribution(){return championPowerEnabled?average(ownParticipants,true)-average(enemyParticipants,true):0;}
 public double itemContribution(){return championPowerEnabled?average(ownParticipants,false)-average(enemyParticipants,false):0;}
 public double contextContribution(){return finalContribution()-levelContribution()-itemContribution();}
 private static double average(Map<PlayerKey,ChampionPowerBreakdown>v,boolean level){return v.values().stream().mapToDouble(x->level?x.levelModifier():x.itemModifier()).average().orElse(0);}
}
