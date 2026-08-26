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

@SpringBootTest
class CompositionV9ApplicationCausalityDiagnosticTest {
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired PlayerIdentityCatalog identities;
    @Autowired PlayerRatingCatalog ratings;
    @Autowired ChampionProficiencyCatalog proficiencies;

    @Test
    @Tag("composition-v9-application-causality-freeze")
    void freezeContractBeforeFirstExecution() throws Exception {
        var result = runner().freeze(backendRoot(), output());
        assertThat(result.overlapAudit().clean()).isTrue();
        assertThat(result.overlapAudit().freshSeedCount()).isEqualTo(400);
    }

    @Test
    @Tag("composition-v9-application-causality-finalize")
    void finalizeAuthenticatedArtifacts() throws Exception {
        var result = runner().finalizeArtifacts(backendRoot(), output());
        assertThat(result.matchRowCount()).isEqualTo(800);
        assertThat(result.pairedComparisonCount()).isEqualTo(400);
        assertThat(result.totalSimulationCount()).isEqualTo(1100);
    }

    private CompositionV9ApplicationCausalityRunner runner() {
        return new CompositionV9ApplicationCausalityRunner(orchestrator, simulators, mapper,
                champions, identities, ratings, proficiencies);
    }

    private static Path backendRoot() { return Path.of(".").toAbsolutePath().normalize(); }
    private static Path output() { return CompositionV9ApplicationCausalityRunner.OUTPUT; }
}
