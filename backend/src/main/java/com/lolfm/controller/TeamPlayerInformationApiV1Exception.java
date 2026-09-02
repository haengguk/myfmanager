package com.lolfm.controller;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/** Stable client-facing failure at the isolated team/player reference API boundary. */
public final class TeamPlayerInformationApiV1Exception extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String field;
    private final String clientMessage;

    private TeamPlayerInformationApiV1Exception(
            HttpStatus status,
            String code,
            String field,
            String clientMessage,
            Throwable cause
    ) {
        super(code, cause);
        this.status = Objects.requireNonNull(status, "status");
        this.code = Objects.requireNonNull(code, "code");
        this.field = field;
        this.clientMessage = Objects.requireNonNull(clientMessage, "clientMessage");
    }

    public static TeamPlayerInformationApiV1Exception leagueNotFound() {
        return new TeamPlayerInformationApiV1Exception(HttpStatus.NOT_FOUND,
                "REFERENCE_LEAGUE_NOT_FOUND", "leagueCode",
                "요청한 reference league를 찾을 수 없습니다.", null);
    }

    public static TeamPlayerInformationApiV1Exception teamNotFound() {
        return new TeamPlayerInformationApiV1Exception(HttpStatus.NOT_FOUND,
                "REFERENCE_TEAM_NOT_FOUND", "teamCode",
                "요청한 reference team을 찾을 수 없습니다.", null);
    }

    public static TeamPlayerInformationApiV1Exception playerNotFound() {
        return new TeamPlayerInformationApiV1Exception(HttpStatus.NOT_FOUND,
                "REFERENCE_PLAYER_NOT_FOUND", "playerId",
                "요청한 reference player를 찾을 수 없습니다.", null);
    }

    public static TeamPlayerInformationApiV1Exception invalidQuery(String field) {
        return new TeamPlayerInformationApiV1Exception(HttpStatus.BAD_REQUEST,
                "REFERENCE_QUERY_INVALID", field,
                "reference 조회 조건이 유효하지 않습니다.", null);
    }

    public static TeamPlayerInformationApiV1Exception integrityFailure(Throwable cause) {
        return new TeamPlayerInformationApiV1Exception(HttpStatus.INTERNAL_SERVER_ERROR,
                "PLAYER_INFORMATION_RESOURCE_INTEGRITY_FAILURE", null,
                "선수 정보 resource 무결성을 확인할 수 없습니다.", cause);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }

    public String clientMessage() {
        return clientMessage;
    }
}
