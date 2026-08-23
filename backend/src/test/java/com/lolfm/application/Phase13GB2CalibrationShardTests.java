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

abstract class Phase13GB2CalibrationShardTestSupport {
    private static final int SHARD_COUNT = 4;
    private static final Path OUTPUT = Path.of("build", "reports", "phase13g-b2");

    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired PlayerIdentityCatalog identities;
    @Autowired PlayerRatingCatalog ratings;
    @Autowired ChampionProficiencyCatalog proficiencies;

    final void runShard(int shardIndex) throws Exception {
        var result = new Phase13GB2CalibrationRunner(
                orchestrator,
                simulators,
                mapper,
                champions,
                identities,
                ratings,
                proficiencies).runShard(Path.of("."), OUTPUT, shardIndex, SHARD_COUNT);
        assertThat(result.shardIndex()).isEqualTo(shardIndex);
        assertThat(result.shardCount()).isEqualTo(SHARD_COUNT);
        assertThat(result.completedFixtureCount()).isEqualTo(25);
        assertThat(result.calibrationMatchCount()).isEqualTo(3_000);
        assertThat(result.holdoutMatchCount()).isZero();
        assertThat(result.runGuardHash()).matches("[0-9a-f]{64}");
        Path receiptPath = OUTPUT
                .resolve(Phase13GB2CheckpointStore.RECEIPT_DIRECTORY_NAME)
                .resolve("shard-" + shardIndex + ".json");
        var receipt = mapper.readValue(
                receiptPath.toFile(),
                Phase13GB2CalibrationModel.WorkerReceipt.class);
        assertThat(receipt.shardIndex()).isEqualTo(shardIndex);
        assertThat(receipt.shardCount()).isEqualTo(SHARD_COUNT);
        assertThat(receipt.workerJvmIdentityHash())
                .isEqualTo(Phase13GB2CheckpointStore.workerJvmIdentityHash());
        assertThat(receipt.checkpoints()).hasSize(25);
    }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("phase13g-b2-calibration-shard")
class Phase13GB2CalibrationShard0Test extends Phase13GB2CalibrationShardTestSupport {
    @Test void executesShard() throws Exception { runShard(0); }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("phase13g-b2-calibration-shard")
class Phase13GB2CalibrationShard1Test extends Phase13GB2CalibrationShardTestSupport {
    @Test void executesShard() throws Exception { runShard(1); }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("phase13g-b2-calibration-shard")
class Phase13GB2CalibrationShard2Test extends Phase13GB2CalibrationShardTestSupport {
    @Test void executesShard() throws Exception { runShard(2); }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("phase13g-b2-calibration-shard")
class Phase13GB2CalibrationShard3Test extends Phase13GB2CalibrationShardTestSupport {
    @Test void executesShard() throws Exception { runShard(3); }
}
