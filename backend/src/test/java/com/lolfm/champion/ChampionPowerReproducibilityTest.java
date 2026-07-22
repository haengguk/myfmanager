package com.lolfm.champion;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.factory.DummyDataFactory;
import com.lolfm.simulator.*;
import org.junit.jupiter.api.Test;

class ChampionPowerReproducibilityTest {
    @Test void sameSeedLineupAndOptionsProduceTheSameCompleteTimelineOnAndOff() {
        for(boolean enabled:new boolean[]{false,true}) assertThat(run(73,enabled)).usingRecursiveComparison().isEqualTo(run(73,enabled));
    }
    private com.lolfm.domain.MatchTimeline run(long seed,boolean enabled){ChampionCatalog catalog=new ChampionCatalog(new ObjectMapper());var assignments=new ChampionSelectionValidator(catalog).resolve(new ChampionSelectionRequest(new ChampionLineupRequest("renekton","sejuani","azir","jinx","nautilus"),new ChampionLineupRequest("jax","lee-sin","ahri","kaisa","rakan")));DummyDataFactory teams=new DummyDataFactory();MatchSimulator simulator=new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(catalog),new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),new StructureResolver(),new PushResolver(),SimulationOptions.productionDefaults().withChampionPowerEnabled(enabled));return simulator.simulate(teams.createBlueTeam(),teams.createRedTeam(),seed,assignments);}
}
