package com.lolfm.simulator;

import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import java.util.List;
import java.util.Random;

final class ObjectiveDecisionTestSupport {
    private ObjectiveDecisionTestSupport() { }

    static GameState dragonState(boolean enabled) {
        GameState state = new GameState(team("BLUE"), team("RED"), true, true, true, true, enabled);
        state.advanceTimeSeconds(300);
        new ObjectiveResolver().updateSpawnState(state);
        state.advanceTimeSeconds(40);
        return state;
    }

    static TeamState team(String name) {
        return new TeamState(name, List.of(
                player(name + "-TOP", Position.TOP, 14, 14),
                player(name + "-JUNGLE", Position.JUNGLE, 14, 14),
                player(name + "-MID", Position.MID, 14, 14),
                player(name + "-ADC", Position.ADC, 14, 14),
                player(name + "-SUPPORT", Position.SUPPORT, 14, 14)
        ));
    }

    static PlayerState player(String name, Position position, int farming, int teamfighting) {
        return new PlayerState(name, position, new PlayerAttributes(14, 14, farming, teamfighting), 500);
    }

    static final class SequenceRandom extends Random {
        private final double[] values;
        private int index;
        private int doubleCalls;
        private int booleanCalls;
        SequenceRandom(double... values) { this.values = values; }
        @Override public double nextDouble() {
            doubleCalls++;
            return index < values.length ? values[index++] : 0;
        }
        @Override public boolean nextBoolean() { booleanCalls++; return false; }
        int doubleCalls() { return doubleCalls; }
        int booleanCalls() { return booleanCalls; }
    }
}
