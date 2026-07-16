package com.lolfm.simulator;
import com.lolfm.domain.LateGameDecisionData;import java.util.EnumMap;
public final class LateGameState{
 private final boolean enabled;private int started=-1,next=-1,sequence,lastProcessed=-1;private LateGameTransitionReason reason;private LateGameDecisionData latest;private final EnumMap<TeamSide,LateGameTeamPlanState> plans=new EnumMap<>(TeamSide.class);private final LateGameExecutionStats stats=new LateGameExecutionStats();
 public LateGameState(boolean enabled){this.enabled=enabled;for(TeamSide side:TeamSide.values())plans.put(side,new LateGameTeamPlanState());if(!enabled)plans.values().forEach(LateGameTeamPlanState::disable);}
 public boolean isEnabled(){return enabled;}public int getLateGameStartedAtSeconds(){return started;}public int getNextEvaluationAtSeconds(){return next;}public int getEvaluationSequence(){return sequence;}public LateGameTransitionReason getTransitionReason(){return reason;}public LateGameDecisionData getLatestDecision(){return latest;}public LateGameTeamPlanState teamPlan(TeamSide s){return plans.get(s);}public LateGameExecutionStats getStats(){return stats;}
 public boolean start(int time,LateGameTransitionReason r){if(started>=0){stats.duplicate();return false;}started=time;reason=r;next=enabled?time+LateGameRuleConfig.FIRST_EVALUATION_DELAY_SECONDS:-1;stats.transition(r);return true;}
 public boolean due(int time){return enabled&&started>=0&&next>=0&&time>=next&&time!=lastProcessed;}
 public int beginEvaluation(int time){if(!due(time))throw new IllegalStateException("late-game evaluation not due");int due=next;lastProcessed=time;sequence++;do{next+=LateGameRuleConfig.EVALUATION_INTERVAL_SECONDS;}while(next<=time);stats.evaluation();return due;}
 public void record(LateGameDecisionData value){latest=value;}public void expire(int time){plans.values().forEach(p->p.expire(time));}
 public void finish(){next=-1;plans.values().forEach(LateGameTeamPlanState::finish);}
}
