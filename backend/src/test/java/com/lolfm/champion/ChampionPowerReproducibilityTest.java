package com.lolfm.champion;

import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteTimelineEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.factory.DummyDataFactory;
import com.lolfm.simulator.EndGameEvaluator;
import com.lolfm.simulator.MatchSimulator;
import com.lolfm.simulator.ObjectiveAttemptResolver;
import com.lolfm.simulator.ObjectiveResolver;
import com.lolfm.simulator.PostFightResolver;
import com.lolfm.simulator.PushResolver;
import com.lolfm.simulator.SimulationOptions;
import com.lolfm.simulator.SnapshotFactory;
import com.lolfm.simulator.StructureResolver;
import com.lolfm.simulator.TeamfightResolver;
import org.junit.jupiter.api.Test;

class ChampionPowerReproducibilityTest {
    @Test
    void sameSeedLineupAndOptionsProduceTheSameCompleteTimelineOnAndOff() {
        for (boolean enabled : new boolean[]{false, true}) {
            assertCompleteTimelineEquals(run(73, enabled), run(73, enabled));
        }
    }

    private MatchTimeline run(long seed, boolean enabled) {
        ChampionCatalog catalog = new ChampionCatalog(new ObjectMapper());
        MatchChampionAssignments assignments = new ChampionSelectionValidator(catalog).resolve(
                new ChampionSelectionRequest(
                        new ChampionLineupRequest(
                                "renekton", "sejuani", "azir", "jinx", "nautilus"),
                        new ChampionLineupRequest(
                                "jax", "lee-sin", "ahri", "kaisa", "rakan")));
        DummyDataFactory teams = new DummyDataFactory();
        MatchSimulator simulator = new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(catalog),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(),
                SimulationOptions.productionDefaults().withChampionPowerEnabled(enabled));
        return simulator.simulate(teams.createBlueTeam(), teams.createRedTeam(), seed, assignments);
    }
}
