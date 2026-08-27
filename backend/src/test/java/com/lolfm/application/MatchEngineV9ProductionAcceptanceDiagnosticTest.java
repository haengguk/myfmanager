package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-production-acceptance")
class MatchEngineV9ProductionAcceptanceDiagnosticTest {
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired LckTeamAssembler teams;
    @Autowired ChampionProficiencyCatalog proficiencies;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired SimulationProvenanceService provenance;

    @Test
    void runsBoundedFixedDraftProductSanityAndWritesCleanRawCandidate() throws Exception {
        Path backendRoot = Path.of(System.getProperty(
                "matchEngineV9AcceptanceBackendRoot", "."));
        Path output = Path.of(System.getProperty("matchEngineV9AcceptanceRawOutput",
                "build/reports/match-engine-v9-production-acceptance-raw-candidate"));
        var result = new MatchEngineV9ProductionAcceptanceRunner(
                mapper, champions, teams, proficiencies, simulators, provenance)
                .run(backendRoot, output);
        assertThat(result.coreSimulationCount()).isEqualTo(1_200);
        assertThat(result.pairedCellCount()).isEqualTo(400);
        assertThat(result.additionalSimulationCount()).isEqualTo(10);
        assertThat(result.correctness().clean()).isTrue();
    }
}
