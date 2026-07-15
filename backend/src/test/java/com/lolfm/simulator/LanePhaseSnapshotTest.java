package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.LanePhaseLaneSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanePhaseSnapshotTest {
    private final SnapshotFactory factory=new SnapshotFactory();
    @Test void snapshotContainsPhasePressureAndBothOuterTurrets(){
        GameState s=LanePhaseTestSupport.state();var phase=factory.create(s).getLanePhase();
        assertTrue(phase.enabled());assertEquals(MatchPhase.LANING,phase.matchPhase());assertEquals(3,phase.lanes().size());
        for(LanePhaseLaneSnapshot lane:phase.lanes()){
            assertEquals(100,lane.blueOuter().remainingIntegrity());assertEquals(100,lane.redOuter().remainingIntegrity());
            assertTrue(lane.pressureFarmModifierActive());
        }
    }
    @Test void pastSnapshotIsImmutableAfterDamageAndDestruction(){
        GameState s=LanePhaseTestSupport.state();var before=factory.create(s);
        s.advanceTimeSeconds(300);s.laneState(Lane.TOP).setPressure(100);s.getMapState().getLaneState(TeamSide.RED,Lane.TOP).applyOuterDamage(99);
        new LanePhaseResolver().resolveOuterSieges(s,300,new LanePhaseTestSupport.CountingRandom(.5),new StructureResolver());
        var old=before.getLanePhase().lanes().getFirst().redOuter();assertTrue(old.alive());assertEquals(100,old.remainingIntegrity());
        var now=factory.create(s).getLanePhase().lanes().getFirst().redOuter();assertFalse(now.alive());assertEquals(0,now.remainingIntegrity());
    }
    @Test void snapshotCreationConsumesNoRandomAndDoesNotMutateState(){
        GameState s=LanePhaseTestSupport.state();int last=s.getLanePhaseState().getLastOuterSiegeEvaluationAtSeconds();factory.create(s);factory.create(s);assertEquals(last,s.getLanePhaseState().getLastOuterSiegeEvaluationAtSeconds());
    }
    @Test void offSnapshotIsNeutralPhaseButShowsLegacyStructureTruth(){
        GameState s=LanePhaseTestSupport.state(false);s.advanceTimeSeconds(500);new StructureResolver().destroyNextStructure(s,TeamSide.BLUE,Lane.TOP,PushReason.MACRO_PLAY);
        var phase=factory.create(s).getLanePhase();assertFalse(phase.enabled());assertEquals(MatchPhase.LANING,phase.matchPhase());assertEquals(LanePhase.LANING,phase.lanes().getFirst().phase());assertFalse(phase.lanes().getFirst().redOuter().alive());
    }
    @Test void jsonApiShapeIsAdditiveAndKeepsObjectivePriority()throws Exception{
        String json=new ObjectMapper().writeValueAsString(factory.create(LanePhaseTestSupport.state()));
        assertTrue(json.contains("\"objectivePriority\""));assertTrue(json.contains("\"lanePhase\""));assertTrue(json.contains("\"blueOuter\""));assertTrue(json.contains("\"redOuter\""));
    }
    @Test void phaseChangeAndStructureEventsExposeStructuredData(){
        GameState s=LanePhaseTestSupport.state();s.advanceTimeSeconds(840);var phaseEvent=new LanePhaseResolver().transitionIfDue(s).orElseThrow();
        assertEquals(com.lolfm.domain.MatchEventType.MATCH_PHASE_CHANGE,phaseEvent.getType());assertNotNull(phaseEvent.getMatchPhaseChange());
        GameState tower=LanePhaseTestSupport.state();tower.advanceTimeSeconds(500);StructureResolver structures=new StructureResolver();var outcome=structures.destroyNextStructure(tower,TeamSide.BLUE,Lane.TOP,PushReason.MACRO_PLAY).orElseThrow();
        var event=structures.createStructureEvent(tower,outcome);assertEquals(Lane.TOP,event.getStructureLane());assertEquals(StructureActionSource.MACRO_PLAY,event.getStructureActionSource());
    }
}
