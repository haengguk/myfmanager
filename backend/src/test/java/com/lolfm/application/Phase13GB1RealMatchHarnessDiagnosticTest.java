package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.JungleTempoActionType;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** One real fixture and five paired profiles; explicitly not calibration or holdout. */
@SpringBootTest
@Tag("diagnostic")
@Tag("phase13g-b1-dry-run")
class Phase13GB1RealMatchHarnessDiagnosticTest {
    private static final Path REPORT_OUTPUT =
            Path.of("build", "reports", "phase13g-b1");
    private static final List<String> ARTIFACT_FILES = List.of(
            Phase13GB1AuditArtifactWriter.SUMMARY_FILE,
            Phase13GB1AuditArtifactWriter.PROFILE_FILE,
            Phase13GB1AuditArtifactWriter.SCHEDULE_JSON_FILE,
            Phase13GB1AuditArtifactWriter.SCHEDULE_CSV_FILE,
            Phase13GB1AuditArtifactWriter.DRY_RUN_JSON_FILE,
            Phase13GB1AuditArtifactWriter.DRY_RUN_CSV_FILE,
            Phase13GB1AuditArtifactWriter.SHA_FILE);

    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired PlayerIdentityCatalog identities;
    @Autowired PlayerRatingCatalog ratings;
    @Autowired ChampionProficiencyCatalog proficiencies;
    @Autowired LckTeamAssembler teams;

    @TempDir Path temporaryOutput;

