package com.lolfm.simulator;
import java.util.*;
public record CombatOutcomeExecutionStatsSnapshot(Map<ProgressionCombatContext,Map<TeamSide,Integer>> wins,Map<ProgressionCombatContext,Map<PlayerKey,Integer>> participantWins,int duplicateOutcomeRecordErrors,int outcomeWithoutAttemptErrors,int outcomeWithoutWinnerErrors,int participantMismatchErrors){
 public CombatOutcomeExecutionStatsSnapshot{var w=new EnumMap<ProgressionCombatContext,Map<TeamSide,Integer>>(ProgressionCombatContext.class);wins.forEach((k,v)->w.put(k,Map.copyOf(v)));wins=Map.copyOf(w);var p=new EnumMap<ProgressionCombatContext,Map<PlayerKey,Integer>>(ProgressionCombatContext.class);participantWins.forEach((k,v)->p.put(k,Map.copyOf(v)));participantWins=Map.copyOf(p);}
 public int wins(ProgressionCombatContext context,TeamSide side){return wins.getOrDefault(context,Map.of()).getOrDefault(side,0);}
 public int participantWins(ProgressionCombatContext context,PlayerKey key){return participantWins.getOrDefault(context,Map.of()).getOrDefault(key,0);}
}
