package com.lolfm.simulator;

import com.lolfm.domain.OuterTurretSiegeData;
import com.lolfm.domain.Position;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OuterTurretSiegeTest {
    private final LanePhaseResolver resolver=new LanePhaseResolver();
    private final StructureResolver structures=new StructureResolver();

    @Test void evaluationBoundariesAre299No300Yes840Yes841No(){
        assertEquals(0,resolveAt(299,20).getLanePhaseExecutionStats().snapshot().actualSieges());
        assertEquals(1,resolveAt(300,20).getLanePhaseExecutionStats().snapshot().actualSieges());
        assertEquals(0,resolveAt(310,20).getLanePhaseExecutionStats().snapshot().actualSieges());
        assertEquals(1,resolveAt(840,20).getLanePhaseExecutionStats().snapshot().actualSieges());
        assertEquals(0,resolveAt(841,20).getLanePhaseExecutionStats().snapshot().actualSieges());
    }
    @Test void duplicateTimeIsIdempotentAndConsumesNoAdditionalRandom(){
        GameState s=LanePhaseTestSupport.state();s.advanceTimeSeconds(300);s.laneState(Lane.TOP).setPressure(20);
        var r=new LanePhaseTestSupport.CountingRandom(.5);
        resolver.resolveOuterSieges(s,300,r,structures);double integrity=s.getMapState().getLaneState(TeamSide.RED,Lane.TOP).getOuterRemainingIntegrity();
        resolver.resolveOuterSieges(s,300,r,structures);
        assertEquals(1,r.doubles);assertEquals(integrity,s.getMapState().getLaneState(TeamSide.RED,Lane.TOP).getOuterRemainingIntegrity());
    }
    @Test void pastEvaluationTimeFollowsExceptionPolicy(){
        GameState s=LanePhaseTestSupport.state();s.advanceTimeSeconds(330);s.laneState(Lane.TOP).setPressure(20);
        resolver.resolveOuterSieges(s,330,new LanePhaseTestSupport.CountingRandom(.5),structures);
        assertThrows(IllegalArgumentException.class,()->resolver.resolveOuterSieges(s,300,new LanePhaseTestSupport.CountingRandom(.5),structures));
    }
    @Test void positiveAndNegativePressureAttackOppositeOuterSymmetrically(){
        GameState blue=resolveAt(300,20),red=resolveAt(300,-20);
        assertEquals(93,blue.getMapState().getLaneState(TeamSide.RED,Lane.TOP).getOuterRemainingIntegrity(),1e-9);
        assertEquals(93,red.getMapState().getLaneState(TeamSide.BLUE,Lane.TOP).getOuterRemainingIntegrity(),1e-9);
    }
    @Test void belowThresholdConsumesNoRandomAndExactThresholdIsEligible(){
        GameState s=LanePhaseTestSupport.state();s.advanceTimeSeconds(300);s.laneState(Lane.TOP).setPressure(19.999);
        var r=new LanePhaseTestSupport.CountingRandom(.5);resolver.resolveOuterSieges(s,300,r,structures);
        assertEquals(0,r.doubles);assertEquals(100,s.getMapState().getLaneState(TeamSide.RED,Lane.TOP).getOuterRemainingIntegrity());
    }
    @Test void deadOrRoamingPrimaryBlocksWithoutRandomButFarmBlockAloneDoesNot(){
        GameState dead=LanePhaseTestSupport.state();dead.advanceTimeSeconds(300);dead.laneState(Lane.TOP).setPressure(30);
        dead.getBlueTeamState().playerAt(Position.TOP).markDead(300,100);
        var r1=new LanePhaseTestSupport.CountingRandom(.5);resolver.resolveOuterSieges(dead,300,r1,structures);assertEquals(0,r1.doubles);
        GameState roaming=LanePhaseTestSupport.state();roaming.advanceTimeSeconds(300);roaming.laneState(Lane.TOP).setPressure(30);
        roaming.getBlueTeamState().playerAt(Position.TOP).beginRoamActivity(Lane.TOP,Lane.MID,300);
        var r2=new LanePhaseTestSupport.CountingRandom(.5);resolver.resolveOuterSieges(roaming,300,r2,structures);assertEquals(0,r2.doubles);
        GameState blocked=LanePhaseTestSupport.state();blocked.advanceTimeSeconds(300);blocked.laneState(Lane.TOP).setPressure(30);
        blocked.getBlueTeamState().playerAt(Position.TOP).markDead(280,10);
        assertFalse(blocked.getBlueTeamState().playerAt(Position.TOP).canFarmAt(300));
        var r3=new LanePhaseTestSupport.CountingRandom(.5);resolver.resolveOuterSieges(blocked,300,r3,structures);assertEquals(1,r3.doubles);
    }
    @Test void botAdcIsRequiredWhileSupportOnlyAddsBonus(){
        GameState normal=LanePhaseTestSupport.state();normal.advanceTimeSeconds(300);normal.laneState(Lane.BOT).setPressure(20);
        resolver.resolveOuterSieges(normal,300,new LanePhaseTestSupport.CountingRandom(.5),structures);
        OuterTurretSiegeData withSupport=normal.getLanePhaseExecutionStats().snapshot().sieges().getFirst();
        assertEquals(1.5,withSupport.botSupportBonus());
        GameState absent=LanePhaseTestSupport.state();absent.advanceTimeSeconds(300);absent.laneState(Lane.BOT).setPressure(20);
        absent.getBlueTeamState().playerAt(Position.SUPPORT).beginRoamActivity(Lane.BOT,Lane.MID,300);
        resolver.resolveOuterSieges(absent,300,new LanePhaseTestSupport.CountingRandom(.5),structures);
        assertEquals(0,absent.getLanePhaseExecutionStats().snapshot().sieges().getFirst().botSupportBonus());
        GameState noAdc=LanePhaseTestSupport.state();noAdc.advanceTimeSeconds(300);noAdc.laneState(Lane.BOT).setPressure(20);
        noAdc.getBlueTeamState().playerAt(Position.ADC).markDead(300,100);
        var r=new LanePhaseTestSupport.CountingRandom(.5);resolver.resolveOuterSieges(noAdc,300,r,structures);assertEquals(0,r.doubles);
    }
    @Test void defenderAbsenceAndPressureComponentsUseSpecifiedFormula(){
        GameState s=LanePhaseTestSupport.state();s.advanceTimeSeconds(300);s.laneState(Lane.MID).setPressure(50);
        s.getRedTeamState().playerAt(Position.MID).beginRoamActivity(Lane.MID,Lane.TOP,300);
        resolver.resolveOuterSieges(s,300,new LanePhaseTestSupport.CountingRandom(.5),structures);
        OuterTurretSiegeData d=s.getLanePhaseExecutionStats().snapshot().sieges().getFirst();
        assertEquals(3.6,d.pressureDamage(),1e-9);assertEquals(4,d.defenderAbsentBonus(),1e-9);assertEquals(14.6,d.finalDamage(),1e-9);
    }
    @Test void fixedLaneOrderAndOneRandomPerEligibleSiege(){
        GameState s=LanePhaseTestSupport.state();s.advanceTimeSeconds(300);for(Lane lane:Lane.values())s.laneState(lane).setPressure(20);
        var r=new LanePhaseTestSupport.CountingRandom(.5);resolver.resolveOuterSieges(s,300,r,structures);
        assertEquals(3,r.doubles);assertEquals(List.of(Lane.TOP,Lane.MID,Lane.BOT),s.getLanePhaseExecutionStats().snapshot().sieges().stream().map(OuterTurretSiegeData::lane).toList());
    }
    @Test void destructionUsesCommonRewardEventAndDuplicateProtection(){
        GameState s=LanePhaseTestSupport.state();s.advanceTimeSeconds(300);s.laneState(Lane.TOP).setPressure(20);
        LaneStructureState tower=s.getMapState().getLaneState(TeamSide.RED,Lane.TOP);tower.applyOuterDamage(99);
        int gold=s.getBlueTeamState().getGold();
        var outcomes=resolver.resolveOuterSieges(s,300,new LanePhaseTestSupport.CountingRandom(.5),structures);
        assertEquals(1,outcomes.size());assertEquals(StructureActionSource.LANE_PRESSURE,outcomes.getFirst().source());
        assertFalse(tower.isOuterTowerAlive());assertEquals(0,tower.getOuterRemainingIntegrity());assertEquals(1,s.getBlueTeamState().getTowersDestroyed());
        assertEquals(gold+625,s.getBlueTeamState().getGold());assertEquals(LanePhase.OPEN,s.getLanePhaseState().getLanePhase(Lane.TOP));
        assertNotNull(structures.createStructureEvent(s,outcomes.getFirst()).getOuterTurretSiege());
        resolver.resolveOuterSieges(s,300,new LanePhaseTestSupport.CountingRandom(.5),structures);
        assertEquals(1,s.getBlueTeamState().getTowersDestroyed());
    }
    @Test void featureOffAndOpenLaneAreCompleteNoOps(){
        GameState off=LanePhaseTestSupport.state(false);off.advanceTimeSeconds(300);off.laneState(Lane.TOP).setPressure(100);
        var r1=new LanePhaseTestSupport.CountingRandom(.5);resolver.resolveOuterSieges(off,300,r1,structures);assertEquals(0,r1.doubles);
        GameState open=LanePhaseTestSupport.state();open.advanceTimeSeconds(300);open.getLanePhaseState().openLane(Lane.TOP,290);open.laneState(Lane.TOP).setPressure(100);
        var r2=new LanePhaseTestSupport.CountingRandom(.5);resolver.resolveOuterSieges(open,300,r2,structures);assertEquals(0,r2.doubles);
    }
    private GameState resolveAt(int time,double pressure){
        GameState s=LanePhaseTestSupport.state();s.advanceTimeSeconds(time);s.laneState(Lane.TOP).setPressure(pressure);
        resolver.resolveOuterSieges(s,time,new LanePhaseTestSupport.CountingRandom(.5),structures);return s;
    }
}
