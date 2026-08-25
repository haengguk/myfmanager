package com.lolfm.application;

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

@SpringBootTest
abstract class MatchupV9StructureAttributionTestSupport {
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired PlayerIdentityCatalog identities;
    @Autowired PlayerRatingCatalog ratings;
    @Autowired ChampionProficiencyCatalog proficiencies;

    MatchupV9StructureAttributionRunner runner() {
        return new MatchupV9StructureAttributionRunner(
                orchestrator, simulators, mapper, champions, identities, ratings, proficiencies);
    }
}

@Tag("diagnostic")
@Tag("matchup-v9-structure-attribution-freeze")
class MatchupV9StructureAttributionFreezeTest
        extends MatchupV9StructureAttributionTestSupport {
    @Test void freezesContractAndVerifiesPredecessor() throws Exception {
        runner().freeze(Path.of("."), MatchupV9StructureAttributionRunner.OUTPUT);
    }
}

abstract class MatchupV9StructureAttributionShardSupport
        extends MatchupV9StructureAttributionTestSupport {
    void run(int shard) throws Exception {
        runner().runShard(Path.of("."), MatchupV9StructureAttributionRunner.OUTPUT, shard);
    }
}

@Tag("diagnostic") @Tag("matchup-v9-structure-attribution-shard")
class MatchupV9StructureAttributionShard0Test extends MatchupV9StructureAttributionShardSupport {
    @Test void runs() throws Exception { run(0); }
}

@Tag("diagnostic") @Tag("matchup-v9-structure-attribution-shard")
class MatchupV9StructureAttributionShard1Test extends MatchupV9StructureAttributionShardSupport {
    @Test void runs() throws Exception { run(1); }
}

@Tag("diagnostic") @Tag("matchup-v9-structure-attribution-shard")
class MatchupV9StructureAttributionShard2Test extends MatchupV9StructureAttributionShardSupport {
    @Test void runs() throws Exception { run(2); }
}

@Tag("diagnostic") @Tag("matchup-v9-structure-attribution-shard")
class MatchupV9StructureAttributionShard3Test extends MatchupV9StructureAttributionShardSupport {
    @Test void runs() throws Exception { run(3); }
}

@Tag("diagnostic")
@Tag("matchup-v9-structure-attribution-finalize")
class MatchupV9StructureAttributionFinalizerTest
        extends MatchupV9StructureAttributionTestSupport {
    @Test void finalizesCanonicalArtifacts() throws Exception {
        runner().finalizeArtifacts(Path.of("."), MatchupV9StructureAttributionRunner.OUTPUT);
    }
}
