package com.lolfm.simulator;

import com.lolfm.domain.*;
import java.util.*;

/** Upper-pit decisions reuse objective contest/secure and the common structure reward owner. */
public final class UpperObjectiveResolver {
    public static final Set<Position> LOCAL_POSITIONS = Set.of(Position.TOP, Position.JUNGLE, Position.MID);
    public Optional<MatchEvent> attempt(GameState state, Random random, ObjectiveResolver objectives,
            StructureResolver structures, List<MatchEvent> events) {
        if (!state.isRealismEnabled() || state.isFinished() || state.wasMajorCombatAttemptedThisTick()) return Optional.empty();
        int time=state.getCurrentTimeSeconds();
        UpperObjectiveState upper=state.getObjectiveState().upper();
        ObjectiveType type=upper.available(ObjectiveType.VOID_GRUB,time) ? ObjectiveType.VOID_GRUB : ObjectiveType.RIFT_HERALD;
        if (!upper.due(type,time)) return Optional.empty();
        var blue=participants(state,TeamSide.BLUE); var red=participants(state,TeamSide.RED);
        if (blue.isEmpty() && red.isEmpty()) return Optional.empty();
        double top=state.laneState(Lane.TOP).getPressure()*UpperObjectiveRuleConfig.TOP_PRIORITY_WEIGHT
                + state.laneState(Lane.MID).getPressure()*UpperObjectiveRuleConfig.MID_PRIORITY_WEIGHT;
        // Dragon soul point and strong opposite-side lane control can justify conceding the upper pit.
        if (state.getObjectiveState().isDragonAlive()
                && (state.getBlueTeamState().getDragons()==3 || state.getRedTeamState().getDragons()==3
                    || Math.abs(state.laneState(Lane.BOT).getPressure()) > Math.abs(top))) return Optional.empty();
        if (!upper.beginEvaluation(time)) return Optional.empty();
        if (random.nextDouble() >= UpperObjectiveRuleConfig.ATTEMPT_CHANCE) return Optional.empty();
        double bw=blue.size()*(1+top/200), rw=red.size()*(1-top/200);
        TeamSide initiative=red.isEmpty()?TeamSide.BLUE:blue.isEmpty()?TeamSide.RED
                :random.nextDouble()<bw/(bw+rw)?TeamSide.BLUE:TeamSide.RED;
        upper.attempted(time);
        var zero=ObjectiveSelectionWeightBreakdown.zero();
        var decision=new ObjectivePriorityDecisionData(type,time,true,true,false,true,top,0,top,
                50+top/2,50-top/2,UpperObjectiveRuleConfig.ATTEMPT_CHANCE,0,
                UpperObjectiveRuleConfig.ATTEMPT_CHANCE,true,true,!blue.isEmpty(),!red.isEmpty(),
                zero,zero,1,1,bw,rw,!blue.isEmpty()&&!red.isEmpty(),initiative);
        state.getObjectivePriorityExecutionStats().recordDecision(decision);
        return new ObjectiveDecisionResolver().resolve(state,type,initiative,top,random,objectives,structures,events,decision);
    }
    public static List<PlayerState> participants(GameState state, TeamSide side) {
        return state.getTeamState(side).getPlayers().stream().filter(p -> LOCAL_POSITIONS.contains(p.getPosition())
                && p.canParticipateInMajorCombatAt(state.getCurrentTimeSeconds())).toList();
    }
    public Optional<MatchEvent> capture(GameState state, ObjectiveType type, TeamSide side) {
        if (!state.isRealismEnabled() || state.isFinished()) return Optional.empty();
        List<PlayerState> local=participants(state,side);
        if(local.isEmpty())return Optional.empty();
        PlayerState killer=local.stream().filter(p->p.getPosition()==Position.JUNGLE).findFirst().orElse(local.getFirst());
        int time=state.getCurrentTimeSeconds(); var upper=state.getObjectiveState().upper();
        String id=upper.capture(type,side,killer.getPosition(),time);
        if(id==null)return Optional.empty();
        int gold=type==ObjectiveType.VOID_GRUB?UpperObjectiveRuleConfig.GRUB_GOLD:UpperObjectiveRuleConfig.HERALD_GOLD;
        int xp=type==ObjectiveType.VOID_GRUB?UpperObjectiveRuleConfig.GRUB_XP:UpperObjectiveRuleConfig.HERALD_XP;
        new GoldAwardService().awardGold(state.getTeamState(side),killer,gold,GoldSource.OBJECTIVE,false,time);
        for(int i=0;i<local.size();i++) {
            PlayerState player=local.get(i);
            new ProgressionRewardResolver().awardExperience(player,ExperienceSource.EPIC_MONSTER,
                    xp/local.size()+(i<xp%local.size()?1:0),time);
            player.blockFarmUntil(time+UpperObjectiveRuleConfig.CAPTURE_FARM_COST);
            player.getActivityState().beginUpperObjectiveReturn(id, time,
                    time+UpperObjectiveRuleConfig.CAPTURE_FARM_COST);
        }
        MatchEvent event=new MatchEvent(time,type==ObjectiveType.VOID_GRUB?MatchEventType.VOID_GRUB:MatchEventType.RIFT_HERALD,
                type==ObjectiveType.VOID_GRUB?"공허 유충 한 마리를 확보했습니다.":"협곡의 전령과 전령의 눈을 확보했습니다.",null,null,List.of());
        event.setActionId(id);event.setActorPlayerId(killer.getStructuredPlayerId());
        event.setUpperObjective(new UpperObjectiveData(type,id,"CAPTURED",side,killer.getStructuredPlayerId(),
                local.stream().map(PlayerState::getStructuredPlayerId).toList(),gold,xp,upper.grubs(side),upper.expiresAt(),Lane.TOP,null,0));
        return Optional.of(event);
    }
    public void lifecycle(GameState state, StructureResolver structures, List<MatchEvent> events) {
        if(!state.isRealismEnabled() || state.isFinished())return;
        var u=state.getObjectiveState().upper(); int time=state.getCurrentTimeSeconds();
        if(!u.beginLifecycle(time) || u.heraldOwner()==null)return;
        if(u.heraldPhase()!=UpperObjectiveState.HeraldPhase.HELD && u.heraldPhase()!=UpperObjectiveState.HeraldPhase.SUMMONED)return;
        if(time>=u.expiresAt()) {u.expire();events.add(lifecycleEvent(state,"EXPIRED",null,0));return;}
        if(u.heraldPhase()==UpperObjectiveState.HeraldPhase.HELD) {
            PlayerState holder=state.getTeamState(u.heraldOwner()).playerAt(u.holder());
            if(time<u.acquiredAt()+UpperObjectiveRuleConfig.SUMMON_DELAY || !holder.canParticipateInMajorCombatAt(time))return;
            Lane best=null;double score=-Double.MAX_VALUE;
            for(Lane lane:Lane.values()) {
                var target=tower(state,u.heraldOwner(),lane);
                if(target==null)continue;
                double pressure=state.laneState(lane).getPressure()*(u.heraldOwner()==TeamSide.BLUE?1:-1);
                if(pressure<0 && time+UpperObjectiveRuleConfig.SUMMON_DELAY<u.expiresAt())continue;
                if(pressure>score){score=pressure;best=lane;}
            }
            if(best==null)return;
            u.summon(best,time);holder.blockFarmUntil(time+UpperObjectiveRuleConfig.CHARGE_DELAY);
            events.add(lifecycleEvent(state,"SUMMONED",null,0));return;
        }
        if(time<u.chargeAt())return;
        var target=tower(state,u.heraldOwner(),u.summonLane());
        if(target==null){u.destroy();events.add(lifecycleEvent(state,"NO_VALID_TOWER",null,0));return;}
        // The mercenary is a separate unit after summon; the holder need not survive or hit the tower.
        var request=StructureAttackRequest.fixed(u.heraldOwner(),u.summonLane(),target.planningTarget(),
                PushReason.MACRO_PLAY,Set.of(),chargeDamage(u),StructureActionSource.RIFT_HERALD,
                "RIFT_HERALD:1:1:CHARGE:"+u.charges());
        var hit=structures.attemptSiege(state,request);
        if(hit.isPresent()) {
            structures.addAttackEvents(state,hit.get(),events);
            events.add(lifecycleEvent(state,"CHARGED",target.stableId(),hit.get().damage()));
            u.charged(time);
            // Surviving local defenders can kill the mercenary after its first committed charge.
            long defenders=state.getTeamState(u.heraldOwner().opposite()).getPlayers().stream()
                    .filter(p->p.canParticipateInMajorCombatAt(time) && new LaneResourceResolver().lane(p)==u.summonLane()).count();
            if(defenders>=2)u.destroy();
        }
    }
    public static double chargeDamage(UpperObjectiveState u) {
        return UpperObjectiveRuleConfig.HERALD_FIRST_CHARGE_DAMAGE*Math.pow(UpperObjectiveRuleConfig.LATER_CHARGE_MULTIPLIER,u.charges());
    }
    static StructureTargetId tower(GameState s,TeamSide owner,Lane lane) {
        var structures=s.getMapState().getLaneState(owner.opposite(),lane);
        for(TowerTier tier:TowerTier.values())if(structures.canDestroy(tier))return StructureTargetId.tower(owner.opposite(),lane,tier);
        return null;
    }
    private MatchEvent lifecycleEvent(GameState state,String phase,String target,double damage) {
        var u=state.getObjectiveState().upper();
        MatchEventType type=phase.equals("SUMMONED")?MatchEventType.HERALD_SUMMON:phase.equals("CHARGED")?MatchEventType.HERALD_CHARGE:MatchEventType.HERALD_EXPIRED;
        MatchEvent e=new MatchEvent(state.getCurrentTimeSeconds(),type,"전령: "+phase,null,null,List.of());
        e.setActionId("RIFT_HERALD:1:1:"+phase+":"+u.charges());
        e.setUpperObjective(new UpperObjectiveData(ObjectiveType.RIFT_HERALD,"RIFT_HERALD:1:1",phase,
                u.heraldOwner(),state.getTeamState(u.heraldOwner()).playerAt(u.holder()).getStructuredPlayerId(),List.of(),
                0,0,u.grubs(u.heraldOwner()),u.expiresAt(),u.summonLane(),target,damage));
        return e;
    }
}
