package com.lolfm.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.league.LeagueBackgroundExecutionPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.main.lazy-initialization=true")
@AutoConfigureMockMvc
class LeagueApiV1BackgroundAvailabilityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean LeagueBackgroundExecutionPort background;

    @Test
    void durableQueuedWorkReturns503ThenExactReplayRekicksWithoutDuplication()
            throws Exception {
        String createdText = mvc.perform(post("/api/v1/leagues")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"schemaVersion":"AI_LEAGUE_CREATE_REQUEST_V1",
                         "leagueKey":"background-boundary",
                         "seasonKey":"season-a",
                         "seasonMode":"SPECTATOR_FULL_AUTO",
                         "managedTeamCode":null,
                         "seasonRootSeed":"904",
                         "clientCommandId":"background-create"}
                        """))
                .andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        JsonNode season = mapper.readTree(createdText).path("season");
        String base = "/api/v1/leagues/" + season.path("leagueId").asText()
                + "/seasons/" + season.path("seasonId").asText();
        String runBody = """
                {"schemaVersion":"AI_LEAGUE_RUN_ROUND_COMMAND_V1",
                 "expectedLifecycleRevision":%d,
                 "clientCommandId":"background-run"}
                """.formatted(season.path("lifecycleRevision").asLong());

        when(background.submit(anyString())).thenReturn(false);
        mvc.perform(post(base + "/commands/run-current-round")
                        .contentType(MediaType.APPLICATION_JSON).content(runBody))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("LEAGUE_BACKGROUND_EXECUTION_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true));

        when(background.submit(anyString())).thenReturn(true);
        mvc.perform(post(base + "/commands/run-current-round")
                        .contentType(MediaType.APPLICATION_JSON).content(runBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.jobs.length()").value(5));
        mvc.perform(post(base + "/commands/run-current-round")
                        .contentType(MediaType.APPLICATION_JSON).content(runBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.jobs.length()").value(5));
        mvc.perform(get(base + "/fixtures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixtures[?(@.roundNumber == 1)]")
                        .value(hasSize(5)));
        verify(background, times(3)).submit(anyString());
    }
}
