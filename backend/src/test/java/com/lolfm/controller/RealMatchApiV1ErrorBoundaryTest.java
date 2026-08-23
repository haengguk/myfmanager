package com.lolfm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lolfm.application.RealMatchApiV1Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RealMatchApiV1ErrorBoundaryTest {
    private static final String REQUEST = """
            {"schemaVersion":"REAL_MATCH_SIMULATE_REQUEST_V1",
             "blueTeamCode":"GEN","redTeamCode":"T1","seed":"73"}
            """;

    private RealMatchApiV1Service service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(RealMatchApiV1Service.class);
        RealMatchApiV1Controller controller = new RealMatchApiV1Controller(
                new RealMatchApiV1RequestParser(), service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RealMatchApiV1ExceptionHandler()).build();
    }

    @Test
    void integrityFailureReturnsOnlyStableStructured500() throws Exception {
        when(service.simulate(any())).thenThrow(
                RealMatchApiV1Exception.integrityFailure(
                        new IllegalStateException("/private/resource/path")));

        mvc.perform(post("/api/v1/real-matches/simulate")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.schemaVersion").value("REAL_MATCH_API_ERROR_V1"))
                .andExpect(jsonPath("$.code").value("ENGINE_OUTPUT_INTEGRITY_FAILED"))
                .andExpect(jsonPath("$.field").isEmpty())
                .andExpect(jsonPath("$.message").value("경기 결과 무결성을 확인할 수 없습니다."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/private/resource/path"))));
    }

    @Test
    void knownPreflightFailureUsesStructured422() throws Exception {
        when(service.simulate(any())).thenThrow(RealMatchApiV1Exception.unprocessable(
                "REAL_MATCH_PREFLIGHT_FAILED", null,
                "실제 roster 또는 Draft 사전 검증을 통과하지 못했습니다.", null));

        mvc.perform(post("/api/v1/real-matches/simulate")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.schemaVersion").value("REAL_MATCH_API_ERROR_V1"))
                .andExpect(jsonPath("$.code").value("REAL_MATCH_PREFLIGHT_FAILED"));
    }
}
