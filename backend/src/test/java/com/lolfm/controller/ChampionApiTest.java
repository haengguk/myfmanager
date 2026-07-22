package com.lolfm.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChampionApiTest {
    @Autowired MockMvc mvc;

    @Test void catalogEndpointReturnsPinnedOrderedThirtyChampionContract() throws Exception {
        mvc.perform(get("/api/champions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.championPoolVersion").value("initial-30-v1"))
                .andExpect(jsonPath("$.championBalanceVersion").value("initial-30-power-v1"))
                .andExpect(jsonPath("$.championPowerProfileVersion").value("initial-30-power-v1"))
                .andExpect(jsonPath("$.riotDataVersion").value("16.14.1"))
                .andExpect(jsonPath("$.champions.length()").value(30))
                .andExpect(jsonPath("$.champions[0].id").value("renekton"))
                .andExpect(jsonPath("$.champions[6].id").value("lee-sin"))
                .andExpect(jsonPath("$.champions[0].levelCurveId").value("EARLY_DOMINANT"))
                .andExpect(jsonPath("$.champions[0].tags.length()").value(4))
                .andExpect(jsonPath("$.defaultSelection.red.adc").value("kaisa"));
    }

    @Test void legacyAndExplicitRequestsExposeChampionMetadataAndSnapshots() throws Exception {
        mvc.perform(post("/api/matches/simulate").contentType(MediaType.APPLICATION_JSON).content("{\"seed\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.championMetadata.selectionMode").value("DEFAULT_FIXED"))
                .andExpect(jsonPath("$.championMetadata.championPowerEnabled").value(true))
                .andExpect(jsonPath("$.championMetadata.championPowerProfileVersion").value("initial-30-power-v1"))
                .andExpect(jsonPath("$.championMetadata.blue.top.powerProfile.profileVersion").value("initial-30-power-v1"))
                .andExpect(jsonPath("$.championMetadata.blue.top.powerProfile.currentLevelModifier").value(0.18))
                .andExpect(jsonPath("$.championMetadata.blue.top.powerProfile.currentItemModifier").value(0.0))
                .andExpect(jsonPath("$.timeline.snapshots[0].playerSnapshots[0].champion.id").isString());
        mvc.perform(post("/api/matches/simulate").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.championMetadata.selectionMode").value("EXPLICIT"))
                .andExpect(jsonPath("$.championMetadata.blue.top.id").value("renekton"))
                .andExpect(jsonPath("$.championMetadata.blue.top.powerProfile.levelCurveId").value("EARLY_DOMINANT"))
                .andExpect(jsonPath("$.championMetadata.blue.top.powerProfile.itemCurveId").value("EARLY_ONE_CORE"));
    }

    @Test void invalidSelectionsReturnStructured400Never500() throws Exception {
        mvc.perform(post("/api/matches/simulate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seed\":7,\"championSelection\":{\"blue\":{\"top\":\"unknown\",\"jgl\":\"sejuani\",\"mid\":\"azir\",\"adc\":\"jinx\",\"sup\":\"nautilus\"},\"red\":{\"top\":\"jax\",\"jgl\":\"lee-sin\",\"mid\":\"ahri\",\"adc\":\"kaisa\",\"sup\":\"rakan\"}}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_CHAMPION"))
                .andExpect(jsonPath("$.field").value("championSelection.blue.top"))
                .andExpect(jsonPath("$.championId").value("unknown"));
        mvc.perform(post("/api/matches/simulate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seed\":7,\"championSelection\":{\"blue\":null,\"red\":null}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHAMPION_SELECTION_MISSING"));
    }

    private String validRequest() { return "{\"seed\":7,\"championSelection\":{\"blue\":{\"top\":\"renekton\",\"jgl\":\"sejuani\",\"mid\":\"azir\",\"adc\":\"jinx\",\"sup\":\"nautilus\"},\"red\":{\"top\":\"jax\",\"jgl\":\"lee-sin\",\"mid\":\"ahri\",\"adc\":\"kaisa\",\"sup\":\"rakan\"}}}"; }
}
