package com.lolfm.application;

import com.lolfm.controller.RealMatchApiV1Exception;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import java.util.Objects;
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
        validateRequest(request);
        MatchEngineV1Output output;
        try {
            // This overload owns a fresh SeriesDraftHistory for exactly one Game 1.
            output = matches.orchestrateV1(
                    request.blueTeamCode(), request.redTeamCode(), request.seedAsLong());
        } catch (IllegalArgumentException error) {
            throw RealMatchApiV1Exception.unprocessable(
                    "REAL_MATCH_PREFLIGHT_FAILED", null,
                    "실제 roster 또는 Draft 사전 검증을 통과하지 못했습니다.", error);
        }
        validateOutput(output);
        try {
            return responses.response(output);
        } catch (RuntimeException error) {
            throw RealMatchApiV1Exception.integrityFailure(error);
        }
    }

    private void validateRequest(RealMatchApiV1Dtos.SimulateRequest request) {
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
            request.seedAsLong();
        } catch (NumberFormatException error) {
            throw RealMatchApiV1Exception.badRequest(
                    "INVALID_SEED", "seed",
                    "seed는 canonical signed 64-bit decimal string이어야 합니다.");
        }
    }

    private void validateOutput(MatchEngineV1Output output) {
        if (output == null
                || output.executionProvenance() == null
                || !output.productionPolicy().equals(MatchEngineV1Policy.authoritative())
                || !output.configurationHash().equals(
                MatchEngineV1Policy.authoritative().configurationHash())
                || !output.hasValidOutputHash(canonicalizer)) {
            throw RealMatchApiV1Exception.integrityFailure(null);
        }
    }
}
