package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.draft.PlayerDraftControlPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerDraftApiV1ControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void redControlledStartAutoAdvancesAndExposesFullLegalPoolSeparateFromAdvice()
            throws Exception {
        JsonNode session = start("RED", "73");

        assertThat(session.path("schemaVersion").asText())
                .isEqualTo("PLAYER_DRAFT_SESSION_V1");
        assertThat(session.path("revision").asLong()).isZero();
        assertThat(session.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(session.path("seriesGameNumber").asInt()).isOne();
        assertThat(session.path("state").path("hardFearlessExclusions")).isEmpty();
        assertThat(session.path("decisions")).hasSize(1);
        assertThat(session.path("decisions").get(0).path("authority").asText())
                .isEqualTo("AI");
        assertThat(session.path("decisions").get(0).path("autoSelectionTrace")
                .path("policyId").asText()).isEqualTo("AUTO_DRAFT_VARIETY_V1");
        assertThat(session.path("currentTurn").path("turn").asInt()).isEqualTo(2);
        assertThat(session.path("currentTurn").path("teamSide").asText()).isEqualTo("RED");
        assertThat(session.path("selectableChampions").size())
                .isGreaterThan(session.path("advisoryRecommendations").size());
        assertThat(session.path("advisoryRecommendations")).allSatisfy(value ->
                assertThat(value.path("advisoryOnly").asBoolean()).isTrue());
        assertThat(session.path("playerControlPolicy").path("policyHash").asText())
                .isEqualTo(PlayerDraftControlPolicy.POLICY_HASH);
    }

    @Test
    void actionIsRevisionAtomicIdempotentAndOnlyOneConcurrentSubmitSucceeds()
            throws Exception {
        JsonNode started = start("BLUE", "74");
        String sessionId = started.path("sessionId").asText();
        String champion = firstSelectable(started);
        String body = actionBody(0, "same-action", champion);

        JsonNode first = action(sessionId, body, 200);
        JsonNode retry = action(sessionId, body, 200);
        assertThat(retry).isEqualTo(first);
        assertThat(first.path("revision").asLong()).isEqualTo(1);
        assertThat(first.path("decisions").get(0).path("authority").asText())
                .isEqualTo("PLAYER");
        assertThat(first.path("decisions").get(0).path("playerSelectionEvidence")
                .path("clientActionId").asText()).isEqualTo("same-action");

        String differentChampion = first.path("selectableChampions").get(0)
                .path("champion").path("championId").asText();
        action(sessionId, actionBody(0, "same-action", differentChampion), 409);
        action(sessionId, actionBody(0, "stale-action", differentChampion), 409);

        long revision = first.path("revision").asLong();
        String nextChampion = firstSelectable(first);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Integer>> calls = List.of(
                    () -> actionStatus(sessionId,
                            actionBody(revision, "concurrent-a", nextChampion)),
                    () -> actionStatus(sessionId,
                            actionBody(revision, "concurrent-b", nextChampion)));
            List<Integer> statuses = new ArrayList<>();
            for (var future : executor.invokeAll(calls)) statuses.add(future.get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }
        JsonNode current = getSession(sessionId);
        assertThat(current.path("revision").asLong()).isEqualTo(revision + 1);
    }

    @Test
    void invalidSelectionAndParsingErrorsHaveZeroMutationAndSanitizedContracts()
            throws Exception {
        JsonNode started = start("BLUE", "75");
        String sessionId = started.path("sessionId").asText();
        String beforeHash = started.path("stateHash").asText();

        JsonNode unknown = action(sessionId,
                actionBody(0, "unknown", "not-a-real-champion"), 400);
        assertThat(unknown.path("schemaVersion").asText())
                .isEqualTo("PLAYER_DRAFT_API_ERROR_V1");
        assertThat(unknown.path("code").asText()).isEqualTo("UNKNOWN_CHAMPION");
        JsonNode after = getSession(sessionId);
        assertThat(after.path("revision").asLong()).isZero();
        assertThat(after.path("stateHash").asText()).isEqualTo(beforeHash);

        mvc.perform(post("/api/v1/player-drafts/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.schemaVersion").value("PLAYER_DRAFT_API_ERROR_V1"))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(get("/api/v1/player-drafts/sessions/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_ID"));
        mvc.perform(get("/api/v1/player-drafts/sessions/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_DRAFT_SESSION_NOT_FOUND"));
    }

    @Test
    void completionDoesNotSimulateAndExplicitSimulationUsesAcceptedProductionV9Exactly()
            throws Exception {
        JsonNode session = start("BLUE", "76");
        String sessionId = session.path("sessionId").asText();
        int action = 0;
        while (!"COMPLETED".equals(session.path("status").asText())) {
            session = action(sessionId, actionBody(
                    session.path("revision").asLong(), "complete-" + action++,
                    firstSelectable(session)), 200);
        }

        assertThat(action).isEqualTo(10);
        assertThat(session.path("completedDraft").path("finalAssignments")).hasSize(10);
        assertThat(session.path("completedDraft").path("controlEvidenceHash").asText())
                .matches("[0-9a-f]{64}");
        assertThat(session.has("match")).isFalse();
        mvc.perform(post("/api/v1/player-drafts/sessions/" + sessionId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody(session.path("revision").asLong(),
                                "after-complete", "ahri")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAYER_DRAFT_ALREADY_COMPLETE"));

        String simulateBody = """
                {"schemaVersion":"PLAYER_DRAFT_SIMULATE_REQUEST_V1"}
                """;
        JsonNode first = simulate(sessionId, simulateBody);
        JsonNode replay = simulate(sessionId, simulateBody);

        assertThat(replay).isEqualTo(first);
        assertThat(first.path("schemaVersion").asText())
                .isEqualTo("PLAYER_DRAFT_MATCH_RESPONSE_V1");
        assertThat(first.path("session").path("status").asText()).isEqualTo("SIMULATED");
        assertThat(first.path("match").path("productionPolicy").path("policyId").asText())
                .isEqualTo(MatchEngineV1Policy.POLICY_ID);
        assertThat(first.path("match").path("productionPolicy")
                .path("runtimeProfileId").asText())
                .isEqualTo("PRODUCTION_MATCHUP_COMPOSITION_V1");
        assertThat(first.path("match").path("integrity")
                .path("engineImplementationVersion").asText())
                .isEqualTo("MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9");
        assertThat(first.path("match").path("integrity")
                .path("controlPolicyId").asText())
                .isEqualTo(PlayerDraftControlPolicy.POLICY_ID);
        assertThat(first.path("match").path("draft").path("decisions")).hasSize(20);
        assertThat(first.path("match").path("result").path("players")).hasSize(10);
        assertThat(first.path("match").path("timeline").path("events")).isNotEmpty();
        assertThat(first.path("match").path("integrity").path("randomFingerprint")
                .path("randomTraceHash").asText()).matches("[0-9a-f]{64}");
    }

    @Test
    void cancelIsExplicitAndBlocksFurtherMutation() throws Exception {
        JsonNode started = start("BLUE", "77");
        String sessionId = started.path("sessionId").asText();
        mvc.perform(delete("/api/v1/player-drafts/sessions/" + sessionId))
                .andExpect(status().isNoContent());
        JsonNode cancelled = getSession(sessionId);
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("currentTurn").isNull()).isTrue();
        assertThat(cancelled.path("selectableChampions")).isEmpty();
        assertThat(cancelled.path("unavailableChampions")).isEmpty();
        assertThat(cancelled.path("advisoryRecommendations")).isEmpty();
        assertThat(cancelled.path("selectableSetIdentity").isNull()).isTrue();
        action(sessionId, actionBody(0, "cancelled", firstSelectable(started)), 409);
    }

    private JsonNode start(String controlledSide, String seed) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/player-drafts/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":"PLAYER_DRAFT_START_REQUEST_V1",
                                 "blueTeamCode":"GEN","redTeamCode":"T1",
                                 "controlledSide":"%s","seed":"%s"}
                                """.formatted(controlledSide, seed)))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getSession(String sessionId) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/player-drafts/sessions/" + sessionId))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode action(String sessionId, String body, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/player-drafts/sessions/"
                        + sessionId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private int actionStatus(String sessionId, String body) throws Exception {
        return mvc.perform(post("/api/v1/player-drafts/sessions/"
                        + sessionId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode simulate(String sessionId, String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/player-drafts/sessions/"
                        + sessionId + "/simulate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private static String actionBody(long revision, String actionId, String championId) {
        return """
                {"schemaVersion":"PLAYER_DRAFT_ACTION_REQUEST_V1",
                 "expectedRevision":%d,"clientActionId":"%s","championId":"%s"}
                """.formatted(revision, actionId, championId);
    }

    private static String firstSelectable(JsonNode session) {
        return session.path("selectableChampions").get(0)
                .path("champion").path("championId").asText();
    }
}
