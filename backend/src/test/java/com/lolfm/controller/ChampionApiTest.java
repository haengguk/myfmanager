package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Autowired ObjectMapper mapper;

    @Test void catalogEndpointReturnsPinnedOrderedFullChampionContract() throws Exception {
        mvc.perform(get("/api/champions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.championPoolVersion").value("full-173-2026-08-v1"))
                .andExpect(jsonPath("$.championBalanceVersion").value("full-173-power-2026-08-v1"))
                .andExpect(jsonPath("$.championPowerProfileVersion").value("full-173-power-2026-08-v1"))
                .andExpect(jsonPath("$.riotDataVersion").value("16.15.1"))
                .andExpect(jsonPath("$.champions.length()").value(173))
                .andExpect(jsonPath("$.champions[0].id").value("renekton"))
                .andExpect(jsonPath("$.champions[6].id").value("lee-sin"))
                .andExpect(jsonPath("$.champions[0].levelCurveId").value("EARLY_DOMINANT"))
                .andExpect(jsonPath("$.champions[0].tags.length()").value(4))
                .andExpect(jsonPath("$.champions[?(@.id == 'galio')].supportedPositions.length()")
                        .value(3))
                .andExpect(jsonPath("$.defaultSelection.red.adc").value("kaisa"));
    }

    @Test void legacyAndExplicitRequestsExposeChampionMetadataAndSnapshots() throws Exception {
        mvc.perform(post("/api/matches/simulate").contentType(MediaType.APPLICATION_JSON).content("{\"seed\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.championMetadata.selectionMode").value("DEFAULT_FIXED"))
                .andExpect(jsonPath("$.championMetadata.championPowerEnabled").value(true))
                .andExpect(jsonPath("$.championMetadata.championPowerProfileVersion").value("full-173-power-2026-08-v1"))
                .andExpect(jsonPath("$.championMetadata.blue.top.powerProfile.profileVersion").value("full-173-power-2026-08-v1"))
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

    @Test
    void actualKillEventSerializesDisplayAndStableParticipantIdentitiesAdditively() throws Exception {
        String body = mvc.perform(post("/api/matches/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seed\":7}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode kill = null;
        for (JsonNode event : mapper.readTree(body).path("timeline").path("events")) {
            if ("KILL".equals(event.path("type").asText())) {
                kill = event;
                break;
            }
        }

        assertThat(kill).as("fixed seed must produce an actual KILL event").isNotNull();
        assertThat(kill.has("killer")).isTrue();
        assertThat(kill.has("victim")).isTrue();
        assertThat(kill.has("assists")).isTrue();
        assertThat(kill.has("killerPlayerId")).isTrue();
        assertThat(kill.has("victimPlayerId")).isTrue();
        assertThat(kill.has("assistPlayerIds")).isTrue();
        assertThat(kill.path("killer").isTextual()).isTrue();
        assertThat(kill.path("victim").isTextual()).isTrue();
        assertThat(kill.path("assists").isArray()).isTrue();
        assertThat(kill.path("assistPlayerIds").isArray()).isTrue();
        assertThat(kill.path("killerPlayerId").asText()).startsWith("player-fixture-");
        assertThat(kill.path("victimPlayerId").asText()).startsWith("player-fixture-");
        assertThat(kill.path("killer").asText()).isNotEqualTo(kill.path("killerPlayerId").asText());
        assertThat(kill.path("victim").asText()).isNotEqualTo(kill.path("victimPlayerId").asText());
    }

    private String validRequest() { return "{\"seed\":7,\"championSelection\":{\"blue\":{\"top\":\"renekton\",\"jgl\":\"sejuani\",\"mid\":\"azir\",\"adc\":\"jinx\",\"sup\":\"nautilus\"},\"red\":{\"top\":\"jax\",\"jgl\":\"lee-sin\",\"mid\":\"ahri\",\"adc\":\"kaisa\",\"sup\":\"rakan\"}}}"; }
}
