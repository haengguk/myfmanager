package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1Output;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.RealDraftMatchOrchestrator;
import com.lolfm.application.RealMatchApiV1ResponseMapper;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.simulator.GameEndReason;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealMatchApiV1ControllerTest {
    private static final String FIXED_REQUEST = """
            {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
             "blueTeamCode":"GEN","redTeamCode":"T1","seed":"73"}
            """;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired RealMatchApiV1ResponseMapper responseMapper;

    private JsonNode first;
    private JsonNode second;

    @BeforeAll
    void executeTwoIndependentHttpGames() throws Exception {
        first = simulate(FIXED_REQUEST);
        second = simulate(FIXED_REQUEST);
    }

    @Test
    void optionsExposeCanonicalTenTeamFiftyPlayerContractWithoutGameplayProfiles() throws Exception {
        JsonNode root = mapper.readTree(mvc.perform(get("/api/v1/real-matches/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("REAL_MATCH_OPTIONS_V1"))
                .andExpect(jsonPath("$.matchEngineContract").value("MATCH_ENGINE_CONTRACT_V1"))
                .andExpect(jsonPath("$.seedPolicy.required").value(true))
                .andExpect(jsonPath("$.seedPolicy.encoding")
                        .value("SIGNED_INT64_DECIMAL_STRING"))
                .andExpect(jsonPath("$.teams.length()").value(10))
                .andReturn().getResponse().getContentAsString());

        List<String> codes = new ArrayList<>();
        HashSet<String> playerIds = new HashSet<>();
        for (JsonNode team : root.path("teams")) {
            codes.add(team.path("teamCode").asText());
            assertThat(team.path("lineup")).hasSize(5);
            List<String> positions = new ArrayList<>();
            for (JsonNode player : team.path("lineup")) {
                playerIds.add(player.path("playerId").asText());
                positions.add(player.path("position").asText());
                assertThat(iterableFieldNames(player))
                        .containsExactlyInAnyOrder("playerId", "nickname", "position");
            }
            assertThat(positions).containsExactly("TOP", "JUNGLE", "MID", "ADC", "SUPPORT");
        }
        assertThat(codes).isSorted().doesNotHaveDuplicates();
        assertThat(playerIds).hasSize(50);
        assertThat(root.path("productionPolicy").path("policyId").asText())
                .isEqualTo(MatchEngineV1Policy.POLICY_ID);
        assertThat(root.path("productionPolicy").path("policyHash").asText())
                .isEqualTo(MatchEngineV1Policy.authoritative().policyHash());
        assertThat(root.path("productionPolicy").path("runtimeProfileId").asText())
                .isEqualTo("BASELINE_V1");
        assertThat(root.path("productionPolicy").path("economyCandidateActivation").asBoolean())
                .isFalse();
        assertThat(root.path("productionPolicy").path("tempoCandidateActivation").asBoolean())
                .isFalse();
    }

    @Test
    void strictHttpValidationReturnsScopedStructuredErrors() throws Exception {
        mvc.perform(post("/api/v1/real-matches/simulate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.schemaVersion").value("REAL_MATCH_API_ERROR_V1"))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post("/api/v1/real-matches/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post("/api/v1/real-matches/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
                                 "blueTeamCode":"UNKNOWN","redTeamCode":"T1","seed":"73"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_TEAM"))
                .andExpect(jsonPath("$.field").value("blueTeamCode"));
        mvc.perform(post("/api/v1/real-matches/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
                                 "blueTeamCode":"GEN","redTeamCode":"T1","seed":73}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEED"));
        mvc.perform(post("/api/v1/real-matches/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
                                 "blueTeamCode":"GEN","redTeamCode":"T1","seed":"73",
                                 "jungleTempoEnabled":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_REQUEST_FIELD"))
                .andExpect(jsonPath("$.field").value("jungleTempoEnabled"));
    }

    @Test
    void realGenT1RequestReturnsAutomaticDraftStructuredResultTimelineAndIntegrity() {
        assertThat(first.path("schemaVersion").asText()).isEqualTo("REAL_MATCH_RESPONSE_V1");
        assertThat(first.path("seed").isTextual()).isTrue();
        assertThat(first.path("seed").asText()).isEqualTo("73");
        assertThat(first.path("teams")).hasSize(2);
        assertThat(first.path("teams").get(0).path("teamSide").asText()).isEqualTo("BLUE");
        assertThat(first.path("teams").get(0).path("teamCode").asText()).isEqualTo("GEN");
        assertThat(first.path("teams").get(1).path("teamSide").asText()).isEqualTo("RED");
        assertThat(first.path("teams").get(1).path("teamCode").asText()).isEqualTo("T1");
        assertThat(first.path("teams").get(0).path("lineup")).hasSize(5);
        assertThat(first.path("teams").get(1).path("lineup")).hasSize(5);
        assertThat(first.path("teams").get(0).path("lineup").get(0)
                .path("playerId").isTextual()).isTrue();
        assertThat(first.path("teams").get(0).path("lineup").get(0)
                .path("championId").isTextual()).isTrue();

        assertThat(first.path("draft").path("seriesGameNumber").asInt()).isEqualTo(1);
        assertThat(first.path("draft").path("hardFearlessExclusionsBeforeDraft")).isEmpty();
        assertThat(first.path("draft").path("decisions")).hasSize(20);
        assertThat(first.path("draft").path("finalAssignments")).hasSize(10);
        assertThat(first.path("result").path("players")).hasSize(10);
        assertThat(first.path("timeline").path("events")).isNotEmpty();
        assertThat(first.path("timeline").path("snapshots")).isNotEmpty();
        assertThat(first.path("integrity").path("policyId").asText())
                .isEqualTo(MatchEngineV1Policy.POLICY_ID);
        assertThat(first.path("integrity").path("runtimeProfileId").asText())
                .isEqualTo("BASELINE_V1");
        assertThat(first.path("integrity").path("outputHash").asText())
                .matches("[0-9a-f]{64}");
        assertThat(first.path("integrity").path("randomFingerprint")
                .path("randomTraceHash").asText()).matches("[0-9a-f]{64}");
        assertThat(first.path("integrity").path("diagnosticsExcludedFromGameplayIdentity")
                .asBoolean()).isTrue();
    }

    @Test
    void repeatedHttpRequestsAreExactFreshGameOneExecutions() {
        assertThat(second).isEqualTo(first);
        assertThat(second.path("draft").path("seriesGameNumber").asInt()).isEqualTo(1);
        assertThat(second.path("draft").path("hardFearlessExclusionsBeforeDraft")).isEmpty();
        assertThat(second.path("draft").path("finalDraftHash"))
                .isEqualTo(first.path("draft").path("finalDraftHash"));
        assertThat(second.path("timeline")).isEqualTo(first.path("timeline"));
        assertThat(second.path("integrity").path("outputHash"))
                .isEqualTo(first.path("integrity").path("outputHash"));
        assertThat(second.path("integrity").path("randomFingerprint"))
                .isEqualTo(first.path("integrity").path("randomFingerprint"));
    }

    @Test
    void directOrchestratorAndHttpServiceProjectionHaveExactParity() {
        MatchEngineV1Output direct = orchestrator.orchestrateV1("GEN", "T1", 73L);
        JsonNode expected = mapper.valueToTree(responseMapper.response(direct));

        assertJsonEquals(expected, first, "$");
        assertThat(first.path("matchIdentity").asText()).isEqualTo(direct.matchIdentity());
        assertThat(first.path("result").path("winner").asText())
                .isEqualTo(direct.resultSummary().winner().name());
        assertThat(first.path("result").path("durationSeconds").asInt())
                .isEqualTo(direct.resultSummary().durationSeconds());
        assertThat(first.path("integrity").path("inputHash").asText())
                .isEqualTo(direct.inputHash());
        assertThat(first.path("integrity").path("replayProvenanceHash").asText())
                .isEqualTo(direct.executionProvenance().replayProvenanceHash());
        assertThat(first.path("integrity").path("structuredTimelineHash").asText())
                .isEqualTo(direct.structuredTimelineHash());
    }

    @Test
    void timeoutWinnerSerializesAsExplicitNullableField() {
        RealMatchApiV1Dtos.Result source = mapper.convertValue(
                first.path("result"), RealMatchApiV1Dtos.Result.class);
        RealMatchApiV1Dtos.Result timeout = new RealMatchApiV1Dtos.Result(
                source.schemaVersion(), null, GameEndReason.SIMULATION_TIMEOUT,
                source.durationSeconds(), source.teams(), source.players(),
                source.finalDraftHash(), source.finalAssignmentHash(),
                source.runtimeProfileId(), source.configurationHash(),
                source.resourceProvenanceHash(), source.replayProvenanceHash());

        JsonNode json = mapper.valueToTree(timeout);
        assertThat(json.has("winner")).isTrue();
        assertThat(json.path("winner").isNull()).isTrue();
        assertThat(json.path("endReason").asText()).isEqualTo("SIMULATION_TIMEOUT");
    }

    private JsonNode simulate(String request) throws Exception {
        String body = mvc.perform(post("/api/v1/real-matches/simulate")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private static List<String> iterableFieldNames(JsonNode node) {
        ArrayList<String> result = new ArrayList<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static void assertJsonEquals(JsonNode expected, JsonNode actual, String path) {
        if (expected.equals(actual)) return;
        if (expected.isNumber() && actual.isNumber()
                && expected.decimalValue().compareTo(actual.decimalValue()) == 0) {
            return;
        }
        if (expected.isObject() && actual.isObject()) {
            assertThat(iterableFieldNames(actual)).as(path + " field names")
                    .containsExactlyInAnyOrderElementsOf(iterableFieldNames(expected));
            expected.fieldNames().forEachRemaining(field ->
                    assertJsonEquals(expected.get(field), actual.get(field), path + "." + field));
            return;
        }
        if (expected.isArray() && actual.isArray()) {
            assertThat(actual.size()).as(path + " array size").isEqualTo(expected.size());
            for (int index = 0; index < expected.size(); index++) {
                assertJsonEquals(expected.get(index), actual.get(index), path + "[" + index + "]");
            }
            return;
        }
        assertThat(actual).as(path).isEqualTo(expected);
    }
}
