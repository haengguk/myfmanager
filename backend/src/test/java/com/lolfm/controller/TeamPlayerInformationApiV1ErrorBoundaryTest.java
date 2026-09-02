package com.lolfm.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lolfm.application.TeamPlayerInformationApiV1Service;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TeamPlayerInformationApiV1ErrorBoundaryTest {
    @Test
    void unexpectedResourceFailureDoesNotExposePathMessageOrStackTrace() throws Exception {
        TeamPlayerInformationApiV1Service service = mock(
                TeamPlayerInformationApiV1Service.class);
        when(service.metadata("LCK")).thenThrow(new RuntimeException(
                "C:/private/players.json password java.lang.IllegalStateException"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new TeamPlayerInformationApiV1Controller(service))
                .setControllerAdvice(new TeamPlayerInformationApiV1ExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/reference/leagues/LCK"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.schemaVersion")
                        .value("TEAM_PLAYER_INFORMATION_API_ERROR_V1"))
                .andExpect(jsonPath("$.code")
                        .value("PLAYER_INFORMATION_RESOURCE_INTEGRITY_FAILURE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("C:/private"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("password"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("java.lang"))));
    }
}
