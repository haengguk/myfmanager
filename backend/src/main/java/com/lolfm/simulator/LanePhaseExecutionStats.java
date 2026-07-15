package com.lolfm.simulator;

import com.lolfm.domain.OuterTurretSiegeData;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class LanePhaseExecutionStats {
    private final boolean enabled;
    private int evaluationTicks, lanePhaseIneligible, pressureBelowThreshold, attackerDead;
    private int attackerActivityIneligible, targetAlreadyDestroyed, featureDisabled;
    private int actualSieges, siegeRandomRolls, timeLimitTransitions, allLanesOpenTransitions;
    private int duplicateLaneTransitions, duplicateMatchTransitions;
    private int positivePressureDecays, negativePressureDecays, pressureNearNeutral;
    private int laneCombatExcluded, jungleGankExcluded, roamOriginExcluded, roamTargetExcluded;
    private final EnumMap<Lane,Integer> laneEvaluations=new EnumMap<>(Lane.class);
    private final EnumMap<TeamSide,Integer> destroyedSides=new EnumMap<>(TeamSide.class);
    private final EnumMap<Lane,Integer> destroyedLanes=new EnumMap<>(Lane.class);
    private final EnumMap<StructureActionSource,Integer> destroyedSources=new EnumMap<>(StructureActionSource.class);
    private final EnumMap<Lane,Integer> laneOpenCounts=new EnumMap<>(Lane.class);
    private final List<OuterTurretSiegeData> sieges=new ArrayList<>();

    public LanePhaseExecutionStats(boolean enabled) {
        this.enabled=enabled;
        for(Lane lane:Lane.values()){laneEvaluations.put(lane,0);destroyedLanes.put(lane,0);laneOpenCounts.put(lane,0);}
        for(TeamSide side:TeamSide.values())destroyedSides.put(side,0);
        for(StructureActionSource source:StructureActionSource.values())destroyedSources.put(source,0);
    }
    public void recordEvaluationTick(){evaluationTicks++;}
    public void recordLaneEvaluation(Lane lane){laneEvaluations.merge(lane,1,Integer::sum);}
    public void recordLanePhaseIneligible(){lanePhaseIneligible++;}
    public void recordPressureBelowThreshold(){pressureBelowThreshold++;}
    public void recordAttackerDead(){attackerDead++;}
    public void recordAttackerActivityIneligible(){attackerActivityIneligible++;}
    public void recordTargetAlreadyDestroyed(){targetAlreadyDestroyed++;}
    public void recordFeatureDisabled(){featureDisabled++;}
    public void recordSiege(OuterTurretSiegeData data){actualSieges++;siegeRandomRolls++;sieges.add(data);}
    public void recordDestruction(TeamSide owner,Lane lane,StructureActionSource source){destroyedSides.merge(owner,1,Integer::sum);destroyedLanes.merge(lane,1,Integer::sum);destroyedSources.merge(source,1,Integer::sum);}
    public void recordLaneOpen(Lane lane){laneOpenCounts.merge(lane,1,Integer::sum);}
    public void recordDuplicateLaneTransition(){duplicateLaneTransitions++;}
    public void recordTransition(MidGameTransitionReason reason){if(reason==MidGameTransitionReason.TIME_LIMIT)timeLimitTransitions++;else allLanesOpenTransitions++;}
    public void recordDuplicateMatchTransition(){duplicateMatchTransitions++;}
    public void recordPressureDecay(double before,double after){if(before>0)positivePressureDecays++;else if(before<0)negativePressureDecays++;if(Math.abs(after)<1.0)pressureNearNeutral++;}
    public void recordLaneCombatExcluded(){laneCombatExcluded++;}
    public void recordJungleGankExcluded(){jungleGankExcluded++;}
    public void recordRoamOriginExcluded(){roamOriginExcluded++;}
    public void recordRoamTargetExcluded(){roamTargetExcluded++;}

    public LanePhaseExecutionStatsSnapshot snapshot(){return new LanePhaseExecutionStatsSnapshot(enabled,evaluationTicks,laneEvaluations,lanePhaseIneligible,pressureBelowThreshold,attackerDead,attackerActivityIneligible,targetAlreadyDestroyed,featureDisabled,actualSieges,siegeRandomRolls,sieges,destroyedSides,destroyedLanes,destroyedSources,laneOpenCounts,timeLimitTransitions,allLanesOpenTransitions,duplicateLaneTransitions,duplicateMatchTransitions,positivePressureDecays,negativePressureDecays,pressureNearNeutral,laneCombatExcluded,jungleGankExcluded,roamOriginExcluded,roamTargetExcluded);}
}
