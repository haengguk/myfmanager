package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.*;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LanePressureTest {
    @Test void initialStateHasThreeNeutralLanesAndIsIndependent() {
        GameState one = game(14, 14), two = game(14, 14);
        for (Lane lane : Lane.values()) { assertEquals(0.0, one.laneState(lane).getPressure()); assertEquals(LanePriority.NEUTRAL, one.laneState(lane).getPriority()); }
        new LanePressureResolver().resolve(one, 30, new Random(1));
        assertEquals(0.0, two.laneState(Lane.TOP).getPressure());
    }
    @Test void cadenceDuplicateAndTimePolicyAreGameLocal() {
        GameState state=game(14,14); LanePressureResolver resolver=new LanePressureResolver(); Random random=new Random(4);
        resolver.resolve(state,0,random); resolver.resolve(state,10,random); resolver.resolve(state,20,random); assertEquals(-1,state.getLastLanePressureResolvedAtSeconds());
        resolver.resolve(state,30,random); double top=state.laneState(Lane.TOP).getPressure(); resolver.resolve(state,30,random); assertEquals(top,state.laneState(Lane.TOP).getPressure()); assertEquals(1,state.getDuplicateLanePressureResolutionCount());
        resolver.resolve(state,60,random); assertEquals(60,state.getLastLanePressureResolvedAtSeconds()); assertThrows(IllegalArgumentException.class,()->resolver.resolve(state,50,random));
    }
    @Test void formulaClampAndPriorityBoundariesWork() {
        LanePressureResolver r=new LanePressureResolver();
        assertEquals(19.5,r.calculateNextPressure(10,5,2,0));
        assertEquals(100.0,r.calculateNextPressure(100,100,4,2.5)); assertEquals(-100.0,r.calculateNextPressure(-100,-100,-4,-2.5));
        LaneState lane=new LaneState(Lane.TOP); lane.setPressure(20); assertEquals(LanePriority.BLUE,lane.getPriority()); lane.setPressure(-20); assertEquals(LanePriority.RED,lane.getPriority()); lane.setPressure(19.999); assertEquals(LanePriority.NEUTRAL,lane.getPriority());
    }
    @Test void topMidAndBotUseOnlyTheirParticipantsAndBotUsesSupport() {
        LanePressureResolver r=new LanePressureResolver(); GameState top=game(18,10); assertTrue(r.lanePowerDifference(top,Lane.TOP)>0); assertEquals(0.0,r.lanePowerDifference(top,Lane.MID),.00001);
        GameState bot=gameWithBotSupport(14,14,18,10); assertTrue(r.lanePowerDifference(bot,Lane.BOT)>0); assertEquals(0.0,r.lanePowerDifference(bot,Lane.TOP),.00001);
        bot.getBlueTeamState().addGold(99999); assertEquals(0.0,r.goldModifier(bot,Lane.MID),.00001);
    }
    @Test void pressureChangesOnlyMatchingLaneCs() {
        GameState state=game(14,14); state.laneState(Lane.TOP).setPressure(100); PositionEconomyResolver r=new PositionEconomyResolver();
        assertEquals(1.15,r.laneCsMultiplier(Position.TOP,state,TeamSide.BLUE),.00001); assertEquals(.85,r.laneCsMultiplier(Position.TOP,state,TeamSide.RED),.00001); assertEquals(1.0,r.laneCsMultiplier(Position.JUNGLE,state,TeamSide.BLUE),.00001); assertEquals(1.0,r.laneCsMultiplier(Position.SUPPORT,state,TeamSide.BLUE),.00001);
    }
    @Test void snapshotIsImmutableAndOrdered() {
        GameState state=game(14,14); state.laneState(Lane.BOT).setPressure(25); var snapshot=new SnapshotFactory().create(state); state.laneState(Lane.BOT).setPressure(-25);
        assertEquals(List.of(Lane.TOP,Lane.MID,Lane.BOT),snapshot.getLaneSnapshots().stream().map(x->x.lane()).toList()); assertEquals(25,snapshot.getLaneSnapshots().get(2).pressure()); assertEquals(LanePriority.BLUE,snapshot.getLaneSnapshots().get(2).priority());
    }
    private GameState game(int blueTop, int redTop) { return gameWithBotSupport(blueTop,redTop,14,14); }
    private GameState gameWithBotSupport(int blueTop,int redTop,int blueSup,int redSup) { return new GameState(team("B",blueTop,blueSup),team("R",redTop,redSup)); }
    private TeamState team(String name,int top,int sup) { return new TeamState(name,List.of(p("top",Position.TOP,top),p("j",Position.JUNGLE,14),p("mid",Position.MID,14),p("adc",Position.ADC,14),p("sup",Position.SUPPORT,sup))); }
    private PlayerState p(String n,Position pos,int value) { return new PlayerState(n,pos,new PlayerAttributes(value,value,14,value),500); }
}