    @Test
    void writesOneFixedRealDraftFiveProfileDryRunAndDeterminismEvidence() throws Exception {
        Phase13GB1AuditSchedule.Schedule schedule = Phase13GB1AuditSchedule.create();
        assertThat(schedule.teamCodes()).containsExactlyElementsOf(
                teams.teamCodes().stream().sorted().toList());
        Phase13GB1AuditSchedule.Fixture fixture = schedule.primaryFixtures().stream()
                .filter(value -> value.blueTeamCode().equals("GEN")
                        && value.redTeamCode().equals("T1"))
                .findFirst().orElseThrow();
        long seed = Phase13GB1AuditSchedule.dryRunSeed(fixture);
        Phase13GB1RealMatchHarness harness = new Phase13GB1RealMatchHarness(
                orchestrator,
                simulators,
                mapper,
                champions,
                identities,
                ratings,
                proficiencies);

        var fabricated = new Phase13GB1AuditSchedule.Fixture(
                fixture.fixtureId(), fixture.fixtureLane(), fixture.pairId(),
                fixture.blueTeamCode(), "KT", fixture.seriesGameNumber(),
                fixture.calibrationSeeds(), fixture.holdoutSeeds());
        assertThatThrownBy(() -> harness.prepareFixture(fabricated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("differs from the frozen 13G-B schedule");

        Phase13GB1RealMatchHarness.PreparedFixture prepared =
                harness.prepareFixture(fixture);
        List<Phase13GB1RealMatchHarness.AuditMatchRun> runs = harness.executeAllProfiles(
                prepared,
                Phase13GB1AuditSchedule.SampleLane.DRY_RUN,
                seed);
        Phase13GB1RealMatchHarness.AuditMatchRun replay = harness.execute(
                prepared,
                Phase13GB1AuditSchedule.SampleLane.DRY_RUN,
                seed,
                SimulationRuntimeProfileId.BASELINE_V1);

        assertPairedFixtureAndProvenance(prepared, runs, replay);
        assertProfileSpecificExecution(runs);
        assertThat(runs).allSatisfy(run -> assertThat(run.integrityDiagnostics().clean()).isTrue());
        assertThat(harness.resourceProvenance().jungleClearGameplayEnabledProfileCount())
                .isEqualTo(51);

        var artifacts = Phase13GB1AuditArtifactWriter.write(
                mapper,
                Path.of("."),
                REPORT_OUTPUT,
                schedule,
                prepared,
                runs,
                replay,
                harness.resourceProvenance());
        Phase13GB1AuditArtifactWriter.write(
                mapper,
                Path.of("."),
                temporaryOutput,
                schedule,
                prepared,
                runs,
                replay,
                harness.resourceProvenance());

        assertThat(artifacts.sameSeedReplayExact()).isTrue();
        assertThat(artifacts.productionSourceTree().fileCount()).isPositive();
        assertThat(artifacts.auditHarnessSourceTree().fileCount()).isPositive();
        for (String file : ARTIFACT_FILES) {
            assertThat(Files.readAllBytes(REPORT_OUTPUT.resolve(file)))
                    .as("deterministic artifact %s", file)
                    .isEqualTo(Files.readAllBytes(temporaryOutput.resolve(file)));
        }
        assertShaManifest(REPORT_OUTPUT);
        assertScopeBoundary(REPORT_OUTPUT.resolve(
                Phase13GB1AuditArtifactWriter.SUMMARY_FILE));
    }

    private static void assertPairedFixtureAndProvenance(
            Phase13GB1RealMatchHarness.PreparedFixture prepared,
            List<Phase13GB1RealMatchHarness.AuditMatchRun> runs,
            Phase13GB1RealMatchHarness.AuditMatchRun replay
    ) {
        assertThat(prepared.productionOrchestrationCount()).isOne();
        assertThat(runs).hasSize(5);
        assertThat(runs.stream().map(Phase13GB1RealMatchHarness.AuditMatchRun::profileId))
                .containsExactlyElementsOf(Phase13GB1RealMatchHarness.AUDIT_PROFILES);
        assertThat(runs.stream().map(
                Phase13GB1RealMatchHarness.AuditMatchRun::draftDecisionHash).distinct())
                .hasSize(1);
        assertThat(runs.stream().map(
                Phase13GB1RealMatchHarness.AuditMatchRun::finalDraftHash).distinct())
                .hasSize(1);
        assertThat(runs.stream().map(
                Phase13GB1RealMatchHarness.AuditMatchRun::finalAssignmentHash).distinct())
                .hasSize(1);
        assertThat(runs.stream().map(
                Phase13GB1RealMatchHarness.AuditMatchRun::resourceProvenanceHash).distinct())
                .hasSize(1);
        assertThat(runs.stream().map(
                Phase13GB1RealMatchHarness.AuditMatchRun::configurationHash))
                .doesNotHaveDuplicates();
        assertThat(replay.replayProvenanceHash())
                .isEqualTo(runs.getFirst().replayProvenanceHash());
        assertThat(replay.timelineHash()).isEqualTo(runs.getFirst().timelineHash());
        assertThat(replay.randomFingerprint()).isEqualTo(runs.getFirst().randomFingerprint());
        assertThat(replay.combatDiagnostics()).isEqualTo(runs.getFirst().combatDiagnostics());
        assertThat(replay.jungleEconomyDiagnostics())
                .isEqualTo(runs.getFirst().jungleEconomyDiagnostics());
        assertThat(replay.jungleTempoDiagnostics())
                .isEqualTo(runs.getFirst().jungleTempoDiagnostics());
        assertThat(runs).allSatisfy(run -> {
            assertThat(run.blueJungle().playerId()).isNotBlank();
            assertThat(run.redJungle().playerId()).isNotBlank();
            assertThat(run.blueJungle().championId()).isNotBlank();
            assertThat(run.redJungle().championId()).isNotBlank();
            assertThat(run.randomFingerprint().randomDrawCount()).isPositive();
        });
    }

    private static void assertProfileSpecificExecution(
            List<Phase13GB1RealMatchHarness.AuditMatchRun> runs
    ) {
        for (int index = 0; index < 3; index++) {
            var run = runs.get(index);
            assertThat(run.jungleEconomyDiagnostics().evaluations()).isZero();
            assertThat(run.jungleEconomyDiagnostics().awardedCs()).isZero();
            assertThat(run.jungleTempoDiagnostics().economyUpdates()).isZero();
        }
        var economy = runs.get(3);
        assertThat(economy.jungleEconomyDiagnostics().evaluations()).isPositive();
        assertThat(economy.jungleEconomyDiagnostics().eligibleOutcomes()).isPositive();
        assertThat(economy.jungleEconomyDiagnostics().awardedCs()).isPositive();
        assertThat(economy.jungleEconomyDiagnostics().awardedGold()).isPositive();
        assertThat(economy.jungleEconomyDiagnostics().awardedExperience()).isPositive();
        assertThat(economy.jungleTempoDiagnostics().economyUpdates()).isZero();

        var tempo = runs.get(4);
        assertThat(tempo.jungleEconomyDiagnostics().evaluations()).isPositive();
        assertThat(tempo.jungleTempoDiagnostics().economyUpdates()).isPositive();
        assertThat(tempo.jungleTempoDiagnostics().actualConsumptions()
                .get(JungleTempoActionType.GANK))
                .isEqualTo(tempo.combatDiagnostics().jungleGankAttempts());
        assertThat(tempo.jungleTempoDiagnostics().actualConsumptions()
                .get(JungleTempoActionType.COUNTER_GANK))
                .isEqualTo(tempo.combatDiagnostics().counterGankAttempts());
    }

    private void assertScopeBoundary(Path summaryFile) throws Exception {
        JsonNode summary = mapper.readTree(summaryFile.toFile());
        assertThat(summary.path("status").asText())
                .isEqualTo("HARNESS_READY_FOR_CALIBRATION");
        assertThat(summary.path("calibrationExecuted").asBoolean()).isFalse();
        assertThat(summary.path("calibrationMatchExecutionCount").asInt()).isZero();
        assertThat(summary.path("holdoutExecuted").asBoolean()).isFalse();
        assertThat(summary.path("holdoutMatchExecutionCount").asInt()).isZero();
        assertThat(summary.path("productionDecision").asText())
                .isEqualTo("NOT_EVALUATED");
        assertThat(summary.path("productionTuningChanged").asBoolean()).isFalse();
        assertThat(summary.path("pairedDryRunMatchExecutionCount").asInt()).isEqualTo(5);
        assertThat(summary.path("determinismReplayExecutionCount").asInt()).isOne();
        assertThat(summary.path("sameSeedReplayExact").asBoolean()).isTrue();
        assertThat(summary.path("engineImplementationVersion").asText())
                .isEqualTo(SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION);
        assertThat(summary.path("hashContracts").path("configurationHashScope").asText())
                .isEqualTo("GAMEPLAY_CONFIGURATION_ONLY_INSTRUMENTATION_EXCLUDED");
        assertThat(summary.path("hashContracts").path("randomTraceHashScope").asText())
                .isEqualTo("OBSERVATIONAL_OUTPUT_NOT_REPLAY_INPUT");
    }

    private static void assertShaManifest(Path directory) throws Exception {
        List<String> lines = Files.readAllLines(
                directory.resolve(Phase13GB1AuditArtifactWriter.SHA_FILE),
                StandardCharsets.UTF_8);
        assertThat(lines).hasSize(6);
        for (String line : lines) {
            String[] fields = line.split("  ", 2);
            assertThat(fields).hasSize(2);
            assertThat(sha256(Files.readAllBytes(directory.resolve(fields[1]))))
                    .isEqualTo(fields[0]);
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
