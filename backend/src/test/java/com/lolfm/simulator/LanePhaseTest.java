package com.lolfm.simulator;

import com.lolfm.domain.MatchPhaseChangeData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanePhaseTest {
    @Test void newMatchStartsWithAllLanesLaningAndFreshOuterTurrets(){
        GameState s=LanePhaseTestSupport.state();
        assertEquals(MatchPhase.LANING,s.getLanePhaseState().getMatchPhase());
        assertEquals(-1,s.getLanePhaseState().getMidGameStartedAtSeconds());
        for(Lane lane:Lane.values()){
            assertEquals(LanePhase.LANING,s.getLanePhaseState().getLanePhase(lane));
            for(TeamSide side:TeamSide.values()){
                LaneStructureState tower=s.getMapState().getLaneState(side,lane);
                assertTrue(tower.isOuterTowerAlive());
                assertEquals(100,tower.getOuterRemainingIntegrity(),1e-9);
            }
        }
    }
    @Test void matchesDoNotShareLanePhaseOrIntegrityState(){
        GameState a=LanePhaseTestSupport.state(),b=LanePhaseTestSupport.state();
        a.getMapState().getLaneState(TeamSide.RED,Lane.TOP).applyOuterDamage(40);
        a.getLanePhaseState().openLane(Lane.TOP,300);
        assertEquals(100,b.getMapState().getLaneState(TeamSide.RED,Lane.TOP).getOuterRemainingIntegrity(),1e-9);
        assertEquals(LanePhase.LANING,b.getLanePhaseState().getLanePhase(Lane.TOP));
    }
    @Test void resolverIsStateless(){
        assertEquals(0,LanePhaseResolver.class.getDeclaredFields().length);
    }
    @Test void timeLimitTransitionOccursAfterLastLaningTickAndForcesRemainingLanesOpen(){
        GameState s=LanePhaseTestSupport.state();
        assertTrue(s.getLanePhaseState().transitionIfDue(839).isEmpty());
        assertEquals(LanePhase.LANING,s.getLanePhaseState().getLanePhase(Lane.TOP));
        MatchPhaseChangeData d=s.getLanePhaseState().transitionIfDue(840).orElseThrow();
        assertEquals(MidGameTransitionReason.TIME_LIMIT,d.reason());
        assertEquals(840,s.getLanePhaseState().getMidGameStartedAtSeconds());
        assertEquals(3,d.forcedOpenLanes().size());
        assertTrue(s.getMapState().getLaneState(TeamSide.BLUE,Lane.TOP).isOuterTowerAlive());
    }
    @Test void allLanesOpenTransitionsEarlyExactlyOnce(){
        GameState s=LanePhaseTestSupport.state();
        for(Lane lane:Lane.values())s.getLanePhaseState().openLane(lane,600);
        MatchPhaseChangeData d=s.getLanePhaseState().transitionIfDue(600).orElseThrow();
        assertEquals(MidGameTransitionReason.ALL_LANES_OPEN,d.reason());
        assertEquals(600,d.transitionTimeSeconds());
        assertTrue(s.getLanePhaseState().transitionIfDue(610).isEmpty());
    }
    @Test void featureOffKeepsLegacyLaningStateAndNoTransition(){
        GameState s=LanePhaseTestSupport.state(false);
        assertFalse(s.getLanePhaseState().isEnabled());
        assertFalse(s.getLanePhaseState().openLane(Lane.TOP,300));
        assertTrue(s.getLanePhaseState().transitionIfDue(900).isEmpty());
        assertEquals(LanePhase.LANING,s.getLanePhaseState().getLanePhase(Lane.TOP));
    }
}
