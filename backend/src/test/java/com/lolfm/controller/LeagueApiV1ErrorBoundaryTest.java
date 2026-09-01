package com.lolfm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lolfm.league.LeagueApiV1Facade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LeagueApiV1ErrorBoundaryTest {
    private LeagueApiV1Facade facade;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        facade = mock(LeagueApiV1Facade.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new LeagueApiV1Controller(facade,
                                new LeagueApiV1RequestParser()))
                .setControllerAdvice(new LeagueApiV1ExceptionHandler()).build();
    }

    @Test
    void transientDatabaseDetailsAreHiddenBehindStableRetryableError() throws Exception {
        when(facade.create(any())).thenThrow(new CannotAcquireLockException(
                "SELECT secret_value FROM internal_table TIMEOUT"));
        mvc.perform(post("/api/v1/leagues")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.schemaVersion").value("AI_LEAGUE_API_ERROR_V1"))
                .andExpect(jsonPath("$.code").value("LEAGUE_TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret_value"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal_table"))));
    }

    @Test
    void unexpectedInternalExceptionDoesNotExposeMessageOrStackTrace() throws Exception {
        when(facade.create(any())).thenThrow(new RuntimeException(
                "jdbc:h2:file:C:/private SELECT password"));
        mvc.perform(post("/api/v1/leagues")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("LEAGUE_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("password"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("jdbc:h2"))));
    }

    private static String validBody() {
        return """
                {"schemaVersion":"AI_LEAGUE_CREATE_REQUEST_V1",
                 "leagueKey":"error-boundary","seasonKey":"season-a",
                 "seasonMode":"SPECTATOR_FULL_AUTO","seasonRootSeed":"73",
                 "clientCommandId":"error-boundary-create"}
                """;
    }
}
