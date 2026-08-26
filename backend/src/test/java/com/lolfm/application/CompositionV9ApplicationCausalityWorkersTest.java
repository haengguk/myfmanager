package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

abstract class CompositionV9ApplicationCausalityWorkerSupport {
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired PlayerIdentityCatalog identities;
    @Autowired PlayerRatingCatalog ratings;
    @Autowired ChampionProficiencyCatalog proficiencies;

    final void assertShard(int shard) throws Exception {
        var runner = new CompositionV9ApplicationCausalityRunner(orchestrator, simulators, mapper,
                champions, identities, ratings, proficiencies);
        var result = runner.runShard(Path.of(".").toAbsolutePath().normalize(),
                CompositionV9ApplicationCausalityRunner.OUTPUT, shard);
        assertThat(result.fixtureCount()).isEqualTo(25);
        assertThat(result.pairCount()).isEqualTo(100);
        assertThat(result.replayChecks()).isEqualTo(25);
        assertThat(result.instrumentationChecks()).isEqualTo(50);
    }
}

@SpringBootTest
@Tag("composition-v9-application-causality-worker")
class CompositionV9ApplicationCausalityShard0Test extends CompositionV9ApplicationCausalityWorkerSupport {
    @Test void runShard() throws Exception { assertShard(0); }
}

@SpringBootTest
@Tag("composition-v9-application-causality-worker")
class CompositionV9ApplicationCausalityShard1Test extends CompositionV9ApplicationCausalityWorkerSupport {
    @Test void runShard() throws Exception { assertShard(1); }
}

@SpringBootTest
@Tag("composition-v9-application-causality-worker")
class CompositionV9ApplicationCausalityShard2Test extends CompositionV9ApplicationCausalityWorkerSupport {
    @Test void runShard() throws Exception { assertShard(2); }
}

@SpringBootTest
@Tag("composition-v9-application-causality-worker")
class CompositionV9ApplicationCausalityShard3Test extends CompositionV9ApplicationCausalityWorkerSupport {
    @Test void runShard() throws Exception { assertShard(3); }
}
