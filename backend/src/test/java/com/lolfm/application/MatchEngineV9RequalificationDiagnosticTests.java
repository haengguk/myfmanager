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

abstract class MatchEngineV9RequalificationTestSupport {
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired PlayerIdentityCatalog identities;
    @Autowired PlayerRatingCatalog ratings;
    @Autowired ChampionProficiencyCatalog proficiencies;

    MatchEngineV9RequalificationRunner runner() {
        return new MatchEngineV9RequalificationRunner(
                orchestrator, simulators, mapper, champions, identities, ratings, proficiencies);
    }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-requalification-freeze")
class MatchEngineV9RequalificationFreezeTest extends MatchEngineV9RequalificationTestSupport {
    @Test void freezesContractBeforeSimulation() throws Exception {
        var result = runner().freeze(Path.of("."), MatchEngineV9RequalificationRunner.OUTPUT);
        assertThat(result.contractHash()).matches("[0-9a-f]{64}");
        assertThat(result.seedOverlapAudit().clean()).isTrue();
        assertThat(identities.all()).hasSize(50);
    }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-requalification-smoke")
class MatchEngineV9RequalificationSmokeTest extends MatchEngineV9RequalificationTestSupport {
    @Test void verifiesThreeProfilesPairingReachabilityReplayAndInstrumentation() throws Exception {
        var result = runner().smoke(Path.of("."), MatchEngineV9RequalificationRunner.OUTPUT);
        assertThat(result.clean()).isTrue();
        assertThat(result.matchRows()).isEqualTo(6);
    }
}

abstract class MatchEngineV9CalibrationShardSupport extends MatchEngineV9RequalificationTestSupport {
    final void run(int shard) throws Exception {
        var result = runner().runShard(Path.of("."), MatchEngineV9RequalificationRunner.OUTPUT,
                MatchEngineV9RequalificationContract.SampleLane.CALIBRATION, shard);
        assertThat(result.fixtureCount()).isEqualTo(25);
        assertThat(result.rowCount()).isEqualTo(600);
    }
}

@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-requalification-calibration-shard")
class MatchEngineV9CalibrationShard0Test extends MatchEngineV9CalibrationShardSupport {
    @Test void executes() throws Exception { run(0); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-requalification-calibration-shard")
class MatchEngineV9CalibrationShard1Test extends MatchEngineV9CalibrationShardSupport {
    @Test void executes() throws Exception { run(1); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-requalification-calibration-shard")
class MatchEngineV9CalibrationShard2Test extends MatchEngineV9CalibrationShardSupport {
    @Test void executes() throws Exception { run(2); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-requalification-calibration-shard")
class MatchEngineV9CalibrationShard3Test extends MatchEngineV9CalibrationShardSupport {
    @Test void executes() throws Exception { run(3); }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-requalification-calibration-finalize")
class MatchEngineV9CalibrationFinalizerTest extends MatchEngineV9RequalificationTestSupport {
    @Test void reviewsCalibrationWithoutChangingFrozenGates() throws Exception {
        var result = runner().finalizeCalibration(
                Path.of("."), MatchEngineV9RequalificationRunner.OUTPUT);
        assertThat(result.matchRowCount()).isEqualTo(2_400);
        assertThat(result.gatePolicy()).contains("RETAINED");
    }
}

abstract class MatchEngineV9HoldoutShardSupport extends MatchEngineV9RequalificationTestSupport {
    final void run(int shard) throws Exception {
        var result = runner().runShard(Path.of("."), MatchEngineV9RequalificationRunner.OUTPUT,
                MatchEngineV9RequalificationContract.SampleLane.HOLDOUT, shard);
        assertThat(result.fixtureCount()).isEqualTo(25);
        assertThat(result.rowCount()).isEqualTo(300);
    }
}

@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-requalification-holdout-shard")
class MatchEngineV9HoldoutShard0Test extends MatchEngineV9HoldoutShardSupport {
    @Test void executes() throws Exception { run(0); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-requalification-holdout-shard")
class MatchEngineV9HoldoutShard1Test extends MatchEngineV9HoldoutShardSupport {
    @Test void executes() throws Exception { run(1); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-requalification-holdout-shard")
class MatchEngineV9HoldoutShard2Test extends MatchEngineV9HoldoutShardSupport {
    @Test void executes() throws Exception { run(2); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-requalification-holdout-shard")
class MatchEngineV9HoldoutShard3Test extends MatchEngineV9HoldoutShardSupport {
    @Test void executes() throws Exception { run(3); }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-requalification-fresh-jvm-artifact")
class MatchEngineV9FreshJvmArtifactATest extends MatchEngineV9RequalificationTestSupport {
    @Test void writesCandidateA() throws Exception {
        var result = runner().writeCandidate(Path.of("."), MatchEngineV9RequalificationRunner.OUTPUT,
                MatchEngineV9RequalificationRunner.OUTPUT.resolve(".fresh-jvm-a"));
        assertThat(result.holdoutRows()).isEqualTo(1_200);
    }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-requalification-fresh-jvm-artifact")
class MatchEngineV9FreshJvmArtifactBTest extends MatchEngineV9RequalificationTestSupport {
    @Test void writesCandidateB() throws Exception {
        var result = runner().writeCandidate(Path.of("."), MatchEngineV9RequalificationRunner.OUTPUT,
                MatchEngineV9RequalificationRunner.OUTPUT.resolve(".fresh-jvm-b"));
        assertThat(result.holdoutRows()).isEqualTo(1_200);
    }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-requalification-promote")
class MatchEngineV9OfficialPromotionTest extends MatchEngineV9RequalificationTestSupport {
    @Test void requiresFreshJvmByteEqualityBeforeOfficialPromotion() throws Exception {
        Path output = MatchEngineV9RequalificationRunner.OUTPUT;
        var result = runner().promoteOfficial(Path.of("."), output,
                output.resolve(".fresh-jvm-a"), output.resolve(".fresh-jvm-b"));
        assertThat(result.recommendation()).isIn(
                "RECOMMEND_FULL_SYSTEM_CANDIDATE_V1",
                "RECOMMEND_MATCHUP_ONLY_CANDIDATE_V1",
                "RECOMMEND_BASELINE_V1",
                "NO_PRODUCTION_RECOMMENDATION_BLOCKED");
    }
}
