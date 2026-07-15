package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Position;
import com.lolfm.factory.DummyDataFactory;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanePhaseIntegrationTest {
    @Test void openPressureDecaysTowardZeroWithoutRandomOrSignReversal(){
        GameState s=LanePhaseTestSupport.state();s.getLanePhaseState().openLane(Lane.TOP,0);s.laneState(Lane.TOP).setPressure(40);
        var r=new LanePhaseTestSupport.CountingRandom(.5);new LanePressureResolver().resolve(s,30,r);
        assertEquals(26,s.laneState(Lane.TOP).getPressure(),1e-9);assertTrue(s.laneState(Lane.TOP).getPressure()>0);assertEquals(2,r.doubles);
        GameState red=LanePhaseTestSupport.state();red.getLanePhaseState().openLane(Lane.TOP,0);red.laneState(Lane.TOP).setPressure(-40);
        new LanePressureResolver().resolve(red,30,new LanePhaseTestSupport.CountingRandom(.5));assertEquals(-26,red.laneState(Lane.TOP).getPressure(),1e-9);
    }
    @Test void openLaneRemovesOnlyPressureFarmModifierAndKeepsBaseFarm(){
        GameState s=LanePhaseTestSupport.state();s.laneState(Lane.TOP).setPressure(100);
        PositionEconomyResolver e=new PositionEconomyResolver();assertTrue(e.laneCsMultiplier(Position.TOP,s,TeamSide.BLUE)>1);
        s.getLanePhaseState().openLane(Lane.TOP,300);assertEquals(1,e.laneCsMultiplier(Position.TOP,s,TeamSide.BLUE),1e-9);
        int before=s.getBlueTeamState().playerAt(Position.TOP).getCs();
        e.resolve(s,s.getBlueTeamState(),TeamSide.BLUE,310,10,new LanePhaseTestSupport.CountingRandom(0));
        assertTrue(s.getBlueTeamState().playerAt(Position.TOP).getCs()>before);assertEquals(0,s.getBlueTeamState().playerAt(Position.SUPPORT).getCs());
    }
    @Test void openLaneIsExcludedFromLaneCombatAndAllOpenFallsThrough(){
        GameState s=LanePhaseTestSupport.state();s.getLanePhaseState().openLane(Lane.TOP,300);
        assertFalse(new LaneCombatResolver().eligible(s,Lane.TOP,300));
        assertTrue(new LaneCombatResolver().eligible(s,Lane.MID,300));
        s.getLanePhaseState().openLane(Lane.MID,300);s.getLanePhaseState().openLane(Lane.BOT,300);
        var r=new LanePhaseTestSupport.CountingRandom(0);assertFalse(new LaneCombatResolver().resolve(s,r,new java.util.ArrayList<>()));
        assertEquals(0,r.doubles);
    }
    @Test void openLaneIsExcludedFromGankAndCounterTargeting(){
        GameState s=LanePhaseTestSupport.state();s.getLanePhaseState().openLane(Lane.TOP,300);
        JungleGankResolver g=new JungleGankResolver();
        assertFalse(g.laneEligible(s,Lane.TOP,300));assertTrue(g.laneEligible(s,Lane.MID,300));
        s.getLanePhaseState().openLane(Lane.MID,300);s.getLanePhaseState().openLane(Lane.BOT,300);
        var r=new LanePhaseTestSupport.CountingRandom(0);assertFalse(g.resolve(s,r,new java.util.ArrayList<>()));assertEquals(0,r.doubles);
    }
    @Test void openRoamOriginAndTargetAreExcludedWithoutTriggerRandom(){
        GameState origin=LanePhaseTestSupport.state();origin.getLanePhaseState().openLane(Lane.MID,300);
        RoamResolver roam=new RoamResolver();assertFalse(roam.eligible(origin,new RoamResolver.Candidate(TeamSide.BLUE,Position.MID,0),300));
        GameState target=LanePhaseTestSupport.state();target.getLanePhaseState().openLane(Lane.TOP,300);target.getLanePhaseState().openLane(Lane.BOT,300);
        var r=new LanePhaseTestSupport.CountingRandom(0);assertFalse(roam.resolve(target,r,new java.util.ArrayList<>()));assertEquals(0,r.doubles);
    }
    @Test void pushOuterDestructionOpensLaneButLeavesOppositeOuterAliveAndPreservesOrder(){
        GameState s=LanePhaseTestSupport.state();s.advanceTimeSeconds(500);StructureResolver structures=new StructureResolver();
        StructureOutcome outer=structures.destroyNextStructure(s,TeamSide.BLUE,Lane.TOP,PushReason.MACRO_PLAY).orElseThrow();
        assertEquals(TowerTier.OUTER,outer.towerTier());assertEquals(StructureActionSource.MACRO_PLAY,outer.source());
        assertEquals(LanePhase.OPEN,s.getLanePhaseState().getLanePhase(Lane.TOP));assertTrue(s.getMapState().getLaneState(TeamSide.BLUE,Lane.TOP).isOuterTowerAlive());
        assertEquals(TowerTier.INNER,structures.destroyNextStructure(s,TeamSide.BLUE,Lane.TOP,PushReason.MACRO_PLAY).orElseThrow().towerTier());
    }
    @Test void openPressureDecayFeedsObjectivePriorityWithoutMutatingPastSnapshot(){
        GameState s=LanePhaseTestSupport.state();s.laneState(Lane.MID).setPressure(60);s.laneState(Lane.BOT).setPressure(60);
        var factory=new com.lolfm.simulator.SnapshotFactory();var before=factory.create(s);
        s.getLanePhaseState().openLane(Lane.MID,0);new LanePressureResolver().resolve(s,30,new LanePhaseTestSupport.CountingRandom(.5));
        var after=factory.create(s);
        assertEquals(60,before.getLaneSnapshots().stream().filter(x->x.lane()==Lane.MID).findFirst().orElseThrow().pressure(),1e-9);
        assertTrue(after.getObjectivePriority().dragonLanePressureScore()<before.getObjectivePriority().dragonLanePressureScore());
    }
    @Test void offKeepsLegacyPressureFarmAndCombatEligibility(){
        GameState s=LanePhaseTestSupport.state(false);s.laneState(Lane.TOP).setPressure(100);
        assertTrue(new PositionEconomyResolver().laneCsMultiplier(Position.TOP,s,TeamSide.BLUE)>1);
        assertTrue(new LaneCombatResolver().eligible(s,Lane.TOP,300));assertTrue(new JungleGankResolver().laneEligible(s,Lane.TOP,300));
    }
    @Test void identicalSeedProducesIdenticalSiegeDamageAndState(){
        GameState a=LanePhaseTestSupport.state(),b=LanePhaseTestSupport.state();a.advanceTimeSeconds(300);b.advanceTimeSeconds(300);a.laneState(Lane.TOP).setPressure(55);b.laneState(Lane.TOP).setPressure(55);
        new LanePhaseResolver().resolveOuterSieges(a,300,new Random(77),new StructureResolver());
        new LanePhaseResolver().resolveOuterSieges(b,300,new Random(77),new StructureResolver());
        assertEquals(a.getMapState().getLaneState(TeamSide.RED,Lane.TOP).getOuterRemainingIntegrity(),b.getMapState().getLaneState(TeamSide.RED,Lane.TOP).getOuterRemainingIntegrity());
    }

    @Test void identicalSeedProducesIdenticalCompleteTimelineAndDiagnosticsStayObservational() throws Exception {
        var factory=new DummyDataFactory();
        MatchTimeline first=simulator(SimulationOptions.productionDefaults().withDiagnosticsEnabled(true))
                .simulate(factory.createBlueTeam(),factory.createRedTeam(),8181L);
        MatchTimeline replay=simulator(SimulationOptions.productionDefaults().withDiagnosticsEnabled(true))
                .simulate(factory.createBlueTeam(),factory.createRedTeam(),8181L);
        MatchTimeline diagnosticsOff=simulator(SimulationOptions.productionDefaults().withDiagnosticsEnabled(false))
                .simulate(factory.createBlueTeam(),factory.createRedTeam(),8181L);
        ObjectMapper json=new ObjectMapper();
        assertEquals(json.writeValueAsString(first),json.writeValueAsString(replay));
        assertEquals(json.writeValueAsString(first),json.writeValueAsString(diagnosticsOff));
    }

    private MatchSimulator simulator(SimulationOptions options){
        return new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(),
                new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),
                new StructureResolver(),new PushResolver(),options);
    }

}
