package com.lolfm.simulator;
import com.lolfm.domain.LateGameTeamPlanSnapshot;import com.lolfm.domain.Position;import java.util.EnumSet;import java.util.Set;
public final class LateGameTeamPlanState{
 private LateGameAttackPlan attackPlan;private LateGameDefenseResponse defenseResponse;private Lane targetLane;private LateGameStructureTarget targetStructure;private final EnumSet<Position> positions=EnumSet.noneOf(Position.class);private int started=-1,until=-1;private LateGamePlanStatus status=LateGamePlanStatus.NOT_STARTED;private LateGameActionResult result=LateGameActionResult.NOT_EVALUATED;private LateGamePlanEndReason endReason;
 void beginAttack(LateGameAttackPlan p,Lane lane,LateGameStructureTarget target,Set<Position> assigned,int time){close(LateGamePlanEndReason.REPLACED);attackPlan=p;defenseResponse=null;targetLane=lane;targetStructure=target;positions.clear();positions.addAll(assigned);started=time;until=time+LateGameRuleConfig.PLAN_DURATION_SECONDS;status=LateGamePlanStatus.ACTIVE;result=LateGameActionResult.NOT_EVALUATED;endReason=null;}
 void beginDefense(LateGameDefenseResponse r,Lane lane,LateGameStructureTarget target,Set<Position> assigned,int time){close(LateGamePlanEndReason.REPLACED);attackPlan=null;defenseResponse=r;targetLane=lane;targetStructure=target;positions.clear();positions.addAll(assigned);started=time;until=time+LateGameRuleConfig.PLAN_DURATION_SECONDS;status=LateGamePlanStatus.ACTIVE;result=LateGameActionResult.NOT_EVALUATED;endReason=null;}
 void expire(int time){if(status==LateGamePlanStatus.ACTIVE&&time>=until){close(LateGamePlanEndReason.EXPIRED);status=LateGamePlanStatus.EXPIRED;}}
 void finish(){close(LateGamePlanEndReason.GAME_FINISHED);status=LateGamePlanStatus.MATCH_ENDED;result=LateGameActionResult.GAME_FINISHED;}
 void disable(){close(LateGamePlanEndReason.FEATURE_DISABLED);status=LateGamePlanStatus.DISABLED;}
 void setResult(LateGameActionResult value){result=value;}
 private void close(LateGamePlanEndReason reason){if(status!=LateGamePlanStatus.ACTIVE)return;attackPlan=null;defenseResponse=null;targetLane=null;targetStructure=null;positions.clear();started=-1;until=-1;endReason=reason;}
 public LateGameTeamPlanSnapshot snapshot(String role){return new LateGameTeamPlanSnapshot(role,attackPlan,defenseResponse,targetLane,targetStructure,positions,started,until,status,result,endReason);}
}
