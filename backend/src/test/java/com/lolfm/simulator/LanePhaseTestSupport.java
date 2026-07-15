package com.lolfm.simulator;

import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class LanePhaseTestSupport {
    private LanePhaseTestSupport() { }
    static GameState state(){return state(true);}
    static GameState state(boolean enabled){return new GameState(team("Blue"),team("Red"),true,true,enabled);}
    static TeamState team(String name){
        List<PlayerState> players=new ArrayList<>();
        for(Position p:Position.values())players.add(new PlayerState(name+"-"+p,p,new PlayerAttributes(14,14,14,14),500));
        return new TeamState(name,players);
    }
    static final class CountingRandom extends Random {
        private final double value; int doubles;
        CountingRandom(double value){this.value=value;}
        @Override public double nextDouble(){doubles++;return value;}
        @Override public int nextInt(int bound){return 0;}
        @Override public boolean nextBoolean(){return false;}
    }
}
