package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void historicalBaselineInspectorCannotBeReusedAsTheActivatedProductionOracle() {
        assertThatThrownBy(() -> inspector().inspect(Path.of(".")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RealDraft default/explicit execution provenance mismatch");
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
