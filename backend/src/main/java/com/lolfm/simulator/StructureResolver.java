package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StructureResolver {
    private static final int TOWER_GOLD_PER_PLAYER = 125;

    public Optional<StructureOutcome> destroyNextStructure(GameState state, TeamSide attackingSide, Lane lane, PushReason reason) {
        if (state.isFinished()) return Optional.empty();
        TeamSide defendingSide=attackingSide.opposite();
        LaneStructureState laneState=state.getMapState().getLaneState(defendingSide,lane);
        Optional<TowerTier> tower=laneState.nextAliveTower();
        if(tower.isPresent()) {
            laneState.destroy(tower.get());
            TeamState attackers=state.getTeamState(attackingSide);
            attackers.addTowerDestroyed(); awardTowerGold(attackers);
            return Optional.of(new StructureOutcome(attackingSide,defendingSide,StructureKind.TOWER,lane,tower.get(),state.getCurrentTimeSeconds(),reason,false));
        }
        if(laneState.destroyInhibitor(state.getCurrentTimeSeconds())) {
            state.getMapState().activateBasePressure(attackingSide, state.getCurrentTimeSeconds());
            return Optional.of(new StructureOutcome(attackingSide,defendingSide,StructureKind.INHIBITOR,lane,null,state.getCurrentTimeSeconds(),reason,false));
        }
        BaseState base=state.getMapState().getBaseState(defendingSide);
        if(state.getMapState().areNexusTurretsVulnerable(defendingSide) && base.destroyOneNexusTurret()) {
            return Optional.of(new StructureOutcome(attackingSide,defendingSide,StructureKind.NEXUS_TURRET,null,null,state.getCurrentTimeSeconds(),reason,false));
        }
        if(state.getMapState().isNexusVulnerable(defendingSide) && base.destroyNexus(state.getCurrentTimeSeconds())) {
            state.finish(attackingSide, GameEndReason.NEXUS_DESTROYED);
            return Optional.of(new StructureOutcome(attackingSide,defendingSide,StructureKind.NEXUS,null,null,state.getCurrentTimeSeconds(),reason,true));
        }
        return Optional.empty();
    }

    public MatchEvent createStructureEvent(GameState state, StructureOutcome outcome) {
        String team=state.getTeamState(outcome.attackingSide()).getTeamName();
        return new MatchEvent(outcome.occurredAtSeconds(), MatchEventType.TOWER, message(team,outcome,state), null,null,List.of());
    }
    private void awardTowerGold(TeamState team){for(PlayerState p:team.getPlayers())p.addGold(TOWER_GOLD_PER_PLAYER);team.addGold(TOWER_GOLD_PER_PLAYER*team.getPlayers().size());}
    private String message(String team, StructureOutcome o, GameState state) {
        if(o.structureKind()==StructureKind.NEXUS) return team+"가 적 넥서스를 파괴합니다.";
        if(o.structureKind()==StructureKind.NEXUS_TURRET) {
            int remaining=state.getMapState().getBaseState(o.defendingSide()).getNexusTurretsRemaining();
            return team+(o.reason()==PushReason.BARON_PRESSURE?"가 바론 버프를 앞세워 ":"가 적 본진에 진입해 ")+"넥서스 포탑을 파괴합니다. 남은 포탑: "+remaining;
        }
        String lane=switch(o.lane()){case TOP->"탑";case MID->"미드";case BOT->"바텀";};
        if(o.structureKind()==StructureKind.INHIBITOR) return o.reason()==PushReason.POST_FIGHT?team+"가 한타 승리를 바탕으로 "+lane+" 억제기까지 무너뜨립니다.":team+"가 "+lane+" 억제기를 파괴합니다.";
        String tier=switch(o.towerTier()){case OUTER->"외곽 포탑";case INNER->"내부 포탑";case INHIBITOR->"억제기 포탑";};
        return switch(o.reason()){case BARON_PRESSURE->team+"가 바론 버프를 앞세워 "+lane+" "+tier+"을 무너뜨립니다.";case POST_FIGHT->team+"가 한타 승리 이후 "+lane+" "+tier+"까지 진격합니다.";case MACRO_PLAY->team+"가 운영 압박으로 "+lane+" "+tier+"을 파괴합니다.";};
    }
}
