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
import java.util.List;
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
        when(orchestrator.orchestrateV1("GEN", "T1", 73L))
                .thenThrow(new RealDraftMatchPreflightException(internal));

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
    void unexpectedOrEngineIllegalArgumentFailureIsStable500NotPreflight422() {
        IllegalArgumentException internal = new IllegalArgumentException(
                "MATCH_ENGINE_INTERNAL_PATH: /private/file.json");
        when(orchestrator.orchestrateV1("GEN", "T1", 73L)).thenThrow(internal);

        assertThatExceptionOfType(RealMatchApiV1Exception.class)
                .isThrownBy(() -> service.simulate(request("GEN", "T1")))
                .satisfies(error -> {
                    assertThat(error.status().value()).isEqualTo(500);
                    assertThat(error.code()).isEqualTo("REAL_MATCH_INTERNAL_ERROR");
                    assertThat(error.clientMessage()).doesNotContain("/private/file.json");
                });
        verify(orchestrator).orchestrateV1("GEN", "T1", 73L);
        verifyNoInteractions(canonicalizer, mapper);
    }

    @Test
    void invalidEngineHashFailsBeforeAnyTransportResponseIsMapped() {
        OutputFixture fixture = validOutputFixture();
        when(fixture.output().hasValidOutputHash(canonicalizer)).thenReturn(false);

        assertThatExceptionOfType(RealMatchApiV1Exception.class)
                .isThrownBy(() -> service.simulate(request("GEN", "T1")))
                .satisfies(error -> {
                    assertThat(error.status().value()).isEqualTo(500);
                    assertThat(error.code()).isEqualTo("ENGINE_OUTPUT_INTEGRITY_FAILED");
                });
        verify(mapper, never()).response(fixture.output());
    }

    @Test
    void validOutputIsProjectedOnlyAfterMandatoryPolicyAndHashValidation() {
        OutputFixture fixture = validOutputFixture();
        RealMatchApiV1Dtos.Response response = mock(RealMatchApiV1Dtos.Response.class);
        when(mapper.response(fixture.output())).thenReturn(response);

        assertThat(service.simulate(request("GEN", "T1"))).isSameAs(response);
        verify(fixture.output()).hasValidOutputHash(canonicalizer);
        verify(mapper).response(fixture.output());
    }

    @Test
    void selfConsistentOutputForDifferentTeamsOrSeedIsRejected() {
        OutputFixture wrongTeam = validOutputFixture();
        when(wrongTeam.execution().blueTeamCode()).thenReturn("T1");
        assertIntegrityFailure(wrongTeam.output());

        OutputFixture wrongSeed = validOutputFixture();
        when(wrongSeed.execution().matchSeed()).thenReturn(74L);
        assertIntegrityFailure(wrongSeed.output());
    }

    @Test
    void nonFreshSeriesOutputIsRejectedEvenWhenItsOwnHashIsValid() {
        OutputFixture gameTwo = validOutputFixture();
        when(gameTwo.execution().seriesGameNumber()).thenReturn(2);
        when(gameTwo.draft().seriesGameNumber()).thenReturn(2);
        assertIntegrityFailure(gameTwo.output());

        OutputFixture inheritedHistory = validOutputFixture();
        when(inheritedHistory.execution().seriesHistoryBeforeHash())
                .thenReturn("b".repeat(64));
        assertIntegrityFailure(inheritedHistory.output());
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

    private OutputFixture validOutputFixture() {
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        MatchEngineV1Output output = mock(MatchEngineV1Output.class);
        SimulationExecutionProvenance execution = mock(SimulationExecutionProvenance.class);
        MatchEngineV1Input.DraftInput draft = mock(MatchEngineV1Input.DraftInput.class);
        MatchEngineV1Output.MatchResultSummaryV1 result = mock(
                MatchEngineV1Output.MatchResultSummaryV1.class);
        SimulationResourceProvenance resources = mock(SimulationResourceProvenance.class);
        String timelineHash = "a".repeat(64);
        String resourceHash = MatchEngineV1Policy.APPROVED_RESOURCE_PROVENANCE_SHA256;
        String replayHash = "c".repeat(64);

        when(orchestrator.orchestrateV1("GEN", "T1", 73L)).thenReturn(output);
        when(output.executionProvenance()).thenReturn(execution);
        when(output.finalDraft()).thenReturn(draft);
        when(output.resultSummary()).thenReturn(result);
        when(output.productionPolicy()).thenReturn(policy);
        when(output.configurationHash()).thenReturn(policy.configurationHash());
        when(output.simulatorTimelineHash()).thenReturn(timelineHash);
        when(output.hasValidOutputHash(canonicalizer)).thenReturn(true);

        when(execution.runtimeProfileId()).thenReturn(policy.retainedRuntimeProfileId());
        when(execution.resolvedGameplayConfiguration()).thenReturn(
                policy.gameplayConfiguration());
        when(execution.configurationHash()).thenReturn(policy.configurationHash());
        when(execution.engineImplementationVersion()).thenReturn(
                policy.engineImplementationVersion());
        when(execution.activeGameplayRulesVersion()).thenReturn(
                policy.activeGameplayRulesVersion());
        when(execution.blueTeamCode()).thenReturn("GEN");
        when(execution.redTeamCode()).thenReturn("T1");
        when(execution.matchSeed()).thenReturn(73L);
        when(execution.seriesGameNumber()).thenReturn(1);
        when(execution.seriesHistoryBeforeHash()).thenReturn(
                MatchEngineV1Input.seriesHistoryHash(0, Set.of()));
        when(execution.replayProvenanceHashAlgorithm()).thenReturn(
                SimulationProvenanceService.MATCH_ENGINE_V1_REPLAY_PROVENANCE_HASH_ALGORITHM);
        when(execution.draftRuleSetIdentity()).thenReturn(
                MatchEngineV1Policy.DRAFT_RULE_SET_IDENTITY);
        when(execution.draftRuleSetHash()).thenReturn(
                MatchEngineV1Policy.DRAFT_RULE_SET_SHA256);
        when(execution.draftScoringPolicyHash()).thenReturn(
                MatchEngineV1Policy.DRAFT_SCORING_POLICY_SHA256);
        when(execution.draftSelectionPolicyId()).thenReturn(
                MatchEngineV1Policy.DRAFT_SELECTION_POLICY_ID);
        when(execution.draftSelectionPolicyHash()).thenReturn(
                MatchEngineV1Policy.DRAFT_SELECTION_POLICY_SHA256);
        when(execution.draftSelectionTraceHash()).thenReturn("1".repeat(64));
        when(execution.draftDecisionHash()).thenReturn("d".repeat(64));
        when(execution.finalDraftHash()).thenReturn("e".repeat(64));
        when(execution.finalAssignmentHash()).thenReturn("f".repeat(64));
        when(execution.resourceProvenance()).thenReturn(resources);
        when(execution.replayProvenanceHash()).thenReturn(replayHash);
        when(execution.timelineHash()).thenReturn(timelineHash);
        when(resources.resourceProvenanceHash()).thenReturn(resourceHash);

        when(draft.seriesGameNumber()).thenReturn(1);
        when(draft.hardFearlessExclusions()).thenReturn(List.of());
        when(draft.draftRuleSetIdentity()).thenReturn(
                MatchEngineV1Policy.DRAFT_RULE_SET_IDENTITY);
        when(draft.draftRuleSetHash()).thenReturn(MatchEngineV1Policy.DRAFT_RULE_SET_SHA256);
        when(draft.draftScoringPolicyHash()).thenReturn(
                MatchEngineV1Policy.DRAFT_SCORING_POLICY_SHA256);
        when(draft.draftSelectionPolicyId()).thenReturn(
                MatchEngineV1Policy.DRAFT_SELECTION_POLICY_ID);
        when(draft.draftSelectionPolicyHash()).thenReturn(
                MatchEngineV1Policy.DRAFT_SELECTION_POLICY_SHA256);
        when(draft.draftSelectionTraceHash()).thenReturn("1".repeat(64));
        when(draft.draftDecisionHash()).thenReturn("d".repeat(64));
        when(draft.finalDraftHash()).thenReturn("e".repeat(64));
        when(draft.finalAssignmentHash()).thenReturn("f".repeat(64));

        when(result.runtimeProfileId()).thenReturn(policy.retainedRuntimeProfileId().name());
        when(result.configurationHash()).thenReturn(policy.configurationHash());
        when(result.finalDraftHash()).thenReturn("e".repeat(64));
        when(result.finalAssignmentHash()).thenReturn("f".repeat(64));
        when(result.resourceProvenanceHash()).thenReturn(resourceHash);
        when(result.replayProvenanceHash()).thenReturn(replayHash);
        return new OutputFixture(output, execution, draft);
    }

    private void assertIntegrityFailure(MatchEngineV1Output output) {
        assertThatExceptionOfType(RealMatchApiV1Exception.class)
                .isThrownBy(() -> service.simulate(request("GEN", "T1")))
                .satisfies(error -> {
                    assertThat(error.status().value()).isEqualTo(500);
                    assertThat(error.code()).isEqualTo("ENGINE_OUTPUT_INTEGRITY_FAILED");
                });
        verify(mapper, never()).response(output);
    }

    private record OutputFixture(
            MatchEngineV1Output output,
            SimulationExecutionProvenance execution,
            MatchEngineV1Input.DraftInput draft
    ) {
    }
}
