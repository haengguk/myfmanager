package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChampionRoleScopeApiTest {
    @Autowired MockMvc mvc;

    @Test
    void catalogEndpointSerializesTheCorrectExpandedSupportedPositions() throws Exception {
        JsonNode root = new ObjectMapper().readTree(mvc.perform(get("/api/champions"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(root.get("champions").size()).isEqualTo(173);
        assertPositions(root, "varus", "TOP", "ADC");
        assertPositions(root, "anivia", "TOP", "MID");
        assertPositions(root, "cassiopeia", "MID", "ADC");
        assertPositions(root, "taliyah", "MID", "JUNGLE", "ADC");
        assertPositions(root, "skarner", "JUNGLE");
        assertPositions(root, "rumble", "TOP");
        assertPositions(root, "annie", "MID");
        assertPositions(root, "sylas", "MID");
        assertPositions(root, "syndra", "MID");
    }

    private void assertPositions(JsonNode root, String championId, String... expected) {
        JsonNode champion = StreamSupport.stream(root.get("champions").spliterator(), false)
                .filter(node -> championId.equals(node.get("id").asText()))
                .findFirst()
                .orElseThrow();
        List<String> actual = StreamSupport.stream(champion.get("supportedPositions").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(actual).containsExactlyInAnyOrder(expected);
    }
}
