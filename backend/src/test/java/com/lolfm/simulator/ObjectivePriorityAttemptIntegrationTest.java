package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.*;
import com.lolfm.domain.*;
import com.lolfm.factory.DummyDataFactory;
import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectivePriorityAttemptIntegrationTest {
    private static final double D=1e-6;
    private final ObjectiveAttemptResolver attempts=new ObjectiveAttemptResolver();
    private final ObjectiveResolver objectives=new ObjectiveResolver();
    private final ObjectivePriorityResolver priority=new ObjectivePriorityResolver();

    @Test void productionDefaultsEnablePriorityAndExplicitOffIsMatchScopedNoOp(){
        assertTrue(SimulationOptions.productionDefaults().objectivePriorityEnabled());
        assertFalse(SimulationOptions.productionDefaults().withObjectivePriorityEnabled(false).objectivePriorityEnabled());
        GameState s=state(false); priority.applyJungleGankKill(s,100,Lane.BOT,TeamSide.BLUE); priority.decayRecentControl(s,500);
        assertEquals(0,s.getObjectivePriorityState().getDragonRecentControl(),D); assertEquals(0,s.getObjectivePriorityState().getAppliedImpactCount()); assertEquals(0,s.getObjectivePriorityState().getLastDecayAppliedAtSeconds());
    }
    @Test void existingWeightBreakdownPreservesEveryLegacyContributionAndTotal(){
        GameState s=state(true); s.getBlueTeamState().addGold(900); s.getBlueTeamState().addKill(); s.recordBigWin(TeamSide.BLUE); s.recordAce(TeamSide.BLUE);
        var w=attempts.objectiveWeightBreakdown(s,TeamSide.BLUE);
        assertEquals(800,w.aliveContribution(),D); assertEquals(s.getBlueTeamState().getGold()/90.0,w.goldContribution(),D); assertEquals(65,w.killContribution(),D); assertEquals(450,w.recentBigWinContribution(),D); assertEquals(800,w.recentAceContribution(),D); assertEquals(0,w.otherContribution(),D);
        assertEquals(w.aliveContribution()+w.goldContribution()+w.killContribution()+w.recentBigWinContribution()+w.recentAceContribution(),w.totalExistingWeight(),D);
    }
    @Test void priorityMultiplierIsNeutralDirectionalClampedAndMultiplicative(){
        assertEquals(1,attempts.priorityMultiplier(0,TeamSide.BLUE,true),D); assertEquals(1,attempts.priorityMultiplier(0,TeamSide.RED,true),D);
        assertEquals(2,attempts.priorityMultiplier(100,TeamSide.BLUE,true),D); assertEquals(.1,attempts.priorityMultiplier(100,TeamSide.RED,true),D);
        assertEquals(.1,attempts.priorityMultiplier(-100,TeamSide.BLUE,true),D); assertEquals(2,attempts.priorityMultiplier(-100,TeamSide.RED,true),D);
        assertEquals(1,attempts.priorityMultiplier(100,TeamSide.BLUE,false),D); assertTrue(1234*attempts.priorityMultiplier(100,TeamSide.RED,true)>0);
    }
    @Test void attemptBonusIsAbsoluteCappedAndKeepsExistingElapsedChance(){
        assertEquals(0,attempts.priorityAttemptBonus(0,true),D); assertEquals(.05,attempts.priorityAttemptBonus(100,true),D); assertEquals(.05,attempts.priorityAttemptBonus(-100,true),D); assertEquals(0,attempts.priorityAttemptBonus(100,false),D);
        assertEquals(.17,attempts.finalAttemptChance(.17,0,true),D); assertEquals(.22,attempts.finalAttemptChance(.17,100,true),D); assertEquals(1,attempts.finalAttemptChance(.98,100,true),D);
        GameState s=dragonDue(true,600); assertEquals(.45,attempts.dragonExistingBaseAttemptChance(s),D); assertEquals(.50,attempts.finalAttemptChance(.45,100,true),D);
    }
    @Test void randomBoundariesUseZeroOneOrTwoSelectionDoublesAsRequired(){
        GameState neither=dragonDue(true,340); makeIneligible(neither,TeamSide.BLUE,3); makeIneligible(neither,TeamSide.RED,3); int unchangedAttemptAt=neither.getObjectiveState().getNextDragonAttemptSeconds(); CountingRandom r=new CountingRandom(0); assertTrue(attempts.maybeAttemptObjective(neither,r,objectives).isEmpty()); assertEquals(0,r.doubleCalls); assertEquals(unchangedAttemptAt,neither.getObjectiveState().getNextDragonAttemptSeconds()); assertEquals(0,neither.getGeneralDragonAttemptCount());
        GameState fail=dragonDue(true,340); r=new CountingRandom(.99); assertTrue(attempts.maybeAttemptObjective(fail,r,objectives).isEmpty()); assertEquals(1,r.doubleCalls);
        GameState one=dragonDue(true,340); makeIneligible(one,TeamSide.RED,3); r=new CountingRandom(0); var e=attempts.maybeAttemptObjective(one,r,objectives).orElseThrow(); assertEquals(1,r.doubleCalls); assertEquals(TeamSide.BLUE,e.getObjectivePriorityDecision().selectedSide()); assertFalse(e.getObjectivePriorityDecision().sideSelectionRollExecuted());
        GameState both=dragonDue(true,340); r=new CountingRandom(0,0); assertTrue(attempts.maybeAttemptObjective(both,r,objectives).isPresent()); assertEquals(2,r.doubleCalls);
    }
    @Test void aliveButActivityUnavailableTeamsConsumeNoRandomCooldownOrCapture(){
        GameState s=dragonDue(true,340); int time=s.getCurrentTimeSeconds();
        for(TeamSide side:TeamSide.values())for(PlayerState player:s.getTeamState(side).getPlayers())player.beginRoamActivity(Lane.BOT,Lane.MID,time);
        int attemptAt=s.getObjectiveState().getNextDragonAttemptSeconds(); CountingRandom r=new CountingRandom(0);
        assertTrue(attempts.maybeAttemptObjective(s,r,objectives).isEmpty()); assertEquals(0,r.doubleCalls);
        assertEquals(attemptAt,s.getObjectiveState().getNextDragonAttemptSeconds()); assertEquals(0,s.getGeneralDragonAttemptCount());
        assertTrue(s.getObjectiveState().isDragonAlive()); assertEquals(0,s.getBlueTeamState().getDragons()); assertEquals(0,s.getRedTeamState().getDragons());
    }
    @Test void offPathKeepsLegacyChanceWeightsAndSeededSelectionOrder(){
        CountingRandom a=new CountingRandom(0,.75),b=new CountingRandom(0,.75); var on=attempts.maybeAttemptObjective(dragonDue(true,340),a,objectives).orElseThrow().getObjectivePriorityDecision(); var off=attempts.maybeAttemptObjective(dragonDue(false,340),b,objectives).orElseThrow().getObjectivePriorityDecision();
        assertEquals(on.existingBaseAttemptChance(),off.existingBaseAttemptChance(),D); assertEquals(on.blueExistingWeight(),off.blueExistingWeight()); assertEquals(on.redExistingWeight(),off.redExistingWeight()); assertEquals(on.selectedSide(),off.selectedSide()); assertEquals(a.doubleCalls,b.doubleCalls); assertEquals(0,off.priorityAttemptBonus(),D); assertEquals(1,off.bluePriorityMultiplier(),D); assertEquals(1,off.redPriorityMultiplier(),D); assertFalse(off.priorityApplied());
    }
    @Test void dragonAndBaronUseOnlyTheirOwnSignedPriority(){
        GameState s=state(true); s.laneState(Lane.BOT).setPressure(100); assertEquals(55,priority.dragonSignedPriority(s),D); assertEquals(0,priority.baronSignedPriority(s),D); s.laneState(Lane.BOT).setPressure(0); s.laneState(Lane.TOP).setPressure(-100); assertEquals(0,priority.dragonSignedPriority(s),D); assertEquals(-55,priority.baronSignedPriority(s),D);
    }
    @Test void elderDecisionNeverAppliesPriorityOrChangesLegacyRollCount(){
        GameState s=elderDue(true); priority.applyTeamfightWin(s,s.getCurrentTimeSeconds(),new TeamfightOutcome(TeamSide.BLUE,FightGrade.ACE,5,0,s.getCurrentTimeSeconds(),List.of())); CountingRandom r=new CountingRandom(0,0); var d=attempts.maybeAttemptObjective(s,r,objectives).orElseThrow().getObjectivePriorityDecision();
        assertEquals(ObjectiveType.ELDER,d.objectiveType()); assertFalse(d.priorityApplied()); assertEquals(0,d.priorityAttemptBonus(),D); assertEquals(1,d.bluePriorityMultiplier(),D); assertEquals(1,d.redPriorityMultiplier(),D); assertEquals(2,r.doubleCalls);
    }
    @Test void postFightCaptureHasStructuredNonPriorityDecisionAndNoPriorityRoll(){
        GameState s=dragonDue(true,340); CountingRandom r=new CountingRandom(0); var d=new PostFightResolver().resolve(s,new TeamfightOutcome(TeamSide.BLUE,FightGrade.BIG_WIN,3,0,340,List.of()),r,objectives).orElseThrow().getObjectivePriorityDecision();
        assertTrue(d.postFightLinked()); assertFalse(d.generalAttempt()); assertFalse(d.priorityApplied()); assertEquals(0,d.priorityAttemptBonus(),D); assertFalse(d.sideSelectionRollExecuted()); assertEquals(1,r.doubleCalls);
    }
    @Test void bigWinAceLegacyWeightAndTeamfightRecentControlRemainSeparate(){
        GameState s=state(true); s.recordBigWin(TeamSide.BLUE); s.recordAce(TeamSide.BLUE); var before=attempts.objectiveWeightBreakdown(s,TeamSide.BLUE); var out=new TeamfightOutcome(TeamSide.BLUE,FightGrade.ACE,5,0,0,List.of()); assertTrue(priority.applyTeamfightWin(s,0,out)); var after=attempts.objectiveWeightBreakdown(s,TeamSide.BLUE);
        assertEquals(before,after); assertEquals(450,after.recentBigWinContribution(),D); assertEquals(800,after.recentAceContribution(),D); assertEquals(24,s.getObjectivePriorityState().getDragonRecentControl(),D); assertFalse(priority.applyTeamfightWin(s,0,out));
    }
    @Test void generalDecisionDataIsStructuredAndPriorityAppliedOnlyWhenEnabled(){
        GameState s=dragonDue(true,340); s.laneState(Lane.MID).setPressure(40); var d=attempts.maybeAttemptObjective(s,new CountingRandom(0,0),objectives).orElseThrow().getObjectivePriorityDecision();
        assertEquals(ObjectiveType.DRAGON,d.objectiveType()); assertTrue(d.generalAttempt()); assertFalse(d.postFightLinked()); assertTrue(d.priorityApplied()); assertEquals(18,d.lanePressureScore(),D); assertEquals(.009,d.priorityAttemptBonus(),D); assertNotNull(d.blueExistingWeight()); assertTrue(d.finalBlueSelectionWeight()>0); assertNotNull(d.selectedSide());
    }
    @Test void diagnosticsInstrumentationIsObservationalAndStatsSnapshotsAreImmutable(){
        var f=new DummyDataFactory();var on=simulator(SimulationOptions.productionDefaults().withDiagnosticsEnabled(true)).simulate(f.createBlueTeam(),f.createRedTeam(),44321L);var off=simulator(SimulationOptions.productionDefaults().withDiagnosticsEnabled(false)).simulate(f.createBlueTeam(),f.createRedTeam(),44321L);assertEquals(signatures(on.getEvents()),signatures(off.getEvents()));
        GameState state=state(true);priority.applyLaneCombatKill(state,1,Lane.TOP,TeamSide.BLUE);var stats=state.getObjectivePriorityExecutionStats().snapshot();assertEquals(1,stats.impactsApplied());assertThrows(UnsupportedOperationException.class,()->stats.impactsByLane().put(Lane.TOP,99L));assertThrows(UnsupportedOperationException.class,()->stats.decisions().add(null));
    }
    @Test void sameOptionsAndSeedReproduceDecisionAndCompleteTimeline(){
        var sim=simulator(SimulationOptions.productionDefaults()); var f=new DummyDataFactory(); var a=sim.simulate(f.createBlueTeam(),f.createRedTeam(),91234L); var b=sim.simulate(f.createBlueTeam(),f.createRedTeam(),91234L);
        assertEquals(a.getDurationSeconds(),b.getDurationSeconds()); assertEquals(a.getWinner(),b.getWinner()); assertEquals(signatures(a.getEvents()),signatures(b.getEvents())); assertEquals(a.getSnapshots().size(),b.getSnapshots().size());
    }
    private List<String> signatures(List<MatchEvent> es){return es.stream().map(e->e.getTimeSeconds()+"|"+e.getType()+"|"+e.getCombatSource()+"|"+e.getKiller()+"|"+e.getVictim()+"|"+e.getObjectivePriorityDecision()).toList();}
    private MatchSimulator simulator(SimulationOptions o){return new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(),new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),new StructureResolver(),new PushResolver(),o);}
    private GameState dragonDue(boolean en,int time){GameState s=state(en);s.advanceTimeSeconds(300);s.getObjectiveState().updateSpawnState(300);s.advanceTimeSeconds(time-300);return s;}
    private GameState elderDue(boolean en){GameState s=state(en);s.getObjectiveState().claimSoul(TeamSide.BLUE,0);s.advanceTimeSeconds(ElderRuleConfig.FIRST_ELDER_SPAWN_DELAY_SECONDS);s.getObjectiveState().updateSpawnState(s.getCurrentTimeSeconds());s.advanceTimeSeconds(ElderRuleConfig.ELDER_FIRST_ATTEMPT_DELAY_SECONDS);return s;}
    private void makeIneligible(GameState s,TeamSide side,int n){for(int i=0;i<n;i++)s.getTeamState(side).getPlayers().get(i).markDead(s.getCurrentTimeSeconds(),9999);}
    private GameState state(boolean en){return new GameState(team("BLUE"),team("RED"),true,en,true,true,false);}
    private TeamState team(String n){return new TeamState(n,List.of(p(n+" top",Position.TOP),p(n+" jungle",Position.JUNGLE),p(n+" mid",Position.MID),p(n+" adc",Position.ADC),p(n+" support",Position.SUPPORT)));}
    private PlayerState p(String n,Position p){return new PlayerState(n,p,new PlayerAttributes(14,14,14,14),500);}
    private static final class CountingRandom extends Random{final double[] v;int i,doubleCalls;CountingRandom(double...v){this.v=v;}@Override public double nextDouble(){doubleCalls++;return v[Math.min(i++,v.length-1)];}@Override public boolean nextBoolean(){return false;}}
}
