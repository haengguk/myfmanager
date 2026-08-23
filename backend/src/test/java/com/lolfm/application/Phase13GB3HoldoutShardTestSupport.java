package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;

abstract class Phase13GB3HoldoutShardTestSupport {
    private static final Path OUTPUT = Path.of("build", "reports", "phase13g-b3");
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired PlayerIdentityCatalog identities;
    @Autowired PlayerRatingCatalog ratings;
    @Autowired ChampionProficiencyCatalog proficiencies;

    final void runShard(int shardIndex) throws Exception {
        var result = new Phase13GB3FrozenHoldoutRunner(
                orchestrator, simulators, mapper, champions, identities, ratings,
                proficiencies).runShard(Path.of("."), OUTPUT, shardIndex, 4);
        assertThat(result.shardIndex()).isEqualTo(shardIndex);
        assertThat(result.completedFixtureCount()).isEqualTo(25);
        assertThat(result.holdoutMatchCount()).isEqualTo(1_000);
        assertThat(result.calibrationMatchCount()).isZero();
        assertThat(result.workerJvmIdentityHash()).matches("[0-9a-f]{64}");
    }
}
