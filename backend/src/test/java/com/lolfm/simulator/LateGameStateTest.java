package com.lolfm.simulator;import static org.junit.jupiter.api.Assertions.*;import org.junit.jupiter.api.Test;
class LateGameStateTest{
 @Test void dueBoundaryOverdueAndDuplicateAreDeterministic(){LateGameState s=new LateGameState(true);assertTrue(s.start(1800,LateGameTransitionReason.TIME_LIMIT));assertFalse(s.due(1829));assertTrue(s.due(1830));assertEquals(1830,s.beginEvaluation(1840));assertEquals(1890,s.getNextEvaluationAtSeconds());assertFalse(s.due(1840));assertThrows(IllegalStateException.class,()->s.beginEvaluation(1840));}
 @Test void featureOffKeepsPhaseStateButNoSchedule(){LateGameState s=new LateGameState(false);s.start(1800,LateGameTransitionReason.TIME_LIMIT);assertEquals(-1,s.getNextEvaluationAtSeconds());assertFalse(s.due(2000));}
}
