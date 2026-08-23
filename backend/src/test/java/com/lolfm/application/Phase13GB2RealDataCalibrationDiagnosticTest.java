package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Tag("diagnostic")
class Phase13GB2RealDataCalibrationDiagnosticTest {
    private static final Path OUTPUT = Path.of("build", "reports", "phase13g-b2");

    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired PlayerIdentityCatalog identities;
    @Autowired PlayerRatingCatalog ratings;
    @Autowired ChampionProficiencyCatalog proficiencies;
    @TempDir Path smokeOutput;

    @Test
    @Tag("phase13g-b2-smoke")
    void oneFixedRealDraftAndOneCalibrationSeedReachAllFiveProfiles() throws Exception {
        var result = runner().runSmoke(Path.of("."), smokeOutput);

        assertThat(result.rows()).hasSize(5);
        assertThat(result.productionOrchestrationCount()).isOne();
        assertThat(result.rows().stream().map(
                Phase13GB2CalibrationModel.MatchRow::profileId))
                .containsExactlyElementsOf(Phase13GB1RealMatchHarness.AUDIT_PROFILES);
        assertThat(result.rows()).allSatisfy(row -> {
            assertThat(row.integrityClean()).isTrue();
            assertThat(row.sampleLane())
                    .isEqualTo(Phase13GB1AuditSchedule.SampleLane.CALIBRATION);
            if (row.profileId() == com.lolfm.simulator.SimulationRuntimeProfileId
                    .FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1) {
                assertThat(row.tempoGankConsumptions())
                        .isEqualTo(row.jungleGankAttempts());
                assertThat(row.tempoCounterGankConsumptions())
                        .isEqualTo(row.counterGankAttempts());
            } else {
                assertThat(row.tempoGankConsumptions()).isZero();
                assertThat(row.tempoCounterGankConsumptions()).isZero();
            }
            assertThat(row.jungleObservations()).isNotEmpty().allSatisfy(observation -> {
                if (observation.checkpointKind().equals("FIXED")) {
                    assertThat(observation.actualTimeSeconds())
                            .isGreaterThanOrEqualTo(observation.requestedTimeSeconds());
                    assertThat(observation.actualTimeSeconds())
                            .isLessThanOrEqualTo(row.durationSeconds());
                    assertThat(observation.requestedTimeSeconds())
                            .isLessThanOrEqualTo(row.durationSeconds());
                }
            });
        });
        assertThat(result.rows().stream()
                .flatMap(row -> row.jungleObservations().stream())
                .filter(observation -> observation.checkpointKind().equals("FIXED")))
                .anyMatch(observation -> observation.actualTimeSeconds()
                        > observation.requestedTimeSeconds());
        assertThat(result.runGuard().holdoutMatchExecutionCount()).isZero();
        assertThat(result.artifacts().matchCount()).isEqualTo(5);
        assertThat(result.artifacts().marginalCount()).isEqualTo(5);
        assertThat(java.nio.file.Files.readAllLines(
                smokeOutput.resolve("phase13g-b2-smoke-matches.jsonl")))
                .hasSize(5);
        assertThat(java.nio.file.Files.readAllLines(
                smokeOutput.resolve("phase13g-b2-smoke-marginals.csv")))
                .hasSize(6);

        var store = new Phase13GB2CheckpointStore(mapper);
        var baseline = result.rows().getFirst();
        var baselineEvidence = store.rowEvidence(baseline);
        store.validateReplayProvenance(baseline, result.runGuard());
        store.validateRowEvidence(baseline, baselineEvidence);

        ObjectNode relabeledNode = mapper.valueToTree(baseline);
        var fixture = Phase13GB1AuditSchedule.create().allFixtures().stream()
                .filter(value -> value.fixtureId().equals(result.fixtureId()))
                .findFirst().orElseThrow();
        var relabeledJob = Phase13GB2CalibrationContract.jobs(fixture).get(5);
        relabeledNode.put("jobId", relabeledJob.jobId());
        relabeledNode.put("seedIndex", relabeledJob.seedIndex());
        relabeledNode.put("seed", relabeledJob.seed());
        var relabeled = mapper.treeToValue(
                relabeledNode, Phase13GB2CalibrationModel.MatchRow.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> store.validateReplayProvenance(relabeled, result.runGuard()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("replay provenance");

        ObjectNode outcomeNode = mapper.valueToTree(baseline);
        outcomeNode.put("blueGold", baseline.blueGold() + 1);
        var outcomeChanged = mapper.treeToValue(
                outcomeNode, Phase13GB2CalibrationModel.MatchRow.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> store.validateRowEvidence(outcomeChanged, baselineEvidence))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outcome or observation");

        ObjectNode observationNode = mapper.valueToTree(baseline);
        ObjectNode firstObservation = (ObjectNode) observationNode
                .withArray("jungleObservations").get(0);
        firstObservation.put("gold", firstObservation.path("gold").asInt() + 1);
        var observationChanged = mapper.treeToValue(
                observationNode, Phase13GB2CalibrationModel.MatchRow.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> store.validateRowEvidence(observationChanged, baselineEvidence))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outcome or observation");

        byte[] checkpointPayload = mapper.writeValueAsBytes(result.rows());
        var payloadReceipt = new Phase13GB2CalibrationModel.CheckpointPayloadReceipt(
                0,
                fixture.fixtureId(),
                "smoke-checkpoint.json",
                Phase13GB2CalibrationContract.EXPECTED_ROWS_PER_FIXTURE,
                Phase13GB2CheckpointStore.sha256(checkpointPayload));
        store.validatePayloadReceipt(checkpointPayload, payloadReceipt);
        byte[] changedPayload = checkpointPayload.clone();
        changedPayload[changedPayload.length - 1] ^= 1;
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> store.validatePayloadReceipt(changedPayload, payloadReceipt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("digest");

        var syntheticArtifacts =
                Phase13GB2CalibrationArtifactWriter.writeSyntheticValidation(
                        mapper,
                        smokeOutput.resolve("synthetic-validation"),
                        result.runGuard(),
                        result.fixedDraft(),
                        result.rows());
        assertThat(syntheticArtifacts.status())
                .isEqualTo("SYNTHETIC_VALIDATION_ONLY");
        assertThat(syntheticArtifacts.officialCalibrationEvidence()).isFalse();
        assertThat(syntheticArtifacts.status())
                .isNotEqualTo("CALIBRATION_EVIDENCE_READY_FOR_REVIEW");
    }

    @Test
    @Tag("phase13g-b2-calibration-finalizer")
    void finalizesOnlyFrozenCalibrationCheckpointsAndWritesReviewEvidence() throws Exception {
        var result = runner().finalizeOfficial(Path.of("."), OUTPUT);

        assertThat(result.completedFixtureCount()).isEqualTo(100);
        assertThat(result.calibrationMatchCount()).isEqualTo(12_000);
        assertThat(result.holdoutMatchCount()).isZero();
        assertThat(result.artifacts().status())
                .isEqualTo("CALIBRATION_EVIDENCE_READY_FOR_REVIEW");
        assertThat(result.artifacts().pairedMarginalCount()).isEqualTo(12_000);
        assertThat(result.artifacts().reviewSha256()).matches("[0-9a-f]{64}");
        assertThat(result.artifacts().shaManifestSha256()).matches("[0-9a-f]{64}");
        var receiptManifest = mapper.readTree(OUTPUT.resolve(
                Phase13GB2CalibrationArtifactWriter
                        .CHECKPOINT_RECEIPT_MANIFEST_FILE).toFile());
        assertThat(receiptManifest.path("workerReceiptCount").asInt()).isEqualTo(4);
        assertThat(receiptManifest.path("distinctFreshJvmCount").asInt()).isEqualTo(4);
        assertThat(receiptManifest.path("checkpointCount").asInt()).isEqualTo(100);
        assertThat(receiptManifest.path("checkpoints").size()).isEqualTo(100);
        assertThat(java.nio.file.Files.readAllLines(
                OUTPUT.resolve(Phase13GB2CalibrationArtifactWriter.SHA_FILE)))
                .hasSize(16);
    }

    private Phase13GB2CalibrationRunner runner() {
        return new Phase13GB2CalibrationRunner(
                orchestrator,
                simulators,
                mapper,
                champions,
                identities,
                ratings,
                proficiencies);
    }
}
