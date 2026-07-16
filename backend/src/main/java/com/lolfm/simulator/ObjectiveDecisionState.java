package com.lolfm.simulator;
import com.lolfm.domain.*;
import java.util.*;
public final class ObjectiveDecisionState {
 private final boolean enabled; private int sequence; private final Set<ObjectiveDecisionKey> resolved=new HashSet<>(); private final List<ObjectiveDecisionData> history=new ArrayList<>(); private final ObjectiveDecisionExecutionStats stats;
 ObjectiveDecisionState(boolean enabled,boolean diagnostics){this.enabled=enabled;stats=new ObjectiveDecisionExecutionStats(diagnostics);}
 public boolean isEnabled(){return enabled;} public int getDecisionSequence(){return sequence;} public List<ObjectiveDecisionData> getHistory(){return List.copyOf(history);} public Set<ObjectiveDecisionKey> getResolvedDecisionKeys(){return Set.copyOf(resolved);} public ObjectiveDecisionExecutionStats getStats(){return stats;}
 boolean reserve(ObjectiveDecisionKey key){if(!enabled)return false;if(!resolved.add(key)){stats.duplicate();return false;}return true;}
 int nextSequence(){return ++sequence;}
 void record(ObjectiveDecisionData data){history.add(data);stats.record(data);}
 public ObjectiveDecisionSnapshot snapshot(){if(!enabled)return ObjectiveDecisionSnapshot.disabled();ObjectiveDecisionData overall=history.isEmpty()?null:history.getLast();return new ObjectiveDecisionSnapshot(true,overall,latest(ObjectiveType.DRAGON),latest(ObjectiveType.BARON),latest(ObjectiveType.ELDER),latest(TeamSide.BLUE),latest(TeamSide.RED));}
 private ObjectiveDecisionData latest(ObjectiveType type){for(int i=history.size()-1;i>=0;i--)if(history.get(i).objectiveType()==type)return history.get(i);return null;}
 private ObjectiveDecisionData latest(TeamSide side){for(int i=history.size()-1;i>=0;i--)if(history.get(i).initiativeSide()==side||history.get(i).responderSide()==side)return history.get(i);return null;}
}
