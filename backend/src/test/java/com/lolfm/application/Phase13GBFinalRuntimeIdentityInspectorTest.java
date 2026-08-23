package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.controller.MatchController;
import com.lolfm.factory.DummyDataFactory;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.MatchSimulator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class Phase13GBFinalRuntimeIdentityInspectorTest {
    @Autowired ObjectMapper mapper;
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired MatchSimulator autowiredSimulator;
    @Autowired ConfiguredMatchSimulatorFactory configuredFactory;
    @Autowired ChampionCatalog champions;
    @Autowired DummyDataFactory dummyDataFactory;
    @Autowired MatchController controller;
    @Autowired MockMvc mvc;

    @Test
    void productionRegistryAndActualWiringProduceCanonicalRuntimeEvidence() throws Exception {
        var evidence = inspector().inspect(Path.of("."));
        Path output = Path.of("build", "reports", "final-13g-b-runtime-identity");
        String rawSha = Phase13GBFinalRuntimeIdentityEvidence.writeBundle(output, evidence);
        var replay = Phase13GBFinalRuntimeIdentityEvidence.readBundle(output, true);

        assertThat(replay).isEqualTo(evidence);
        assertThat(evidence.identityValues())
                .containsEntry("retainedRuntimeProfileId", "BASELINE_V1")
                .containsEntry("realDraftDefaultVsExplicitReplayIdentityExact", "true")
                .containsEntry("springAutowiredTimelineExact", "true")
                .containsEntry("httpInjectedAutowiredSimulatorExact", "true")
                .containsEntry("httpInputRosterSource", "DUMMY_DATA_FACTORY")
                .containsEntry(
                        "lowLevelProductionDefaultsAuthoritativeApplicationRuntimeDefault",
                        "false")
                .containsEntry("lowLevelProductionDefaultsChampionMatchupMode", "GEOMETRIC_V2")
                .containsEntry(
                        "lowLevelProductionDefaultsTeamCompositionGameplayMode",
                        "PRODUCTION_V2")
                .containsEntry("automaticTuningPerformed", "false")
                .containsEntry("holdoutRerunPerformed", "false");
        assertThat(evidence.runtimeIdentityHash()).matches("[0-9a-f]{64}");
        assertThat(evidence.runtimeIdentityHash())
                .isEqualTo(Phase13GBFinalRuntimeIdentityEvidence.EXPECTED_RUNTIME_IDENTITY_HASH);
        assertThat(rawSha)
                .isEqualTo(Phase13GBFinalRuntimeIdentityEvidence.EXPECTED_EVIDENCE_RAW_SHA256);
    }

    @Test
    void actualPostRouteRemainsAutowiredBaselineSimulatorWithDummyRoster() throws Exception {
        mvc.perform(post("/api/matches/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seed\":2026082303}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seed").value(2026082303L))
                .andExpect(jsonPath("$.blueTeam.name").value("블루 미라지"))
                .andExpect(jsonPath("$.blueTeam.players[0].name").value("Atlas"))
                .andExpect(jsonPath("$.redTeam.name").value("레드 템페스트"))
                .andExpect(jsonPath("$.redTeam.players[0].name").value("Blade"))
                .andExpect(jsonPath("$.timeline.winner").isString());
    }

    private Phase13GBFinalRuntimeIdentityInspector inspector() {
        return new Phase13GBFinalRuntimeIdentityInspector(
                mapper, orchestrator, autowiredSimulator, configuredFactory,
                champions, dummyDataFactory, controller);
    }
}
