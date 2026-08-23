package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lolfm.controller.RealMatchApiV1Exception;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealMatchApiV1ServiceTest {
    private LckTeamAssembler teams;
    private RealDraftMatchOrchestrator orchestrator;
    private MatchEngineV1Canonicalizer canonicalizer;
    private RealMatchApiV1ResponseMapper mapper;
    private RealMatchApiV1Service service;

    @BeforeEach
    void setUp() {
        teams = mock(LckTeamAssembler.class);
        orchestrator = mock(RealDraftMatchOrchestrator.class);
        canonicalizer = mock(MatchEngineV1Canonicalizer.class);
        mapper = mock(RealMatchApiV1ResponseMapper.class);
        service = new RealMatchApiV1Service(teams, orchestrator, canonicalizer, mapper);
        when(teams.teamCodes()).thenReturn(Set.of("GEN", "T1"));
    }

    @Test
    void unknownAndSameTeamRequestsNeverReachDraftSimulationOrRandomBoundary() {
        assertFailure(request("UNKNOWN", "T1"), "UNKNOWN_TEAM", "blueTeamCode");
        assertFailure(request("GEN", "UNKNOWN"), "UNKNOWN_TEAM", "redTeamCode");
        assertFailure(request("GEN", "GEN"), "SAME_TEAM_NOT_ALLOWED", "redTeamCode");

        verifyNoInteractions(orchestrator, canonicalizer, mapper);
    }

    @Test
    void knownPreflightFailureIsStable422WithoutLeakingInternalDetail() {
        IllegalArgumentException internal = new IllegalArgumentException(
                "LOCAL_RESOURCE_PATH: /private/file.json");
        when(orchestrator.orchestrateV1("GEN", "T1", 73L)).thenThrow(internal);

        assertThatExceptionOfType(RealMatchApiV1Exception.class)
                .isThrownBy(() -> service.simulate(request("GEN", "T1")))
                .satisfies(error -> {
                    assertThat(error.status().value()).isEqualTo(422);
                    assertThat(error.code()).isEqualTo("REAL_MATCH_PREFLIGHT_FAILED");
                    assertThat(error.clientMessage()).doesNotContain("/private/file.json");
                });
        verify(orchestrator).orchestrateV1("GEN", "T1", 73L);
        verifyNoInteractions(canonicalizer, mapper);
    }

    @Test
    void invalidEngineHashFailsBeforeAnyTransportResponseIsMapped() {
        MatchEngineV1Output output = mock(MatchEngineV1Output.class);
        when(orchestrator.orchestrateV1("GEN", "T1", 73L)).thenReturn(output);
        when(output.executionProvenance()).thenReturn(mock(SimulationExecutionProvenance.class));
        when(output.productionPolicy()).thenReturn(MatchEngineV1Policy.authoritative());
        when(output.configurationHash()).thenReturn(
                MatchEngineV1Policy.authoritative().configurationHash());
        when(output.hasValidOutputHash(canonicalizer)).thenReturn(false);

        assertThatExceptionOfType(RealMatchApiV1Exception.class)
                .isThrownBy(() -> service.simulate(request("GEN", "T1")))
                .satisfies(error -> {
                    assertThat(error.status().value()).isEqualTo(500);
                    assertThat(error.code()).isEqualTo("ENGINE_OUTPUT_INTEGRITY_FAILED");
                });
        verify(mapper, never()).response(output);
    }

    @Test
    void validOutputIsProjectedOnlyAfterMandatoryPolicyAndHashValidation() {
        MatchEngineV1Output output = mock(MatchEngineV1Output.class);
        RealMatchApiV1Dtos.Response response = mock(RealMatchApiV1Dtos.Response.class);
        when(orchestrator.orchestrateV1("GEN", "T1", 73L)).thenReturn(output);
        when(output.executionProvenance()).thenReturn(mock(SimulationExecutionProvenance.class));
        when(output.productionPolicy()).thenReturn(MatchEngineV1Policy.authoritative());
        when(output.configurationHash()).thenReturn(
                MatchEngineV1Policy.authoritative().configurationHash());
        when(output.hasValidOutputHash(canonicalizer)).thenReturn(true);
        when(mapper.response(output)).thenReturn(response);

        assertThat(service.simulate(request("GEN", "T1"))).isSameAs(response);
        verify(output).hasValidOutputHash(canonicalizer);
        verify(mapper).response(output);
    }

    private static RealMatchApiV1Dtos.SimulateRequest request(String blue, String red) {
        return new RealMatchApiV1Dtos.SimulateRequest(
                RealMatchApiV1Dtos.REQUEST_SCHEMA, blue, red, "73");
    }

    private void assertFailure(
            RealMatchApiV1Dtos.SimulateRequest request, String code, String field
    ) {
        assertThatExceptionOfType(RealMatchApiV1Exception.class)
                .isThrownBy(() -> service.simulate(request))
                .satisfies(error -> {
                    assertThat(error.code()).isEqualTo(code);
                    assertThat(error.field()).isEqualTo(field);
                });
    }
}
