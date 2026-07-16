package com.lolfm.simulator;
import com.lolfm.domain.BaseThreatSnapshot;import java.util.EnumSet;
/** Stateless, read-only derivation from the existing map structure truth. */
public final class BaseThreatEvaluator{
 public BaseThreatSnapshot evaluate(GameState s,TeamSide d){MapState m=s.getMapState();BaseState b=m.getBaseState(d);int destroyed=3-m.getAliveInhibitorCount(d);
  if(!b.isNexusAlive())return new BaseThreatSnapshot(d,BaseThreatLevel.MATCH_ENDED,SetUtil.none(),null,null,destroyed,b.getNexusTurretsRemaining(),true,false);
  if(m.isNexusVulnerable(d))return new BaseThreatSnapshot(d,BaseThreatLevel.NEXUS_THREAT,SetUtil.none(),null,LateGameStructureTarget.NEXUS,destroyed,0,true,true);
  if(m.areNexusTurretsVulnerable(d))return new BaseThreatSnapshot(d,BaseThreatLevel.NEXUS_TURRET_THREAT,SetUtil.none(),null,LateGameStructureTarget.NEXUS_TURRET,destroyed,b.getNexusTurretsRemaining(),false,true);
  EnumSet<Lane> inhibitors=EnumSet.noneOf(Lane.class),towers=EnumSet.noneOf(Lane.class);Lane deepest=null;int max=-1;
  for(Lane lane:Lane.values()){LateGameStructureTarget next=nextLaneTarget(m.getLaneState(d,lane));if(next==LateGameStructureTarget.INHIBITOR)inhibitors.add(lane);if(next==LateGameStructureTarget.INHIBITOR_TOWER)towers.add(lane);int p=m.calculateLaneProgress(d,lane);if(p>max){max=p;deepest=lane;}}
  if(!inhibitors.isEmpty())return new BaseThreatSnapshot(d,BaseThreatLevel.INHIBITOR_THREAT,inhibitors,deepest(inhibitors,m,d),LateGameStructureTarget.INHIBITOR,destroyed,b.getNexusTurretsRemaining(),false,true);
  if(!towers.isEmpty())return new BaseThreatSnapshot(d,BaseThreatLevel.INHIBITOR_TOWER_THREAT,towers,deepest(towers,m,d),LateGameStructureTarget.INHIBITOR_TOWER,destroyed,b.getNexusTurretsRemaining(),false,true);
  return new BaseThreatSnapshot(d,BaseThreatLevel.NONE,SetUtil.none(),deepest,deepest==null?null:nextLaneTarget(m.getLaneState(d,deepest)),destroyed,b.getNexusTurretsRemaining(),false,true);
 }
 public LateGameStructureTarget nextTarget(GameState s,TeamSide attacking,Lane lane){TeamSide d=attacking.opposite();MapState m=s.getMapState();if(m.isNexusVulnerable(d))return LateGameStructureTarget.NEXUS;if(m.areNexusTurretsVulnerable(d))return LateGameStructureTarget.NEXUS_TURRET;return lane==null?null:nextLaneTarget(m.getLaneState(d,lane));}
 public LateGameStructureTarget nextLaneTarget(LaneStructureState l){if(l.isOuterTowerAlive())return LateGameStructureTarget.OUTER;if(l.isInnerTowerAlive())return LateGameStructureTarget.INNER;if(l.isInhibitorTowerAlive())return LateGameStructureTarget.INHIBITOR_TOWER;if(l.isInhibitorAlive())return LateGameStructureTarget.INHIBITOR;return null;}
 private Lane deepest(EnumSet<Lane> lanes,MapState m,TeamSide d){Lane result=null;int max=-1;for(Lane lane:lanes){int p=m.calculateLaneProgress(d,lane);if(p>max){max=p;result=lane;}}return result;}
 private static final class SetUtil{static EnumSet<Lane> none(){return EnumSet.noneOf(Lane.class);}}
}
