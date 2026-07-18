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
                .andExpect(jsonPath("$.championBalanceVersion").value("neutral-foundation-v1"))
                .andExpect(jsonPath("$.riotDataVersion").value("16.14.1"))
                .andExpect(jsonPath("$.champions.length()").value(30))
                .andExpect(jsonPath("$.champions[0].id").value("renekton"))
                .andExpect(jsonPath("$.champions[6].id").value("lee-sin"))
                .andExpect(jsonPath("$.defaultSelection.red.adc").value("kaisa"));
    }

    @Test void legacyAndExplicitRequestsExposeChampionMetadataAndSnapshots() throws Exception {
        mvc.perform(post("/api/matches/simulate").contentType(MediaType.APPLICATION_JSON).content("{\"seed\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.championMetadata.selectionMode").value("DEFAULT_FIXED"))
                .andExpect(jsonPath("$.timeline.snapshots[0].playerSnapshots[0].champion.id").isString());
        mvc.perform(post("/api/matches/simulate").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.championMetadata.selectionMode").value("EXPLICIT"))
                .andExpect(jsonPath("$.championMetadata.blue.top.id").value("renekton"));
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
