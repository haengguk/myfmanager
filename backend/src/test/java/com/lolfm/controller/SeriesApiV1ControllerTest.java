package com.lolfm.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class SeriesApiV1ControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void createIsStrictDeterministicAndExactCommandReplayUses200() throws Exception {
        String body = createBody("73", "api-create");
        String first = mvc.perform(post("/api/v1/series")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion").value("SERIES_VIEW_V1"))
                .andExpect(jsonPath("$.revision").value(0))
                .andExpect(jsonPath("$.currentGameNumber").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode firstJson = mapper.readTree(first);
        mvc.perform(post("/api/v1/series")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seriesId").value(
                        firstJson.path("seriesId").asText()));

        mvc.perform(post("/api/v1/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("+73", "bad-seed")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SERIES_INVALID_ROOT_SEED"));
        mvc.perform(post("/api/v1/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("73", "unknown")
                                .replace("\"clientCommandId\":\"unknown\"",
                                        "\"clientCommandId\":\"unknown\",\"winner\":\"GEN\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(
                        "SERIES_UNSUPPORTED_REQUEST_FIELD"));
    }

    @Test
    void cancelUsesExactRevisionAndReturnsEmpty204() throws Exception {
        JsonNode created = mapper.readTree(mvc.perform(post("/api/v1/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("74", "cancel-create")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(delete("/api/v1/series/" + created.path("seriesId").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":"SERIES_CANCEL_REQUEST_V1",
                                 "expectedRevision":0,"clientCommandId":"cancel-series"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void allSeriesRoutesExposeParentBoundDraftLifecycleAndStructuredConflicts()
            throws Exception {
        JsonNode created = mapper.readTree(mvc.perform(post("/api/v1/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("75", "routes-create")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String seriesId = created.path("seriesId").asText();

        mvc.perform(get("/api/v1/series/" + seriesId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedCommands[0]")
                        .value("CREATE_DRAFT_SESSION"));

        String createDraftBody = """
                {"schemaVersion":"SERIES_DRAFT_SESSION_CREATE_REQUEST_V1",
                 "expectedRevision":0,"clientCommandId":"routes-draft"}
                """;
        String draftBody = mvc.perform(post("/api/v1/series/" + seriesId
                        + "/games/current/draft-session")
                        .contentType(MediaType.APPLICATION_JSON).content(createDraftBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.series.revision").value(1))
                .andExpect(jsonPath("$.draftSession.binding.seriesId").value(seriesId))
                .andExpect(jsonPath("$.draftSession.binding.gameNumber").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode draft = mapper.readTree(draftBody);
        String childId = draft.at("/draftSession/session/sessionId").asText();
        String championId = draft.at(
                "/draftSession/session/selectableChampions/0/champion/championId").asText();

        mvc.perform(post("/api/v1/series/" + seriesId
                        + "/games/current/draft-session")
                        .contentType(MediaType.APPLICATION_JSON).content(createDraftBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.series.revision").value(1))
                .andExpect(jsonPath("$.draftSession.session.sessionId").value(childId));
        mvc.perform(post("/api/v1/series/" + seriesId
                        + "/games/current/draft-session")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"schemaVersion":"SERIES_DRAFT_SESSION_CREATE_REQUEST_V1",
                                 "expectedRevision":1,"clientCommandId":"routes-draft"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SERIES_COMMAND_ID_PAYLOAD_CONFLICT"))
                .andExpect(jsonPath("$.currentRevision").value(1));

        mvc.perform(get("/api/v1/series/" + seriesId
                        + "/games/1/draft-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftSession.session.sessionId").value(childId));
        mvc.perform(post("/api/v1/series/" + seriesId
                        + "/games/1/draft-session/actions")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"schemaVersion":"SERIES_DRAFT_ACTION_REQUEST_V1",
                                 "expectedSeriesRevision":0,"expectedDraftRevision":0,
                                 "clientCommandId":"routes-stale","championId":"%s"}
                                """.formatted(championId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SERIES_STALE_REVISION"))
                .andExpect(jsonPath("$.currentRevision").value(1));
        mvc.perform(post("/api/v1/series/" + seriesId
                        + "/games/1/draft-session/actions")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"schemaVersion":"SERIES_DRAFT_ACTION_REQUEST_V1",
                                 "expectedSeriesRevision":1,"expectedDraftRevision":0,
                                 "clientCommandId":"routes-action","championId":"%s"}
                                """.formatted(championId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series.revision").value(2))
                .andExpect(jsonPath("$.draftSession.session.revision").value(1));

        mvc.perform(post("/api/v1/series/" + seriesId + "/games/1/simulate")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"schemaVersion":"SERIES_SIMULATE_REQUEST_V1",
                                 "expectedSeriesRevision":2,"expectedDraftRevision":1,
                                 "clientCommandId":"routes-too-early"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SERIES_DRAFT_NOT_COMPLETE"));
        mvc.perform(post("/api/v1/series/" + seriesId + "/games/1/replay")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"schemaVersion":"SERIES_GAME_REPLAY_REQUEST_V1",
                                 "clientCommandId":"routes-replay"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SERIES_GAME_NOT_COMMITTED"));
        mvc.perform(get("/api/v1/series/" + seriesId + "/games/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT_ACTIVE"));

        mvc.perform(delete("/api/v1/series/" + seriesId
                        + "/games/1/draft-session")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"schemaVersion":"SERIES_DRAFT_CANCEL_REQUEST_V1",
                                 "expectedRevision":2,"clientCommandId":"routes-cancel-draft"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(delete("/api/v1/series/" + seriesId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"schemaVersion":"SERIES_CANCEL_REQUEST_V1",
                                 "expectedRevision":3,"clientCommandId":"routes-cancel"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(get("/api/v1/series/" + seriesId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reservation").doesNotExist())
                .andExpect(jsonPath("$.activeDraftSession.session.status")
                        .value("CANCELLED"));
    }

    @Test
    void strictCreateRejectsCallerAuthorityAndNonCanonicalTokens() throws Exception {
        mvc.perform(post("/api/v1/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("76", "authority")
                                .replace("}", ",\"score\":{\"GEN\":2}}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("SERIES_UNSUPPORTED_REQUEST_FIELD"));
        mvc.perform(post("/api/v1/series")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"schemaVersion":"SERIES_CREATE_REQUEST_V1","format":"BO3",
                                 "teamACode":"GEN","teamBCode":"T1",
                                 "managedTeamCode":"GEN","game1BlueTeamCode":"GEN",
                                 "rootSeed":73,"clientCommandId":"numeric-seed"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SERIES_MISSING_REQUEST_FIELD"));
        mvc.perform(post("/api/v1/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("77", "bad-format")
                                .replace("\"BO3\"", "\"BO1\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SERIES_UNSUPPORTED_FORMAT"));
        mvc.perform(post("/api/v1/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("78", "unknown-team")
                                .replace("\"teamBCode\":\"T1\"",
                                        "\"teamBCode\":\"UNKNOWN\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SERIES_UNKNOWN_TEAM"));
    }

    private static String createBody(String seed, String command) {
        return """
                {"schemaVersion":"SERIES_CREATE_REQUEST_V1","format":"BO3",
                 "teamACode":"GEN","teamBCode":"T1","managedTeamCode":"GEN",
                 "game1BlueTeamCode":"GEN","rootSeed":"%s","clientCommandId":"%s"}
                """.formatted(seed, command);
    }
}
