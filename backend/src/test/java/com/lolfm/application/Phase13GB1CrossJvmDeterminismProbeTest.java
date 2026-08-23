package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Writes one canonical B1 artifact set for comparison with another test-worker JVM. */
@SpringBootTest
@Tag("diagnostic")
@Tag("phase13g-b1-cross-jvm-probe")
class Phase13GB1CrossJvmDeterminismProbeTest {
    static final String OUTPUT_PROPERTY = "lolfm.phase13gb1.crossJvmOutput";

    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired PlayerIdentityCatalog identities;
    @Autowired PlayerRatingCatalog ratings;
    @Autowired ChampionProficiencyCatalog proficiencies;

    @Test
    void writesCanonicalArtifactFromThisFreshJvm() throws Exception {
        String configuredOutput = System.getProperty(OUTPUT_PROPERTY);
        assertThat(configuredOutput).as(OUTPUT_PROPERTY).isNotBlank();
        var schedule = Phase13GB1AuditSchedule.create();
        var fixture = schedule.primaryFixtures().stream()
                .filter(value -> value.blueTeamCode().equals("GEN")
                        && value.redTeamCode().equals("T1"))
                .findFirst().orElseThrow();
        long seed = Phase13GB1AuditSchedule.dryRunSeed(fixture);
        var harness = new Phase13GB1RealMatchHarness(
                orchestrator,
                simulators,
                mapper,
                champions,
                identities,
                ratings,
                proficiencies);
        var prepared = harness.prepareFixture(fixture);
        var runs = harness.executeAllProfiles(
                prepared, Phase13GB1AuditSchedule.SampleLane.DRY_RUN, seed);
        var replay = harness.execute(
                prepared,
                Phase13GB1AuditSchedule.SampleLane.DRY_RUN,
                seed,
                SimulationRuntimeProfileId.BASELINE_V1);

        assertThat(runs).allSatisfy(run -> {
            assertThat(run.integrityDiagnostics().clean()).isTrue();
            assertThat(run.engineImplementationVersion())
                    .isEqualTo(SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION);
        });
        Phase13GB1AuditArtifactWriter.write(
                mapper,
                Path.of("."),
                Path.of(configuredOutput),
                schedule,
                prepared,
                runs,
                replay,
                harness.resourceProvenance());
    }
}
