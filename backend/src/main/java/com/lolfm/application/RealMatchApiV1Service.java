package com.lolfm.application;

import com.lolfm.controller.RealMatchApiV1Exception;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Stateless application service for one fresh, isolated Real Match V1 game per call. */
@Service
public final class RealMatchApiV1Service {
    private final LckTeamAssembler teams;
    private final RealDraftMatchOrchestrator matches;
    private final MatchEngineV1Canonicalizer canonicalizer;
    private final RealMatchApiV1ResponseMapper responses;

    public RealMatchApiV1Service(
            LckTeamAssembler teams,
            RealDraftMatchOrchestrator matches,
            MatchEngineV1Canonicalizer canonicalizer,
            RealMatchApiV1ResponseMapper responses
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.matches = Objects.requireNonNull(matches, "matches");
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    public RealMatchApiV1Dtos.OptionsResponse options() {
        return responses.options();
    }

    public RealMatchApiV1Dtos.Response simulate(RealMatchApiV1Dtos.SimulateRequest request) {
        long matchSeed = validateRequest(request);
        MatchEngineV1Output output;
        try {
            // This overload owns a fresh SeriesDraftHistory for exactly one Game 1.
            output = matches.orchestrateV1(
                    request.blueTeamCode(), request.redTeamCode(), matchSeed);
        } catch (RealDraftMatchPreflightException error) {
            throw RealMatchApiV1Exception.unprocessable(
                    "REAL_MATCH_PREFLIGHT_FAILED", null,
                    "실제 roster 또는 Draft 사전 검증을 통과하지 못했습니다.", error);
        } catch (RuntimeException error) {
            throw RealMatchApiV1Exception.internalFailure(error);
        }
        validateOutput(request, matchSeed, output);
        try {
            return responses.response(output);
        } catch (RuntimeException error) {
            throw RealMatchApiV1Exception.integrityFailure(error);
        }
    }

    private long validateRequest(RealMatchApiV1Dtos.SimulateRequest request) {
        Objects.requireNonNull(request, "request");
        if (!RealMatchApiV1Dtos.REQUEST_SCHEMA.equals(request.schemaVersion())) {
            throw RealMatchApiV1Exception.badRequest(
                    "INVALID_REQUEST_SCHEMA", "schemaVersion",
                    "지원하지 않는 Real Match 요청 schema입니다.");
        }
        if (request.blueTeamCode().equals(request.redTeamCode())) {
            throw RealMatchApiV1Exception.badRequest(
                    "SAME_TEAM_NOT_ALLOWED", "redTeamCode",
                    "BLUE 팀과 RED 팀은 서로 달라야 합니다.");
        }
        if (!teams.teamCodes().contains(request.blueTeamCode())) {
            throw RealMatchApiV1Exception.badRequest(
                    "UNKNOWN_TEAM", "blueTeamCode", "지원하지 않는 BLUE 팀 코드입니다.");
        }
        if (!teams.teamCodes().contains(request.redTeamCode())) {
            throw RealMatchApiV1Exception.badRequest(
                    "UNKNOWN_TEAM", "redTeamCode", "지원하지 않는 RED 팀 코드입니다.");
        }
        try {
            return request.seedAsLong();
        } catch (NumberFormatException error) {
            throw RealMatchApiV1Exception.badRequest(
                    "INVALID_SEED", "seed",
                    "seed는 canonical signed 64-bit decimal string이어야 합니다.");
        }
    }

    private void validateOutput(
            RealMatchApiV1Dtos.SimulateRequest request,
            long matchSeed,
            MatchEngineV1Output output
    ) {
        try {
            MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
            SimulationExecutionProvenance execution = Objects.requireNonNull(
                    output, "output").executionProvenance();
            MatchEngineV1Input.DraftInput draft = output.finalDraft();
            MatchEngineV1Output.MatchResultSummaryV1 result = output.resultSummary();
            String freshSeriesHistoryHash = MatchEngineV1Input.seriesHistoryHash(0, Set.of());
            boolean valid = execution != null
                    && draft != null
                    && result != null
                    && output.productionPolicy().equals(policy)
                    && output.configurationHash().equals(policy.configurationHash())
                    && execution.runtimeProfileId() == policy.retainedRuntimeProfileId()
                    && execution.resolvedGameplayConfiguration().equals(
                    policy.gameplayConfiguration())
                    && execution.configurationHash().equals(policy.configurationHash())
                    && execution.engineImplementationVersion().equals(
                    policy.engineImplementationVersion())
                    && execution.activeGameplayRulesVersion().equals(
                    policy.activeGameplayRulesVersion())
                    && execution.blueTeamCode().equals(request.blueTeamCode())
                    && execution.redTeamCode().equals(request.redTeamCode())
                    && execution.matchSeed() == matchSeed
                    && execution.seriesGameNumber() == 1
                    && execution.seriesHistoryBeforeHash().equals(freshSeriesHistoryHash)
                    && execution.replayProvenanceHashAlgorithm().equals(
                    SimulationProvenanceService.MATCH_ENGINE_V1_REPLAY_PROVENANCE_HASH_ALGORITHM)
                    && draft.seriesGameNumber() == 1
                    && draft.hardFearlessExclusions().isEmpty()
                    && draft.draftRuleSetIdentity().equals(execution.draftRuleSetIdentity())
                    && draft.draftRuleSetHash().equals(execution.draftRuleSetHash())
                    && draft.draftScoringPolicyHash().equals(
                    execution.draftScoringPolicyHash())
                    && draft.draftSelectionPolicyId().equals(
                    execution.draftSelectionPolicyId())
                    && draft.draftSelectionPolicyHash().equals(
                    execution.draftSelectionPolicyHash())
                    && draft.draftSelectionTraceHash().equals(
                    execution.draftSelectionTraceHash())
                    && draft.draftDecisionHash().equals(execution.draftDecisionHash())
                    && draft.finalDraftHash().equals(execution.finalDraftHash())
                    && draft.finalAssignmentHash().equals(execution.finalAssignmentHash())
                    && result.runtimeProfileId().equals(
                    policy.retainedRuntimeProfileId().name())
                    && result.configurationHash().equals(policy.configurationHash())
                    && result.finalDraftHash().equals(draft.finalDraftHash())
                    && result.finalAssignmentHash().equals(draft.finalAssignmentHash())
                    && result.resourceProvenanceHash().equals(
                    execution.resourceProvenance().resourceProvenanceHash())
                    && result.replayProvenanceHash().equals(
                    execution.replayProvenanceHash())
                    && output.simulatorTimelineHash().equals(execution.timelineHash())
                    && output.hasValidOutputHash(canonicalizer);
            if (!valid) {
                throw RealMatchApiV1Exception.integrityFailure(null);
            }
        } catch (RealMatchApiV1Exception error) {
            throw error;
        } catch (RuntimeException error) {
            throw RealMatchApiV1Exception.integrityFailure(error);
        }
    }
}
