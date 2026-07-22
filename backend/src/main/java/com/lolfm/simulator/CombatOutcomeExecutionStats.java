package com.lolfm.simulator;
import java.util.*;
/** Match-scoped observational counts recorded only after an actual combat winner is known. */
public final class CombatOutcomeExecutionStats{
 private final EnumMap<ProgressionCombatContext,Map<TeamSide,Integer>> wins=new EnumMap<>(ProgressionCombatContext.class);
 private final EnumMap<ProgressionCombatContext,Map<PlayerKey,Integer>> participantWins=new EnumMap<>(ProgressionCombatContext.class);
 private final Set<CombatOutcomeIdentity> recordedOutcomes=new HashSet<>();
 private int duplicate,outcomeWithoutAttempt,outcomeWithoutWinner,participantMismatch;
 public CombatOutcomeExecutionStats(){for(var c:ProgressionCombatContext.values()){var sides=new EnumMap<TeamSide,Integer>(TeamSide.class);for(var s:TeamSide.values())sides.put(s,0);wins.put(c,sides);participantWins.put(c,new HashMap<>());}}
 public void record(ProgressionCombatContext context,int resolvedAtSeconds,boolean actualAttempt,TeamSide winner,List<PlayerState> blue,List<PlayerState> red){
  if(!actualAttempt){outcomeWithoutAttempt++;return;}if(winner==null){outcomeWithoutWinner++;return;}
  Set<PlayerKey> blueKeys=keys(TeamSide.BLUE,blue),redKeys=keys(TeamSide.RED,red);if(blueKeys.isEmpty()||redKeys.isEmpty()){participantMismatch++;return;}
  if(!recordedOutcomes.add(new CombatOutcomeIdentity(context,resolvedAtSeconds,blueKeys,redKeys))){duplicate++;return;}
  Set<PlayerKey> winning=winner==TeamSide.BLUE?blueKeys:redKeys;wins.get(context).merge(winner,1,Integer::sum);for(PlayerKey key:winning)participantWins.get(context).merge(key,1,Integer::sum);
 }
 private Set<PlayerKey> keys(TeamSide side,List<PlayerState> players){Set<PlayerKey> result=new HashSet<>();for(PlayerState p:players)result.add(new PlayerKey(side,p.getPosition()));return Set.copyOf(result);}
 public CombatOutcomeExecutionStatsSnapshot snapshot(){return new CombatOutcomeExecutionStatsSnapshot(wins,participantWins,duplicate,outcomeWithoutAttempt,outcomeWithoutWinner,participantMismatch);}
 private record CombatOutcomeIdentity(ProgressionCombatContext context,int resolvedAtSeconds,Set<PlayerKey> blueParticipants,Set<PlayerKey> redParticipants) { }
}
