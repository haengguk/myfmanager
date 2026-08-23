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
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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

        var schedule = Phase13GB1AuditSchedule.create();
        var fixture = schedule.allFixtures().stream()
                .filter(value -> value.fixtureId().equals(result.fixtureId()))
                .findFirst().orElseThrow();
        Map<com.lolfm.simulator.SimulationRuntimeProfileId,
                Phase13GB2CalibrationModel.MatchRow> templates = result.rows().stream()
                .collect(Collectors.toMap(
                        Phase13GB2CalibrationModel.MatchRow::profileId,
                        Function.identity()));
        ArrayList<Phase13GB2CalibrationModel.MatchRow> syntheticRows = new ArrayList<>();
        for (var job : Phase13GB2CalibrationContract.jobs(fixture)) {
            ObjectNode node = mapper.valueToTree(templates.get(job.profileId()));
            node.put("jobId", job.jobId());
            node.put("seedIndex", job.seedIndex());
            node.put("seed", job.seed());
            node.put("profileIndex", job.profileIndex());
            syntheticRows.add(mapper.treeToValue(
                    node, Phase13GB2CalibrationModel.MatchRow.class));
        }
        var store = new Phase13GB2CheckpointStore(mapper);
        var checkpoint = new Phase13GB2CalibrationModel.FixtureCheckpoint(
                Phase13GB2CalibrationModel.CHECKPOINT_SCHEMA,
                store.guardHash(result.runGuard()),
                result.runGuard(),
                result.fixedDraft(),
                result.determinismReplay(),
                syntheticRows);
        Path checkpointPath = store.checkpointPath(
                smokeOutput.resolve("checkpoint-roundtrip"), 0, fixture);
        store.writeAtomic(checkpointPath, checkpoint, result.runGuard(), fixture);
        assertThat(store.readAndValidate(
                checkpointPath, result.runGuard(), fixture)).isEqualTo(checkpoint);

        ArrayList<Phase13GB2CalibrationModel.FixtureCheckpoint> syntheticPopulation =
                new ArrayList<>();
        int fixtureIndex = 0;
        for (var scheduledFixture : schedule.allFixtures()) {
            ObjectNode draftNode = mapper.valueToTree(result.fixedDraft());
            draftNode.put("fixtureId", scheduledFixture.fixtureId());
            draftNode.put("fixtureLane", scheduledFixture.fixtureLane().name());
            draftNode.put("pairId", scheduledFixture.pairId());
            draftNode.put("blueTeamCode", scheduledFixture.blueTeamCode());
            draftNode.put("redTeamCode", scheduledFixture.redTeamCode());
            draftNode.put("seriesGameNumber", scheduledFixture.seriesGameNumber());
            draftNode.put("productionOrchestrationCount",
                    scheduledFixture.seriesGameNumber());
            var syntheticDraft = mapper.treeToValue(
                    draftNode, Phase13GB2CalibrationModel.FixedDraftRow.class);
            ObjectNode replayNode = mapper.valueToTree(result.determinismReplay());
            replayNode.put("fixtureId", scheduledFixture.fixtureId());
            replayNode.put("seedIndex", 0);
            replayNode.put("seed", scheduledFixture.calibrationSeeds().getFirst());
            var syntheticReplay = mapper.treeToValue(
                    replayNode,
                    Phase13GB2CalibrationModel.DeterminismReplayEvidence.class);
            ArrayList<Phase13GB2CalibrationModel.MatchRow> fixtureRows = new ArrayList<>();
            for (var job : Phase13GB2CalibrationContract.jobs(scheduledFixture)) {
                ObjectNode node = mapper.valueToTree(templates.get(job.profileId()));
                node.put("jobId", job.jobId());
                node.put("fixtureId", job.fixtureId());
                node.put("fixtureLane", job.fixtureLane().name());
                node.put("pairId", job.pairId());
                node.put("blueTeamCode", job.blueTeamCode());
                node.put("redTeamCode", job.redTeamCode());
                node.put("seriesGameNumber", job.seriesGameNumber());
                node.put("seedIndex", job.seedIndex());
                node.put("seed", job.seed());
                node.put("profileIndex", job.profileIndex());
                fixtureRows.add(mapper.treeToValue(
                        node, Phase13GB2CalibrationModel.MatchRow.class));
            }
            syntheticPopulation.add(new Phase13GB2CalibrationModel.FixtureCheckpoint(
                    Phase13GB2CalibrationModel.CHECKPOINT_SCHEMA,
                    store.guardHash(result.runGuard()),
                    result.runGuard(),
                    syntheticDraft,
                    syntheticReplay,
                    fixtureRows));
            fixtureIndex++;
        }
        Path finalizerOutput = smokeOutput.resolve("synthetic-finalizer");
        var syntheticArtifacts = Phase13GB2CalibrationArtifactWriter.write(
                mapper,
                finalizerOutput,
                schedule,
                result.runGuard(),
                syntheticPopulation);
        assertThat(fixtureIndex).isEqualTo(100);
        assertThat(syntheticArtifacts.status())
                .isEqualTo("CALIBRATION_EVIDENCE_READY_FOR_REVIEW");
        assertThat(syntheticArtifacts.calibrationMatchCount()).isEqualTo(12_000);
        assertThat(syntheticArtifacts.pairedMarginalCount()).isEqualTo(12_000);
        assertThat(java.nio.file.Files.readAllLines(
                finalizerOutput.resolve(
                        Phase13GB2CalibrationArtifactWriter.MATCHES_JSONL_FILE)))
                .hasSize(12_000);
        assertThat(java.nio.file.Files.readAllLines(
                finalizerOutput.resolve(Phase13GB2CalibrationArtifactWriter.SHA_FILE)))
                .hasSize(15);
        assertThat(java.nio.file.Files.readAllLines(finalizerOutput.resolve(
                Phase13GB2CalibrationArtifactWriter.DETERMINISM_REPLAYS_FILE)))
                .hasSize(101);
        var contract = mapper.readTree(finalizerOutput.resolve(
                Phase13GB2CalibrationArtifactWriter.CONTRACT_FILE).toFile());
        assertThat(contract.path("fixturePreparationOrchestrationCount").asInt())
                .isEqualTo(110);
        assertThat(contract.path("calibrationMatchExecutionCount").asInt())
                .isEqualTo(12_000);
        assertThat(contract.path("determinismReplayExecutionCount").asInt())
                .isEqualTo(100);
        assertThat(contract.path("holdoutExecuted").asBoolean()).isFalse();
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
