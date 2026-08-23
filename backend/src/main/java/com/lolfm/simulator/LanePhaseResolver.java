package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.composition.CompositionActionType;
import com.lolfm.composition.CompositionBaselineScoreDomain;
import com.lolfm.composition.FightScale;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchPhaseChangeData;
import com.lolfm.domain.OuterTurretSiegeData;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class LanePhaseResolver {
    public List<StructureOutcome> resolveOuterSieges(GameState state,int time,Random random,StructureResolver structures){
        LanePhaseState phases=state.getLanePhaseState();
        if(!phases.shouldEvaluateOuterSiegeAt(time))return List.of();
        phases.markOuterSiegeEvaluatedAt(time);
        LanePhaseExecutionStats stats=state.getLanePhaseExecutionStats();stats.recordEvaluationTick();
        List<StructureOutcome> destroyed=new ArrayList<>();
        for(Lane lane:Lane.values()){
            stats.recordLaneEvaluation(lane);
            if(!phases.isLaning(lane)){stats.recordLanePhaseIneligible();continue;}
            double pressure=state.laneState(lane).getPressure();
            if(Math.abs(pressure)<LanePhaseRuleConfig.MIN_SIEGE_PRESSURE){stats.recordPressureBelowThreshold();continue;}
            TeamSide attacking=pressure>0?TeamSide.BLUE:TeamSide.RED,defending=attacking.opposite();
            if(state.wasStructureActionPerformedThisTick(attacking)){state.recordLaterStructureResolverBlockedByAttempt();continue;}
            LaneStructureState target=state.getMapState().getLaneState(defending,lane);
            if(!target.isOuterTowerAlive()){stats.recordTargetAlreadyDestroyed();continue;}
            PlayerState primary=primary(state.getTeamState(attacking),lane);
            if(!primary.isAlive(time)){stats.recordAttackerDead();continue;}
            if(primary.getActivityState().getActivityType()!=PlayerActivityType.DEFAULT_ROLE||state.wasMajorCombatParticipantThisTick(primary)){
                stats.recordAttackerActivityIneligible();continue;
            }
            PlayerState defender=primary(state.getTeamState(defending),lane);
            boolean defenderAbsent=!defender.isAlive(time)||defender.getActivityState().getActivityType()!=PlayerActivityType.DEFAULT_ROLE||state.wasMajorCombatParticipantThisTick(defender);
            boolean supportPresent=lane==Lane.BOT&&supportPresent(state.getTeamState(attacking),time);
            double before=target.getOuterRemainingIntegrity();
            double pressureDamage=Math.max(0,Math.abs(pressure)-LanePhaseRuleConfig.MIN_SIEGE_PRESSURE)*LanePhaseRuleConfig.PRESSURE_DAMAGE_PER_POINT_OVER_THRESHOLD;
            double absentBonus=defenderAbsent?LanePhaseRuleConfig.DEFENDER_PRIMARY_ABSENT_DAMAGE_BONUS:0;
            double supportBonus=supportPresent?LanePhaseRuleConfig.BOT_SUPPORT_PRESENT_DAMAGE_BONUS:0;
            state.getCompositionRuntimeState().recordActualAttempt(
                    CompositionActionType.SIEGE, attacking, attacking, defending, FightScale.NONE,
                    null, false, com.lolfm.simulator.StructureKind.TOWER, lane, time,
                    CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null);
            state.markStructureActionAttempted(attacking);
            double variance=(random.nextDouble()*2-1)*LanePhaseRuleConfig.OUTER_SIEGE_RANDOM_VARIANCE;
            double damage=clamp(LanePhaseRuleConfig.BASE_OUTER_SIEGE_DAMAGE+pressureDamage+absentBonus+supportBonus+variance,LanePhaseRuleConfig.MIN_OUTER_SIEGE_DAMAGE,LanePhaseRuleConfig.MAX_OUTER_SIEGE_DAMAGE);
            double after=target.applyOuterDamage(damage);
            boolean destroyedNow=after<=0;
            OuterTurretSiegeData data=new OuterTurretSiegeData(time,lane,attacking,defending,pressure,before,pressureDamage,absentBonus,supportBonus,variance,damage,after,destroyedNow);
            stats.recordSiege(data);
            if(destroyedNow)structures.destroyOuterFromLanePressure(state,attacking,lane,data).ifPresent(destroyed::add);
        }
        return List.copyOf(destroyed);
    }
    public Optional<MatchEvent> transitionIfDue(GameState state){
        Optional<MatchPhaseChangeData> transition=state.getLanePhaseState().transitionIfDue(state.getCurrentTimeSeconds());
        return transition.map(data->{MatchEvent event=new MatchEvent(data.transitionTimeSeconds(),MatchEventType.MATCH_PHASE_CHANGE,phaseMessage(data),null,null,List.of());event.setMatchPhaseChange(data);return event;});
    }
    public Optional<MatchEvent> transitionToLateGameIfDue(GameState state){
        Optional<MatchPhaseChangeData> transition=state.getLanePhaseState().transitionToLateGameIfDue(state);
        return transition.map(data->{MatchEvent event=new MatchEvent(data.transitionTimeSeconds(),MatchEventType.MATCH_PHASE_CHANGE,phaseMessage(data),null,null,List.of());event.setMatchPhaseChange(data);return event;});
    }
    private PlayerState primary(TeamState team,Lane lane){return team.playerAt(switch(lane){case TOP->Position.TOP;case MID->Position.MID;case BOT->Position.ADC;});}
    private boolean supportPresent(TeamState team,int time){PlayerState support=team.playerAt(Position.SUPPORT);return support.isAlive(time)&&support.getActivityState().getActivityType()==PlayerActivityType.DEFAULT_ROLE;}
    private String phaseMessage(MatchPhaseChangeData data){return switch(data.newPhase()){case MID_GAME->"라인전이 종료되고 미드 게임 단계로 전환됩니다.";case LATE_GAME->"경기가 후반 운영 단계로 전환됩니다.";case LANING->"라인전 단계가 시작됩니다.";};}
    private double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
}
